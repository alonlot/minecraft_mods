package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApparitionRulesTest {
    @Test
    void rangeAndChannelPerRank() {
        assertEquals(48, ApparitionRules.range(1));
        assertEquals(80, ApparitionRules.range(2));
        assertEquals(128, ApparitionRules.range(3));
        assertEquals(200, ApparitionRules.range(4));
        assertEquals(80, ApparitionRules.channelTicks(1));
        assertEquals(60, ApparitionRules.channelTicks(2));
        assertEquals(40, ApparitionRules.channelTicks(3));
        assertEquals(20, ApparitionRules.channelTicks(4));
    }

    @Test
    void splinchFallsWithRankAndCanBeSwitchedOff() {
        assertEquals(0.10, ApparitionRules.splinchChance(1, 0.10, true), 1e-9);
        assertEquals(0.05, ApparitionRules.splinchChance(2, 0.10, true), 1e-9);
        assertEquals(0.02, ApparitionRules.splinchChance(3, 0.10, true), 1e-9);
        assertEquals(0.0, ApparitionRules.splinchChance(4, 0.10, true), 1e-9);
        assertEquals(0.0, ApparitionRules.splinchChance(1, 0.10, false), 1e-9);
        assertEquals(1.0, ApparitionRules.splinchChance(1, 1.5, true), 1e-9);
        assertEquals(0.0, ApparitionRules.splinchChance(1, -1.0, true), 1e-9);
        assertEquals(0.0, ApparitionRules.splinchChance(1, Double.NaN, true), 1e-9);
        assertEquals(0.0, ApparitionRules.splinchChance(4, Double.NaN, true), 1e-9);
    }

    @Test
    void fireplacesAtMaster() {
        assertFalse(ApparitionRules.jumpsToFireplaces(3));
        assertTrue(ApparitionRules.jumpsToFireplaces(4));
    }

    @Test
    void mapStepFitsTheScreen() {
        assertEquals(1, ApparitionRules.mapStep(97, 2, 400));
        assertEquals(2, ApparitionRules.mapStep(161, 2, 300));
        assertEquals(3, ApparitionRules.mapStep(401, 2, 300));
        assertEquals(401, ApparitionRules.mapStep(401, 2, 0));
        assertEquals(1, ApparitionRules.mapStep(0, 2, 300));
    }

    @Test
    void tileCenterStaysInsideTheSpan() {
        assertEquals(0, ApparitionRules.tileCenterOffset(0, 1, 97));
        assertEquals(5, ApparitionRules.tileCenterOffset(5, 1, 97));
        assertEquals(1, ApparitionRules.tileCenterOffset(0, 3, 401));
        assertEquals(31, ApparitionRules.tileCenterOffset(10, 3, 401));
        assertEquals(400, ApparitionRules.tileCenterOffset(133, 3, 401));
        assertEquals(400, ApparitionRules.tileCenterOffset(134, 3, 401));
    }
}
