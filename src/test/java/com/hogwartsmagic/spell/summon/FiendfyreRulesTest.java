package com.hogwartsmagic.spell.summon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiendfyreRulesTest {
    @Test
    void longerFireAndMoreSerpentsWithRank() {
        assertEquals(120, FiendfyreRules.durationTicks(1));
        assertEquals(160, FiendfyreRules.durationTicks(2));
        assertEquals(200, FiendfyreRules.durationTicks(3));
        assertEquals(240, FiendfyreRules.durationTicks(4));
        assertEquals(1, FiendfyreRules.serpentCount(1));
        assertEquals(1, FiendfyreRules.serpentCount(2));
        assertEquals(2, FiendfyreRules.serpentCount(3));
        assertEquals(3, FiendfyreRules.serpentCount(4));
    }

    @Test
    void rogueChanceShrinksToNothing() {
        assertEquals(0.15, FiendfyreRules.rogueChance(1), 1e-9);
        assertEquals(0.08, FiendfyreRules.rogueChance(2), 1e-9);
        assertEquals(0.03, FiendfyreRules.rogueChance(3), 1e-9);
        assertEquals(0.0, FiendfyreRules.rogueChance(4), 1e-9);
        assertTrue(FiendfyreRules.goesRogue(1, 0.14));
        assertFalse(FiendfyreRules.goesRogue(1, 0.15));
        assertFalse(FiendfyreRules.goesRogue(4, 0.0));
    }

    @Test
    void theTurnLeavesRoomForTheTelegraphAndTheRampage() {
        assertEquals(50, FiendfyreRules.rogueTurnTick(120, 0.0));
        assertEquals(100, FiendfyreRules.rogueTurnTick(120, 0.999999));
        assertEquals(100, FiendfyreRules.rogueTurnTick(120, 1.0));
        assertEquals(50, FiendfyreRules.rogueTurnTick(40, 0.5));
        assertFalse(FiendfyreRules.telegraphing(79, 100));
        assertTrue(FiendfyreRules.telegraphing(80, 100));
        assertTrue(FiendfyreRules.telegraphing(99, 100));
        assertFalse(FiendfyreRules.telegraphing(100, 100));
        assertFalse(FiendfyreRules.telegraphing(90, -1));
        assertEquals(120, FiendfyreRules.rogueLifeEnd(120, 60));
        assertEquals(140, FiendfyreRules.rogueLifeEnd(120, 100));
    }

    @Test
    void rogueIndexStaysInRange() {
        assertEquals(0, FiendfyreRules.rogueIndex(1, 0.9));
        assertEquals(0, FiendfyreRules.rogueIndex(2, 0.4));
        assertEquals(1, FiendfyreRules.rogueIndex(2, 0.6));
        assertEquals(1, FiendfyreRules.rogueIndex(2, 1.0));
    }

    @Test
    void channelEndsAtTheRankDuration() {
        assertFalse(FiendfyreRules.expired(1000L, 1119L, 1));
        assertTrue(FiendfyreRules.expired(1000L, 1120L, 1));
        assertFalse(FiendfyreRules.expired(1000L, 1239L, 4));
    }

    @Test
    void formationAndMasterPerks() {
        assertEquals(0.0, FiendfyreRules.lateralOffset(0, 1), 1e-9);
        assertEquals(-1.6, FiendfyreRules.lateralOffset(0, 2), 1e-9);
        assertEquals(1.6, FiendfyreRules.lateralOffset(1, 2), 1e-9);
        assertEquals(-2.4, FiendfyreRules.lateralOffset(0, 3), 1e-9);
        assertEquals(0.0, FiendfyreRules.lateralOffset(1, 3), 1e-9);
        assertEquals(2.4, FiendfyreRules.lateralOffset(2, 3), 1e-9);
        assertFalse(FiendfyreRules.dispatches(3));
        assertTrue(FiendfyreRules.dispatches(4));
        assertFalse(FiendfyreRules.destroysHorcrux(3));
        assertTrue(FiendfyreRules.destroysHorcrux(4));
    }

    @Test
    void rogueSparesTheProtected() {
        assertFalse(FiendfyreRules.rogueMayTarget(true, true, true, true, true, true));    // creative caster
        assertTrue(FiendfyreRules.rogueMayTarget(true, true, false, false, false, true));  // its own caster, PvP off
        assertFalse(FiendfyreRules.rogueMayTarget(false, true, false, false, true, true)); // other player, PvP off
        assertFalse(FiendfyreRules.rogueMayTarget(false, true, false, true, false, true)); // caster may not harm them
        assertTrue(FiendfyreRules.rogueMayTarget(false, true, false, true, true, true));
        assertTrue(FiendfyreRules.rogueMayTarget(false, false, false, false, false, true)); // any mob
        assertFalse(FiendfyreRules.rogueMayTarget(false, false, false, false, false, false)); // an armor stand
    }

    @Test
    void dispatchSparesTheCasterAlliesAndProtectedPlayers() {
        // (isCaster, isPlayer, creativeOrSpectator, pvpAllowed, casterCanHarm, alliedToCaster)
        assertFalse(FiendfyreRules.dispatchMayTarget(true, true, false, true, true, false));   // the caster
        assertFalse(FiendfyreRules.dispatchMayTarget(false, true, true, true, true, false));   // creative or spectator
        assertFalse(FiendfyreRules.dispatchMayTarget(false, false, false, true, true, true));  // an owned allied mob
        assertFalse(FiendfyreRules.dispatchMayTarget(false, true, false, true, true, true));   // an allied player
        assertFalse(FiendfyreRules.dispatchMayTarget(false, true, false, false, true, false)); // other player, PvP off
        assertTrue(FiendfyreRules.dispatchMayTarget(false, true, false, true, true, false));   // other player, PvP on
        assertFalse(FiendfyreRules.dispatchMayTarget(false, true, false, true, false, false)); // caster may not harm them
        assertTrue(FiendfyreRules.dispatchMayTarget(false, false, false, false, false, false)); // any other mob
    }
}
