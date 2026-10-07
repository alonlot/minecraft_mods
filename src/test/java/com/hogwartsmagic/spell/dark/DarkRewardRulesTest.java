package com.hogwartsmagic.spell.dark;

import com.hogwartsmagic.spell.dark.DarkRewardRules.Reward;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkRewardRulesTest {
    private static final float EPS = 1.0e-6f;

    @Test
    void rewardTiersMatchTheSpec() {
        assertEquals(DarkRules.CORRUPTED, Reward.DARK_MANA_DISCOUNT.fromTier());
        assertEquals(DarkRules.CORRUPTED, Reward.DARK_MARK_BUFF.fromTier());
        assertEquals(DarkRules.LOST, Reward.DEATH_EATERS_NEUTRAL.fromTier());
        assertEquals(DarkRules.LOST, Reward.MORSMORDRE_ESCORT.fromTier());
        assertEquals(4, Reward.values().length);
        for (int tier = DarkRules.CLEAN; tier <= DarkRules.LOST; tier++) {
            assertEquals(tier >= DarkRules.CORRUPTED, DarkRewardRules.darkMarkBuffs(tier), "mark " + tier);
            assertEquals(tier == DarkRules.LOST, DarkRewardRules.deathEatersNeutral(tier), "neutral " + tier);
            assertEquals(tier == DarkRules.LOST, DarkRewardRules.morsmordreEscort(tier), "escort " + tier);
        }
    }

    @Test
    void onlyDarkSpellsGetTheDiscountAndOnlyFromCorrupted() {
        assertEquals(0.85f, DarkRewardRules.DARK_MANA_MULTIPLIER, EPS);
        for (String id : DarkRules.darkSpellIds()) {
            assertEquals(1.0f, DarkRewardRules.manaMultiplier(id, DarkRules.CLEAN), EPS, id);
            assertEquals(1.0f, DarkRewardRules.manaMultiplier(id, DarkRules.TAINTED), EPS, id);
            assertEquals(0.85f, DarkRewardRules.manaMultiplier(id, DarkRules.CORRUPTED), EPS, id);
            assertEquals(0.85f, DarkRewardRules.manaMultiplier(id, DarkRules.LOST), EPS, id);
        }
        for (String id : new String[] {"heal", "expecto_patronum", "stupefy", "obliviate", "confundo", "protego"}) {
            assertEquals(1.0f, DarkRewardRules.manaMultiplier(id, DarkRules.LOST), EPS, id);
        }
        assertEquals(1.0f, DarkRewardRules.manaMultiplier(null, DarkRules.LOST), EPS);
    }

    @Test
    void discountedCostsRoundAfterWandAndMastery() {
        // Base costs from SpellType: Avada 100, Morsmordre 90, Fiendfyre 85, Imperio 80, Crucio 60.
        float lost = DarkRewardRules.manaMultiplier("avada_kedavra", DarkRules.LOST);
        assertEquals(85, DarkRewardRules.manaCost(100 * 1.0f * 1.0f, lost));
        assertEquals(77, DarkRewardRules.manaCost(90 * 1.0f * 1.0f, lost));
        assertEquals(72, DarkRewardRules.manaCost(85 * 1.0f * 1.0f, lost));
        assertEquals(68, DarkRewardRules.manaCost(80 * 1.0f * 1.0f, lost));
        assertEquals(51, DarkRewardRules.manaCost(60 * 1.0f * 1.0f, lost));
        assertEquals(100, DarkRewardRules.manaCost(100 * 1.0f * 1.0f, 1.0f));
        // After a 0.8 wand and a 0.76 mastery multiplier: 100 * 0.8 * 0.76 = 60.8, then 0.85 = 51.68.
        assertEquals(52, DarkRewardRules.manaCost(100 * 0.8f * 0.76f, lost));
        assertEquals(61, DarkRewardRules.manaCost(100 * 0.8f * 0.76f, 1.0f));
        assertEquals(1, DarkRewardRules.manaCost(0.5f, lost));
    }

    @Test
    void theToggleOffMeansNoReward() {
        int off = DarkRules.effectiveTier(false, 100.0f);
        assertEquals(1.0f, DarkRewardRules.manaMultiplier("avada_kedavra", off), EPS);
        assertFalse(DarkRewardRules.darkMarkBuffs(off) || DarkRewardRules.deathEatersNeutral(off)
                || DarkRewardRules.morsmordreEscort(off));
    }

    @Test
    void tiersOutsideTheRangeAreClamped() {
        assertEquals(1.0f, DarkRewardRules.manaMultiplier("crucio", -1), EPS);
        assertEquals(0.85f, DarkRewardRules.manaMultiplier("crucio", 9), EPS);
        assertTrue(DarkRewardRules.deathEatersNeutral(7));
        assertFalse(DarkRewardRules.darkMarkBuffs(-4));
    }

    @Test
    void escortLastsFiveMinutesAndHasFourToSix() {
        assertEquals(6_000L, DarkRewardRules.ESCORT_DURATION_TICKS);
        assertEquals(1_006_000L, DarkRewardRules.escortUntil(1_000_000L));
        long until = DarkRewardRules.escortUntil(1_000L);
        assertTrue(DarkRewardRules.escortActive(1_000L, until));
        assertTrue(DarkRewardRules.escortActive(6_999L, until));
        assertFalse(DarkRewardRules.escortActive(7_000L, until));
        assertFalse(DarkRewardRules.escortActive(1_000L, 1_000L + 6_001L)); // a bad saved value ends it
        assertEquals(4, DarkRewardRules.escortSize(0));
        assertEquals(6, DarkRewardRules.escortSize(2));
        assertEquals(4, DarkRewardRules.escortSize(3));
        assertEquals(6, DarkRewardRules.escortSize(-1));
    }

    @Test
    void escortChecksOncePerSecond() {
        assertEquals(20, DarkRewardRules.ESCORT_CHECK_INTERVAL_TICKS);
        int due = 0;
        for (long tick = 0L; tick < 20L; tick++) {
            if (DarkRewardRules.dueToEscortCheck(tick, 13L)) {
                due++;
            }
        }
        assertEquals(1, due);
        assertTrue(DarkRewardRules.dueToEscortCheck(7L, 13L));
    }

    @Test
    void escortFollowsWithALeash() {
        double near = 3.0D * 3.0D;
        double mid = 10.0D * 10.0D;
        double far = 21.0D * 21.0D;
        assertFalse(DarkRewardRules.escortStartsFollowing(near, false));
        assertTrue(DarkRewardRules.escortStartsFollowing(mid, false));
        assertFalse(DarkRewardRules.escortStartsFollowing(mid, true)); // fighting near the owner
        assertTrue(DarkRewardRules.escortStartsFollowing(far, true)); // past the leash: back, target or not
        assertTrue(DarkRewardRules.escortKeepsFollowing(6.0D * 6.0D, false));
        assertFalse(DarkRewardRules.escortKeepsFollowing(near, false));
        assertFalse(DarkRewardRules.escortKeepsFollowing(mid, true));
        assertTrue(DarkRewardRules.escortKeepsFollowing(far, true));
        assertTrue(DarkRewardRules.beyondLeash(far));
        assertFalse(DarkRewardRules.beyondLeash(mid));
        assertFalse(DarkRewardRules.needsCatchUp(far));
        assertTrue(DarkRewardRules.needsCatchUp(33.0D * 33.0D));
    }

    @Test
    void escortCatchUpIsHorizontalAndLandsWithinReach() {
        assertEquals(25.0D, DarkRewardRules.horizontalDistanceSqr(3.0D, -4.0D), 1.0e-9);
        // An owner 40 blocks straight up is no reason to catch up; 33 blocks away on the ground is.
        assertFalse(DarkRewardRules.needsCatchUp(DarkRewardRules.horizontalDistanceSqr(0.0D, 0.0D)));
        assertTrue(DarkRewardRules.needsCatchUp(DarkRewardRules.horizontalDistanceSqr(33.0D, 0.0D)));
        assertTrue(DarkRewardRules.catchUpLandingInReach(8.0D * 8.0D));
        assertTrue(DarkRewardRules.catchUpLandingInReach(32.0D * 32.0D));
        assertFalse(DarkRewardRules.catchUpLandingInReach(40.0D * 40.0D)); // the ground far below a flying owner
    }

    @Test
    void escortLandsOnlyOnSafeGround() {
        assertTrue(DarkRewardRules.escortLandingOk(64, -64, 64, true, false, true, true));
        assertTrue(DarkRewardRules.escortLandingOk(70, -64, 64, true, false, true, true));
        assertFalse(DarkRewardRules.escortLandingOk(71, -64, 64, true, false, true, true)); // the owner underground
        assertFalse(DarkRewardRules.escortLandingOk(-64, -64, 64, true, false, true, true)); // an empty column
        assertFalse(DarkRewardRules.escortLandingOk(64, -64, 64, false, false, true, true)); // not sturdy
        assertFalse(DarkRewardRules.escortLandingOk(64, -64, 64, true, true, true, true)); // lava below
        assertFalse(DarkRewardRules.escortLandingOk(64, -64, 64, true, false, false, true)); // feet blocked
        assertFalse(DarkRewardRules.escortLandingOk(64, -64, 64, true, false, true, false)); // head blocked
    }

    @Test
    void escortEndsWhenTheOwnerDiedSinceSummoning() {
        assertFalse(DarkRewardRules.ownerDiedSince(3, 3));
        assertTrue(DarkRewardRules.ownerDiedSince(3, 4));
        assertFalse(DarkRewardRules.ownerDiedSince(-1, 4)); // nothing recorded yet
        assertTrue(DarkRewardRules.ownerDiedSince(0, 1));
    }

    @Test
    void innocentsAreVillagersStudentsElvesAndPassiveMobs() {
        assertTrue(DarkRewardRules.isInnocent(true, false, false, false));
        assertTrue(DarkRewardRules.isInnocent(false, true, false, false));
        assertTrue(DarkRewardRules.isInnocent(false, false, true, false));
        assertTrue(DarkRewardRules.isInnocent(false, false, false, true));
        assertFalse(DarkRewardRules.isInnocent(false, false, false, false)); // a zombie, a Death Eater, a player
    }

    @Test
    void innocentBonusOnlyForAKill() {
        assertEquals(3.0f, DarkRewardRules.innocentBonus(true, true), EPS);
        assertEquals(0.0f, DarkRewardRules.innocentBonus(false, true), EPS); // survived (a totem, a boss)
        assertEquals(0.0f, DarkRewardRules.innocentBonus(true, false), EPS);
        assertEquals(DarkRules.AVADA_INNOCENT_BONUS, DarkRewardRules.innocentBonus(true, true), EPS);
    }

    @Test
    void deedsChargeLikeCasts() {
        assertEquals(DarkRules.Payer.BODY, DarkRewardRules.deedPayer(false, false, false));
        assertEquals(DarkRules.Payer.PUPPETEER, DarkRewardRules.deedPayer(false, true, true));
        assertEquals(DarkRules.Payer.NOBODY, DarkRewardRules.deedPayer(false, false, true));
        assertEquals(DarkRules.Payer.NOBODY, DarkRewardRules.deedPayer(true, false, false));
        assertEquals(DarkRules.Payer.NOBODY, DarkRewardRules.deedPayer(true, true, false));
    }

    @Test
    void horcruxAddsThirtyAndLiftsToTheFloor() {
        assertEquals(60.0f, DarkRewardRules.afterHorcrux(0.0f), EPS);
        assertEquals(60.0f, DarkRewardRules.afterHorcrux(25.0f), EPS);
        assertEquals(66.5f, DarkRewardRules.afterHorcrux(36.5f), EPS);
        assertEquals(100.0f, DarkRewardRules.afterHorcrux(85.0f), EPS);
        assertEquals(100.0f, DarkRewardRules.afterHorcrux(100.0f), EPS);
        assertEquals(60.0f, DarkRewardRules.afterHorcrux(Float.NaN), EPS);
    }

    @Test
    void floorWhileTheHorcruxExists() {
        assertEquals(60.0f, DarkRewardRules.floor(true, 60.0f), EPS);
        assertEquals(60.0f, DarkRewardRules.floor(true, 85.0f), EPS);
        assertEquals(0.0f, DarkRewardRules.floor(true, 59.99f), EPS);
        assertEquals(0.0f, DarkRewardRules.floor(false, 85.0f), EPS);
        // Decay and redemption stop at the floor and resume when it goes.
        assertEquals(60.0f, DarkRules.afterLoss(60.5f, 1.0f, DarkRewardRules.floor(true, 60.5f)), EPS);
        assertEquals(59.5f, DarkRules.afterLoss(60.5f, 1.0f, DarkRewardRules.floor(false, 60.5f)), EPS);
        // No value below 60 is frozen: 45 with a Horcrux (a creative or toggle-off forging) still falls to 44.
        float floor45 = DarkRewardRules.floor(true, 45.0f);
        assertEquals(44.0f, DarkRules.afterLoss(45.0f, DarkRules.DECAY_STEP, floor45), EPS);
        assertEquals(44.0f, DarkRules.decay(45.0f, 0, DarkRules.DEFAULT_DECAY_TICKS, true, floor45,
                DarkRules.DEFAULT_DECAY_TICKS).darkness(), EPS);
        assertEquals(44.0f, DarkRewardRules.afterRedemption(45.0f, floor45), EPS);
    }

    @Test
    void redemptionLightensByOneThroughTheFloor() {
        assertEquals(84.0f, DarkRewardRules.afterRedemption(85.0f, DarkRules.NO_FLOOR), EPS);
        assertEquals(0.0f, DarkRewardRules.afterRedemption(0.5f, DarkRules.NO_FLOOR), EPS);
        assertEquals(0.0f, DarkRewardRules.afterRedemption(0.0f, DarkRules.NO_FLOOR), EPS);
        assertEquals(60.0f, DarkRewardRules.afterRedemption(60.4f, DarkRules.HORCRUX_FLOOR), EPS);
        assertEquals(60.0f, DarkRewardRules.afterRedemption(60.0f, DarkRules.HORCRUX_FLOOR), EPS);
    }

    @Test
    void onlyAWillingHealOfAnotherHurtThingRedeems() {
        assertTrue(DarkRewardRules.healRedeems(false, false, false, true, true));
        assertFalse(DarkRewardRules.healRedeems(true, false, false, true, true)); // yourself
        assertFalse(DarkRewardRules.healRedeems(false, true, false, true, true)); // an armour stand
        assertFalse(DarkRewardRules.healRedeems(false, false, true, true, true)); // a puppet heal
        assertFalse(DarkRewardRules.healRedeems(false, false, false, false, true)); // creative or spectator
        assertFalse(DarkRewardRules.healRedeems(false, false, false, true, false)); // already at full health
        assertTrue(DarkRewardRules.wasHurt(10.0f, 20.0f));
        assertFalse(DarkRewardRules.wasHurt(20.0f, 20.0f));
        assertFalse(DarkRewardRules.wasHurt(0.0f, 20.0f));
    }

    @Test
    void redemptionAtMostOncePerWindowOfPlay() {
        int window = DarkRules.DEFAULT_REDEMPTION_TICKS;
        assertTrue(DarkRewardRules.redemptionWindowOpen(false, 0, 0, window));
        assertFalse(DarkRewardRules.redemptionWindowOpen(true, 50_000, 40_000, window));
        assertFalse(DarkRewardRules.redemptionWindowOpen(true, 75_999, 40_000, window));
        assertTrue(DarkRewardRules.redemptionWindowOpen(true, 76_000, 40_000, window));
        assertTrue(DarkRewardRules.redemptionWindowOpen(true, 100, 40_000, window)); // the stat was reset
        assertFalse(DarkRewardRules.redemptionWindowOpen(true, 40_000, 40_000, 0)); // a bad window is one tick
        assertTrue(DarkRewardRules.redemptionWindowOpen(true, 40_001, 40_000, 0));
        assertTrue(DarkRewardRules.redemptionWindowOpen(true, Integer.MAX_VALUE, Integer.MIN_VALUE + 1, window));
    }

    @Test
    void redemptionWindowEqualsTheDecayPeriod() {
        // Decay: 1 point per 36000 ticks; redemption: 1 more per 36000 ticks of play at most.
        int window = DarkRules.DEFAULT_REDEMPTION_TICKS;
        int decay = DarkRules.DEFAULT_DECAY_TICKS;
        double hoursAlone = 100.0D * decay / 72_000.0D;
        double hoursWithHeals = 100.0D / (1.0D / decay + 1.0D / window) / 72_000.0D;
        assertEquals(50.0D, hoursAlone, 1.0e-9);
        assertEquals(25.0D, hoursWithHeals, 1.0e-9);
    }
}
