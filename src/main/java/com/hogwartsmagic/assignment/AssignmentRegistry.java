package com.hogwartsmagic.assignment;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads {@code data/<namespace>/assignments/<spell id>.json} on every datapack (re)load. The file name
 * is the spell id. A bad file is skipped with a log line naming the reason, so one typo never breaks
 * the others. Server side only: clients get what they need in {@link OpenAssignmentPacket}.
 */
public final class AssignmentRegistry extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Map<String, AssignmentDef> defs = Map.of();

    public AssignmentRegistry() {
        super(new Gson(), "assignments");
    }

    @Nullable
    public static AssignmentDef get(String spellId) {
        return defs.get(spellId);
    }

    public static boolean has(SpellType spell) {
        return defs.containsKey(spell.getId());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, AssignmentDef> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            String spellId = entry.getKey().getPath();
            if (!SpellType.fromId(spellId).getId().equals(spellId)) {
                LOGGER.warn("Assignment {} skipped: no spell has the id {}", entry.getKey(), spellId);
                continue;
            }
            try {
                loaded.put(spellId, AssignmentParser.parse(spellId, entry.getValue().getAsJsonObject()));
            } catch (RuntimeException e) {
                LOGGER.warn("Assignment {} skipped: {}", entry.getKey(), e.getMessage());
            }
        }
        defs = Map.copyOf(loaded);
        LOGGER.info("Loaded {} spell assignments", loaded.size());
    }
}
