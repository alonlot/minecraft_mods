package com.hogwartsmagic.pensieve.scene;

import java.util.List;

/**
 * Pure maths for the {@code sky} effect: a daytime sky for a memory whose biome sky and fog are black
 * ({@code memory_void}). The client draws a large camera-centred dome whose colour runs from the horizon to the zenith
 * ({@link #colour}) under a few soft cloud layers, and, while it shows, lifts the fog colour from black to the horizon
 * colour ({@link #fogColour}) so distant blocks fade into haze instead of into night. Drawing is
 * {@code client/pensieve/SkyRenderer}.
 *
 * <p>Colours are linear 0..1 RGB, picked to read as a real sky rather than a saturated primary.
 */
public final class SkyDome {
    /** The looks a sky may take; the first is the default. */
    public static final List<String> STYLES = List.of("day", "overcast", "dawn", "storm");
    public static final String STORM = "storm";
    /** A lightning flash lights the sky to at most this much (the ceiling's flicker peaks at 1; the open sky is dimmer). */
    public static final double FLASH_PEAK = 0.85;
    /** Storm clouds slide this much of a texture tile a tick along the wind (unit direction), times the layer's speed. */
    public static final float STORM_DRIFT = 0.0035f;
    public static final String DAY = "day";
    /** The sky fades in from its {@code t}, and out over the end of a finite {@code duration}, over this many ticks. */
    public static final int FADE_TICKS = 20;
    /** The dome's smallest radius, and the share of the far plane it stays inside. */
    public static final float MIN_RADIUS = 48f, FAR_SHARE = 0.85f;

    /** {horizon, zenith} per style, in {@link #STYLES} order. */
    private static final float[][][] COLOURS = {
            {{0.80f, 0.88f, 0.96f}, {0.34f, 0.56f, 0.88f}},     // day: pale hazy horizon to a soft blue
            {{0.80f, 0.82f, 0.84f}, {0.62f, 0.65f, 0.69f}},     // overcast: English grey-white
            {{0.98f, 0.75f, 0.58f}, {0.40f, 0.52f, 0.76f}},     // dawn: warm horizon, cool blue overhead
            {{0x3A / 255f, 0x40 / 255f, 0x48 / 255f}, {0x14 / 255f, 0x17 / 255f, 0x1C / 255f}}, // storm: slate to near-black
    };

    private SkyDome() {
    }

    /** {@code style} if it is one of {@link #STYLES}, else the default (an absent or unknown style is day). */
    public static String style(String style) {
        return style != null && STYLES.contains(style) ? style : DAY;
    }

    private static float[][] of(String style) {
        return COLOURS[STYLES.indexOf(style(style))];
    }

    /** The horizon colour of {@code style}: also what the fog turns to. */
    public static float[] horizon(String style) {
        return of(style)[0].clone();
    }

    /**
     * The sky colour at {@code elevation}, 0 (the horizon) to 1 (the zenith); out of range is clamped. The blue deepens
     * quickly off the horizon and slowly overhead, as a real sky does.
     */
    public static float[] colour(String style, float elevation) {
        float[][] c = of(style);
        float e = Math.max(0f, Math.min(1f, elevation));
        float k = (float) Math.pow(e, 0.6);
        return new float[] {c[0][0] + (c[1][0] - c[0][0]) * k, c[0][1] + (c[1][1] - c[0][1]) * k,
                c[0][2] + (c[1][2] - c[0][2]) * k};
    }

    /**
     * How much of the sky shows {@code local} ticks after the effect's {@code t}: 0 before it, easing up to 1 over
     * {@link #FADE_TICKS}, then 1 until the scene ends ({@code duration} 0) or until the last {@link #FADE_TICKS} of a
     * finite {@code duration}, easing out to 0 at its end.
     */
    public static float fade(float local, int duration) {
        float in = smooth(local / FADE_TICKS);
        if (duration <= 0) return in;
        float out = smooth((duration - local) / FADE_TICKS);
        return Math.min(in, out);
    }

