package com.hogwartsmagic.spell.disable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StupefyRulesTest {
    @Test
    void stunGrowsToTwoAndAHalfSeconds() {
        assertEquals(30, StupefyRules.stunTicks(1));
        assertEquals(40, StupefyRules.stunTicks(2));
        assertEquals(50, StupefyRules.stunTicks(3));
        assertEquals(50, StupefyRules.stunTicks(4));
    }

    @Test
    void ranksOutsideOneToFourAreClamped() {
        assertEquals(30, StupefyRules.stunTicks(0));
        assertEquals(50, StupefyRules.stunTicks(9));
    }

    @Test
    void knockbackStartsAtAdept() {
        assertEquals(0.0, StupefyRules.knockback(1), 1e-9);
        assertEquals(0.4, StupefyRules.knockback(2), 1e-9);
        assertEquals(0.4, StupefyRules.knockback(4), 1e-9);
    }

    @Test
    void onlyMasterChainsForHalfTheStun() {
        assertFalse(StupefyRules.chains(3));
        assertTrue(StupefyRules.chains(4));
        assertEquals(25, StupefyRules.chainStunTicks(4));
        assertEquals(5.0, StupefyRules.CHAIN_RADIUS, 1e-9);
    }

    @Test
    void masterBeamIsAQuarterFaster() {
        assertEquals(1.0f, StupefyRules.beamSpeedMultiplier(3), 1e-6);
        assertEquals(1.25f, StupefyRules.beamSpeedMultiplier(4), 1e-6);
    }
}
