package com.hogwartsmagic.spell.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvadaRulesTest {
    @Test
    void windupShrinksFromOneAndAHalfSecondsToPointEight() {
        assertEquals(30, AvadaRules.windupTicks(1));
        assertEquals(24, AvadaRules.windupTicks(2));
        assertEquals(20, AvadaRules.windupTicks(3));
        assertEquals(16, AvadaRules.windupTicks(4));
    }

    @Test
    void backlashShrinksToNothingAtMaster() {
        assertEquals(4.0f, AvadaRules.backlash(1), 1e-6);
        assertEquals(3.0f, AvadaRules.backlash(2), 1e-6);
        assertEquals(2.0f, AvadaRules.backlash(3), 1e-6);
        assertEquals(0.0f, AvadaRules.backlash(4), 1e-6);
    }

    @Test
    void bossesLoseAFifthOfTheirMaxHealth() {
        assertEquals(210.0f, AvadaRules.bossDamage(300.0f), 1e-4);
    }

    @Test
    void hitsOfFourOrMoreBreakTheWindup() {
        assertFalse(AvadaRules.interrupts(3.9f));
        assertTrue(AvadaRules.interrupts(4.0f));
    }

    @Test
    void windupFinishesOnItsLastTick() {
        assertFalse(AvadaRules.windupDone(29L, 1));
        assertTrue(AvadaRules.windupDone(30L, 1));
        assertTrue(AvadaRules.windupDone(16L, 4));
    }
}
