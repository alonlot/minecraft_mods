package com.hogwartsmagic.ambience;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hall doors' swing: opens for a visitor over 30 ticks, holds 100 after the last one leaves, then closes. */
class DoorSwingTest {
    private static long run(DoorSwing.State s, boolean near, long from, int ticks) {
        long t = from;
        for (int i = 0; i < ticks; i++) {
            DoorSwing.tick(s, near, t++);
        }
        return t;
    }

    @Test
    void opensOverThirtyTicksWhenSomeoneIsNear() {
        DoorSwing.State s = new DoorSwing.State();
        long t = run(s, true, 1000, DoorSwing.OPEN_TICKS - 1);
        assertTrue(s.progress() > 0f && s.progress() < 1f, "still swinging, progress " + s.progress());
        run(s, true, t, 1);
        assertEquals(1f, s.progress(), 1e-4f);
    }

    @Test
    void staysShutWithNobodyNear() {
        DoorSwing.State s = new DoorSwing.State();
        run(s, false, 0, 500);
        assertEquals(0f, s.progress(), 0f);
    }

    @Test
    void holdsOpenThenClosesAfterTheLastVisitorLeaves() {
        DoorSwing.State s = new DoorSwing.State();
        long t = run(s, true, 2000, 40);
        long left = t;
        t = run(s, false, t, DoorSwing.HOLD_TICKS);
        assertEquals(1f, s.progress(), 1e-4f, "held open for " + DoorSwing.HOLD_TICKS + " ticks after the last visitor");
        t = run(s, false, t, 1 + DoorSwing.OPEN_TICKS);
        assertEquals(0f, s.progress(), 1e-4f, "shut again " + (t - left) + " ticks after they left");
    }

    @Test
    void aReturningVisitorKeepsItOpen() {
        DoorSwing.State s = new DoorSwing.State();
        long t = run(s, true, 0, 40);
        t = run(s, false, t, 90);
        t = run(s, true, t, 1);
        run(s, false, t, 99);
        assertEquals(1f, s.progress(), 1e-4f);
    }

    @Test
    void easingIsFlatAtBothEndsAndMonotonic() {
        assertEquals(0f, DoorSwing.eased(0f), 0f);
        assertEquals(1f, DoorSwing.eased(1f), 0f);
        float last = 0f;
        for (float p = 0.05f; p <= 1f; p += 0.05f) {
            float e = DoorSwing.eased(p);
            assertTrue(e >= last);
            last = e;
        }
        assertTrue(DoorSwing.eased(0.1f) < 0.1f, "slow start");
    }

    @Test
    void thePairOpensOppositeWaysFromTheClosedYaw() {
        assertEquals(180f, DoorSwing.yaw(180f, false, 0f), 1e-4f);
        assertEquals(180f, DoorSwing.yaw(180f, true, 0f), 1e-4f);
        assertEquals(180f - DoorSwing.OPEN_DEGREES, DoorSwing.yaw(180f, false, 1f), 1e-4f);
        assertEquals(180f + DoorSwing.OPEN_DEGREES, DoorSwing.yaw(180f, true, 1f), 1e-4f);
    }

    @Test
    void leafMiddlesMeetInTheDoorway() {
        // The castle's pair: hinges at 65.5 (plain) and 57.5 (mirrored), both closed at yaw 180: the leaves meet at 61.5.
        double[] plain = DoorSwing.leafMid(65.5, 110.0, 180f, false);
        double[] mirrored = DoorSwing.leafMid(57.5, 110.0, 180f, true);
        assertEquals(63.5, plain[0], 1e-6);
        assertEquals(59.5, mirrored[0], 1e-6);
        assertEquals(110.0, plain[1], 1e-6);
    }
}
