package com.hogwartsmagic.pensieve.scene;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The real ceiling's depth: parallax layers, the sun and moon on the plane, the atmosphere, star colour, the volume. */
class CeilingSkyDepthTest {

    private static final float[] NOON = CeilingSky.realWeights(6000, 0f, 0f);
    private static final float[] MIDNIGHT = CeilingSky.realWeights(18000, 0f, 0f);
    private static final float[] SUNSET = CeilingSky.realWeights(12500, 0f, 0f);

    @Test
    void aLayerOnThePlaneSamplesThePlanePointItself() {
        float[] out = new float[2];
        CeilingSky.parallaxPoint(3f, -2f, 10f, 7f, 16f, 0f, out);
        assertEquals(10f, out[0], 1e-5f);
        assertEquals(7f, out[1], 1e-5f);
    }

    @Test
    void deeperLayersSampleFurtherOutAlongTheRay() {
        float[] a = new float[2], b = new float[2];
        CeilingSky.parallaxPoint(0f, 0f, 4f, 2f, 16f, CeilingSky.DECK_H, a);
        CeilingSky.parallaxPoint(0f, 0f, 4f, 2f, 16f, CeilingSky.STAR_LAYER_H, b);
        // On the ray: the same direction from the camera, (dy + h) / dy times as far.
        assertEquals(4f * (16f + 50f) / 16f, a[0], 1e-4f);
        assertEquals(2f * (16f + 50f) / 16f, a[1], 1e-4f);
        assertTrue(b[0] > a[0] && b[1] > a[1]);
        // The camera's own column samples straight up whatever the depth.
        CeilingSky.parallaxPoint(5f, 5f, 5f, 5f, 16f, CeilingSky.STAR_LAYER_H, b);
        assertArrayEquals(new float[]{5f, 5f}, b, 1e-5f);
    }

    @Test
    void planePointUndoesParallaxPoint() {
        float[] s = new float[2], p = new float[2];
        CeilingSky.parallaxPoint(-6f, 9f, 11f, -3f, 12f, CeilingSky.CIRRUS_H, s);
        CeilingSky.planePoint(-6f, 9f, s[0], s[1], 12f, CeilingSky.CIRRUS_H, p);
        assertEquals(11f, p[0], 1e-3f);
        assertEquals(-3f, p[1], 1e-3f);
    }

    @Test
    void walkingUnderTheSkyFarLayersSwingLessThanNearOnes() {
        // A fixed feature of each layer, straight above the camera at the start; the camera then walks 10 blocks.
        // How far its direction swings (radians) shrinks as the layer gets deeper: the stars barely move.
        float[] p = new float[2];
        float dy = 16f, walk = 10f;
        float previous = Float.MAX_VALUE;
        for (float h : new float[]{0f, CeilingSky.DECK_H, CeilingSky.CIRRUS_H, CeilingSky.STAR_LAYER_H}) {
            CeilingSky.planePoint(walk, 0f, 0f, 0f, dy, h, p);
            double swing = Math.atan2(Math.abs(p[0] - walk), dy);
            assertTrue(swing < previous, "layer " + h + " swung " + swing + ", not less than " + previous);
            previous = (float) swing;
        }
        // On the plane itself a point 10 blocks back swings by atan(10 / 16); the stars by a small fraction of it.
        CeilingSky.planePoint(walk, 0f, 0f, 0f, dy, CeilingSky.STAR_LAYER_H, p);
        assertTrue(Math.atan2(Math.abs(p[0] - walk), dy) < 0.1 * Math.atan2(walk, dy));
    }

    @Test
    void parallaxDoesNotRaceAsTheCameraNearsThePlane() {
        // Closer than the floor (or above the plane) the scale stops growing, and stays finite and positive.
        float atMin = CeilingSky.parallaxScale(CeilingSky.PARALLAX_MIN_DY, CeilingSky.STAR_LAYER_H);
        assertEquals(atMin, CeilingSky.parallaxScale(0.5f, CeilingSky.STAR_LAYER_H), 1e-5f);
        assertEquals(atMin, CeilingSky.parallaxScale(-3f, CeilingSky.STAR_LAYER_H), 1e-5f);
        assertEquals(1f, CeilingSky.parallaxScale(16f, 0f), 1e-6f);
        assertTrue(CeilingSky.layerTile(20f, CeilingSky.DECK_H) > 20f);
    }

