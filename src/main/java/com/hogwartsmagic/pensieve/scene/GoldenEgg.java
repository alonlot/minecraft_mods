package com.hogwartsmagic.pensieve.scene;

/**
 * The golden egg of the First Task as a memory actor ({@link SceneNames#GOLDEN_EGG_ACTOR}): its size, its shape and
 * the glint that flashes at its top. Pure: no Minecraft imports; {@code first_task_props.egg_box} and
 * {@code first_task_props.egg_glint} mirror the size and the glint (checked by GoldenEggTest against
 * {@code egg_cases.json}).
 *
 * <p>The actor's {@code path} is the egg's centre. It stands on its broad end, upright, and looks the same from every
 * side, so a look {@code yaw} shows nothing and a look {@code roll} is rejected (it is not in
 * {@link SceneNames#ROLL_TYPES}). At {@code scale} 1 it is {@link #LENGTH} tall and {@link #WIDTH} across, the size
 * of a real egg in a champion's hand; a champion at scale 0.85 holds one at scale 0.85 too.
 *
 * <p><b>The glint.</b> A star of light flashes at the top every 1.5 to 2.5 seconds ({@link #glint}). Time is cut into
 * {@link #SLOT} tick slots, and each slot flashes once, starting a stable pseudo-random {@link #JITTER} ticks or less
 * into it, so two flashes are always between {@code SLOT - JITTER} and {@code SLOT + JITTER} ticks apart (30 to 50: 1.5
 * to 2.5 s). The flash is a sine bump {@link #FLASH} ticks long. It is a pure function of the clock and the actor's
 * seed, so a late joiner and a replay see the same sparkle at the same moment.
 */
public final class GoldenEgg {
    private GoldenEgg() {
    }

    /** The egg's height and its widest width at scale 1, in blocks. */
    public static final double LENGTH = 0.36, WIDTH = 0.26;
    /** Ticks per glint slot, the latest a flash starts into its slot, and a flash's length (ticks). */
    public static final double SLOT = 40, JITTER = 10, FLASH = 8;
    /** The gap between two flashes, ticks: no shorter than 1.5 s, no longer than 2.5 s. */
    public static final double MIN_GAP = SLOT - JITTER, MAX_GAP = SLOT + JITTER;
    /** How the egg narrows toward its top (0 is an ellipsoid): the radius at height v is scaled by (1 - TAPER * v). */
    public static final double TAPER = 0.14;
    /** The glint star's width at scale 1 and at full flash, and how far above the top it sits (blocks). */
    public static final double STAR_SIZE = 0.34, STAR_LIFT = 0.02;

    /** The egg's bounds about its centre, {min x, min y, min z, max x, max y, max z}, at {@code scale}. */
    public static double[] box(float scale) {
        double r = WIDTH / 2 * scale, h = LENGTH / 2 * scale;
        return new double[]{-r, -h, -r, r, h, r};
    }

    public static boolean isEgg(String type) {
        return SceneNames.GOLDEN_EGG_ACTOR.equals(type);
    }

    // ---- The shape --------------------------------------------------------------------------------------------------

    private static final double PEAK = peak();

    private static double profile(double v) {
        return Math.sqrt(Math.max(0, 1 - v * v)) * (1 - TAPER * v);
    }

    private static double peak() {
        double best = 0;
        for (int i = 0; i <= 2000; i++) best = Math.max(best, profile(-1 + i / 1000.0));
        return best;
    }

    /**
     * The egg's radius at height v (-1 the bottom, +1 the top; blocks at scale 1): a mild egg shape, broad below,
     * whose widest point is exactly {@link #WIDTH} / 2.
     */
    public static double radiusAt(double v) {
        return WIDTH / 2 * profile(Math.max(-1, Math.min(1, v))) / PEAK;
    }

    // ---- The glint --------------------------------------------------------------------------------------------------

    /** The splitmix64 finaliser, as {@code Crowd.mix}: one long to a well-mixed long. */
    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A stable number in [0, 1) for (seed, slot). */
    static double unit(int seed, long slot) {
        return (mix(mix((long) seed) + slot) >>> 11) * 0x1.0p-53;
    }

    /** The scene time (ticks) at which slot k's flash starts. */
    public static double glintStart(int seed, long k) {
        return SLOT * k + JITTER * unit(seed, k);
    }

    /**
     * The glint's strength at scene time t (ticks): 0 outside a flash, rising to 1 mid-flash. {@code seed} is
     * {@link MemoryBall#seedFor} of the actor's id.
     */
    public static double glint(double t, int seed) {
        long k = (long) Math.floor(t / SLOT);
        double local = t - glintStart(seed, k);
        if (local <= 0 || local >= FLASH) return 0;
        return Math.sin(Math.PI * local / FLASH);
    }

    /** {@link #glint(double, int)} for an actor id. */
    public static double glint(double t, String id) {
        return glint(t, MemoryBall.seedFor(id));
    }

    /** The star's turn at full flash, degrees: it spins a quarter turn over the flash, a little each time. */
    public static double starTurn(double t, int seed) {
        long k = (long) Math.floor(t / SLOT);
        double local = Math.max(0, Math.min(FLASH, t - glintStart(seed, k)));
        return 90.0 * local / FLASH + 360.0 * unit(seed, k + 7919);
    }
}
