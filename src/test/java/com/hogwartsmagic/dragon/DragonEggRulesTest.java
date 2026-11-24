package com.hogwartsmagic.dragon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DragonEggRulesTest {
    @Test void aDragonWithinSixtyFourBlocksNoticesTheTheft() {
        assertTrue(DragonEggRules.notices(64.0 * 64.0));
        assertFalse(DragonEggRules.notices(64.01 * 64.01));
    }

    @Test void creativeAndSpectatorPlayersNeverRaiseAGrudge() {
        assertTrue(DragonEggRules.raisesGrudge(false, false));
        assertFalse(DragonEggRules.raisesGrudge(true, false));
        assertFalse(DragonEggRules.raisesGrudge(false, true));
    }

    @Test void theGrudgeLastsFiveMinutes() {
        assertEquals(7000L, DragonEggRules.grudgeExpiry(1000L));
        assertTrue(DragonEggRules.grudgeActive(6999L, 7000L));
        assertFalse(DragonEggRules.grudgeActive(7000L, 7000L));
    }

    @Test void carryingTheEggKeepsTheGrudgeAlive() {
        assertEquals(11000L, DragonEggRules.renewedExpiry(5000L, 7000L, true, 50.0 * 50.0));
        assertEquals(7000L, DragonEggRules.renewedExpiry(5000L, 7000L, false, 50.0 * 50.0));
        assertEquals(7000L, DragonEggRules.renewedExpiry(5000L, 7000L, true, 97.0 * 97.0));
        assertEquals(99999L, DragonEggRules.renewedExpiry(100L, 99999L, true, 0.0));
    }

    @Test void theDragonPursuesOnlyInsideItsRangeAndWindow() {
        assertTrue(DragonEggRules.pursues(100L, 200L, 96.0 * 96.0));
        assertFalse(DragonEggRules.pursues(100L, 200L, 96.5 * 96.5));
        assertFalse(DragonEggRules.pursues(200L, 200L, 0.0));
    }

    @Test void onlyAWildIronbellyAdoptsANest() {
        assertFalse(DragonEggRules.adoptsNest(true, true, false));
        assertFalse(DragonEggRules.adoptsNest(false, true, false));
        assertFalse(DragonEggRules.adoptsNest(true, false, false));
        assertTrue(DragonEggRules.adoptsNest(false, false, false));
        assertFalse(DragonEggRules.adoptsNest(false, false, true));
    }

    @Test void aStrayedIronbellyHeadsHome() {
        assertTrue(DragonEggRules.shouldReturnHome(false, 13.0 * 13.0));
        assertFalse(DragonEggRules.shouldReturnHome(true, 13.0 * 13.0));
        assertFalse(DragonEggRules.shouldReturnHome(false, 11.0 * 11.0));
    }

    @Test void borginPaysMoreForTheRarerIronbellyEgg() {
        assertEquals(32, DragonEggRules.priceEmeralds(false));
        assertEquals(40, DragonEggRules.priceEmeralds(true));
    }
}
