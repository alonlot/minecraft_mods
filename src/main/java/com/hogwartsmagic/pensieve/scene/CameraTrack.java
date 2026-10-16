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
                    clampFov(num(o, "fov", p, DEFAULT_FOV)), num(o, "roll", p, 0f), ease(o, p), bool(o, "cut", p, false)));
        }
        return new CameraTrack(keys);
    }

    /** {@code actorEye} maps an actor id to its eye position, or null when it is not available. */
    public CameraPose sample(float t, Function<String, float[]> actorEye) {
        if (keys.isEmpty()) return new CameraPose(0, 0, 0, 0, 0, 0, DEFAULT_FOV);
        CameraKey first = keys.get(0);
        CameraKey from = first;
        CameraKey to = first;
        float k = 0;
        if (t >= keys.get(keys.size() - 1).t()) {
            from = to = keys.get(keys.size() - 1);
        } else if (t > first.t()) {
            for (int i = 1; i < keys.size(); i++) {
                CameraKey next = keys.get(i);
                if (t < next.t()) {
                    from = keys.get(i - 1);
                    to = next;
                    float span = next.t() - from.t();
                    k = to.cut() || span <= 0 ? 0 : to.ease().apply((t - from.t()) / span);
                    break;
                }
            }
        }
        float[] fp = from.pos();
        float[] tp = to.pos();
        double x = fp[0] + (tp[0] - fp[0]) * k;
        double y = fp[1] + (tp[1] - fp[1]) * k;
        double z = fp[2] + (tp[2] - fp[2]) * k;
        float[] fl = target(from, actorEye);
        float[] tl = target(to, actorEye);
        double dx = fl[0] + (tl[0] - fl[0]) * k - x;
        double dy = fl[1] + (tl[1] - fl[1]) * k - y;
        double dz = fl[2] + (tl[2] - fl[2]) * k - z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (dx == 0 && dz == 0) ? 0f : (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (horiz == 0 && dy == 0) ? 0f : (float) -Math.toDegrees(Math.atan2(dy, horiz));
        float fov = clampFov(from.fov() + (to.fov() - from.fov()) * k);
        float roll = from.roll() + (to.roll() - from.roll()) * k;
        return new CameraPose(x, y, z, yaw, pitch, roll, fov);
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
