package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.spell.combat.BleedRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectumsempraRulesTest {
    @Test
    void damageAndStackCaps() {
        assertEquals(5f, SectumsempraRules.damage(1), 1e-6);
        assertEquals(8f, SectumsempraRules.damage(4), 1e-6);
        assertEquals(1, SectumsempraRules.maxStacks(1));
        assertEquals(2, SectumsempraRules.maxStacks(2));
        assertEquals(3, SectumsempraRules.maxStacks(3));
        assertEquals(BleedRules.MAX_STACKS, SectumsempraRules.maxStacks(4));
    }

    @Test
    void stacksGrowToTheRankCapAndNeverDrop() {
        assertEquals(1, SectumsempraRules.nextStacks(1, 0));
        assertEquals(1, SectumsempraRules.nextStacks(1, 1));
        assertEquals(2, SectumsempraRules.nextStacks(2, 1));
        assertEquals(2, SectumsempraRules.nextStacks(2, 2));
        assertEquals(3, SectumsempraRules.nextStacks(3, 2));
        assertEquals(3, SectumsempraRules.nextStacks(4, 3));
        assertEquals(3, SectumsempraRules.nextStacks(1, 3));
    }

    @Test
    void onlyMasterDetonatesExistingStacks() {
        assertFalse(SectumsempraRules.detonates(3));
        assertTrue(SectumsempraRules.detonates(4));
        assertEquals(0f, SectumsempraRules.burstDamage(4, 0), 1e-6);
        assertEquals(6f, SectumsempraRules.burstDamage(4, 3), 1e-6);
        assertEquals(0f, SectumsempraRules.burstDamage(3, 3), 1e-6);
        assertEquals(120, SectumsempraRules.BLEED_TICKS);
    }
}
