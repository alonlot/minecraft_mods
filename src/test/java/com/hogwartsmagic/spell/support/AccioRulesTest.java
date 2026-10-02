package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccioRulesTest {
    @Test
    void reelCapGrowsAndNeverTakesBosses() {
        assertTrue(AccioRules.canReel(1, 20f, false));
        assertFalse(AccioRules.canReel(1, 24f, false));
        assertTrue(AccioRules.canReel(2, 40f, false));
        assertTrue(AccioRules.canReel(3, 80f, false));
        assertFalse(AccioRules.canReel(3, 100f, false));
        assertFalse(AccioRules.canReel(2, 40.5f, false));
        assertFalse(AccioRules.canReel(3, 80.5f, false));
        assertTrue(AccioRules.canReel(4, 500f, false));
        assertFalse(AccioRules.canReel(4, 20f, true));
    }

    @Test
    void itemRadiusPerRank() {
        assertEquals(1.5, AccioRules.itemRadius(1), 1e-9);
        assertEquals(3.0, AccioRules.itemRadius(2), 1e-9);
        assertEquals(4.0, AccioRules.itemRadius(3), 1e-9);
        assertEquals(5.0, AccioRules.itemRadius(4), 1e-9);
    }

    @Test
    void unlocks() {
        assertFalse(AccioRules.pullsContainers(2));
        assertTrue(AccioRules.pullsContainers(3));
        assertFalse(AccioRules.pullsPlayerItem(3));
        assertTrue(AccioRules.pullsPlayerItem(4));
    }

    @Test
    void pullSpeedRisesWithDistanceAndCaps() {
        assertEquals(0.3, AccioRules.itemPullSpeed(0.0), 1e-9);
        assertEquals(0.8, AccioRules.itemPullSpeed(5.0), 1e-9);
        assertEquals(1.2, AccioRules.itemPullSpeed(40.0), 1e-9);
    }
}
