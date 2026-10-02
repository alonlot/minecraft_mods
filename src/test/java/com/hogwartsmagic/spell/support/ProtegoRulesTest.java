package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtegoRulesTest {
    @Test
    void blockFractionPerRank() {
        assertEquals(0.40f, ProtegoRules.blockFraction(1), 1e-4);
        assertEquals(0.50f, ProtegoRules.blockFraction(2), 1e-4);
        assertEquals(0.60f, ProtegoRules.blockFraction(3), 1e-4);
        assertEquals(0.75f, ProtegoRules.blockFraction(4), 1e-4);
    }

    @Test
    void parryWindowWidensAtExpert() {
        assertEquals(8, ProtegoRules.parryWindowTicks(1));
        assertEquals(8, ProtegoRules.parryWindowTicks(2));
        assertEquals(12, ProtegoRules.parryWindowTicks(3));
        assertEquals(12, ProtegoRules.parryWindowTicks(4));
    }

    @Test
    void parryModeClimbsFromDissolveToReturnToSender() {
        assertEquals(ProtegoRules.PARRY_DISSOLVE, ProtegoRules.parryMode(1));
        assertEquals(ProtegoRules.PARRY_RANDOM, ProtegoRules.parryMode(2));
        assertEquals(ProtegoRules.PARRY_AT_CASTER, ProtegoRules.parryMode(3));
        assertEquals(ProtegoRules.PARRY_AT_CASTER, ProtegoRules.parryMode(4));
    }

    @Test
    void masterRefundsAndKnocksBack() {
        assertEquals(0f, ProtegoRules.refundFraction(3), 1e-4);
        assertEquals(0.5f, ProtegoRules.refundFraction(4), 1e-4);
        assertFalse(ProtegoRules.knocksBackMelee(3));
        assertTrue(ProtegoRules.knocksBackMelee(4));
    }

    @Test
    void parryWindowCountsFromTheRaise() {
        assertTrue(ProtegoRules.inParryWindow(100, 100, 1));
        assertTrue(ProtegoRules.inParryWindow(100, 107, 1));
        assertFalse(ProtegoRules.inParryWindow(100, 108, 1));
        assertTrue(ProtegoRules.inParryWindow(100, 111, 3));
        assertFalse(ProtegoRules.inParryWindow(100, 112, 3));
        assertFalse(ProtegoRules.inParryWindow(100, 99, 1));
    }

    @Test
    void frontIsTheHalfYouFace() {
        assertTrue(ProtegoRules.isFront(0, 1, 0, 5));
        assertFalse(ProtegoRules.isFront(0, 1, 0, -5));
        assertTrue(ProtegoRules.isFront(0, 1, 3, 0));
        assertTrue(ProtegoRules.isFront(0, 0, 1, 1));
    }

    @Test
    void reducedDamageKeepsTheUnblockedShare() {
        assertEquals(6.0f, ProtegoRules.reducedDamage(10f, 1), 1e-4);
        assertEquals(2.5f, ProtegoRules.reducedDamage(10f, 4), 1e-4);
    }

    @Test
    void randomReflectStaysWithinSixtyDegrees() {
        assertEquals(90f, ProtegoRules.randomReflectYaw(90f, 0.5), 1e-4);
        assertEquals(30f, ProtegoRules.randomReflectYaw(90f, 0.0), 1e-4);
        assertEquals(150f, ProtegoRules.randomReflectYaw(90f, 1.0), 1e-4);
    }

    @Test
    void unforgivablesAndRepeatsAreNeverReflected() {
        assertTrue(ProtegoRules.canReflect(false, false));
        assertFalse(ProtegoRules.canReflect(true, false));
        assertFalse(ProtegoRules.canReflect(false, true));
    }
}
