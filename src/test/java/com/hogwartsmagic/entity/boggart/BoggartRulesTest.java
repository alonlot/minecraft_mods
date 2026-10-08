package com.hogwartsmagic.entity.boggart;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoggartRulesTest {
    @Test
    void hitsToBanishFallWithRank() {
        assertEquals(3, BoggartRules.hitsToBanish(1));
        assertEquals(2, BoggartRules.hitsToBanish(2));
        assertEquals(2, BoggartRules.hitsToBanish(3));
        assertEquals(1, BoggartRules.hitsToBanish(4));
    }

    @Test
    void ranksOutsideOneToFourAreClamped() {
        assertEquals(3, BoggartRules.hitsToBanish(0));
        assertEquals(3, BoggartRules.hitsToBanish(-5));
        assertEquals(1, BoggartRules.hitsToBanish(9));
    }

    @Test
    void pickFormTakesTheHighestCount() {
        Map<String, Integer> fears = new HashMap<>();
        fears.put("minecraft:zombie", 2);
        fears.put("minecraft:creeper", 7);
        fears.put("hogwartsmagic:acromantula", 1);
        assertEquals("minecraft:creeper", BoggartRules.pickForm(fears));
    }

    @Test
    void pickFormBreaksTiesByStringOrder() {
        Map<String, Integer> fears = new HashMap<>();
        fears.put("minecraft:zombie", 3);
        fears.put("minecraft:creeper", 3);
        fears.put("minecraft:skeleton", 3);
        assertEquals("minecraft:creeper", BoggartRules.pickForm(fears));
    }

    @Test
    void playerKillerBecomesADementor() {
        Map<String, Integer> fears = new HashMap<>();
        fears.put("player", 5);
        fears.put("minecraft:zombie", 1);
        assertEquals("hogwartsmagic:dementor", BoggartRules.pickForm(fears));
    }

    @Test
    void noRecordedDeathsMeansAcromantula() {
        assertEquals("hogwartsmagic:acromantula", BoggartRules.pickForm(new HashMap<>()));
        assertEquals("hogwartsmagic:acromantula", BoggartRules.pickForm(null));
        assertEquals(BoggartRules.DEFAULT_FORM, BoggartRules.pickForm(Map.of()));
    }

    @Test
    void laughScaleIsFullSizeBeforeAnyHit() {
        assertEquals(1.0f, BoggartRules.laughScale(0, 0, 3), 1e-6);
        assertEquals(1.0f, BoggartRules.laughScale(60, 0, 3), 1e-6);
    }

    @Test
    void laughScaleShrinksWithHitsAndFloorsAtAThird() {
        // 1 of 3 hits: 1 - 0.5 * 1/3
        assertEquals(1.0f - 0.5f / 3.0f, BoggartRules.laughScale(0, 1, 3), 1e-5);
        // 2 of 2 hits: 1 - 0.5 = 0.5
        assertEquals(0.5f, BoggartRules.laughScale(0, 2, 2), 1e-6);
        // Far past the needed count: floor
        assertEquals(0.35f, BoggartRules.laughScale(0, 9, 2), 1e-6);
        // A bad "needed" of 0 does not divide by zero
        assertEquals(0.35f, BoggartRules.laughScale(0, 1, 0), 1e-6);
    }

    @Test
    void laughScaleWobblesOnlyInTheFirstTenLaughTicks() {
        // laughTicks counts down from 60, so the first ten laugh ticks are 60..51.
        float steady = BoggartRules.laughScale(0, 1, 3);
        assertEquals(steady, BoggartRules.laughScale(50, 1, 3), 1e-6);
        assertEquals(steady, BoggartRules.laughScale(5, 1, 3), 1e-6);
        float wobbled = BoggartRules.laughScale(55, 1, 3);
        float expected = (float) (steady * (1.0 + 0.15 * Math.sin(55 * 1.2)));
        assertEquals(expected, wobbled, 1e-5);
        assertTrue(Math.abs(wobbled - steady) > 1e-4);
    }

    @Test
    void shouldHideAfterTwoHundredTicksAlone() {
        assertFalse(BoggartRules.shouldHide(0));
        assertFalse(BoggartRules.shouldHide(BoggartRules.HIDE_AFTER_TICKS - 1));
        assertTrue(BoggartRules.shouldHide(BoggartRules.HIDE_AFTER_TICKS));
        assertTrue(BoggartRules.shouldHide(BoggartRules.HIDE_AFTER_TICKS + 500));
    }

    @Test
    void constantsMatchThePlan() {
        assertEquals(8.0, BoggartRules.REVEAL_RADIUS, 1e-9);
        assertEquals(6.0, BoggartRules.FEAR_RADIUS, 1e-9);
        assertEquals(80, BoggartRules.FEAR_DARKNESS_TICKS);
        assertEquals(200, BoggartRules.HIDE_AFTER_TICKS);
        assertEquals(60, BoggartRules.LAUGH_TICKS);
    }
}
