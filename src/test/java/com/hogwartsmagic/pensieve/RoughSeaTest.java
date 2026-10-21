package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.RoughSea;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoughSeaTest {
    private static final long SEED = 0x5EA5EA5EAL;

    @Test
    void heightIsBoundedAndReallyRough() {
        Random r = new Random(1);
        double most = 0;
        for (int i = 0; i < 20000; i++) {
            double h = RoughSea.height(r.nextDouble() * 200 - 100, r.nextDouble() * 200 - 100, r.nextDouble() * 4000,
                    SEED + i % 7);
            assertTrue(Math.abs(h) <= RoughSea.MAX_AMPLITUDE + 1e-9, "height " + h);
            most = Math.max(most, Math.abs(h));
        }
        // Amplitude about 0.6 at intensity 1: the swells do pile up to most of that.
        assertTrue(most > 0.45, "the sea never rises above " + most);
        assertEquals(0.6, RoughSea.MAX_AMPLITUDE, 1e-9);
    }

    @Test
    void deterministicPerSeed() {
        double a = RoughSea.height(3.25, -7.5, 123.5, SEED);
        assertEquals(a, RoughSea.height(3.25, -7.5, 123.5, SEED));
        // Asking for another seed in between must not disturb the first (the wave set is cached per seed).
        RoughSea.height(0, 0, 0, SEED + 1);
        assertEquals(a, RoughSea.height(3.25, -7.5, 123.5, SEED));
        int differ = 0;
        for (int i = 0; i < 20; i++) {
            if (Math.abs(RoughSea.height(i * 1.7, i * -0.9, i * 13, SEED) - RoughSea.height(i * 1.7, i * -0.9, i * 13, SEED + 99)) > 1e-3) differ++;
        }
        assertTrue(differ > 15, "two seeds give the same sea");
    }

    @Test
    void continuousInTimeAndSpace() {
        Random r = new Random(2);
        for (int i = 0; i < 5000; i++) {
            double x = r.nextDouble() * 100 - 50, z = r.nextDouble() * 100 - 50, t = r.nextDouble() * 3000;
            double h = RoughSea.height(x, z, t, SEED);
            // A twentieth of a tick, a hundredth of a block: tiny changes.
            assertTrue(Math.abs(RoughSea.height(x, z, t + 0.05, SEED) - h) < 0.01);
            assertTrue(Math.abs(RoughSea.height(x + 0.01, z, t, SEED) - h) < 0.01);
            assertTrue(Math.abs(RoughSea.height(x, z + 0.01, t, SEED) - h) < 0.01);
        }
    }

    @Test
    void intensityScalesTheAmplitude() {
        Random r = new Random(3);
        for (int i = 0; i < 500; i++) {
            double x = r.nextDouble() * 60, z = r.nextDouble() * 60, t = r.nextDouble() * 900;
            double h = RoughSea.height(x, z, t, SEED);
            assertEquals(2 * h, RoughSea.height(x, z, t, SEED, 2.0), 1e-12);
            assertEquals(0.5 * h, RoughSea.height(x, z, t, SEED, 0.5), 1e-12);
        }
    }

    @Test
    void normalIsUnitAndMatchesTheSlope() {
        Random r = new Random(4);
        for (int i = 0; i < 500; i++) {
            double x = r.nextDouble() * 60, z = r.nextDouble() * 60, t = r.nextDouble() * 900;
            double[] n = RoughSea.normal(x, z, t, SEED);
            assertEquals(1.0, Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]), 1e-9);
            double e = 1e-4;
            double gx = (RoughSea.height(x + e, z, t, SEED) - RoughSea.height(x - e, z, t, SEED)) / (2 * e);
            double gz = (RoughSea.height(x, z + e, t, SEED) - RoughSea.height(x, z - e, t, SEED)) / (2 * e);
            assertEquals(-gx, n[0] / n[1], 2e-3);
            assertEquals(-gz, n[2] / n[1], 2e-3);
        }
    }

    @Test
    void theFastSineIsAccurate() {
        for (double a = -50; a < 50; a += 0.0137) {
            assertEquals(Math.sin(a), RoughSea.sin(a), 1e-6);
            assertEquals(Math.cos(a), RoughSea.cos(a), 1e-6);
        }
    }

    @Test
    void foamIsInRangeAndSitsOnCrests() {
        Random r = new Random(5);
        double crestFoam = 0, troughFoam = 0;
        int crests = 0, troughs = 0;
        for (int i = 0; i < 20000; i++) {
            double x = r.nextDouble() * 100, z = r.nextDouble() * 100, t = r.nextDouble() * 900;
            double f = RoughSea.foam(x, z, t, SEED, 1.0);
            assertTrue(f >= 0 && f <= 1, "foam " + f);
            double h = RoughSea.height(x, z, t, SEED);
            if (h > 0.35) {
                crestFoam += f;
                crests++;
            } else if (h < -0.3) {
                troughFoam += f;
                troughs++;
            }
        }
        assertTrue(crests > 0 && troughs > 0);
        assertTrue(crestFoam / crests > 3 * (troughFoam / troughs + 1e-3), "foam is not on the crests");
    }

    @Test
    void seaEasesInAfterTheStartAndHasASceneRelativeSeed() {
        RoughSea.Sea late = RoughSea.Sea.of(new float[]{1, 2, 3}, new float[]{10, 0, 10}, 1f, 100f, 0);
        RoughSea.Sea early = RoughSea.Sea.of(new float[]{1, 2, 3}, new float[]{10, 0, 10}, 1f, 0f, 200);
        assertEquals(0, late.intensityAt(99), 1e-12);
        assertEquals(0, late.intensityAt(100), 1e-12);
        assertEquals(1, late.intensityAt(100 + RoughSea.RAMP_TICKS), 1e-12);
        assertEquals(1, early.intensityAt(0), 1e-12);        // a sea there from the start is there at once
        assertEquals(1, early.intensityAt(150), 1e-12);
        assertTrue(early.intensityAt(190) < 1);                 // calms over its last two seconds
        assertEquals(0, early.intensityAt(200), 1e-12);
        assertEquals(2.0, early.surface(5, 5, 250), 1e-12);    // gone: the still surface
        // The seed is the effect seed of the scene-relative at and t (the same in every session slot).
        assertEquals(com.hogwartsmagic.pensieve.scene.EffectSeed.of(1, 2, 3, 0, 0, 0, 100f), late.seed());
        assertNotEquals(late.seed(), early.seed());
    }
}
