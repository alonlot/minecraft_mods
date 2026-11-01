package com.hogwartsmagic.client.ambience;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The world ambience clock: smooth, monotonic, shared between players, and exact however long the world has run. */
class AmbienceClockTest {
    @Test
    void monotonicAcrossTicks() {
        float last = -1f;
        for (long tick = 5_000L; tick < 5_400L; tick++) {
            for (float partial = 0f; partial < 1f; partial += 0.25f) {
                float now = AmbienceClock.shared(tick, partial);
                assertTrue(now > last, "the clock went back at tick " + tick + " partial " + partial);
                last = now;
            }
        }
    }

    @Test
    void everyClientReadsTheSameTimeOnTheSameTick() {
        // The value depends on the game time alone: nothing about when a client joined enters into it.
        long tick = 3L * AmbienceClock.EPOCH + 777L;
        assertEquals(777.25f, AmbienceClock.shared(tick, 0.25f), 0f);
        assertEquals(AmbienceClock.shared(tick, 0.25f), AmbienceClock.shared(tick, 0.25f), 0f);
    }

    @Test
    void everyClientStepsToTheNewEpochOnTheSameTick() {
        long boundary = 7L * AmbienceClock.EPOCH;
        float before = AmbienceClock.shared(boundary - 1, 0.5f);
        float after = AmbienceClock.shared(boundary, 0.5f);
        assertEquals(AmbienceClock.EPOCH - 0.5f, before, 1e-2f);
        assertEquals(0.5f, after, 0f, "the new epoch starts at 0 for everyone on the same tick");
    }

    @Test
    void stillExactAfterAVeryLongWorld() {
        long tick = 9_000_000_000L; // 12 years of game time
        float a = AmbienceClock.shared(tick, 0f);
        float b = AmbienceClock.shared(tick + 1, 0f);
        assertEquals(1f, b - a, 1e-3f, "a whole tick is still a whole tick");
        assertTrue(a < AmbienceClock.EPOCH, "the number the renderers see stays small");
    }

    @Test
    void elapsedSinceAnEarlierTickIsTheRealOneWithinAnEpoch() {
        long now = 4L * AmbienceClock.EPOCH + 500L;
        long base = AmbienceClock.baseFor(now);
        assertEquals(60.5f, AmbienceClock.elapsed(base, now, 0.5f) - AmbienceClock.elapsed(base, now - 60, 0f), 1e-4f);
    }

    @Test
    void daylightFollowsTheSun() {
        assertEquals(1f, AmbienceClock.daylight(0f), 1e-4f, "noon");
        assertEquals(0f, AmbienceClock.daylight((float) Math.PI), 1e-4f, "midnight");
        assertEquals(0f, AmbienceClock.daylight((float) (Math.PI / 2)), 1e-4f, "the horizon at dusk");
        assertEquals(0f, AmbienceClock.daylight((float) (3 * Math.PI / 2)), 1e-4f, "the horizon at dawn");
        float morning = AmbienceClock.daylight((float) (Math.PI * 1.75));
        assertTrue(morning > 0.2f && morning <= 1f, "an hour after dawn there is light, got " + morning);
    }
}
