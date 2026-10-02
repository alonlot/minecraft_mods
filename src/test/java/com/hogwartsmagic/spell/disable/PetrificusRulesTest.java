package com.hogwartsmagic.spell.disable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetrificusRulesTest {
    @Test
    void durationsAreFiveSevenNineTwelveSeconds() {
        assertEquals(100, PetrificusRules.durationTicks(1));
        assertEquals(140, PetrificusRules.durationTicks(2));
        assertEquals(180, PetrificusRules.durationTicks(3));
        assertEquals(240, PetrificusRules.durationTicks(4));
    }

    @Test
    void noviceBreaksOnAnyDamage() {
        assertTrue(PetrificusRules.breaks(1, 0.1f));
        assertFalse(PetrificusRules.breaks(1, 0.0f));
        assertFalse(PetrificusRules.breaks(1, Float.NaN));
    }

    @Test
    void fromAdeptHitsUnderTwoHeartsDoNotBreak() {
        assertFalse(PetrificusRules.breaks(2, 3.9f));
        assertTrue(PetrificusRules.breaks(2, 4.0f));
        assertFalse(PetrificusRules.breaks(4, 3.9f));
        assertTrue(PetrificusRules.breaks(4, 10.0f));
    }

    @Test
    void onlyMasterShatters() {
        assertEquals(1.0f, PetrificusRules.shatterMultiplier(3), 1e-6);
        assertEquals(1.5f, PetrificusRules.shatterMultiplier(4), 1e-6);
    }
}
