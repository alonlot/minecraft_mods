package com.hogwartsmagic.spell.support;

/**
 * Ascendio's self-launch. {@link #apexHeight} simulates vanilla living-entity vertical motion (move,
 * then {@code v = (v - 0.08) * 0.98}) so {@link #launchVelocity} can hit a height in blocks exactly.
 * Pure numbers.
 */
public final class AscendioRules {
    public static final double GRAVITY = 0.08;
    public static final double DRAG = 0.98;
    public static final float SELF_CAST_PITCH = 60.0f;
    public static final int SAFE_FALL_TICKS = 100;
    public static final double DOUBLE_CAST_HEIGHT_FACTOR = 0.5;
    public static final double SURGE_SPEED = 1.2;
    public static final int SURGE_MAX_TICKS = 60;
    public static final double PUSH_RADIUS = 2.0;
    public static final double PUSH_STRENGTH = 1.0;
    public static final double PUSH_LIFT = 0.3;
    public static final int AIR_CHARGE_GRACE_TICKS = 5;

    private static final double[] HEIGHT = {6.0, 9.0, 12.0, 15.0};

    private AscendioRules() {
    }

    public static double heightBlocks(int rank) {
        return HEIGHT[RankIndex.of(rank)];
    }

    public static double apexHeight(double initialVelocity) {
        double height = 0.0;
        double v = initialVelocity;
        for (int tick = 0; tick < 1000 && v > 0.0; tick++) {
            height += v;
            v = (v - GRAVITY) * DRAG;
        }
        return height;
    }

    /** Smallest upward speed whose apex reaches {@code blocks}. */
    public static double launchVelocity(double blocks) {
        if (blocks <= 0.0) {
            return 0.0;
        }
        double low = 0.0;
        double high = 6.0;
        for (int i = 0; i < 60; i++) {
            double mid = (low + high) * 0.5;
            if (apexHeight(mid) < blocks) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return high;
    }

    public static boolean wantsSelfCast(float pitch, boolean crouching) {
        return crouching || pitch >= SELF_CAST_PITCH;
    }

    public static boolean doubleCasts(int rank) {
        return rank >= 3;
    }

    public static boolean surges(int rank) {
        return rank >= 4;
    }

    public static boolean pushesOnTakeoff(int rank) {
        return rank >= 4;
    }
}
