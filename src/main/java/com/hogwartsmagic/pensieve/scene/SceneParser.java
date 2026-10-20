package com.hogwartsmagic.pensieve.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hogwartsmagic.pensieve.scene.SceneJson.*;

/** Parses a scene JSON object into a {@link SceneDef}. Unknown keys are errors, with the JSON path named. */
public final class SceneParser {
    private SceneParser() {
    }

    public static SceneDef parse(String id, JsonObject json) throws SceneException {
        checkKeys(json, "", "title", "length", "radius", "set", "poses", "actors", "events", "words", "camera", "end_action",
                "fog_far", "next");
        String title = str(json, "title", "", id);
        int length = Math.round(reqTime(json, "length", ""));
        int radius = integer(json, "radius", "", 40);
        SceneDef.SetDef set = has(json, "set") ? parseSet(obj(json.get("set"), "set"), "set") : emptySet();
        Map<String, Map<String, float[]>> poses = parsePoses(json);

        List<SceneDef.ActorDef> actors = new ArrayList<>();
        if (has(json, "actors")) {
            JsonArray a = arr(json.get("actors"), "actors");
            for (int i = 0; i < a.size(); i++) actors.add(parseActor(a.get(i), index("actors", i)));
        }
        List<SceneDef.EventDef> events = new ArrayList<>();
        if (has(json, "events")) {
            JsonArray a = arr(json.get("events"), "events");
            for (int i = 0; i < a.size(); i++) events.add(parseEvent(a.get(i), index("events", i)));
        }
        List<SceneDef.WordDef> words = new ArrayList<>();
        if (has(json, "words")) {
            JsonArray a = arr(json.get("words"), "words");
            for (int i = 0; i < a.size(); i++) words.add(parseWord(a.get(i), index("words", i)));
        }
        String endAction = str(json, "end_action", "", null);
        int fogFar = integer(json, "fog_far", "", 0);
        String next = str(json, "next", "", null);
        CameraTrack camera = has(json, "camera") ? CameraTrack.parse(arr(json.get("camera"), "camera"), "camera") : CameraTrack.EMPTY;
        return new SceneDef(id, title, length, radius, set, poses, actors, events, words, camera, endAction, fogFar, next);
    }

    private static SceneDef.SetDef emptySet() {
        return new SceneDef.SetDef(List.of(), List.of(), List.of(), List.of(), 18000L, 60);
    }

    private static SceneDef.SetDef parseSet(JsonObject o, String path) throws SceneException {
        checkKeys(o, path, "templates", "schematics", "fills", "disks", "features", "time_of_day", "build_ticks");
        List<SceneDef.TemplateRef> templates = new ArrayList<>();
        for (Map.Entry<String, JsonObject> en : each(o, "templates", path)) {
            JsonObject t = en.getValue();
            String p = en.getKey();
            checkKeys(t, p, "id", "at");
            templates.add(new SceneDef.TemplateRef(reqStr(t, "id", p), reqInts(t, "at", p, 3)));
        }
        List<SceneDef.SchematicRef> schematics = new ArrayList<>();
        for (Map.Entry<String, JsonObject> en : each(o, "schematics", path)) {
            JsonObject t = en.getValue();
            String p = en.getKey();
            checkKeys(t, p, "id", "at", "rotation");
            schematics.add(new SceneDef.SchematicRef(reqStr(t, "id", p), reqInts(t, "at", p, 3),
                    integer(t, "rotation", p, 0)));
        }
        List<SceneDef.Fill> fills = new ArrayList<>();
        for (Map.Entry<String, JsonObject> en : each(o, "fills", path)) {
            JsonObject t = en.getValue();
            String p = en.getKey();
            checkKeys(t, p, "block", "from", "to");
            fills.add(new SceneDef.Fill(reqStr(t, "block", p), reqInts(t, "from", p, 3), reqInts(t, "to", p, 3)));
        }
        List<SceneDef.Disk> disks = new ArrayList<>();
        for (Map.Entry<String, JsonObject> en : each(o, "disks", path)) {
            JsonObject t = en.getValue();
            String p = en.getKey();
            checkKeys(t, p, "block", "center", "radius");
            disks.add(new SceneDef.Disk(reqStr(t, "block", p), reqInts(t, "center", p, 3), Math.round(reqNum(t, "radius", p))));
        }
        List<SceneDef.FeatureRef> features = new ArrayList<>();
        for (Map.Entry<String, JsonObject> en : each(o, "features", path)) {
            JsonObject t = en.getValue();
            String p = en.getKey();
            checkKeys(t, p, "id", "at");
            features.add(new SceneDef.FeatureRef(reqStr(t, "id", p), reqInts(t, "at", p, 3)));
        }
        long timeOfDay = Math.round(num(o, "time_of_day", path, 18000f));
        int buildTicks = integer(o, "build_ticks", path, 60);
        return new SceneDef.SetDef(templates, schematics, fills, disks, features, timeOfDay, buildTicks);
    }

