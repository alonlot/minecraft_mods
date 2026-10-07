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
}
