package com.hogwartsmagic.spell.disable;

import com.hogwartsmagic.spell.combat.BrittleRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlaciusRulesTest {
    @Test
    void slowLastsLongerWithRankAndInTheCold() {
        assertEquals(80, GlaciusRules.slowTicks(1, false));
        assertEquals(120, GlaciusRules.slowTicks(1, true));
        assertEquals(140, GlaciusRules.slowTicks(4, false));
    }

    @Test
    void windowAndFreezeScale() {
        assertEquals(80, GlaciusRules.chillWindowTicks(1));
        assertEquals(120, GlaciusRules.chillWindowTicks(4));
        assertEquals(40, GlaciusRules.freezeTicks(1));
        assertEquals(50, GlaciusRules.freezeTicks(2));
        assertEquals(70, GlaciusRules.freezeTicks(4));
    }

    @Test
    void expertBrittleIsSixtyPercent() {
        assertEquals(1.3f, BrittleRules.bonusMultiplier(GlaciusRules.brittleAmplifier(2)), 1e-4);
        assertEquals(1.6f, BrittleRules.bonusMultiplier(GlaciusRules.brittleAmplifier(3)), 1e-4);
        assertEquals(1.6f, BrittleRules.bonusMultiplier(GlaciusRules.brittleAmplifier(4)), 1e-4);
    }

    @Test
    void brittleOutlastsTheFreeze() {
        assertEquals(100, GlaciusRules.brittleTicks(40));
    }

    @Test
    void onlyMasterShatters() {
        assertFalse(GlaciusRules.shatters(3));
        assertTrue(GlaciusRules.shatters(4));
    }
}
