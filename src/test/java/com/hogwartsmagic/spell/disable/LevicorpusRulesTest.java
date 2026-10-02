package com.hogwartsmagic.spell.disable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevicorpusRulesTest {
    @Test
    void hangsThreeToSixSeconds() {
        assertEquals(60, LevicorpusRules.hangTicks(1));
        assertEquals(80, LevicorpusRules.hangTicks(2));
        assertEquals(100, LevicorpusRules.hangTicks(3));
        assertEquals(120, LevicorpusRules.hangTicks(4));
    }

    @Test
    void damageBonusFromAdept() {
        assertEquals(1.0f, LevicorpusRules.damageTakenMultiplier(1), 1e-6);
        assertEquals(1.25f, LevicorpusRules.damageTakenMultiplier(2), 1e-6);
        assertEquals(1.25f, LevicorpusRules.damageTakenMultiplier(4), 1e-6);
    }

    @Test
    void onlyMasterCanDrop() {
        assertFalse(LevicorpusRules.liberacorpus(3));
        assertTrue(LevicorpusRules.liberacorpus(4));
    }

    @Test
    void dropDealsSixBlocksOfFallDamage() {
        // Vanilla fall damage is distance minus 3 safe blocks.
        assertEquals(6.0f - 3.0f, LevicorpusRules.LIBERACORPUS_FALL_DAMAGE, 1e-6);
    }
}
