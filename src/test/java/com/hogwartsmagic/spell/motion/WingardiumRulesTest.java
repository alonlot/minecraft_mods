package com.hogwartsmagic.spell.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WingardiumRulesTest {
    @Test
    void blocksAndThrowsFromExpertTwoHeldAtMaster() {
        assertFalse(WingardiumRules.liftsBlocks(2));
        assertTrue(WingardiumRules.liftsBlocks(3));
        assertFalse(WingardiumRules.canThrow(2));
        assertTrue(WingardiumRules.canThrow(3));
        assertEquals(1, WingardiumRules.maxHeld(3));
        assertEquals(2, WingardiumRules.maxHeld(4));
        assertEquals(0f, WingardiumRules.throwDamage(2), 1e-6);
        assertEquals(3f, WingardiumRules.throwDamage(3), 1e-6);
        assertEquals(6f, WingardiumRules.throwDamage(4), 1e-6);
    }

    @Test
    void onlyPlainBlocksUpToStoneLift() {
        assertTrue(WingardiumRules.isLiftable(1.5f, false, false, false, true, false, false)); // stone
        assertTrue(WingardiumRules.isLiftable(0.0f, false, false, false, true, false, false)); // a flower pot or torch
        assertFalse(WingardiumRules.isLiftable(1.6f, false, false, false, true, false, false)); // harder than stone
        assertFalse(WingardiumRules.isLiftable(-1.0f, false, false, false, true, false, false)); // bedrock
        assertFalse(WingardiumRules.isLiftable(0.5f, true, false, false, true, false, false)); // air
        assertFalse(WingardiumRules.isLiftable(0.5f, false, true, false, true, false, false)); // a chest
        assertFalse(WingardiumRules.isLiftable(0.0f, false, false, true, true, false, false)); // short grass
        assertFalse(WingardiumRules.isLiftable(0.5f, false, false, false, false, false, false)); // waterlogged
        assertFalse(WingardiumRules.isLiftable(1.0f, false, false, false, true, true, false)); // a door half
        assertFalse(WingardiumRules.isLiftable(0.5f, false, false, false, true, false, true)); // tagged immune
        assertFalse(WingardiumRules.isLiftable(Float.NaN, false, false, false, true, false, false));
    }

    @Test
    void aThrowIsAFreshPress() {
        assertTrue(WingardiumRules.risingEdge(false, true));
        assertFalse(WingardiumRules.risingEdge(true, true));
        assertFalse(WingardiumRules.risingEdge(false, false));
        assertFalse(WingardiumRules.risingEdge(true, false));
    }

    @Test
    void outOfRangeRanksClamp() {
        assertFalse(WingardiumRules.liftsBlocks(0));
        assertFalse(WingardiumRules.liftsBlocks(-1));
        assertTrue(WingardiumRules.liftsBlocks(5));
        assertFalse(WingardiumRules.canThrow(0));
        assertTrue(WingardiumRules.canThrow(5));
        assertEquals(1, WingardiumRules.maxHeld(0));
        assertEquals(1, WingardiumRules.maxHeld(-1));
        assertEquals(2, WingardiumRules.maxHeld(5));
    }
}
