package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepulsoRulesTest {
    @Test
    void knockbackAndSlamPerRank() {
        assertEquals(1.5, DepulsoRules.knockback(1), 1e-9);
        assertEquals(1.8, DepulsoRules.knockback(2), 1e-9);
        assertEquals(2.0, DepulsoRules.knockback(3), 1e-9);
        assertEquals(2.2, DepulsoRules.knockback(4), 1e-9);
        assertEquals(3f, DepulsoRules.slamDamage(1), 1e-4);
        assertEquals(5f, DepulsoRules.slamDamage(2), 1e-4);
        assertEquals(5f, DepulsoRules.slamDamage(3), 1e-4);
        assertEquals(8f, DepulsoRules.slamDamage(4), 1e-4);
    }

    @Test
    void unlocks() {
        assertFalse(DepulsoRules.pushesArea(2));
        assertTrue(DepulsoRules.pushesArea(3));
        assertFalse(DepulsoRules.deflectsProjectiles(2));
        assertTrue(DepulsoRules.deflectsProjectiles(3));
        assertFalse(DepulsoRules.slamsIntoMobs(3));
        assertTrue(DepulsoRules.slamsIntoMobs(4));
    }
}
