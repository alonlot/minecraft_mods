package com.hogwartsmagic.spell.disable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TetherMathTest {
    @Test
    void insideTheLeashVelocityIsUntouched() {
        assertArrayEquals(new double[]{0.3, 0.1, -0.2},
                TetherMath.correctedVelocity(1, 0, 0, 2, 0.3, 0.1, -0.2), 1e-9);
    }

    @Test
    void outwardMotionIsCancelledAndPulledBack() {
        assertArrayEquals(new double[]{-0.4, 0, 0},
                TetherMath.correctedVelocity(3, 0, 0, 2, 0.5, 0, 0), 1e-9);
    }

    @Test
    void sidewaysMotionSurvives() {
        assertArrayEquals(new double[]{-0.4, 0, 0.3},
                TetherMath.correctedVelocity(3, 0, 0, 2, 0, 0, 0.3), 1e-9);
    }

    @Test
    void inwardMotionIsKept() {
        assertArrayEquals(new double[]{-0.6, 0, 0},
                TetherMath.correctedVelocity(3, 0, 0, 2, -0.2, 0, 0), 1e-9);
    }

    @Test
    void pullIsCapped() {
        assertArrayEquals(new double[]{-0.6, 0, 0},
                TetherMath.correctedVelocity(10, 0, 0, 2, 0, 0, 0), 1e-9);
    }

    @Test
    void outsideAndSnapThresholds() {
        assertTrue(TetherMath.isOutside(2.01, 0, 0, 2));
        assertFalse(TetherMath.isOutside(2.0, 0, 0, 2));
        assertTrue(TetherMath.shouldSnap(4.01, 0, 0, 2));
        assertFalse(TetherMath.shouldSnap(3.99, 0, 0, 2));
    }

    @Test
    void snapPointSitsOnTheLeash() {
        assertArrayEquals(new double[]{10, 64, 12},
                TetherMath.snapPoint(10, 64, 10, 0, 0, 5, 2), 1e-9);
    }

    @Test
    void zeroLengthIsSafe() {
        assertArrayEquals(new double[]{0.1, 0.2, 0.3},
                TetherMath.correctedVelocity(0, 0, 0, 0, 0.1, 0.2, 0.3), 1e-9);
        assertArrayEquals(new double[]{5, 6, 7},
                TetherMath.snapPoint(5, 6, 7, 0, 0, 0, 2), 1e-9);
    }
}
