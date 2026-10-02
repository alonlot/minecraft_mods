package com.hogwartsmagic.spell.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DolohovRulesTest {
    @Test
    void woundGrowsDeeperAndLonger() {
        assertEquals(2, DolohovRules.hearts(1));
        assertEquals(3, DolohovRules.hearts(2));
        assertEquals(4, DolohovRules.hearts(3));
        assertEquals(5, DolohovRules.hearts(4));
        assertEquals(600, DolohovRules.ticks(1));
        assertEquals(900, DolohovRules.ticks(2));
        assertEquals(1200, DolohovRules.ticks(3));
        assertEquals(1800, DolohovRules.ticks(4));
        assertEquals(6f, DolohovRules.DAMAGE, 1e-6);
    }

    @Test
    void onlyMasterSpreadsForHalf() {
        assertFalse(DolohovRules.spreads(3));
        assertTrue(DolohovRules.spreads(4));
        assertEquals(3, DolohovRules.spreadHearts(5));
        assertEquals(1, DolohovRules.spreadHearts(1));
        assertEquals(900, DolohovRules.spreadTicks(1800));
        assertEquals(20, DolohovRules.spreadTicks(10));
    }
}
