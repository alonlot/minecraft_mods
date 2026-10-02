package com.hogwartsmagic.spell.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BleedRulesTest {
    @Test
    void stacksClimbToThree() {
        assertEquals(0, BleedRules.nextAmplifier(-1));
        assertEquals(1, BleedRules.nextAmplifier(0));
        assertEquals(2, BleedRules.nextAmplifier(1));
        assertEquals(2, BleedRules.nextAmplifier(2));
        assertEquals(2, BleedRules.nextAmplifier(7));
    }

    @Test
    void damageIsOnePerStackCappedAtThree() {
        assertEquals(1f, BleedRules.damagePerSecond(0), 1e-6);
        assertEquals(2f, BleedRules.damagePerSecond(1), 1e-6);
        assertEquals(3f, BleedRules.damagePerSecond(2), 1e-6);
        assertEquals(3f, BleedRules.damagePerSecond(9), 1e-6);
    }

    @Test
    void damageTicksOncePerSecond() {
        assertTrue(BleedRules.isDamageTick(120));
        assertTrue(BleedRules.isDamageTick(20));
        assertFalse(BleedRules.isDamageTick(119));
        assertFalse(BleedRules.isDamageTick(0));
        assertFalse(BleedRules.isDamageTick(-1));
    }

    @Test
    void healingIsHalved() {
        assertEquals(0.5f, BleedRules.HEAL_MULTIPLIER, 1e-6);
    }
}
