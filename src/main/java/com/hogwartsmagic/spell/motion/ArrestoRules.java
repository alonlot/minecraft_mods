package com.hogwartsmagic.spell.motion;

/**
 * Arresto Momentum from Expert: physical projectiles near the impact freeze for a while, then drop (never a
 * spell beam, and nothing is ever sent back); Master reaches further.
 */
public final class ArrestoRules {
    public static final int FREEZE_TICKS = 40;
    public static final int MAX_FROZEN_PER_LEVEL = 64;
    /** Downward speed given to fireballs (no gravity of their own) when they drop. */
    public static final double DROP_SPEED = 0.6;
    private static final double[] RADIUS = {0.0, 0.0, 4.0, 6.0};

    private ArrestoRules() {
    }

    public static boolean freezes(int rank) {
        return RankIndex.of(rank) >= 2;
    }

    public static double freezeRadius(int rank) {
        return RADIUS[RankIndex.of(rank)];
    }

    public static boolean released(long until, long now) {
        return now >= until;
    }
}
