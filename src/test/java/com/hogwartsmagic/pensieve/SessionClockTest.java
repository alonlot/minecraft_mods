package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.SessionClock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionClockTest {
    @Test
    void joinerSeesCurrentMoment() {
        assertEquals(150.5f, SessionClock.elapsed(1000, 1150, 0.5f), 1e-4f);
    }

    @Test
    void neverNegative() {
        assertEquals(0f, SessionClock.elapsed(1000, 990, 0.5f), 1e-6f);
    }

    @Test
    void finishedAtLength() {
        assertFalse(SessionClock.finished(0, 99, 100));
        assertTrue(SessionClock.finished(0, 100, 100));
    }
}
