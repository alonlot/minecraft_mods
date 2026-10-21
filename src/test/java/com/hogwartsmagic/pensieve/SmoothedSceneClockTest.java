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
            assertEquals(k, c.at(0), EPS);
            assertEquals(1, c.rate(), EPS);
        }
    }

    /** The 1 Hz time sync moving the client a tick either way: never a jump, never a snap, always forward. */
    @Test
    void absorbsOneTickSyncsWithoutJumps() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        int offset = 0;
        c.tick(0);
        double last = c.at((float) (FRAMES - 1) / FRAMES); // the last frame drawn before tick 1
        double maxStep = (1 + SmoothedSceneClock.MAX_SPEED_UP) / FRAMES;
        for (int k = 1; k <= 400; k++) {
            if (k % 20 == 0) offset += (k / 20) % 2 == 0 ? -1 : 1;
            assertFalse(c.tick(k + offset), "a one-tick correction must not snap, t " + k);
            assertTrue(c.rate() >= 0.85, "a one-tick correction barely slows the clock: " + c.rate() + " at " + k);
            for (int f = 0; f < FRAMES; f++) {
                double now = c.at((float) f / FRAMES);
                double step = now - last;
                assertTrue(step <= maxStep + SmoothedSceneClock.EXACT_TICKS, "frame step " + step + " at tick " + k);
                assertTrue(step >= 0, "frame step " + step + " at tick " + k);
                last = now;
            }
        }
    }

    /** A forward correction ends exactly on the server's time, so tick work lands on whole ticks again. */
    @Test
    void aOneTickErrorIsClosedExactly() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(0);
        for (int k = 1; k <= 10; k++) c.tick(k);
        int closedAt = -1;
        for (int k = 11; k <= 60; k++) {
            c.tick(k + 1); // the server says one tick later from here on
            if (closedAt < 0 && c.at(0) == k + 1) closedAt = k;
        }
        assertTrue(closedAt > 0 && closedAt - 11 <= 30, "closed at " + closedAt);
        assertEquals(61, c.at(0), EPS);
        assertEquals(1, c.rate(), EPS);
    }

    @Test
    void speedUpNeverPassesTheCap() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(0);
        for (int k = 1; k <= 50; k++) {
            c.tick(k + (k > 5 ? 8 : 0)); // a big but sub-snap lag
            assertTrue(c.rate() <= 1 + SmoothedSceneClock.MAX_SPEED_UP + EPS, "rate " + c.rate());
            assertTrue(c.rate() >= 0, "rate " + c.rate());
        }
    }

    @Test
    void aLargeLagSnapsForwardAtOnce() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(0);
        for (int k = 1; k <= 10; k++) c.tick(k);
        assertFalse(c.tick(11 + (int) SmoothedSceneClock.SNAP_FORWARD_TICKS - 1), "just under the threshold eases");
        SmoothedSceneClock d = new SmoothedSceneClock();
        d.tick(0);
        for (int k = 1; k <= 10; k++) d.tick(k);
        assertTrue(d.tick(11 + SmoothedSceneClock.SNAP_FORWARD_TICKS + 1));
        assertEquals(11 + SmoothedSceneClock.SNAP_FORWARD_TICKS + 1, d.at(0), EPS);
        assertEquals(1, d.rate(), EPS);
    }

    /** Ahead of the server: slow down or hold, never step back, unless the gap is a real desync. */
    @Test
    void aheadItHoldsAndSnapsBackOnlyPastTheBackLimit() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(0);
        for (int k = 1; k <= 30; k++) c.tick(k);
        double before = c.at(0);
        assertFalse(c.tick(31 - 20), "20 ticks ahead is held, not rewound");
        assertTrue(c.at(0) >= before);
        assertTrue(c.rate() < 0.2, "rate " + c.rate());
        SmoothedSceneClock d = new SmoothedSceneClock();
        d.tick(0);
        for (int k = 1; k <= 60; k++) d.tick(k);
        assertTrue(d.tick(61 - SmoothedSceneClock.SNAP_BACK_TICKS - 2));
        assertEquals(61 - SmoothedSceneClock.SNAP_BACK_TICKS - 2, d.at(0), EPS);
    }

    @Test
    void framesWithinATickAreContinuousAcrossTheTick() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(0);
        c.tick(1);
        c.tick(3); // behind by one: speeds up
        assertTrue(c.rate() > 1);
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

    // ---- a vanilla-like client clock on a slow server -------------------------------------------------------

    /** What happened over a run: the largest backward frame step, the worst error against the server, the rates. */
    private record Run(double worstBack, double maxError, double minRate, double maxRate, double meanRate, int snaps) {
    }

    /**
     * The client adds 1 to its game time every client tick (20 a second). The server makes {@code tps} ticks a second
     * and every 20 server ticks sets the client's time to its own (vanilla's time sync). Ticks {@code freezeFrom}
     * to {@code freezeTo} the client's time stops ({@code /tick freeze}). Measured after a 100-tick settle.
     */
    private static Run run(double tps, int ticks, int freezeFrom, int freezeTo) {
        SmoothedSceneClock c = new SmoothedSceneClock();
        long raw = 0;
        long serverTicks = 0;
        double acc = 0;
        double last = Double.NaN;
        double worstBack = 0;
        double maxError = 0;
        double minRate = Double.MAX_VALUE;
        double maxRate = -Double.MAX_VALUE;
        double sumRate = 0;
        int n = 0;
        int snaps = 0;
        for (int k = 1; k <= ticks; k++) {
            boolean frozen = k >= freezeFrom && k < freezeTo;
            if (!frozen) {
                raw++;
                acc += tps / 20.0;
                while (acc >= 1) {
                    acc -= 1;
                    serverTicks++;
                    if (serverTicks % 20 == 0) raw = serverTicks;
                }
            }
            double server = frozen ? raw : serverTicks + acc;
            if (c.tick(raw) && k > 1) snaps++;
            for (int f = 0; f < FRAMES; f++) {
                double now = c.at((float) f / FRAMES);
                if (!Double.isNaN(last)) worstBack = Math.min(worstBack, now - last);
                last = now;
            }
            if (k > 100) {
                maxError = Math.max(maxError, Math.abs(c.at(0) - server));
                minRate = Math.min(minRate, c.rate());
                maxRate = Math.max(maxRate, c.rate());
                sumRate += c.rate();
                n++;
            }
        }
        return new Run(worstBack, maxError, minRate, maxRate, sumRate / n, snaps);
    }

    @Test
    void aServerAt18TpsSlowsTheClockWithoutRewinding() {
        Run r = run(18, 800, -1, -1);
        assertEquals(0, r.worstBack(), EPS, "never a step back");
        assertEquals(0, r.snaps());
        assertTrue(r.maxError() < 2.5, "error " + r.maxError());
        assertEquals(0.9, r.meanRate(), 0.02);
        assertTrue(r.minRate() > 0.7 && r.maxRate() < 1.0, "a steady speed: " + r.minRate() + ".." + r.maxRate());
    }

    @Test
    void aServerAt15TpsSlowsTheClockWithoutRewinding() {
        Run r = run(15, 800, -1, -1);
        assertEquals(0, r.worstBack(), EPS, "never a step back");
        assertEquals(0, r.snaps());
        assertTrue(r.maxError() < 6, "error " + r.maxError());
        assertEquals(0.75, r.meanRate(), 0.02);
        assertTrue(r.minRate() > 0.45 && r.maxRate() < 0.95, "a steady speed: " + r.minRate() + ".." + r.maxRate());
    }

    @Test
    void aFullSpeedServerIsUntouched() {
        Run r = run(20, 800, -1, -1);
        assertEquals(0, r.worstBack(), EPS);
        assertEquals(0, r.maxError(), EPS);
        assertEquals(1, r.minRate(), EPS);
        assertEquals(1, r.maxRate(), EPS);
    }

    /** {@code /tick freeze}: the clock holds within a couple of ticks, never rewinds, and runs on after the thaw. */
    @Test
    void aFrozenTickRateHoldsTheClock() {
        Run r = run(20, 600, 200, 400);
        assertEquals(0, r.worstBack(), EPS, "never a step back");
        assertEquals(0, r.snaps());
        assertTrue(r.maxError() < 2.5, "error " + r.maxError());
        SmoothedSceneClock c = new SmoothedSceneClock();
        for (int k = 0; k <= 50; k++) c.tick(k);
        for (int k = 0; k < 30; k++) c.tick(50);
        assertEquals(0, c.rate(), EPS, "held");
        double held = c.at(0);
        assertTrue(held - 50 < 2.5, "held " + held);
        for (int k = 51; k <= 120; k++) c.tick(k);
        assertEquals(120, c.at(0), 0.05);
    }
}
