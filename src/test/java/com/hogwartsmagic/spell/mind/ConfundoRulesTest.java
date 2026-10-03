package com.hogwartsmagic.spell.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfundoRulesTest {
    @Test
    void confusionLastsFiveToTwelveSeconds() {
        assertEquals(100, ConfundoRules.confuseTicks(1));
        assertEquals(140, ConfundoRules.confuseTicks(2));
        assertEquals(180, ConfundoRules.confuseTicks(3));
        assertEquals(240, ConfundoRules.confuseTicks(4));
    }

    @Test
    void onlyMasterIsContagious() {
        assertFalse(ConfundoRules.contagious(3));
        assertTrue(ConfundoRules.contagious(4));
        assertEquals(ConfundoRules.AMP_CONTAGIOUS, ConfundoRules.amplifierFor(true));
        assertEquals(ConfundoRules.AMP_PLAIN, ConfundoRules.amplifierFor(false));
    }

    @Test
    void playersReelForTheConfusionPlusThreeSeconds() {
        assertEquals(160, ConfundoRules.playerNauseaTicks(1));
        assertEquals(50, ConfundoRules.playerSlownessTicks(1));
        assertEquals(300, ConfundoRules.playerNauseaTicks(4));
        assertEquals(120, ConfundoRules.playerSlownessTicks(4));
    }

    @Test
    void contagionNeedsAContagiousSourceAndAFreshNonBossVictim() {
        assertTrue(ConfundoRules.spreads(true, false, false));
        assertFalse(ConfundoRules.spreads(false, false, false));
        assertFalse(ConfundoRules.spreads(true, true, false));
        assertFalse(ConfundoRules.spreads(true, false, true));
        assertEquals(60, ConfundoRules.CONTAGION_TICKS);
    }
}
