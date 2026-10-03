package com.hogwartsmagic.spell.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObliviateRulesTest {
    @Test
    void mobsIgnoreTheCasterLongerWithRank() {
        assertEquals(100, ObliviateRules.ignoreTicks(1));
        assertEquals(200, ObliviateRules.ignoreTicks(2));
        assertEquals(400, ObliviateRules.ignoreTicks(3));
        assertEquals(600, ObliviateRules.ignoreTicks(4));
    }

    @Test
    void playersAreSilencedTwoToFourSeconds() {
        assertEquals(40, ObliviateRules.silenceTicks(1));
        assertEquals(60, ObliviateRules.silenceTicks(2));
        assertEquals(60, ObliviateRules.silenceTicks(3));
        assertEquals(80, ObliviateRules.silenceTicks(4));
    }

    @Test
    void onlyMasterCastsTheMemoryCharm() {
        assertFalse(ObliviateRules.memoryCharm(3));
        assertTrue(ObliviateRules.memoryCharm(4));
    }

    @Test
    void ranksOutsideOneToFourAreClamped() {
        assertEquals(100, ObliviateRules.ignoreTicks(0));
        assertEquals(600, ObliviateRules.ignoreTicks(9));
    }

    @Test
    void forgettingEndsAtTheStoredTime() {
        assertTrue(ObliviateRules.stillForgotten(99L, 100L));
        assertFalse(ObliviateRules.stillForgotten(100L, 100L));
    }
}
