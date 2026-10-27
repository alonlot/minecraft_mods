package com.hogwartsmagic.pensieve.capture;

import com.hogwartsmagic.pensieve.capture.BlockRewind.Change;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockRewindTest {
    private static final int AIR = 0;
    private static final int STONE = 1;
    private static final int DOOR_CLOSED = 2;
    private static final int DOOR_OPEN = 3;

    @Test
    void doorOpenedAndShut() {
        List<Change> changes = List.of(new Change(10, 0, DOOR_CLOSED, DOOR_OPEN), new Change(30, 0, DOOR_OPEN, DOOR_CLOSED));
        assertArrayEquals(new int[] {DOOR_CLOSED}, BlockRewind.rewind(new int[] {DOOR_CLOSED}, changes));
        assertEquals(changes, BlockRewind.forward(changes));
    }

    @Test
    void placedThenBroken() {
        List<Change> changes = List.of(new Change(5, 0, AIR, STONE), new Change(9, 0, STONE, AIR));
        assertArrayEquals(new int[] {AIR}, BlockRewind.rewind(new int[] {AIR}, changes));
    }

    @Test
    void brokenOnly() {
        int[] current = {AIR};
        assertArrayEquals(new int[] {STONE}, BlockRewind.rewind(current, List.of(new Change(5, 0, STONE, AIR))));
        assertArrayEquals(new int[] {AIR}, current);
    }
}
