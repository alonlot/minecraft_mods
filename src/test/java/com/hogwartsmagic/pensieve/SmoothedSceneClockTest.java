package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.SmoothedSceneClock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntToDoubleFunction;

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
        double lastRate = c.rate();
        double maxStep = (1 + SmoothedSceneClock.MAX_SPEED_UP) / FRAMES;
        for (int k = 1; k <= 400; k++) {
            if (k % 20 == 0) offset += (k / 20) % 2 == 0 ? -1 : 1;
            assertFalse(c.tick(k + offset), "a one-tick correction must not snap, t " + k);
            assertTrue(c.rate() >= 0.85, "a one-tick correction barely slows the clock: " + c.rate() + " at " + k);
            assertTrue(Math.abs(c.rate() - lastRate) <= SmoothedSceneClock.MAX_RATE_STEP + EPS, "rate step at " + k);
            lastRate = c.rate();
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
        for (int k = 11; k <= 120; k++) {
            c.tick(k + 1); // the server says one tick later from here on
            if (closedAt < 0 && c.at(0) == k + 1) closedAt = k;
        }
        assertTrue(closedAt > 0 && closedAt - 11 <= 60, "closed at " + closedAt);
        assertEquals(121, c.at(0), EPS);
        assertEquals(1, c.rate(), EPS);
    }

    @Test
    void anOrdinaryLagIsCaughtUpGently() {
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
        // After a steady stretch, so the jump does not read as a server racing back (which may lag further).
        SmoothedSceneClock d = new SmoothedSceneClock();
        d.tick(0);
        for (int k = 1; k <= 100; k++) d.tick(k);
        assertTrue(d.tick(101 + SmoothedSceneClock.SNAP_FORWARD_TICKS + 1));
        assertEquals(101 + SmoothedSceneClock.SNAP_FORWARD_TICKS + 1, d.at(0), EPS);
        assertEquals(1, d.rate(), EPS);
    }

    /** Ahead of the server: brake to a hold, never step back, unless the gap is a real desync. */
    @Test
    void aheadItHoldsAndSnapsBackOnlyPastTheBackLimit() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(0);
        for (int k = 1; k <= 30; k++) c.tick(k);
        double last = c.at(0);
        double lastRate = c.rate();
        double minRate = 1;
        for (int k = 31; k <= 200; k++) {
            assertFalse(c.tick(k - 20), "20 ticks ahead is held, not rewound");
            assertTrue(c.at(0) >= last, "never back, t " + k);
            assertTrue(Math.abs(c.rate() - lastRate) <= SmoothedSceneClock.MAX_RATE_STEP + EPS);
            minRate = Math.min(minRate, c.rate());
            last = c.at(0);
            lastRate = c.rate();
        }
        assertTrue(minRate < 0.6, "it slowed right down: " + minRate);
        assertEquals(180, c.at(0), 0.5);
        SmoothedSceneClock d = new SmoothedSceneClock();
        d.tick(0);
        for (int k = 1; k <= 150; k++) d.tick(k);
        assertTrue(d.tick(151 - SmoothedSceneClock.SNAP_BACK_TICKS - 2));
        assertEquals(151 - SmoothedSceneClock.SNAP_BACK_TICKS - 2, d.at(0), EPS);
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

    // ---- a vanilla-like client clock -----------------------------------------------------------------------

    /** What happened over a run, measured from tick {@code from} on (backward steps and rate steps: all along). */
    private record Run(double worstBack, double maxError, double minRate, double maxRate, double meanRate, int snaps,
                       int settled, double overshoot, double maxRateStep, int fullSpeedTicks) {
    }

    /** A run's setup: server rate per client tick, length, measuring start and the rest. */
    private static final class Sim {
        IntToDoubleFunction tps = k -> 20;
        int ticks = 800;
        int from = 100;
        int freezeFrom = -1;
        int freezeTo = -1;
        /** A sync arrives up to this many client ticks late, carrying the server's time when it was sent. */
        int jitter;
        long seed = 1;
        /** Overshoot (clock ahead of the server) is measured from here on. */
        int overFrom = Integer.MAX_VALUE;

        Sim tps(IntToDoubleFunction f) {
            tps = f;
            return this;
        }

        Sim ticks(int n, int measureFrom) {
            ticks = n;
            from = measureFrom;
            return this;
        }

        /**
         * The client adds 1 to its game time every client tick (20 a second). The server makes {@code tps} ticks a
         * second and every 20 server ticks sends its time, which the client takes when it arrives (vanilla's time
         * sync). While frozen the client's time stops ({@code /tick freeze}).
         */
        Run run() {
            SmoothedSceneClock c = new SmoothedSceneClock();
            Random random = new Random(seed);
            List<long[]> pending = new ArrayList<>();
            long raw = 0;
            long serverTicks = 0;
            double acc = 0;
            double last = Double.NaN;
            double lastRate = 1;
            double worstBack = 0;
            double maxError = 0;
            double minRate = Double.MAX_VALUE;
            double maxRate = -Double.MAX_VALUE;
            double sumRate = 0;
            double overshoot = 0;
            double maxRateStep = 0;
            int n = 0;
            int snaps = 0;
            int lastBad = from;
            int fullSpeed = 0;
            for (int k = 1; k <= ticks; k++) {
                boolean frozen = k >= freezeFrom && k < freezeTo;
                if (!frozen) {
                    raw++;
                    acc += tps.applyAsDouble(k) / 20.0;
                    while (acc >= 1) {
                        acc -= 1;
                        serverTicks++;
                        if (serverTicks % 20 == 0) {
                            pending.add(new long[]{k + (jitter > 0 ? random.nextInt(jitter + 1) : 0), serverTicks});
                        }
                    }
                }
                for (int i = 0; i < pending.size(); i++) {
                    if (pending.get(i)[0] <= k) {
                        raw = pending.remove(i--)[1];
                    }
                }
                double server = frozen ? raw : serverTicks + acc;
                boolean snapped = c.tick(raw);
                if (snapped && k > 1) snaps++;
                if (k > 1 && !snapped) maxRateStep = Math.max(maxRateStep, Math.abs(c.rate() - lastRate));
                lastRate = c.rate();
                for (int f = 0; f < FRAMES; f++) {
                    double now = c.at((float) f / FRAMES);
                    if (!Double.isNaN(last)) worstBack = Math.min(worstBack, now - last);
                    last = now;
                }
                if (k >= overFrom) overshoot = Math.max(overshoot, c.at(0) - server);
                if (k > from) {
                    double err = Math.abs(c.at(0) - server);
                    maxError = Math.max(maxError, err);
                    if (err >= 1) lastBad = k;
                    minRate = Math.min(minRate, c.rate());
                    maxRate = Math.max(maxRate, c.rate());
                    sumRate += c.rate();
                    if (c.serverRate() == 1) fullSpeed++;
                    n++;
                }
            }
            return new Run(worstBack, maxError, minRate, maxRate, sumRate / n, snaps, lastBad - from, overshoot,
                    maxRateStep, fullSpeed);
        }
    }

    private static Run run(double tps) {
        return new Sim().tps(k -> tps).run();
    }

    private static void smooth(Run r, String what) {
        assertEquals(0, r.worstBack(), EPS, "never a step back: " + what);
        assertTrue(r.maxRateStep() <= SmoothedSceneClock.MAX_RATE_STEP + EPS,
                "rate step " + r.maxRateStep() + ": " + what);
    }

    @Test
    void aServerAt18TpsSlowsTheClockWithoutRewinding() {
        Run r = new Sim().tps(k -> 18).ticks(1200, 400).run();
        smooth(r, "18 tps");
        assertEquals(0, r.snaps());
        assertTrue(r.maxError() < 1.5, "error " + r.maxError());
        assertEquals(0.9, r.meanRate(), 0.02);
        assertTrue(r.minRate() > 0.8 && r.maxRate() < 1.0, "a steady speed: " + r.minRate() + ".." + r.maxRate());
    }

    @Test
    void aServerAt15TpsSlowsTheClockWithoutRewinding() {
        Run r = new Sim().tps(k -> 15).ticks(1200, 400).run();
        smooth(r, "15 tps");
        assertEquals(0, r.snaps());
        assertTrue(r.maxError() < 1.5, "error " + r.maxError());
        assertEquals(0.75, r.meanRate(), 0.02);
        assertTrue(r.minRate() > 0.65 && r.maxRate() < 0.85, "a steady speed: " + r.minRate() + ".." + r.maxRate());
    }

    @Test
    void aFullSpeedServerIsUntouched() {
        Run r = run(20);
        assertEquals(0, r.worstBack(), EPS);
        assertEquals(0, r.maxError(), EPS);
        assertEquals(1, r.minRate(), EPS);
        assertEquals(1, r.maxRate(), EPS);
    }

    /** Sync packets arriving 0 to 1/2/3 ticks late at full speed: the speed barely moves. */
    @Test
    void aFullSpeedServerWithJitterBarelyMovesTheSpeed() {
        for (int j = 1; j <= 3; j++) {
            Sim s = new Sim().tps(k -> 20).ticks(4000, 100);
            s.jitter = j;
            Run r = s.run();
            smooth(r, "jitter " + j);
            assertEquals(0, r.snaps());
            assertTrue(r.minRate() >= 0.8 && r.maxRate() <= 1 + SmoothedSceneClock.MAX_SPEED_UP + EPS,
                    "jitter " + j + ": " + r.minRate() + ".." + r.maxRate());
        }
    }

    /** A slow server whose syncs jitter: no false "back at full speed", and the speed stays steady. */
    @Test
    void aSlowServerWithJitterIsNotTakenForFullSpeed() {
        for (int j = 2; j <= 3; j++) {
            for (long seed = 1; seed <= 5; seed++) {
                Sim s = new Sim().tps(k -> 15).ticks(4000, 200);
                s.jitter = j;
                s.seed = seed;
                Run r = s.run();
                smooth(r, "jitter " + j);
                assertEquals(0, r.snaps());
                assertEquals(0, r.fullSpeedTicks(), "taken for full speed, jitter " + j + " seed " + seed);
                assertTrue(r.minRate() > 0.6, "rate " + r.minRate() + ", jitter " + j + " seed " + seed);
            }
        }
    }

    /** {@code /tick freeze}: the clock brakes to a hold, never rewinds, and runs on after the thaw. */
    @Test
    void aFrozenTickRateHoldsTheClock() {
        Sim s = new Sim().tps(k -> 20).ticks(700, 100);
        s.freezeFrom = 200;
        s.freezeTo = 400;
        Run r = s.run();
        smooth(r, "freeze");
        assertEquals(0, r.snaps());
        assertTrue(r.maxError() < 12, "error " + r.maxError());
        assertTrue(r.settled() < 400, "settled " + r.settled());
        SmoothedSceneClock c = new SmoothedSceneClock();
        for (int k = 0; k <= 50; k++) c.tick(k);
        for (int k = 0; k < 30; k++) c.tick(50);
        assertEquals(0, c.rate(), EPS, "held");
        assertTrue(c.at(0) - 50 < 11, "held at " + c.at(0));
        for (int k = 51; k <= 200; k++) c.tick(k);
        assertEquals(200, c.at(0), 0.05);
    }

    /**
     * A slow server back at full speed: its syncs no longer move the client, so the clock learns it only when the
     * pull-back it expects is overdue. The lag that builds up meanwhile is bounded and then closed.
     */
    @Test
    void aServerRecoveringFrom15TpsIsCaughtUp() {
        for (int at = 400; at < 427; at += 3) {
            int sw = at;
            Run r = new Sim().tps(k -> k < sw ? 15 : 20).ticks(900, sw).run();
            smooth(r, "switch " + sw);
            assertEquals(0, r.snaps(), "switch " + sw);
            assertTrue(r.maxError() < 12, "error " + r.maxError() + ", switch " + sw);
            assertTrue(r.settled() <= 120, "within 1 tick after " + r.settled() + " ticks, switch " + sw);
            assertTrue(r.maxRate() <= 1 + SmoothedSceneClock.MAX_CATCH_UP + EPS, "rate " + r.maxRate());
        }
    }

    @Test
    void aServerRecoveringFrom18TpsIsCaughtUp() {
        for (int at = 400; at < 423; at += 2) {
            int sw = at;
            Run r = new Sim().tps(k -> k < sw ? 18 : 20).ticks(900, sw).run();
            smooth(r, "switch " + sw);
            assertEquals(0, r.snaps(), "switch " + sw);
            assertTrue(r.maxError() < 4.5, "error " + r.maxError() + ", switch " + sw);
            assertTrue(r.settled() <= 100, "within 1 tick after " + r.settled() + " ticks, switch " + sw);
        }
    }

    /**
     * A singleplayer hitch: the integrated server stops for {@code d} ticks while the client keeps ticking, then runs
     * at double speed until it has caught up. The clock cannot know during the stop, so it runs on. Once the
     * pull-back comes it brakes to a hold instead of rewinding, then follows the server racing back. It never snaps,
     * never changes speed faster than the limit, and does not overshoot the server when the race ends.
     */
    @Test
    void aSingleplayerHitchIsHeldThenFollowedWithoutOvershoot() {
        for (int d : new int[]{10, 20, 40, 60}) {
            int h = d;
            Sim s = new Sim().tps(k -> k < 300 ? 20 : k < 300 + h ? 0 : k < 300 + 2 * h ? 40 : 20).ticks(1000, 300);
            s.overFrom = 300 + 2 * d;
            Run r = s.run();
            smooth(r, "hitch " + d);
            assertEquals(0, r.snaps(), "hitch " + d);
            assertTrue(r.overshoot() < 1, "overshoot " + r.overshoot() + ", hitch " + d);
            assertTrue(r.maxError() <= d + 1, "error " + r.maxError() + ", hitch " + d);
            assertTrue(r.settled() <= 2 * d + 100, "within 1 tick after " + r.settled() + " ticks, hitch " + d);
            assertTrue(r.maxRate() <= SmoothedSceneClock.MAX_FOLLOW_RATE + EPS, "rate " + r.maxRate());
        }
    }

    /** A full-speed server going slow: the clock brakes to it without rewinding. */
    @Test
    void aServerDroppingTo15TpsIsFollowedDown() {
        Run r = new Sim().tps(k -> k < 400 ? 20 : 15).ticks(1600, 400).run();
        smooth(r, "20 to 15");
        assertEquals(0, r.snaps());
        assertTrue(r.maxError() < 12, "error " + r.maxError());
    }
}
