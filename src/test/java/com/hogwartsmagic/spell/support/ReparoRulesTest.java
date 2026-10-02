package com.hogwartsmagic.spell.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReparoRulesTest {
    @Test
    void blocksAndRadiusPerRank() {
        assertEquals(4, ReparoRules.maxBlocks(1));
        assertEquals(10, ReparoRules.maxBlocks(2));
        assertEquals(24, ReparoRules.maxBlocks(3));
        assertEquals(48, ReparoRules.maxBlocks(4));
        assertEquals(6.0, ReparoRules.radius(1), 1e-9);
        assertEquals(10.0, ReparoRules.radius(2), 1e-9);
        assertEquals(14.0, ReparoRules.radius(3), 1e-9);
        assertEquals(20.0, ReparoRules.radius(4), 1e-9);
    }

    @Test
    void itemsFromExpert() {
        assertFalse(ReparoRules.repairsItems(2));
        assertTrue(ReparoRules.repairsItems(3));
    }

    @Test
    void repairIsATenthRoundedUp() {
        assertEquals(6, ReparoRules.itemRepairAmount(59));
        assertEquals(25, ReparoRules.itemRepairAmount(250));
        assertEquals(157, ReparoRules.itemRepairAmount(1561));
        assertEquals(1, ReparoRules.itemRepairAmount(1));
        assertEquals(0, ReparoRules.itemRepairAmount(0));
    }

    @Test
    void extraManaScalesWithDurabilityAndIsCapped() {
        assertEquals(0, ReparoRules.itemExtraMana(33));
        assertEquals(1, ReparoRules.itemExtraMana(59));
        assertEquals(3, ReparoRules.itemExtraMana(250));
        assertEquals(5, ReparoRules.itemExtraMana(528));
        assertEquals(16, ReparoRules.itemExtraMana(1561));
        assertEquals(20, ReparoRules.itemExtraMana(2031));
        assertEquals(20, ReparoRules.itemExtraMana(5000));
    }
}
