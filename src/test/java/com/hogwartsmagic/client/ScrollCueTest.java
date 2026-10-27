package com.hogwartsmagic.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrollCueTest {
    @Test
    void thumbSitsAtTheTopAndBottomOfTheTrack() {
        assertArrayEquals(new int[] {0, 50}, ScrollCue.thumb(0, 100, 100, 8));
        assertArrayEquals(new int[] {50, 50}, ScrollCue.thumb(100, 100, 100, 8));
    }

    @Test
    void thumbNeverShrinksBelowTheMinimum() {
        assertEquals(8, ScrollCue.thumb(0, 10000, 100, 8)[1]);
    }

    @Test
    void arrowsShowOnlyWhereThereIsMore() {
        assertFalse(ScrollCue.moreAbove(0));
        assertTrue(ScrollCue.moreAbove(1));
        assertTrue(ScrollCue.moreBelow(99.5f, 100));
        assertFalse(ScrollCue.moreBelow(100, 100));
    }
}
