package com.hogwartsmagic.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

/**
 * Resource keys for the mod's own overworld biomes.
 *
 * <p>The biomes themselves are datapack JSON under {@code data/hogwartsmagic/worldgen/biome/}; these
 * keys are how common code (the multi-noise mixin, surface rules) and client code (fog) name them.
 * Keeping them in one place means the string literals exist exactly once.
 *
 * <p>Common-safe: {@link ResourceKey} and {@link Biome} are both common classes, so a dedicated
 * server can load this.
 */
public final class ModBiomes {

    private static ResourceKey<Biome> key(String path) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("hogwartsmagic", path));
    }

    /** Old-growth spruce forest gone wrong: acromantulas, centaurs, fine wood. */
    public static final ResourceKey<Biome> FORBIDDEN_FOREST = key("forbidden_forest");

    /** Small, drowned, colourless bog where Dementors drift. */
    public static final ResourceKey<Biome> DEMENTOR_FEN = key("dementor_fen");

    /** Scorched blackstone crags — the Hungarian Horntail's nesting ground. */
    public static final ResourceKey<Biome> HORNTAIL_PEAKS = key("horntail_peaks");

    /** Cave biome: flooded green stone where the Basilisk finally has somewhere to live. */
    public static final ResourceKey<Biome> CHAMBER_DEPTHS = key("chamber_depths");

    /** Cave biome: hot, gold-veined vaults full of goblins. */
    public static final ResourceKey<Biome> GOBLIN_UNDERCITY = key("goblin_undercity");

    /** Deep, cold, black water — the reason to grow Gillyweed. Generates as a coastal deep ocean. */
    public static final ResourceKey<Biome> BLACK_LAKE = key("black_lake");

    /**
     * The same black water, inland: sea lochs and enclosed lakes cut out of the river band. Shallow,
     * so it carries merpeople but not the village. See {@link BlackLakeShallowsClimateZones}.
     */
    public static final ResourceKey<Biome> BLACK_LAKE_SHALLOWS = key("black_lake_shallows");

    private ModBiomes() {}
}
