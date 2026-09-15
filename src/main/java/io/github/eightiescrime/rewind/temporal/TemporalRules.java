package io.github.eightiescrime.rewind.temporal;

import io.github.eightiescrime.rewind.config.RewindConfig;
import io.github.eightiescrime.rewind.damage.TemporalDamageType;

import java.util.Set;

/**
 * Вся арифметика способности: стоимость, регенерация, порог опасности,
 * убывающее мастерство, уровень.
 *
 * <p>Здесь намеренно нет ни одного класса Minecraft — поэтому баланс
 * проверяется обычными юнит-тестами, без запуска игры.
 */
public final class TemporalRules {
    public static final int TICKS_PER_SECOND = 20;

    private TemporalRules() {
    }

    public static int secondsToTicks(double seconds) {
        return (int) Math.round(seconds * TICKS_PER_SECOND);
    }

    /** ТЗ §7: manualCost = baseCost + rewindSeconds * costPerSecond. */
    public static double manualCost(RewindConfig config, double seconds) {
        return config.manualBaseCost + seconds * config.manualCostPerSecond;
    }

    /**
     * До какой глубины игрок дотягивается прямо сейчас — в целых секундах.
     *
     * <p>Шкала ручной отмотки размечена посекундно, дробная глубина на ней
     * всё равно не выбирается, поэтому остаток отбрасывается: показать
     * доступной секунду, на которую не хватает энергии, — значит соврать.
     *
     * <p>Считается на сервере и уезжает клиенту уже готовым числом: раздавать
     * клиенту стоимость и энергию в сыром виде — значит рассказывать, как
     * подделать запрос (ТЗ §24).
     */
    public static int affordableSeconds(RewindConfig config, double energy) {
        int ceiling = (int) config.manualMaxSeconds;
        if (config.manualCostPerSecond <= 0.0) {
            return energy >= config.manualBaseCost ? ceiling : 0;
        }
        double reach = (energy - config.manualBaseCost) / config.manualCostPerSecond;
        return (int) Math.clamp(Math.floor(reach), 0, ceiling);
    }

    /**
     * ТЗ §8: долг замедляет восстановление энергии, но не останавливает его.
     * Это и есть предохранитель от цепочки «отмотался — восстановился — отмотался».
     */
    public static double regenPerTick(RewindConfig config, double debtSeconds) {
        double perTick = config.energyRegenPerSecond / TICKS_PER_SECOND;
        return debtSeconds > 0 ? perTick * config.debtRegenMultiplier : perTick;
    }

    /**
     * ТЗ §5: способность не должна дёргаться на каждую царапину.
     *
     * <p>Смертельный урон считается опасным в любом режиме — иначе
     * «lethal-only» оказался бы строже самого себя.
     */
    public static boolean isDangerous(RewindConfig config,
                                      float health, float absorption, float maxHealth, float incoming) {
        float effective = health + absorption;
        float projected = effective - incoming;
        if (projected <= 0.0f) {
            return true;
        }
        return switch (config.dangerMode) {
            case "LETHAL_ONLY" -> false;
            case "DAMAGE_PERCENT" -> incoming >= maxHealth * config.dangerDamagePercent;
            default -> projected <= maxHealth * config.dangerHealthPercent;
        };
    }

    /**
     * Пора ли предупреждать тиком: здоровья мало, но страховка ещё есть.
     *
     * <p>Тикать, когда отмотка всё равно не сработает, хуже, чем молчать:
     * игрок расслабится и умрёт под звук, обещавший спасение. Поэтому здесь
     * проверяется не только здоровье, но и энергия, кулдаун и перелом.
     */
    public static boolean nearRewind(RewindConfig config, double energy, int autoCooldown,
                                     int fractureTicks, float healthPool, float maxHealth) {
        if (energy < config.autoEnergyCost || autoCooldown > 0 || fractureTicks > 0) {
            return false;
        }
        return healthPool <= maxHealth * config.dangerHealthPercent;
    }

    /**
     * ТЗ §36: один и тот же источник кормит всё хуже.
     *
     * @param timesSeenRecently сколько раз этот источник уже давал мастерство
     *                          с прошлого сброса
     */
    public static double masteryMultiplier(RewindConfig config, int timesSeenRecently) {
        double[] table = config.diminishing;
        if (table.length == 0) {
            return 1.0;
        }
        int index = Math.min(timesSeenRecently, table.length - 1);
        return table[Math.max(0, index)];
    }

    /**
     * ТЗ §37: уровень требует и мастерства, и встречи с новой угрозой.
     *
     * <p>Проверка идёт снизу вверх и останавливается на первом непройденном
     * уровне: нельзя перепрыгнуть третий уровень, набрав мастерства на четвёртый.
     */
    public static int levelFor(RewindConfig config, double mastery, Set<TemporalDamageType> encountered) {
        int level = 1;
        for (int next = 2; next <= config.maxLevel; next++) {
            double required = next - 1 < config.masteryPerLevel.length
                    ? config.masteryPerLevel[next - 1]
                    : Double.MAX_VALUE;
            if (mastery < required) {
                break;
            }
            Set<TemporalDamageType> needed = TemporalDamageType.unlockedAtLevel(next);
            if (!needed.isEmpty() && needed.stream().noneMatch(encountered::contains)) {
                break;
            }
            level = next;
        }
        return level;
    }
}
