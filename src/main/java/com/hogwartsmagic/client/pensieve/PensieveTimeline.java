package com.hogwartsmagic.client.pensieve;

/**
 * The dive's timings and curves, as pure functions of phase time, so the cinematic is the same film for every
 * viewer and can be unit tested without a client. No Minecraft types.
 */
public final class PensieveTimeline {
    public static final int SWALLOW_TICKS = 30;
    /** The silver surface rises over the last this-many ticks of the swallow. */
    public static final int SURFACE_RISE_TICKS = 12;
    /** FALL crossfades from the silver surface to black over this many ticks. */
    public static final int FALL_FADE_TICKS = 8;
    public static final int EXIT_TICKS = 30;
    /** EXIT: the fog collapses and the darkness rises over the first half; wisps streak down over the second. */
    public static final int EXIT_COLLAPSE_TICKS = 15;
    /** After the move back, the camera eases up out of the basin to the eye over this many ticks. */
    public static final int RETURN_TICKS = 15;
    /** If the server's move back is late, the black hold gives up after this many ticks. */
    public static final int RETURN_WAIT_LIMIT = 40;
    public static final int YANK_TICKS = 10;
    public static final int FLASH_TICKS = 2;
    public static final int THUD_DECAY_TICKS = 12;
    public static final int SHUDDER_TICKS = 6;
    /** BUILD camera settles down from this height above its key, like landing in the memory. */
    public static final float SETTLE_HEIGHT = 2.5f;
    public static final int SETTLE_TICKS = 25;
    /** A late joiner lands straight in PLAY; it fades in from black over this many ticks. */
    public static final int LATE_FADE_TICKS = 20;
    /** A scene with a {@code next} fades to black over its last this-many ticks; the next one fades in from it. */
    public static final int CHAIN_FADE_TICKS = 20;
    /** The fall through the dark always lasts at least this long, however soon the server is ready. */
    public static final int MIN_FALL_TICKS = 20;

    public static final float BUILD_FOG_FROM = 2f;
    public static final float FOG_INNER = 8f;
    public static final float FOG_OUTER = 2f;
    /** {@code fog_far} scenes: the haze starts at this fraction of the fog distance. */
    public static final float HAZE_FROM = 0.75f;
    public static final float DESAT_BUILD = 0.9f;
    public static final float DESAT_PLAY = 0.35f;
    public static final float SWALLOW_FOV = 30f;
    public static final float VIGNETTE_PLAY = 0.45f;

    private PensieveTimeline() {
    }

    public static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    public static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }

    public static float smoothstep(float k) {
        k = clamp01(k);
        return k * k * (3 - 2 * k);
    }

    public static float easeInOutCubic(float k) {
        k = clamp01(k);
        return k < 0.5f ? 4 * k * k * k : 1 - (float) Math.pow(-2 * k + 2, 3) / 2;
    }

    public static float easeInQuad(float k) {
        k = clamp01(k);
        return k * k;
    }

    public static float easeOutCubic(float k) {
        k = clamp01(k);
        float i = 1 - k;
        return 1 - i * i * i;
    }

    public static float easeInOutSine(float k) {
        k = clamp01(k);
        return (float) (-(Math.cos(Math.PI * k) - 1) / 2);
    }

    /** SWALLOW: how far the camera has travelled from the eye to the basin surface, 0..1. */
    public static float swallowMove(float t) {
        return easeInOutCubic(t / SWALLOW_TICKS);
    }

    /** SWALLOW: how much of the screen the silver surface covers, 0..1 (only over the last 12 ticks). */
    public static float surfaceRise(float t) {
        return smoothstep((t - (SWALLOW_TICKS - SURFACE_RISE_TICKS)) / SURFACE_RISE_TICKS);
    }

    /** The reveal (BUILD, and its tail into PLAY): 0..1 over {@code span} ticks. */
    public static float reveal(float t, int span) {
        if (span <= 0) return 1;
        return clamp01(t / span);
    }

    /** The visible radius grows from 2 to the scene radius as the memory is revealed. */
    public static float revealFogRadius(float reveal, float radius) {
        return lerp(BUILD_FOG_FROM, radius, easeOutCubic(reveal));
    }

    /** Desaturation eases from 0.9 to 0.35 as the memory is revealed. */
    public static float revealDesaturation(float reveal) {
        return lerp(DESAT_BUILD, DESAT_PLAY, smoothstep(reveal));
    }

    /** Darkness eases from 1 (black) to 0 as the memory is revealed. */
    public static float revealDarkness(float reveal) {
        return 1 - smoothstep(reveal);
    }

    /**
     * A chained scene's end: darkness eases from 0 to 1 over the last {@link #CHAIN_FADE_TICKS} before
     * {@code length}, and stays black past it while the next scene is handed over.
     */
    public static float chainEndDarkness(float sceneTime, int length) {
        return smoothstep((sceneTime - (length - CHAIN_FADE_TICKS)) / CHAIN_FADE_TICKS);
    }

    /** EXIT: the visible radius collapses to nothing over the first 15 ticks. */
    public static float exitFogRadius(float t, float radius) {
        return radius * (1 - easeInQuad(t / EXIT_COLLAPSE_TICKS));
    }

    public static float exitDarkness(float t) {
        return smoothstep(t / EXIT_COLLAPSE_TICKS);
    }

    public static float fogStart(float visibleRadius) {
        return Math.max(0, visibleRadius - FOG_INNER);
    }

    public static float fogEnd(float visibleRadius) {
        return Math.max(0.5f, visibleRadius + FOG_OUTER);
    }

    /**
     * A {@code fog_far} scene's fog, as {near, far}: the haze begins at {@link #HAZE_FROM} of the fog distance and
     * is total at the fog distance, so a far backdrop fades into the dark instead of ending at a wall. The fog
     * distance grows and collapses with the visible radius ({@code visibleRadius / radius}, 0..1), and {@code cap}
     * (the client's own render-distance fog) still wins; near always stays below far.
     */
    public static float[] farFog(float visibleRadius, float radius, float fogFar, float cap) {
        float k = radius <= 0 ? 1 : clamp01(visibleRadius / radius);
        float far = Math.min(cap, fogEnd(fogFar * k));
        float near = Math.min(fogFar * k * HAZE_FROM, far * HAZE_FROM);
        return new float[]{Math.max(0, near), far};
    }

    /** A hit on the body: red edge strength, decaying linearly over 12 ticks. */
    public static float thud(float strength, float sinceHit) {
        if (sinceHit < 0) return 0;
        return strength * Math.max(0, 1 - sinceHit / THUD_DECAY_TICKS);
    }

    /** A hit on the body: screen shudder amplitude, over 6 ticks. */
    public static float shudder(float strength, float sinceHit) {
        if (sinceHit < 0) return 0;
        return strength * Math.max(0, 1 - sinceHit / SHUDDER_TICKS);
    }

    /** RETURN: the camera rising from the basin surface back to the eye, 0..1. */
    public static float returnMove(float t) {
        return easeInOutCubic(t / RETURN_TICKS);
    }
}
