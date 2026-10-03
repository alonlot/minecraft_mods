package com.hogwartsmagic.spell.support.lock;

/** Alohomora and Revelio on piston doors: how much lock each rank can read. Pure numbers. */
public final class LockRules {
    /** Pistons this far (a cube) from the struck block count as the door. */
    public static final int DOOR_RADIUS = 6;
    public static final int MAX_DOOR_PISTONS = 32;
    /** Wait after flipping levers before the first press. */
    public static final int LEVER_SETTLE_TICKS = 20;
    /** Between two presses: longer than any button, plate or sculk sensor stays on. */
    public static final int PRESS_GAP_TICKS = 40;
    /** A forced source reads 15 for this long. */
    public static final int FORCE_TICKS = 60;
    /** Wait after starting a forced source before the next action. */
    public static final int FORCE_GAP_TICKS = 80;
    public static final int MAX_FORCED_SOURCES = 8;
    /** Frames and containers Revelio may solve for in one lock. */
    public static final int MAX_ITEM_KEYS = 3;
    public static final int HINT_TICKS = 300;
    public static final int SOLVE_TIMEOUT_TICKS = 200;

    private static final int[] TRACE_BLOCKS = {0, 0, 512, 8192};
    private static final int[] MAX_INPUTS = {0, 0, 4, 20};
    private static final int[] MAX_PRESSES = {0, 0, 1, 4};
    private static final long[] BUDGET = {0L, 0L, 2_000_000L, 200_000_000L};

    private LockRules() {
    }

    public static boolean readsLocks(int rank) {
        return rank >= 3;
    }

    public static boolean forcesSources(int rank) {
        return rank >= 4;
    }

    public static boolean readsItemKeys(int rank) {
        return rank >= 4;
    }

    public static int traceBlocks(int rank) {
        return TRACE_BLOCKS[index(rank)];
    }

    public static int maxInputs(int rank) {
        return MAX_INPUTS[index(rank)];
    }

    public static int maxPresses(int rank) {
        return MAX_PRESSES[index(rank)];
    }

    /** Node updates the solver may spend before it settles for the best it has found. */
    public static long budget(int rank) {
        return BUDGET[index(rank)];
    }

    /** The comparator signal of a container holding this many 64-stacking items, as vanilla computes it. */
    public static int containerSignal(int items, int slots) {
        if (items <= 0 || slots <= 0) {
            return 0;
        }
        return Math.min(15, items * 14 / (64 * slots) + 1);
    }

    /** {fewest, most} 64-stacking items that give exactly this signal. */
    public static int[] itemsForSignal(int signal, int slots) {
        int capacity = 64 * slots;
        if (signal <= 0 || slots <= 0) {
            return new int[] {0, 0};
        }
        int min = signal == 1 ? 1 : ceilDiv((signal - 1) * capacity, 14);
        int max = signal >= 15 ? capacity : Math.min(capacity, ceilDiv(signal * capacity, 14) - 1);
        return new int[] {min, max};
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    private static int index(int rank) {
        return Math.max(1, Math.min(4, rank)) - 1;
    }
}
