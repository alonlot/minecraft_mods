package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.CeilingSky;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                float k = CeilingSky.twinkle(5L, t, i);
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
    }

    @Test
    void twinkleDependsOnTheSkySeed() {
        int differ = 0;
        for (int i = 0; i < 50; i++) {
            if (Math.abs(CeilingSky.twinkle(1L, 300f, i) - CeilingSky.twinkle(2L, 300f, i)) > 0.01f) differ++;
        }
        assertTrue(differ > 25, "two skies twinkle in step");
    }

    @Test
    void starCountFollowsArea() {
        assertEquals(CeilingSky.MAX_STARS, CeilingSky.starCount(100, 100));
        assertEquals(CeilingSky.MIN_STARS, CeilingSky.starCount(10, 10));      // 400 sq blocks: the floor
        assertEquals(Math.round(4 * 15 * 30 * CeilingSky.STAR_DENSITY), CeilingSky.starCount(15, 30));
        // A tiny ceiling is not crammed with the full floor: at most 1.5 stars per square block.
        assertEquals(6, CeilingSky.starCount(1, 1));
    }

    @Test
    void nightGradientCorners() {
        float m = CeilingSky.SKY_TEXTURE_MEAN;
        float[] centre = CeilingSky.skyColour("night", 3L, 0f, 0f);
        float[] corner = CeilingSky.skyColour("night", 3L, 1f, -1f);   // off the band, which always runs through (1, 1)
        // Overhead is #1A2350 (plus whatever band glow passes overhead), the corners #0B1030, both before the texture.
        assertTrue(centre[2] * m >= 0x50 / 255f - 1e-4f, "centre blue " + centre[2] * m);
        assertEquals(0x0B / 255f, corner[0] * m, 0.02f);
        assertEquals(0x30 / 255f, corner[2] * m, 0.03f);
        assertTrue(centre[2] > corner[2]);
    }

    @Test
    void stormIsWarmAtTheWalls() {
        float[] mid = CeilingSky.stormCloudColour(0f, 0f, null, 20, 30);
        float[] wall = CeilingSky.stormCloudColour(0.99f, 0f, null, 20, 30);
        assertTrue(mid[2] > mid[0], "the middle is blue-grey");
        assertTrue(wall[0] > wall[2], "the walls are warm");
        float[] sky = CeilingSky.skyColour("storm", 3L, 0.99f, 0.99f);
        assertTrue(sky[0] > CeilingSky.skyColour("storm", 3L, 0f, 0f)[0]);
    }

    @Test
    void flashLightsTheCloudNearIt() {
        float[] flash = {0.2f, -0.3f, 1f, 0f, 0f};
        float near = CeilingSky.stormCloudColour(0.2f, -0.3f, flash, 20, 30)[2];
        float far = CeilingSky.stormCloudColour(-0.9f, 0.9f, flash, 20, 30)[2];
        float none = CeilingSky.stormCloudColour(0.2f, -0.3f, null, 20, 30)[2];
        assertTrue(near > none + 1f && Math.abs(far - CeilingSky.stormCloudColour(-0.9f, 0.9f, null, 20, 30)[2]) < 0.01f);
    }

    @Test
    void shimmerOnlyOnTheFront() {
        assertEquals(0f, CeilingSky.shimmer(0f), 1e-6f);
        assertEquals(0f, CeilingSky.shimmer(1f), 1e-6f);
        assertEquals(0.35f, CeilingSky.shimmer(0.5f), 1e-6f);
    }

    @Test
    void strikesEverySixToFourteenSeconds() {
        assertEquals(-1, CeilingSky.strikeIndex(4L, 0f));
        assertNull(CeilingSky.flash(4L, CeilingSky.FIRST_STRIKE - 1f));
        for (int k = 0; k < 40; k++) {
            float gap = CeilingSky.strikeStart(4L, k + 1) - CeilingSky.strikeStart(4L, k);
            assertTrue(gap >= 120f && gap <= 280f, "gap " + gap);
            assertEquals(k, CeilingSky.strikeIndex(4L, CeilingSky.strikeStart(4L, k) + 1f));
            float thunder = CeilingSky.thunderAt(4L, k) - CeilingSky.strikeStart(4L, k);
            assertTrue(thunder >= 10f && thunder <= 30f);
        }
    }

    @Test
    void flashBoundedAndDeterministic() {
        int lit = 0;
        for (float t = 0; t < 4000; t += 0.5f) {
            float[] f = CeilingSky.flash(8L, t);
            if (f == null) continue;
            lit++;
            assertTrue(f[2] > 0f && f[2] <= 1f, "intensity " + f[2]);
            assertTrue(Math.abs(f[0]) <= 0.75f && Math.abs(f[1]) <= 0.75f);
            assertArrayEquals(f, CeilingSky.flash(8L, t));
        }
        assertTrue(lit > 0 && lit < 8000 / 4, "lit " + lit);
        float[] start = CeilingSky.flash(8L, CeilingSky.strikeStart(8L, 3) + 1f);
        assertEquals(1f, start[2], 1e-5f);
    }

    @Test
    void boltsStayOnTheCeiling() {
        for (int k = 0; k < 60; k++) {
            float[][] segs = CeilingSky.bolt(12L, k);
            assertTrue(segs.length >= 13, "a main stroke and a branch");
            for (float[] s : segs) {
                for (int i = 0; i < 4; i++) assertTrue(Math.abs(s[i]) <= 1f, "outside: " + s[i]);
                assertTrue(s[4] > 0f && s[4] <= 1f);
            }
            assertArrayEquals(segs, CeilingSky.bolt(12L, k));
        }
        assertNotEquals(CeilingSky.bolt(12L, 1)[0][0], CeilingSky.bolt(12L, 2)[0][0]);
    }

    @Test
    void slicesRiseFromTheWallTopsIntoTheSky() {
        int n = CeilingSky.SLICES;
        assertEquals(CeilingSky.SLICE_LOW, CeilingSky.sliceHeight(0, n), 1e-5f);
        assertEquals(CeilingSky.SKY_TOP, CeilingSky.sliceHeight(n - 1, n), 1e-4f);
        float last = -1f, lastGap = 0f;
        for (int i = 0; i < n; i++) {
            float h = CeilingSky.sliceHeight(i, n);
            assertTrue(h > last, "slices must rise");
            if (i > 1) assertTrue(h - last >= lastGap - 1e-4f, "packed near the bottom, sparser up high");
            if (i > 0) lastGap = h - last;
            last = h;
        }
        assertTrue(CeilingSky.SKY_TOP >= 20f && CeilingSky.SKY_TOP <= 30f);
        // Each slice reaches further past the walls than the one below it.
        assertTrue(CeilingSky.sliceReach(CeilingSky.SKY_TOP) > CeilingSky.sliceReach(5f));
        assertEquals(0f, CeilingSky.sliceReach(0f), 1e-6f);
    }

    @Test
    void densityBoundedDeterministicAndContinuous() {
        for (float x = -40; x <= 40; x += 3.7f) {
            for (float h = 0; h <= 26; h += 2.3f) {
                float d = CeilingSky.density(7L, x, h, x * 0.5f, 100f);
                assertTrue(d >= 0f && d <= 1f, "density " + d);
                assertEquals(d, CeilingSky.density(7L, x, h, x * 0.5f, 100f), 0f);
                // A small step up the stack barely changes it: neighbouring slices agree, one volume.
                assertTrue(Math.abs(CeilingSky.density(7L, x, h + 0.2f, x * 0.5f, 100f) - d) < 0.12f);
            }
        }
        // It rolls: the field moves with time.
        assertTrue(Math.abs(CeilingSky.density(7L, 3f, 5f, 2f, 0f) - CeilingSky.density(7L, 3f, 5f, 2f, 400f)) > 0f);
    }

    @Test
    void lowerCloudIsDenserWithGaps() {
        double low = 0, high = 0;
        int gaps = 0, samples = 0;
        for (float x = -40; x <= 40; x += 1.3f) {
            for (float z = -20; z <= 20; z += 1.7f) {
                float a = CeilingSky.sliceAlpha(3L, x, 1f, z, 0f);
                low += a;
                high += CeilingSky.sliceAlpha(3L, x, 24f, z, 0f);
                if (a < 0.05f) gaps++;
                samples++;
                assertTrue(a <= CeilingSky.sliceBase(1f) + 1e-6f);
            }
        }
        assertTrue(low > high * 2, "low " + low + " high " + high);
        assertTrue(gaps > samples / 20, "the low cloud needs gaps for the stars: " + gaps + "/" + samples);
        assertTrue(CeilingSky.sliceBase(0f) > CeilingSky.sliceBase(CeilingSky.SKY_TOP));
    }

    @Test
    void sliceColourWarmLowCoolHighLitByFlashWithDepth() {
        float[] lowWall = CeilingSky.sliceColour(40f, 1f, 0f, 41f, 16.5f, null);
        float[] highWall = CeilingSky.sliceColour(40f, 24f, 0f, 41f, 16.5f, null);
        assertTrue(lowWall[0] > lowWall[2], "low cloud by the walls is lit warm from below");
        assertTrue(highWall[2] > highWall[0], "high cloud is cool");
        float[] flash = {0f, 0f, 1f, 0f, 0f, 10f};
        float at = CeilingSky.sliceColour(0f, 10f, 0f, 41f, 16.5f, flash)[2];
        float above = CeilingSky.sliceColour(0f, 22f, 0f, 41f, 16.5f, flash)[2];
        float none = CeilingSky.sliceColour(0f, 10f, 0f, 41f, 16.5f, null)[2];
        assertTrue(at > none + 1f, "the slice at the strike lights up");
        assertTrue(at - none > 4 * (above - CeilingSky.sliceColour(0f, 22f, 0f, 41f, 16.5f, null)[2]),
                "and the light falls off with depth");
        float[] f = CeilingSky.flash(8L, CeilingSky.strikeStart(8L, 2) + 1f);
        assertTrue(f[5] >= 6f && f[5] <= 18f, "strike height " + f[5]);
    }

    @Test
    void starsBarelyMoveWhileTheCloudSlides() {
        // The camera 10 blocks under the plane (the crane shot) moves 4 blocks sideways.
        double low = CeilingSky.parallax(4f, 10f + CeilingSky.SLICE_LOW);
        double cloud = CeilingSky.parallax(4f, 10f + 8f);
        double stars = CeilingSky.parallax(4f, 10f + CeilingSky.STAR_HEIGHT);
        assertTrue(low > cloud && cloud > stars);
        assertTrue(low > 5 * stars, "low " + low + " stars " + stars);
        assertTrue(CeilingSky.STAR_HEIGHT >= 60f && CeilingSky.STAR_HEIGHT <= 90f);
        assertTrue(CeilingSky.BACKDROP_HEIGHT > CeilingSky.STAR_HEIGHT);
    }

    @Test
    void planeSitsJustBelowTheRoof() {
        float y = CeilingSky.planeY(30);
        assertTrue(y < 30f && y > 29.9f, "plane at " + y);
    }
}
