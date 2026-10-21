package com.hogwartsmagic.pensieve.scene;

import java.util.function.DoubleUnaryOperator;

/**
 * Easing curve for the keyframe segment that ends at a key. Cubic; no switch (see project rule).
 *
 * <p>{@link #SPLINE} is for camera keys only ({@link CameraTrack}): there the segment is a C1 cubic through the
 * neighbouring keys, not a curve of its own, so {@link #apply} is linear for it. Actor tracks reject it on parse.
 */
public enum Ease {
    LINEAR(x -> x, 1, 1),
    IN(x -> x * x * x, 0, 3),
    OUT(x -> 1 - Math.pow(1 - x, 3), 3, 0),
    IN_OUT(x -> x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2, 0, 0),
    SPLINE(x -> x, 1, 1);

    private final DoubleUnaryOperator fn;
    private final float startSlope;
    private final float endSlope;

    Ease(DoubleUnaryOperator fn, float startSlope, float endSlope) {
        this.fn = fn;
        this.startSlope = startSlope;
        this.endSlope = endSlope;
    }

    public float apply(float x) {
        return (float) fn.applyAsDouble(x);
    }

    /** The curve's slope at 0: a segment's speed as it leaves its first key, as a multiple of the straight line's. */
    public float startSlope() {
        return startSlope;
    }

    /** The curve's slope at 1: the speed as the segment reaches its key, as a multiple of the straight line's. */
    public float endSlope() {
        return endSlope;
    }

    /** "linear", "in", "out", "in_out", "spline"; null defaults to IN_OUT, anything else is rejected with null. */
    public static Ease parse(String s) {
        if (s == null) return IN_OUT;
        for (Ease e : values()) {
            if (e.name().equalsIgnoreCase(s)) return e;
        }
        return null;
    }
}
