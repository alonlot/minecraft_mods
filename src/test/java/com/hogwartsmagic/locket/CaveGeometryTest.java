package com.hogwartsmagic.locket;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaveGeometryTest {
    @Test void islandAndCave() {
        assertTrue(CaveGeometry.onIsland(5, 0, 3));
        assertFalse(CaveGeometry.onIsland(7, 0, 0));
        assertFalse(CaveGeometry.onIsland(0, 4, 0));
        assertTrue(CaveGeometry.inCave(30, -10, 0));
        assertFalse(CaveGeometry.inCave(0, 30, 0));
    }

    @Test void lakeExcludesAntechamberPoolAndAir() {
        assertTrue(CaveGeometry.inLake(0, -5, 0));
        assertTrue(CaveGeometry.inLake(23, -2, 0));   // far east shore water
        assertTrue(CaveGeometry.inLake(0, -2, -19));  // far north shore water
        assertFalse(CaveGeometry.inLake(0, -1, -31)); // antechamber entry pool
        assertFalse(CaveGeometry.inLake(0, 1, 0));    // standing above the water
        assertFalse(CaveGeometry.inLake(0, -12, 0));  // below the lake floor
        assertTrue(CaveGeometry.inCave(0, -1, -31));  // still "in the cave"
    }
}
