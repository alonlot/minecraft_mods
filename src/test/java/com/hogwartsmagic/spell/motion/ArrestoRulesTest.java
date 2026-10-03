package com.hogwartsmagic.spell.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArrestoRulesTest {
    @Test
    void freezingFromExpertWiderAtMaster() {
        assertFalse(ArrestoRules.freezes(2));
        assertTrue(ArrestoRules.freezes(3));
        assertEquals(0.0, ArrestoRules.freezeRadius(2), 1e-9);
        assertEquals(4.0, ArrestoRules.freezeRadius(3), 1e-9);
        assertEquals(6.0, ArrestoRules.freezeRadius(4), 1e-9);
        assertFalse(ArrestoRules.redirects(3));
        assertTrue(ArrestoRules.redirects(4));
    }

    @Test
    void timing() {
        assertFalse(ArrestoRules.released(140L, 139L));
        assertTrue(ArrestoRules.released(140L, 140L));
        assertTrue(ArrestoRules.inRedirectWindow(140L, 140L));
        assertFalse(ArrestoRules.inRedirectWindow(140L, 141L));
        assertEquals(40, ArrestoRules.FREEZE_TICKS);
    }

    @Test
    void redirectSpeedIsBounded() {
        assertEquals(1.0, ArrestoRules.redirectSpeed(0.2), 1e-9);
        assertEquals(2.5, ArrestoRules.redirectSpeed(2.5), 1e-9);
        assertEquals(3.0, ArrestoRules.redirectSpeed(9.0), 1e-9);
        assertEquals(1.0, ArrestoRules.redirectSpeed(Double.NaN), 1e-9);
    }
}
