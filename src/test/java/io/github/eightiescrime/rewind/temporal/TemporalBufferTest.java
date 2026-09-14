package io.github.eightiescrime.rewind.temporal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalBufferTest {

    /** Снимок-заглушка: без эффектов, чтобы тест не поднимал классы Minecraft. */
    private static TemporalSnapshot snap(long tick) {
        return new TemporalSnapshot(tick,
                0, 0, 0, 0, 0, 0, 0f, 0f,
                20f, 0f, 20, 5f, 0f, 300, 0, 0, 0f, true, 0,
                List.of());
    }

    @Test
    void overwritesOldestWhenFull() {
        TemporalBuffer b = new TemporalBuffer(3);
        for (long t = 1; t <= 5; t++) {
            b.push(snap(t));
        }
        assertEquals(3, b.size());
        assertEquals(3, b.oldestTick());
        assertEquals(5, b.newestTick());
    }

    @Test
    void findsNearestSnapshotAtOrBeforeTarget() {
        TemporalBuffer b = new TemporalBuffer(10);
        b.push(snap(10));
        b.push(snap(20));
        b.push(snap(30));
        assertEquals(20, b.findAtOrBefore(25).orElseThrow().tick());
        assertEquals(30, b.findAtOrBefore(30).orElseThrow().tick());
        assertTrue(b.findAtOrBefore(5).isEmpty());
    }

    @Test
    void dropFromRemovesTargetAndEverythingNewer() {
        TemporalBuffer b = new TemporalBuffer(10);
        for (long t = 1; t <= 5; t++) {
            b.push(snap(t));
        }
        b.dropFrom(3);
        assertEquals(2, b.size());
        assertEquals(2, b.newestTick());
        assertEquals(2, b.findAtOrBefore(4).orElseThrow().tick());
    }

    @Test
    void dropFromSurvivesWrapAround() {
        TemporalBuffer b = new TemporalBuffer(3);
        for (long t = 1; t <= 7; t++) {
            b.push(snap(t));
        }
        // в буфере 5, 6, 7 — причём запись уже завернулась через конец массива
        b.dropFrom(6);
        assertEquals(1, b.size());
        assertEquals(5, b.newestTick());
        b.push(snap(8));
        assertEquals(2, b.size());
        assertEquals(8, b.newestTick());
        assertEquals(5, b.oldestTick());
    }

    @Test
    void dropFromOlderThanEverythingEmptiesBuffer() {
        TemporalBuffer b = new TemporalBuffer(4);
        b.push(snap(10));
        b.push(snap(11));
        b.dropFrom(1);
        assertTrue(b.isEmpty());
    }

    @Test
    void clearEmptiesBuffer() {
        TemporalBuffer b = new TemporalBuffer(4);
        b.push(snap(1));
        b.clear();
        assertEquals(0, b.size());
        assertTrue(b.findAtOrBefore(1).isEmpty());
        assertThrows(IllegalStateException.class, b::newestTick);
    }
}
