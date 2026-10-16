package com.hogwartsmagic.pensieve.scene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Keyframed vector curve: holds the first value before the first key and the last after the last. */
public final class Track {
    public static final Track EMPTY = new Track(List.of());

    private final List<Keyframe> keys;

    public Track(List<Keyframe> keys) {
        List<Keyframe> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.comparingDouble(Keyframe::t));
        this.keys = List.copyOf(sorted);
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public List<Keyframe> keys() {
        return keys;
    }

    public float[] sample(float t) {
        if (keys.isEmpty()) return new float[0];
        Keyframe first = keys.get(0);
        if (t <= first.t()) return first.v().clone();
        Keyframe last = keys.get(keys.size() - 1);
        if (t >= last.t()) return last.v().clone();
        for (int i = 1; i < keys.size(); i++) {
            Keyframe next = keys.get(i);
            if (t <= next.t()) {
                Keyframe prev = keys.get(i - 1);
                float span = next.t() - prev.t();
                float frac = span <= 0 ? 1 : (t - prev.t()) / span;
                float k = next.ease().apply(frac);
                int n = Math.min(prev.v().length, next.v().length);
                float[] out = new float[n];
                for (int c = 0; c < n; c++) out[c] = prev.v()[c] + (next.v()[c] - prev.v()[c]) * k;
                return out;
            }
        }
        return last.v().clone();
    }
}