    /**
     * {@link #fade(float, int)} for a sky keyed at scene tick {@code startT}: one keyed at tick 0 is the scene's own
     * sky, so it shows in full from the first tick instead of fading in out of the reveal; a later one fades in as
     * usual. A finite {@code duration} still fades out at its end.
     */
    public static float fade(float local, int duration, float startT) {
        if (startT > 0) return fade(local, duration);
        if (local < 0f) return 0f;
        if (duration <= 0) return 1f;
        return smooth((duration - local) / FADE_TICKS);
    }

    private static float smooth(float x) {
        float k = Math.max(0f, Math.min(1f, x));
        return k * k * (3f - 2f * k);
    }

    /** The fog colour while the sky is {@code fade} shown: black (the memory's own) up to the horizon colour. */
    public static float[] fogColour(String style, float fade) {
        float[] h = of(style)[0];
        float f = Math.max(0f, Math.min(1f, fade));
        return new float[] {h[0] * f, h[1] * f, h[2] * f};
    }

    /**
     * The dome's radius for a scene of {@code sceneRadius} blocks: beyond the farthest set block, but never past
     * {@link #FAR_SHARE} of the camera's far plane (a clipped dome would show black), and not below {@link #MIN_RADIUS}.
     */
    public static float domeRadius(float sceneRadius, float farPlane) {
        float want = Math.max(MIN_RADIUS, sceneRadius * 1.25f + 8f);
        return Math.min(want, farPlane * FAR_SHARE);
    }

    /**
     * The lightning flash on the sky {@code t} ticks into the scene, 0 to {@link #FLASH_PEAK}: the strongest of the
     * flickers of every strike in {@code strikeTicks} (the scene ticks of the scene's {@code lightning} effects; null or
     * empty is none). Each strike flickers two or three times over {@link CeilingSky#FLASH_TICKS} (1.3 s), by the
     * shared {@link CeilingSky#flicker}, the second and third pulses picked from the strike's own tick so two strikes
     * differ. Pure: every viewer sees the same flash at the same moment.
     */
    public static double flash(double t, double[] strikeTicks) {
        if (strikeTicks == null) return 0.0;
        float best = 0f;
        for (double s : strikeTicks) {
            double dt = t - s;
            if (dt < 0.0 || dt >= CeilingSky.FLASH_TICKS) continue;
            long h = mix(Double.doubleToLongBits(s) ^ 0x2545F4914F6CDD1DL);
            float second = 0.45f + 0.35f * unit(h);
            h = mix(h);
            float third = unit(h) < 0.5f ? 0f : 0.5f + 0.4f * unit(mix(h));
            best = Math.max(best, CeilingSky.flicker((float) dt, second, third));
        }
        return Math.min(1f, best) * FLASH_PEAK;
    }

    /** {@code colour} lit by a {@code flash} (0..FLASH_PEAK): toward a pale blue-white, 0 leaves it as it is. */
    public static float[] flashed(float[] colour, double flash) {
        float f = (float) Math.max(0.0, Math.min(1.0, flash));
        return new float[] {colour[0] + (0.78f - colour[0]) * f * 0.9f, colour[1] + (0.82f - colour[1]) * f * 0.9f,
                colour[2] + (0.95f - colour[2]) * f * 0.9f};
    }

    /** A storm's wind direction from its {@code seed}: a unit {x, z}, the same for every viewer. */
    public static double[] stormWind(long seed) {
        double a = unit(mix(seed ^ 0x51ED270B3C6EF372L)) * Math.PI * 2;
        return new double[] {Math.cos(a), Math.sin(a)};
    }

    /** How far storm clouds of speed 1 have slid by {@code now} along the unit wind {@code (dx, dz)}, in texture tiles {u, v}. */
    public static double[] stormDrift(float now, double dx, double dz) {
        return new double[] {now * STORM_DRIFT * dx, now * STORM_DRIFT * dz};
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float unit(long h) {
        return (h >>> 40) / (float) (1 << 24);
    }

    /** How far the clouds have slid by scene time {@code now} (ticks), in texture tiles: a slow steady drift. */
    public static float cloudDrift(float now) {
        return now * 0.00035f;
    }
}
