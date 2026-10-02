package com.hogwartsmagic.spell.disable;

/** Levicorpus: the target hangs helpless. Master recasts drop it hard (Liberacorpus). */
public final class LevicorpusRules {
    private static final int[] HANG_TICKS = {60, 80, 100, 120};
    private static final float[] DAMAGE_TAKEN = {1.0f, 1.25f, 1.25f, 1.25f};
    public static final double HANG_HEIGHT = 1.5;
    /** Fraction of the gap to the anchor closed each tick. */
    public static final double HOLD_GAIN = 0.5;
    /** Further than this from the anchor and the target is put back at once. */
    public static final double SNAP_DISTANCE = 1.0;
    /** Fall damage for a 6-block fall: vanilla takes off 3 safe blocks. */
    public static final float LIBERACORPUS_FALL_DAMAGE = 3.0f;
    public static final double LIBERACORPUS_DROP_SPEED = -1.2;

    private LevicorpusRules() {
    }

    public static int hangTicks(int rank) {
        return HANG_TICKS[RankIndex.of(rank)];
    }

    public static float damageTakenMultiplier(int rank) {
        return DAMAGE_TAKEN[RankIndex.of(rank)];
    }

    public static boolean liberacorpus(int rank) {
        return RankIndex.of(rank) == 3;
    }
}
