package com.hogwartsmagic.spell.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrucioRulesTest {
    @Test
    void channelGrowsFromTwoToFiveSeconds() {
        assertEquals(40, CrucioRules.channelTicks(1));
        assertEquals(60, CrucioRules.channelTicks(2));
        assertEquals(80, CrucioRules.channelTicks(3));
        assertEquals(100, CrucioRules.channelTicks(4));
    }

    @Test
    void damageLandsEveryHalfSecondButNotOnTheFirstTick() {
        assertFalse(CrucioRules.isDamageTick(0));
        assertFalse(CrucioRules.isDamageTick(9));
        assertTrue(CrucioRules.isDamageTick(10));
        assertTrue(CrucioRules.isDamageTick(20));
    }

    @Test
    void aFullChannelDealsSixToFifteen() {
        assertEquals(6.0f, CrucioRules.fullChannelDamage(1), 1e-6);
        assertEquals(15.0f, CrucioRules.fullChannelDamage(4), 1e-6);
    }

    @Test
    void onlyMasterMakesTheVictimFlee() {
        assertFalse(CrucioRules.fleesAfter(3));
        assertTrue(CrucioRules.fleesAfter(4));
    }

    @Test
    void sixTicksWithoutAHeartbeatEndsTheChannel() {
        assertFalse(CrucioRules.heartbeatLapsed(106L, 100L));
        assertTrue(CrucioRules.heartbeatLapsed(107L, 100L));
    }
}
