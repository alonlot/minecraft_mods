package com.hogwartsmagic.client.portrait;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hogwartsmagic.portrait.PortraitRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The data side of the Fat Lady's assets: {@code portraits/fat_lady.json}, read lazily and again after every
 * resource reload.
 *
 * <pre>{"lines": {key: {"seconds": s, "envelope": [0..1 every 50 ms]}},
 *  "clips": {clip: {"frames": n, "fps": f}}}</pre>
 *
 * Anything missing or broken just yields null: a line without an envelope has a flat mouth, a clip without data
 * is not played (the puppet says the line instead).
 */
final class PortraitAssets {
    private static final Logger LOGGER = LogUtils.getLogger();

    static final ResourceLocation DATA = PortraitRegistry.id("portraits/fat_lady.json");
    private static final String TEXTURES = "textures/entity/portrait/fat_lady/";

    record Clip(int frames, float fps) {
        float seconds() {
            return frames / fps;
        }
    }

    private static boolean loaded;
    private static final Map<String, float[]> ENVELOPES = new HashMap<>();
    private static final Map<String, Clip> CLIPS = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> EXISTS = new HashMap<>();

    private PortraitAssets() {
    }

    static ResourceLocation texture(String name) {
        return PortraitRegistry.id(TEXTURES + name + ".png");
    }

    static ResourceLocation frame(String clip, int index) {
        return PortraitRegistry.id(TEXTURES + "clips/" + clip + "/" + String.format("%03d", index) + ".png");
    }

    /** The 50 ms loudness envelope of a line, or null. */
    static float[] envelope(String lineKey) {
        ensureLoaded();
        return ENVELOPES.get(lineKey);
    }

    /** Frame count and rate of a clip, or null when the data is missing or the first frame is not there. */
    static Clip clip(String name) {
        ensureLoaded();
        Clip clip = CLIPS.get(name);
        if (clip == null || !exists(frame(name, 0))) {
            return null;
        }
        return clip;
    }

    /** Whether a texture file is present (cached until the next reload). */
    static boolean exists(ResourceLocation location) {
        Boolean known = EXISTS.get(location);
        if (known == null) {
            known = Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
            EXISTS.put(location, known);
        }
        return known;
    }

    static void invalidate() {
        loaded = false;
        ENVELOPES.clear();
        CLIPS.clear();
        EXISTS.clear();
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(DATA);
            if (resource.isEmpty()) {
                return;
            }
            try (InputStream in = resource.get().open();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject lines = root.has("lines") ? root.getAsJsonObject("lines") : new JsonObject();
                for (Map.Entry<String, JsonElement> e : lines.entrySet()) {
                    JsonObject line = e.getValue().getAsJsonObject();
                    if (line.has("envelope")) {
                        JsonArray array = line.getAsJsonArray("envelope");
                        float[] env = new float[array.size()];
                        for (int i = 0; i < env.length; i++) {
                            env[i] = array.get(i).getAsFloat();
                        }
                        ENVELOPES.put(e.getKey(), env);
                    }
                }
                JsonObject clips = root.has("clips") ? root.getAsJsonObject("clips") : new JsonObject();
                for (Map.Entry<String, JsonElement> e : clips.entrySet()) {
                    JsonObject clip = e.getValue().getAsJsonObject();
                    int frames = clip.has("frames") ? clip.get("frames").getAsInt() : 0;
                    float fps = clip.has("fps") ? clip.get("fps").getAsFloat() : 0f;
                    if (frames > 0 && fps > 0f) {
                        CLIPS.put(e.getKey(), new Clip(frames, fps));
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("Could not read {}: {}", DATA, ex.toString());
        }
    }
}
