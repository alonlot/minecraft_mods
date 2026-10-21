package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.SmoothedSceneClock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmoothedSceneClockTest {
    private static final int FRAMES = 3; // 60 fps at 20 tps
    private static final double EPS = 1e-9;

    @Test
    void firstTickSnapsToTheServer() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        assertFalse(c.running());
        assertTrue(c.tick(150));
        assertEquals(150, c.at(0), EPS);
        assertEquals(150.5, c.at(0.5f), EPS);
    }

    @Test
    void steadyServerIsFollowedExactly() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(0);
        for (int k = 1; k <= 200; k++) {
            assertFalse(c.tick(k));
            assertEquals(k, c.at(0), 1e-6);
            assertEquals(1, c.rate(), EPS);
        }
    }

    /** The 1 Hz time sync moving the client a tick either way: never a jump, never a snap, always forward. */
    @Test
    void absorbsServerSnapsWithoutJumps() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        int offset = 0;
        c.tick(0);
        double last = c.at((float) (FRAMES - 1) / FRAMES); // the last frame drawn before tick 1
        double maxStep = (1 + SmoothedSceneClock.MAX_SLEW) / FRAMES;
        double minStep = (1 - SmoothedSceneClock.MAX_SLEW) / FRAMES;
        for (int k = 1; k <= 400; k++) {
            if (k % 20 == 0) offset += (k / 20) % 2 == 0 ? -1 : 1;
            assertFalse(c.tick(k + offset), "a one-tick correction must not snap, t " + k);
            for (int f = 0; f < FRAMES; f++) {
                double now = c.at((float) f / FRAMES);
                double step = now - last;
                assertTrue(step <= maxStep + 1e-6, "frame step " + step + " at tick " + k);
                assertTrue(step >= minStep - 1e-6, "frame step " + step + " at tick " + k);
                last = now;
            }
        }
    }

    @Test
    void aOneTickErrorIsClosedWithinASecondAndAQuarter() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(0);
        for (int k = 1; k <= 10; k++) c.tick(k);
        // The server says one tick later from here on.
        int closedAt = -1;
        for (int k = 11; k <= 60; k++) {
            c.tick(k + 1);
            double err = (k + 1) - c.at(0);
            if (closedAt < 0 && Math.abs(err) < 0.05) closedAt = k;
        }
        assertTrue(closedAt > 0 && closedAt - 11 <= 25, "closed at " + closedAt);
        assertEquals(61, c.at(0) + 0, 0.01);
    }

    @Test
    void rateNeverLeavesTheSlewBand() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(0);
        for (int k = 1; k <= 50; k++) {
            c.tick(k + (k > 5 ? 8 : 0)); // a big but sub-snap error
            assertTrue(Math.abs(c.rate() - 1) <= SmoothedSceneClock.MAX_SLEW + EPS, "rate " + c.rate());
        }
    }

    @Test
    void aLargeErrorSnapsAtOnce() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(0);
        for (int k = 1; k <= 10; k++) c.tick(k);
        assertFalse(c.tick(11 + (int) SmoothedSceneClock.SNAP_TICKS - 1), "just under the threshold eases");
        SmoothedSceneClock d = new SmoothedSceneClock();
        d.tick(0);
        for (int k = 1; k <= 10; k++) d.tick(k);
        assertTrue(d.tick(11 + SmoothedSceneClock.SNAP_TICKS + 1));
        assertEquals(11 + SmoothedSceneClock.SNAP_TICKS + 1, d.at(0), EPS);
        assertEquals(1, d.rate(), EPS);
        // And backwards (the server fell far behind).
        assertTrue(d.tick(3));
        assertEquals(3, d.at(0), EPS);
    }

    @Test
    void framesWithinATickAreContinuousAcrossTheTick() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(0);
        c.tick(1);
        c.tick(3); // error 1: speeds up
        double endOfTick = c.at(1f);
        c.tick(4);
        assertEquals(endOfTick, c.at(0), 1e-9);
    }

    @Test
    void resetSnapsAgain() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(100);
        c.reset();
        assertFalse(c.running());
        assertTrue(c.tick(5));
        assertEquals(5, c.at(0), EPS);
    }
}
