package com.hogwartsmagic.spell.disable;

/** Incarcerous: a tether. Bound targets can fight, but not leave the spot. */
public final class IncarcerousRules {
    private static final int[] MAIN_TICKS = {100, 110, 120, 140};
    private static final int[] SECONDARY_TICKS = {0, 70, 80, 140};
    private static final double[] SECONDARY_RADIUS = {0.0, 2.5, 3.5, 3.5};
    private static final double[] LEASH = {2.0, 2.0, 2.0, 1.0};
    /** Downward speed forced on a tethered flyer that is above its anchor (Expert and up). */
    public static final double PULL_DOWN_SPEED = 0.35;
    /** How far above its ground anchor a pulled-down target may be before it is forced down. */
    public static final double PULL_DOWN_VERTICAL_SLACK = 0.5;
    /** Master: how far to look for the other members of a bound group when sharing knockback. */
    public static final double GROUP_SEARCH_RADIUS = 12.0;

    private IncarcerousRules() {
    }

    public static int mainTicks(int rank) {
        return MAIN_TICKS[RankIndex.of(rank)];
    }

    public static int secondaryTicks(int rank) {
        return SECONDARY_TICKS[RankIndex.of(rank)];
    }

    public static double secondaryRadius(int rank) {
        return SECONDARY_RADIUS[RankIndex.of(rank)];
    }

    public static double leash(int rank) {
        return LEASH[RankIndex.of(rank)];
    }

    public static boolean pullsFlyersDown(int rank) {
        return RankIndex.of(rank) >= 2;
    }

    public static boolean bindsTogether(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /** Leash between two bound bodies: the rank leash measured edge to edge, not centre to centre. */
    public static double boundLeash(int rank, double mainWidth, double otherWidth) {
        return leash(rank) + (mainWidth + otherWidth) * 0.5;
    }
}
