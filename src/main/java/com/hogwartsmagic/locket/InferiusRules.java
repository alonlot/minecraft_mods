package com.hogwartsmagic.locket;

/** Numbers for the Inferius. Pure: no Minecraft imports. */
public final class InferiusRules {
    public static final int MAX_HEALTH = 24;
    public static final int GRAB_TICKS = 40;
    public static final int GRAB_COOLDOWN = 100;
    public static final float NON_FIRE_FACTOR = 0.3f;
    public static final float BURNING_FACTOR = 2.0f;
    public static final int FEAR_RADIUS = 6;
    public static final int BLOCK_FEAR_SCAN = 4;
    public static final int IGNITE_ON_FIRE_HIT_SECONDS = 8;
    public static final double WATER_PULL_PER_TICK = 0.05;
    public static final int IDLE_DESPAWN_TICKS = 2400;
    /** The grab on land: a weaker drag than in water, toward the nearest lake water. */
    public static final double LAND_PULL_PER_TICK = 0.02;
    public static final float LATCH_DAMAGE = 1f;
    public static final int LATCH_DAMAGE_INTERVAL = 40;
    /** A swimming Inferius this close (blocks) to a moving skiff with a rider can latch on. */
    public static final double LATCH_RANGE = 2.0;
    /** Ambush Inferi rise this many blocks (at most) outside the island shore. */
    public static final int SHORE_SPAWN_BAND = 3;

    private InferiusRules() {}

    public static float scaleDamage(float amount, boolean fireSource, boolean burning) {
        float scaled = fireSource ? amount : amount * NON_FIRE_FACTOR;
        return burning ? scaled * BURNING_FACTOR : scaled;
    }

    public static boolean isGrabReady(long now, long lastGrab) {
        return now - lastGrab >= GRAB_COOLDOWN;
    }

    /** True when a cave-bound Inferius has strayed farther from the basin than the nearest arch: turn back. */
    public static boolean pastArch(double distFromBasin, double archDist) {
        return distFromBasin > archDist;
    }

    /** A latched Inferius lets go when the rider is gone, it was hit, or it is burning. */
    public static boolean latchReleasedWhenRiderGone(boolean riderPresent, boolean hit, boolean burning) {
        return !riderPresent || hit || burning;
    }

    /**
     * True when a point is on the outside of the arch plane: within 3 blocks of the arch marker's height and more than
     * half a block past it along the seal's outward direction (fx, fz). The offsets are point minus arch marker centre.
     */
    public static boolean pastArchPlane(double dx, double dy, double dz, int fx, int fz) {
        return Math.abs(dy) <= 3.0 && dx * fx + dz * fz > 0.5;
    }
}
