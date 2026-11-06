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
    void onlyACursedPlayerAtNightInANaturalDimensionFeelsIt() {
        assertTrue(LycanthropyRules.curseAwake(true, false, true, true));
        assertFalse(LycanthropyRules.curseAwake(false, false, true, true), "no curse");
        assertFalse(LycanthropyRules.curseAwake(true, true, true, true), "bitten tonight: waits for the next dusk");
        assertFalse(LycanthropyRules.curseAwake(true, false, false, true), "day");
        assertFalse(LycanthropyRules.curseAwake(true, false, true, false), "the Nether has no moon");
    }

    @Test
    void theBeastComesOnlyAtTheFullMoon() {
        assertTrue(LycanthropyRules.shouldBeBeast(true, false, true, true, 0));
        assertFalse(LycanthropyRules.isRestless(true, false, true, true, 0));
        for (int phase = 1; phase < 8; phase++) {
            assertFalse(LycanthropyRules.shouldBeBeast(true, false, true, true, phase), "phase " + phase);
            assertTrue(LycanthropyRules.isRestless(true, false, true, true, phase), "phase " + phase);
        }
        assertFalse(LycanthropyRules.shouldBeBeast(true, false, false, true, 0), "a full-moon day");
        assertFalse(LycanthropyRules.isRestless(false, false, true, true, 3), "no curse");
    }

    @Test
    void theNightBeforeTheFullMoonWarns() {
        assertTrue(LycanthropyRules.warnsOfFullMoon(7));
        for (int phase = 0; phase < 7; phase++) {
            assertFalse(LycanthropyRules.warnsOfFullMoon(phase), "phase " + phase);
        }
    }

    @Test
    void theBeastHowlsMoreThanARestlessNightGrowls() {
        assertEquals(1, LycanthropyRules.BEAST_AMPLIFIER);
        assertTrue(LycanthropyRules.HOWL_ONE_IN_CHECKS < LycanthropyRules.GROWL_ONE_IN_CHECKS);
    }

    @Test
    void beastEffectsAreToppedUpBeforeTheyRunOut() {
        assertTrue(LycanthropyRules.BEAST_EFFECT_REFRESH_BELOW > LycanthropyRules.CHECK_INTERVAL_TICKS);
        assertTrue(LycanthropyRules.BEAST_EFFECT_REFRESH_BELOW < LycanthropyRules.BEAST_EFFECT_TICKS);
        assertTrue(LycanthropyRules.BEAST_NIGHT_VISION_TICKS > 200 + LycanthropyRules.CHECK_INTERVAL_TICKS,
                "Night Vision must never drop under the 10-second flicker");
    }
}
