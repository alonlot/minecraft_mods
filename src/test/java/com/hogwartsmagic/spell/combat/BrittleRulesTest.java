package com.hogwartsmagic.spell.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrittleRulesTest {
    @Test
    void explosionsAndProjectilesAlwaysCount() {
        assertTrue(BrittleRules.countsAsBrittleHit(true, false, false, true, true));
        assertTrue(BrittleRules.countsAsBrittleHit(false, true, false, false, true));
    }

    @Test
    void meleeCountsButMagicAndFireDoNot() {
        assertTrue(BrittleRules.countsAsBrittleHit(false, false, true, false, false));
        assertFalse(BrittleRules.countsAsBrittleHit(false, false, true, true, false));
        assertFalse(BrittleRules.countsAsBrittleHit(false, false, true, false, true));
        assertFalse(BrittleRules.countsAsBrittleHit(false, false, false, false, false));
    }

    @Test
    void bonusIsThirtyPercentPerLevel() {
        assertEquals(1.3f, BrittleRules.bonusMultiplier(0), 1e-4);
        assertEquals(1.6f, BrittleRules.bonusMultiplier(1), 1e-4);
        assertEquals(2.5f, BrittleRules.bonusMultiplier(4), 1e-4);
        assertEquals(2.5f, BrittleRules.bonusMultiplier(99), 1e-4);
        assertEquals(1.3f, BrittleRules.bonusMultiplier(-3), 1e-4);
    }
}
