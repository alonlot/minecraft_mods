package com.hogwartsmagic.pensieve.scene;

/**
 * Pure maths for the {@code wand_choice} effect: the moment the right wand chooses the boy. A warm golden glow gathers
 * at {@code at} (the wand tip, or the boy's chest) over {@link #GATHER_TICKS}, a soft bloom washes the screen as it
 * peaks, a wind of glowing motes spirals up round him, and everything settles over the last {@link #SETTLE_TICKS}.
 * Positions are offsets from {@code at} in blocks (y up). A function of the effect's age and a seed, so every viewer
 * sees the same motes. The drawing is {@code client/pensieve/EffectVisuals.WandChoiceGlow}.
 */
public final class WandChoice {
    /** A wand_choice with no {@code duration} lasts this long (5 s). */
    public static final int DEFAULT_TICKS = 100;
    /** The glow gathers over this many ticks, and settles over the last this many. */
    public static final int GATHER_TICKS = 20;
    public static final int SETTLE_TICKS = 30;
    /** The event's {@code intensity} range: a multiplier on the bloom and the motes (default 1). */
    public static final float MIN_INTENSITY = 0.1f, MAX_INTENSITY = 2f;
    /** How many motes, and the column they stay in: within this radius of {@code at} and this high above it. */
    public static final int MOTES = 80;
    public static final float MAX_RADIUS = 2.4f;
    public static final float MAX_HEIGHT = 4.0f;
    /** The bloom's peak alpha at intensity 1, and the share of it that holds until the settle. */
    public static final float BLOOM_ALPHA = 0.3f;
    private static final float BLOOM_HOLD = 0.35f;
    /** Only a viewer whose camera is this close to {@code at} sees the bloom. */
    public static final double BLOOM_RANGE = 24.0;
    /** Where the gathering motes start, and how close in they come. */
    private static final float GATHER_FROM = 2.0f, GATHER_TO = 0.12f;

    private WandChoice() {
    }

    /** The effect's length for an event {@code duration} (0 or less: {@link #DEFAULT_TICKS}). */
    public static int duration(int eventDuration) {
        return eventDuration > 0 ? eventDuration : DEFAULT_TICKS;
    }

    /** The multiplier for an event's {@code intensity}: 1 when absent (NaN), else clamped to the range. */
    public static float intensity(float eventIntensity) {
        if (Float.isNaN(eventIntensity)) return 1f;
        return Math.max(MIN_INTENSITY, Math.min(MAX_INTENSITY, eventIntensity));
    }

    /** The gather, shortened for a short effect so a gather and a settle always fit. */
    private static float gather(int total) {
        return Math.min(GATHER_TICKS, total * 0.4f);
    }

    private static float settle(int total) {
        return Math.min(SETTLE_TICKS, total * 0.5f);
    }

    private static float smooth(float x) {
        float k = Math.max(0f, Math.min(1f, x));
        return k * k * (3 - 2 * k);
    }

    /** The settle factor: 1 until the settle begins, easing to 0 at the end. */
    private static float fade(float t, int total) {
        float s = settle(total);
        return 1f - smooth((t - (total - s)) / s);
    }

    /** The golden glow at {@code at}, 0 to 1: gathers (smoothly) over the gather, holds, settles to 0 at the end. */
    public static float glow(float t, int total) {
        if (t <= 0 || t >= total) return 0f;
        return Math.min(smooth(t / gather(total)), fade(t, total));
    }

    /** The wind's strength, 0 to 1: it rises from halfway through the gather, holds, and settles with the glow. */
    public static float wind(float t, int total) {
        if (t <= 0 || t >= total) return 0f;
        float g = gather(total);
        return Math.min(smooth((t - g * 0.5f) / g), fade(t, total));
    }

    /**
     * The screen bloom's alpha: rises with the gather to {@link #BLOOM_ALPHA} at its end, eases back to a soft
     * {@code BLOOM_HOLD} share, and fades out with the settle; times the intensity.
     */
    public static float bloom(float t, int total, float intensity) {
        if (t <= 0 || t >= total) return 0f;
        float g = gather(total);
        float a;
        if (t <= g) {
            a = smooth(t / g);
        } else {
            float k = smooth((t - g) / Math.max(1f, g));
            a = 1f - (1f - BLOOM_HOLD) * k;
        }
        return BLOOM_ALPHA * a * Math.min(1f, fade(t, total)) * intensity;
    }

    /**
     * Mote {@code i} at age {@code t}: {x, y, z, alpha}, an offset from {@code at} and how bright it is (0 to 1).
     * While the glow gathers the motes swirl in from a ring round {@code at} to its centre; then they spiral up round
     * it in a widening column, each on its own loop, fading at the top; all dim with the settle.
     */
    public static float[] mote(long seed, int i, float t, int total) {
        if (t <= 0 || t >= total) return new float[]{0, 0, 0, 0};
        long h = mix(seed * 0x9E3779B97F4A7C15L + i);
        float a0 = unit(h) * (float) (Math.PI * 2);
        float u = unit(h >>> 11);
        float v = unit(h >>> 22);
        float w = unit(h >>> 33);
        float g = gather(total);
        if (t < g) {
            float k = smooth(t / g);
            float r = GATHER_FROM + 0.3f * v - (GATHER_FROM + 0.3f * v - GATHER_TO) * k;
            float ang = a0 + k * (2.5f + w) * (float) Math.PI;
            float y = (0.3f + 1.6f * u) * (1f - k);
            float alpha = 0.85f * smooth(t / (g * 0.4f));
            return new float[]{(float) Math.cos(ang) * r, y, (float) Math.sin(ang) * r, alpha};
        }
        float s0 = t - g;
        float period = 36f + 24f * w;                    // ticks for one loop up the column
        float s = (s0 / period + u) % 1f;
        float r0 = 0.35f + 0.5f * v;
        float r = r0 + (MAX_RADIUS - 0.05f - r0) * s * s;
        float ang = a0 + s0 * (0.09f + 0.06f * v) + s * (float) (Math.PI * 2);
        float y = MAX_HEIGHT * s;
        // In at the bottom, out at the top, and never a pop when the loop starts over.
        float life = (float) Math.sin(Math.PI * s);
        float alpha = Math.max(0f, Math.min(1f, life * wind(t, total)));
        return new float[]{(float) Math.cos(ang) * r, y, (float) Math.sin(ang) * r, alpha};
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** 0 to 1 from the low 11 bits. */
    private static float unit(long bits) {
        return (bits & 0x7FF) / 2047f;
    }
}
