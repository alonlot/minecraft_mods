package com.hogwartsmagic.client.portrait;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hogwartsmagic.portrait.PortraitKind;
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
 * The data side of a portrait kind's assets: {@code portraits/<id>.json}, read lazily per kind and again after
 * every resource reload.
 *
 * <pre>{"lines": {key: {"seconds": s, "envelope": [0..1 every 50 ms]}},
 *  "clips": {clip: {"frames": n, "fps": f}}}</pre>
 *
 * Anything missing or broken just yields null: a line without an envelope has a flat mouth, a clip without data
 * is not played (the puppet says the line instead).
 */
final class PortraitAssets {
    private static final Logger LOGGER = LogUtils.getLogger();

    record Clip(int frames, float fps) {
        float seconds() {
            return frames / fps;
        }
    }

    /** What one kind's json holds. */
    private static final class Data {
        final Map<String, float[]> envelopes = new HashMap<>();
        final Map<String, Clip> clips = new HashMap<>();
    }

    private static final Map<String, Data> DATA = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> EXISTS = new HashMap<>();

    private PortraitAssets() {
    }

    static ResourceLocation texture(PortraitKind kind, String name) {
        return PortraitRegistry.id(kind.texturePath() + name + ".png");
    }

    static ResourceLocation frame(PortraitKind kind, String clip, int index) {
        return PortraitRegistry.id(kind.texturePath() + "clips/" + clip + "/" + String.format("%03d", index) + ".png");
    }

    /** The 50 ms loudness envelope of a line, or null. */
    static float[] envelope(PortraitKind kind, String lineKey) {
        return data(kind).envelopes.get(lineKey);
    }

    /** Frame count and rate of a clip, or null when the data is missing or the first frame is not there. */
    static Clip clip(PortraitKind kind, String name) {
        Clip clip = data(kind).clips.get(name);
        if (clip == null || !exists(frame(kind, name, 0))) {
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
        DATA.clear();
        EXISTS.clear();
    }

    private static Data data(PortraitKind kind) {
        Data data = DATA.get(kind.id());
        if (data == null) {
            data = new Data();
            DATA.put(kind.id(), data); // even a failed read is remembered until the next reload
            load(kind, data);
        }
        return data;
    }

    private static void load(PortraitKind kind, Data data) {
        ResourceLocation location = PortraitRegistry.id(kind.dataPath());
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(location);
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
                        data.envelopes.put(e.getKey(), env);
                    }
                }
                JsonObject clips = root.has("clips") ? root.getAsJsonObject("clips") : new JsonObject();
                for (Map.Entry<String, JsonElement> e : clips.entrySet()) {
                    JsonObject clip = e.getValue().getAsJsonObject();
                    int frames = clip.has("frames") ? clip.get("frames").getAsInt() : 0;
                    float fps = clip.has("fps") ? clip.get("fps").getAsFloat() : 0f;
                    if (frames > 0 && fps > 0f) {
                        data.clips.put(e.getKey(), new Clip(frames, fps));
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("Could not read {}: {}", location, ex.toString());
        }
    }
}
