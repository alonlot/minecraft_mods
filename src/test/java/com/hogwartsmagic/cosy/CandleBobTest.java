package com.hogwartsmagic.cosy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CandleBobTest {
    private static final long[] SEEDS = {0L, 1L, 123456789L, -42L, Long.MAX_VALUE};

    @Test
    void boundedOverAMinute() {
        for (long seed : SEEDS) {
            for (long t = 0; t <= 1200; t++) {
                for (int candle = 0; candle < 3; candle++) {
                    for (float partial : new float[]{0f, 0.5f, 0.99f}) {
                        double y = CandleBob.offsetY(t, partial, candle, seed);
                        float sway = CandleBob.swayDeg(t, partial, candle, seed);
                        assertTrue(Math.abs(y) <= 1.5 / 16.0 + 1e-9, "offset " + y + " at t=" + t);
                        assertTrue(Math.abs(sway) <= 4.0f + 1e-4f, "sway " + sway + " at t=" + t);
                    }
                }
            }
        }
    }

    @Test
    void candlesOutOfPhase() {
        for (long seed : SEEDS) {
            double a = CandleBob.offsetY(0, 0f, 0, seed);
            double b = CandleBob.offsetY(0, 0f, 1, seed);
            double c = CandleBob.offsetY(0, 0f, 2, seed);
            assertFalse(a == b && b == c, "the three candles should not move in lockstep (seed " + seed + ")");
        }
    }

    @Test
    void actuallyMoves() {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (long t = 0; t < 600; t++) {
            double y = CandleBob.offsetY(t, 0f, 0, 7L);
            min = Math.min(min, y);
            max = Math.max(max, y);
        }
        assertTrue(max - min > 1.0 / 16.0, "a candle should travel more than a pixel over ten seconds");
    }

    @Test
    void swayShiftFollowsTheAngle() {
        assertTrue(CandleBob.swayShift(0f, 0.4) == 0.0);
        assertTrue(CandleBob.swayShift(4f, 0.4) < 0.0);
        assertTrue(CandleBob.swayShift(-4f, 0.4) > 0.0);
    }
}
