package com.hogwartsmagic.pensieve.scene;

import java.util.function.DoubleUnaryOperator;

/** Easing curve for the keyframe segment that ends at a key. Cubic; no switch (see project rule). */
public enum Ease {
    LINEAR(x -> x),
    IN(x -> x * x * x),
    OUT(x -> 1 - Math.pow(1 - x, 3)),
    IN_OUT(x -> x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2);

    private final DoubleUnaryOperator fn;

    Ease(DoubleUnaryOperator fn) {
        this.fn = fn;
    }

    public float apply(float x) {
        return (float) fn.applyAsDouble(x);
    }

    /** "linear", "in", "out", "in_out"; null defaults to IN_OUT, anything else is rejected with null. */
    public static Ease parse(String s) {
        if (s == null) return IN_OUT;
        for (Ease e : values()) {
            if (e.name().equalsIgnoreCase(s)) return e;
        }
        return null;
    }
}
