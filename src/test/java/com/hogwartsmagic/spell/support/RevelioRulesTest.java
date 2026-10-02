package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevelioRulesTest {
    @Test
    void radiiGrowWithRank() {
        assertEquals(12, RevelioRules.oreRadius(1));
        assertEquals(14, RevelioRules.oreRadius(2));
        assertEquals(16, RevelioRules.oreRadius(3));
        assertEquals(20, RevelioRules.oreRadius(4));
        assertEquals(8, RevelioRules.chestRadius(1));
        assertEquals(10, RevelioRules.chestRadius(2));
        assertEquals(12, RevelioRules.chestRadius(3));
        assertEquals(16, RevelioRules.chestRadius(4));
    }

    @Test
    void disguisesOnlyAtMaster() {
        assertFalse(RevelioRules.revealsDisguises(3));
        assertTrue(RevelioRules.revealsDisguises(4));
        assertEquals(20.0, RevelioRules.MASTER_RADIUS, 1e-9);
        assertEquals(100, RevelioRules.REVEAL_TICKS);
    }
}
