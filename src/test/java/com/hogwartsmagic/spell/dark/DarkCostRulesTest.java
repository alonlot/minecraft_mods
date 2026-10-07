package com.hogwartsmagic.spell.dark;

import com.hogwartsmagic.spell.dark.DarkCostRules.Cost;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkCostRulesTest {
    private static final float EPS = 1.0e-6f;

    private static void assertWindow(Cost cost, int from, int to) {
        assertEquals(from, cost.fromTier(), cost + " from");
        assertEquals(to, cost.toTier(), cost + " to");
    }

    @Test
    void costWindowsMatchTheSpec() {
        assertWindow(Cost.HALF_CORPOREAL_PATRONUS, DarkRules.SHADOWED, DarkRules.SHADOWED);
        assertWindow(Cost.NO_CORPOREAL_PATRONUS, DarkRules.TAINTED, DarkRules.LOST);
        assertWindow(Cost.EARLY_INSOMNIA, DarkRules.TAINTED, DarkRules.LOST);
        assertWindow(Cost.SHIELD_UPKEEP, DarkRules.CORRUPTED, DarkRules.LOST);
        assertWindow(Cost.DEMENTORS_PREFER, DarkRules.CORRUPTED, DarkRules.LOST);
        assertWindow(Cost.STUDENTS_KEEP_AWAY, DarkRules.CORRUPTED, DarkRules.CORRUPTED);
        assertWindow(Cost.SMOKE, DarkRules.CORRUPTED, DarkRules.LOST);
        assertWindow(Cost.LOST_MAX_HEALTH, DarkRules.LOST, DarkRules.LOST);
        assertWindow(Cost.STUDENTS_ATTACK, DarkRules.LOST, DarkRules.LOST);
        assertWindow(Cost.ELVES_FLEE, DarkRules.LOST, DarkRules.LOST);
        assertEquals(10, Cost.values().length);
    }

    @Test
    void eachTierHasExactlyTheSpecCosts() {
        assertEquals(EnumSet.noneOf(Cost.class), DarkCostRules.costsAt(DarkRules.CLEAN));
        assertEquals(EnumSet.of(Cost.HALF_CORPOREAL_PATRONUS), DarkCostRules.costsAt(DarkRules.SHADOWED));
        assertEquals(EnumSet.of(Cost.NO_CORPOREAL_PATRONUS, Cost.EARLY_INSOMNIA),
                DarkCostRules.costsAt(DarkRules.TAINTED));
        assertEquals(EnumSet.of(Cost.NO_CORPOREAL_PATRONUS, Cost.EARLY_INSOMNIA, Cost.SHIELD_UPKEEP,
                        Cost.DEMENTORS_PREFER, Cost.STUDENTS_KEEP_AWAY, Cost.SMOKE),
                DarkCostRules.costsAt(DarkRules.CORRUPTED));
        assertEquals(EnumSet.of(Cost.NO_CORPOREAL_PATRONUS, Cost.EARLY_INSOMNIA, Cost.SHIELD_UPKEEP,
                        Cost.DEMENTORS_PREFER, Cost.SMOKE, Cost.LOST_MAX_HEALTH, Cost.STUDENTS_ATTACK,
                        Cost.ELVES_FLEE),
                DarkCostRules.costsAt(DarkRules.LOST));
    }

    @Test
    void aTierKeepsTheCostsBelowItExceptTheTwoReplacements() {
        Set<Cost> replaced = EnumSet.of(Cost.HALF_CORPOREAL_PATRONUS, Cost.STUDENTS_KEEP_AWAY);
        for (int tier = DarkRules.SHADOWED; tier <= DarkRules.LOST; tier++) {
            Set<Cost> below = EnumSet.noneOf(Cost.class);
            below.addAll(DarkCostRules.costsAt(tier - 1));
            below.removeAll(replaced);
            assertTrue(DarkCostRules.costsAt(tier).containsAll(below), "tier " + tier);
        }
        // Each replacement is replaced by something harder at the next tier.
        assertTrue(DarkCostRules.costsAt(DarkRules.TAINTED).contains(Cost.NO_CORPOREAL_PATRONUS));
        assertTrue(DarkCostRules.costsAt(DarkRules.LOST).contains(Cost.STUDENTS_ATTACK));
    }

    @Test
    void corporealChanceByTier() {
        float base = 0.15f;
        assertEquals(0.15f, DarkCostRules.corporealPatronusChance(base, DarkRules.CLEAN), EPS);
        assertEquals(0.075f, DarkCostRules.corporealPatronusChance(base, DarkRules.SHADOWED), EPS);
        assertEquals(0.0f, DarkCostRules.corporealPatronusChance(base, DarkRules.TAINTED), EPS);
        assertEquals(0.0f, DarkCostRules.corporealPatronusChance(base, DarkRules.CORRUPTED), EPS);
        assertEquals(0.0f, DarkCostRules.corporealPatronusChance(base, DarkRules.LOST), EPS);
    }

    @Test
    void thePatronusNeverFails() {
        // The chance only moves between 0 and the base: a roll gives a corporeal Patronus or the shield, never
        // nothing. Nothing in these rules can refuse the charm.
        for (int tier = -2; tier <= 6; tier++) {
            for (float base : new float[] {0.0f, 0.15f, 1.0f}) {
                float chance = DarkCostRules.corporealPatronusChance(base, tier);
                assertTrue(chance >= 0.0f && chance <= base, "tier " + tier + " base " + base);
            }
        }
    }

    @Test
    void shieldUpkeepIsHalfAgainFromCorrupted() {
        assertEquals(1.5f, DarkCostRules.SHIELD_UPKEEP_MULTIPLIER, EPS);
        assertEquals(1.0f, DarkCostRules.shieldUpkeepMultiplier(DarkRules.CLEAN), EPS);
        assertEquals(1.0f, DarkCostRules.shieldUpkeepMultiplier(DarkRules.SHADOWED), EPS);
        assertEquals(1.0f, DarkCostRules.shieldUpkeepMultiplier(DarkRules.TAINTED), EPS);
        assertEquals(1.5f, DarkCostRules.shieldUpkeepMultiplier(DarkRules.CORRUPTED), EPS);
        assertEquals(1.5f, DarkCostRules.shieldUpkeepMultiplier(DarkRules.LOST), EPS);
    }

    @Test
    void insomniaComesAfterThreeDaysFromTainted() {
        assertEquals(24_000, DarkCostRules.TICKS_PER_DAY);
        assertEquals(120_000, DarkCostRules.BASE_INSOMNIA_TICKS);
        assertEquals(72_000, DarkCostRules.DARK_INSOMNIA_TICKS);
        assertEquals(120_000, DarkCostRules.insomniaTicks(DarkRules.CLEAN));
        assertEquals(120_000, DarkCostRules.insomniaTicks(DarkRules.SHADOWED));
        assertEquals(72_000, DarkCostRules.insomniaTicks(DarkRules.TAINTED));
        assertEquals(72_000, DarkCostRules.insomniaTicks(DarkRules.CORRUPTED));
        assertEquals(72_000, DarkCostRules.insomniaTicks(DarkRules.LOST));
    }

    @Test
    void dementorsPreferCorruptedAndLost() {
        assertFalse(DarkCostRules.dementorsPrefer(DarkRules.CLEAN));
        assertFalse(DarkCostRules.dementorsPrefer(DarkRules.SHADOWED));
        assertFalse(DarkCostRules.dementorsPrefer(DarkRules.TAINTED));
        assertTrue(DarkCostRules.dementorsPrefer(DarkRules.CORRUPTED));
        assertTrue(DarkCostRules.dementorsPrefer(DarkRules.LOST));
    }

    @Test
    void studentsKeepAwayAtCorruptedAndAttackAtLost() {
        for (int tier = DarkRules.CLEAN; tier <= DarkRules.LOST; tier++) {
            assertEquals(tier == DarkRules.CORRUPTED, DarkCostRules.studentsKeepAway(tier), "keep away " + tier);
            assertEquals(tier == DarkRules.LOST, DarkCostRules.studentsAttack(tier), "attack " + tier);
            assertFalse(DarkCostRules.studentsKeepAway(tier) && DarkCostRules.studentsAttack(tier));
        }
        assertEquals(8.0f, DarkCostRules.STUDENT_KEEP_AWAY_DISTANCE, EPS);
    }

    @Test
    void elvesFleeOnlyFromLost() {
        for (int tier = DarkRules.CLEAN; tier <= DarkRules.LOST; tier++) {
            assertEquals(tier == DarkRules.LOST, DarkCostRules.elvesFlee(tier), "tier " + tier);
        }
        assertEquals(8.0D, DarkCostRules.ELF_FLEE_DISTANCE, 1.0e-9);
        assertEquals(12.0D, DarkCostRules.ELF_FLEE_STOP_DISTANCE, 1.0e-9);
    }

    @Test
    void lostCostsTwoMaxHealthAndStacks() {
        assertEquals(-2.0D, DarkCostRules.LOST_MAX_HEALTH_DELTA, 1.0e-9);
        for (int tier = DarkRules.CLEAN; tier < DarkRules.LOST; tier++) {
            assertEquals(0.0D, DarkCostRules.maxHealthDelta(tier), 1.0e-9, "tier " + tier);
        }
        assertEquals(-2.0D, DarkCostRules.maxHealthDelta(DarkRules.LOST), 1.0e-9);
        double base = 20.0D;
        double hufflepuff = 4.0D;
        double horcrux = -2.0D;
        assertEquals(20.0D, base + hufflepuff + horcrux + DarkCostRules.maxHealthDelta(DarkRules.LOST), 1.0e-9);
        assertEquals(16.0D, base + horcrux + DarkCostRules.maxHealthDelta(DarkRules.LOST), 1.0e-9);
    }

    @Test
    void healthIsClampedButNeverKills() {
        assertEquals(18.0f, DarkCostRules.healthAfterMaxChange(20.0f, 18.0f), EPS);
        assertEquals(17.0f, DarkCostRules.healthAfterMaxChange(17.0f, 18.0f), EPS);
        assertEquals(5.0f, DarkCostRules.healthAfterMaxChange(5.0f, 22.0f), EPS);
        assertEquals(1.0f, DarkCostRules.healthAfterMaxChange(3.0f, 0.5f), EPS);
        assertEquals(0.0f, DarkCostRules.healthAfterMaxChange(0.0f, 18.0f), EPS);
        assertTrue(Float.isNaN(DarkCostRules.healthAfterMaxChange(Float.NaN, 18.0f)));
    }

    @Test
    void smokeAtCorruptedAndThickerAtLost() {
        assertEquals(0, DarkCostRules.smokeCount(DarkRules.CLEAN));
        assertEquals(0, DarkCostRules.smokeCount(DarkRules.SHADOWED));
        assertEquals(0, DarkCostRules.smokeCount(DarkRules.TAINTED));
        assertEquals(2, DarkCostRules.smokeCount(DarkRules.CORRUPTED));
        assertEquals(4, DarkCostRules.smokeCount(DarkRules.LOST));
        assertEquals(10, DarkCostRules.SMOKE_INTERVAL_TICKS);
        int due = 0;
        for (long tick = 0L; tick < 10L; tick++) {
            if (DarkCostRules.dueToSmoke(tick, 7L)) {
                due++;
            }
        }
        assertEquals(1, due);
        assertTrue(DarkCostRules.dueToSmoke(3L, 7L));
        assertTrue(DarkCostRules.dueToSmoke(-3L, 3L));
    }

    @Test
    void presenceCacheGoesStaleAfterOneSecond() {
        assertEquals(20, DarkCostRules.PRESENCE_REFRESH_TICKS);
        assertEquals(5, DarkCostRules.NPC_CHECK_EVERY_CALLS);
        assertFalse(DarkCostRules.presenceStale(100, 100));
        assertFalse(DarkCostRules.presenceStale(119, 100));
        assertTrue(DarkCostRules.presenceStale(120, 100));
        assertTrue(DarkCostRules.presenceStale(50, 100));
    }

    @Test
    void tiersOutsideTheRangeAreClamped() {
        assertEquals(DarkRules.CLEAN, DarkCostRules.clampTier(-1));
        assertEquals(DarkRules.LOST, DarkCostRules.clampTier(5));
        assertEquals(DarkCostRules.costsAt(DarkRules.CLEAN), DarkCostRules.costsAt(-3));
        assertEquals(DarkCostRules.costsAt(DarkRules.LOST), DarkCostRules.costsAt(9));
        assertEquals(0.15f, DarkCostRules.corporealPatronusChance(0.15f, -1), EPS);
    }

    @Test
    void theToggleOffMeansNoCost() {
        // enableDarkMagic = false makes DarkMagicData.getTier read Clean (Phase 1), so every cost reads tier 0.
        int off = DarkRules.effectiveTier(false, 100.0f);
        assertEquals(DarkRules.CLEAN, off);
        assertTrue(DarkCostRules.costsAt(off).isEmpty());
        assertEquals(0.15f, DarkCostRules.corporealPatronusChance(0.15f, off), EPS);
        assertEquals(1.0f, DarkCostRules.shieldUpkeepMultiplier(off), EPS);
        assertEquals(DarkCostRules.BASE_INSOMNIA_TICKS, DarkCostRules.insomniaTicks(off));
        assertEquals(0.0D, DarkCostRules.maxHealthDelta(off), 1.0e-9);
        assertEquals(0, DarkCostRules.smokeCount(off));
        assertFalse(DarkCostRules.dementorsPrefer(off) || DarkCostRules.studentsKeepAway(off)
                || DarkCostRules.studentsAttack(off) || DarkCostRules.elvesFlee(off));
    }
}
