package com.hogwartsmagic.pensieve.scene;

/**
 * Pure maths for the {@code light_rays} effect: warm god-rays pouring through a widening doorway, with dust motes
 * drifting in them. Everything is in the effect's local frame: x across the doorway (lateral), y up from the floor,
 * z along the light (0 at the doorway, {@code len} at the far end), with {@code hw}, {@code h}, {@code len} the
 * event's {@code spread}. A function of the scene time and a seed, so every viewer sees the same light. The drawing
 * is {@code client/pensieve/LightRaysRenderer}.
 */
public final class LightRays {
    /** A light_rays with no {@code duration} opens over this many ticks (2 s). */
    public static final int DEFAULT_OPEN_TICKS = 40;
    /** The gap at the first tick, as a fraction of the full width: a thin bright line. */
    public static final float CRACK = 0.04f;
    /** How many shafts, and how many motes. */
    public static final int SHAFTS = 18;
    public static final int MOTES = 140;
    /** The most any ray or mote strays sideways per block travelled: the light box is {@code ±(hw + MAX_FAN len)}. */
    public static final float MAX_FAN = 0.35f;
    /** The light enters through the middle 85% of the doorway (the jambs and leaf edges eat the rest). */
    private static final float INNER = 0.85f;
    /** Ticks for the light to come up from nothing, so the first frame never pops. */
    private static final float APPEAR_TICKS = 6f;

    private LightRays() {
    }

    /**
     * How open the doorway is {@code elapsed} ticks in, 0 to 1 of the full width: a crack at 0, then ease-in (slow,
     * then accelerating like a heavy door) to full at {@code duration}, and full from then on. 0 before it starts.
     */
    public static float gap(float elapsed, int duration) {
        if (elapsed < 0) return 0f;
        if (duration <= 0 || elapsed >= duration) return 1f;
        float k = elapsed / duration;
        return CRACK + (1f - CRACK) * k * k;
    }

    /** The light's fade-in over its first few ticks, 0 to 1. */
    public static float appear(float elapsed) {
        return Math.max(0f, Math.min(1f, elapsed / APPEAR_TICKS));
    }

    /**
     * {@code count} shafts, each {x, y, fanX, fanY, width, phase}: x is where it crosses the doorway (-1..1 of the
     * open gap, spread evenly with jitter), y its height (fraction of h), fanX/fanY its sideways and downward drift
     * per block travelled (outer shafts fan outward; all sink a little toward the floor), width its half-width as a
     * fraction of hw, phase a shimmer phase.
     */
    public static float[][] shafts(long seed, int count) {
        float[][] out = new float[Math.max(0, count)][];
        long s = seed * 0x9E3779B97F4A7C15L + 0x1B873593L;
        for (int i = 0; i < out.length; i++) {
            s = mix(s + i);
            float x = -1f + (2f * i + 1f) / out.length + (unit(s) - 0.5f) * (1.6f / out.length);
            x = Math.max(-1f, Math.min(1f, x));
            s = mix(s);
            float y = 0.2f + 0.75f * unit(s);
            s = mix(s);
            float fanX = x * 0.2f + (unit(s) - 0.5f) * 0.1f;
            s = mix(s);
            float fanY = -0.02f - 0.12f * unit(s);
            s = mix(s);
            float width = 0.2f + 0.3f * unit(s);
            s = mix(s);
            out[i] = new float[]{x, y, fanX, fanY, width, 6.2831855f * unit(s)};
        }
        return out;
    }

    /** A shaft's axis at gap {@code gap}: {sx, sy, sz, ex, ey, ez}, from the doorway (z 0) to the far end (z len). */
    public static float[] shaftEnds(float[] shaft, float gap, float hw, float h, float len) {
        float sx = shaft[0] * gap * hw * INNER;
        float sy = shaft[1] * h;
        float ex = sx + shaft[2] * len;
        float ey = Math.max(0.05f * h, Math.min(h, sy + shaft[3] * len));
        return new float[]{sx, sy, 0f, ex, ey, len};
    }

