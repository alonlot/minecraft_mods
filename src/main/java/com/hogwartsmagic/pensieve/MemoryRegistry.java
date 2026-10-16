package com.hogwartsmagic.pensieve;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.hogwartsmagic.Config;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneException;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Loads {@code data/<namespace>/memories/<id>.json} on every datapack (re)load. A bad file is logged with the
 * parse message (which names the JSON path) and skipped, so one typo never hides the other scenes. Files whose
 * name starts with an underscore are not scenes; {@code _skins.json} lists the memory skins the server knows.
 * Server side only: nothing here touches client classes.
 */
public final class MemoryRegistry extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Map<String, LoadedScene> scenes = Map.of();
    private static volatile Set<String> skins = Set.of();

    /** A parsed scene with the raw JSON it came from (kept to send to clients). */
    public record LoadedScene(SceneDef def, String json) {
    }

    public MemoryRegistry() {
        super(new Gson(), "memories");
    }

    public static Optional<SceneDef> get(String id) {
        LoadedScene s = scenes.get(id);
        return s == null ? Optional.empty() : Optional.of(s.def());
    }

    public static Optional<String> rawJson(String id) {
        LoadedScene s = scenes.get(id);
        return s == null ? Optional.empty() : Optional.of(s.json());
    }

    public static Set<String> ids() {
        return new TreeSet<>(scenes.keySet());
    }

    /** Skin names from {@code _skins.json}, for the validator. */
    public static Set<String> skins() {
        return skins;
    }

    /**
     * The scene to preview: with {@code pensieveDevSceneDir} set and {@code <dir>/<id>.json} present, that file
     * parsed fresh from disk; otherwise the registry copy. Empty when neither exists.
     */
    public static Optional<LoadedScene> loadForPreview(String id) throws SceneException {
        String dir = Config.pensieveDevSceneDir;
        if (dir != null && !dir.isBlank() && !id.startsWith("_") && !id.contains("/") && !id.contains("\\")) {
            Path file = Path.of(dir).resolve(id + ".json");
            if (Files.isRegularFile(file)) {
                String json;
                try {
                    json = Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new SceneException("", "cannot read " + file + ": " + e.getMessage());
                }
                return Optional.of(new LoadedScene(parseText(id, json), json));
            }
        }
        LoadedScene s = scenes.get(id);
        return Optional.ofNullable(s);
    }

    /** Parses scene text, turning any JSON or structure problem into a {@link SceneException}. */
    static SceneDef parseText(String id, String json) throws SceneException {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new SceneException("", "not valid JSON: " + e.getMessage());
        }
        if (!root.isJsonObject()) throw new SceneException("", "expected a JSON object");
        return SceneParser.parse(id, root.getAsJsonObject());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, LoadedScene> loaded = new HashMap<>();
        Set<String> skinNames = new HashSet<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            String id = entry.getKey().getPath();
            if (id.equals("_skins")) {
                if (entry.getValue().isJsonArray()) {
                    entry.getValue().getAsJsonArray().forEach(e -> {
                        if (e.isJsonPrimitive()) skinNames.add(e.getAsString());
                    });
                } else {
                    LOGGER.warn("Memory skin list {} skipped: expected an array of names", entry.getKey());
                }
                continue;
            }
            if (id.startsWith("_") || id.contains("/_")) continue;
            try {
                String json = entry.getValue().toString();
                loaded.put(id, new LoadedScene(parseText(id, json), json));
            } catch (SceneException e) {
                LOGGER.warn("Memory {} skipped: {}", entry.getKey(), e.getMessage());
            } catch (RuntimeException e) {
                LOGGER.warn("Memory {} skipped: {}", entry.getKey(), e.toString());
            }
        }
        scenes = Map.copyOf(loaded);
        skins = Set.copyOf(skinNames);
        LOGGER.info("Loaded {} Pensieve memories", loaded.size());
    }
}
