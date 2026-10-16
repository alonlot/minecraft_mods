package com.hogwartsmagic.pensieve;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildOrderTest {
    @Test
    void centreFirstThenRings() {
        List<int[]> in = new ArrayList<>(List.of(new int[]{3, 0, 0}, new int[]{0, 0, 0}, new int[]{0, 0, 1}));
        List<int[]> out = BuildOrder.sortOutward(in, new int[]{0, 0, 0});
        assertEquals(0, out.get(0)[0] + out.get(0)[2]);
        assertEquals(1, out.get(1)[2]);
        assertEquals(3, out.get(2)[0]);
    }

    @Test
    void sameRingGoesLowestFirstAndIsStable() {
        List<int[]> in = List.of(new int[]{1, 5, 0}, new int[]{1, 2, 0}, new int[]{0, 5, 1});
        List<int[]> out = BuildOrder.sortOutward(in, new int[]{0, 0, 0});
        assertEquals(2, out.get(0)[1]);
        assertEquals(1, out.get(1)[0]);
        assertEquals(1, out.get(2)[2]);
    }
}
