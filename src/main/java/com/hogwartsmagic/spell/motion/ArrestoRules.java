package com.hogwartsmagic.spell.motion;

/**
 * Arresto Momentum from Expert: physical projectiles near the impact freeze (never a spell beam); at Master,
 * the caster's next cast sends the arrows, fireballs and venom they froze where they aim.
 */
public final class ArrestoRules {
    public static final int FREEZE_TICKS = 40;
    public static final int REDIRECT_WINDOW_TICKS = 40;
    public static final int MAX_FROZEN_PER_LEVEL = 64;
    /** Downward speed given to fireballs (no gravity of their own) when they drop. */
    public static final double DROP_SPEED = 0.6;
    /** A redirected projectile leaves at its frozen speed, kept within these bounds. */
    public static final double REDIRECT_MIN_SPEED = 1.0;
    public static final double REDIRECT_MAX_SPEED = 3.0;
    private static final double[] RADIUS = {0.0, 0.0, 4.0, 6.0};

    private ArrestoRules() {
    }

    public static boolean freezes(int rank) {
        return RankIndex.of(rank) >= 2;
    }

    public static double freezeRadius(int rank) {
        return RADIUS[RankIndex.of(rank)];
    }

    public static boolean redirects(int rank) {
        return RankIndex.of(rank) == 3;
    }

    public static boolean released(long until, long now) {
        return now >= until;
    }

    public static boolean inRedirectWindow(long until, long now) {
        return now <= until;
    }

    /** The frozen speed clamped to REDIRECT_MIN_SPEED..REDIRECT_MAX_SPEED; NaN counts as the minimum. */
    public static double redirectSpeed(double frozenSpeed) {
        if (!(frozenSpeed >= REDIRECT_MIN_SPEED)) {
            return REDIRECT_MIN_SPEED;
        }
        return Math.min(REDIRECT_MAX_SPEED, frozenSpeed);
    }
}