    @Test
    void theSunIsStraightUpAtNoonAndRisesInTheEast() {
        float[] d = new float[3];
        CeilingSky.sunDirection(0f, d);
        assertArrayEquals(new float[]{0f, 1f, 0f}, d, 1e-6f);
        CeilingSky.sunDirection(0.75f, d);                         // sunrise: due east, on the horizon
        assertEquals(1f, d[0], 1e-5f);
        assertEquals(0f, d[1], 1e-5f);
        CeilingSky.sunDirection(0.5f, d);                          // midnight: straight down (the moon is up)
        assertEquals(-1f, d[1], 1e-5f);
    }

    @Test
    void theNoonSunMeetsThePlaneRightAboveTheCamera() {
        float[] d = new float[3], hit = new float[3];
        CeilingSky.sunDirection(0f, d);
        assertTrue(CeilingSky.planeHit(3f, -7f, 15f, d[0], d[1], d[2], 16f, 36f, hit));
        assertEquals(3f, hit[0], 1e-4f);
        assertEquals(-7f, hit[1], 1e-4f);
        assertEquals(15f, hit[2], 1e-4f);
    }

    @Test
    void aSunOffTheRectangleIsNotDrawn() {
        float[] d = new float[3], hit = {9f, 9f, 9f};
        CeilingSky.sunDirection(0.80f, d);                         // low in the eastern morning sky
        assertFalse(CeilingSky.planeHit(0f, 0f, 15f, d[0], d[1], d[2], 16f, 36f, hit));
        assertArrayEquals(new float[]{9f, 9f, 9f}, hit, 0f, "a miss leaves the output alone");
        // Below the horizon, or the camera above the plane: no hit either.
        CeilingSky.sunDirection(0.5f, d);
        assertFalse(CeilingSky.planeHit(0f, 0f, 15f, d[0], d[1], d[2], 16f, 36f, hit));
        CeilingSky.sunDirection(0f, d);
        assertFalse(CeilingSky.planeHit(0f, 0f, -2f, d[0], d[1], d[2], 16f, 36f, hit));
        // Mid-morning it lands off to the east, inside a long hall from its west end.
        CeilingSky.sunDirection(0.92f, d);
        assertTrue(CeilingSky.planeHit(-14f, 0f, 15f, d[0], d[1], d[2], 16f, 36f, hit));
        assertTrue(hit[0] > -14f);
    }

    @Test
    void insideFadeIsWholeInTheMiddleAndGoneAtTheEdge() {
        assertEquals(1f, CeilingSky.insideFade(0f, 0f, 16f, 36f, 2f), 1e-6f);
        assertEquals(0f, CeilingSky.insideFade(16f, 0f, 16f, 36f, 2f), 1e-6f);
        float mid = CeilingSky.insideFade(15f, 0f, 16f, 36f, 2f);
        assertTrue(mid > 0f && mid < 1f);
    }

    @Test
    void theDaySkyIsDeepBlueOverheadAndPaleAtTheHorizon() {
        float[] sun = {0.6f, 0.8f, 0f};
        float[] zenith = new float[3], horizon = new float[3];
        CeilingSky.atmosphere(0f, 1f, 0f, sun[0], sun[1], sun[2], NOON, zenith);
        // A ray grazing toward a wall, well away from the sun.
        CeilingSky.atmosphere(0f, 0.35f, -0.937f, sun[0], sun[1], sun[2], NOON, horizon);
        assertTrue(zenith[2] > zenith[0] * 2f, "overhead is blue");
        assertTrue(lum(horizon) > lum(zenith) + 0.12f, "the horizon is paler");
        assertTrue(horizon[2] - horizon[0] < zenith[2] - zenith[0], "and less saturated");
    }

    @Test
    void theDaySkyBrightensRoundTheSun() {
        float[] sun = {0f, 1f, 0f};
        float[] at = new float[3], away = new float[3];
        CeilingSky.atmosphere(0f, 1f, 0f, sun[0], sun[1], sun[2], NOON, at);
        CeilingSky.atmosphere(0.6f, 0.8f, 0f, sun[0], sun[1], sun[2], NOON, away);
        assertTrue(lum(at) > lum(away));
    }

    @Test
    void theDuskHorizonGlowsTowardTheSun() {
        float[] sun = {-0.99f, 0.14f, 0f};                       // just above the western horizon
        float[] toward = new float[3], away = new float[3];
        CeilingSky.atmosphere(-0.85f, 0.5f, 0.17f, sun[0], sun[1], sun[2], SUNSET, toward);
        CeilingSky.atmosphere(0.85f, 0.5f, 0.17f, sun[0], sun[1], sun[2], SUNSET, away);
        assertTrue(toward[0] > away[0] + 0.15f, "warmer toward the sun");
        assertTrue(toward[0] > toward[2], "orange, not blue");
    }

