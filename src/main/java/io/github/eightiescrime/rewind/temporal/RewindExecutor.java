package io.github.eightiescrime.rewind.temporal;

import io.github.eightiescrime.rewind.RewindMod;
import io.github.eightiescrime.rewind.config.RewindConfig;
import io.github.eightiescrime.rewind.damage.DamageContext;
import io.github.eightiescrime.rewind.damage.TemporalDamageType;
import io.github.eightiescrime.rewind.persistence.TemporalAttachments;
import io.github.eightiescrime.rewind.persistence.TemporalState;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Собственно отмотка: проверки, поиск безопасной точки, восстановление игрока.
 *
 * <p>Возвращается только игрок. Мир не трогается ни в одной строчке этого
 * класса — ни блоки, ни мобы, ни снаряды, ни время (RULE 1). Зомби, ударивший
 * игрока, остаётся там же и в том же состоянии: отмотка даёт второй шанс,
 * а не отменяет угрозу (ТЗ §58).
 */
public final class RewindExecutor {

    /** Сколько снимков просматривать назад в поисках безопасной точки. */
    private static final int MAX_SAFETY_STEPS = 40;

    private RewindExecutor() {
    }

    public static RewindResult rewind(ServerPlayerEntity player, double seconds,
                                      @Nullable DamageContext cause, boolean manual) {
        RewindConfig config = RewindConfig.get();
        if (!config.enabled) {
            return RewindResult.DISABLED;
        }
        // ТЗ §22: если Minecraft уже перевёл игрока в состояние смерти, воскрешать нельзя
        if (!player.isAlive() || player.isDead()) {
            return RewindResult.DEAD;
        }

        TemporalState state = TemporalAttachments.of(player);
        if (!state.unlocked) {
            return RewindResult.NOT_UNLOCKED;
        }
        if (manual && state.level < config.maxLevel) {
            return RewindResult.WRONG_LEVEL;
        }
        if (manual && seconds > config.manualMaxSeconds) {
            return RewindResult.TOO_DEEP;
        }
        if ((manual ? state.manualCooldown : state.autoCooldown) > 0 || state.fractureTicks > 0) {
            return RewindResult.ON_COOLDOWN;
        }

        double cost = manual ? TemporalRules.manualCost(config, seconds) : config.autoEnergyCost;
        if (state.energy < cost) {
            return RewindResult.NO_ENERGY;
        }

        ServerWorld world = player.getServerWorld();
        TemporalBuffer buffer = TemporalManager.INSTANCE.buffer(player);
        long targetTick = world.getTime() - TemporalRules.secondsToTicks(seconds);

        Optional<TemporalSnapshot> found = buffer.findAtOrBefore(targetTick);
        if (found.isEmpty()) {
            return RewindResult.NO_BUFFER;
        }

        TemporalSnapshot chosen = findSafe(world, player, buffer, found.get());
        if (chosen == null) {
            return RewindResult.NO_SAFE_SNAPSHOT;
        }

        restore(player, chosen, cause);

        // ТЗ §15: использованная временная точка перестаёт существовать,
        // иначе в неё можно было бы возвращаться снова и снова
        buffer.dropFrom(chosen.tick());

        state.energy -= cost;
        if (manual) {
            state.manualCooldown = TemporalRules.secondsToTicks(config.manualCooldownSeconds);
            state.debtSeconds = Math.min(config.maxDebtSeconds, state.debtSeconds + seconds);
        } else {
            state.autoCooldown = TemporalRules.secondsToTicks(config.autoCooldownSeconds);
        }

        int protectionTicks = TemporalRules.secondsToTicks(config.temporalProtectionSeconds);
        if (cause != null) {
            state.protection.grant(cause.sourceKey(), cause.type(), protectionTicks);
        } else {
            state.protection.grant("manual", TemporalDamageType.OTHER, protectionTicks);
        }

        TemporalAttachments.markDirty(player, state);
        return RewindResult.SUCCESS;
    }

    /**
     * Ищет снимок, в который безопасно вернуться.
     *
     * <p>За прошедшие секунды мир мог измениться: там, где игрок стоял, теперь
     * может быть блок или разлившаяся лава. Мир при этом не откатывается, значит
     * подстраиваться должна отмотка — шагаем дальше в прошлое, пока точка не
     * окажется пригодной (ТЗ §45, §47).
     */
    @Nullable
    private static TemporalSnapshot findSafe(ServerWorld world, ServerPlayerEntity player,
                                             TemporalBuffer buffer, TemporalSnapshot start) {
        TemporalSnapshot candidate = start;
        for (int step = 0; step < MAX_SAFETY_STEPS; step++) {
            if (isSafe(world, player, candidate)) {
                return candidate;
            }
            Optional<TemporalSnapshot> earlier = buffer.findAtOrBefore(candidate.tick() - 1);
            if (earlier.isEmpty()) {
                return null;
            }
            candidate = earlier.get();
        }
        return null;
    }

    private static boolean isSafe(ServerWorld world, ServerPlayerEntity player, TemporalSnapshot snapshot) {
        Box box = player.getDimensions(EntityPose.STANDING)
                .getBoxAt(snapshot.x(), snapshot.y(), snapshot.z());
        if (!world.isSpaceEmpty(player, box)) {
            return false;
        }
        // вернуться обратно в лаву — значит получить тот же урон следующим тиком
        BlockPos pos = BlockPos.ofFloored(snapshot.x(), snapshot.y(), snapshot.z());
        return !world.getFluidState(pos).isIn(FluidTags.LAVA);
    }

    /**
     * Возвращает игроку его прошлое состояние.
     *
     * <p>Инвентарь, опыт, прочность и продвижения здесь не упоминаются вовсе —
     * их нет в снимке, и они остаются такими, какими стали (ТЗ §17, §18).
     */
    private static void restore(ServerPlayerEntity player, TemporalSnapshot snapshot,
                                @Nullable DamageContext cause) {
        player.networkHandler.requestTeleport(snapshot.x(), snapshot.y(), snapshot.z(),
                snapshot.yaw(), snapshot.pitch());

        boolean fromFall = cause != null && cause.type() == TemporalDamageType.FALL;
        // при падении вертикальную скорость гасим, иначе игрок повторит ту же
        // траекторию и разобьётся снова (ТЗ §46)
        player.setVelocity(snapshot.velX(), fromFall ? 0.0 : snapshot.velY(), snapshot.velZ());
        player.velocityModified = true;

        player.setHealth(snapshot.health());
        player.setAbsorptionAmount(snapshot.absorption());
        player.getHungerManager().setFoodLevel(snapshot.foodLevel());
        player.getHungerManager().setSaturationLevel(snapshot.saturation());
        player.getHungerManager().setExhaustion(snapshot.exhaustion());
        player.setAir(snapshot.air());
        player.setFireTicks(snapshot.fireTicks());
        player.setFrozenTicks(snapshot.frozenTicks());

        // намеренное расхождение со снимком: накопленное падение не возвращаем,
        // иначе приземление тут же повторит смертельный урон (ТЗ §46)
        player.fallDistance = 0.0f;

        player.clearStatusEffects();
        for (StatusEffectInstance effect : snapshot.effects()) {
            player.addStatusEffect(new StatusEffectInstance(effect));
        }
        player.getInventory().selectedSlot = snapshot.selectedSlot();

        RewindMod.LOGGER.debug("Rewind: {} возвращён на тик {}",
                player.getGameProfile().getName(), snapshot.tick());
    }
}
