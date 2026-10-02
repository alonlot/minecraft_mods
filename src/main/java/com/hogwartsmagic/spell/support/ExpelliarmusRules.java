package com.hogwartsmagic.spell.support;

/** Expelliarmus: silenced ("disarmed") time, knockback and the Master allegiance rule. Pure numbers. */
public final class ExpelliarmusRules {
    private static final int[] SILENCE = {40, 60, 60, 80};
    private static final double[] KNOCKBACK = {0.5, 0.8, 0.8, 0.8};

    private ExpelliarmusRules() {
    }

    public static int silenceTicks(int rank) {
        return SILENCE[RankIndex.of(rank)];
    }

    public static double knockback(int rank) {
        return KNOCKBACK[RankIndex.of(rank)];
    }

    public static boolean winsElderWand(int rank) {
        return rank >= 4;
    }
}
