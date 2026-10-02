package com.hogwartsmagic.spell.disable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncarcerousRulesTest {
    @Test
    void mainAndSecondaryDurations() {
        assertEquals(100, IncarcerousRules.mainTicks(1));
        assertEquals(140, IncarcerousRules.mainTicks(4));
        assertEquals(0, IncarcerousRules.secondaryTicks(1));
        assertEquals(70, IncarcerousRules.secondaryTicks(2));
        assertEquals(80, IncarcerousRules.secondaryTicks(3));
        assertEquals(140, IncarcerousRules.secondaryTicks(4));
    }

    @Test
    void radiusGrowsAndMasterShortensTheLeash() {
        assertEquals(0.0, IncarcerousRules.secondaryRadius(1), 1e-9);
        assertEquals(2.5, IncarcerousRules.secondaryRadius(2), 1e-9);
        assertEquals(3.5, IncarcerousRules.secondaryRadius(3), 1e-9);
        assertEquals(2.0, IncarcerousRules.leash(3), 1e-9);
        assertEquals(1.0, IncarcerousRules.leash(4), 1e-9);
    }

    @Test
    void expertPullsFlyersMasterBinds() {
        assertFalse(IncarcerousRules.pullsFlyersDown(2));
        assertTrue(IncarcerousRules.pullsFlyersDown(3));
        assertFalse(IncarcerousRules.bindsTogether(3));
        assertTrue(IncarcerousRules.bindsTogether(4));
    }

    @Test
    void boundLeashCountsBothBodies() {
        assertEquals(1.6, IncarcerousRules.boundLeash(4, 0.6, 0.6), 1e-9);
    }
}
