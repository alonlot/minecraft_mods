package com.hogwartsmagic.pensieve.scene;

import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The real ceiling as a window onto vanilla's sky: its stars, sun, clouds, sunrise fan and fog, as vanilla has them. */
class CeilingSkyWindowTest {

    /** {@code LevelRenderer#drawStars}, transcribed with vanilla's own RandomSource: every corner, in order. */
    private static float[] vanillaReference() {
        RandomSource randomsource = RandomSource.create(10842L);
        List<Float> out = new ArrayList<>();
        for (int j = 0; j < 1500; j++) {
            float f1 = randomsource.nextFloat() * 2.0F - 1.0F;
            float f2 = randomsource.nextFloat() * 2.0F - 1.0F;
            float f3 = randomsource.nextFloat() * 2.0F - 1.0F;
            float f4 = 0.15F + randomsource.nextFloat() * 0.1F;
            float f5 = f1 * f1 + f2 * f2 + f3 * f3;
            if (!(f5 <= 0.010000001F) && !(f5 >= 1.0F)) {
                Vector3f vector3f = new Vector3f(f1, f2, f3).normalize(100.0F);
                float f6 = (float) (randomsource.nextDouble() * (float) Math.PI * 2.0);
                Quaternionf quaternionf = new Quaternionf().rotateTo(new Vector3f(0.0F, 0.0F, -1.0F), vector3f)
                        .rotateZ(f6);
                Vector3f[] corners = {
                        new Vector3f(vector3f.add(new Vector3f(f4, -f4, 0.0F).rotate(quaternionf))),
                        new Vector3f(vector3f.add(new Vector3f(f4, f4, 0.0F).rotate(quaternionf))),
                        new Vector3f(vector3f.add(new Vector3f(-f4, f4, 0.0F).rotate(quaternionf))),
                        new Vector3f(vector3f.add(new Vector3f(-f4, -f4, 0.0F).rotate(quaternionf)))};
                for (Vector3f c : corners) {
                    out.add(c.x);
                    out.add(c.y);
                    out.add(c.z);
                }
            }
        }
        float[] a = new float[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    @Test
    void vanillaStarsAreVanillasStars() {
        float[] ours = CeilingSky.vanillaStars();
        float[] ref = vanillaReference();
        assertEquals(ref.length, ours.length, "same star count");
        int stars = ours.length / CeilingSky.STAR_STRIDE;
        assertTrue(stars > 700 && stars < 860, "about pi/6 of 1500 tries land in the ball: " + stars);
        // The first stars' directions, and every corner of every star, bit for bit.
        assertArrayEquals(ref, ours, 0f);
    }

    @Test
    void vanillaStarsLieOnTheSkySphere() {
        float[] s = CeilingSky.vanillaStars();
        for (int i = 0; i < s.length; i += 3) {
            float len = (float) Math.sqrt(s[i] * s[i] + s[i + 1] * s[i + 1] + s[i + 2] * s[i + 2]);
            assertEquals(100f, len, 0.6f);
        }
    }

    @Test
    void theLcgIsVanillasRandomSource() {
        RandomSource ref = RandomSource.create(10842L);
        CeilingSky.LegacyLcg ours = new CeilingSky.LegacyLcg(10842L);
        for (int i = 0; i < 200; i++) {
            assertEquals(ref.nextFloat(), ours.nextFloat(), 0f);
            assertEquals(ref.nextDouble(), ours.nextDouble(), 0.0);
        }
    }

    @Test
    void sunIsStraightUpAtNoon() {
        float[] d = new float[3];
        CeilingSky.sunDirection(0f, d);
        assertArrayEquals(new float[]{0f, 1f, 0f}, d, 1e-6f);
        // The sky's own frame at noon (sun angle 0) is the world's: the sun's quad centre straight overhead.
        float[] w = new float[3];
        CeilingSky.skyToWorld(1f, 0f, 0f, CeilingSky.SKY_DISTANCE, 0f, w, 0);
        assertArrayEquals(new float[]{0f, 100f, 0f}, w, 1e-5f);
        // The moon's straight down.
        CeilingSky.skyToWorld(1f, 0f, 0f, -CeilingSky.SKY_DISTANCE, 0f, w, 0);
        assertArrayEquals(new float[]{0f, -100f, 0f}, w, 1e-5f);
    }

    @Test
    void theSunSetsInTheWestAndTheSkyRotationAgreesWithSunDirection() {
        float[] d = new float[3], w = new float[3];
        for (float tod = 0f; tod < 1f; tod += 0.07f) {
            float a = (float) (tod * Math.PI * 2.0);
            CeilingSky.sunDirection(tod, d);
            CeilingSky.skyToWorld((float) Math.cos(a), (float) Math.sin(a), 0f, 1f, 0f, w, 0);
            assertArrayEquals(d, w, 1e-5f);
        }
        CeilingSky.sunDirection(0.25f, d);                     // sunset: on the western (-x) horizon
        assertArrayEquals(new float[]{-1f, 0f, 0f}, d, 1e-5f);
    }

    @Test
    void aRayReachesThePlaneWhereItShould() {
        float[] out = {9f, 9f};
        assertTrue(CeilingSky.rayToPlane(1f, 2f, -3f, 10f, out));
        assertArrayEquals(new float[]{5f, -15f}, out, 1e-5f);
        // Length does not matter, only direction.
        assertTrue(CeilingSky.rayToPlane(10f, 20f, -30f, 10f, out));
        assertArrayEquals(new float[]{5f, -15f}, out, 1e-4f);
        float[] keep = {7f, 7f};
        assertFalse(CeilingSky.rayToPlane(1f, -1f, 0f, 10f, keep), "a falling ray");
        assertFalse(CeilingSky.rayToPlane(1f, 0f, 0f, 10f, keep), "a level ray");
        assertFalse(CeilingSky.rayToPlane(0f, 1f, 0f, -2f, keep), "a plane below the camera");
        assertFalse(CeilingSky.rayToPlane(0f, Float.NaN, 0f, 5f, keep), "NaN");
        assertArrayEquals(new float[]{7f, 7f}, keep, 0f);
    }

    @Test
    void planeHitInsideTheRectangleOnly() {
        float[] hit = new float[3];
        assertTrue(CeilingSky.planeHit(0f, 0f, 10f, 0f, 1f, 0f, 5f, 5f, hit));
        assertArrayEquals(new float[]{0f, 0f, 10f}, hit, 1e-5f);
        float s = (float) Math.sqrt(0.5);
        assertFalse(CeilingSky.planeHit(0f, 0f, 10f, s, s, 0f, 5f, 5f, hit), "lands 10 out, past the edge");
        assertFalse(CeilingSky.planeHit(0f, 0f, 15f, 1f, 0.005f, 0f, 1000f, 1000f, hit), "grazing");
    }

    @Test
    void aLayerOnThePlaneSamplesThePlanePointAndDeeperOnesFurtherOut() {
        float[] out = new float[2];
        CeilingSky.parallaxPoint(3f, -2f, 10f, 7f, 16f, 0f, out);
        assertArrayEquals(new float[]{10f, 7f}, out, 1e-5f);
        CeilingSky.parallaxPoint(0f, 0f, 4f, 2f, 16f, 16f, out);
        assertArrayEquals(new float[]{8f, 4f}, out, 1e-5f);
    }

    @Test
    void theCloudTexelIsVanillasOffset() {
        double camX = 1234.5, camZ = -987.25;
        float ticks = 54321.5f;
        float ay = 15f, h = 100f, px = 3f, pz = -7f;
        float[] base = new float[2], texel = new float[2];
        CeilingSky.cloudBase(camX, camZ, ticks, base);
        CeilingSky.cloudTexel(base, px, pz, ay, h, texel);
        // Vanilla (renderClouds + buildClouds): the cloud point is where the ray through the plane point reaches the
        // layer; its local position in the cloud model is rel / 12 + f3, and its UV local / 256 + floor(d2) / 256.
        double d1 = (double) (ticks * 0.03F);
        double d2 = (camX + d1) / 12.0;
        double d4 = camZ / 12.0 + 0.33F;
        d2 -= Math.floor(d2 / 2048.0) * 2048;
        d4 -= Math.floor(d4 / 2048.0) * 2048;
        float f3 = (float) (d2 - Math.floor(d2));
        float f5 = (float) (d4 - Math.floor(d4));
        float k = (ay + h) / ay;
        float relX = px * k, relZ = pz * k;
        float lx = relX / 12f + f3, lz = relZ / 12f + f5;
        float u = lx * 0.00390625F + (float) Math.floor(d2) * 0.00390625F;
        float v = lz * 0.00390625F + (float) Math.floor(d4) * 0.00390625F;
        assertEquals(wrap(u * 256f), wrap(texel[0]), 2e-3f);
        assertEquals(wrap(v * 256f), wrap(texel[1]), 2e-3f);
        // And the drift: 0.03 blocks a tick, 12 blocks a texel, along x only.
        float[] later = new float[2];
        CeilingSky.cloudBase(camX, camZ, ticks + 400f, later);
        assertEquals(1f, wrap(later[0] - base[0]), 2e-3f);
        assertEquals(0f, wrap(later[1] - base[1] + 0.5f) - 0.5f, 1e-4f);
    }

    private static float wrap(float t) {
        return (float) (t - Math.floor(t / 256.0) * 256.0);
    }

    @Test
    void theSunriseFanBlendsTowardTheSunOnly() {
        float[] fan = new float[CeilingSky.FAN_FLOATS];
        float alpha = 0.8f;
        CeilingSky.sunriseFan(true, alpha, fan);
        assertEquals(-100f, fan[0], 1e-4f, "dusk: the fan centres on the western horizon");
        // A ray through a point inside a triangle (half the centre, a quarter of two rim points) gets half the alpha.
        float px = 0.5f * fan[0] + 0.25f * fan[3] + 0.25f * fan[6];
        float py = 0.5f * fan[1] + 0.25f * fan[4] + 0.25f * fan[7];
        float pz = 0.5f * fan[2] + 0.25f * fan[5] + 0.25f * fan[8];
        assertEquals(0.5f * alpha, CeilingSky.sunriseAlpha(fan, alpha, px, py, pz), 1e-3f);
        assertEquals(0f, CeilingSky.sunriseAlpha(fan, alpha, 0f, 1f, 0f), 0f, "nothing overhead");
        assertEquals(0f, CeilingSky.sunriseAlpha(fan, alpha, 1f, 0.3f, 0f), 0f, "nothing on the far side");
        CeilingSky.sunriseFan(false, alpha, fan);
        assertEquals(100f, fan[0], 1e-4f, "dawn: the eastern horizon");
    }

    @Test
    void theSkyFogsTowardTheHorizonOnly() {
        assertTrue(CeilingSky.skyFog(1f, 0f, 192f) < 0.03f, "overhead");
        assertEquals(1f, CeilingSky.skyFog(0f, 1f, 192f), 0f, "level");
        float prev = 0f;
        for (float e = 1.5f; e > 0.01f; e -= 0.05f) {
            float f = CeilingSky.skyFog((float) Math.sin(e), (float) Math.cos(e), 192f);
            assertTrue(f >= prev - 1e-6f && f <= 1f);
            prev = f;
        }
        assertEquals(0f, CeilingSky.terrainFog(10f, 170f, 192f), 0f);
        assertEquals(1f, CeilingSky.terrainFog(300f, 170f, 192f), 0f);
    }

    @Test
    void theRenderersSkyFogEndIsADistanceSoTheWindowShowsTheSkyColour() {
        // The renderer takes its fog end through skyFogEnd from the render distance in blocks (12 chunks: 192).
        assertEquals(32f, CeilingSky.skyFogEnd(32f), 0f, "a distance, not clamped to 0..1");
        for (float rd : new float[]{128f, 192f, 512f}) {
            float end = CeilingSky.skyFogEnd(rd);
            assertEquals(rd, end, 0f, "a distance, not clamped to 0..1");
            // A ray up from the hall floor to the middle of the ceiling (16 up, a few blocks across): mostly sky colour.
            float len = (float) Math.sqrt(16f * 16f + 4f * 4f);
            assertTrue(CeilingSky.skyFog(16f / len, 4f / len, end) < 0.1f, "zenith-ish at render distance " + rd);
            // Straight up is all but pure sky colour; a ray toward the far end of the hall (40 out) a little hazier.
            assertTrue(CeilingSky.skyFog(1f, 0f, end) < 0.05f);
            float far = (float) Math.sqrt(16f * 16f + 40f * 40f);
            assertTrue(CeilingSky.skyFog(16f / far, 40f / far, end) < 0.5f);
        }
        assertEquals(CeilingSky.SKY_FOG_END_FALLBACK, CeilingSky.skyFogEnd(Float.NaN), 0f);
        assertEquals(CeilingSky.SKY_FOG_END_FALLBACK, CeilingSky.skyFogEnd(0f), 0f);
        assertEquals(CeilingSky.SKY_FOG_END_FALLBACK, CeilingSky.skyFogEnd(-5f), 0f);
        assertEquals(CeilingSky.SKY_FOG_END_FALLBACK, CeilingSky.skyFogEnd(Float.POSITIVE_INFINITY), 0f);
    }

    @Test
    void fancyCloudUndersidesAreShadedAsVanillaShadesThem() {
        assertEquals(0.7f, CeilingSky.cloudUnderside(true), 0f);
        assertEquals(1f, CeilingSky.cloudUnderside(false), 0f);
    }

    @Test
    void motesStayUnderTheCeilingAndFade() {
        float[] m = new float[4], again = new float[4];
        for (int i = 0; i < CeilingSky.moteCount(16f, 36.5f); i++) {
            for (float t = 0f; t < 3000f; t += 37f) {
                CeilingSky.mote(42L, i, t, 16f, 36.5f, m);
                assertTrue(Math.abs(m[0]) <= 16f && Math.abs(m[1]) <= 36.5f);
                assertTrue(m[2] >= 0.3f && m[2] <= 1.5f);
                assertTrue(m[3] >= 0f && m[3] <= 1f);
                CeilingSky.mote(42L, i, t, 16f, 36.5f, again);
                assertArrayEquals(m, again, 0f);
            }
        }
        assertEquals(20, CeilingSky.moteCount(16f, 36.5f));
        assertEquals(4, CeilingSky.moteCount(2f, 2f));
    }

    @Test
    void starMagicIsGentle() {
        float[] c = new float[3];
        int coloured = 0;
        for (int i = 0; i < 800; i++) {
            for (float t = 0f; t < 600f; t += 13f) {
                float tw = CeilingSky.starTwinkle(i, t);
                assertTrue(tw >= 0.8f - 1e-5f && tw <= 1.1f + 1e-5f);
            }
            CeilingSky.starTint(i, c);
            if (c[0] < 1f || c[1] < 1f || c[2] < 1f) coloured++;
        }
        assertTrue(coloured > 40 && coloured < 140, "about one in nine faintly coloured: " + coloured);
    }
}
