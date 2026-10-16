package com.hogwartsmagic.pensieve.scene;

/** One key of a {@link Track}. {@code ease} shapes the segment that ends at this key. */
public record Keyframe(float t, float[] v, Ease ease) {
}
