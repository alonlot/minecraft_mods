package com.hogwartsmagic.pensieve.capture;

import org.junit.jupiter.api.Test;

import java.util.function.LongToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowRulesTest {
    private static final double[] CENTRE = {0, 64, 0};

    /** Owner sampled every tick in [from, to]. */
    private static long[] ticks(long from, long to) {
        long[] t = new long[(int) (to - from + 1)];
        for (int i = 0; i < t.length; i++) {
            t[i] = from + i;
        }
        return t;
    }

    /** Owner at (x(tick), 64, 0). */
    private static double[] xyz(long[] ticks, LongToDoubleFunction x) {
        double[] a = new double[ticks.length * 3];
        for (int i = 0; i < ticks.length; i++) {
            a[i * 3] = x.applyAsDouble(ticks[i]);
            a[i * 3 + 1] = 64;
        }
        return a;
    }

    @Test
    void fullWindowWhenNothingHappened() {
        long[] t = ticks(0, 5000);
        assertEquals(3801, WindowRules.start(5000, 1200, 0, -1, t, xyz(t, k -> 1), CENTRE, 16));
    }

    @Test
    void startsAfterTeleport() {
        long[] t = ticks(0, 5000);
        assertEquals(4501, WindowRules.start(5000, 1200, 0, 4500, t, xyz(t, k -> 1), CENTRE, 16));
    }

    @Test
    void startsAfterJump() {
        long[] t = ticks(0, 5000);
        assertEquals(4201, WindowRules.start(5000, 1200, 0, -1, t, xyz(t, k -> k <= 4200 ? 0 : 10), CENTRE, 16));
    }

    @Test
    void startsWhenOwnerArrived() {
        long[] t = ticks(0, 5000);
        // 100 blocks away up to tick 4300, then 10 blocks from the centre: the far rule gives 4301
        // (the 90 block step at 4301 is also a jump and agrees).
        assertEquals(4301, WindowRules.start(5000, 1200, 0, -1, t, xyz(t, k -> k <= 4300 ? 100 : 10), CENTRE, 16));
    }

    @Test
    void farRuleAloneWithoutJump() {
        // walks in 5 blocks per tick from 100 away to 10: no step over the jump limit
        long[] t = ticks(0, 5000);
        double[] a = xyz(t, k -> k < 4000 ? 100 : Math.max(10, 100 - (k - 4000) * 5));
        // far limit is 16 + 48 = 64: last far sample is the one at 100 - 5n > 64, n = 7 -> tick 4007 (x = 65)
        assertEquals(4008, WindowRules.start(5000, 1200, 0, -1, t, a, CENTRE, 16));
    }

    @Test
    void logOverflowMovesStart() {
        long[] t = ticks(0, 5000);
        assertEquals(4700, WindowRules.start(5000, 1200, 4700, -1, t, xyz(t, k -> 1), CENTRE, 16));
    }

    @Test
    void ownerFirstSampleLimitsStart() {
        long[] t = ticks(4000, 5000);
        assertEquals(4000, WindowRules.start(5000, 1200, 0, -1, t, xyz(t, k -> 1), CENTRE, 16));
    }

    @Test
    void tooSoon() {
        assertFalse(WindowRules.usable(4980, 5000));
        assertTrue(WindowRules.usable(4961, 5000));
    }
}
