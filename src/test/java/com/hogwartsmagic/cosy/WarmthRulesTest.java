package com.hogwartsmagic.cosy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WarmthRulesTest {
    @Test
    void healsOnTheIntervalWhenNotRecentlyHurt() {
        assertTrue(WarmthRules.shouldHeal(80, 100));
    }

    @Test
    void doesNotHealJustAfterBeingHurt() {
        assertFalse(WarmthRules.shouldHeal(80, 99));
    }

    @Test
    void doesNotHealOffTheInterval() {
        assertFalse(WarmthRules.shouldHeal(79, 500));
    }

    @Test
    void constantsMatchTheSpec() {
        assertEquals(6.0, WarmthRules.RADIUS);
        assertEquals(40, WarmthRules.PULSE_TICKS);
        assertEquals(200, WarmthRules.WARMED_TICKS);
        assertEquals(80, WarmthRules.HEAL_INTERVAL);
        assertEquals(100, WarmthRules.HURT_GRACE);
    }
}
