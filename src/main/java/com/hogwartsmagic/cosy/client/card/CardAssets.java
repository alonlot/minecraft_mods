package com.hogwartsmagic.cosy.client.card;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Where a living card finds its art. The asset contract (all under {@code assets/hogwartsmagic/}):
 * <pre>
 * textures/card/frame_common.png, frame_rare.png, frame_legendary.png, back.png   320x448, window punched out
 * textures/card/box.png                                                           the reveal box, RGBA
 * textures/card/&lt;key&gt;/base.png, empty.png, figure.png, blink.png, look_l.png, look_r.png   256x384
 * textures/card/&lt;key&gt;/clips/exit/0000.png ...                                   legendaries only
 * cards/&lt;key&gt;.json   {"eyes":[cx,cy,rx,ry], "face":[cx,cy,rx,ry], "exit_clip":{"frames":N,"fps":12} | null}
 * </pre>
 * A card whose {@code base}, {@code empty} and {@code figure} are not all there falls back to the generated
 * placeholder ({@code textures/card/_placeholder/}), tinted by the draw, so a card with no art draws a silhouette on a
 * plate instead of the purple missing texture and never logs a missing resource.
 *
 * <p>Looking things up goes through a {@link Source} so JUnit can run it without a resource manager.
 */
public final class CardAssets {
    public static final String NAMESPACE = "hogwartsmagic";
    public static final String PLACEHOLDER_KEY = "_placeholder";

    /** Where resources come from: the client resource manager, or a fake in a test. */
    public interface Source {
        boolean exists(ResourceLocation location);

        /** The text of a resource, or null when it is missing or unreadable. */
        @Nullable
        String readText(ResourceLocation location);
    }

    /** An ellipse in texture pixels: centre and radii. */
    public record Box(float cx, float cy, float rx, float ry) {
        public boolean valid() {
            return this.rx > 0.0F && this.ry > 0.0F;
        }

        public float left() {
            return this.cx - this.rx;
        }

        public float top() {
            return this.cy - this.ry;
        }

        public float right() {
            return this.cx + this.rx;
        }

        public float bottom() {
            return this.cy + this.ry;
        }
    }

    /** A legendary's exit clip. */
    public record Clip(int frames, int fps) {
    }

    /** The {@code cards/<key>.json} values. {@code exit} is null when the card has no clip. */
    public record Meta(Box eyes, Box face, @Nullable Clip exit) {
    }

    /** One card's art. {@code placeholder} is true when this is the generated stand-in. */
    public record Assets(String key, boolean placeholder, ResourceLocation base, ResourceLocation empty,
                         ResourceLocation figure, ResourceLocation blink, ResourceLocation lookL,
                         ResourceLocation lookR, Meta meta) {
        /** Frames in the exit clip, 0 when there is none. */
        public int clipFrames() {
            return this.meta.exit() == null ? 0 : this.meta.exit().frames();
        }
    }

    static final Meta DEFAULT_META = new Meta(new Box(128, 130, 28, 10), new Box(128, 140, 50, 60), null);

    private static final Map<String, Assets> CACHE = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> EXISTS = new HashMap<>();
    private static Source source = new ClientSource();

    private CardAssets() {
    }

    /** Test hook: swap the resource side and forget everything cached. */
    static void setSource(Source replacement) {
        source = replacement;
        invalidate();
    }

    static void resetSource() {
        source = new ClientSource();
        invalidate();
    }

    /** Resource reload: forget the cached lookups. */
    public static void invalidate() {
        CACHE.clear();
        EXISTS.clear();
    }

