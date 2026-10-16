package com.hogwartsmagic.assignment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads one {@code data/<ns>/assignments/<spell>.json} file:
 * <pre>
 * {
 *   "teacher": "flitwick",
 *   "dark": false,
 *   "objectives": [
 *     {"type": "place", "target": ["minecraft:torch", "minecraft:wall_torch"], "count": 12, "no_sky": true}
 *   ]
 * }
 * </pre>
 * "target" is a string or a list; every key other than type, target and count is a parameter. A bad
 * file throws {@link IllegalArgumentException} with the reason, so the loader can skip and log it.
 */
public final class AssignmentParser {
    public static final int MAX_OBJECTIVES = 6;
    public static final int MAX_COUNT = 100000;

    private AssignmentParser() {
    }

    public static AssignmentDef parse(String spellId, JsonObject json) {
        String teacher = json.has("teacher") ? json.get("teacher").getAsString().trim() : "";
        if (teacher.isEmpty()) {
            throw new IllegalArgumentException("missing teacher");
        }
        boolean dark = json.has("dark") && json.get("dark").getAsBoolean();
        if (!json.has("objectives") || !json.get("objectives").isJsonArray()) {
            throw new IllegalArgumentException("missing objectives list");
        }
        JsonArray array = json.getAsJsonArray("objectives");
        if (array.isEmpty() || array.size() > MAX_OBJECTIVES) {
            throw new IllegalArgumentException("needs 1 to " + MAX_OBJECTIVES + " objectives, has " + array.size());
        }
        List<ObjectiveDef> objectives = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            objectives.add(parseObjective(array.get(i), i));
        }
        return new AssignmentDef(spellId, teacher, dark, objectives);
    }

    private static ObjectiveDef parseObjective(JsonElement element, int index) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("objective " + index + " is not an object");
        }
        JsonObject json = element.getAsJsonObject();
        String typeId = json.has("type") ? json.get("type").getAsString() : null;
        ObjectiveType type = ObjectiveType.byId(typeId);
        if (type == null) {
            throw new IllegalArgumentException("objective " + index + " has unknown type " + typeId);
        }
        List<String> targets = new ArrayList<>();
        if (json.has("target")) {
            JsonElement target = json.get("target");
            if (target.isJsonArray()) {
                for (JsonElement entry : target.getAsJsonArray()) {
                    targets.add(entry.getAsString().trim());
                }
            } else {
                targets.add(target.getAsString().trim());
            }
        }
        if ((type == ObjectiveType.CAST || type == ObjectiveType.KNOW_SPELL || type == ObjectiveType.DELIVER
                || type == ObjectiveType.ADVANCEMENT || type == ObjectiveType.VISIT) && targets.isEmpty()) {
            throw new IllegalArgumentException("objective " + index + " (" + type.id() + ") needs a target");
        }
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("objective " + index + " count must be 1 to " + MAX_COUNT);
        }
        Map<String, String> params = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            if (key.equals("type") || key.equals("target") || key.equals("count")) {
                continue;
            }
            if (!entry.getValue().isJsonPrimitive()) {
                throw new IllegalArgumentException("objective " + index + " parameter " + key + " must be a plain value");
            }
            params.put(key, entry.getValue().getAsString());
        }
        return new ObjectiveDef(type, targets, count, params);
    }
}
