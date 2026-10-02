package com.hogwartsmagic.spell.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncendioRulesTest {
    @Test
    void ignitionScalesAndRainCutsIt() {
        assertEquals(4, IncendioRules.igniteSeconds(1, false));
        assertEquals(5, IncendioRules.igniteSeconds(2, false));
        assertEquals(7, IncendioRules.igniteSeconds(4, false));
        assertEquals(2, IncendioRules.igniteSeconds(1, true));
        assertEquals(2, IncendioRules.igniteSeconds(3, true));
        assertEquals(3, IncendioRules.igniteSeconds(4, true));
    }

    @Test
    void fireBurstStartsAtAdept() {
        assertFalse(IncendioRules.spreadsFire(1));
        assertTrue(IncendioRules.spreadsFire(2));
        assertEquals(1.0, IncendioRules.fireRadius(2), 1e-9);
        assertEquals(2.0, IncendioRules.fireRadius(3), 1e-9);
        assertEquals(3.0, IncendioRules.fireRadius(4), 1e-9);
    }

    @Test
    void furnaceFuelAddsTenSecondsUpToTheCap() {
        assertEquals(200, IncendioRules.furnaceBurnTime(0));
        assertEquals(200, IncendioRules.furnaceBurnTime(-5));
        assertEquals(1600, IncendioRules.furnaceBurnTime(1500));
        assertEquals(1600, IncendioRules.furnaceBurnTime(1600));
        assertEquals(2000, IncendioRules.furnaceBurnTime(2000));
    }
}
