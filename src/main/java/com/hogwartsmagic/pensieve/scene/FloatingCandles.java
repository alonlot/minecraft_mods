package com.hogwartsmagic.pensieve.scene;

/**
 * Pure maths for the {@code floating_candles} effect: the Great Hall's hundreds of floating candles, each bobbing slowly
 * on its own rhythm with a flickering flame. Positions are offsets from the effect's centre inside its half-box
 * {@code ±hx, ±hy, ±hz}; a candle's position is the bottom of its wax. Deterministic from a seed and the scene time, so
 * every viewer sees the same candles in the same places. The drawing is {@code client/pensieve/FloatingCandlesRenderer}.
 */
public final class FloatingCandles {
    /** Candles when the scene gives no {@code count}, and the most it may ask for. */
    public static final int DEFAULT_COUNT = 400, MAX_COUNT = 1500;
    /** How far a candle bobs up and down, and how far it drifts sideways, in blocks. */
    public static final float BOB = 0.15f, DRIFT = 0.06f;
    /** Bob period range, in ticks (3 to 6 s). */
    public static final float BOB_MIN_TICKS = 60f, BOB_MAX_TICKS = 120f;
    /** Wax height range and width, in blocks. */
    public static final float MIN_HEIGHT = 0.5f, MAX_HEIGHT = 0.8f, WIDTH = 0.12f;

    /** Indices into a candle's array. */
    public static final int X = 0, Y = 1, Z = 2, HEIGHT = 3, PERIOD = 4, PHASE = 5, DRIFT_PHASE = 6, SEED = 7;

    private FloatingCandles() {
    }

    /**
     * {@code count} candles, each {x, y, z, height, bobPeriod, bobPhase, driftPhase, seed} (see the index constants).
     * The rest positions are kept in from the box's faces by the bob and drift, so a bobbing candle never leaves it.
     */
    public static float[][] candles(long seed, int count, float hx, float hy, float hz) {
        float[][] out = new float[Math.max(0, Math.min(MAX_COUNT, count))][];
        float ix = Math.max(0f, hx - DRIFT), iy = Math.max(0f, hy - BOB), iz = Math.max(0f, hz - DRIFT);
        long s = seed * 0x9E3779B97F4A7C15L + 0x4CF5AD432745937FL;
        for (int i = 0; i < out.length; i++) {
            s = mix(s + i);
            float x = (unit(s) * 2f - 1f) * ix;
            s = mix(s);
            float y = (unit(s) * 2f - 1f) * iy;
            s = mix(s);
            float z = (unit(s) * 2f - 1f) * iz;
            s = mix(s);
            float height = MIN_HEIGHT + (MAX_HEIGHT - MIN_HEIGHT) * unit(s);
            s = mix(s);
            float period = BOB_MIN_TICKS + (BOB_MAX_TICKS - BOB_MIN_TICKS) * unit(s);
            s = mix(s);
            float phase = 6.2831855f * unit(s);
            s = mix(s);
            float drift = 6.2831855f * unit(s);
            s = mix(s);
            out[i] = new float[]{x, y, z, height, period, phase, drift, (float) (s >>> 41)};
        }
        return out;
    }

    /** The candle's bob at scene time {@code t}: up to ±{@link #BOB} blocks, on its own period. */
    public static float bob(float[] c, float t) {
        return BOB * (float) Math.sin(6.2831855f * t / c[PERIOD] + c[PHASE]);
    }

    /** The candle's position at scene time {@code t}: {x, y, z}, its rest position plus bob and a slow sideways drift. */
    public static float[] position(float[] c, float t) {
        float w = t / (c[PERIOD] * 2.7f) * 6.2831855f + c[DRIFT_PHASE];
        float dx = DRIFT * (float) Math.sin(w);
        float dz = DRIFT * (float) Math.cos(w * 0.83f + 1.3f);
        return new float[]{c[X] + dx, c[Y] + bob(c, t), c[Z] + dz};
    }

    /**
     * Candle {@code i}'s flame brightness at {@code t}, 0.75 to 1: a fast, irregular flicker (three sines out of step
     * with each other and with every other candle).
     */
    public static float flicker(float t, int i) {
        long h = mix(i * 0xD1B54A32D192ED03L + 0x8CB92BA72F3D8DD7L);
        float p = 6.2831855f * unit(h);
        float w = 0.5f * (float) Math.sin(t * 0.83f + p) + 0.3f * (float) Math.sin(t * 1.91f + p * 2.1f)
                + 0.2f * (float) Math.sin(t * 3.7f + p * 3.3f);
        return 0.75f + 0.25f * (0.5f + 0.5f * w);
    }

    /** Candle {@code i}'s flame lean at {@code t}, -1 to 1 (the renderer scales it to a few degrees). */
    public static float sway(float t, int i) {
        long h = mix(i * 0x94D049BB133111EBL + 0x2545F4914F6CDD1DL);
        float p = 6.2831855f * unit(h);
        return 0.6f * (float) Math.sin(t * 0.21f + p) + 0.4f * (float) Math.sin(t * 0.57f + p * 1.7f);
    }

    /** SplitMix64 finaliser. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** [0, 1) from the top 24 bits. */
    private static float unit(long h) {
        return (h >>> 40) / (float) (1 << 24);
    }
}
