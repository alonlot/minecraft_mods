package com.hogwartsmagic.cosy;

/**
 * How a floating candle drifts: a slow bob and a slight sway, each a sum of two sines whose phase comes from the
 * block position and the candle, so no two candles move together. Pure maths, shared by the renderer (which
 * draws the candle there) and the wick particles (which must stay on the wick).
 */
public final class CandleBob {
    private CandleBob() {
    }

    /** The bob never exceeds this, in blocks (1.5 pixels). */
    public static final double MAX_OFFSET = 1.5 / 16.0;
    /** The sway never exceeds this, in degrees. */
    public static final float MAX_SWAY_DEG = 4.0f;

    private static final double TWO_PI = Math.PI * 2.0;
    /** Periods in ticks: about 3.1 s and 4.7 s for the bob, and two slower, unrelated ones for the sway. */
    private static final double BOB_A = 62.0;
    private static final double BOB_B = 94.0;
    private static final double SWAY_A = 137.0;
    private static final double SWAY_B = 81.0;

    public static double offsetY(long gameTime, float partial, int candle, long seed) {
        double t = gameTime + (double) partial;
        double a = Math.sin(TWO_PI * t / BOB_A + phase(seed, candle, 0));
        double b = Math.sin(TWO_PI * t / BOB_B + phase(seed, candle, 1));
        return MAX_OFFSET * (0.6 * a + 0.4 * b);
    }

    public static float swayDeg(long gameTime, float partial, int candle, long seed) {
        double t = gameTime + (double) partial;
        double a = Math.sin(TWO_PI * t / SWAY_A + phase(seed, candle, 2));
        double b = Math.sin(TWO_PI * t / SWAY_B + phase(seed, candle, 3));
        return (float) (MAX_SWAY_DEG * (0.55 * a + 0.45 * b));
    }

    /**
     * How far a point {@code lever} blocks above the candle pivot is thrown sideways by the sway. The renderer
     * rolls the candle about Z by the same angle (counter-clockwise for a positive angle, so a point above the
     * pivot moves toward -x).
     */
    public static double swayShift(float swayDeg, double lever) {
        return -lever * Math.sin(Math.toRadians(swayDeg));
    }

    /** A phase in [0, 2 pi) from a splitmix64 hash of the position, candle and which sine it is for. */
    private static double phase(long seed, int candle, int which) {
        long z = seed + 0x9E3779B97F4A7C15L * (candle * 4L + which + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return ((z >>> 11) * 0x1.0p-53) * TWO_PI;
    }
}
