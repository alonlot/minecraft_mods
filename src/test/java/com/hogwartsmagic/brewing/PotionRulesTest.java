package com.hogwartsmagic.brewing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionRulesTest {
    @Test
    void numbersMatchTheSpec() {
        assertEquals(4.0f, PotionRules.DITTANY_HEAL);
        assertEquals(3600, PotionRules.FELIX_TICKS);
        assertEquals(2, PotionRules.FELIX_LUCK_AMPLIFIER);
        assertEquals(1.25, PotionRules.FELIX_XP_MULTIPLIER);
        assertEquals(160, PotionRules.MANDRAKE_GAZE_WARD_TICKS);
        assertEquals(100, PotionRules.CURE_SLOWNESS_TICKS);
        assertEquals(400, PotionRules.LIVING_DEATH_STUN_TICKS);
        assertEquals(4.0, PotionRules.SPLASH_RADIUS);
    }

    @Test
    void felixBonusNeedsLuckThree() {
        assertEquals(1.0, PotionRules.felixXpMultiplier(-1));
        assertEquals(1.0, PotionRules.felixXpMultiplier(0));
        assertEquals(1.0, PotionRules.felixXpMultiplier(1));
        assertEquals(1.25, PotionRules.felixXpMultiplier(2));
        assertEquals(1.25, PotionRules.felixXpMultiplier(5));
    }

    @Test
    void splashReachesFourBlocks() {
        assertTrue(PotionRules.inSplash(0.0));
        assertTrue(PotionRules.inSplash(16.0));
        assertFalse(PotionRules.inSplash(16.01));
    }

    @Test
    void rollsLandBelowTheChance() {
        assertTrue(PotionRules.rolls(0.0, 0.05));
        assertTrue(PotionRules.rolls(0.0499, 0.05));
        assertFalse(PotionRules.rolls(0.05, 0.05));
        assertFalse(PotionRules.rolls(0.5, 0.0));
    }

    @Test
    void dropChancesAreTheRuledOnes() {
        assertEquals(0.25, PotionRules.VALERIAN_RUIN_CAMP_CHANCE);
        assertEquals(0.10, PotionRules.ASHWINDER_DEATH_EATER_CAMP_CHANCE);
        assertEquals(0.15, PotionRules.ASHWINDER_CHAMBER_CHANCE);
        assertEquals(0.05, PotionRules.ASHWINDER_BLAZE_CHANCE);
    }

    @Test
    void remorseHurtsButNeverKills() {
        assertEquals(3.0f, PotionRules.remorseDamage(20f));
        assertEquals(1.5f, PotionRules.remorseDamage(2.5f));
        assertEquals(0.0f, PotionRules.remorseDamage(1f));
        assertEquals(0.0f, PotionRules.remorseDamage(0.5f));
        assertEquals(5.0f, PotionRules.REMORSE_DARKNESS);
        assertEquals(144_000, PotionRules.DEFAULT_REMORSE_COOLDOWN_TICKS);
    }

    @Test
    void betterBrewsLastLonger() {
        assertEquals(48, PotionRules.qualityTicks(48, 0));
        assertEquals(72, PotionRules.qualityTicks(48, 1));
        assertEquals(96, PotionRules.qualityTicks(48, 2));
        assertEquals(96, PotionRules.qualityTicks(48, 9));
        assertEquals(48, PotionRules.qualityTicks(48, -3));
    }

    @Test
    void betterBrewsWorkHarder() {
        assertEquals(40f, PotionRules.qualityAmount(40f, 0));
        assertEquals(50f, PotionRules.qualityAmount(40f, 1));
        assertEquals(6f, PotionRules.qualityAmount(4f, 2));
        assertEquals(7.5f, PotionRules.qualityAmount(PotionRules.REMORSE_DARKNESS, 2));
        assertEquals(4f, PotionRules.qualityAmount(4f, -1));
        assertEquals(6f, PotionRules.qualityAmount(4f, 7));
    }

    @Test
    void veritaserumWorksOncePerVillagerPerDay() {
        assertTrue(PotionRules.veritaserumReady(false, 0, 5));
        assertFalse(PotionRules.veritaserumReady(true, 100, 24_099));
        assertTrue(PotionRules.veritaserumReady(true, 100, 24_100));
    }
}
