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
}
