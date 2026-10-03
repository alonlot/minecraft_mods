package com.hogwartsmagic.spell.mind;

/** Confundo: friendly fire. The mob attacks the nearest other creature; at Master the confusion spreads. */
public final class ConfundoRules {
    private static final int[] CONFUSE_TICKS = {100, 140, 180, 240};
    public static final int CONTAGION_TICKS = 60;
    public static final int RETARGET_INTERVAL = 20;
    public static final double SEARCH_RADIUS = 12.0;
    /** Amplifier of the confunded marker: a plain confusion, or a Master one that spreads on hit. */
    public static final int AMP_PLAIN = 0;
    public static final int AMP_CONTAGIOUS = 1;
    private static final int PLAYER_NAUSEA_BONUS = 60;

    private ConfundoRules() {
    }

    public static int confuseTicks(int rank) {
        return CONFUSE_TICKS[RankIndex.of(rank)];
    }

    public static boolean contagious(int rank) {
        return RankIndex.of(rank) == 3;
    }

    public static int amplifierFor(boolean contagious) {
        return contagious ? AMP_CONTAGIOUS : AMP_PLAIN;
    }

    /** Nausea on a player outlasts the mob confusion by three seconds, so it reads. */
    public static int playerNauseaTicks(int rank) {
        return confuseTicks(rank) + PLAYER_NAUSEA_BONUS;
    }

    public static int playerSlownessTicks(int rank) {
        return confuseTicks(rank) / 2;
    }

    /** Only a Master confusion spreads, never onto a boss, never onto a mob that is already confused. */
    public static boolean spreads(boolean sourceContagious, boolean victimBoss, boolean victimAlreadyConfused) {
        return sourceContagious && !victimBoss && !victimAlreadyConfused;
    }
}
