package com.hogwartsmagic.client.ambience;

import com.hogwartsmagic.pensieve.scene.CeilingSky;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A sealed world ceiling is drawn closed until its reveal is told, then dissolves, then is open. */
class WorldCeilingRevealTest {
    private static float reveal(float now, float start) {
        return CeilingSky.revealAt(now - start, CeilingSky.DEFAULT_REVEAL_TICKS);
    }

    @Test
    void anUnenteredSealedCeilingIsDrawnClosed() {
        float start = WorldCeilingReveal.start(true, false, false, 0f);
        for (float now : new float[]{0f, 1f, 5_000f, 1_000_000f}) {
            assertEquals(0f, reveal(now, start), 0f, "the plank roof is solid at clock " + now);
        }
    }

    @Test
    void aSealedCeilingTheServerSaidIsOpenIsOpen() {
        assertEquals(1f, reveal(0f, WorldCeilingReveal.start(true, true, false, 0f)), 0f);
    }

    @Test
    void anOrdinaryCeilingIsOpenFromTheStart() {
        assertEquals(1f, reveal(0f, WorldCeilingReveal.start(false, false, false, 0f)), 0f);
    }

    @Test
    void aToldRevealDissolvesTheRoofFromThatMomentAndThenStaysOpen() {
        float start = WorldCeilingReveal.start(true, false, true, 1000f);
        assertEquals(0f, reveal(1000f, start), 0f);
        float mid = reveal(1000f + CeilingSky.DEFAULT_REVEAL_TICKS / 2f, start);
        assertTrue(mid > 0f && mid < 1f, "mid dissolve, got " + mid);
        assertEquals(1f, reveal(1000f + CeilingSky.DEFAULT_REVEAL_TICKS, start), 0f);
        assertEquals(1f, reveal(50_000f, start), 0f);
    }
}
