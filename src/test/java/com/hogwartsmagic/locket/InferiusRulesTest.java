package com.hogwartsmagic.locket;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InferiusRulesTest {
    @Test void nonFireDamageIsCutTo30Percent() {
        assertEquals(3.0f, InferiusRules.scaleDamage(10f, false, false), 1e-4);
    }
    @Test void fireDamageIsFull() {
        assertEquals(10f, InferiusRules.scaleDamage(10f, true, false), 1e-4);
    }
    @Test void burningDoublesEverything() {
        assertEquals(6f, InferiusRules.scaleDamage(10f, false, true), 1e-4);
        assertEquals(20f, InferiusRules.scaleDamage(10f, true, true), 1e-4);
    }
    @Test void grabRespectsCooldown() {
        assertTrue(InferiusRules.isGrabReady(500, 0));
        assertFalse(InferiusRules.isGrabReady(150, 100));
        assertTrue(InferiusRules.isGrabReady(200, 100));
    }
}
