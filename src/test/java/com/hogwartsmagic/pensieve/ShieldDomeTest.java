package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.ShieldDome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShieldDomeTest {
    @Test
    void risesFromTheGroundAndStaysUp() {
        assertEquals(0f, ShieldDome.rise(0), 1e-6f);
        float half = ShieldDome.rise(ShieldDome.RISE_TICKS / 2f);
        assertTrue(half > 0.5f && half < 1f, "half way " + half);
        assertEquals(1f, ShieldDome.rise(ShieldDome.RISE_TICKS), 1e-6f);
        assertEquals(1f, ShieldDome.rise(10_000), 1e-6f);
        // below the seam it is there, above it it is not
        assertEquals(1f, ShieldDome.risen(0.1f, 0.5f), 1e-6f);
        assertEquals(0f, ShieldDome.risen(0.6f, 0.5f), 1e-6f);
        assertEquals(1f, ShieldDome.risen(1f, 1f), 1e-6f);
        assertTrue(ShieldDome.seam(0.48f, 0.5f) > 0f);
        assertEquals(0f, ShieldDome.seam(0.3f, 1f), 1e-6f);
    }

    @Test
    void aHitFlashesThenRipplesOutAndFades() {
        assertTrue(ShieldDome.ripple(0f, 0f) > 2f);
        assertEquals(0f, ShieldDome.ripple(0f, ShieldDome.HIT_TICKS), 1e-6f);
        // the ring's peak moves outward with age
        float early = peak(6f), late = peak(18f);
        assertTrue(late > early + 4f, "ring " + early + " -> " + late);
        assertTrue(late <= ShieldDome.HIT_REACH + 0.5f);
    }

    private static float peak(float age) {
        float best = 0f, at = 0f;
        for (float d = 1f; d < 30f; d += 0.1f) {
            float v = ShieldDome.ripple(d, age);
            if (v > best) {
                best = v;
                at = d;
            }
        }
        return at;
    }

    @Test
    void burnsAwayBehindTheFront() {
        float span = 260f;
        assertEquals(0f, ShieldDome.front(0f, span), 1e-6f);
        float f = ShieldDome.front(ShieldDome.BURN_TICKS / 2f, span);
        assertEquals(0f, ShieldDome.burnt(f - 1f, f), 1e-6f);
        assertEquals(1f, ShieldDome.burnt(f + ShieldDome.EMBER_BAND + 1f, f), 1e-6f);
        assertTrue(ShieldDome.embers(f + 2f, f) > 0.5f);
        assertEquals(0f, ShieldDome.embers(f - 1f, f), 1e-6f);
        // the whole dome is gone by the end
        assertTrue(ShieldDome.front(ShieldDome.BURN_TICKS, span) >= span);
        assertTrue(ShieldDome.alive(ShieldDome.BURN_TICKS - 1, 0) && ShieldDome.alive(1e6f, 0));
        assertFalse(ShieldDome.alive(100 + ShieldDome.BURN_TICKS, 100));
    }
}
