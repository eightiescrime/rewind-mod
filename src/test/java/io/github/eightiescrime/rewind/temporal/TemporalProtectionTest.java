package io.github.eightiescrime.rewind.temporal;

import io.github.eightiescrime.rewind.damage.TemporalDamageType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalProtectionTest {

    @Test
    void protectionCoversSameSourceAndTypeOnly() {
        TemporalProtection p = new TemporalProtection();
        p.grant("zombie-uuid", TemporalDamageType.MELEE, 16);
        assertTrue(p.covers("zombie-uuid", TemporalDamageType.MELEE));
        assertFalse(p.covers("skeleton-uuid", TemporalDamageType.MELEE));
        assertFalse(p.covers("zombie-uuid", TemporalDamageType.PROJECTILE));
    }

    @Test
    void environmentalProtectionCoversTypeRegardlessOfSource() {
        TemporalProtection p = new TemporalProtection();
        p.grant("lava", TemporalDamageType.LAVA, 16);
        assertTrue(p.covers("что угодно", TemporalDamageType.LAVA));
        assertFalse(p.covers("что угодно", TemporalDamageType.FIRE));
    }

    @Test
    void protectionExpires() {
        TemporalProtection p = new TemporalProtection();
        p.grant("zombie-uuid", TemporalDamageType.MELEE, 2);
        p.tick();
        assertTrue(p.covers("zombie-uuid", TemporalDamageType.MELEE));
        p.tick();
        assertFalse(p.covers("zombie-uuid", TemporalDamageType.MELEE));
        assertTrue(p.isEmpty());
    }

    @Test
    void longerGrantWins() {
        TemporalProtection p = new TemporalProtection();
        p.grant("lava", TemporalDamageType.LAVA, 20);
        p.grant("lava", TemporalDamageType.LAVA, 4);
        for (int i = 0; i < 5; i++) {
            p.tick();
        }
        assertTrue(p.covers("lava", TemporalDamageType.LAVA));
    }
}
