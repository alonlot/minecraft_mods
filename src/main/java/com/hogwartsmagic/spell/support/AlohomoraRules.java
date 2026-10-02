package com.hogwartsmagic.spell.support;

/** Alohomora: what it may open at each rank. Pure numbers. */
public final class AlohomoraRules {
    public static final int IRON_OPEN_TICKS = 100;
    public static final int MAX_AREA_OPENINGS = 12;
    public static final int WARD_SUPPRESS_TICKS = 100;

    private static final int[] AREA = {0, 0, 4, 4};

    private AlohomoraRules() {
    }

    public static boolean opensIron(int rank) {
        return rank >= 2;
    }

    public static int areaRadius(int rank) {
        return AREA[RankIndex.of(rank)];
    }

    public static boolean opensVaults(int rank) {
        return rank >= 4;
    }

    public static boolean suppressesWards(int rank) {
        return rank >= 4;
    }
}
