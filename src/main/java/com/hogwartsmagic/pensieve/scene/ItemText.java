package com.hogwartsmagic.pensieve.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Item placeholders in a scene. {@code {reject_wand_1..3}} are three other wands (see {@link #rejects}). {@code {viewer_wand}} may stand wherever an item id is accepted (an actor's
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
    /** The three wands the wandmaker tries and rejects, filled like {@link #VIEWER_WAND}. */
    public static final List<String> REJECT_WANDS = List.of("{reject_wand_1}", "{reject_wand_2}", "{reject_wand_3}");
    /** The start-context key of reject {@code i} (1..3), the id the placeholder becomes. */
    public static String rejectKey(int i) {
        return "reject_wand_" + i;
    }
    /** Every wand a reject may be, in preference order: the wandmaker's sold wands first, then the rest. */
    private static final List<String> REJECT_POOL = List.of("hogwartsmagic:unicorn_core_wand",
            "hogwartsmagic:dragon_core_wand", "hogwartsmagic:phoenix_core_wand", "hogwartsmagic:elder_wand",
            "hogwartsmagic:harry_potter_wand");
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
        if (isWand(purchased)) return purchased;
        if (held != null) {
            for (String id : held) {
                if (isWand(id)) return id;
            }
        }
        return DEFAULT_WAND;
    }

    /**
     * Three distinct wands, none of them {@code viewerWand}: the first of {@link #REJECT_POOL} that differ from it,
     * so the same viewer wand always gets the same three.
     */
    public static List<String> rejects(String viewerWand) {
        List<String> out = new ArrayList<>(3);
        for (String id : REJECT_POOL) {
            if (!id.equals(viewerWand) && out.size() < 3) out.add(id);
        }
        return out;
    }

    /**
     * The start context with {@link #CONTEXT_WAND} resolved by {@link #viewerWand} and the three reject keys
     * filled from it ({@link #rejects}); other keys are kept.
     */
    public static Map<String, String> resolve(Map<String, String> context, List<String> held) {
        HashMap<String, String> out = new HashMap<>(context == null ? Map.of() : context);
        String wand = viewerWand(out.get(CONTEXT_WAND), held);
        out.put(CONTEXT_WAND, wand);
        List<String> rejects = rejects(wand);
        for (int i = 0; i < 3; i++) out.put(rejectKey(i + 1), rejects.get(i));
        return Map.copyOf(out);
    }

    /**
     * {@code json} with every item value that is exactly {@link #VIEWER_WAND} replaced by the context's wand (or
     * {@link #DEFAULT_WAND} when the context has no item id for it). Only item values change: a word or a name that
     * reads "{viewer_wand}" stays as written. A scene with no placeholder comes back as the same string.
     */
    public static String fill(String json, Map<String, String> context) {
        if (json == null || !(json.contains(VIEWER_WAND) || json.contains("{reject_wand_"))) return json;
        String wand = context == null ? null : context.get(CONTEXT_WAND);
        if (!isWand(wand)) wand = DEFAULT_WAND;
        Map<String, String> values = new HashMap<>();
        values.put(VIEWER_WAND, wand);
        List<String> fallback = rejects(wand);
        for (int i = 0; i < 3; i++) {
            String r = context == null ? null : context.get(rejectKey(i + 1));
            values.put(REJECT_WANDS.get(i), isWand(r) && !r.equals(wand) ? r : fallback.get(i));
        }
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
                for (String slot : items.keySet()) changed |= swap(items, slot, values);
            }
            if (actor.get("actions") instanceof JsonArray actions) {
                for (JsonElement c : actions) {
                    if (c.isJsonObject()) changed |= swap(c.getAsJsonObject(), "item", values);
                }
            }
        }
        return changed ? root.toString() : json;
    }

    private static boolean swap(JsonObject o, String key, Map<String, String> values) {
        JsonElement v = o.get(key);
        if (v instanceof JsonPrimitive p && p.isString() && values.containsKey(p.getAsString())) {
            o.addProperty(key, values.get(p.getAsString()));
            return true;
        }
        return false;
    }
}
