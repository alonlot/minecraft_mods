package com.hogwartsmagic.pensieve.scene;

/**
 * How far an actor has sunk into a crouch, for the camera that looks at its eye. Vanilla swaps the player's eye
 * height in one frame when the pose changes; a camera aimed at that eye would jolt, so the camera reads this
 * eased fraction instead. Pure maths over the actor's sneak/stand cues, so it is stateless and any time can be
 * sampled (a late joiner, a rewind) with the same answer.
 */
public final class EyeEase {
    /** Ticks a crouch or a stand takes to ease through. */
    public static final float TICKS = 4f;
    /** A player's eye height standing and crouching, in blocks at scale 1. */
    public static final float STAND_EYE = 1.62f;
    public static final float CROUCH_EYE = 1.27f;

    private EyeEase() {
    }

    /**
     * The crouch fraction at scene time {@code t}: 0 standing, 1 crouched, smoothstepped over {@link #TICKS} from
     * each cue and continuing from wherever an interrupted change had got to. {@code times} ascend;
     * {@code crouch[i]} is the pose the i-th cue switches to. Stands before the first cue.
     */
    public static float fraction(float[] times, boolean[] crouch, float t) {
        float from = 0f;
        float target = 0f;
        float start = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < times.length && times[i] <= t; i++) {
            from = at(from, target, start, times[i]);
            target = crouch[i] ? 1f : 0f;
            start = times[i];
        }
        return at(from, target, start, t);
    }

    /** The eye height for that fraction. */
    public static float eyeHeight(float fraction) {
        return STAND_EYE + (CROUCH_EYE - STAND_EYE) * fraction;
    }

    private static float at(float from, float target, float start, float t) {
        float u = (t - start) / TICKS;
        if (!(u > 0f)) return from;
        if (u >= 1f) return target;
        return from + (target - from) * (u * u * (3f - 2f * u));
    }
}
