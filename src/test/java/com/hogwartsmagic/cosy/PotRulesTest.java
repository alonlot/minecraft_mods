package com.hogwartsmagic.cosy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The cooking pot cooks only while it is hot AND being stirred; stirring runs down unless the ladle is enchanted. */
class PotRulesTest {

    @Test
    void progressNeedsHeatAndStirring() {
        assertEquals(5, PotRules.nextProgress(5, true, false), "hot but not stirred");
        assertEquals(6, PotRules.nextProgress(5, true, true), "hot and stirred");
        assertEquals(5, PotRules.nextProgress(5, false, true), "stirred but cold");
        assertEquals(5, PotRules.nextProgress(5, false, false), "neither");
    }

    @Test
    void manualStirringRunsDown() {
        assertEquals(59, PotRules.nextStir(60, false));
        assertEquals(0, PotRules.nextStir(0, false));
        assertEquals(0, PotRules.nextStir(1, false));
    }

    @Test
    void anEnchantedLadleNeverRunsDown() {
        assertEquals(60, PotRules.nextStir(60, true));
        assertEquals(0, PotRules.nextStir(0, true));
    }

    @Test
    void constants() {
        assertEquals(60, PotRules.MANUAL_STIR_TICKS);
        assertEquals(6, PotRules.MAX_INGREDIENTS);
        assertEquals(8 * 1600, PotRules.STOVE_MAX_BURN);
    }

    @Test
    void stirringIsEitherTicksLeftOrEnchanted() {
        assertEquals(true, PotRules.isStirring(1, false));
        assertEquals(false, PotRules.isStirring(0, false));
        assertEquals(true, PotRules.isStirring(0, true));
    }

    @Test
    void fuelIsAcceptedUpToTheCap() {
        assertEquals(true, PotRules.canTakeFuel(0, 1600));
        assertEquals(true, PotRules.canTakeFuel(7 * 1600, 1600));
        assertEquals(false, PotRules.canTakeFuel(7 * 1600 + 1, 1600));
        assertEquals(false, PotRules.canTakeFuel(0, 0), "an item that does not burn is refused");
        assertEquals(false, PotRules.canTakeFuel(0, 20000), "a lava bucket would overfill it");
    }
}