    /** Elements of an optional array of objects, paired with their indexed paths. */
    private static List<Map.Entry<String, JsonObject>> each(JsonObject o, String key, String path) throws SceneException {
        List<Map.Entry<String, JsonObject>> out = new ArrayList<>();
        if (!has(o, key)) return out;
        String base = child(path, key);
        JsonArray a = arr(o.get(key), base);
        for (int i = 0; i < a.size(); i++) {
            String p = index(base, i);
            out.add(Map.entry(p, obj(a.get(i), p)));
        }
        return out;
    }

    private static Map<String, Map<String, float[]>> parsePoses(JsonObject json) throws SceneException {
        Map<String, Map<String, float[]>> out = new LinkedHashMap<>();
        if (!has(json, "poses")) return out;
        JsonObject poses = obj(json.get("poses"), "poses");
        for (String name : poses.keySet()) {
            String p = child("poses", name);
            JsonObject parts = obj(poses.get(name), p);
            Map<String, float[]> m = new LinkedHashMap<>();
            for (String part : parts.keySet()) m.put(part, floatsOf(parts.get(part), child(p, part), 3));
            out.put(name, m);
        }
        return out;
    }

    private static SceneDef.ActorDef parseActor(JsonElement e, String path) throws SceneException {
        JsonObject o = obj(e, path);
        checkKeys(o, path, "id", "type", "skin", "name", "items", "path", "look", "limbs", "poses", "anims", "actions", "scale", "life",
                "block");
        String id = reqStr(o, "id", path);
        String type = reqStr(o, "type", path);
        SceneDef.SkinRef skin = null;
        if (has(o, "skin")) {
            String sp = child(path, "skin");
            JsonObject s = obj(o.get("skin"), sp);
            checkKeys(s, sp, "texture", "username", "slim", "viewer");
            skin = new SceneDef.SkinRef(str(s, "texture", sp, null), str(s, "username", sp, null), bool(s, "slim", sp, false),
                    bool(s, "viewer", sp, false));
        }
        String name = str(o, "name", path, null);
        Map<String, String> items = new LinkedHashMap<>();
        if (has(o, "items")) {
            String ip = child(path, "items");
            JsonObject io = obj(o.get("items"), ip);
            for (String slot : io.keySet()) items.put(slot, strOf(io.get(slot), child(ip, slot)));
        }
        Track pathTrack = has(o, "path") ? keyed(o.get("path"), child(path, "path"), "pos") : Track.EMPTY;
        Track look = has(o, "look") ? parseLook(o.get("look"), child(path, "look")) : Track.EMPTY;
        Map<String, PartTrack> limbs = new LinkedHashMap<>();
        if (has(o, "limbs")) {
            String lp = child(path, "limbs");
            JsonObject lo = obj(o.get("limbs"), lp);
            for (String part : lo.keySet()) {
                String pp = child(lp, part);
                JsonObject po = obj(lo.get(part), pp);
                checkKeys(po, pp, "rot", "offset", "mode");
                String mode = str(po, "mode", pp, "add");
                if (!"add".equals(mode) && !"set".equals(mode)) {
                    throw new SceneException(child(pp, "mode"), "must be \"add\" or \"set\", got \"" + mode + "\"");
                }
                Track rot = has(po, "rot") ? keyed(po.get("rot"), child(pp, "rot"), "v") : Track.EMPTY;
                Track off = has(po, "offset") ? keyed(po.get("offset"), child(pp, "offset"), "v") : Track.EMPTY;
                limbs.put(part, new PartTrack(rot, off, "set".equals(mode)));
            }
        }
        List<SceneDef.Cue> poses = cues(o, "poses", path);
        List<SceneDef.Cue> anims = cues(o, "anims", path);
        List<SceneDef.ActionCue> actions = new ArrayList<>();
        for (Map.Entry<String, JsonObject> en : each(o, "actions", path)) actions.add(parseAction(en.getValue(), en.getKey()));
        return new SceneDef.ActorDef(id, type, skin, name, items, pathTrack, look, limbs, poses, anims, actions,
                num(o, "scale", path, 1f), bool(o, "life", path, true), str(o, "block", path, null));
    }

