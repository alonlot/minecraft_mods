package com.hogwartsmagic.cosy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The butterbeer keg: a batch needs all three ingredients, then ages by the clock, never by a countdown. */
class KegRulesTest {

    @Test
    void agesExactlySixThousandTicks() {
        assertEquals(6000, KegRules.AGE_TICKS);
        assertFalse(KegRules.ready(0, 5999));
        assertTrue(KegRules.ready(0, 6000));
        assertTrue(KegRules.ready(100, 6100));
        assertFalse(KegRules.ready(100, 6099));
    }

    @Test
    void anUnstartedBatchIsNeverReady() {
        assertFalse(KegRules.ready(KegRules.NOT_STARTED, 1_000_000_000L));
    }

    @Test
    void aBatchNeedsAllThreeIngredients() {
        assertTrue(KegRules.complete(true, true, true));
        assertFalse(KegRules.complete(false, true, true));
        assertFalse(KegRules.complete(true, false, true));
        assertFalse(KegRules.complete(true, true, false));
        assertFalse(KegRules.complete(false, false, false));
    }

    @Test
    void aBatchMakesEightServings() {
        assertEquals(8, KegRules.SERVINGS);
    }

    @Test
    void fillStateFollowsTheBatch() {
        assertEquals(0, KegRules.fill(false, false, false, KegRules.NOT_STARTED, 0, 0));
        assertEquals(1, KegRules.fill(true, false, false, KegRules.NOT_STARTED, 0, 0));
        assertEquals(1, KegRules.fill(true, true, false, KegRules.NOT_STARTED, 0, 0));
        assertEquals(2, KegRules.fill(true, true, true, 0, 8, 100));
        assertEquals(3, KegRules.fill(true, true, true, 0, 8, 6000));
        assertEquals(0, KegRules.fill(true, true, true, 0, 0, 7000), "an empty keg is empty whatever else it holds");
    }
}
