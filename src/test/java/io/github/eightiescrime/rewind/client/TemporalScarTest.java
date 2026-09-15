package io.github.eightiescrime.rewind.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalScarTest {

    @Test
    void oneSmallRewindLeavesFrameClean() {
        assertEquals(0.0f, TemporalScar.intensity(0));
        assertEquals(0.0f, TemporalScar.intensity(TemporalScar.FLOOR_PERCENT));
    }

    @Test
    void intensityGrowsFromFloorToFull() {
        assertEquals(0.5f, TemporalScar.intensity(60), 1e-6f);
        assertEquals(1.0f, TemporalScar.intensity(100));
        // потолок долга мог быть занижен в конфиге — кадр всё равно не сходит с ума
        assertEquals(1.0f, TemporalScar.intensity(140));
    }

    @Test
    void tearsComeFasterWithDeeperScar() {
        assertTrue(TemporalScar.tearDelayMs(1.0f) < TemporalScar.tearDelayMs(0.2f));
        assertEquals(TemporalScar.TEAR_FAST_MS, TemporalScar.tearDelayMs(1.0f));
        assertEquals(TemporalScar.TEAR_SLOW_MS, TemporalScar.tearDelayMs(0.0f));
    }
}
