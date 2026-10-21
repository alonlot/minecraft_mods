package com.hogwartsmagic.pensieve.scene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Keyframed vector curve: holds the first value before the first key and the last after the last.
 *
 * <p>A segment takes the ease of the key it ends at. A {@link Ease#SPLINE} segment (actor {@code path} and
 * {@code look} keys only; limbs reject it on parse) is the same cubic Hermite as {@link CameraTrack}'s: at a key
 * between two spline segments the velocity is {@code (h1*d0 + h0*d1)/(h0+h1)} (the parabola through the key and its
 * neighbours), at a key shared with an eased segment it is that segment's own speed there ({@link Ease#startSlope}
 * and {@link Ease#endSlope} times its secant), and at the first or last key the end is natural. A track has no cuts.
 *
 * <p>{@link #sampleWithin} samples only the keys in a time window, as if the rest did not exist: a rider whose
 * path changes frame at a ride window's edge ({@link Vehicle}) never blends a world key with a vehicle-frame key.
 */
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
        return sample(t, 0, keys.size());
    }

    /**
     * The track at t using only the keys with {@code from <= key t < until} (holding the first and last of them), or
     * null when no key lies in the window.
     */
    public float[] sampleWithin(float t, float from, float until) {
        int lo = 0;
        while (lo < keys.size() && keys.get(lo).t() < from) lo++;
        int hi = lo;
        while (hi < keys.size() && keys.get(hi).t() < until) hi++;
        if (hi <= lo) return null;
        return sample(t, lo, hi);
    }

    /** Whether any key lies in {@code from <= key t < until}. */
    public boolean hasKeysWithin(float from, float until) {
        for (Keyframe k : keys) {
            if (k.t() >= from && k.t() < until) return true;
        }
        return false;
    }

    /** The keys [lo, hi) as their own track. */
    private float[] sample(float t, int lo, int hi) {
        if (hi <= lo) return new float[0];
        Keyframe first = keys.get(lo);
        if (t <= first.t()) return first.v().clone();
        Keyframe last = keys.get(hi - 1);
        if (t >= last.t()) return last.v().clone();
        for (int i = lo + 1; i < hi; i++) {
            Keyframe next = keys.get(i);
            if (t <= next.t()) {
                if (isSpline(i, lo, hi)) return spline(i, t, lo, hi);
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

    /** The segment ending at key i is a spline key of nonzero length inside [lo, hi). */
    private boolean isSpline(int i, int lo, int hi) {
        if (i <= lo || i >= hi) return false;
        Keyframe b = keys.get(i);
        return b.ease() == Ease.SPLINE && b.t() > keys.get(i - 1).t();
    }

    /** The segment ending at key i exists in [lo, hi) and has length. */
    private boolean moves(int i, int lo, int hi) {
        return i > lo && i < hi && keys.get(i).t() > keys.get(i - 1).t();
    }

    /** The spline segment ending at key b, at time t (see the class comment). */
    private float[] spline(int b, float t, int lo, int hi) {
        int a = b - 1;
        Keyframe ka = keys.get(a), kb = keys.get(b);
        double ta = ka.t(), tb = kb.t(), dt = tb - ta;
        float[] va = ka.v(), vb = kb.v();
        int n = Math.min(va.length, vb.length);
        float[] vp = a - 1 >= lo ? keys.get(a - 1).v() : null;
        float[] vn = b + 1 < hi ? keys.get(b + 1).v() : null;
        double tp = a - 1 >= lo ? keys.get(a - 1).t() : 0;
        double tn = b + 1 < hi ? keys.get(b + 1).t() : 0;
        // 0: shared with the spline segment before/after; 1: shared with an eased segment; 2: a natural end.
        int startKind = isSpline(a, lo, hi) ? 0 : moves(a, lo, hi) ? 1 : 2;
        int endKind = isSpline(b + 1, lo, hi) ? 0 : moves(b + 1, lo, hi) ? 1 : 2;
        if (vp != null && vp.length < n) startKind = 2;
        if (vn != null && vn.length < n) endKind = 2;
        float slopeIn = moves(a, lo, hi) ? ka.ease().endSlope() : 0;
        float slopeOut = moves(b + 1, lo, hi) ? keys.get(b + 1).ease().startSlope() : 0;
        double u = Math.max(0, Math.min(1, (t - ta) / dt));
        double u2 = u * u, u3 = u2 * u;
        double h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;
        float[] out = new float[n];
        for (int c = 0; c < n; c++) {
            double secant = ((double) vb[c] - va[c]) / dt;
            double ma = startKind == 0 ? tangent(vp[c], va[c], vb[c], ta - tp, dt)
                    : startKind == 1 ? slopeIn * ((double) va[c] - vp[c]) / (ta - tp) : Double.NaN;
            double mb = endKind == 0 ? tangent(va[c], vb[c], vn[c], dt, tn - tb)
                    : endKind == 1 ? slopeOut * ((double) vn[c] - vb[c]) / (tn - tb) : Double.NaN;
            if (Double.isNaN(ma) && Double.isNaN(mb)) {
                ma = mb = secant;
            } else if (Double.isNaN(ma)) {
                ma = (3 * secant - mb) / 2;
            } else if (Double.isNaN(mb)) {
                mb = (3 * secant - ma) / 2;
            }
            out[c] = (float) (h00 * va[c] + h10 * dt * ma + h01 * vb[c] + h11 * dt * mb);
        }
        return out;
    }

    /** The parabola slope at the middle of three keys {@code h0} and {@code h1} ticks apart (as CameraTrack). */
    private static double tangent(double p0, double p1, double p2, double h0, double h1) {
        double d0 = (p1 - p0) / h0;
        double d1 = (p2 - p1) / h1;
        return (h1 * d0 + h0 * d1) / (h0 + h1);
    }
}
