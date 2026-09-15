package io.github.eightiescrime.rewind.temporal;

import io.github.eightiescrime.rewind.config.RewindConfig;
import io.github.eightiescrime.rewind.network.TemporalSync;
import io.github.eightiescrime.rewind.persistence.TemporalAttachments;
import io.github.eightiescrime.rewind.persistence.TemporalState;
import io.github.eightiescrime.rewind.sound.RewindSounds;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Сердцебиение способности: раз в тик снимает срез каждого игрока
 * и крутит энергию, долг, кулдауны и защиты.
 *
 * <p>Буферы держатся здесь, а не в состоянии игрока, именно потому, что они
 * не должны сохраняться (ТЗ §41). Всё выполняется на серверном треде, поэтому
 * обычная карта без синхронизации.
 * ponytail: single-threaded access, ConcurrentHashMap если появятся асинхронные потребители.
 */
public final class TemporalManager {
    public static final TemporalManager INSTANCE = new TemporalManager();

    private final Map<UUID, TemporalBuffer> buffers = new HashMap<>();
    private long tickCounter;

    private TemporalManager() {
    }

    public TemporalBuffer buffer(ServerPlayerEntity player) {
        return buffers.computeIfAbsent(player.getUuid(),
                uuid -> new TemporalBuffer(RewindConfig.get().bufferCapacity()));
    }

    public void clearBuffer(ServerPlayerEntity player) {
        TemporalBuffer buffer = buffers.get(player.getUuid());
        if (buffer != null) {
            buffer.clear();
        }
    }

    /** Полностью забыть игрока: вызывается при выходе с сервера. */
    public void forget(ServerPlayerEntity player) {
        buffers.remove(player.getUuid());
        TemporalSync.forget(player);
    }

    public void tick(MinecraftServer server) {
        RewindConfig config = RewindConfig.get();
        if (!config.enabled) {
            return;
        }
        tickCounter++;
        boolean captureThisTick = tickCounter % config.ticksBetweenSnapshots() == 0;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            TemporalState state = TemporalAttachments.of(player);
            tickTimers(state, config);

            if (player.isSpectator() || !player.isAlive()) {
                // в этих состояниях позиция ничего не значит, копить её вредно
                clearBuffer(player);
            } else {
                if (captureThisTick) {
                    buffer(player).push(capture(player));
                }
                nearRewindTick(player, state, config, tickCounter);
            }

            TemporalAttachments.markDirty(player, state);
            TemporalSync.maybeSend(player, state, tickCounter);
        }
    }

    /**
     * Едва слышный тик, когда здоровья мало, а страховка ещё есть.
     *
     * <p>Смысл в том, чтобы игрок чувствовал отмотку до удара, а не узнавал
     * о ней постфактум. Тик звучит только тогда, когда отмотка действительно
     * сработает: хватает энергии, нет кулдауна и нет перелома.
     */
    private static void nearRewindTick(ServerPlayerEntity player, TemporalState state,
                                       RewindConfig config, long tick) {
        if (!config.soundEnabled || !config.serverFeedbackEnabled || !state.unlocked) {
            return;
        }
        int every = TemporalRules.secondsToTicks(config.nearRewindTickSeconds);
        if (every <= 0 || tick % every != 0) {
            return;
        }
        float pool = player.getHealth() + player.getAbsorptionAmount();
        if (!TemporalRules.nearRewind(config, state.energy, state.autoCooldown,
                state.fractureTicks, pool, player.getMaxHealth())) {
            return;
        }
        player.playSoundToPlayer(RewindSounds.TEMPORAL_TICK, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private static void tickTimers(TemporalState state, RewindConfig config) {
        if (state.autoCooldown > 0) {
            state.autoCooldown--;
        }
        if (state.manualCooldown > 0) {
            state.manualCooldown--;
        }
        if (state.fractureTicks > 0) {
            state.fractureTicks--;
        }
        state.protection.tick();

        if (state.debtSeconds > 0) {
            state.debtSeconds = Math.max(0.0,
                    state.debtSeconds - config.debtDecayPerSecond / TemporalRules.TICKS_PER_SECOND);
        }

        // временной перелом временно отнимает способность целиком, включая
        // восстановление энергии (ТЗ §39)
        if (state.fractureTicks == 0 && state.energy < config.maxEnergy) {
            state.energy = Math.min(config.maxEnergy,
                    state.energy + TemporalRules.regenPerTick(config, state.debtSeconds));
        }

        if (state.recentResetTicks > 0) {
            state.recentResetTicks--;
        } else if (!state.recentSources.isEmpty()) {
            // множители убывающей отдачи периодически восстанавливаются (ТЗ §36)
            state.recentSources.clear();
            state.recentResetTicks = config.diminishingResetMinutes * 60 * TemporalRules.TICKS_PER_SECOND;
        }
    }

    public TemporalSnapshot capture(ServerPlayerEntity player) {
        return new TemporalSnapshot(
                player.getServerWorld().getTime(),
                player.getX(), player.getY(), player.getZ(),
                player.getVelocity().x, player.getVelocity().y, player.getVelocity().z,
                player.getYaw(), player.getPitch(),
                player.getHealth(), player.getAbsorptionAmount(),
                player.getHungerManager().getFoodLevel(),
                player.getHungerManager().getSaturationLevel(),
                player.getHungerManager().getExhaustion(),
                player.getAir(), player.getFireTicks(), player.getFrozenTicks(),
                player.fallDistance, player.isOnGround(),
                player.getInventory().selectedSlot,
                player.getStatusEffects().stream().map(StatusEffectInstance::new).toList());
    }
}
