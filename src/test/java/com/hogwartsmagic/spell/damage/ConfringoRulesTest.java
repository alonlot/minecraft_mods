package com.hogwartsmagic.spell.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfringoRulesTest {
    @Test
    void powerGrowsAndRainDampensIt() {
        assertEquals(1.5f, ConfringoRules.power(1, false), 1e-6);
        assertEquals(1.8f, ConfringoRules.power(2, false), 1e-6);
        assertEquals(2.1f, ConfringoRules.power(3, false), 1e-6);
        assertEquals(2.4f, ConfringoRules.power(4, false), 1e-6);
        assertEquals(1.155f, ConfringoRules.power(1, true), 1e-4);
    }

    @Test
    void ignitionAndSplash() {
        assertEquals(4, ConfringoRules.igniteSeconds(1));
        assertEquals(7, ConfringoRules.igniteSeconds(4));
        assertEquals(0.0, ConfringoRules.splashIgniteRadius(1), 1e-9);
        assertEquals(1.5, ConfringoRules.splashIgniteRadius(2), 1e-9);
        assertEquals(2.5, ConfringoRules.splashIgniteRadius(4), 1e-9);
        assertEquals(2, ConfringoRules.splashIgniteSeconds(2));
        assertEquals(3, ConfringoRules.splashIgniteSeconds(4));
    }

    @Test
    void onlyMasterPunishesBrittle() {
        assertEquals(1.0f, ConfringoRules.brittleBonus(3), 1e-6);
        assertEquals(1.5f, ConfringoRules.brittleBonus(4), 1e-6);
    }
}
