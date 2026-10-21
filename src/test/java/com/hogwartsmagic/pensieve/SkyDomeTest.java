package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.SkyDome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyDomeTest {
    private static float luma(float[] c) {
        return 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2];
    }

    @Test
    void everyStyleHasAGradientFromHorizonToZenith() {
        for (String style : SkyDome.STYLES) {
            if ("storm".equals(style)) continue;   // a night-dark sky: stormGradient
            float[] h = SkyDome.colour(style, 0f);
            float[] z = SkyDome.colour(style, 1f);
            assertArrayEquals(SkyDome.horizon(style), h, 1e-6f, style);
            assertTrue(luma(h) > 0.55f, style + " horizon is a daylight colour, not dark: " + luma(h));
            assertTrue(luma(z) > 0.3f, style + " zenith is a daylight colour: " + luma(z));
            // real sky, not a saturated primary
            for (float v : h) assertTrue(v >= 0.3f && v <= 1f, style + " horizon channel " + v);
            for (float v : z) assertTrue(v >= 0.3f && v <= 1f, style + " zenith channel " + v);
            // the gradient is monotone in each channel's distance from the horizon colour
            float prev = 0f;
            for (int i = 1; i <= 10; i++) {
                float[] c = SkyDome.colour(style, i / 10f);
                float d = Math.abs(c[0] - h[0]) + Math.abs(c[1] - h[1]) + Math.abs(c[2] - h[2]);
                assertTrue(d >= prev - 1e-6f, style + " step " + i);
                prev = d;
            }
        }
    }

    @Test
    void dayIsBlueOverheadAndPaleAtTheHorizon() {
        float[] h = SkyDome.colour("day", 0f), z = SkyDome.colour("day", 1f);
        assertTrue(z[2] > z[0] + 0.25f, "zenith is blue");
        assertTrue(h[0] > z[0] && h[1] > z[1], "the horizon is paler than the zenith");
        assertTrue(h[2] - h[0] < 0.2f, "the horizon is hazy, nearly white");
    }

    @Test
    void overcastIsNeutralGrey() {
        for (float e : new float[] {0f, 0.5f, 1f}) {
            float[] c = SkyDome.colour("overcast", e);
            assertTrue(Math.max(c[0], Math.max(c[1], c[2])) - Math.min(c[0], Math.min(c[1], c[2])) < 0.08f);
        }
    }

    @Test
    void dawnIsWarmAtTheHorizon() {
        float[] h = SkyDome.colour("dawn", 0f);
        assertTrue(h[0] > h[2] + 0.2f, "warm horizon");
        assertTrue(SkyDome.colour("dawn", 1f)[2] > SkyDome.colour("dawn", 1f)[0], "cool zenith");
    }

    @Test
    void elevationIsClamped() {
        assertArrayEquals(SkyDome.colour("day", 0f), SkyDome.colour("day", -3f), 1e-6f);
        assertArrayEquals(SkyDome.colour("day", 1f), SkyDome.colour("day", 4f), 1e-6f);
    }

    @Test
    void anUnknownOrMissingStyleIsDay() {
        assertEquals("day", SkyDome.style(null));
        assertEquals("day", SkyDome.style("blizzard"));
        assertEquals("storm", SkyDome.style("storm"));
        assertEquals("dawn", SkyDome.style("dawn"));
        assertArrayEquals(SkyDome.colour("day", 0.4f), SkyDome.colour(null, 0.4f), 1e-6f);
    }

    @Test
    void theSkyFadesInAndOut() {
        assertEquals(0f, SkyDome.fade(-5f, 0), 1e-6f);
        assertEquals(0f, SkyDome.fade(0f, 0), 1e-6f);
        assertEquals(1f, SkyDome.fade(SkyDome.FADE_TICKS, 0), 1e-6f);
        assertEquals(1f, SkyDome.fade(100000f, 0), 1e-6f, "duration 0 lasts until the scene ends");
        assertEquals(1f, SkyDome.fade(100f, 200), 1e-6f);
        assertEquals(0f, SkyDome.fade(200f, 200), 1e-6f);
        assertEquals(0f, SkyDome.fade(300f, 200), 1e-6f);
        assertTrue(SkyDome.fade(190f, 200) < 1f && SkyDome.fade(190f, 200) > 0f);
    }

    @Test
    void aSkyKeyedAtTickZeroShowsAtOnceAndOthersStillFadeIn() {
        assertEquals(1f, SkyDome.fade(0f, 0, 0), 1e-6f, "the scene's own sky is there on the first tick");
        assertEquals(1f, SkyDome.fade(3f, 0, 0), 1e-6f);
        assertEquals(0f, SkyDome.fade(-1f, 0, 0), 1e-6f, "but not before the scene");
        assertEquals(1f, SkyDome.fade(0f, 400, 0), 1e-6f);
        assertEquals(SkyDome.fade(390f, 400), SkyDome.fade(390f, 400, 0), 1e-6f, "the end still fades out");
        assertEquals(0f, SkyDome.fade(400f, 400, 0), 1e-6f);
        assertEquals(0f, SkyDome.fade(0f, 0, 200), 1e-6f, "a mid-scene sky starts from nothing");
        assertEquals(SkyDome.fade(7f, 0), SkyDome.fade(7f, 0, 200), 1e-6f, "and eases in as before");
        assertEquals(1f, SkyDome.fade(SkyDome.FADE_TICKS, 0, 200), 1e-6f);
    }

    @Test
    void domeSitsBeyondTheSetAndInsideTheFarPlane() {
        float r = SkyDome.domeRadius(120f, 512f);
        assertTrue(r > 120f && r < 512f, "" + r);
        assertTrue(SkyDome.domeRadius(200f, 512f) > 200f);
        assertTrue(SkyDome.domeRadius(200f, 200f) < 200f, "a short far plane wins: the dome must not be clipped");
        assertTrue(SkyDome.domeRadius(2f, 512f) >= 48f);
    }

    @Test
    void fogColourFollowsTheHorizonAsTheSkyFades() {
        assertArrayEquals(new float[] {0f, 0f, 0f}, SkyDome.fogColour("day", 0f), 1e-6f);
        assertArrayEquals(SkyDome.horizon("day"), SkyDome.fogColour("day", 1f), 1e-6f);
        float[] half = SkyDome.fogColour("day", 0.5f), h = SkyDome.horizon("day");
        assertEquals(h[1] * 0.5f, half[1], 1e-6f);
    }

    private static void assertHex(int rgb, float[] c) {
        assertEquals(((rgb >> 16) & 0xFF) / 255f, c[0], 0.5f / 255f);
        assertEquals(((rgb >> 8) & 0xFF) / 255f, c[1], 0.5f / 255f);
        assertEquals((rgb & 0xFF) / 255f, c[2], 0.5f / 255f);
    }

    @Test
    void stormGradient() {
        assertTrue(SkyDome.STYLES.contains("storm"));
        assertHex(0x4A525C, SkyDome.colour("storm", 0f));
        assertHex(0x262C35, SkyDome.colour("storm", 1f));
        assertHex(0x4A525C, SkyDome.horizon("storm"));
        assertHex(0x4A525C, SkyDome.fogColour("storm", 1f));
        // a dark grey DAY storm, not night: the zenith is not black and the horizon is readable grey
        assertTrue(luma(SkyDome.colour("storm", 1f)) > 0.13f && luma(SkyDome.colour("storm", 1f)) < 0.22f);
        assertTrue(luma(SkyDome.colour("storm", 0f)) > 0.25f && luma(SkyDome.colour("storm", 0f)) < 0.40f);
        assertTrue(luma(SkyDome.colour("storm", 0.5f)) < luma(SkyDome.colour("storm", 0f)));
        assertTrue(luma(SkyDome.colour("storm", 1f)) < luma(SkyDome.colour("storm", 0.5f)));
    }

    @Test
    void flashPeaksAfterStrike() {
        double[] strikes = {100.0};
        double peak = 0;
        for (double t = 100; t <= 126; t += 0.25) peak = Math.max(peak, SkyDome.flash(t, strikes));
        assertEquals(0.85, peak, 0.02, "peaks at 0.85");
        assertTrue(peak <= 0.85 + 1e-9);
        assertTrue(SkyDome.flash(101.0, strikes) > 0.8, "the first flicker is at the strike");
        // two or three separate flickers: count the rises
        int rises = 0;
        double prev = 0;
        boolean up = false;
        for (double t = 100; t < 126; t += 0.25) {
            double v = SkyDome.flash(t, strikes);
            if (v > prev + 1e-9) {
                if (!up && t > 100.5) rises++;
                up = true;
            } else if (v < prev - 1e-9) {
                up = false;
            }
            prev = v;
        }
        assertTrue(rises >= 1 && rises <= 2, "extra flickers after the first: " + rises);
        // the same strike later gives the same shape; another strike's later ones add as a maximum
        assertEquals(SkyDome.flash(104.0, strikes), SkyDome.flash(204.0, new double[] {200.0}), 1e-9);
        assertTrue(SkyDome.flash(301.0, new double[] {100.0, 300.0}) > 0.8);
    }

    @Test
    void flashIsZeroWithoutLightning() {
        assertEquals(0.0, SkyDome.flash(50.0, null), 0.0);
        assertEquals(0.0, SkyDome.flash(50.0, new double[0]), 0.0);
        assertEquals(0.0, SkyDome.flash(99.9, new double[] {100.0}), 0.0, "not before the strike");
    }

    @Test
    void flashFadesBy1_3s() {
        double[] strikes = {100.0};
        assertEquals(0.0, SkyDome.flash(100.0 + 26.0, strikes), 0.0);
        assertEquals(0.0, SkyDome.flash(400.0, strikes), 0.0);
        assertTrue(SkyDome.flash(100.0 + 25.0, strikes) < 0.02);
    }

    @Test
    void flashSharesCeilingSkysFlicker() {
        // C8: one flicker function, scaled: the peak of CeilingSky's shape is 1, the sky's is 0.85 of it.
        assertEquals(1.0, com.hogwartsmagic.pensieve.scene.CeilingSky.flicker(1f, 0.6f, 0f), 1e-6);
        assertEquals(0.0, com.hogwartsmagic.pensieve.scene.CeilingSky.flicker(-1f, 0.6f, 0.5f), 0.0);
        assertTrue(com.hogwartsmagic.pensieve.scene.CeilingSky.flicker(6f, 0.6f, 0f) > 0.55f, "the second flicker");
        assertEquals(0.0, com.hogwartsmagic.pensieve.scene.CeilingSky.flicker(
                com.hogwartsmagic.pensieve.scene.CeilingSky.FLASH_TICKS, 0.6f, 0.5f), 0.0);
    }

    @Test
    void stormWindIsAUnitDirectionFromTheSeed() {
        double[] a = SkyDome.stormWind(42L), b = SkyDome.stormWind(42L), c = SkyDome.stormWind(43L);
        assertEquals(1.0, Math.hypot(a[0], a[1]), 1e-9);
        assertEquals(a[0], b[0], 0.0);
        assertTrue(a[0] != c[0] || a[1] != c[1]);
        double[] d = SkyDome.stormDrift(100f, 1.0, 0.0);
        assertTrue(d[0] > 0 && d[1] == 0);
        assertTrue(SkyDome.stormDrift(200f, 1.0, 0.0)[0] > d[0]);
    }

    @Test
    void flashLiftsAColourTowardPaleBlue() {
        float[] base = SkyDome.colour("storm", 0f);
        assertArrayEquals(base, SkyDome.flashed(base, 0.0), 1e-6f);
        float[] lit = SkyDome.flashed(base, 0.85);
        assertTrue(lit[2] > base[2] + 0.3f && lit[2] >= lit[0]);
    }

    @Test
    void stormCloudsDriftAboutADegreeASecondOverhead() {
        double toDegPerSec = 180.0 / Math.PI * 20.0;
        double deck = SkyDome.cloudAngularSpeed(SkyDome.STORM_LAYERS[0][1], SkyDome.STORM_LAYERS[0][0],
                SkyDome.STORM_LAYERS[0][2], Math.PI / 2) * toDegPerSec;
        assertTrue(deck >= 0.8 && deck <= 2.0, "the deck overhead: " + deck + " deg/s");
        double prev = 0;
        for (float[] l : SkyDome.STORM_LAYERS) {
            double up = SkyDome.cloudAngularSpeed(l[1], l[0], l[2], Math.PI / 2) * toDegPerSec;
            double low = SkyDome.cloudAngularSpeed(l[1], l[0], l[2], Math.toRadians(30)) * toDegPerSec;
            assertTrue(up >= 0.8 && up <= 3.0, "overhead " + up);
            assertTrue(low < up && low <= 1.6, "at 30 degrees " + low);
            assertTrue(up >= prev - 1e-9, "lower layers sweep faster (scud under the deck): " + up);
            prev = up;
        }
    }

    @Test
    void flashLightsTheCloudNearestTheBolt() {
        assertEquals(1.0, SkyDome.flashWeight(1, 0, 5, 0), 1e-9);
        assertEquals(0.4, SkyDome.flashWeight(-1, 0, 5, 0), 1e-9);
        assertEquals(0.7, SkyDome.flashWeight(0, 1, 5, 0), 1e-9);
        assertEquals(1.0, SkyDome.flashWeight(1, 0, 0, 0), 1e-9, "degenerate: even");
    }

    @Test
    void stormFogClosesInAndOnlyInward() {
        assertArrayEquals(new float[] {0f, 100f}, SkyDome.stormFog(0f, 100f, 1f), 1e-6f, "never pushes fog out");
        float[] f = SkyDome.stormFog(60f, 400f, 1f);
        assertEquals(SkyDome.STORM_FOG_START, f[0], 1e-6f);
        assertEquals(SkyDome.STORM_FOG_END, f[1], 1e-6f);
        assertArrayEquals(new float[] {60f, 400f}, SkyDome.stormFog(60f, 400f, 0f), 1e-6f);
        assertEquals(320f, SkyDome.stormFog(60f, 400f, 0.5f)[1], 1e-3f);
    }

    @Test
    void rainSheetsAreThinAndFarOnesThicker() {
        assertEquals(SkyDome.STORM_BANDS.length, SkyDome.STORM_BAND_ALPHA.length);
        float sum = 0;
        for (int i = 0; i < SkyDome.STORM_BANDS.length; i++) {
            if (i > 0) {
                assertTrue(SkyDome.STORM_BANDS[i] > SkyDome.STORM_BANDS[i - 1]);
                assertTrue(SkyDome.STORM_BAND_ALPHA[i] >= SkyDome.STORM_BAND_ALPHA[i - 1]);
            }
            assertTrue(SkyDome.STORM_BAND_ALPHA[i] > 0f && SkyDome.STORM_BAND_ALPHA[i] <= 0.15f);
            sum += SkyDome.STORM_BAND_ALPHA[i];
        }
        assertTrue(sum < 0.35f, "all together still see-through");
    }

    @Test
    void cloudDriftIsASceneClockFunction() {
        assertEquals(SkyDome.cloudDrift(123.5f), SkyDome.cloudDrift(123.5f), 0f);
        assertTrue(SkyDome.cloudDrift(200f) > SkyDome.cloudDrift(100f));
    }
}
