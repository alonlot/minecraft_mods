package com.hogwartsmagic.worldgen;

import net.minecraft.world.level.biome.Climate;

/**
 * Multi-noise parameter slice for {@code hogwartsmagic:goblin_undercity}.
 *
 * <p>The second of the mod's two cave biomes. {@link ChamberDepthsClimateZones} claims a damp humidity
 * band; this one claims the <i>hot</i> temperature band and the dry humidity below the Chamber's, so
 * the two windows cannot touch. Two cave biomes that overlapped would not crash — multi-noise would
 * simply pick one and the other would become nearly unreachable — which is a far harder failure to
 * notice than a crash.
 *
 * <p>Like the Chamber it stops short of vanilla's dripstone window and leaves erosion and weirdness
 * open, so a vault system runs a long way once it starts.
 *
 * <p>Also like the Chamber, the biome alone is only a label: the caverns come from the carver in the
 * biome JSON and the gold-veined look from stone-replacing features, not from being a biome.
 *
 * <p>Note: only affects newly generated chunks — existing worlds keep their current layout.
 */
public final class GoblinUndercityClimateZones {

    public static final Climate.ParameterPoint[] POINTS = ClimateSlices.cave(
            Climate.Parameter.span(0.45F, 1.00F),   // temperature: the hot band -- forges and deep stone
            Climate.Parameter.span(-1.00F, 0.25F),  // humidity: dry, disjoint from the Chamber's band
            ClimateSlices.BELOW_DRIPSTONE,          // continentalness: stop short of dripstone caves
            ClimateSlices.ANY,                      // erosion: caves are not shaped by surface terrain
            ClimateSlices.ANY);                     // weirdness: open -> long connected vault systems

    private GoblinUndercityClimateZones() {}
}
