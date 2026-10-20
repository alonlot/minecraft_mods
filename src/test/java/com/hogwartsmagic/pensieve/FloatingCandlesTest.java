package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.FloatingCandles;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingCandlesTest {

    @Test
    void candlesDeterministic() {
        assertArrayEquals(FloatingCandles.candles(5L, 300, 10, 2, 30), FloatingCandles.candles(5L, 300, 10, 2, 30));
        float[][] a = FloatingCandles.candles(5L, 300, 10, 2, 30);
        float[][] b = FloatingCandles.candles(6L, 300, 10, 2, 30);
        int same = 0;
        for (int i = 0; i < a.length; i++) if (a[i][0] == b[i][0]) same++;
        assertTrue(same < 5);
    }

    @Test
    void countCapped() {
        assertEquals(1500, FloatingCandles.candles(1L, 5000, 5, 1, 5).length);
        assertEquals(0, FloatingCandles.candles(1L, -3, 5, 1, 5).length);
    }

    @Test
    void candlesStayInsideTheBoxWhileBobbing() {
        float[][] boxes = {{10, 2, 30}, {0.5f, 0.5f, 0.5f}, {40, 6, 40}};
        for (float[] box : boxes) {
            float[][] cs = FloatingCandles.candles(9L, 400, box[0], box[1], box[2]);
            for (float[] c : cs) {
                assertTrue(c[FloatingCandles.HEIGHT] >= 0.5f && c[FloatingCandles.HEIGHT] <= 0.8f);
                assertTrue(c[FloatingCandles.PERIOD] >= 60f && c[FloatingCandles.PERIOD] <= 120f);
                for (float t = 0; t < 600; t += 13f) {
                    float[] p = FloatingCandles.position(c, t);
                    assertTrue(Math.abs(p[0]) <= box[0] + 1e-4f && Math.abs(p[1]) <= box[1] + 1e-4f
                            && Math.abs(p[2]) <= box[2] + 1e-4f, "outside at " + t);
                }
            }
        }
    }

    @Test
    void bobBoundedAndIndependent() {
        float[][] cs = FloatingCandles.candles(2L, 100, 10, 2, 10);
        int moving = 0;
        for (float[] c : cs) {
            float lo = 1f, hi = -1f;
            for (float t = 0; t < 240; t += 1f) {
                float b = FloatingCandles.bob(c, t);
                lo = Math.min(lo, b);
                hi = Math.max(hi, b);
            }
            assertTrue(lo >= -0.15f - 1e-5f && hi <= 0.15f + 1e-5f);
            assertTrue(hi - lo > 0.2f, "a candle that never bobs");
        }
        // Not in lockstep: at one moment the candles are at many different points of their bob.
        for (float[] c : cs) if (FloatingCandles.bob(c, 100f) > 0.05f) moving++;
        assertTrue(moving > 15 && moving < 85, "moving " + moving);
    }

    @Test
    void flickerAndSwayBounded() {
        for (int i = 0; i < 50; i++) {
            for (float t = 0; t < 400; t += 0.37f) {
                float f = FloatingCandles.flicker(t, i);
                float s = FloatingCandles.sway(t, i);
                assertTrue(f >= 0.75f - 1e-5f && f <= 1f + 1e-5f);
                assertTrue(s >= -1f && s <= 1f);
            }
        }
    }
}
