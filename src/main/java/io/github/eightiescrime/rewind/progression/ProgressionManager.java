package io.github.eightiescrime.rewind.progression;

import io.github.eightiescrime.rewind.config.RewindConfig;
import io.github.eightiescrime.rewind.damage.DamageContext;
import io.github.eightiescrime.rewind.damage.TemporalDamageType;
import io.github.eightiescrime.rewind.feedback.ServerFeedback;
import io.github.eightiescrime.rewind.persistence.TemporalAttachments;
import io.github.eightiescrime.rewind.persistence.TemporalState;
import io.github.eightiescrime.rewind.temporal.TemporalRules;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Путь от инстинкта к контролю (ТЗ §2, §35–§37).
 *
 * <p>Мастерство даётся не за количество повторов, а за разнообразие: один и тот
 * же зомби кормит всё хуже, а первая встреча с незнакомой угрозой стоит дорого.
 * Уровень при этом требует и мастерства, и самой встречи — защиту от взрыва
 * нельзя получить, ни разу не услышав шипение.
 */
public final class ProgressionManager {

    private ProgressionManager() {
    }

    /**
     * Сколько мастерства принесёт отмотка от этого источника.
     *
     * <p>Метод не чистый: он же и увеличивает счётчик повторов источника.
     * Так сделано намеренно — вся арифметика убывающей отдачи остаётся
     * в одном месте и проверяется юнит-тестом без запуска игры.
     */
    public static double masteryGain(RewindConfig config, TemporalState state,
                                     TemporalDamageType type, String sourceKey, boolean firstEncounter) {
        String key = type.name() + '|' + sourceKey;
        int seen = state.recentSources.getOrDefault(key, 0);
        state.recentSources.put(key, seen + 1);

        double gain = config.masteryPerRewind * TemporalRules.masteryMultiplier(config, seen);
        if (firstEncounter) {
            gain += config.masteryFirstEncounterBonus;
        }
        return gain;
    }

    /**
     * Отмечает, что игрок столкнулся с угрозой.
     *
     * <p>Встреча засчитывается от самого факта урона — умирать ради неё не надо
     * (ТЗ §37).
     *
     * @return была ли это первая встреча с таким типом угрозы
     */
    public static boolean recordEncounter(ServerPlayerEntity player, TemporalState state, DamageContext ctx) {
        boolean first = !state.hasMet(ctx.type());
        state.encounters.merge(ctx.type(), 1, Integer::sum);
        if (first) {
            tryLevelUp(player, state);
        }
        return first;
    }

    /** Начисляет мастерство за удавшуюся отмотку и поднимает уровень, если пора. */
    public static void rewardRewind(ServerPlayerEntity player, TemporalState state,
                                    DamageContext ctx, boolean firstEncounter) {
        RewindConfig config = RewindConfig.get();
        state.mastery += masteryGain(config, state, ctx.type(), ctx.sourceKey(), firstEncounter);
        tryLevelUp(player, state);
        TemporalAttachments.markDirty(player, state);
    }

    public static boolean tryLevelUp(ServerPlayerEntity player, TemporalState state) {
        RewindConfig config = RewindConfig.get();
        int deserved = TemporalRules.levelFor(config, state.mastery, state.encountered());
        if (deserved <= state.level) {
            return false;
        }
        state.level = deserved;
        state.energy = config.maxEnergy;
        ServerFeedback.levelUp(player, deserved);
        TemporalAttachments.markDirty(player, state);
        return true;
    }

    /**
     * Первое обретение способности (ТЗ §38).
     *
     * <p>Пока единственный путь сюда — команда оператора: Temporal Rift
     * с реликвией придёт в седьмой фазе.
     */
    public static boolean unlock(ServerPlayerEntity player, TemporalState state) {
        if (state.unlocked) {
            return false;
        }
        RewindConfig config = RewindConfig.get();
        state.unlocked = true;
        state.level = Math.max(1, state.level);
        state.energy = config.maxEnergy;
        ServerFeedback.unlocked(player);
        TemporalAttachments.markDirty(player, state);
        return true;
    }
}
