package com.hogwartsmagic.cosy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AmbienceRulesTest {
    @Test
    void rainOnWindowNeedsAllThree() {
        int trues = 0;
        for (int bits = 0; bits < 8; bits++) {
            boolean raining = (bits & 1) != 0;
            boolean underRoof = (bits & 2) != 0;
            boolean glass = (bits & 4) != 0;
            boolean result = AmbienceRules.rainOnWindow(raining, underRoof, glass);
            if (raining && underRoof && glass) {
                assertTrue(result, "all three true should give rain on the window");
                trues++;
            } else {
                assertFalse(result, "raining=" + raining + " underRoof=" + underRoof + " glass=" + glass);
            }
        }
        assertEquals(1, trues);
    }

    @Test
    void fadeStepMovesByAFortieth() {
        assertEquals(0.025f, AmbienceRules.fadeStep(0f, true));
        assertEquals(1f, AmbienceRules.fadeStep(1f, true));
        assertEquals(0f, AmbienceRules.fadeStep(0f, false));
        assertEquals(0.475f, AmbienceRules.fadeStep(0.5f, false), 1e-6f);
    }

    @Test
    void fadeTakesAboutTwoSeconds() {
        float level = 0f;
        int ticks = 0;
        while (level < 1f) {
            level = AmbienceRules.fadeStep(level, true);
            ticks++;
        }
        assertEquals(40, ticks);
    }

    @Test
    void crackleFallsOffLinearlyToTwelveBlocks() {
        assertEquals(1f, AmbienceRules.crackleScale(0.0), 1e-6f);
        assertEquals(0.5f, AmbienceRules.crackleScale(6.0), 1e-6f);
        assertEquals(0f, AmbienceRules.crackleScale(12.0), 1e-6f);
        assertEquals(0f, AmbienceRules.crackleScale(40.0), 1e-6f);
    }
}
