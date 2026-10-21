package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.LetterFlutter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LetterFlutterTest {
    @Test
    void rollIsBounded() {
        for (int seed = 0; seed <= 50; seed++) {
            for (float age = 0; age <= 400; age += 0.5f) {
                assertTrue(Math.abs(LetterFlutter.roll(age, seed)) <= 35f, "roll " + age + " " + seed);
                assertTrue(Math.abs(LetterFlutter.wobble(age, seed)) <= 20f, "wobble " + age + " " + seed);
            }
        }
    }

    @Test
    void seedsDiffer() {
        assertNotEquals(LetterFlutter.roll(10, 1), LetterFlutter.roll(10, 2));
    }

    @Test
    void isSmooth() {
        for (int seed = 0; seed <= 50; seed++) {
            for (float age = 0; age <= 400; age += 0.5f) {
                assertTrue(Math.abs(LetterFlutter.roll(age + 0.5f, seed) - LetterFlutter.roll(age, seed)) < 6f);
                assertTrue(Math.abs(LetterFlutter.wobble(age + 0.5f, seed) - LetterFlutter.wobble(age, seed)) < 6f);
            }
        }
    }

    @Test
    void amplitudeScalesWithSpeed() {
        assertEquals(0f, LetterFlutter.amplitude(0f), 0f);
        assertEquals(0.5f, LetterFlutter.amplitude(0.025f), 1e-6f);
        assertEquals(1f, LetterFlutter.amplitude(0.05f), 0f);
        assertEquals(1f, LetterFlutter.amplitude(0.4f), 0f);
        assertEquals(0f, LetterFlutter.amplitude(-1f), 0f);
        // Smooth: no snap between a still letter and a moving one.
        for (float s = 0f; s < 0.1f; s += 0.001f) {
            assertTrue(Math.abs(LetterFlutter.amplitude(s + 0.001f) - LetterFlutter.amplitude(s)) <= 0.0201f);
        }
    }

    @Test
    void seedStableAcrossReplays() {
        // The default seed comes from the actor id (stable), not the global entity counter (new on every replay).
        assertEquals(LetterFlutter.seedFor("letter_1"), LetterFlutter.seedFor(new String("letter_1")));
        assertNotEquals(LetterFlutter.seedFor("letter_1"), LetterFlutter.seedFor("letter_2"));
        assertEquals(LetterFlutter.seedFor(null), LetterFlutter.seedFor(""));
    }
}
