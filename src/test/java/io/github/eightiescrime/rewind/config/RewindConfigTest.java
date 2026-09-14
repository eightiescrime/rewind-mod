package io.github.eightiescrime.rewind.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RewindConfigTest {

    @Test
    void defaultsMatchSpec() {
        RewindConfig c = new RewindConfig();
        assertEquals(100.0, c.maxEnergy);
        assertEquals(20.0, c.autoEnergyCost);
        assertEquals(40.0, c.manualBaseCost);
        assertEquals(8.0, c.manualCostPerSecond);
        assertEquals(0.6, c.autoRewindSeconds);
        assertEquals(5.0, c.autoCooldownSeconds);
        assertEquals(0.8, c.temporalProtectionSeconds);
        assertEquals(10.0, c.bufferSeconds);
        assertEquals(20, c.snapshotsPerSecond);
    }

    @Test
    void savesAndLoadsRoundTrip(@TempDir Path dir) {
        Path file = dir.resolve("rewind.json");
        RewindConfig original = new RewindConfig();
        original.maxEnergy = 250.0;
        original.dangerMode = "LETHAL_ONLY";
        RewindConfig.save(file, original);

        RewindConfig loaded = RewindConfig.load(file);
        assertEquals(250.0, loaded.maxEnergy);
        assertEquals("LETHAL_ONLY", loaded.dangerMode);
        assertEquals(0.75, loaded.diminishing[2]);
    }

    @Test
    void bufferSizingFollowsConfig() {
        RewindConfig c = new RewindConfig();
        assertEquals(200, c.bufferCapacity());
        assertEquals(1, c.ticksBetweenSnapshots());

        c.snapshotsPerSecond = 5;
        c.bufferSeconds = 8.0;
        assertEquals(40, c.bufferCapacity());
        assertEquals(4, c.ticksBetweenSnapshots());
    }
}
