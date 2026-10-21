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
        assertEquals("day", SkyDome.style("storm"));
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

    @Test
    void cloudDriftIsASceneClockFunction() {
        assertEquals(SkyDome.cloudDrift(123.5f), SkyDome.cloudDrift(123.5f), 0f);
        assertTrue(SkyDome.cloudDrift(200f) > SkyDome.cloudDrift(100f));
    }
}
