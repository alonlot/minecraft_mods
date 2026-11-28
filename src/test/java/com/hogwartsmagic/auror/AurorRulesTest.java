package com.hogwartsmagic.auror;

import com.hogwartsmagic.spell.dark.DarkRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AurorRulesTest {
    @Test void oneDarkSlipIsNotHunted() {
        assertFalse(AurorRules.hunts(DarkRules.CLEAN, 100L, 0L));
        assertFalse(AurorRules.hunts(DarkRules.SHADOWED, 100L, 0L));
    }

    @Test void taintedAndDarkerAreHunted() {
        assertTrue(AurorRules.hunts(DarkRules.TAINTED, 100L, 0L));
        assertTrue(AurorRules.hunts(DarkRules.LOST, 100L, 0L));
    }

    @Test void wantedLastsUntilItsTime() {
        assertTrue(AurorRules.hunts(DarkRules.CLEAN, 100L, 101L));
        assertFalse(AurorRules.hunts(DarkRules.CLEAN, 101L, 101L));
    }

    @Test void squadsOnlyForTheDarkest() {
        assertEquals(0.0f, AurorRules.squadChance(DarkRules.TAINTED));
        assertTrue(AurorRules.squadChance(DarkRules.CORRUPTED) > 0.0f);
        assertTrue(AurorRules.squadChance(DarkRules.LOST) > AurorRules.squadChance(DarkRules.CORRUPTED));
    }

    @Test void squadCooldown() {
        assertTrue(AurorRules.squadOffCooldown(5L, 0L));
        assertFalse(AurorRules.squadOffCooldown(1000L + AurorRules.SQUAD_COOLDOWN_TICKS - 1, 1000L));
        assertTrue(AurorRules.squadOffCooldown(1000L + AurorRules.SQUAD_COOLDOWN_TICKS, 1000L));
    }

    @Test void squadSizeStaysInRange() {
        for (int roll = -3; roll < 10; roll++) {
            int n = AurorRules.squadSize(roll);
            assertTrue(n >= AurorRules.SQUAD_MIN_SIZE && n <= AurorRules.SQUAD_MAX_SIZE);
        }
    }

    @Test void aPrisonerInItsCellHasNotEscaped() {
        assertFalse(AurorRules.escaped(1.5 * 1.5));
        assertTrue(AurorRules.escaped(3.5 * 3.5));
    }
}
