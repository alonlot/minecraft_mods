package com.hogwartsmagic.cosy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The living-portrait timeline: pure maths over game time, card id and a per-view seed. */
class CardTimelineTest {
    private static final long[] SEEDS = {0L, 7L, 123456789L, -42L, 99999L};

    private static CardTimeline.State at(long t, int card, long seed) {
        return CardTimeline.at(t, 0.0F, card, seed, false, 0);
    }

    @Test
    void presentMostOfTheTime() {
        for (long seed : SEEDS) {
            for (int card : new int[]{3, 9, 14}) {
                int present = 0;
                for (long t = 0; t < 72000; t++) {
                    if (at(t, card, seed).present()) {
                        present++;
                    }
                }
                double frac = present / 72000.0;
                assertTrue(frac >= 0.60 && frac <= 0.95, "card " + card + " seed " + seed + " present " + frac);
            }
        }
    }

    @Test
    void exitsWithinBounds() {
        for (long seed : SEEDS) {
            int run = 0;
            int stretches = 0;
            boolean startedAbsent = at(0, 5, seed).present() ? false : true;
            for (long t = 0; t <= 72000; t++) {
                boolean present = at(t, 5, seed).present();
                if (!present) {
                    run++;
                } else if (run > 0) {
                    // a stretch cut by the start of the sample is allowed to be short
                    if (!(startedAbsent && stretches == 0)) {
                        assertTrue(run >= 400 && run <= 1200, "absent stretch of " + run + " ticks, seed " + seed);
                    }
                    stretches++;
                    run = 0;
                }
            }
            assertTrue(stretches >= 10, "expected several exits in 72000 ticks, saw " + stretches);
        }
    }

    @Test
    void walkOutSlidesOffAnEdge() {
        for (long seed : SEEDS) {
            List<Float> offsets = new ArrayList<>();
            boolean seen = false;
            for (long t = 1; t < 36000 && !seen; t++) {
                CardTimeline.State s = at(t, 4, seed);
                CardTimeline.State before = at(t - 1, 4, seed);
                // the first tick of a walk-out: moving now, standing still in the frame a tick ago
                if (s.present() && s.figureOffsetX() != 0.0F && before.present() && before.figureOffsetX() == 0.0F) {
                    // Collect the whole walk-out: it begins at the first non-zero offset.
                    for (long u = t - 1; u < t + CardTimeline.WALK_TICKS + 1; u++) {
                        CardTimeline.State w = at(u, 4, seed);
                        if (w.present()) {
                            offsets.add(Math.abs(w.figureOffsetX()));
                        }
                    }
                    seen = true;
                }
            }
            assertTrue(seen, "a walk-out was found");
            float prev = -1;
            for (float o : offsets) {
                assertTrue(o >= prev, "the offset only rises: " + prev + " then " + o);
                prev = o;
            }
            assertEquals(0.0F, offsets.get(0), 0.0001F);
            assertTrue(prev >= 1.0F, "the figure ends past a full portrait width, was " + prev);
        }
    }

    @Test
    void walkOutAndBackInStayOnOneSideEach() {
        // While walking, the sign never flips mid-walk.
        for (long t = 0; t < 36000; t++) {
            CardTimeline.State a = at(t, 6, 3L);
            CardTimeline.State b = at(t + 1, 6, 3L);
            if (a.present() && b.present() && a.figureOffsetX() != 0 && b.figureOffsetX() != 0) {
                assertTrue(Math.signum(a.figureOffsetX()) == Math.signum(b.figureOffsetX()), "no flip at " + t);
            }
        }
    }

    @Test
    void legendaryUsesClip() {
        int frames = 60;
        for (long seed : SEEDS) {
            int first = -2;
            int last = -2;
            int prev = -1;
            for (long t = 0; t < 36000; t++) {
                CardTimeline.State s = CardTimeline.at(t, 0.0F, 1, seed, true, frames);
                if (s.clipFrame() >= 0) {
                    assertEquals(0.0F, s.figureOffsetX(), 0.0F, "no slide while the clip plays");
                    assertTrue(s.clipFrame() >= prev, "the clip only runs forwards");
                    if (first == -2) {
                        first = s.clipFrame();
                    }
                    last = s.clipFrame();
                    prev = s.clipFrame();
                } else if (prev >= 0) {
                    // the clip ended; the next exit starts a fresh run
                    assertEquals(0, first);
                    assertEquals(frames - 1, last);
                    first = -2;
                    prev = -1;
                }
            }
        }
    }