    @Test
    void theNightIsDarkNavyAndRainGreysTheDay() {
        float[] sun = {0.6f, -0.8f, 0f};                       // the moon up, but not overhead
        float[] night = new float[3], day = new float[3], rain = new float[3];
        CeilingSky.atmosphere(0f, 1f, 0f, sun[0], sun[1], sun[2], MIDNIGHT, night);
        assertTrue(lum(night) < 0.1f && night[2] > night[0]);
        CeilingSky.atmosphere(0f, 1f, 0f, 0f, 1f, 0f, NOON, day);
        CeilingSky.atmosphere(0f, 1f, 0f, 0f, 1f, 0f, CeilingSky.realWeights(6000, 1f, 0f), rain);
        assertTrue(rain[2] - rain[0] < 0.5f * (day[2] - day[0]), "rain washes the blue toward grey");
        for (float c : day) assertTrue(c >= 0f && c <= 1f);
    }

    @Test
    void cloudsAreWhiteByDayWarmAtDuskAndDimAtNight() {
        float[] c = new float[3];
        CeilingSky.cloudTint(0f, 1f, 0f, 0f, 1f, 0f, NOON, 0f, c);
        assertTrue(c[0] > 0.9f && c[1] > 0.9f && c[2] > 0.9f);
        float[] sun = {-0.99f, 0.14f, 0f};
        CeilingSky.cloudTint(-0.8f, 0.6f, 0f, sun[0], sun[1], sun[2], SUNSET, 0f, c);
        assertTrue(c[0] > c[2] + 0.3f, "sunlit amber toward the sun");
        CeilingSky.cloudTint(0f, 1f, 0f, 0f, -1f, 0f, MIDNIGHT, 0f, c);
        assertTrue(lum(c) < 0.4f);
    }

    @Test
    void cloudSlicesAreLitFromAboveAndOnTheSunSide() {
        // Higher in the stack is brighter, at equal density.
        assertTrue(CeilingSky.cloudLight(0.6f, 0.6f, 1f) > CeilingSky.cloudLight(0.6f, 0.6f, 0f));
        // Less cloud toward the light: the lit side. More: in the shade.
        assertTrue(CeilingSky.cloudLight(0.6f, 0.3f, 0.5f) > CeilingSky.cloudLight(0.6f, 0.9f, 0.5f));
        for (float h = 0f; h <= 1f; h += 0.25f) {
            float l = CeilingSky.cloudLight(1f, 0f, h);
            assertTrue(l >= 0.15f && l <= 1f);
        }
        float[] lit = new float[3], shade = new float[3];
        CeilingSky.volumeCloudColour(1f, NOON, lit);
        CeilingSky.volumeCloudColour(0.15f, NOON, shade);
        assertTrue(lum(lit) > lum(shade) + 0.2f);
    }

    @Test
    void theSunIsGoldenLowAndWhiteHigh() {
        float[] low = new float[3], high = new float[3];
        CeilingSky.sunColour(0.02f, low);
        CeilingSky.sunColour(1f, high);
        assertTrue(low[2] < 0.4f && high[2] > 0.8f);
    }

    @Test
    void starsRangeFromBlueToOrangeMostlyWhite() {
        float[] c = new float[3];
        int blue = 0, warm = 0, white = 0;
        for (int i = 0; i < 2000; i++) {
            CeilingSky.starColour(42L, i, c);
            for (float x : c) assertTrue(x >= 0f && x <= 1f);
            assertEquals(1f, Math.max(c[0], Math.max(c[1], c[2])), 1e-6f);
            if (c[2] - c[0] > 0.15f) blue++;
            else if (c[0] - c[2] > 0.25f) warm++;
            else if (c[0] - c[2] < 0.08f && c[2] - c[0] < 0.08f) white++;
        }
        assertTrue(blue > 40 && warm > 40, "both tails: blue " + blue + ", warm " + warm);
        assertTrue(white > blue && white > warm, "white leads: " + white);
    }

    @Test
    void onlyBrightStarsComeOutAtDusk() {
        assertEquals(1f, CeilingSky.starVisibility(1f, 0.12f), 1e-6f);
        assertTrue(CeilingSky.starVisibility(0.3f, 1f) > 0.3f);
        assertEquals(0f, CeilingSky.starVisibility(0.3f, 0.12f), 1e-6f);
        assertEquals(0f, CeilingSky.starVisibility(0f, 1f), 1e-6f);
    }

