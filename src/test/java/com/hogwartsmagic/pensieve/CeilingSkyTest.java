package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.CeilingSky;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CeilingSkyTest {

    @Test
    void revealStartsAtZero() {
        assertEquals(0f, CeilingSky.revealAt(0, 60), 1e-6f);
        assertEquals(0f, CeilingSky.revealAt(-5, 60), 1e-6f);
    }

    @Test
    void revealEndsAtOne() {
        assertEquals(1f, CeilingSky.revealAt(60, 60), 1e-6f);
        assertEquals(1f, CeilingSky.revealAt(10_000, 60), 1e-6f);
        // A late joiner long past the reveal sees the sky, whatever the duration.
        assertEquals(1f, CeilingSky.revealAt(500, 1), 1e-6f);
        assertEquals(1f, CeilingSky.revealAt(1, 0), 1e-6f);
    }

    @Test
    void revealMonotonic() {
        float last = -1f;
        for (int t = 0; t <= 80; t++) {
            float r = CeilingSky.revealAt(t, 60);
            assertTrue(r >= last, "reveal fell at " + t);
            assertTrue(r >= 0f && r <= 1f);
            last = r;
        }
        // Ease-out: more than half revealed at the half-way mark.
        assertTrue(CeilingSky.revealAt(30, 60) > 0.5f);
    }

    @Test
    void starsInsideRect() {
        float[][] hs = {{15, 10}, {1, 100}, {100, 1}, {12, 40}};
        for (float[] h : hs) {
            float[][] stars = CeilingSky.stars(42L, h[0], h[1], 900);
            assertEquals(900, stars.length);
            for (float[] s : stars) {
                assertEquals(3, s.length);
                assertTrue(Math.abs(s[0]) <= h[0] && Math.abs(s[1]) <= h[1], "star outside: " + s[0] + "," + s[1]);
                assertTrue(s[2] > 0f && s[2] <= 1f, "size " + s[2]);
            }
        }
    }

    @Test
    void starsDeterministic() {
        assertArrayEquals(CeilingSky.stars(7L, 15, 10, 300), CeilingSky.stars(7L, 15, 10, 300));
        float[][] a = CeilingSky.stars(7L, 15, 10, 300);
        float[][] b = CeilingSky.stars(8L, 15, 10, 300);
        int same = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i][0] == b[i][0] && a[i][1] == b[i][1]) same++;
        }
        assertTrue(same < 5, "different seeds should give different skies");
    }

    @Test
    void twinkleBounded() {
        for (int i = 0; i < 200; i++) {
            float lo = 2f, hi = -1f;
            for (float t = 0; t < 2000; t += 0.7f) {
                float k = CeilingSky.twinkle(t, i);
                lo = Math.min(lo, k);
                hi = Math.max(hi, k);
            }
            assertTrue(lo >= 0.35f - 1e-5f && hi <= 1f + 1e-5f, "star " + i + ": " + lo + ".." + hi);
            assertTrue(hi - lo > 0.05f, "star " + i + " never twinkles");
        }
    }

    @Test
    void cloudOffsetDrifts() {
        assertEquals(0f, CeilingSky.cloudOffset(0f), 1e-6f);
        assertEquals(0.06f, CeilingSky.cloudOffset(100f), 1e-5f);
    }

    @Test
    void dissolveCoversAndClears() {
        for (float u = -1; u <= 1; u += 0.25f) {
            for (float v = -1; v <= 1; v += 0.25f) {
                assertEquals(0f, CeilingSky.dissolve(0f, u, v), 1e-6f);
                assertEquals(1f, CeilingSky.dissolve(1f, u, v), 1e-6f);
            }
        }
        // The sky opens from the middle outward.
        assertTrue(CeilingSky.dissolve(0.4f, 0f, 0f) > CeilingSky.dissolve(0.4f, 0.95f, 0.95f));
    }

    @Test
    void shootingStarStaysOnTheCeiling() {
        int seen = 0;
        for (float t = 0; t < 20 * 60 * 5; t += 1f) {
            float[] s = CeilingSky.shootingStar(99L, t, 20, 8);
            if (s == null) continue;
            seen++;
            assertEquals(5, s.length);
            assertTrue(Math.abs(s[0]) <= 20 && Math.abs(s[2]) <= 20, "x outside");
            assertTrue(Math.abs(s[1]) <= 8 && Math.abs(s[3]) <= 8, "z outside");
            assertTrue(s[4] >= 0f && s[4] <= 1f);
        }
        assertTrue(seen > 0, "no shooting star in five minutes");
        assertTrue(seen < 20 * 60 * 5 / 4, "shooting stars should be rare");
        assertNotNull(CeilingSky.stars(1L, 1, 1, 1));
    }

    @Test
    void planeSitsJustBelowTheRoof() {
        float y = CeilingSky.planeY(30);
        assertTrue(y < 30f && y > 29.9f, "plane at " + y);
    }
}
