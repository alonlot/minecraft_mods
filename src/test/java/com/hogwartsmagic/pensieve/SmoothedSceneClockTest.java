package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.SmoothedSceneClock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The memory scene clock against a simulated vanilla server ({@link SceneClockSim}). Error bounds are in ticks and
 * grow with the sync jitter {@code j} (a sync up to {@code j} ticks late): the client's own time is that stale too,
 * so no clock can do better than the delay.
 */
class SmoothedSceneClockTest {
    private static final double EPS = 1e-9;

    // ---- the clock on its own -------------------------------------------------------------------------------

    @Test
    void firstTickSnapsToTheClientTime() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        assertFalse(c.running());
        assertTrue(c.tick(150, false));
        assertEquals(150, c.at(0), EPS);
        assertEquals(150.5, c.at(0.5f), EPS);
    }

    /** A 20 tps server: every sync agrees with the client, and the clock runs at exactly 1 on exactly the server. */
    @Test
    void aFullSpeedServerIsFollowedExactly() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(0, false);
        for (int k = 1; k <= 2000; k++) {
            assertFalse(c.tick(k, k % 20 == 7));
            assertEquals(k, c.at(0), 0.0);
            assertEquals(1, c.rate(), 0.0);
            assertEquals(1, c.serverRate(), 0.0);
        }
    }

    @Test
    void framesAreContinuousAcrossATick() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(0, false);
        for (int k = 1; k <= 30; k++) c.tick(k, k % 20 == 0);
        c.tick(33, true); // the server says two ticks later: speeds up
        assertTrue(c.rate() > 1, "rate " + c.rate());
        double endOfTick = c.at(1f);
        c.tick(34, false);
        assertEquals(endOfTick, c.at(0), 1e-9);
    }

    @Test
    void resetSnapsAgain() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(100, false);
        c.tick(101, false);
        c.reset();
        assertFalse(c.running());
        assertTrue(c.tick(5, false));
        assertEquals(5, c.at(0), EPS);
        assertEquals(1, c.rate(), EPS);
    }

    /** A one-tick correction is eased in and then landed on exactly, so tick work falls on whole ticks again. */
    @Test
    void aOneTickCorrectionEndsOnWholeTicks() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(0, false);
        for (int k = 1; k <= 100; k++) c.tick(k, k % 20 == 0);
        double lastRate = c.rate();
        for (int k = 101; k <= 400; k++) {
            assertFalse(c.tick(k + 1, k % 20 == 0)); // the server is one tick later from here on
            assertTrue(Math.abs(c.rate() - lastRate) <= SmoothedSceneClock.MAX_RATE_STEP + EPS);
            assertTrue(c.rate() <= 1.1, "a gentle catch-up: " + c.rate());
            lastRate = c.rate();
        }
        assertEquals(401, c.at(0), 0.0);
        assertEquals(1, c.rate(), 0.0);
    }

    @Test
    void aLargeLagSnapsForward() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(0, false);
        for (int k = 1; k <= 100; k++) c.tick(k, k % 20 == 0);
        double jump = SmoothedSceneClock.SNAP_FORWARD_TICKS + 5;
        assertTrue(c.tick(101 + jump, true));
        assertEquals(101 + jump, c.at(0), EPS);
        SmoothedSceneClock d = new SmoothedSceneClock();
        d.tick(0, false);
        for (int k = 1; k <= 100; k++) d.tick(k, k % 20 == 0);
        assertFalse(d.tick(101 + SmoothedSceneClock.SNAP_FORWARD_TICKS - 5, true), "under the limit it eases");
    }

    /** Ahead of the server: slow to a hold, never step back, and snap back only past the back limit. */
    @Test
    void aheadItHoldsAndSnapsBackOnlyPastTheLimit() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        c.tick(0, false);
        for (int k = 1; k <= 100; k++) c.tick(k, k % 20 == 0);
        double last = c.at(0);
        double minRate = 1;
        for (int k = 101; k <= 400; k++) {
            assertFalse(c.tick(k - 40, k % 20 == 0), "40 ticks ahead is held, not rewound");
            assertTrue(c.at(0) >= last, "never back, t " + k);
            minRate = Math.min(minRate, c.rate());
            last = c.at(0);
        }
        assertTrue(minRate < 0.1, "it slowed right down: " + minRate);
        assertEquals(360, c.at(0), 0.5);
        SmoothedSceneClock d = new SmoothedSceneClock();
        d.tick(0, false);
        for (int k = 1; k <= 200; k++) d.tick(k, k % 20 == 0);
        double back = 201 - SmoothedSceneClock.SNAP_BACK_TICKS - 5;
        assertTrue(d.tick(back, true));
        assertEquals(back, d.at(0), EPS);
    }

    /**
     * After a slow stretch the server is back at full speed. Its syncs move nothing, but each is a sample: the
     * estimate is near full speed within three of them and exactly at it once the slow samples have left the fit
     * window, however long the slow stretch was (round 3 was blind to it for hundreds of ticks).
     */
    @Test
    void syncsThatMoveNothingShowTheServerKeepingPace() {
        for (int slow : new int[]{100, 400, 1000}) {
            SmoothedSceneClock c = new SmoothedSceneClock();
            c.tick(0, false);
            double server = 0;
            long raw = 0;
            int syncs = 0;
            int k = 1;
            for (; k <= slow; k++) {
                raw++;
                double before = server;
                server += 0.75;
                if (Math.floor(server / 20) > Math.floor(before / 20)) raw = (long) server;
                c.tick(raw, raw == (long) server && Math.floor(server / 20) > Math.floor(before / 20));
            }
            assertEquals(0.75, c.serverRate(), 0.05, "slow " + slow);
            raw = (long) Math.floor(server);
            for (; syncs < 3; k++) {
                raw++;
                boolean sync = raw % 20 == 0;
                if (sync) syncs++;
                c.tick(raw, sync);
            }
            assertEquals(1, c.serverRate(), 0.15, "slow " + slow);
            for (int i = 0; i < SmoothedSceneClock.FIT_WINDOW_TICKS; i++) {
                raw++;
                c.tick(raw, raw % 20 == 0);
            }
            assertEquals(1, c.serverRate(), 0.0, "slow " + slow);
        }
    }

    // ---- the clock against a simulated server -----------------------------------------------------------------

    private static void smooth(SceneClockSim.Result r, String what) {
        assertEquals(0, r.worstBack(), EPS, "never a step back: " + what);
        assertTrue(r.maxRateStep() <= SmoothedSceneClock.MAX_RATE_STEP + EPS, "rate step " + r.maxRateStep() + ": " + what);
        assertTrue(r.minRate() >= 0 && r.maxRate() <= SmoothedSceneClock.MAX_RATE + EPS, "rate range: " + what);
    }

    private static void noSnap(SceneClockSim.Result r, String what) {
        assertEquals(0, r.snaps(), "snapped: " + what);
    }

    @Test
    void aSteady20TpsServerPlaysAtExactlyOne() {
        SceneClockSim.Result r = new SceneClockSim().ticks(4000, 1).run();
        smooth(r, "20 tps");
        noSnap(r, "20 tps");
        assertEquals(1, r.minRate(), 0.0);
        assertEquals(1, r.maxRate(), 0.0);
        assertEquals(0, r.maxAhead(), 0.0);
        assertEquals(0, r.maxBehind(), 0.0);
    }

    /** 20, 18, 15, 12 and 10 tps, sync jitter 0-3: within a tick (plus the delay), at the server's own speed. */
    @Test
    void aSteadySlowServerIsTrackedClosely() {
        for (double tps : new double[]{20, 18, 15, 12, 10}) {
            for (int j = 0; j <= 3; j++) {
                for (long seed = 1; seed <= (j == 0 ? 1 : 3); seed++) {
                    double rate = tps / 20;
                    String what = tps + " tps, jitter " + j + " seed " + seed;
                    SceneClockSim.Result r = new SceneClockSim().rate(k -> rate).ticks(4000, 400).jitter(j, seed).run();
                    smooth(r, what);
                    noSnap(r, what);
                    assertTrue(r.maxAhead() <= 1 + 1.2 * j && r.maxBehind() <= 1 + 1.2 * j,
                            "error " + r.maxAhead() + " / " + r.maxBehind() + ": " + what);
                    assertEquals(rate, r.meanRate(), 0.01, what);
                    assertTrue(r.minRate() >= rate - 0.04 - 0.04 * j && r.maxRate() <= rate + 0.04 + 0.04 * j,
                            "speed " + r.minRate() + ".." + r.maxRate() + ": " + what);
                }
            }
        }
    }

    /** A slow server back at full speed, switching at every phase of its sync interval. */
    @Test
    void aSlowServerRecoveringIsCaughtUp() {
        for (int from : new int[]{15, 18}) {
            for (int j = 0; j <= 2; j += 2) {
                for (int at = 400; at < 440; at++) {
                    int sw = at;
                    double rate = from / 20.0;
                    String what = from + " -> 20 at " + at + ", jitter " + j;
                    SceneClockSim.Result r = new SceneClockSim().rate(k -> k < sw ? rate : 1).ticks(1200, sw)
                            .jitter(j, at).run();
                    smooth(r, what);
                    noSnap(r, what);
                    assertTrue(r.maxBehind() <= 10.5 + j && r.maxAhead() <= 1 + j,
                            "error " + r.maxAhead() + " / " + r.maxBehind() + ": " + what);
                    assertTrue(r.tail() <= 1 + 1.5 * j, "tail " + r.tail() + ": " + what);
                    if (j == 0) assertTrue(r.lastOff1() <= 200, "settled after " + r.lastOff1() + ": " + what);
                }
            }
        }
    }

    @Test
    void aServerDroppingTo15TpsIsFollowedDown() {
        for (int j = 0; j <= 2; j += 2) {
            for (int at = 400; at < 440; at++) {
                int sw = at;
                String what = "20 -> 15 at " + at + ", jitter " + j;
                SceneClockSim.Result r = new SceneClockSim().rate(k -> k < sw ? 1 : 0.75).ticks(1600, sw).jitter(j, at).run();
                smooth(r, what);
                noSnap(r, what);
                assertTrue(r.maxAhead() <= 10.5 + j && r.maxBehind() <= 1 + 1.2 * j,
                        "error " + r.maxAhead() + " / " + r.maxBehind() + ": " + what);
                assertTrue(r.tail() <= 1 + 1.5 * j, "tail " + r.tail() + ": " + what);
            }
        }
    }

    /**
     * 20 -> 15 -> 20 tps, slow for 100-500 ticks from any start. Round 3 recorded the first sync after a long
     * full-speed stretch as one long interval, missed the recovery and ended 70-93 ticks behind, then snapped.
     */
    @Test
    void aSlowStretchAnywhereIsRecoveredFrom() {
        for (int j = 0; j <= 2; j += 2) {
            for (int start = 40; start <= 340; start += 10) {
                for (int d = 100; d <= 500; d += 50) {
                    int s0 = start;
                    int dd = d;
                    String what = "15 tps from " + start + " for " + d + ", jitter " + j;
                    SceneClockSim.Result r = new SceneClockSim().rate(k -> k >= s0 && k < s0 + dd ? 0.75 : 1)
                            .ticks(s0 + dd + 700, s0).jitter(j, start * 1000L + d).run();
                    smooth(r, what);
                    noSnap(r, what);
                    assertTrue(r.maxAhead() <= 10.5 + j && r.maxBehind() <= 10.5 + j,
                            "error " + r.maxAhead() + " / " + r.maxBehind() + ": " + what);
                    assertTrue(r.tail() <= 1 + 1.5 * j, "tail " + r.tail() + ": " + what);
                }
            }
        }
    }

    /**
     * The singleplayer hitch: the server stops for {@code h} ticks while the client keeps ticking, then (vanilla's
     * catch-up) races at double speed for {@code h} ticks, or skips the lost ticks and goes on at full speed. Every
     * stall length 10-80, every phase against the sync, jitter 0-3. The clock cannot see a stall until a sync is
     * overdue, so it runs on by the stall (at most {@code h} ahead); then it never snaps, never steps back, and after a
     * race it settles on the server without running past it. Round 3 snapped in 14 of 105 such runs without jitter.
     */
    @Test
    void anyHitchIsRiddenOutWithoutASnap() {
        for (boolean catchUp : new boolean[]{true, false}) {
            for (int j = 0; j <= 3; j++) {
                for (int h = 10; h <= 80; h += 5) {
                    for (int ph = 0; ph < 20; ph++) {
                        for (long seed = 1; seed <= (j == 0 ? 1 : 3); seed++) {
                            int start = 300 + ph;
                            String what = "hitch " + h + " phase " + ph + (catchUp ? " 2x" : " skip") + ", jitter " + j
                                    + " seed " + seed;
                            SceneClockSim.Result r = new SceneClockSim()
                                    .rate(SceneClockSim.hitch(start, h, catchUp)).ticks(1200, 299)
                                    .jitter(j, seed * 1000 + h * 20 + ph)
                                    .recoverFrom(catchUp ? start + 2 * h + 5 : start + h + 5).run();
                            smooth(r, what);
                            noSnap(r, what);
                            assertTrue(r.maxAhead() <= h + 1 + j, "ahead " + r.maxAhead() + ": " + what);
                            assertTrue(r.maxBehind() <= 7 + 2.5 * j, "behind " + r.maxBehind() + ": " + what);
                            if (catchUp) {
                                assertTrue(r.recoverPeak() <= 7 + 2.5 * j, "after the race " + r.recoverPeak() + ": " + what);
                            }
                            assertTrue(r.tail() <= 1 + 1.5 * j, "tail " + r.tail() + ": " + what);
                        }
                    }
                }
            }
        }
    }

    /**
     * Round 3's other failure: a 45-tick hitch whose race ends exactly on a sync made the clock jump ~15 ticks and
     * then run 5% fast to ~24 ahead. Now it rides the stall at full speed and the server simply catches up with it.
     */
    @Test
    void aRaceEndingOnASyncIsNotOvershot() {
        SceneClockSim.Result r = new SceneClockSim().rate(SceneClockSim.hitch(315, 45, true)).ticks(1500, 300)
                .recoverFrom(315 + 90).run();
        smooth(r, "hitch 45 at phase 15");
        noSnap(r, "hitch 45 at phase 15");
        assertTrue(r.recoverPeak() < 0.5, "after the race " + r.recoverPeak());
        assertTrue(r.maxRate() <= 1.5, "rate " + r.maxRate());
        assertEquals(0, r.tail(), 1e-6);
    }

    /** {@code /tick freeze}: the syncs keep coming with the frozen time, so the clock holds and never snaps. */
    @Test
    void aFrozenTickRateHoldsTheClock() {
        for (int f : new int[]{50, 200, 2000}) {
            int ff = f;
            SceneClockSim.Result r = new SceneClockSim().frozen(k -> k >= 300 && k < 300 + ff).ticks(300 + f + 800, 290).run();
            smooth(r, "freeze " + f);
            noSnap(r, "freeze " + f);
            assertTrue(r.maxAhead() <= 20 && r.maxBehind() <= 16, "error " + r.maxAhead() + " / " + r.maxBehind());
            assertTrue(r.minRate() == 0, "it held");
            assertEquals(0, r.tail(), 1e-6, "freeze " + f);
        }
    }

    /** One sync held up in transit (a lag spike): a brief, gentle dip, never a lurch. */
    @Test
    void aLateSyncIsAbsorbed() {
        for (int late : new int[]{3, 6, 10}) {
            SceneClockSim.Result r = new SceneClockSim().spike(20, late).ticks(1200, 300).run();
            smooth(r, "late " + late);
            noSnap(r, "late " + late);
            assertTrue(r.maxAhead() <= 0.5 && r.maxBehind() <= 3.5, "error " + r.maxAhead() + " / " + r.maxBehind());
            assertTrue(r.minRate() >= 0.75 && r.maxRate() <= 1.1, "speed " + r.minRate() + ".." + r.maxRate());
            assertEquals(0, r.tail(), 1e-6, "late " + late);
        }
    }
}