    @Test
    void theRealTwinkleIsGentle() {
        for (int i = 0; i < 200; i++) {
            for (float t = 0f; t < 400f; t += 37f) {
                float overhead = CeilingSky.realTwinkle(9L, t, i, 1f);
                float low = CeilingSky.realTwinkle(9L, t, i, 0.3f);
                assertTrue(overhead >= 0.74f && overhead <= 1f, "overhead " + overhead);
                assertTrue(low >= 0.55f && low <= 1f, "low " + low);
                assertTrue(low <= overhead + 1e-6f, "low stars flicker more");
            }
        }
    }

    @Test
    void theStarFieldCrowdsTheBandAndStaysInItsTile() {
        float[] f = CeilingSky.starField(5L);
        int n = CeilingSky.fieldCount();
        assertEquals(n * 3, f.length);
        assertTrue(n > 1000);
        int near = 0;
        for (int i = 0; i < n; i++) {
            float a = f[i * 3], b = f[i * 3 + 1], s = f[i * 3 + 2];
            assertTrue(a >= 0f && a < CeilingSky.FIELD_ALONG);
            assertTrue(b >= -CeilingSky.FIELD_ACROSS / 2 && b < CeilingSky.FIELD_ACROSS / 2);
            assertTrue(s > 0f && s <= 1f);
            if (Math.abs(b) < CeilingSky.FIELD_BAND_WIDTH) near++;
        }
        float uniform = n * 2f * CeilingSky.FIELD_BAND_WIDTH / CeilingSky.FIELD_ACROSS;
        assertTrue(near > 2f * uniform, "the band is crowded: " + near + " vs " + uniform);
        assertArrayEquals(f, CeilingSky.starField(5L), "the same seed, the same sky");
        // The band glow is on the spine, repeats with the tile, and fades off it.
        assertEquals(1f, CeilingSky.fieldBandGlow(0f), 1e-6f);
        assertEquals(1f, CeilingSky.fieldBandGlow(CeilingSky.FIELD_ACROSS), 1e-5f);
        assertTrue(CeilingSky.fieldBandGlow(3f * CeilingSky.FIELD_BAND_WIDTH) < 0.01f);
    }

    @Test
    void theVolumeFitsUnderTheRoof() {
        assertEquals(0, CeilingSky.volumeHeights(0f).length, "a spell ceiling has no room");
        assertEquals(0, CeilingSky.volumeHeights(2f).length);
        float[] h = CeilingSky.volumeHeights(17f);
        assertEquals(CeilingSky.VOLUME_SLICES, h.length);
        for (int i = 0; i < h.length; i++) {
            assertTrue(h[i] >= CeilingSky.VOLUME_LOW && h[i] <= 17f - 1f);
            if (i > 0) assertTrue(h[i] > h[i - 1]);
        }
        // A low slice spans most of the hall; a high one only the middle, where the roof is still above it.
        assertEquals(1f, CeilingSky.volumeFade(1.5f, 17f, 0f, 0f, 16f, 36f), 1e-6f);
        assertTrue(CeilingSky.volumeFade(1.5f, 17f, 0.6f, 0f, 16f, 36f) > 0.9f);
        assertEquals(0f, CeilingSky.volumeFade(11f, 17f, 0.7f, 0f, 16f, 36f), 1e-6f);
        assertTrue(CeilingSky.volumeFade(11f, 17f, 0f, 0.5f, 16f, 36f) > 0.9f, "along the ridge it stays");
        assertEquals(0f, CeilingSky.volumeFade(1.5f, 0f, 0f, 0f, 16f, 36f), 1e-6f);
    }

    @Test
    void volumeCloudHasGapsThatRainCloses() {
        assertEquals(0f, CeilingSky.volumeAlpha(0.4f, 0f, 0f), 1e-6f, "a gap in fair weather");
        assertTrue(CeilingSky.volumeAlpha(0.4f, 0f, 1f) > 0f, "rain fills it");
        assertTrue(CeilingSky.volumeAlpha(0.9f, 0f, 0f) > CeilingSky.volumeAlpha(0.9f, 1f, 0f), "thinner higher up");
        for (float d = 0f; d <= 1f; d += 0.1f) assertTrue(CeilingSky.volumeAlpha(d, 0f, 1f) <= 0.85f);
    }

    private static float lum(float[] c) {
        return 0.30f * c[0] + 0.59f * c[1] + 0.11f * c[2];
    }
}
