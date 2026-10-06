package com.hogwartsmagic.locket;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CaveSearchTest {
    @Test void picksNearestSkippingLooted() {
        List<long[]> c = List.of(new long[]{10, 0}, new long[]{2, 2}, new long[]{-5, 0});
        assertArrayEquals(new long[]{2, 2}, CaveSearch.nearestUnlooted(0, 0, c, Set.of()).orElseThrow());
        Set<Long> looted = Set.of(CaveSearch.key(2, 2));
        assertArrayEquals(new long[]{-5, 0}, CaveSearch.nearestUnlooted(0, 0, c, looted).orElseThrow());
        assertTrue(CaveSearch.nearestUnlooted(0, 0, List.of(new long[]{2, 2}), looted).isEmpty());
    }
    @Test void keyRoundTripsNegatives() {
        assertNotEquals(CaveSearch.key(-1, 0), CaveSearch.key(0, -1));
    }
}