    /**
     * A shaft's half-width {at the doorway, at the far end}: squeezed to the crack while the door is shut, widening
     * with the gap, and spreading as it travels (less from a narrow slit).
     */
    public static float[] shaftHalfWidths(float[] shaft, float gap, float hw, float len) {
        float start = Math.max(0.01f, shaft[4] * hw * Math.max(gap, 0.02f));
        float end = start + shaft[4] * 0.25f * len * (0.35f + 0.65f * (float) Math.sqrt(Math.max(0f, gap)));
        return new float[]{start, end};
    }

    /** A shaft's brightness at scene time {@code t}, 0.6 to 1: a slow shimmer on the shaft's own two rhythms. */
    public static float shimmer(float t, int shaft) {
        long h = mix(shaft * 0x9E3779B97F4A7C15L + 0x7F4A7C15L);
        float r1 = 0.03f + 0.04f * unit(h);
        h = mix(h);
        float r2 = 0.09f + 0.08f * unit(h);
        h = mix(h);
        float p = 6.2831855f * unit(h);
        float w = 0.6f * (float) Math.sin(t * r1 + p) + 0.4f * (float) Math.sin(t * r2 + p * 2.3f);
        return 0.6f + 0.4f * (0.5f + 0.5f * w);
    }

    /**
     * Dust mote {@code i} at scene time {@code t}: {x, y, z, brightness}. Motes drift slowly along the light and bob,
     * wrapping round from the far end to the doorway (faded at both ends so the wrap never shows), and only glint
     * where the light actually reaches: dark while the door is shut, brightest near the doorway.
     */
    public static float[] mote(long seed, int i, float t, float gap, float hw, float h, float len) {
        long s = mix(seed ^ (i * 0xC2B2AE3D27D4EB4FL + 0x165667B19E3779F9L));
        float fx = unit(s) * 2f - 1f;
        s = mix(s);
        float fy = 0.05f + 0.9f * unit(s);
        s = mix(s);
        float fz = unit(s);
        s = mix(s);
        float speed = 0.0008f + 0.0012f * unit(s);
        s = mix(s);
        float p = 6.2831855f * unit(s);
        s = mix(s);
        float r = 0.7f + 0.6f * unit(s);

        float zf = fz + t * speed;
        zf -= (float) Math.floor(zf);
        float z = zf * len;
        float y = clamp(fy * h + 0.3f * (float) Math.sin(t * 0.02f * r + p), 0f, h);
        float limit = hw + MAX_FAN * len;
        float x = clamp(fx * (INNER * hw + 0.25f * z) + 0.25f * (float) Math.sin(t * 0.017f * r + p * 1.7f), -limit,
                limit);

        float ext = INNER * hw * gap + 0.22f * z + 0.15f;
        float inside = 1f - smoothstep(0.6f * ext, ext, Math.abs(x));
        float ends = (float) Math.sin(Math.PI * zf);
        float near = 1f - 0.6f * zf;
        float glint = 0.55f + 0.45f * (float) Math.sin(t * 0.11f * r + p * 3.1f);
        float b = inside * ends * near * glint * Math.min(1f, gap * 3f);
        return new float[]{x, y, z, clamp(b, 0f, 1f)};
    }

    /**
     * A local offset (lateral, up, along) as a world offset, for light travelling at Minecraft {@code yaw}: yaw 0 is
     * +Z (south), 90 is -X (west). Lateral is the travel direction turned 90 degrees.
     */
    public static double[] worldOffset(float yaw, float lateral, float up, float along) {
        double r = Math.toRadians(yaw);
        double fx = -Math.sin(r), fz = Math.cos(r);
        double sx = Math.cos(r), sz = Math.sin(r);
        return new double[]{fx * along + sx * lateral, up, fz * along + sz * lateral};
    }

    private static float smoothstep(float e0, float e1, float x) {
        float k = clamp((x - e0) / (e1 - e0), 0f, 1f);
        return k * k * (3f - 2f * k);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
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
