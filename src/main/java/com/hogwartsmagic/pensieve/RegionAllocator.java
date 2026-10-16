package com.hogwartsmagic.pensieve;

import java.util.BitSet;

/**
 * Hands out a private slice of the memory dimension per playing session. Pure; slots are spaced 1024 blocks
 * apart on X, comfortably more than twice the 200-block set radius cap, so two sets can never touch.
 */
public final class RegionAllocator {
    public static final int SPACING = 1024;
    private final BitSet used = new BitSet();

    /** The lowest free slot. */
    public synchronized int acquire() {
        int slot = used.nextClearBit(0);
        used.set(slot);
        return slot;
    }

    public synchronized void release(int slot) {
        if (slot >= 0) used.clear(slot);
    }

    public static int[] originFor(int slot) {
        return new int[]{slot * SPACING, 96, 0};
    }
}
