package com.hogwartsmagic.pensieve.scene;

/**
 * The {@code shield_dome} and {@code shield_hit} effects: a Protego Maxima raised over a whole place, struck by spells
 * and burnt away. Pure maths (unit tested), drawn by {@code client/pensieve/ShieldDomeRenderer}.
 *
 * <p>The dome is the upper half of an ellipsoid on {@code at} with half-axes {@code spread} [rx, ry, rz]. Over
 * {@link #RISE_TICKS} it builds from the ground up: everything under the rising seam ({@link #rise}, as a share of the
 * dome's height) is there, with a bright band just under the seam. A hit ({@code shield_hit} at a point near the
 * surface) flashes there and sends a ring out over the surface ({@link #ripple}). When the dome's {@code duration}
 * runs out it burns away: a front spreads from the last hit (the apex if there was none) over {@link #BURN_TICKS},
 * leaving nothing behind it and an ember band along it ({@link #burn}). Everything is a function of the scene clock.
 */
public final class ShieldDome {
    public static final int RISE_TICKS = 90;
    public static final int BURN_TICKS = 70;
    public static final int HIT_TICKS = 26;
    /** How far a hit's ring travels over the surface, in blocks. */
    public static final float HIT_REACH = 16f;
    /** The bright band under the rising seam, as a share of the dome's height. */
    public static final float SEAM_BAND = 0.07f;
    /** The ember band behind the burning front, in blocks. */
    public static final float EMBER_BAND = 7f;
    /** A dome's half-axis range, each axis, in blocks. */
    public static final float MIN_SPREAD = 2f, MAX_SPREAD = 200f;

    private ShieldDome() {
    }

    private static float clamp01(float x) {
        return x < 0f ? 0f : x > 1f ? 1f : x;
    }

    private static float smooth(float x) {
        x = clamp01(x);
        return x * x * (3f - 2f * x);
    }

    /** How far the dome has risen, 0 to 1 of its height, {@code elapsed} ticks after it was cast. */
    public static float rise(float elapsed) {
        if (elapsed <= 0f) return 0f;
        float f = clamp01(elapsed / RISE_TICKS);
        return 1f - (1f - f) * (1f - f) * (1f - f);
    }

    /**
     * The surface's presence at height share {@code h} (0 at the ground, 1 at the apex) when it has risen to
     * {@code rise}: 1 well below the seam, fading to 0 across a thin edge at it. A risen dome is whole.
     */
    public static float risen(float h, float rise) {
        if (rise >= 1f) return 1f;
        return smooth((rise - h) / 0.02f);
    }

    /** The extra glow of the band just under the rising seam (0 once the dome is up). */
    public static float seam(float h, float rise) {
        if (rise >= 1f || rise <= 0f) return 0f;
        float d = rise - h;
        if (d < 0f || d > SEAM_BAND) return 0f;
        return (1f - d / SEAM_BAND) * (1f - rise * 0.5f);
    }

    /**
     * A hit's brightness at {@code dist} blocks from where it struck, {@code age} ticks later: a white flash at the
     * point over its first 8 ticks and a ring running out to {@link #HIT_REACH}, thinning as it goes.
     */
    public static float ripple(float dist, float age) {
        if (age < 0f || age >= HIT_TICKS) return 0f;
        float f = age / HIT_TICKS;
        float r = HIT_REACH * (1f - (1f - f) * (1f - f));
        float w = 1.6f + 2.4f * f;
        float d = (dist - r) / w;
        float ring = (float) Math.exp(-d * d) * (1f - f) * 1.2f;
        float flash = age < 8f ? (float) Math.exp(-dist * dist / 10f) * (1f - age / 8f) * 2.4f : 0f;
        return ring + flash;
    }

    /** How far the burning front has spread {@code since} ticks after the break, for a dome whose longest chord is {@code span}. */
    public static float front(float since, float span) {
        if (since <= 0f) return 0f;
        float f = clamp01(since / BURN_TICKS);
        // Slow to catch, then racing over the shell.
        return span * f * f * (0.35f + 0.65f * f) + 2f * f;
    }

    /** What is left of the surface at {@code dist} from the break point: 0 behind the front, 1 ahead of the embers. */
    public static float burnt(float dist, float front) {
        if (front <= 0f) return 1f;
        return smooth((dist - front) / EMBER_BAND);
    }

    /** The ember glow at {@code dist} from the break point: peaks just ahead of the front, gone behind it. */
    public static float embers(float dist, float front) {
        if (front <= 0f) return 0f;
        float d = dist - front;
        if (d < 0f || d > EMBER_BAND * 1.6f) return 0f;
        float x = d / (EMBER_BAND * 1.6f);
        return (float) Math.sin(Math.PI * Math.sqrt(x)) * 1.4f;
    }

    /** A rim glow: faint face-on, bright where the view grazes the surface ({@code cos} = |normal . view|). */
    public static float fresnel(float cos) {
        float g = 1f - clamp01(cos);
        return 0.05f + 0.55f * g * g * g;
    }

    /** A slow shimmer over the surface, 0.75 to 1.25, from the longitude and height share and the scene clock. */
    public static float shimmer(float lon, float h, float t) {
        double a = Math.sin(lon * 7.0 + t * 0.045) * Math.sin(h * 11.0 - t * 0.03);
        double b = Math.sin(lon * 3.0 - h * 5.0 + t * 0.021);
        return (float) (1.0 + 0.15 * a + 0.1 * b);
    }

    /** Whether the dome is still drawn {@code elapsed} ticks after its cast ({@code duration} 0: until the scene ends). */
    public static boolean alive(float elapsed, int duration) {
        return duration <= 0 || elapsed < duration + BURN_TICKS;
    }
}
