package com.hogwartsmagic.locket;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AmbushScheduleTest {
    @Test void fourWavesOfFourOverTwentySeconds() {
        int total = 0;
        for (int t = 0; t <= 400; t++) total += AmbushSchedule.spawnsDueAt(t);
        assertEquals(16, total);
        assertEquals(4, AmbushSchedule.spawnsDueAt(0));
        assertEquals(4, AmbushSchedule.spawnsDueAt(300));
        assertEquals(0, AmbushSchedule.spawnsDueAt(400));
        assertEquals(0, AmbushSchedule.spawnsDueAt(50));
    }
    @Test void endsWhenAllDeadOrCaveEmpty() {
        assertFalse(AmbushSchedule.isOver(8, 8, true));
        assertFalse(AmbushSchedule.isOver(8, 0, true)); // more waves still to come
        assertTrue(AmbushSchedule.isOver(16, 0, true));
        assertTrue(AmbushSchedule.isOver(8, 5, false));
    }
}