    public static ResourceLocation texture(String key, String name) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, "textures/card/" + key + "/" + name + ".png");
    }

    public static ResourceLocation common(String name) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, "textures/card/" + name + ".png");
    }

    public static ResourceLocation clipFrame(String key, int index) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE,
                "textures/card/" + key + "/clips/exit/" + String.format("%04d", index) + ".png");
    }

    /** The frame texture for a rarity (0 common, 1 rare, 2 legendary). */
    public static ResourceLocation frame(int rarity) {
        return common(rarity >= 2 ? "frame_legendary" : rarity == 1 ? "frame_rare" : "frame_common");
    }

    /** Whether a resource exists; cached, so asking every frame costs a map lookup. */
    public static boolean has(ResourceLocation location) {
        Boolean known = EXISTS.get(location);
        if (known == null) {
            known = source.exists(location);
            EXISTS.put(location, known);
        }
        return known;
    }

    /** The art for a card key: its real files, or the placeholder when any of the three core layers is missing. */
    public static Assets forKey(String key) {
        Assets cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Assets built = build(key);
        CACHE.put(key, built);
        return built;
    }

    private static Assets build(String key) {
        ResourceLocation base = texture(key, "base");
        ResourceLocation empty = texture(key, "empty");
        ResourceLocation figure = texture(key, "figure");
        if (!has(base) || !has(empty) || !has(figure)) {
            return placeholder(key);
        }
        Meta meta = loadMeta(key);
        ResourceLocation blink = texture(key, "blink");
        ResourceLocation lookL = texture(key, "look_l");
        ResourceLocation lookR = texture(key, "look_r");
        // The overlays are optional: a missing one is simply not drawn (the draw checks has()).
        Clip exit = meta.exit();
        if (exit != null && !has(clipFrame(key, 0))) {
            meta = new Meta(meta.eyes(), meta.face(), null);
        }
        return new Assets(key, false, base, empty, figure, blink, lookL, lookR, meta);
    }

    private static Assets placeholder(String key) {
        String k = PLACEHOLDER_KEY;
        Meta meta = loadMetaFrom(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "cards/" + k + ".json"));
        return new Assets(key, true, texture(k, "base"), texture(k, "empty"), texture(k, "figure"),
                texture(k, "blink"), texture(k, "look_l"), texture(k, "look_r"),
                new Meta(meta.eyes(), meta.face(), null));
    }

    private static Meta loadMeta(String key) {
        return loadMetaFrom(ResourceLocation.fromNamespaceAndPath(NAMESPACE, "cards/" + key + ".json"));
    }

    private static Meta loadMetaFrom(ResourceLocation json) {
        String text = source.exists(json) ? source.readText(json) : null;
        return text == null ? DEFAULT_META : parseMeta(text);
    }

    /** Parses a {@code cards/<key>.json}; anything missing or malformed takes its default. Never throws. */
    public static Meta parseMeta(String text) {
        try {
            JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) {
                return DEFAULT_META;
            }
            JsonObject o = root.getAsJsonObject();
            Box eyes = box(o.get("eyes"), DEFAULT_META.eyes());
            Box face = box(o.get("face"), DEFAULT_META.face());
            Clip clip = null;
            JsonElement c = o.get("exit_clip");
            if (c != null && c.isJsonObject()) {
                JsonObject co = c.getAsJsonObject();
                int frames = co.has("frames") ? co.get("frames").getAsInt() : 0;
                int fps = co.has("fps") ? co.get("fps").getAsInt() : com.hogwartsmagic.cosy.CardTimeline.CLIP_FPS;
                if (frames > 0) {
                    clip = new Clip(frames, fps);
                }
            }
            return new Meta(eyes, face, clip);
        } catch (RuntimeException e) {
            return DEFAULT_META;
        }
    }

    private static Box box(@Nullable JsonElement e, Box fallback) {
        if (e == null || !e.isJsonArray()) {
            return fallback;
        }
        JsonArray a = e.getAsJsonArray();
        if (a.size() != 4) {
            return fallback;
        }
        Box b = new Box(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat());
        return b.valid() ? b : fallback;
    }

    /** The real resource manager. */
    private static final class ClientSource implements Source {
        @Override
        public boolean exists(ResourceLocation location) {
            return net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
        }

        @Override
        @Nullable
        public String readText(ResourceLocation location) {
            try (java.io.BufferedReader reader = net.minecraft.client.Minecraft.getInstance().getResourceManager()
                    .openAsReader(location)) {
                StringBuilder out = new StringBuilder();
                char[] buf = new char[2048];
                int n;
                while ((n = reader.read(buf)) > 0) {
                    out.append(buf, 0, n);
                }
                return out.toString();
            } catch (java.io.IOException | RuntimeException e) {
                return null;
            }
        }
    }
}
