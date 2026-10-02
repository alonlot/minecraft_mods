package com.hogwartsmagic.spell.support;

/** Accio: what it can reel in and how far it reaches for items. Pure numbers. */
public final class AccioRules {
    public static final int MAX_ITEMS = 16;
    public static final int ITEM_PULL_TICKS = 60;
    public static final double ARRIVE_DISTANCE = 1.2;

    private static final float[] MAX_HEALTH = {20.0f, 40.0f, 80.0f, Float.MAX_VALUE};
    private static final double[] ITEM_RADIUS = {1.5, 3.0, 4.0, 5.0};

    private AccioRules() {
    }

    public static float maxPullHealth(int rank) {
        return MAX_HEALTH[RankIndex.of(rank)];
    }

    public static boolean canReel(int rank, float maxHealth, boolean boss) {
        return !boss && maxHealth <= maxPullHealth(rank);
    }

    public static double itemRadius(int rank) {
        return ITEM_RADIUS[RankIndex.of(rank)];
    }

    public static boolean pullsContainers(int rank) {
        return rank >= 3;
    }

    public static boolean pullsPlayerItem(int rank) {
        return rank >= 4;
    }

    /** Blocks per tick toward the caster: faster from far away, capped so items don't overshoot. */
    public static double itemPullSpeed(double distance) {
        return Math.min(1.2, 0.3 + 0.1 * Math.max(0.0, distance));
    }
}
