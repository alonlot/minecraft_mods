package com.hogwartsmagic.pensieve.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static com.hogwartsmagic.pensieve.scene.SceneJson.*;

/** Directed camera track: keyed position, look target, fov and roll. */
public final class CameraTrack {
    public static final CameraTrack EMPTY = new CameraTrack(List.of());
    public static final float DEFAULT_FOV = 70f;
    /** A scene's fov is clamped to this range, on parse and on sample: outside it the projection breaks. */
    public static final float MIN_FOV = 10f;
    public static final float MAX_FOV = 150f;

    private final List<CameraKey> keys;

    private CameraTrack(List<CameraKey> keys) {
        List<CameraKey> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.comparingDouble(CameraKey::t));
        this.keys = List.copyOf(sorted);
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public List<CameraKey> keys() {
        return keys;
    }

    /**
     * The keys a cut jumps to after {@code t} and no later than {@code t + ahead}, in order: the shots the camera
     * is about to land in, so the client can have them drawn before it gets there.
     */
    public List<CameraKey> cutsAhead(float t, float ahead) {
        List<CameraKey> out = new ArrayList<>();
        for (CameraKey k : keys) {
            if (k.cut() && k.t() > t && k.t() <= t + ahead) out.add(k);
        }
        return out;
    }

    public static CameraTrack parse(JsonArray a) throws SceneException {
        return parse(a, "camera");
    }

