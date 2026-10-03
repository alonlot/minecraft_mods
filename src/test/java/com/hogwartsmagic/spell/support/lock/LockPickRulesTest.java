package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockPickRulesTest {
    @Test
    void constants() {
        assertEquals(6, LockPickRules.HOLD_TIMEOUT_TICKS);
        assertEquals(600, LockPickRules.HELD_SOLVE_TIMEOUT_TICKS);
        assertEquals(10, LockPickRules.ETA_UNKNOWN_TICKS);
        assertEquals(2, LockPickRules.BAR_INTERVAL_TICKS);
        assertEquals(4, LockPickRules.SPARK_INTERVAL_TICKS);
        assertEquals(20, LockPickRules.BAR_CELLS);
        assertEquals(-1, LockPickRules.UNKNOWN);
    }

    @Test
    void holdLapsesAfterSixQuietTicks() {
        assertFalse(LockPickRules.holdLapsed(10, 4));
        assertTrue(LockPickRules.holdLapsed(11, 4));
        assertTrue(LockPickRules.holdLapsed(1000, Long.MIN_VALUE / 2));
    }

    @Test
    void attemptsOfLeversAndAButton() {
        // 3 levers (1 alternative each), 1 button, 2 presses: (1+3+3+1) * (1+1+1) - 1
        assertEquals(23, LockPickRules.attempts(new int[] {1, 1, 1}, 1, 0, 2));
    }

    @Test
    void attemptsOfForcedSourcesOnly() {
        // pass 1: none; pass 2: 2 + 4 sequences that force something; pass 3: the one pair
        assertEquals(7, LockPickRules.attempts(new int[0], 0, 2, 2));
    }

    @Test
    void attemptsWithAnItemKey() {
        // alternatives 15 and 1: e = 1, 16, 15 (sum 32); 2 buttons, 1 press: 32 * 3 - 1
        assertEquals(95, LockPickRules.attempts(new int[] {15, 1}, 2, 0, 1));
    }

    @Test
    void attemptsWithOneLeverAndOneForcedSource() {
        // pass 1: 2 * 1 - 1 = 1; pass 2: e-sum 2 * (1 - 0) = 2; no pass 3
        assertEquals(3, LockPickRules.attempts(new int[] {1}, 0, 1, 1));
    }

    @Test
    void attemptsOfNothing() {
        assertEquals(0, LockPickRules.attempts(new int[0], 0, 0, 4));
    }

    @Test
    void attemptsSaturate() {
        int[] keys = new int[20];
        Arrays.fill(keys, 15);
        assertEquals(Long.MAX_VALUE, LockPickRules.attempts(keys, 20, 8, 4));
    }

    @Test
    void etaIsUnknownForTheFirstHalfSecondOrWithoutAMeasurement() {
        assertEquals(LockPickRules.UNKNOWN, LockPickRules.etaSeconds(5, 100, 50, 1000, 9, 600));
        assertEquals(LockPickRules.UNKNOWN, LockPickRules.etaSeconds(0, 100, 0, 1000, 40, 600));
    }

    @Test
    void etaFromRemainingAttempts() {
        // 10 of 110 in 20 ticks: 100 left at 0.5 per tick = 200 ticks = 10 s
        assertEquals(10, LockPickRules.etaSeconds(10, 110, 100, 1_000_000, 20, 600));
        // 20 of 120 in 41 ticks: 205 ticks = 10.25 s, shown as 11
        assertEquals(11, LockPickRules.etaSeconds(20, 120, 1, 1_000_000_000_000L, 41, 600));
    }

    @Test
    void etaFromTheWorkBudget() {
        // half the budget in 20 ticks: 20 more ticks = 1 s, though the attempt plan is huge
        assertEquals(1, LockPickRules.etaSeconds(10, 1_000_000_000_000L, 500_000, 1_000_000, 20, 600));
    }

    @Test
    void etaFromTheCap() {
        assertEquals(25, LockPickRules.etaSeconds(1, 1_000_000_000_000L, 1, 1_000_000_000_000L, 100, 600));
    }

    @Test
    void fractionIsTheFurthestOfThree() {
        assertEquals(0.5, LockPickRules.fraction(50, 100, 0, 1000, 0, 600), 1e-9);
        assertEquals(0.5, LockPickRules.fraction(0, 0, 0, 0, 300, 600), 1e-9);
        assertEquals(0.25, LockPickRules.fraction(1, 100, 250, 1000, 6, 600), 1e-9);
        assertEquals(1.0, LockPickRules.fraction(200, 100, 0, 1000, 0, 600), 1e-9);
    }

    @Test
    void barCells() {
        assertEquals(0, LockPickRules.filledCells(0.0));
        assertEquals(0, LockPickRules.filledCells(0.04));
        assertEquals(10, LockPickRules.filledCells(0.5));
        assertEquals(20, LockPickRules.filledCells(1.0));
        assertEquals(20, LockPickRules.filledCells(3.0));
    }

    @Test
    void soundsQuickenAsTheSearchNarrows() {
        assertEquals(8, LockPickRules.clickInterval(0.0));
        assertEquals(6, LockPickRules.clickInterval(0.5));
        assertEquals(3, LockPickRules.clickInterval(1.0));
        assertEquals(0.04, LockPickRules.pistonChance(0.0), 1e-9);
        assertEquals(0.24, LockPickRules.pistonChance(1.0), 1e-9);
        assertEquals(0.24, LockPickRules.pistonChance(2.0), 1e-9);
    }
}