    /** A keyframe array of [x,y,z] vectors stored under {@code vecKey}. */
    private static Track keyed(JsonElement e, String path, String vecKey) throws SceneException {
        JsonArray a = arr(e, path);
        List<Keyframe> keys = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            String p = index(path, i);
            JsonObject k = obj(a.get(i), p);
            checkKeys(k, p, "t", vecKey, "ease");
            keys.add(new Keyframe(reqTime(k, "t", p), reqFloats(k, vecKey, p, 3), ease(k, p)));
        }
        return new Track(keys);
    }

    /** Look keys are [yaw, pitch]; when any key gives a {@code roll} every key is [yaw, pitch, roll] (roll default 0). */
    private static Track parseLook(JsonElement e, String path) throws SceneException {
        JsonArray a = arr(e, path);
        boolean rolls = false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).isJsonObject() && has(a.get(i).getAsJsonObject(), "roll")) rolls = true;
        }
        List<Keyframe> keys = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            String p = index(path, i);
            JsonObject k = obj(a.get(i), p);
            checkKeys(k, p, "t", "yaw", "pitch", "roll", "ease");
            float yaw = reqNum(k, "yaw", p), pitch = num(k, "pitch", p, 0f);
            float[] v = rolls ? new float[]{yaw, pitch, num(k, "roll", p, 0f)} : new float[]{yaw, pitch};
            keys.add(new Keyframe(reqTime(k, "t", p), v, ease(k, p)));
        }
        return new Track(keys);
    }

    private static List<SceneDef.Cue> cues(JsonObject o, String key, String path) throws SceneException {
        List<SceneDef.Cue> out = new ArrayList<>();
        for (Map.Entry<String, JsonObject> en : each(o, key, path)) {
            JsonObject c = en.getValue();
            String p = en.getKey();
            checkKeys(c, p, "t", "name", "blend");
            out.add(new SceneDef.Cue(reqTime(c, "t", p), reqStr(c, "name", p), time(c, "blend", p, 0f)));
        }
        return out;
    }

    private static SceneDef.ActionCue parseAction(JsonObject c, String p) throws SceneException {
        checkKeys(c, p, "t", "do", "spell", "target", "item", "slot");
        SceneDef.Target target = has(c, "target") ? parseTarget(c.get("target"), child(p, "target")) : null;
        return new SceneDef.ActionCue(reqTime(c, "t", p), reqStr(c, "do", p), str(c, "spell", p, null), target, str(c, "item", p, null), str(c, "slot", p, null));
    }

    /** Targets are "actor:ID" or an [x,y,z] point. */
    private static SceneDef.Target parseTarget(JsonElement e, String path) throws SceneException {
        if (e.isJsonArray()) return new SceneDef.Target(null, floatsOf(e, path, 3));
        String s = strOf(e, path);
        if (!s.startsWith("actor:") || s.length() == 6) throw new SceneException(path, "expected \"actor:<id>\" or [x,y,z]");
        return new SceneDef.Target(s.substring(6), null);
    }

    private static SceneDef.EventDef parseEvent(JsonElement e, String path) throws SceneException {
        JsonObject o = obj(e, path);
        String kind = has(o, "kind") && o.get("kind").isJsonPrimitive() ? o.get("kind").getAsString() : "";
        if ("music".equals(kind) || "voice".equals(kind) || "sfx".equals(kind)) {
            // Soundtrack and spoken-line cues are non-positional and unscaled: no at, pitch, count, spread or duration.
            checkKeys(o, path, "t", "kind", "id", "volume");
        } else {
            checkKeys(o, path, "t", "kind", "id", "at", "volume", "pitch", "count", "spread", "duration", "yaw",
                    "style", "intensity");
        }
        float[] at = floats(o, "at", path, 3, null);
        return new SceneDef.EventDef(reqTime(o, "t", path), reqStr(o, "kind", path), str(o, "id", path, null), at,
                num(o, "volume", path, 1f), num(o, "pitch", path, 1f), integer(o, "count", path, -1),
                floats(o, "spread", path, 3, null), Math.round(time(o, "duration", path, 0f)),
                num(o, "yaw", path, Float.NaN), str(o, "style", path, null), num(o, "intensity", path, Float.NaN));
    }

    private static SceneDef.WordDef parseWord(JsonElement e, String path) throws SceneException {
        JsonObject o = obj(e, path);
        checkKeys(o, path, "t", "by", "text", "narration", "duration", "size", "subtitle", "speaker");
        return new SceneDef.WordDef(reqTime(o, "t", path), str(o, "by", path, null), reqStr(o, "text", path),
                bool(o, "narration", path, false), Math.round(time(o, "duration", path, 80f)), num(o, "size", path, 1f),
                bool(o, "subtitle", path, false), str(o, "speaker", path, null));
    }
}
