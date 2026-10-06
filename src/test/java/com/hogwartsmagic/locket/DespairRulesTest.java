package com.hogwartsmagic.locket;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DespairRulesTest {
    @Test void sixDrinksEscalate() {
        assertEquals(1, DespairRules.effectsFor(1).size());
        assertTrue(DespairRules.effectsFor(6).size() >= DespairRules.effectsFor(3).size());
        assertEquals(0f, DespairRules.damageFor(1));
        assertEquals(4f, DespairRules.damageFor(6));
    }
    @Test void drinkDamageNeverKills() {
        assertEquals(1.5f, DespairRules.cappedDamage(4f, 2.5f), 1e-4);
        assertEquals(0f, DespairRules.cappedDamage(4f, 1f), 1e-4);
        assertEquals(3f, DespairRules.cappedDamage(3f, 20f), 1e-4);
    }
    @Test void refillsAfterTimeoutOrLeavingOrDeath() {
        assertFalse(DespairRules.shouldRefill(1000, 500, true, true));
        assertTrue(DespairRules.shouldRefill(1101, 500, true, true));
        assertTrue(DespairRules.shouldRefill(510, 500, false, true));
        assertTrue(DespairRules.shouldRefill(510, 500, true, false));
    }
    @Test void outOfRangeDrinkThrows() {
        assertThrows(IllegalArgumentException.class, () -> DespairRules.effectsFor(0));
        assertThrows(IllegalArgumentException.class, () -> DespairRules.effectsFor(7));
    }
}
