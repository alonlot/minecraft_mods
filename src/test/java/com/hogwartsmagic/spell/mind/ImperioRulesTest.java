package com.hogwartsmagic.spell.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImperioRulesTest {
    @Test
    void mobThrallsLastTwentyThirtyFortyFiveSeconds() {
        assertEquals(400, ImperioRules.thrallTicks(1, false));
        assertEquals(600, ImperioRules.thrallTicks(2, false));
        assertEquals(900, ImperioRules.thrallTicks(3, false));
    }

    @Test
    void playersAndBossesGetThreeFourFiveSeconds() {
        assertEquals(60, ImperioRules.thrallTicks(1, true));
        assertEquals(80, ImperioRules.thrallTicks(2, true));
        assertEquals(100, ImperioRules.thrallTicks(3, true));
    }

    @Test
    void expertHoldsTwoThralls() {
        assertEquals(1, ImperioRules.maxThralls(1));
        assertEquals(1, ImperioRules.maxThralls(2));
        assertEquals(2, ImperioRules.maxThralls(3));
        assertFalse(ImperioRules.atCap(3, 1));
        assertTrue(ImperioRules.atCap(3, 2));
        assertTrue(ImperioRules.atCap(1, 1));
    }

    @Test
    void healthCapAppliesToMobsOnly() {
        assertTrue(ImperioRules.canEnthrall(1, 40.0f, false, false));
        assertFalse(ImperioRules.canEnthrall(1, 40.5f, false, false));
        assertTrue(ImperioRules.canEnthrall(2, 60.0f, false, false));
        assertTrue(ImperioRules.canEnthrall(3, 100.0f, false, false));
        assertTrue(ImperioRules.canEnthrall(1, 500.0f, true, false));
        assertTrue(ImperioRules.canEnthrall(1, 20.0f, false, true));
    }

    @Test
    void strengthFromAdeptAndPossessionAtMaster() {
        assertFalse(ImperioRules.grantsStrength(1));
        assertTrue(ImperioRules.grantsStrength(2));
        assertTrue(ImperioRules.grantsStrength(3));
        assertFalse(ImperioRules.isPossessionRank(3));
        assertTrue(ImperioRules.isPossessionRank(4));
    }

    @Test
    void assistMemoryLastsFiveSeconds() {
        assertTrue(ImperioRules.fresh(200L, 100L));
        assertFalse(ImperioRules.fresh(201L, 100L));
    }
}