    static CameraTrack parse(JsonArray a, String path) throws SceneException {
        List<CameraKey> keys = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            String p = index(path, i);
            JsonObject o = obj(a.get(i), p);
            checkKeys(o, p, "t", "pos", "look_at", "fov", "roll", "ease", "cut");
            float[] lookAt = null;
            String actor = null;
            if (has(o, "look_at")) {
                JsonElement e = o.get("look_at");
                String lp = child(p, "look_at");
                if (e.isJsonArray()) {
                    lookAt = floatsOf(e, lp, 3);
                } else {
                    String s = strOf(e, lp);
                    if (!s.startsWith("actor:") || s.length() == 6) {
                        throw new SceneException(lp, "expected \"actor:<id>\" or [x,y,z]");
                    }
                    actor = s.substring(6);
                }
            }
            keys.add(new CameraKey(reqTime(o, "t", p), reqFloats(o, "pos", p, 3), lookAt, actor,
                    clampFov(num(o, "fov", p, DEFAULT_FOV)), num(o, "roll", p, 0f), cameraEase(o, p), bool(o, "cut", p, false)));
        }
        return new CameraTrack(keys);
    }

    /**
     * {@code actorEye} maps an actor id to its eye position, or null when it is not available.
     *
     * <p>A segment takes the ease of the key it ends at. A {@link Ease#SPLINE} segment is a cubic Hermite through its
     * two keys (position, look target, fov and roll together) whose velocity at each key is shared with the
     * neighbouring segment, so the camera is C1 in time through every key of a run of spline keys: no stop-start and
     * no kink. At a key between two spline segments the velocity is the slope of the parabola through that key and
     * its two neighbours (Catmull-Rom for evenly spaced keys). At a key shared with an eased segment it is that
     * segment's own speed there (0 next to {@code in_out}). Next to a cut, a hold, or the first or last key, the end
     * is natural (no acceleration at the key).
     */
    public CameraPose sample(float t, Function<String, float[]> actorEye) {
        if (keys.isEmpty()) return new CameraPose(0, 0, 0, 0, 0, 0, DEFAULT_FOV);
        double[] v = raw(t, actorEye);
        double x = v[0], y = v[1], z = v[2];
        double dx = v[3] - x;
        double dy = v[4] - y;
        double dz = v[5] - z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (dx == 0 && dz == 0) ? 0f : (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (horiz == 0 && dy == 0) ? 0f : (float) -Math.toDegrees(Math.atan2(dy, horiz));
        return new CameraPose(x, y, z, yaw, pitch, (float) v[7], (float) v[6]);
    }

    /**
     * The point the camera looks at, scene-relative, at {@code t} (the same blend {@link #sample} aims by): the crowd's
     * heads follow it. Null for an empty track.
     */
    public float[] lookTarget(float t, Function<String, float[]> actorEye) {
        if (keys.isEmpty()) return null;
        double[] v = raw(t, actorEye);
        return new float[]{(float) v[3], (float) v[4], (float) v[5]};
    }

    /** Position, look target, fov (clamped) and roll at {@code t}, in that order (see {@link #sample}). */
    private double[] raw(float t, Function<String, float[]> actorEye) {
        CameraKey first = keys.get(0);
        CameraKey from = first;
        CameraKey to = first;
        float k = 0;
        int seg = -1;
        if (t >= keys.get(keys.size() - 1).t()) {
            from = to = keys.get(keys.size() - 1);
        } else if (t > first.t()) {
            for (int i = 1; i < keys.size(); i++) {
                CameraKey next = keys.get(i);
                if (t < next.t()) {
                    from = keys.get(i - 1);
                    to = next;
                    float span = next.t() - from.t();
                    if (isSpline(i)) {
                        seg = i;
                    } else {
                        k = to.cut() || span <= 0 ? 0 : to.ease().apply((t - from.t()) / span);
                    }
                    break;
                }
            }
        }
        double x, y, z, lx, ly, lz;
        float fov, roll;
        if (seg > 0) {
            double[] v = spline(seg, t, actorEye);
            x = v[0];
            y = v[1];
            z = v[2];
            lx = v[3];
            ly = v[4];
            lz = v[5];
            fov = clampFov((float) v[6]);
            roll = (float) v[7];
        } else {
            float[] fp = from.pos();
            float[] tp = to.pos();
            x = fp[0] + (tp[0] - fp[0]) * k;
            y = fp[1] + (tp[1] - fp[1]) * k;
            z = fp[2] + (tp[2] - fp[2]) * k;
            float[] fl = target(from, actorEye);
            float[] tl = target(to, actorEye);
            lx = fl[0] + (tl[0] - fl[0]) * k;
            ly = fl[1] + (tl[1] - fl[1]) * k;
            lz = fl[2] + (tl[2] - fl[2]) * k;
            fov = clampFov(from.fov() + (to.fov() - from.fov()) * k);
            roll = from.roll() + (to.roll() - from.roll()) * k;
        }
        return new double[]{x, y, z, lx, ly, lz, fov, roll};
    }

    /** Whether the segment ending at key {@code i} is a spline segment: a spline key, not a cut, of nonzero length. */
    private boolean isSpline(int i) {
        if (i <= 0 || i >= keys.size()) return false;
        CameraKey b = keys.get(i);
        return b.ease() == Ease.SPLINE && !b.cut() && b.t() > keys.get(i - 1).t();
    }

    /** Whether the segment ending at key {@code i} moves the camera at all: it exists, is not a cut and has length. */
    private boolean moves(int i) {
        if (i <= 0 || i >= keys.size()) return false;
        CameraKey b = keys.get(i);
        return !b.cut() && b.t() > keys.get(i - 1).t();
    }

    /** Position, look target, fov and roll of key {@code i}, in that order (8 values). */
    private double[] values(int i, Function<String, float[]> actorEye) {
        CameraKey key = keys.get(i);
        float[] p = key.pos();
        float[] l = target(key, actorEye);
        return new double[]{p[0], p[1], p[2], l[0], l[1], l[2], key.fov(), key.roll()};
    }

    /** The spline segment ending at key {@code b} (see {@link #sample}), at time {@code t}. */
    private double[] spline(int b, float t, Function<String, float[]> actorEye) {
        int a = b - 1;
        double ta = keys.get(a).t();
        double tb = keys.get(b).t();
        double dt = tb - ta;
        double[] va = values(a, actorEye);
        double[] vb = values(b, actorEye);
        double[] vp = a - 1 >= 0 ? values(a - 1, actorEye) : null;
        double[] vn = b + 1 < keys.size() ? values(b + 1, actorEye) : null;
        double tp = a - 1 >= 0 ? keys.get(a - 1).t() : 0;
        double tn = b + 1 < keys.size() ? keys.get(b + 1).t() : 0;
        // 0: shared with the spline segment before/after; 1: shared with an eased segment; 2: a natural end.
        int startKind = isSpline(a) ? 0 : moves(a) ? 1 : 2;
        int endKind = isSpline(b + 1) ? 0 : moves(b + 1) ? 1 : 2;
        float slopeIn = moves(a) ? keys.get(a).ease().endSlope() : 0;
        float slopeOut = moves(b + 1) ? keys.get(b + 1).ease().startSlope() : 0;
        double u = Math.max(0, Math.min(1, (t - ta) / dt));
        double u2 = u * u;
        double u3 = u2 * u;
        double h00 = 2 * u3 - 3 * u2 + 1;
        double h10 = u3 - 2 * u2 + u;
        double h01 = -2 * u3 + 3 * u2;
        double h11 = u3 - u2;
        double[] out = new double[va.length];
        for (int c = 0; c < va.length; c++) {
            double secant = (vb[c] - va[c]) / dt;
            double ma = startKind == 0 ? tangent(vp[c], va[c], vb[c], ta - tp, dt)
                    : startKind == 1 ? slopeIn * (va[c] - vp[c]) / (ta - tp) : Double.NaN;
            double mb = endKind == 0 ? tangent(va[c], vb[c], vn[c], dt, tn - tb)
                    : endKind == 1 ? slopeOut * (vn[c] - vb[c]) / (tn - tb) : Double.NaN;
            if (Double.isNaN(ma) && Double.isNaN(mb)) {
                ma = mb = secant;
            } else if (Double.isNaN(ma)) {
                ma = naturalEnd(secant, mb);
            } else if (Double.isNaN(mb)) {
                mb = naturalEnd(secant, ma);
            }
            out[c] = h00 * va[c] + h10 * dt * ma + h01 * vb[c] + h11 * dt * mb;
        }
        return out;
    }

    /**
     * The velocity at a key between two segments: the slope there of the parabola through the key and its two
     * neighbours, {@code h0} and {@code h1} ticks away (exact for a quadratic; Catmull-Rom when they are equal).
     */
    private static double tangent(double p0, double p1, double p2, double h0, double h1) {
        double d0 = (p1 - p0) / h0;
        double d1 = (p2 - p1) / h1;
        return (h1 * d0 + h0 * d1) / (h0 + h1);
    }

    /** The velocity at a free end of a cubic whose other end has velocity {@code other}: no acceleration at the end. */
    private static double naturalEnd(double secant, double other) {
        return (3 * secant - other) / 2;
    }

    static float clampFov(float fov) {
        if (Float.isNaN(fov)) return DEFAULT_FOV;
        return Math.max(MIN_FOV, Math.min(MAX_FOV, fov));
    }

    /** The point a key looks at; straight ahead (+Z) when it names nothing usable. */
    private static float[] target(CameraKey key, Function<String, float[]> actorEye) {
        if (key.lookAtActor() != null) {
            float[] eye = actorEye.apply(key.lookAtActor());
            if (eye != null) return eye;
        } else if (key.lookAt() != null) {
            return key.lookAt();
        }
        float[] p = key.pos();
        return new float[]{p[0], p[1], p[2] + 1};
    }
}
