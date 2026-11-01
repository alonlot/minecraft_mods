package com.hogwartsmagic.client.ambience;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The world ambience clock: smooth, monotonic, shared between players, and exact however long the world has run. */
class AmbienceClockTest {
    @Test
    void monotonicAcrossTicks() {
        long base = AmbienceClock.baseFor(5_000L);
        float last = -1f;
        for (long tick = 5_000L; tick < 5_400L; tick++) {
            for (float partial = 0f; partial < 1f; partial += 0.25f) {
                float now = AmbienceClock.elapsed(base, tick, partial);
                assertTrue(now > last, "the clock went back at tick " + tick + " partial " + partial);
                last = now;
            }
        }
    }

    @Test
    void monotonicAcrossAnEpochBoundary() {
        long base = AmbienceClock.baseFor(AmbienceClock.EPOCH - 3);
        float before = AmbienceClock.elapsed(base, AmbienceClock.EPOCH - 1, 0.5f);
        float after = AmbienceClock.elapsed(base, AmbienceClock.EPOCH, 0.5f);
        assertTrue(after > before, "the session's clock never wraps while it runs");
        assertEquals(1f, after - before, 1e-3f);
    }

    @Test
    void playersInTheSameEpochShareTheClock() {
        long tick = 3L * AmbienceClock.EPOCH + 777L;
        float early = AmbienceClock.elapsed(AmbienceClock.baseFor(3L * AmbienceClock.EPOCH + 10L), tick, 0.25f);
        float late = AmbienceClock.elapsed(AmbienceClock.baseFor(tick), tick, 0.25f);
        assertEquals(early, late, 0f, "one who joined early and one who joined late read the same time");
    }

    @Test
    void stillExactAfterAVeryLongWorld() {
        long tick = 9_000_000_000L; // 12 years of game time
        long base = AmbienceClock.baseFor(tick);
        float a = AmbienceClock.elapsed(base, tick, 0f);
        float b = AmbienceClock.elapsed(base, tick + 1, 0f);
        assertEquals(1f, b - a, 1e-3f, "a whole tick is still a whole tick");
        assertTrue(a < AmbienceClock.EPOCH, "the number the renderers see stays small");
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
