package com.hogwartsmagic.pensieve.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Path-aware JSON reading helpers shared by the scene parsers. */
final class SceneJson {
    private SceneJson() {
    }

    static String child(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    static String index(String path, int i) {
        return path + "[" + i + "]";
    }

    static JsonObject obj(JsonElement e, String path) throws SceneException {
        if (e == null || !e.isJsonObject()) throw new SceneException(path, "expected an object");
        return e.getAsJsonObject();
    }

    static JsonArray arr(JsonElement e, String path) throws SceneException {
        if (e == null || !e.isJsonArray()) throw new SceneException(path, "expected an array");
        return e.getAsJsonArray();
    }

    /** Rejects any key not in {@code allowed}, naming it. */
    static void checkKeys(JsonObject o, String path, String... allowed) throws SceneException {
        Set<String> ok = new HashSet<>(Arrays.asList(allowed));
        for (String k : o.keySet()) {
            if (!ok.contains(k)) {
                throw new SceneException(child(path, k), "unknown key (expected one of " + List.of(allowed) + ")");
            }
        }
    }

    static JsonElement req(JsonObject o, String key, String path) throws SceneException {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) throw new SceneException(child(path, key), "missing required key");
        return e;
    }

    static boolean has(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && !e.isJsonNull();
    }

    static String str(JsonObject o, String key, String path, String def) throws SceneException {
        if (!has(o, key)) return def;
        return strOf(o.get(key), child(path, key));
    }

    static String reqStr(JsonObject o, String key, String path) throws SceneException {
        return strOf(req(o, key, path), child(path, key));
    }

    static String strOf(JsonElement e, String path) throws SceneException {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new SceneException(path, "expected a string");
        return e.getAsString();
    }

    static boolean bool(JsonObject o, String key, String path, boolean def) throws SceneException {
        if (!has(o, key)) return def;
        JsonElement e = o.get(key);
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) throw new SceneException(child(path, key), "expected true or false");
        return e.getAsBoolean();
    }

    static float num(JsonObject o, String key, String path, float def) throws SceneException {
        if (!has(o, key)) return def;
        return numOf(o.get(key), child(path, key));
    }

    static float reqNum(JsonObject o, String key, String path) throws SceneException {
        return numOf(req(o, key, path), child(path, key));
    }

    static float numOf(JsonElement e, String path) throws SceneException {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new SceneException(path, "expected a number");
        return e.getAsFloat();
    }

    static int integer(JsonObject o, String key, String path, int def) throws SceneException {
        if (!has(o, key)) return def;
        return Math.round(numOf(o.get(key), child(path, key)));
    }

    /** A time: a number of ticks, or a string like "2.5s" meaning seconds. */
    static float timeOf(JsonElement e, String path) throws SceneException {
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            if (p.isNumber()) return p.getAsFloat();
            if (p.isString()) {
                String s = p.getAsString().trim();
                if (s.endsWith("s")) {
                    try {
                        return Float.parseFloat(s.substring(0, s.length() - 1)) * 20f;
                    } catch (NumberFormatException ignored) {
                        // falls through to the error below
                    }
                }
            }
        }
        throw new SceneException(path, "expected a time: ticks as a number, or seconds like \"2.5s\"");
    }

    static float reqTime(JsonObject o, String key, String path) throws SceneException {
        return timeOf(req(o, key, path), child(path, key));
    }

    static float time(JsonObject o, String key, String path, float def) throws SceneException {
        if (!has(o, key)) return def;
        return timeOf(o.get(key), child(path, key));
    }

    static float[] floatsOf(JsonElement e, String path, int n) throws SceneException {
        JsonArray a = arr(e, path);
        if (n > 0 && a.size() != n) throw new SceneException(path, "expected " + n + " numbers");
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = numOf(a.get(i), index(path, i));
        return out;
    }

    static float[] reqFloats(JsonObject o, String key, String path, int n) throws SceneException {
        return floatsOf(req(o, key, path), child(path, key), n);
    }

    static float[] floats(JsonObject o, String key, String path, int n, float[] def) throws SceneException {
        if (!has(o, key)) return def;
        return floatsOf(o.get(key), child(path, key), n);
    }

    static int[] intsOf(JsonElement e, String path, int n) throws SceneException {
        float[] f = floatsOf(e, path, n);
        int[] out = new int[f.length];
        for (int i = 0; i < out.length; i++) out[i] = Math.round(f[i]);
        return out;
    }

    static int[] reqInts(JsonObject o, String key, String path, int n) throws SceneException {
        return intsOf(req(o, key, path), child(path, key), n);
    }

    static Ease ease(JsonObject o, String path) throws SceneException {
        String s = str(o, "ease", path, null);
        Ease e = Ease.parse(s);
        if (e == Ease.SPLINE) {
            throw new SceneException(child(path, "ease"), "\"spline\" is for camera keys and actor path and look keys, not limbs (linear, in, out, in_out)");
        }
        if (e == null) throw new SceneException(child(path, "ease"), "unknown ease \"" + s + "\" (linear, in, out, in_out)");
        return e;
    }

    /** An actor path or look key's ease: the track eases plus {@code "spline"} (see {@link Track}). */
    static Ease splineEase(JsonObject o, String path) throws SceneException {
        String s = str(o, "ease", path, null);
        Ease e = Ease.parse(s);
        if (e == null) {
            throw new SceneException(child(path, "ease"), "unknown ease \"" + s + "\" (linear, in, out, in_out, spline)");
        }
        return e;
    }

    /** A camera key's ease: the track eases plus {@code "spline"}. */
    static Ease cameraEase(JsonObject o, String path) throws SceneException {
        String s = str(o, "ease", path, null);
        Ease e = Ease.parse(s);
        if (e == null) {
            throw new SceneException(child(path, "ease"), "unknown ease \"" + s + "\" (linear, in, out, in_out, spline)");
        }
        return e;
    }
}
