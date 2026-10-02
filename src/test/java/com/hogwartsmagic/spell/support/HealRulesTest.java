package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealRulesTest {
    @Test
    void healGrowsByAHeartPerRankAndClamps() {
        assertEquals(6f, HealRules.healAmount(1), 1e-4);
        assertEquals(8f, HealRules.healAmount(2), 1e-4);
        assertEquals(10f, HealRules.healAmount(3), 1e-4);
        assertEquals(12f, HealRules.healAmount(4), 1e-4);
        assertEquals(6f, HealRules.healAmount(0), 1e-4);
        assertEquals(12f, HealRules.healAmount(9), 1e-4);
    }

    @Test
    void bleedingIsCleansedFromAdept() {
        assertFalse(HealRules.curesBleeding(1));
        assertTrue(HealRules.curesBleeding(2));
        assertTrue(HealRules.curesBleeding(4));
    }

    @Test
    void cursedWoundOnlyAtMaster() {
        assertFalse(HealRules.curesCursedWound(3));
        assertTrue(HealRules.curesCursedWound(4));
    }

    @Test
    void regenerationFromExpert() {
        assertEquals(0, HealRules.regenTicks(1));
        assertEquals(0, HealRules.regenTicks(2));
        assertEquals(80, HealRules.regenTicks(3));
        assertEquals(80, HealRules.regenTicks(4));
    }
}
