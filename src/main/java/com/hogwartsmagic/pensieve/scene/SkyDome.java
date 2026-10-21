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
    public static final List<String> STYLES = List.of("day", "overcast", "dawn");
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

    /** How far the clouds have slid by scene time {@code now} (ticks), in texture tiles: a slow steady drift. */
    public static float cloudDrift(float now) {
        return now * 0.00035f;
    }
}
