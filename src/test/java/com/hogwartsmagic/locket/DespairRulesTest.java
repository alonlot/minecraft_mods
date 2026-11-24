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
    @Test void drinkPacingIsShorterThanRefill() {
        assertTrue(DespairRules.MIN_TICKS_BETWEEN_DRINKS > 0);
        assertTrue(DespairRules.MIN_TICKS_BETWEEN_DRINKS < DespairRules.REFILL_TIMEOUT_TICKS);
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
    @Test void stageRisesWithCup() {
        assertEquals(0, DespairRules.stageFor(1));
        assertEquals(5, DespairRules.stageFor(6));
        for (int c = 2; c <= 6; c++) assertEquals(DespairRules.stageFor(c - 1) + 1, DespairRules.stageFor(c));
    }
    @Test void kneelOnlyAtFiveAndSix() {
        for (int c = 1; c <= 4; c++) assertEquals(0, DespairRules.kneelTicks(c));
        assertTrue(DespairRules.kneelTicks(5) > 0);
        assertTrue(DespairRules.kneelTicks(6) >= DespairRules.kneelTicks(5));
    }
    @Test void whisperSoundsRiseWithCup() {
        assertTrue(DespairRules.whisperSounds(1) >= 1);
        assertTrue(DespairRules.whisperSounds(6) > DespairRules.whisperSounds(1));
    }
}
