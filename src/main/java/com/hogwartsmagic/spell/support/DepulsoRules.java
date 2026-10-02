package com.hogwartsmagic.spell.support;

/** Depulso, the banishing charm: knockback and the wall slam. Pure numbers. */
public final class DepulsoRules {
    public static final int SLAM_WINDOW_TICKS = 10;
    public static final double VERTICAL_LIFT = 0.5;
    public static final double AREA_RADIUS = 2.0;
    public static final double DEFLECT_RADIUS = 1.5;
    public static final double WALL_PROBE = 0.35;
    public static final double MOB_CONTACT_INFLATE = 0.15;

    private static final double[] KNOCKBACK = {1.5, 1.8, 2.0, 2.2};
    private static final float[] SLAM = {3.0f, 5.0f, 5.0f, 8.0f};

    private DepulsoRules() {
    }

    public static double knockback(int rank) {
        return KNOCKBACK[RankIndex.of(rank)];
    }

    public static float slamDamage(int rank) {
        return SLAM[RankIndex.of(rank)];
    }

    public static boolean pushesArea(int rank) {
        return rank >= 3;
    }

    public static boolean deflectsProjectiles(int rank) {
        return rank >= 3;
    }

    public static boolean slamsIntoMobs(int rank) {
        return rank >= 4;
    }
}
