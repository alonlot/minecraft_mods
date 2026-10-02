package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LumosRulesTest {
    @Test
    void lightLevelFollowsTheAmplifier() {
        assertEquals(12, LumosRules.lightForAmplifier(0));
        assertEquals(15, LumosRules.lightForAmplifier(1));
        assertEquals(12, LumosRules.lightForAmplifier(-1));
        assertEquals(15, LumosRules.lightForAmplifier(5));
    }

    @Test
    void brightFromAdept() {
        assertEquals(0, LumosRules.amplifierForRank(1));
        assertEquals(1, LumosRules.amplifierForRank(2));
        assertEquals(1, LumosRules.amplifierForRank(4));
    }

    @Test
    void beamAndOrbsFromExpertSolemAtMaster() {
        assertFalse(LumosRules.firesBeam(2));
        assertTrue(LumosRules.firesBeam(3));
        assertFalse(LumosRules.placesOrbs(2));
        assertTrue(LumosRules.placesOrbs(3));
        assertFalse(LumosRules.solem(3));
        assertTrue(LumosRules.solem(4));
        assertEquals(3, LumosRules.MAX_ORBS);
        assertEquals(6000, LumosRules.ORB_TICKS);
    }
}
