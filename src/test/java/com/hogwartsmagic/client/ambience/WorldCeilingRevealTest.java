package com.hogwartsmagic.client.ambience;

import com.hogwartsmagic.pensieve.scene.CeilingSky;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sealed world ceiling is drawn closed until its reveal is told, then dissolves, then is open; an ordinary (spell)
 * ceiling dissolves from the tick it was made.
 */
class WorldCeilingRevealTest {
    private static float reveal(float now, float start) {
        return CeilingSky.revealAt(now - start, CeilingSky.DEFAULT_REVEAL_TICKS);
    }

    @Test
    void anUnenteredSealedCeilingIsDrawnClosed() {
        float start = WorldCeilingReveal.start(true, false, false, 0f, 0f);
        for (float now : new float[]{0f, 1f, 5_000f, 1_000_000f}) {
            assertEquals(0f, reveal(now, start), 0f, "the plank roof is solid at clock " + now);
        }
    }

    @Test
    void aSealedCeilingTheServerSaidIsOpenIsOpen() {
        assertEquals(1f, reveal(0f, WorldCeilingReveal.start(true, true, false, 0f, 0f)), 0f);
    }

    @Test
    void anOrdinaryCeilingPlaysItsRevealFromTheTickItWasMade() {
        float created = 2000f;
        float start = WorldCeilingReveal.start(false, false, false, 0f, created);
        assertEquals(0f, reveal(created, start), 0f, "the plank roof is solid on the tick the spell lands");
        float mid = reveal(created + CeilingSky.DEFAULT_REVEAL_TICKS / 2f, start);
        assertTrue(mid > 0f && mid < 1f, "mid dissolve, got " + mid);
        assertEquals(1f, reveal(created + CeilingSky.DEFAULT_REVEAL_TICKS, start), 0f);
    }

    @Test
    void anOrdinaryCeilingSeenLongAfterItWasMadeIsOpen() {
        // A client arriving later reads the creation tick far in the past on its clock (even a negative one, from an
        // earlier epoch): the reveal is long over.
        float start = WorldCeilingReveal.start(false, false, false, 0f, -3_000_000f);
        assertEquals(1f, reveal(10f, start), 0f);
    }

    @Test
    void aToldRevealDissolvesTheRoofFromThatMomentAndThenStaysOpen() {
        float start = WorldCeilingReveal.start(true, false, true, 1000f, 0f);
        assertEquals(0f, reveal(1000f, start), 0f);
        float mid = reveal(1000f + CeilingSky.DEFAULT_REVEAL_TICKS / 2f, start);
        assertTrue(mid > 0f && mid < 1f, "mid dissolve, got " + mid);
        assertEquals(1f, reveal(1000f + CeilingSky.DEFAULT_REVEAL_TICKS, start), 0f);
        assertEquals(1f, reveal(50_000f, start), 0f);
    }
}
