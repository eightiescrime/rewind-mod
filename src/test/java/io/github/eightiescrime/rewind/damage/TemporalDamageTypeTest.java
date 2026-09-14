package io.github.eightiescrime.rewind.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalDamageTypeTest {

    @Test
    void requiredLevelsMatchProgression() {
        assertEquals(1, TemporalDamageType.MELEE.requiredLevel());
        assertEquals(2, TemporalDamageType.PROJECTILE.requiredLevel());
        assertEquals(3, TemporalDamageType.EXPLOSION.requiredLevel());
        assertEquals(4, TemporalDamageType.LAVA.requiredLevel());
        assertEquals(4, TemporalDamageType.FALL.requiredLevel());
        assertEquals(5, TemporalDamageType.OTHER.requiredLevel());
    }

    @Test
    void environmentalTypesAreGrouped() {
        assertTrue(TemporalDamageType.LAVA.isEnvironmental());
        assertTrue(TemporalDamageType.FREEZING.isEnvironmental());
        assertFalse(TemporalDamageType.MELEE.isEnvironmental());
        assertFalse(TemporalDamageType.OTHER.isEnvironmental());
    }

    @Test
    void levelUnlockRequirementsFollowSpec() {
        assertEquals(java.util.Set.of(TemporalDamageType.PROJECTILE),
                TemporalDamageType.unlockedAtLevel(2));
        assertEquals(java.util.Set.of(TemporalDamageType.EXPLOSION),
                TemporalDamageType.unlockedAtLevel(3));
        assertTrue(TemporalDamageType.unlockedAtLevel(4).contains(TemporalDamageType.FALL));
        assertTrue(TemporalDamageType.unlockedAtLevel(5).isEmpty());
    }
}
