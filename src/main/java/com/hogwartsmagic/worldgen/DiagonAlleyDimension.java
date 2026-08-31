package com.hogwartsmagic.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * The world Diagon Alley is actually in.
 *
 * <p>An empty one. The dimension is declared in
 * {@code data/hogwartsmagic/dimension/diagon_alley.json} as a flat generator with no layers, no
 * features and no lakes, so nothing at all exists in it until an alley is pasted in.
 *
 * <h2>Why it borrows the overworld dimension type</h2>
 *
 * <p>Not for flavour, and not negotiable. Crossing into the alley never tells the client it changed
 * worlds -- that is the whole trick, see {@link DiagonAlleyPortal} -- so the client keeps parsing
 * incoming chunks against the level it thinks it is in, which is the overworld. A chunk packet is
 * read as {@code new LevelChunk(clientLevel, pos)} and sized from that level height. Give this
 * dimension its own type with a different {@code min_y} or {@code height} and every chunk sent from
 * it would be misread.
 *
 * <p>Everything that would normally be worth a custom dimension type -- the sky, the fog, the
 * absence of weather -- is done by the biome instead, which is ours and costs nothing.
 */
public final class DiagonAlleyDimension {

    public static final ResourceKey<Level> KEY = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "diagon_alley"));

    private DiagonAlleyDimension() {
    }

    /**
     * The alley level, or null if the datapack failed to load it.
     *
     * <p>Callers check. A missing dimension should leave a wall that refuses to open, not a crash.
     */
    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(KEY);
    }

    /** Is this the alley itself, rather than somewhere a player came from? */
    public static boolean isAlley(Level level) {
        return level.dimension().equals(KEY);
    }
}
