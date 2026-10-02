package com.hogwartsmagic.spell.mastery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MasteryRulesTest {
    @Test
    void alliedAlwaysZero() {
        assertEquals(0.0, MasteryRules.targetMultiplier(true, false, false), 1e-9);
        assertEquals(0.0, MasteryRules.targetMultiplier(true, true, true), 1e-9);
    }

    @Test
    void lowestPenaltyWins() {
        assertEquals(1.0, MasteryRules.targetMultiplier(false, false, false), 1e-9);
        assertEquals(0.25, MasteryRules.targetMultiplier(false, true, false), 1e-9);
        assertEquals(0.5, MasteryRules.targetMultiplier(false, false, true), 1e-9);
        assertEquals(0.25, MasteryRules.targetMultiplier(false, true, true), 1e-9);
    }

    @Test
    void gateWindowIsAtLeastTenSeconds() {
        assertEquals(200L, MasteryRules.gateWindowTicks(20));
        assertEquals(1200L, MasteryRules.gateWindowTicks(1200));
    }

    @Test
    void playersPayHalfAndAlliedPlayersNothing() {
        assertEquals(0.5, MasteryRules.targetMultiplier(false, false, false, true), 1e-9);
        assertEquals(0.0, MasteryRules.targetMultiplier(true, false, false, true), 1e-9);
        assertEquals(0.25, MasteryRules.targetMultiplier(false, true, false, true), 1e-9);
        assertEquals(1.0, MasteryRules.targetMultiplier(false, false, false, false), 1e-9);
    }

    @Test
    void playerPairWindowIsThirtySeconds() {
        assertEquals(600L, MasteryRules.PLAYER_PAIR_WINDOW_TICKS);
    }
}
