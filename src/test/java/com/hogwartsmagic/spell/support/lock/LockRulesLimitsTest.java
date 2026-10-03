package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LockRulesLimitsTest {
    @Test
    void signalsAboveFifteenAreClampedToFifteen() {
        assertArrayEquals(LockRules.itemsForSignal(15, 27), LockRules.itemsForSignal(16, 27));
        assertArrayEquals(LockRules.itemsForSignal(15, 27), LockRules.itemsForSignal(1000, 27));
        assertArrayEquals(new int[] {1728, 1728}, LockRules.itemsForSignal(15, 27));
    }

    @Test
    void signalsBelowZeroAreNoItems() {
        assertArrayEquals(new int[] {0, 0}, LockRules.itemsForSignal(-1, 27));
        assertArrayEquals(new int[] {0, 0}, LockRules.itemsForSignal(-40, 27));
        assertArrayEquals(new int[] {0, 0}, LockRules.itemsForSignal(0, 27));
    }

    @Test
    void ranksOutsideTheTableAreClamped() {
        assertEquals(4, LockRules.maxPresses(9));
        assertEquals(0, LockRules.budget(0));
        assertEquals(0, LockRules.traceBlocks(-5));
        assertEquals(LockRules.traceBlocks(4), LockRules.traceBlocks(9));
    }

    @Test
    void aNearlyFullChestReadsFourteenAndFillsBackToTheSameRange() {
        assertEquals(14, LockRules.containerSignal(1727, 27));
        assertArrayEquals(new int[] {1605, 1727}, LockRules.itemsForSignal(14, 27));
        assertEquals(14, LockRules.containerSignal(1605, 27));
        assertEquals(15, LockRules.containerSignal(1728, 27));
    }
}
