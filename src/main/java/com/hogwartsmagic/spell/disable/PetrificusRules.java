package com.hogwartsmagic.spell.disable;

/** Petrificus Totalus: a long lock that breaks on damage. Master makes the breaking hit a shatter. */
public final class PetrificusRules {
    private static final int[] DURATION_TICKS = {100, 140, 180, 240};
    /** A hit must deal at least this much (before armour) to break the lock; 0 falls back to MIN_BREAK_DAMAGE. */
    private static final float[] BREAK_THRESHOLD = {0.0f, 4.0f, 4.0f, 4.0f};
    public static final float MASTER_SHATTER = 1.5f;

    private PetrificusRules() {
    }

    public static int durationTicks(int rank) {
        return DURATION_TICKS[RankIndex.of(rank)];
    }

    public static float breakThreshold(int rank) {
        return BREAK_THRESHOLD[RankIndex.of(rank)];
    }

    /** The beam itself chips for 0.1; a hit must be at least this much to break even the Novice lock. */
    public static final float MIN_BREAK_DAMAGE = 0.5f;

    /** NaN and non-positive damage never break it. */
    public static boolean breaks(int rank, float amount) {
        return amount > 0.0f && amount >= Math.max(breakThreshold(rank), MIN_BREAK_DAMAGE);
    }

    public static float shatterMultiplier(int rank) {
        return RankIndex.of(rank) == 3 ? MASTER_SHATTER : 1.0f;
    }
}
