package com.hogwartsmagic.client.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hogwartsmagic.spell.voice.VoiceAliases;
import com.hogwartsmagic.spell.voice.VoiceCastConfig;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The voice practice aliases on this computer, one set per speech model (each model mishears in its own way), in
 * {@code <game dir>/hogwartsmagic/voice/aliases.json}. Render thread only. A broken file is set aside, not fatal.
 */
final class VoiceAliasStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<VoiceCastConfig.Model, VoiceAliases> BY_MODEL = new EnumMap<>(VoiceCastConfig.Model.class);
    private static boolean loaded;

    private VoiceAliasStore() {
    }

    private static Path file() {
        return FMLPaths.GAMEDIR.get().resolve("hogwartsmagic").resolve("voice").resolve("aliases.json");
    }

    /** The aliases for {@code model}, loaded from disk on first use. */
    static VoiceAliases forModel(VoiceCastConfig.Model model) {
        load();
        return BY_MODEL.computeIfAbsent(model, m -> new VoiceAliases());
    }

    /** Spell id to aliases for {@code model}, the form the matcher takes. */
    static Map<String, List<String>> view(VoiceCastConfig.Model model) {
        return forModel(model).view();
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path f = file();
        if (!Files.exists(f)) {
            return;
        }
        try {
            JsonObject root = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
            for (VoiceCastConfig.Model m : VoiceCastConfig.Model.values()) {
                JsonElement e = root == null ? null : root.get(m.name().toLowerCase(Locale.ROOT));
                if (e == null || !e.isJsonObject()) {
                    continue;
                }
                VoiceAliases a = new VoiceAliases();
                for (Map.Entry<String, JsonElement> spell : e.getAsJsonObject().entrySet()) {
                    if (!spell.getValue().isJsonArray()) {
                        continue;
                    }
                    for (JsonElement phrase : spell.getValue().getAsJsonArray()) {
                        if (phrase.isJsonPrimitive()) {
                            a.put(spell.getKey(), phrase.getAsString());
                        }
                    }
                }
                BY_MODEL.put(m, a);
            }
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("Voice casting: {} is unreadable, starting fresh: {}", f, ex.toString());
            try {
                Files.move(f, f.resolveSibling("aliases.broken.json"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // left in place; the next save overwrites it
            }
        }
    }

    static void save() {
        load();
        JsonObject root = new JsonObject();
        for (Map.Entry<VoiceCastConfig.Model, VoiceAliases> m : BY_MODEL.entrySet()) {
            JsonObject spells = new JsonObject();
            for (Map.Entry<String, List<String>> s : m.getValue().view().entrySet()) {
                if (s.getValue().isEmpty()) {
                    continue;
                }
                JsonArray arr = new JsonArray();
                s.getValue().forEach(arr::add);
                spells.add(s.getKey(), arr);
            }
            root.add(m.getKey().name().toLowerCase(Locale.ROOT), spells);
        }
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("aliases.json.part");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.warn("Voice casting: could not save {}: {}", f, e.toString());
        }
    }
}
