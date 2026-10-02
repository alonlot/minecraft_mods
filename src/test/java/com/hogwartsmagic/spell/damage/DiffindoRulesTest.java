package com.hogwartsmagic.spell.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffindoRulesTest {
    @Test
    void damageAndPierceScaleWithRank() {
        assertEquals(6f, DiffindoRules.damage(1), 1e-6);
        assertEquals(7f, DiffindoRules.damage(2), 1e-6);
        assertEquals(8f, DiffindoRules.damage(3), 1e-6);
        assertEquals(9f, DiffindoRules.damage(4), 1e-6);
        assertEquals(0.25, DiffindoRules.armorIgnore(1), 1e-9);
        assertEquals(0.40, DiffindoRules.armorIgnore(3), 1e-9);
        assertEquals(0.60, DiffindoRules.armorIgnore(4), 1e-9);
        assertEquals(6f, DiffindoRules.damage(0), 1e-6);
        assertEquals(9f, DiffindoRules.damage(9), 1e-6);
    }

    @Test
    void cutRadiusAndCleave() {
        assertEquals(0.0, DiffindoRules.cutRadius(1), 1e-9);
        assertEquals(1.0, DiffindoRules.cutRadius(2), 1e-9);
        assertEquals(2.0, DiffindoRules.cutRadius(4), 1e-9);
        assertFalse(DiffindoRules.cleaves(3));
        assertTrue(DiffindoRules.cleaves(4));
    }

    @Test
    void armourMathMatchesVanillaWithPartOfTheArmourIgnored() {
        assertEquals(6.0f, DiffindoRules.damageAfterArmor(6f, 0, 0, 0.25), 1e-4);
        assertEquals(1.92f, DiffindoRules.damageAfterArmor(6f, 20, 0, 0.0), 1e-4);
        assertEquals(3.12f, DiffindoRules.damageAfterArmor(6f, 20, 0, 0.25), 1e-4);
        assertEquals(7.74f, DiffindoRules.damageAfterArmor(9f, 20, 0, 0.6), 1e-4);
        assertEquals(3.0f, DiffindoRules.damageAfterArmor(10f, 20, 8, 0.0), 1e-4);
        assertEquals(6.0f, DiffindoRules.damageAfterArmor(6f, 20, 8, 1.0), 1e-4);
    }

    @Test
    void armourMathRejectsBadInput() {
        assertEquals(0f, DiffindoRules.damageAfterArmor(Float.NaN, 10, 0, 0.25), 1e-6);
        assertEquals(0f, DiffindoRules.damageAfterArmor(-3f, 10, 0, 0.25), 1e-6);
    }

    @Test
    void cleaveLaneIsBehindTheTargetAndNarrow() {
        assertTrue(DiffindoRules.inCleaveLane(1, 0, 2, 0.5));
        assertTrue(DiffindoRules.inCleaveLane(5, 0, 2, 0));
        assertFalse(DiffindoRules.inCleaveLane(1, 0, 3.5, 0));
        assertFalse(DiffindoRules.inCleaveLane(1, 0, -1, 0));
        assertFalse(DiffindoRules.inCleaveLane(1, 0, 2, 2));
        assertFalse(DiffindoRules.inCleaveLane(0, 0, 1, 0));
    }
}
