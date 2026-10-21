package com.hogwartsmagic.pensieve.scene;

/**
 * Whether a memory bird is in the air, read from its own path: a path moving faster than {@link #FLY_SPEED} flies,
 * one held still is perched. No scene key; the scene just holds the path where the bird sits.
 *
 * <p>Hysteresis: a perched bird takes off above {@link #FLY_SPEED} but a flying one only lands under the lower
 * {@link #LAND_SPEED}, so a decelerating touchdown or a slow hover does not flicker between the two. Pure (no
 * Minecraft classes); the caller keeps the previous state.
 */
public final class Airborne {
    /** Blocks per tick (0.5 blocks a second): a perched bird faster than this takes off. */
    public static final float FLY_SPEED = 0.025f;
    /** Blocks per tick (0.25 blocks a second): a flying bird slower than this has landed. */
    public static final float LAND_SPEED = 0.0125f;

    private Airborne() {}

    /** Path speed at t in blocks per tick, 3D, measured over the centred window [t - 1, t + 1]. Empty or one key: 0. */
    public static float speed(Track path, float t) {
        if (path == null || path.keys().size() < 2) return 0f;
        float[] a = path.sample(t - 1);
        float[] b = path.sample(t + 1);
        int n = Math.min(3, Math.min(a.length, b.length));
        double d = 0;
        for (int c = 0; c < n; c++) {
            double k = b[c] - a[c];
            d += k * k;
        }
        return (float) (Math.sqrt(d) / 2.0);
    }

    /** True while flying, given whether the bird was flying on the previous tick. */
    public static boolean airborne(Track path, float t, boolean wasAirborne) {
        float v = speed(path, t);
        return wasAirborne ? v >= LAND_SPEED : v > FLY_SPEED;
    }

    /** The stateless test (no hysteresis): faster than {@link #FLY_SPEED}. */
    public static boolean airborne(Track path, float t) {
        return speed(path, t) > FLY_SPEED;
    }
}
