package com.hogwartsmagic.spell.support;

/** Apparition: reach, focus time, splinch risk, and how the destination map scales. Pure numbers. */
public final class ApparitionRules {
    public static final double RANGE_SLACK = 4.0;
    public static final int MAX_KNOWN_FIREPLACES = 32;
    public static final int MAX_LISTED_FIREPLACES = 8;

    private static final int[] RANGE = {48, 80, 128, 200};
    private static final int[] CHANNEL = {80, 60, 40, 20};
    private static final double[] SPLINCH_FACTOR = {1.0, 0.5, 0.2, 0.0};

    private ApparitionRules() {
    }

    public static int range(int rank) {
        return RANGE[RankIndex.of(rank)];
    }

    public static int channelTicks(int rank) {
        return CHANNEL[RankIndex.of(rank)];
    }

    /** The Novice chance comes from config; Adept halves it, Expert takes a fifth, Master never splinches. */
    public static double splinchChance(int rank, double baseChance, boolean enabled) {
        if (!enabled) {
            return 0.0;
        }
        double base = Math.max(0.0, Math.min(1.0, baseChance));
        return base * SPLINCH_FACTOR[RankIndex.of(rank)];
    }

    public static boolean jumpsToFireplaces(int rank) {
        return rank >= 4;
    }

    /** Blocks per map tile so {@code span} blocks fit in {@code maxPx} pixels at {@code tilePx} per tile. */
    public static int mapStep(int span, int tilePx, int maxPx) {
        if (span <= 0 || tilePx <= 0) {
            return 1;
        }
        if (maxPx <= 0) {
            return span;
        }
        return Math.max(1, (int) Math.ceil(span * tilePx / (double) maxPx));
    }

    /** Block offset (0..span-1) a tile stands for: its middle block, never past the span. */
    public static int tileCenterOffset(int tile, int step, int span) {
        return Math.min(span - 1, tile * step + step / 2);
    }
}
