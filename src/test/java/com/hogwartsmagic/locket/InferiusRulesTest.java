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
    @Test void landPullWeakerThanWater() {
        assertEquals(0.02, InferiusRules.LAND_PULL_PER_TICK, 1e-9);
        assertTrue(InferiusRules.LAND_PULL_PER_TICK > 0);
        assertTrue(InferiusRules.LAND_PULL_PER_TICK < InferiusRules.WATER_PULL_PER_TICK);
    }
    @Test void pastArchMeansTurnBack() {
        assertTrue(InferiusRules.pastArch(30.5, 30.0));
        assertFalse(InferiusRules.pastArch(30.0, 30.0));
        assertFalse(InferiusRules.pastArch(12.0, 30.0));
    }
    @Test void latchReleasedWhenRiderGone() {
        assertTrue(InferiusRules.latchReleasedWhenRiderGone(false, false, false));
        assertTrue(InferiusRules.latchReleasedWhenRiderGone(true, true, false));
        assertTrue(InferiusRules.latchReleasedWhenRiderGone(true, false, true));
        assertFalse(InferiusRules.latchReleasedWhenRiderGone(true, false, false));
    }
    @Test void latchHurtsOnAnInterval() {
        assertEquals(1f, InferiusRules.LATCH_DAMAGE, 1e-6);
        assertEquals(40, InferiusRules.LATCH_DAMAGE_INTERVAL);
    }
}
