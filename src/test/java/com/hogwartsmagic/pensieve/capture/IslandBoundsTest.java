package com.hogwartsmagic.pensieve.capture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandBoundsTest {
    @Test
    void clampsToSphere() {
        assertArrayEquals(new double[] {32, 0, 0}, IslandBounds.clamp(40, 0, 0, 0, 0, 0, 32), 1e-9);
    }

    @Test
    void insideUnchanged() {
        assertArrayEquals(new double[] {10, 5, 0}, IslandBounds.clamp(10, 5, 0, 0, 0, 0, 32), 1e-9);
    }

    @Test
    void outsideLimit() {
        assertTrue(IslandBounds.outside(37, 0, 0, 0, 0, 0, 36));
        assertFalse(IslandBounds.outside(36, 0, 0, 0, 0, 0, 36));
    }
}
