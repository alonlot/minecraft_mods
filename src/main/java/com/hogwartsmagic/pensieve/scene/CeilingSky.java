package com.hogwartsmagic.pensieve.scene;

/**
 * Pure maths for the {@code enchanted_ceiling} effect: the Great Hall roof dissolving into a night sky. Everything is
 * a function of the scene time and a seed, so every viewer (late joiners included) sees the same stars twinkling the
 * same way. The drawing is {@code client/pensieve/EnchantedCeiling}.
 */
public final class CeilingSky {
    /** A ceiling with no {@code duration} reveals over this many ticks (3 s). */
    public static final int DEFAULT_REVEAL_TICKS = 60;
    /** Cloud texture drift, in UV units a tick. */
    public static final float CLOUD_DRIFT = 0.0006f;
    /** The sky plane sits this far below the given height, so it never z-fights the roof's underside. */
    public static final float ROOF_GAP = 0.04f;
    /** Width of the soft edge of the dissolve front, as a fraction of the half-size. */
    private static final float DISSOLVE_SOFT = 0.35f;
    /** One shooting star in each window of this many ticks, at most... */
    private static final int SHOOT_WINDOW = 220;
    /** ...crossing the sky in this many ticks. */
    private static final int SHOOT_TICKS = 16;
    /** The share of stars on the Milky Way band, and the band's half-width in normalised units. */
    private static final float BAND_SHARE = 0.32f;
    private static final float BAND_WIDTH = 0.42f;

    private CeilingSky() {
    }

    /** How far the reveal is, 0 to 1 (ease-out), {@code elapsed} ticks after it began. */
    public static float revealAt(int elapsed, int duration) {
        return revealAt((float) elapsed, duration);
    }

    public static float revealAt(float elapsed, int duration) {
        if (elapsed <= 0) return 0f;
        if (duration <= 0 || elapsed >= duration) return 1f;
        float k = elapsed / duration;
        float inv = 1f - k;
        return 1f - inv * inv * inv;
    }

    /**
     * How revealed the sky is at the plane point {@code (u, v)} (each -1..1 across the rectangle), 0 to 1: the roof
     * dissolves from the middle outward, with a soft, slightly ragged front. 0 everywhere at reveal 0, 1 everywhere at 1.
     */
    public static float dissolve(float reveal, float u, float v) {
        if (reveal <= 0f) return 0f;
        if (reveal >= 1f) return 1f;
        float d = (float) Math.sqrt((u * u + v * v) * 0.5f);          // 0 centre .. 1 corner
        float ragged = 0.08f * (float) (Math.sin(u * 7.3f + v * 2.1f) * Math.sin(v * 6.1f - u * 3.7f));
        float front = reveal * (1f + DISSOLVE_SOFT + 0.08f) - DISSOLVE_SOFT;
        float k = (front - (d + ragged) + DISSOLVE_SOFT) / DISSOLVE_SOFT;
        k = Math.max(0f, Math.min(1f, k));
        return k * k * (3f - 2f * k);
    }

    /**
     * {@code count} stars, each {x, z, size}: x and z offsets from the centre inside {@code ±hx, ±hz}, and a size in
     * (0, 1] heavily weighted toward small (most stars are faint pinpricks, a few are bright). About a third crowd
     * along the seed's Milky Way band ({@link #bandAngle}), the rest are scattered evenly.
     */
    public static float[][] stars(long seed, float hx, float hz, int count) {
        float[][] out = new float[Math.max(0, count)][];
        double angle = bandAngle(seed);
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
        long s = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
        for (int i = 0; i < out.length; i++) {
            s = mix(s + i);
            float u = unit(s) * 2f - 1f;
            s = mix(s);
            float v = unit(s) * 2f - 1f;
            s = mix(s);
            if (unit(s) < BAND_SHARE) {
                // Along the band: u runs the length of it, v is a narrow gaussian-ish spread across it.
                s = mix(s);
                float across = (unit(s) + unit(mix(s + 1)) + unit(mix(s + 2)) - 1.5f) * BAND_WIDTH;
                float bu = u * ca - across * sa;
                float bv = u * sa + across * ca;
                if (Math.abs(bu) <= 1f && Math.abs(bv) <= 1f) {   // off the ceiling: it stays a scattered star
                    u = bu;
                    v = bv;
                }
            }
            s = mix(s);
            float r = unit(s);
            float size = 0.12f + 0.88f * r * r * r * r;                 // a long tail of bright ones
            out[i] = new float[]{u * hx, v * hz, Math.min(1f, size)};
        }
        return out;
    }