    @Test
    void clipRunsAtTwelveFps() {
        // Frame n starts at tick n * 20 / 12, so after 100 ticks of a 60-frame clip we are on the last frame.
        assertEquals(100, CardTimeline.clipTicks(60));
        assertEquals(0, CardTimeline.clipTicks(0));
        // find the first clip tick and check the frame at tick 10 and 50 into it
        long start = -1;
        for (long t = 0; t < 36000; t++) {
            if (CardTimeline.at(t, 0.0F, 2, 11L, true, 60).clipFrame() == 0) {
                start = t;
                break;
            }
        }
        assertTrue(start >= 0);
        assertEquals(6, CardTimeline.at(start + 10, 0.0F, 2, 11L, true, 60).clipFrame());
        assertEquals(30, CardTimeline.at(start + 50, 0.0F, 2, 11L, true, 60).clipFrame());
    }

    @Test
    void clipFlagWithNoFramesFallsBackToTheWalk() {
        boolean slid = false;
        for (long t = 0; t < 36000; t++) {
            CardTimeline.State s = CardTimeline.at(t, 0.0F, 1, 5L, true, 0);
            assertEquals(-1, s.clipFrame());
            slid |= s.figureOffsetX() != 0.0F;
        }
        assertTrue(slid);
    }

    @Test
    void idleGapsStayWithinThreeMinutesEvenAcrossSeams() {
        // 5 super slots (30 min each) per seed, several clip lengths
        for (long seed : SEEDS) {
            for (int frames : new int[]{0, 96}) {
                long run = 0;
                int gaps = 0;
                boolean counting = false;
                for (long t = 0; t < 180000; t++) {
                    CardTimeline.State s = CardTimeline.at(t, 0.0F, 2, seed, frames > 0, frames);
                    if (s.idle()) {
                        run++;
                    } else {
                        if (counting && run > 0) {
                            assertTrue(run >= 1190 && run <= 3601, "idle gap " + run + " ticks, seed " + seed + " frames " + frames);
                            gaps++;
                        }
                        // a gap only counts from the first walk-back, so the sample start (a cut-off stretch) is skipped
                        if (!s.present()) {
                            counting = true;
                        }
                        run = 0;
                    }
                }
                assertTrue(gaps >= 30, "gaps seen: " + gaps);
            }
        }
    }

    @Test
    void sameInputsSameState() {
        for (long t = 0; t < 5000; t += 13) {
            assertEquals(CardTimeline.at(t, 0.37F, 8, 55L, false, 0), CardTimeline.at(t, 0.37F, 8, 55L, false, 0));
            assertEquals(CardTimeline.at(t, 0.5F, 2, 55L, true, 80), CardTimeline.at(t, 0.5F, 2, 55L, true, 80));
        }
    }

    @Test
    void differentSeedsDiffer() {
        int differing = 0;
        for (int i = 0; i < 1000; i++) {
            long t = 100000L + i * 17L;
            if (!at(t, 8, 1L).equals(at(t, 8, 2L))) {
                differing++;
            }
        }
        assertTrue(differing > 20, "two views should not be in lockstep, differing samples: " + differing);
    }

    @Test
    void eyesBlinkAndGlanceOnlyWhileIdle() {
        int blinks = 0;
        int looks = 0;
        for (long t = 0; t < 72000; t++) {
            CardTimeline.State s = at(t, 10, 21L);
            if (s.blink() || s.look() != 0) {
                assertTrue(s.idle(), "eyes move only while the figure sits still, at " + t);
            }
            blinks += s.blink() ? 1 : 0;
            looks += s.look() != 0 ? 1 : 0;
        }
        // 3 ticks every ~80 and ~30 ticks every ~200 of the idle time
        assertTrue(blinks > 1000 && blinks < 4000, "blink ticks " + blinks);
        assertTrue(looks > 4000 && looks < 16000, "glance ticks " + looks);
    }

    @Test
    void partialTickMovesSmoothlyThroughTheWalk() {
        // Two sub-tick samples inside a walk differ: the partial tick matters.
        for (long t = 0; t < 36000; t++) {
            CardTimeline.State a = CardTimeline.at(t, 0.0F, 4, 3L, false, 0);
            if (a.present() && a.figureOffsetX() != 0.0F && Math.abs(a.figureOffsetX()) < 0.9F) {
                assertTrue(CardTimeline.at(t, 0.5F, 4, 3L, false, 0).figureOffsetX() != a.figureOffsetX());
                return;
            }
        }
    }

    /** A reveal starts its card at the top of a slot: the wizard stands there, still, for at least a minute. */
    @Test
    void slotStartIsPresentForAMinute() {
        for (int card = 1; card <= 20; card++) {
            for (long seed : new long[]{0L, 7919L * card, -123456789L, Long.MAX_VALUE / 3}) {
                long origin = -CardTimeline.shift(card, seed);
                for (int k = 0; k < 1200; k += 7) {
                    CardTimeline.State s = CardTimeline.at(origin + k, 0.0F, card, seed, card <= 2, 95);
                    assertTrue(s.present() && s.figureOffsetX() == 0.0F && s.clipFrame() < 0,
                            "card " + card + " seed " + seed + " tick " + k + " was " + s);
                }
            }
        }
    }
}
