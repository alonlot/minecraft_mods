package com.hogwartsmagic.spell.support;

/** Revelio: how far it looks, and the Master reveal of disguises and cloaks. Pure numbers. */
public final class RevelioRules {
    public static final double MASTER_RADIUS = 20.0;
    public static final int REVEAL_TICKS = 100;

    private static final int[] ORE = {12, 14, 16, 20};
    private static final int[] CHEST = {8, 10, 12, 16};

    private RevelioRules() {
    }

    public static int oreRadius(int rank) {
        return ORE[RankIndex.of(rank)];
    }

    public static int chestRadius(int rank) {
        return CHEST[RankIndex.of(rank)];
    }

    public static boolean revealsDisguises(int rank) {
        return rank >= 4;
    }
}
