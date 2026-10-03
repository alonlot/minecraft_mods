package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockRulesTest {
    @Test
    void pistonDoorsFromExpert() {
        assertFalse(LockRules.readsLocks(2));
        assertTrue(LockRules.readsLocks(3));
        assertTrue(LockRules.readsLocks(4));
    }

    @Test
    void masterOnlyExtras() {
        assertFalse(LockRules.forcesSources(3));
        assertTrue(LockRules.forcesSources(4));
        assertFalse(LockRules.readsItemKeys(3));
        assertTrue(LockRules.readsItemKeys(4));
    }

    @Test
    void expertReadsASmallLock() {
        assertEquals(512, LockRules.traceBlocks(3));
        assertEquals(4, LockRules.maxInputs(3));
        assertEquals(1, LockRules.maxPresses(3));
        assertEquals(2_000_000L, LockRules.budget(3));
    }

    @Test
    void masterReadsTheFullLock() {
        assertEquals(8192, LockRules.traceBlocks(4));
        assertEquals(20, LockRules.maxInputs(4));
        assertEquals(4, LockRules.maxPresses(4));
        assertEquals(200_000_000L, LockRules.budget(4));
    }

    @Test
    void badRanksClamp() {
        assertEquals(0, LockRules.maxInputs(0));
        assertEquals(20, LockRules.maxInputs(9));
    }

    @Test
    void timingsAndCaps() {
        assertEquals(6, LockRules.DOOR_RADIUS);
        assertEquals(32, LockRules.MAX_DOOR_PISTONS);
        assertEquals(20, LockRules.LEVER_SETTLE_TICKS);
        assertEquals(40, LockRules.PRESS_GAP_TICKS);
        assertEquals(60, LockRules.FORCE_TICKS);
        assertEquals(80, LockRules.FORCE_GAP_TICKS);
        assertEquals(8, LockRules.MAX_FORCED_SOURCES);
        assertEquals(3, LockRules.MAX_ITEM_KEYS);
        assertEquals(300, LockRules.HINT_TICKS);
        assertEquals(200, LockRules.SOLVE_TIMEOUT_TICKS);
    }

    @Test
    void containerSignalMatchesVanilla() {
        // vanilla: floor(fullness * 14) + (any item ? 1 : 0), fullness in full-stack units over all slots
        assertEquals(0, LockRules.containerSignal(0, 27));
        assertEquals(1, LockRules.containerSignal(1, 27));
        assertEquals(15, LockRules.containerSignal(27 * 64, 27));
        assertEquals(1, LockRules.containerSignal(123, 27));
        assertEquals(2, LockRules.containerSignal(124, 27));
    }

    @Test
    void itemsForSignal() {
        assertArrayEquals(new int[] {0, 0}, LockRules.itemsForSignal(0, 27));
        assertArrayEquals(new int[] {1, 123}, LockRules.itemsForSignal(1, 27));
        assertArrayEquals(new int[] {124, 246}, LockRules.itemsForSignal(2, 27));
        assertArrayEquals(new int[] {1728, 1728}, LockRules.itemsForSignal(15, 27));
        assertArrayEquals(new int[] {1, 4}, LockRules.itemsForSignal(1, 1));
    }
}
