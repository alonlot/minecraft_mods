package com.hogwartsmagic.lycanthropy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LycanthropyRulesTest {
    @Test
    void theFullMoonBitesHarder() {
        assertTrue(LycanthropyRules.biteChance(true) > LycanthropyRules.biteChance(false));
        assertTrue(LycanthropyRules.biteInfects(0.29, false));
        assertFalse(LycanthropyRules.biteInfects(0.30, false));
        assertTrue(LycanthropyRules.biteInfects(0.59, true));
        assertFalse(LycanthropyRules.biteInfects(0.60, true));
    }

    @Test
    void onlyACursedPlayerAtNightInANaturalDimensionTurns() {
        assertTrue(LycanthropyRules.shouldBeBeast(true, false, true, true));
        assertFalse(LycanthropyRules.shouldBeBeast(false, false, true, true), "no curse");
        assertFalse(LycanthropyRules.shouldBeBeast(true, true, true, true), "bitten tonight: waits for the next dusk");
        assertFalse(LycanthropyRules.shouldBeBeast(true, false, false, true), "day");
        assertFalse(LycanthropyRules.shouldBeBeast(true, false, true, false), "the Nether has no moon");
    }

    @Test
    void aNightBiteWaitsForTheNextDusk() {
        assertTrue(LycanthropyRules.awaitsDuskAfterBite(true));
        assertFalse(LycanthropyRules.awaitsDuskAfterBite(false));
    }

    @Test
    void theBeastIsStrongerAtTheFullMoon() {
        assertEquals(1, LycanthropyRules.beastAmplifier(true));
        assertEquals(0, LycanthropyRules.beastAmplifier(false));
        assertTrue(LycanthropyRules.howlOneIn(true) < LycanthropyRules.howlOneIn(false));
    }

    @Test
    void beastEffectsAreToppedUpBeforeTheyRunOut() {
        assertTrue(LycanthropyRules.BEAST_EFFECT_REFRESH_BELOW > LycanthropyRules.CHECK_INTERVAL_TICKS);
        assertTrue(LycanthropyRules.BEAST_EFFECT_REFRESH_BELOW < LycanthropyRules.BEAST_EFFECT_TICKS);
        assertTrue(LycanthropyRules.BEAST_NIGHT_VISION_TICKS > 200 + LycanthropyRules.CHECK_INTERVAL_TICKS,
                "Night Vision must never drop under the 10-second flicker");
    }
}
