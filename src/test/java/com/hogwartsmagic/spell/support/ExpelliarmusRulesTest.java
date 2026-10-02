package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpelliarmusRulesTest {
    @Test
    void silenceGrowsToFourSeconds() {
        assertEquals(40, ExpelliarmusRules.silenceTicks(1));
        assertEquals(60, ExpelliarmusRules.silenceTicks(2));
        assertEquals(60, ExpelliarmusRules.silenceTicks(3));
        assertEquals(80, ExpelliarmusRules.silenceTicks(4));
    }

    @Test
    void knockbackStepsUpAtAdept() {
        assertEquals(0.5, ExpelliarmusRules.knockback(1), 1e-9);
        assertEquals(0.8, ExpelliarmusRules.knockback(2), 1e-9);
        assertEquals(0.8, ExpelliarmusRules.knockback(4), 1e-9);
    }

    @Test
    void onlyAMasterWinsTheElderWand() {
        assertFalse(ExpelliarmusRules.winsElderWand(3));
        assertTrue(ExpelliarmusRules.winsElderWand(4));
    }
}