    /** The direction the Milky Way band crosses the ceiling, radians, in normalised (u, v) space. */
    public static double bandAngle(long seed) {
        return 0.35 + unit(mix(seed ^ 0x5DEECE66DL)) * 0.9;            // always a diagonal, never along a wall
    }

    /** How much of the faint band glow lies at (u, v): 1 on the band's spine, falling to 0 across it. */
    public static float bandGlow(long seed, float u, float v) {
        double a = bandAngle(seed);
        float d = (float) (-Math.sin(a) * u + Math.cos(a) * v);
        return (float) Math.exp(-(d * d) / (BAND_WIDTH * BAND_WIDTH * 0.35f));
    }

    /** A star's brightness at scene time {@code t} ticks, 0.35 to 1: two slow sines on the star's own phase and rate. */
    public static float twinkle(float t, int starIndex) {
        long h = mix(starIndex * 0x9E3779B97F4A7C15L + 0x2545F4914F6CDD1DL);
        float rate1 = 0.04f + 0.10f * unit(h);
        h = mix(h);
        float rate2 = 0.15f + 0.25f * unit(h);
        h = mix(h);
        float phase = 6.2831855f * unit(h);
        h = mix(h);
        float depth = 0.35f + 0.65f * unit(h);                       // some stars barely flicker, some pulse
        float w = 0.65f * (float) Math.sin(t * rate1 + phase) + 0.35f * (float) Math.sin(t * rate2 + phase * 1.7f);
        float k = 0.5f + 0.5f * w;                                   // 0..1
        float b = 1f - depth * (1f - k);
        return 0.35f + 0.65f * b;
    }

    /** Cloud texture UV drift at scene time {@code t} ticks. */
    public static float cloudOffset(float t) {
        return t * CLOUD_DRIFT;
    }

    /** The height the sky plane is drawn at, for a ceiling event at block height {@code y}. */
    public static float planeY(float y) {
        return y - ROOF_GAP;
    }

    /**
     * The shooting star in flight at scene time {@code t}, or null: {x0, z0, x1, z1, progress} with both ends inside
     * {@code ±hx, ±hz} and progress 0..1 along it. Rare: at most one every {@value #SHOOT_WINDOW} ticks, and some
     * windows have none.
     */
    public static float[] shootingStar(long seed, float t, float hx, float hz) {
        if (t < 0) return null;
        int window = (int) (t / SHOOT_WINDOW);
        long h = mix(seed ^ (window * 0xD1B54A32D192ED03L));
        if (unit(h) < 0.35f) return null;                            // a quiet stretch
        h = mix(h);
        float start = unit(h) * (SHOOT_WINDOW - SHOOT_TICKS);
        float p = (t - window * SHOOT_WINDOW - start) / SHOOT_TICKS;
        if (p < 0f || p > 1f) return null;
        h = mix(h);
        float x0 = (unit(h) * 1.6f - 0.8f) * hx;
        h = mix(h);
        float z0 = (unit(h) * 1.6f - 0.8f) * hz;
        h = mix(h);
        double a = unit(h) * Math.PI * 2;
        float len = Math.min(12f, 0.6f * Math.max(hx, hz));
        float x1 = clamp(x0 + (float) Math.cos(a) * len, hx);
        float z1 = clamp(z0 + (float) Math.sin(a) * len, hz);
        return new float[]{x0, z0, x1, z1, p};
    }

    private static float clamp(float v, float h) {
        return Math.max(-h, Math.min(h, v));
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
