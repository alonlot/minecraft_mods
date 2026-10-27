package com.hogwartsmagic.brewing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrewQualityTest {
    @Test
    void stirRhythm() {
        assertTrue(BrewQuality.preciseStir(false, 10));
        assertFalse(BrewQuality.preciseStir(false, 9));
        assertTrue(BrewQuality.preciseStir(false, 500));
        assertTrue(BrewQuality.preciseStir(true, 30));
        assertFalse(BrewQuality.preciseStir(true, 31));
        assertFalse(BrewQuality.preciseStir(true, 9));
    }

    @Test
    void emptyCountersAreStandard() {
        assertEquals(0.0, BrewQuality.score(0, 0, 0, 0), 1e-9);
        assertEquals(BrewQuality.STANDARD, BrewQuality.grade(BrewQuality.score(0, 0, 0, 0)));
    }

    @Test
    void scoresAverageThePartsPresent() {
        assertEquals(1.0, BrewQuality.score(0, 0, 3, 3), 1e-9);   // no rare ingredient: stirring alone
        assertEquals(0.5, BrewQuality.score(3, 0, 5, 5), 1e-9);
        assertEquals(0.625, BrewQuality.score(2, 1, 4, 3), 1e-9);
        assertEquals(0.4, BrewQuality.score(5, 2, 0, 0), 1e-9);
        assertEquals(1.0, BrewQuality.score(2, 9, 1, 7), 1e-9);   // clamped
    }

    @Test
    void gradeBoundaries() {
        assertEquals(BrewQuality.EXCEPTIONAL, BrewQuality.grade(0.9));
        assertEquals(BrewQuality.FINE, BrewQuality.grade(0.8999));
        assertEquals(BrewQuality.FINE, BrewQuality.grade(0.5));
        assertEquals(BrewQuality.STANDARD, BrewQuality.grade(0.4999));
        assertEquals(BrewQuality.STANDARD, BrewQuality.grade(Double.NaN));
        assertEquals(2, BrewQuality.clampGrade(7));
        assertEquals(0, BrewQuality.clampGrade(-1));
    }

    @Test
    void gradableIngredients() {
        assertTrue(BrewQuality.gradable(BrewRules.DITTANY));
        assertFalse(BrewQuality.gradable(BrewRules.HONEY_BOTTLE));
        assertFalse(BrewQuality.gradable(BrewRules.POLYJUICE_SAMPLE));
        assertFalse(BrewQuality.gradable(null));
        assertEquals("quality.hogwartsmagic.fine", BrewQuality.langKey(BrewQuality.FINE));
    }
}
