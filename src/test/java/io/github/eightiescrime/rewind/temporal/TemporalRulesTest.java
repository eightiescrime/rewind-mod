package io.github.eightiescrime.rewind.temporal;

import io.github.eightiescrime.rewind.config.RewindConfig;
import io.github.eightiescrime.rewind.damage.TemporalDamageType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalRulesTest {

    @Test
    void manualCostFollowsSpecFormula() {
        RewindConfig c = new RewindConfig();
        assertEquals(40.0, TemporalRules.manualCost(c, 0));
        assertEquals(80.0, TemporalRules.manualCost(c, 5));
    }

    @Test
    void debtSlowsRegeneration() {
        RewindConfig c = new RewindConfig();
        double free = TemporalRules.regenPerTick(c, 0);
        double indebted = TemporalRules.regenPerTick(c, 4.0);
        assertEquals(1.0 / 20.0, free, 1e-9);
        assertEquals(free * 0.25, indebted, 1e-9);
    }

    @Test
    void dangerTriggersOnlyNearDeath() {
        RewindConfig c = new RewindConfig();
        assertFalse(TemporalRules.isDangerous(c, 20f, 0f, 20f, 2f));
        assertTrue(TemporalRules.isDangerous(c, 6f, 0f, 20f, 3f));
        assertTrue(TemporalRules.isDangerous(c, 6f, 0f, 20f, 99f));
    }

    @Test
    void absorptionCountsAsHealth() {
        RewindConfig c = new RewindConfig();
        assertFalse(TemporalRules.isDangerous(c, 6f, 10f, 20f, 5f));
    }

    @Test
    void lethalOnlyModeIgnoresNonLethalHits() {
        RewindConfig c = new RewindConfig();
        c.dangerMode = "LETHAL_ONLY";
        assertFalse(TemporalRules.isDangerous(c, 6f, 0f, 20f, 5f));
        assertTrue(TemporalRules.isDangerous(c, 6f, 0f, 20f, 6f));
    }

    @Test
    void damagePercentModeReactsToBigHitsAtFullHealth() {
        RewindConfig c = new RewindConfig();
        c.dangerMode = "DAMAGE_PERCENT";
        assertFalse(TemporalRules.isDangerous(c, 20f, 0f, 20f, 6f));
        assertTrue(TemporalRules.isDangerous(c, 20f, 0f, 20f, 7f));
    }

    @Test
    void masteryDiminishesOnRepeatedSource() {
        RewindConfig c = new RewindConfig();
        assertEquals(1.0, TemporalRules.masteryMultiplier(c, 0));
        assertEquals(1.0, TemporalRules.masteryMultiplier(c, 1));
        assertEquals(0.75, TemporalRules.masteryMultiplier(c, 2));
        assertEquals(0.5, TemporalRules.masteryMultiplier(c, 3));
        assertEquals(0.25, TemporalRules.masteryMultiplier(c, 4));
        assertEquals(0.0, TemporalRules.masteryMultiplier(c, 5));
        assertEquals(0.0, TemporalRules.masteryMultiplier(c, 99));
    }

    @Test
    void levelRequiresBothMasteryAndEncounter() {
        RewindConfig c = new RewindConfig();
        assertEquals(1, TemporalRules.levelFor(c, 5000, Set.of()));
        assertEquals(2, TemporalRules.levelFor(c, 5000, Set.of(TemporalDamageType.PROJECTILE)));
        assertEquals(1, TemporalRules.levelFor(c, 10, Set.of(TemporalDamageType.PROJECTILE)));
    }

    @Test
    void levelsCannotBeSkipped() {
        RewindConfig c = new RewindConfig();
        // мастерства хватает на пятый, но встречи со снарядом не было
        assertEquals(1, TemporalRules.levelFor(c, 5000,
                Set.of(TemporalDamageType.EXPLOSION, TemporalDamageType.LAVA)));
    }

    @Test
    void fullProgressionReachesFifthLevel() {
        RewindConfig c = new RewindConfig();
        Set<TemporalDamageType> seen = Set.of(
                TemporalDamageType.PROJECTILE,
                TemporalDamageType.EXPLOSION,
                TemporalDamageType.LAVA);
        assertEquals(4, TemporalRules.levelFor(c, 700, seen));
        assertEquals(5, TemporalRules.levelFor(c, 1400, seen));
    }

    @Test
    void тик_звучит_только_когда_страховка_действительно_есть() {
        RewindConfig config = new RewindConfig();
        float max = 20.0f;
        float low = (float) (max * config.dangerHealthPercent);

        assertTrue(TemporalRules.nearRewind(config, 100.0, 0, 0, low, max),
                "здоровья мало, энергии хватает — тикаем");
        assertFalse(TemporalRules.nearRewind(config, 100.0, 0, 0, max, max),
                "здоровье полное — молчим");
        assertFalse(TemporalRules.nearRewind(config, config.autoEnergyCost - 0.1, 0, 0, low, max),
                "энергии не хватит на отмотку — обещать нечего");
        assertFalse(TemporalRules.nearRewind(config, 100.0, 5, 0, low, max),
                "идёт кулдаун — отмотки не будет");
        assertFalse(TemporalRules.nearRewind(config, 100.0, 0, 5, low, max),
                "перелом отнимает способность целиком");
    }

    @Test
    void шкала_ручной_отмотки_кончается_там_где_кончается_энергия() {
        RewindConfig config = new RewindConfig();
        // 40 базовых плюс 8 за секунду при сотне энергии — это ровно семь
        // с половиной секунд, то есть семь целых засечек
        assertEquals((int) config.manualMaxSeconds,
                TemporalRules.affordableSeconds(config, config.maxEnergy),
                "на полной энергии шкала доступна целиком");
        assertEquals(0, TemporalRules.affordableSeconds(config, config.manualBaseCost),
                "хватает только на базу — ни одной целой секунды не выбрать");
        assertEquals(0, TemporalRules.affordableSeconds(config, 0.0),
                "пустая энергия — пустая шкала");
        assertEquals(2, TemporalRules.affordableSeconds(config,
                        config.manualBaseCost + 2.9 * config.manualCostPerSecond),
                "остаток отбрасывается: почти третья секунда — это всё ещё вторая");
    }
}
