package com.hogwartsmagic.pensieve.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Item placeholders in a scene. {@code {viewer_wand}} may stand wherever an item id is accepted (an actor's
 * {@code items}, and the {@code item} of {@code hold}, {@code equip} and {@code use_item}); the server fills it in
 * the scene JSON before it is sent, from the start context ({@link #CONTEXT_WAND}: the wand just bought), so every
 * client gets the same plain id and never guesses. Pure: the server passes in what the player holds.
 */
public final class ItemText {
    /** The placeholder, written as the whole item value. */
    public static final String VIEWER_WAND = "{viewer_wand}";
    /** The start-context key for the viewer's wand (an item id). */
    public static final String CONTEXT_WAND = "viewer_wand";
    /** The wand used when there is no purchase and no wand in hand. */
    public static final String DEFAULT_WAND = "hogwartsmagic:harry_potter_wand";
    /** A namespaced item id: nothing else is ever written into the JSON. */
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private ItemText() {
    }

    /** True for an item id whose path ends in {@code _wand} (every wand item in the mod). */
    public static boolean isWand(String itemId) {
        return itemId != null && ITEM_ID.matcher(itemId).matches() && itemId.endsWith("_wand");
    }

    /**
     * The wand the memory shows: the purchase when it is an item id, else the first wand in {@code held} (the main
     * hand, then the off hand; nulls and non-wands skipped), else {@link #DEFAULT_WAND}.
     */
    public static String viewerWand(String purchased, List<String> held) {
        if (purchased != null && ITEM_ID.matcher(purchased).matches()) return purchased;
        if (held != null) {
            for (String id : held) {
                if (isWand(id)) return id;
            }
        }
        return DEFAULT_WAND;
    }

    /** The start context with {@link #CONTEXT_WAND} resolved by {@link #viewerWand}; other keys are kept. */
    public static Map<String, String> resolve(Map<String, String> context, List<String> held) {
        java.util.HashMap<String, String> out = new java.util.HashMap<>(context == null ? Map.of() : context);
        out.put(CONTEXT_WAND, viewerWand(out.get(CONTEXT_WAND), held));
        return Map.copyOf(out);
    }

    /**
     * {@code json} with every item value that is exactly {@link #VIEWER_WAND} replaced by the context's wand (or
     * {@link #DEFAULT_WAND} when the context has no item id for it). Only item values change: a word or a name that
     * reads "{viewer_wand}" stays as written. A scene with no placeholder comes back as the same string.
     */
    public static String fill(String json, Map<String, String> context) {
        if (json == null || !json.contains(VIEWER_WAND)) return json;
        String wand = context == null ? null : context.get(CONTEXT_WAND);
        if (wand == null || !ITEM_ID.matcher(wand).matches()) wand = DEFAULT_WAND;
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            return json;      // not JSON: the parser will say so when the scene is read
        }
        if (!root.isJsonObject() || !(root.getAsJsonObject().get("actors") instanceof JsonArray actors)) return json;
        boolean changed = false;
        for (JsonElement a : actors) {
            if (!a.isJsonObject()) continue;
            JsonObject actor = a.getAsJsonObject();
            if (actor.get("items") instanceof JsonObject items) {
                for (String slot : items.keySet()) changed |= swap(items, slot, wand);
            }
            if (actor.get("actions") instanceof JsonArray actions) {
                for (JsonElement c : actions) {
                    if (c.isJsonObject()) changed |= swap(c.getAsJsonObject(), "item", wand);
                }
            }
        }
        return changed ? root.toString() : json;
    }

    private static boolean swap(JsonObject o, String key, String wand) {
        JsonElement v = o.get(key);
        if (v instanceof JsonPrimitive p && p.isString() && VIEWER_WAND.equals(p.getAsString())) {
            o.addProperty(key, wand);
            return true;
        }
        return false;
    }
}
