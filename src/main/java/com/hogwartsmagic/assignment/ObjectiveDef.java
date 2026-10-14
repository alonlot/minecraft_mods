package com.hogwartsmagic.assignment;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * One line of an assignment. {@code targets} holds ids ("minecraft:zombie") and tags ("#minecraft:logs");
 * an empty list matches anything. {@code params} holds the type's extra conditions, as strings.
 */
public record ObjectiveDef(ObjectiveType type, List<String> targets, int count, Map<String, String> params) {
    public ObjectiveDef {
        targets = List.copyOf(targets);
        params = Map.copyOf(params);
    }

    /**
     * True when {@code id} is one of the targets, or is in one of the target tags. {@code inTag} answers
     * "is this thing in the tag named by the string" (the string has no leading '#').
     */
    public boolean matches(String id, Predicate<String> inTag) {
        if (this.targets.isEmpty()) {
            return true;
        }
        for (String target : this.targets) {
            if (target.startsWith("#")) {
                if (inTag != null && inTag.test(target.substring(1))) {
                    return true;
                }
            } else if (target.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean matches(String id) {
        return matches(id, null);
    }

    public String param(String key) {
        return this.params.get(key);
    }

    public boolean flag(String key) {
        return "true".equals(this.params.get(key));
    }

    /** The parameter as an int, or {@code fallback} when it is missing or not a number. */
    public int intParam(String key, int fallback) {
        String raw = this.params.get(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
