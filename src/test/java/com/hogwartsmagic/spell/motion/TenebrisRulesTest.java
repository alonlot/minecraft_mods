package com.hogwartsmagic.spell.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenebrisRulesTest {
    @Test
    void passThroughFromExpert() {
        assertEquals(0.0, TenebrisRules.passChance(2), 1e-9);
        assertEquals(0.3, TenebrisRules.passChance(3), 1e-9);
        assertEquals(0.5, TenebrisRules.passChance(4), 1e-9);
        assertTrue(TenebrisRules.passes(3, 0.29));
        assertFalse(TenebrisRules.passes(3, 0.3));
        assertTrue(TenebrisRules.passes(4, 0.49));
        assertFalse(TenebrisRules.passes(2, 0.0));
    }

    @Test
    void ramAtMasterOncePerSecondPerTarget() {
        assertFalse(TenebrisRules.rams(3));
        assertTrue(TenebrisRules.rams(4));
        assertTrue(TenebrisRules.ramReady(false, 0L, 5L));
        assertFalse(TenebrisRules.ramReady(true, 100L, 119L));
        assertTrue(TenebrisRules.ramReady(true, 100L, 120L));
    }

    @Test
    void amplifierCarriesTheRank() {
        assertEquals(0, TenebrisRules.amplifier(1));
        assertEquals(3, TenebrisRules.amplifier(4));
        assertEquals(3, TenebrisRules.amplifier(7));
    }
}
