package com.hogwartsmagic.spell.damage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlastShapeTest {
    @Test
    void sphereSizesMatchTheRadii() {
        assertEquals(1, BlastShape.offsets(0.0).size());
        assertEquals(7, BlastShape.offsets(1.0).size());
        assertEquals(19, BlastShape.offsets(1.5).size());
        assertEquals(33, BlastShape.offsets(2.0).size());
        assertEquals(81, BlastShape.offsets(2.5).size());
        assertEquals(123, BlastShape.offsets(3.0).size());
        assertEquals(179, BlastShape.offsets(3.5).size());
    }

    @Test
    void centreComesFirstAndDistanceNeverDecreases() {
        List<int[]> offsets = BlastShape.offsets(3.5);
        assertArrayEquals(new int[]{0, 0, 0}, offsets.get(0));
        int previous = 0;
        for (int[] o : offsets) {
            int d2 = o[0] * o[0] + o[1] * o[1] + o[2] * o[2];
            assertTrue(d2 >= previous);
            previous = d2;
        }
    }

    @Test
    void badRadiiAreClamped() {
        assertEquals(1, BlastShape.offsets(Double.NaN).size());
        assertEquals(1, BlastShape.offsets(-2.0).size());
        assertEquals(BlastShape.offsets(BlastShape.MAX_RADIUS).size(), BlastShape.offsets(50.0).size());
    }
}
