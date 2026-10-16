package com.hogwartsmagic.pensieve;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The flat, empty world memories play in. Like Diagon Alley it reuses the overworld dimension type so the
 * client never has to learn a new one; the void biome does the dressing.
 */
public final class PensieveDimension {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "memory"));

    private PensieveDimension() {
    }

    /** The memory level; null only if the datapack failed to load it. */
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(KEY);
    }

    public static boolean isMemory(Level level) {
        return level.dimension().equals(KEY);
    }
}
