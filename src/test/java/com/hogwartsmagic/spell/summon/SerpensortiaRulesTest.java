package com.hogwartsmagic.spell.summon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerpensortiaRulesTest {
    @Test
    void moreSnakesLongerAndPoisonTwoFromExpert() {
        assertEquals(1, SerpensortiaRules.snakeCount(1));
        assertEquals(2, SerpensortiaRules.snakeCount(2));
        assertEquals(3, SerpensortiaRules.snakeCount(3));
        assertEquals(3, SerpensortiaRules.snakeCount(4));
        assertEquals(400, SerpensortiaRules.lifetimeTicks(1));
        assertEquals(600, SerpensortiaRules.lifetimeTicks(2));
        assertEquals(800, SerpensortiaRules.lifetimeTicks(3));
        assertEquals(1200, SerpensortiaRules.lifetimeTicks(4));
        assertEquals(0, SerpensortiaRules.poisonAmplifier(2));
        assertEquals(1, SerpensortiaRules.poisonAmplifier(3));
    }

    @Test
    void ownerGoneGraceIsTwoSeconds() {
        assertEquals(40, SerpensortiaRules.OWNER_GONE_TICKS);
    }

    @Test
    void parseltongueAtMasterAndModesToggle() {
        assertFalse(SerpensortiaRules.parseltongue(3));
        assertTrue(SerpensortiaRules.parseltongue(4));
        assertEquals(SerpensortiaRules.MODE_GUARD, SerpensortiaRules.toggledMode(SerpensortiaRules.MODE_FOLLOW));
        assertEquals(SerpensortiaRules.MODE_FOLLOW, SerpensortiaRules.toggledMode(SerpensortiaRules.MODE_GUARD));
    }

    @Test
    void snakesSpreadAroundTheImpact() {
        assertEquals(0.0, SerpensortiaRules.spawnOffset(0, 1), 1e-9);
        assertEquals(-0.4, SerpensortiaRules.spawnOffset(0, 2), 1e-9);
        assertEquals(0.4, SerpensortiaRules.spawnOffset(1, 2), 1e-9);
        assertEquals(-0.8, SerpensortiaRules.spawnOffset(0, 3), 1e-9);
        assertEquals(0.8, SerpensortiaRules.spawnOffset(2, 3), 1e-9);
    }

    @Test
    void commandedAttackOnAPlayerNeedsPvpAndPermissionToHarm() {
        assertTrue(SerpensortiaRules.commandMayHitPlayer(true, true));
        assertFalse(SerpensortiaRules.commandMayHitPlayer(false, true));
        assertFalse(SerpensortiaRules.commandMayHitPlayer(true, false));
        assertFalse(SerpensortiaRules.commandMayHitPlayer(false, false));
    }
}
