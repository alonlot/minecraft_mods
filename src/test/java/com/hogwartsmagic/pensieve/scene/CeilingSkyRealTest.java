package com.hogwartsmagic.pensieve.scene;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code real} ceiling style: its day / dusk / night / overcast / storm weights and its two new colours. */
class CeilingSkyRealTest {

    @Test
    void noonIsDay() {
        float[] w = CeilingSky.realWeights(6000, 0f, 0f);
        assertEquals(1f, w[0], 1e-6f);
        assertEquals(0f, w[1], 1e-6f);
        assertEquals(0f, w[2], 1e-6f);
        assertEquals(0f, w[3], 1e-6f);
        assertEquals(0f, w[4], 1e-6f);
    }

    @Test
    void midnightIsNight() {
        float[] w = CeilingSky.realWeights(18000, 0f, 0f);
        assertEquals(1f, w[2], 1e-6f);
        assertEquals(0f, w[0], 1e-6f);
        assertEquals(0f, w[1], 1e-6f);
    }

    @Test
    void sunsetBlends() {
        float[] w = CeilingSky.realWeights(12500, 0f, 0f);
        assertTrue(w[1] > 0.5f, "dusk should lead at 12500, was " + w[1]);
        assertEquals(1f, w[0] + w[1] + w[2], 1e-5f);
        // The dawn peak mirrors it.
        float[] dawn = CeilingSky.realWeights(23250, 0f, 0f);
        assertTrue(dawn[1] > 0.5f, "dusk (dawn) should lead at 23250, was " + dawn[1]);
        // Half-way into the blend both sides count.
        float[] mid = CeilingSky.realWeights(11750, 0f, 0f);
        assertTrue(mid[0] > 0.1f && mid[1] > 0.1f);
    }

    @Test
    void rainIsOvercast() {
        float[] w = CeilingSky.realWeights(6000, 0.8f, 0f);
        assertEquals(0.8f, w[3], 1e-6f);
        assertEquals(0f, w[4], 1e-6f);
        float[] t = CeilingSky.realWeights(6000, 1f, 0.6f);
        assertEquals(0.6f, t[4], 1e-6f);
        // Out-of-range input is clamped, never leaks a weight outside 0..1.
        float[] c = CeilingSky.realWeights(6000, 3f, -1f);
        assertEquals(1f, c[3], 1e-6f);
        assertEquals(0f, c[4], 1e-6f);
    }

    @Test
    void weightsAlwaysSumToOne() {
        for (int t = 0; t <= 24000; t += 50) {
            float[] w = CeilingSky.realWeights(t, 0.3f, 0.1f);
            assertEquals(1f, w[0] + w[1] + w[2], 1e-5f, "at " + t);
            for (int i = 0; i < 5; i++) assertTrue(w[i] >= 0f && w[i] <= 1f, "weight " + i + " at " + t);
        }
    }

    @Test
    void dayTimeWrapsAroundTheDay() {
        // 24000 is the same moment as 0, and a long-running world's raw day time is taken modulo a day.
        assertEquals(CeilingSky.realWeights(0, 0f, 0f)[1], CeilingSky.realWeights(24000, 0f, 0f)[1], 1e-6f);
        assertEquals(CeilingSky.realWeights(6000, 0f, 0f)[0], CeilingSky.realWeights(30000, 0f, 0f)[0], 1e-6f);
    }

    @Test
    void starBudgetFadesOutInsteadOfCuttingHard() {
        assertEquals(1f, CeilingSky.starBudgetFade(0, 1000f), 0f);
        assertEquals(1f, CeilingSky.starBudgetFade(900, 1000f), 0f);
        assertEquals(0f, CeilingSky.starBudgetFade(1000, 1000f), 0f);
        assertEquals(0f, CeilingSky.starBudgetFade(5000, 1000f), 0f);
        // A small change in the budget moves each star's brightness only a little (no pop by index).
        for (int i = 880; i < 1010; i++) {
            float d = Math.abs(CeilingSky.starBudgetFade(i, 1000.5f) - CeilingSky.starBudgetFade(i, 1000f));
            assertTrue(d < 0.01f, "star " + i + " jumped by " + d);
        }
    }
}
