package com.hogwartsmagic.spell.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrowdControlMathTest {
    @Test
    void fullDurationWithNoModifiers() {
        assertEquals(60, CrowdControlMath.scaledTicks(60, 1.0, false, false, 0.6));
    }

    @Test
    void bossesTakeHalf() {
        assertEquals(30, CrowdControlMath.scaledTicks(60, 1.0, true, false, 0.6));
    }

    @Test
    void playersTakeThePvpMultiplier() {
        assertEquals(36, CrowdControlMath.scaledTicks(60, 1.0, false, true, 0.6));
        assertEquals(60, CrowdControlMath.scaledTicks(60, 1.0, false, true, 1.0));
    }

    @Test
    void diminishingStacksWithEverythingElse() {
        assertEquals(30, CrowdControlMath.scaledTicks(60, 0.5, false, false, 0.6));
        assertEquals(15, CrowdControlMath.scaledTicks(60, 0.5, true, false, 0.6));
        assertEquals(18, CrowdControlMath.scaledTicks(60, 0.5, false, true, 0.6));
    }

    @Test
    void immuneOrEmptyGivesZero() {
        assertEquals(0, CrowdControlMath.scaledTicks(60, 0.0, false, false, 0.6));
        assertEquals(0, CrowdControlMath.scaledTicks(0, 1.0, false, false, 0.6));
        assertEquals(0, CrowdControlMath.scaledTicks(-5, 1.0, false, false, 0.6));
        assertEquals(0, CrowdControlMath.scaledTicks(60, Double.NaN, false, false, 0.6));
    }

    @Test
    void badPvpMultiplierIsClamped() {
        assertEquals(60, CrowdControlMath.scaledTicks(60, 1.0, false, true, Double.NaN));
        assertEquals(60, CrowdControlMath.scaledTicks(60, 1.0, false, true, 3.0));
        assertEquals(0, CrowdControlMath.scaledTicks(60, 1.0, false, true, -1.0));
    }

    @Test
    void roundsToNearestTick() {
        assertEquals(2, CrowdControlMath.scaledTicks(3, 0.5, false, false, 0.6));
        assertEquals(1, CrowdControlMath.scaledTicks(1, 1.0, true, false, 0.6));
    }
}
