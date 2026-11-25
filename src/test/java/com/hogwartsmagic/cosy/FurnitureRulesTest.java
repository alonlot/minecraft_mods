package com.hogwartsmagic.cosy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FurnitureRulesTest {
    @Test
    void sofaPartForEachNeighbourCase() {
        assertEquals(SofaPart.MIDDLE, FurnitureRules.sofaPart(true, true));
        // A neighbour on the left only: this is the right-hand end of the run, so the arm goes on the right.
        assertEquals(SofaPart.RIGHT, FurnitureRules.sofaPart(true, false));
        assertEquals(SofaPart.LEFT, FurnitureRules.sofaPart(false, true));
        assertEquals(SofaPart.SINGLE, FurnitureRules.sofaPart(false, false));
    }

    @Test
    void sofaPartNamesAreTheBlockstateValues() {
        assertEquals("single", SofaPart.SINGLE.getSerializedName());
        assertEquals("left", SofaPart.LEFT.getSerializedName());
        assertEquals("middle", SofaPart.MIDDLE.getSerializedName());
        assertEquals("right", SofaPart.RIGHT.getSerializedName());
    }

    @Test
    void rugBorderMaskHasOneBitPerBareSide() {
        assertEquals(0, FurnitureRules.rugBorderMask(true, true, true, true));
        assertEquals(15, FurnitureRules.rugBorderMask(false, false, false, false));
        // The argument is "there is a matching rug on that side"; the bit is set where there is NOT.
        assertEquals(1, FurnitureRules.rugBorderMask(false, true, true, true));
        assertEquals(2, FurnitureRules.rugBorderMask(true, false, true, true));
        assertEquals(4, FurnitureRules.rugBorderMask(true, true, false, true));
        assertEquals(8, FurnitureRules.rugBorderMask(true, true, true, false));
        assertEquals(10, FurnitureRules.rugBorderMask(true, false, true, false));
    }
}
