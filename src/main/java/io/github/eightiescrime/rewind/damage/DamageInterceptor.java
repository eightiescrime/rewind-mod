package io.github.eightiescrime.rewind.damage;

import io.github.eightiescrime.rewind.config.RewindConfig;
import io.github.eightiescrime.rewind.feedback.ServerFeedback;
import io.github.eightiescrime.rewind.persistence.TemporalAttachments;
import io.github.eightiescrime.rewind.persistence.TemporalState;
import io.github.eightiescrime.rewind.progression.ProgressionManager;
import io.github.eightiescrime.rewind.sound.RewindSounds;
import io.github.eightiescrime.rewind.temporal.RewindExecutor;
import io.github.eightiescrime.rewind.temporal.RewindResult;
import io.github.eightiescrime.rewind.temporal.TemporalRules;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;

/**
 * Перехват урона до его применения (ТЗ §21).
 *
 * <p>{@code ALLOW_DAMAGE} — штатное событие Fabric API, и оно отменяет урон
 * целиком, а не лечит игрока постфактум. Поэтому не срабатывают ни эффекты
 * попадания, ни откидывание, ни смертельная ветка — миксин ради этого не нужен
 * (RULE 6).
 */
public final class DamageInterceptor {

    private DamageInterceptor() {
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) {
                return true;
            }
            RewindConfig config = RewindConfig.get();
            if (!config.enabled) {
                return true;
            }

            TemporalState state = TemporalAttachments.of(player);
            DamageContext ctx = DamageClassifier.context(player, source, amount);

            // встреча засчитывается всегда, даже если отмотки не будет (ТЗ §37)
            boolean firstEncounter = ProgressionManager.recordEncounter(player, state, ctx);
            TemporalAttachments.markDirty(player, state);

            if (!state.unlocked) {
                return true;
            }

            // повтор той же угрозы сразу после отмотки гасится без новой отмотки,
            // иначе получится вечный цикл «лава — отмотка — лава» (ТЗ §10)
            if (state.protection.covers(ctx.sourceKey(), ctx.type())) {
                return false;
            }

            if (ctx.type().requiredLevel() > state.level) {
                return true;
            }

            // ponytail: порог считается по сырому урону до брони — игрок иногда
            // отматывается чуть раньше, чем строго необходимо. Точный расчёт —
            // через DamageUtil.getDamageLeft, если понадобится тонкий баланс.
            if (!TemporalRules.isDangerous(config, player.getHealth(), player.getAbsorptionAmount(),
                    player.getMaxHealth(), amount)) {
                return true;
            }

            RewindResult result = RewindExecutor.rewind(player, config.autoRewindSeconds, ctx, false);
            if (!result.ok()) {
                // запомнить, чего не хватило: если игрок сейчас умрёт, ему надо
                // сказать причину, иначе он решит, что мод сломался
                state.lastDenial = result;
                state.lastDenialTicks = TemporalRules.secondsToTicks(config.denialMemorySeconds);

                // время не удержало. Молчать здесь нельзя: удар был опасный,
                // страховка не сработала, и без звука игрок решит, что мод сломан
                if (config.soundEnabled && config.serverFeedbackEnabled) {
                    player.playSoundToPlayer(RewindSounds.TEMPORAL_INSTABILITY,
                            SoundCategory.PLAYERS, 0.8f, 1.0f);
                }
                return true;   // урон проходит как обычно
            }

            ProgressionManager.rewardRewind(player, state, ctx, firstEncounter);
            ServerFeedback.rewind(player, ctx.position());
            return false;      // урон отменён, игрок уже в прошлом
        });
    }
}
