package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.EyeEase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EyeEaseTest {
    private static final float[] T = {10f, 30f};
    private static final boolean[] C = {true, false};

    @Test
    void standsBeforeAndReachesEachTargetExactly() {
        assertEquals(0f, EyeEase.fraction(T, C, 0f));
        assertEquals(0f, EyeEase.fraction(T, C, 10f));
        assertEquals(1f, EyeEase.fraction(T, C, 10f + EyeEase.TICKS));
        assertEquals(1f, EyeEase.fraction(T, C, 29f));
        assertEquals(0f, EyeEase.fraction(T, C, 30f + EyeEase.TICKS));
        assertEquals(EyeEase.CROUCH_EYE, EyeEase.eyeHeight(1f), 1e-6f);
    }

    @Test
    void continuousMonotonicAndNoOvershoot() {
        float prev = 0f;
        for (float t = 0; t <= 20f; t += 0.05f) {
            float f = EyeEase.fraction(T, C, t);
            assertTrue(f >= -1e-6f && f <= 1f + 1e-6f);
            assertTrue(f >= prev - 1e-6f && f - prev < 0.06f, "no jump at " + t);
            prev = f;
        }
        for (float t = 30f; t <= 40f; t += 0.05f) {
            float f = EyeEase.fraction(T, C, t);
            assertTrue(f <= prev + 1e-6f && prev - f < 0.06f, "no jump at " + t);
            prev = f;
        }
    }

    @Test
    void interruptedChangeContinuesFromWhereItGot() {
        float[] times = {10f, 12f};
        boolean[] crouch = {true, false};
        float mid = EyeEase.fraction(times, crouch, 12f);
        assertEquals(0.5f, mid, 1e-6f);
        assertEquals(mid, EyeEase.fraction(times, crouch, 12.0001f), 1e-3f);
        assertEquals(0f, EyeEase.fraction(times, crouch, 12f + EyeEase.TICKS), 1e-6f);
    }
}
