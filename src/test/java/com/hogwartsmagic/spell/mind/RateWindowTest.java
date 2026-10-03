package com.hogwartsmagic.spell.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateWindowTest {
    @Test
    void allowsCapacityPerWindowThenRefuses() {
        RateWindow window = new RateWindow(3, 20);
        assertTrue(window.tryAcquire(100));
        assertTrue(window.tryAcquire(101));
        assertTrue(window.tryAcquire(102));
        assertFalse(window.tryAcquire(103));
    }

    @Test
    void aNewWindowRefills() {
        RateWindow window = new RateWindow(1, 20);
        assertTrue(window.tryAcquire(100));
        assertFalse(window.tryAcquire(119));
        assertTrue(window.tryAcquire(120));
    }

    @Test
    void timeGoingBackwardsStartsAFreshWindow() {
        RateWindow window = new RateWindow(1, 20);
        assertTrue(window.tryAcquire(500));
        assertTrue(window.tryAcquire(10));
    }
}
