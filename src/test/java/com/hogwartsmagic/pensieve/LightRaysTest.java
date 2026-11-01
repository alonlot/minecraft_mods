package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.LightRays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightRaysTest {
    private static final float[][] BOXES = {{2, 5, 12}, {0.5f, 1, 1}, {12, 24, 60}, {3, 8, 20}};

    @Test
    void gapStartsAsACrack() {
        assertEquals(0f, LightRays.gap(-1, 40), 1e-6f);
        float crack = LightRays.gap(0, 40);
        assertTrue(crack > 0f && crack < 0.1f, "crack " + crack);
    }

    @Test
    void gapEndsFullAndStays() {
        assertEquals(1f, LightRays.gap(40, 40), 1e-6f);
        assertEquals(1f, LightRays.gap(5000, 40), 1e-6f);
        assertEquals(1f, LightRays.gap(3, 0), 1e-6f);
    }

    @Test
    void gapMonotonicBoundedEaseIn() {
        float last = 0f;
        for (float t = 0; t <= 60; t += 0.5f) {
            float g = LightRays.gap(t, 48);
            assertTrue(g >= last && g >= 0f && g <= 1f, "gap at " + t + " = " + g);
            last = g;
        }
        // Ease-in: slow at first, so less than half open half-way through.
        assertTrue(LightRays.gap(24, 48) < 0.5f);
    }

    @Test
    void shaftsInsideTheBox() {
        float[][] shafts = LightRays.shafts(11L, LightRays.SHAFTS);
        for (float[] box : BOXES) {
            float hw = box[0], h = box[1], len = box[2];
            for (float g : new float[]{0f, 0.04f, 0.5f, 1f}) {
                for (float[] s : shafts) {
                    float[] e = LightRays.shaftEnds(s, g, hw, h, len);
                    assertEquals(6, e.length);
                    for (int k = 0; k < 6; k += 3) {
                        assertInside(e[k], e[k + 1], e[k + 2], hw, h, len);
                    }
                    assertEquals(0f, e[2], 1e-6f);
                    assertEquals(len, e[5], 1e-5f);
                    float[] w = LightRays.shaftHalfWidths(s, g, hw, len);
                    assertTrue(w[0] > 0f && w[0] <= hw && w[1] >= w[0], "widths " + w[0] + ", " + w[1]);
                    assertTrue(w[1] <= hw + LightRays.MAX_FAN * len, "far width " + w[1]);
                }
            }
        }
    }

    @Test
    void shaftsDeterministic() {
        assertArrayEquals(LightRays.shafts(3L, 16), LightRays.shafts(3L, 16));
    }

    @Test
    void crackSqueezesTheShafts() {
        float[][] shafts = LightRays.shafts(5L, LightRays.SHAFTS);
        for (float[] s : shafts) {
            float[] crack = LightRays.shaftEnds(s, 0.04f, 3, 8, 20);
            float[] open = LightRays.shaftEnds(s, 1f, 3, 8, 20);
            assertTrue(Math.abs(crack[0]) <= Math.abs(open[0]) + 1e-6f);
            assertTrue(Math.abs(crack[0]) <= 0.04f * 3 + 1e-5f);
        }
    }

    @Test
    void shimmerBounded() {
        for (int i = 0; i < 40; i++) {
            float lo = 2f, hi = -1f;
            for (float t = 0; t < 3000; t += 0.9f) {
                float k = LightRays.shimmer(t, i);
                lo = Math.min(lo, k);
                hi = Math.max(hi, k);
            }
            assertTrue(lo >= 0.6f - 1e-5f && hi <= 1f + 1e-5f, lo + ".." + hi);
            assertTrue(hi - lo > 0.05f, "shaft " + i + " never shimmers");
        }
    }

    @Test
    void motesInsideTheBoxAndDarkWhenShut() {
        for (float[] box : BOXES) {
            float hw = box[0], h = box[1], len = box[2];
            for (int i = 0; i < LightRays.MOTES; i++) {
                for (float t = 0; t < 2000; t += 37f) {
                    float[] m = LightRays.mote(9L, i, t, 1f, hw, h, len);
                    assertInside(m[0], m[1], m[2], hw, h, len);
                    assertTrue(m[3] >= 0f && m[3] <= 1f, "brightness " + m[3]);
                    assertEquals(0f, LightRays.mote(9L, i, t, 0f, hw, h, len)[3], 1e-6f);
                }
            }
        }
    }

    @Test
    void worldOffsetFollowsMinecraftYaw() {
        double[] south = LightRays.worldOffset(0f, 0f, 0f, 1f);
        assertEquals(0, south[0], 1e-6);
        assertEquals(1, south[2], 1e-6);
        double[] west = LightRays.worldOffset(90f, 0f, 2f, 1f);
        assertEquals(-1, west[0], 1e-6);
        assertEquals(2, west[1], 1e-6);
        assertEquals(0, west[2], 1e-6);
        // Lateral stays perpendicular to the travel direction.
        double[] side = LightRays.worldOffset(37f, 1f, 0f, 0f);
        double[] fwd = LightRays.worldOffset(37f, 0f, 0f, 1f);
        assertEquals(0, side[0] * fwd[0] + side[2] * fwd[2], 1e-6);
    }

    private static void assertInside(float x, float y, float z, float hw, float h, float len) {
        assertTrue(Math.abs(x) <= hw + LightRays.MAX_FAN * len + 1e-4f, "x " + x);
        assertTrue(y >= -1e-4f && y <= h + 1e-4f, "y " + y);
        assertTrue(z >= -1e-4f && z <= len + 1e-4f, "z " + z);
    }

    @Test
    void intensityScalesBrightnessLinearly() {
        assertEquals(1f, LightRays.intensity(Float.NaN), 1e-6f);
        assertEquals(0.45f, LightRays.intensity(0.45f), 1e-6f);
        assertEquals(LightRays.MAX_INTENSITY, LightRays.intensity(9f), 1e-6f);
        assertEquals(LightRays.MIN_INTENSITY, LightRays.intensity(0f), 1e-6f);
        float base = 0.3f;
        assertEquals(base, LightRays.scaled(base, 1f), 1e-6f);
        assertEquals(2f * LightRays.scaled(base, 0.5f), LightRays.scaled(base, 1f), 1e-6f);
        assertEquals(LightRays.scaled(base, 0.4f) + LightRays.scaled(base, 0.6f), LightRays.scaled(base, 1f), 1e-6f);
    }
    @Test
    void theSunIsLowOnlyAtDawnAndDusk() {
        assertEquals(0f, LightRays.lowSun(1f), 1e-6f, "full day: a high sun");
        assertEquals(1f, LightRays.lowSun(LightRays.LOW_SUN_DAYLIGHT), 1e-6f, "where the rays go out: the lowest sun");
        float last = 2f;
        for (float d = LightRays.LOW_SUN_DAYLIGHT; d <= 1f; d += 0.05f) {
            float low = LightRays.lowSun(d);
            assertTrue(low >= 0f && low <= 1f && low <= last, "lowSun falls as the daylight grows, at " + d);
            last = low;
        }
    }

    @Test
    void aLowSunsRaysAreWarmer() {
        float[] day = {1f, 0.85f, 0.63f};
        float[] dusk = {1f, 0.6f, 0.3f};
        assertArrayEquals(day, LightRays.sunTint(day, dusk, 0f), 1e-6f);
        assertArrayEquals(dusk, LightRays.sunTint(day, dusk, 1f), 1e-6f);
        float[] mid = LightRays.sunTint(day, dusk, LightRays.lowSun(0.5f));
        assertTrue(mid[1] < day[1] && mid[2] < day[2] && mid[0] == 1f, "dawn light loses green and blue first");
    }

    @Test
    void aLowSunsRaysComeInLower() {
        float[] shaft = LightRays.shafts(7L, LightRays.SHAFTS)[3];
        float[] high = LightRays.shaftEnds(shaft, 1f, 3f, 10f, 20f, LightRays.sunSink(0f));
        float[] low = LightRays.shaftEnds(shaft, 1f, 3f, 10f, 20f, LightRays.sunSink(1f));
        assertArrayEquals(LightRays.shaftEnds(shaft, 1f, 3f, 10f, 20f), high, 1e-6f,
                "a high sun keeps the effect's own slope");
        assertEquals(high[1], low[1], 1e-6f, "both start at the same height in the window");
        assertTrue(low[4] > high[4], "a low sun's shaft drops less over the hall: it comes in at a lower angle");
        assertEquals(LightRays.LOW_SUN_SINK, LightRays.sunSink(1f), 1e-6f);
    }
}
