package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.LetterFlutter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void restingBelowThreshold() {
        assertTrue(LetterFlutter.resting(0f));
        assertTrue(LetterFlutter.resting(0.0099f));
        assertFalse(LetterFlutter.resting(0.01f));
        assertFalse(LetterFlutter.resting(0.2f));
    }
}
