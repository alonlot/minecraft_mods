package com.hogwartsmagic.pensieve.capture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reconstructs the block cells at the start of a window from the current cells and the changes since. */
public final class BlockRewind {
    public record Change(long tick, int index, int oldState, int newState) {}

    private BlockRewind() {}

    /** Walks the (chronological) changes backwards, restoring each cell's old state. */
    public static int[] rewind(int[] current, List<Change> inWindowChronological) {
        int[] cells = current.clone();
        List<Change> ordered = forward(inWindowChronological);
        for (int i = ordered.size() - 1; i >= 0; i--) {
            Change c = ordered.get(i);
            cells[c.index()] = c.oldState();
        }
        return cells;
    }

    /** The same changes stable-sorted by tick, so the caller does not depend on the input order. */
    public static List<Change> forward(List<Change> changes) {
        List<Change> out = new ArrayList<>(changes);
        out.sort(Comparator.comparingLong(Change::tick));
        return out;
    }
}
