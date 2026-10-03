package com.hogwartsmagic.spell.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImperioRulesTest {
    @Test
    void mobThrallsLastTwentyThirtyFortyFiveSeconds() {
        assertEquals(400, ImperioRules.thrallTicks(1, false));
        assertEquals(600, ImperioRules.thrallTicks(2, false));
        assertEquals(900, ImperioRules.thrallTicks(3, false));
    }

    @Test
    void playersAndBossesGetThreeFourFiveSeconds() {
        assertEquals(60, ImperioRules.thrallTicks(1, true));
        assertEquals(80, ImperioRules.thrallTicks(2, true));
        assertEquals(100, ImperioRules.thrallTicks(3, true));
    }

    @Test
    void expertHoldsTwoThralls() {
        assertEquals(1, ImperioRules.maxThralls(1));
        assertEquals(1, ImperioRules.maxThralls(2));
        assertEquals(2, ImperioRules.maxThralls(3));
        assertFalse(ImperioRules.atCap(3, 1));
        assertTrue(ImperioRules.atCap(3, 2));
        assertTrue(ImperioRules.atCap(1, 1));
    }

    @Test
    void healthCapAppliesToMobsOnly() {
        assertTrue(ImperioRules.canEnthrall(1, 40.0f, false, false));
        assertFalse(ImperioRules.canEnthrall(1, 40.5f, false, false));
        assertTrue(ImperioRules.canEnthrall(2, 60.0f, false, false));
        assertTrue(ImperioRules.canEnthrall(3, 100.0f, false, false));
        assertTrue(ImperioRules.canEnthrall(1, 500.0f, true, false));
        assertTrue(ImperioRules.canEnthrall(1, 20.0f, false, true));
    }

    @Test
    void strengthFromAdeptAndPossessionAtMaster() {
        assertFalse(ImperioRules.grantsStrength(1));
        assertTrue(ImperioRules.grantsStrength(2));
        assertTrue(ImperioRules.grantsStrength(3));
        assertFalse(ImperioRules.isPossessionRank(3));
        assertTrue(ImperioRules.isPossessionRank(4));
    }

    @Test
    void assistMemoryLastsFiveSeconds() {
        assertTrue(ImperioRules.fresh(200L, 100L));
        assertFalse(ImperioRules.fresh(201L, 100L));
    }

    @Test
    void motionPushIsThrottled() {
        // nothing sent yet, or four ticks old: push
        assertTrue(ImperioRules.needsPush(false, 0, 0, 0, 0.2, 0, 0, 0, 1));
        assertTrue(ImperioRules.needsPush(true, 0.2, 0, 0, 0.2, 0, 0, 10, 14));
        // same walk a tick later: skip
        assertFalse(ImperioRules.needsPush(true, 0.2, 0, 0, 0.2, 0, 0, 10, 11));
        // small turn skips, a 90 degree turn pushes
        assertFalse(ImperioRules.needsPush(true, 0.2, 0, 0, 0.199, 0, 0.02, 10, 11));
        assertTrue(ImperioRules.needsPush(true, 0.2, 0, 0, 0.0, 0, 0.2, 10, 11));
        // a stop, a start and a jump push at once
        assertTrue(ImperioRules.needsPush(true, 0.2, 0, 0, 0, 0, 0, 10, 11));
        assertTrue(ImperioRules.needsPush(true, 0, 0, 0, 0.2, 0, 0, 10, 11));
        assertTrue(ImperioRules.needsPush(true, 0.2, 0, 0, 0.2, 0.42, 0, 10, 11));
        // standing still stays quiet until the resend
        assertFalse(ImperioRules.needsPush(true, 0, 0, 0, 0, 0, 0, 10, 12));
    }

    @Test
    void onlyMobThrallsAreRefreshed() {
        assertTrue(ImperioRules.mayRefresh(false, false));
        assertFalse(ImperioRules.mayRefresh(true, false));
        assertFalse(ImperioRules.mayRefresh(false, true));
    }

    @Test
    void capDecisionPerRank() {
        assertTrue(ImperioRules.atCap(1, 1));
        assertFalse(ImperioRules.atCap(1, 0));
        assertTrue(ImperioRules.atCap(2, 1));
        assertFalse(ImperioRules.atCap(3, 1));
        assertTrue(ImperioRules.atCap(3, 2));
    }

    @Test
    void bossAndPlayerLockoutLastsOneCooldown() {
        assertEquals(160L, ImperioRules.lockoutTicks(200, 0.8));
        assertEquals(1L, ImperioRules.lockoutTicks(0, 1.0));
        assertTrue(ImperioRules.lockedOut(260, 259));
        assertFalse(ImperioRules.lockedOut(260, 260));
    }

    @Test
    void thrallHouseholdMatchesOnlyTheSamePuppeteer() {
        java.util.UUID a = java.util.UUID.randomUUID();
        java.util.UUID b = java.util.UUID.randomUUID();
        assertTrue(ImperioRules.sameThrallHousehold(a, a));
        assertFalse(ImperioRules.sameThrallHousehold(a, b));
        assertFalse(ImperioRules.sameThrallHousehold(a, null));
        assertFalse(ImperioRules.sameThrallHousehold(null, null));
        assertFalse(ImperioRules.sameThrallHousehold(null, a));
    }

    @Test
    void holdStartAllowsTwoTicksOfSlack() {
        assertFalse(ImperioRules.holdLongEnough(100, 100 + ImperioRules.HOLD_START_TICKS - 3));
        assertTrue(ImperioRules.holdLongEnough(100, 100 + ImperioRules.HOLD_START_TICKS - 2));
        assertTrue(ImperioRules.holdLongEnough(100, 100 + ImperioRules.HOLD_START_TICKS));
    }
}
