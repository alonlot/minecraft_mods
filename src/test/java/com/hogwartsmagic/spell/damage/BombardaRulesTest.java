package com.hogwartsmagic.spell.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BombardaRulesTest {
    @Test
    void radiusDamageAndDrops() {
        assertEquals(1.5, BombardaRules.radius(1), 1e-9);
        assertEquals(2.0, BombardaRules.radius(2), 1e-9);
        assertEquals(2.5, BombardaRules.radius(3), 1e-9);
        assertEquals(3.5, BombardaRules.radius(4), 1e-9);
        assertEquals(5f, BombardaRules.entityDamage(1), 1e-6);
        assertEquals(8f, BombardaRules.entityDamage(4), 1e-6);
        assertEquals(0.5, BombardaRules.dropChance(1), 1e-9);
        assertEquals(1.0, BombardaRules.dropChance(2), 1e-9);
    }

    @Test
    void hardnessTiers() {
        assertTrue(BombardaRules.canBreak(1, 1.5f, false, 0.5));   // stone
        assertTrue(BombardaRules.canBreak(1, 3.0f, false, 0.5));   // wooden door, deepslate
        assertFalse(BombardaRules.canBreak(2, 4.5f, false, 0.0));  // deepslate ore
        assertTrue(BombardaRules.canBreak(3, 5.0f, false, 0.0));   // iron door
        assertFalse(BombardaRules.canBreak(4, 25f, false, 0.0));   // Gringotts lift
        assertFalse(BombardaRules.canBreak(4, -1f, false, 0.0));   // bedrock
        assertFalse(BombardaRules.canBreak(4, Float.NaN, false, 0.0));
    }

    @Test
    void obsidianIsAChanceAtExpertAndCertainAtMaster() {
        assertFalse(BombardaRules.canBreak(2, 50f, true, 0.0));
        assertTrue(BombardaRules.canBreak(3, 50f, true, 0.2));
        assertFalse(BombardaRules.canBreak(3, 50f, true, 0.3));
        assertTrue(BombardaRules.canBreak(4, 50f, true, 0.99));
    }

    @Test
    void splashFallsOffToAQuarterThenStops() {
        assertEquals(5f, BombardaRules.splashDamage(5f, 0.0, 1.5), 1e-6);
        assertEquals(4f, BombardaRules.splashDamage(8f, 2.25, 3.5), 1e-6);
        assertEquals(2f, BombardaRules.splashDamage(8f, 4.0, 3.5), 1e-6);
        assertEquals(0f, BombardaRules.splashDamage(5f, 2.5, 1.5), 1e-6);
        assertEquals(0f, BombardaRules.splashDamage(5f, Double.NaN, 1.5), 1e-6);
    }
}
