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
    /**
     * A chain's hand-over holds the screen black until the new set is drawn around the camera (its chunks are a slot
     * away and arrive after the move), but never longer than this many ticks.
     */
    public static final int HANDOVER_HOLD_LIMIT = 60;
    /** A camera that moves farther than this in one frame jumped (a cut, a hand-over): it did not travel. */
    public static final double CAMERA_JUMP_BLOCKS = 2.0;

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

    /**
     * The colour of a chain's hand-over, as {black, white} cover amounts (0..1; at most one is above 0). The cover is
     * whichever is deeper of the entering scene's fade-in ({@code revealDarkness}, 1 while the hand-over holds for
     * the set) and the leaving scene's fade ({@code chainDarkness}); the deeper one supplies the colour:
     * {@code carriedWhite} for the fade-in (the previous scene handed over through white) and {@code sceneWhite}
     * for the fade out. So white holds through the build and fades in from white.
     */
    public static float[] handoverColour(boolean carriedWhite, boolean sceneWhite, float revealDarkness,
                                         float chainDarkness) {
        float rd = clamp01(revealDarkness);
        float cd = clamp01(chainDarkness);
        boolean fadingIn = rd >= cd;
        float amount = fadingIn ? rd : cd;
        boolean white = fadingIn ? carriedWhite : sceneWhite;
        return white ? new float[]{0f, amount} : new float[]{amount, 0f};
    }

    /** Whether a hand-over carries white into the next scene: only a chain end, never one the server asked for. */
    public static boolean carriesWhite(boolean switched, boolean oldSceneWhite, boolean fadeAsked) {
        return switched && oldSceneWhite && !fadeAsked;
    }

    /** Whether the scene's own chain fade goes to white: it has a next, is white, and no asked fade took over. */
    public static boolean sceneFadesWhite(boolean hasNext, boolean white, boolean fadeAsked) {
        return hasNext && white && !fadeAsked;
    }

    /**
     * How far above its key the camera is while it lands in the memory: {@link #SETTLE_HEIGHT} easing to 0 over
     * {@link #SETTLE_TICKS} of reveal time. A chain's hand-over fades in on the next shot as it is: no drop.
     */
    public static float settleHeight(float revealTime, boolean handOver) {
        if (handOver || revealTime >= SETTLE_TICKS) return 0f;
        return SETTLE_HEIGHT * (1 - easeOutCubic(revealTime / SETTLE_TICKS));
    }

    /**
     * The alpha factor for spoken ({@code by}) lines: they are not drawn over shut eyelids or a chain's fade to
     * black. Narration is exempt (it is meant to be read over black).
     */
    public static float spokenWordsAlpha(float lidsClosed, float chainDarkness) {
        return 1 - Math.max(clamp01(lidsClosed), clamp01(chainDarkness));
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

    /** True while a hand-over still holds black: the set is not drawn yet and the wait has not given up. */
    public static boolean handOverHeld(boolean setReady, float heldTicks) {
        return !setReady && heldTicks < HANDOVER_HOLD_LIMIT;
    }

    /**
     * The reveal the set itself (fog radius, grade, shadow smoke) follows: the dive's own reveal, or all of it at
     * once after a chain's hand-over, which only fades in from black. Re-running the entry's fog-from-two-blocks
     * there made every hand-over look like the memory loading again.
     */
    public static float setReveal(float reveal, boolean handedOver) {
        return handedOver ? 1f : reveal;
    }

    /** True when the camera moved by ({@code dx}, {@code dy}, {@code dz}) in one frame is a jump, not a move. */
    public static boolean cameraJumped(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz > CAMERA_JUMP_BLOCKS * CAMERA_JUMP_BLOCKS;
    }

    /**
     * Squared distance from the point ({@code px}, {@code py}, {@code pz}) to the 16-block render section whose
     * minimum corner is ({@code sx}, {@code sy}, {@code sz}); 0 inside it.
     */
    public static double sectionDistSq(int sx, int sy, int sz, double px, double py, double pz) {
        double dx = Math.max(Math.max(sx - px, px - (sx + 16)), 0);
        double dy = Math.max(Math.max(sy - py, py - (sy + 16)), 0);
        double dz = Math.max(Math.max(sz - pz, pz - (sz + 16)), 0);
        return dx * dx + dy * dy + dz * dz;
    }
}
