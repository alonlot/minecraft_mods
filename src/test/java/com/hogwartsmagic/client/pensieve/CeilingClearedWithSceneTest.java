package com.hogwartsmagic.client.pensieve;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review focus 1: leaving a memory must take the enchanted ceiling with it. The renderer draws only what
 * {@link EffectVisuals#ceilings()} returns, and every exit path runs {@link EffectVisuals#clear()} (through
 * {@code MemoryEvents.clear}), so the sky is gone the frame the scene clears.
 */
class CeilingClearedWithSceneTest {

    @Test
    void ceilingClearedWithScene() {
        EffectVisuals.clear();
        EffectVisuals.startCeiling(new EnchantedCeiling.Sky("storm", 0, 30, 0, 15, 10, 60, 0f, 42L, 0f));
        EffectVisuals.startCeiling(new EnchantedCeiling.Sky("night", 100, 30, 0, 5, 5, 0, 20f, 43L, 0f));
        assertEquals(2, EffectVisuals.ceilings().size());
        EffectVisuals.clear();
        assertTrue(EffectVisuals.ceilings().isEmpty());
    }

    @Test
    void lateJoinerHearsOnlyThunderStillToCome() {
        EnchantedCeiling.Sky early = new EnchantedCeiling.Sky("storm", 0, 30, 0, 15, 10, 60, 0f, 42L, 0f);
        EnchantedCeiling.Sky late = new EnchantedCeiling.Sky("storm", 0, 30, 0, 15, 10, 60, 0f, 42L, 2000f);
        assertTrue(late.nextThunderForTest() > early.nextThunderForTest());
    }
}
