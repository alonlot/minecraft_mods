package com.hogwartsmagic.pensieve;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionAllocatorTest {
    @Test
    void reusesLowestFreedSlot() {
        RegionAllocator a = new RegionAllocator();
        assertEquals(0, a.acquire());
        assertEquals(1, a.acquire());
        assertEquals(2, a.acquire());
        a.release(1);
        assertEquals(1, a.acquire());
        assertEquals(3, a.acquire());
    }

    @Test
    void regionsNeverOverlap() {
        assertTrue(RegionAllocator.originFor(1)[0] - RegionAllocator.originFor(0)[0] >= 1024);
        assertEquals(96, RegionAllocator.originFor(5)[1]);
    }
}
