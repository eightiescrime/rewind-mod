package io.github.eightiescrime.rewind.progression;

import io.github.eightiescrime.rewind.config.RewindConfig;
import io.github.eightiescrime.rewind.damage.TemporalDamageType;
import io.github.eightiescrime.rewind.persistence.TemporalState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionManagerTest {

    @Test
    void repeatedSourceGivesDiminishingMastery() {
        RewindConfig c = new RewindConfig();
        TemporalState s = new TemporalState();
        double[] got = new double[6];
        for (int i = 0; i < 6; i++) {
            got[i] = ProgressionManager.masteryGain(c, s, TemporalDamageType.MELEE, "zombie", false);
        }
        assertEquals(20.0, got[0]);
        assertEquals(20.0, got[1]);
        assertEquals(15.0, got[2]);
        assertEquals(10.0, got[3]);
        assertEquals(5.0, got[4]);
        assertEquals(0.0, got[5]);
    }

    @Test
    void firstEncounterAddsBonus() {
        RewindConfig c = new RewindConfig();
        TemporalState s = new TemporalState();
        assertEquals(70.0,
                ProgressionManager.masteryGain(c, s, TemporalDamageType.EXPLOSION, "creeper", true));
    }

    @Test
    void differentSourcesDoNotShareDiminishing() {
        RewindConfig c = new RewindConfig();
        TemporalState s = new TemporalState();
        for (int i = 0; i < 6; i++) {
            ProgressionManager.masteryGain(c, s, TemporalDamageType.MELEE, "zombie", false);
        }
        assertEquals(0.0, ProgressionManager.masteryGain(c, s, TemporalDamageType.MELEE, "zombie", false));
        assertEquals(20.0, ProgressionManager.masteryGain(c, s, TemporalDamageType.MELEE, "spider", false));
    }

    @Test
    void sameSourceThroughDifferentThreatCountsSeparately() {
        RewindConfig c = new RewindConfig();
        TemporalState s = new TemporalState();
        // один и тот же скелет, но лук и меч — разные уроки
        for (int i = 0; i < 6; i++) {
            ProgressionManager.masteryGain(c, s, TemporalDamageType.PROJECTILE, "skeleton", false);
        }
        assertEquals(20.0,
                ProgressionManager.masteryGain(c, s, TemporalDamageType.MELEE, "skeleton", false));
    }

    @Test
    void grindingOneZombieCannotReachSecondLevel() {
        RewindConfig c = new RewindConfig();
        TemporalState s = new TemporalState();
        for (int i = 0; i < 50; i++) {
            s.mastery += ProgressionManager.masteryGain(c, s, TemporalDamageType.MELEE, "zombie", false);
        }
        // порог второго уровня — 120, а один источник даёт всего 70
        assertTrue(s.mastery < c.masteryPerLevel[1]);
    }
}
