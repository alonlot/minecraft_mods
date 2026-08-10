package com.hogwartsmagic.worldgen;

import net.minecraft.world.level.biome.Climate;

/**
 * Multi-noise parameter slice for {@code hogwartsmagic:goblin_undercity}.
 *
 * <p>The second of the mod's two cave biomes, and the reason both state their temperature and humidity
 * so precisely: {@link ChamberDepthsClimateZones} takes the <i>cold and damp</i> corner, this one takes
 * the <i>warm and dry</i> corner, and the two windows do not touch. Two cave biomes that overlapped
 * would not crash — multi-noise would simply pick one and the other would become nearly unreachable —
 * which is a much harder failure to notice than a crash.
 *
 * <p>Like the Chamber it stays clear of vanilla's dripstone ({@code continentalness 0.8 .. 1.0}) and
 * lush ({@code humidity 0.7 .. 1.0}) windows, and leaves erosion and weirdness open so a vault system
 * runs a long way once it starts.
 *
 * <p>Note: only affects newly generated chunks — existing worlds keep their current layout.
 */
public final class GoblinUndercityClimateZones {

    public static final Climate.ParameterPoint[] POINTS = ClimateSlices.cave(
            Climate.Parameter.span(0.15F, 0.55F),   // temperature: warm -- forges and deep stone
            Climate.Parameter.span(-0.60F, -0.20F), // humidity: dry, opposite corner to the Chamber
            Climate.Parameter.span(0.10F, 0.70F),   // continentalness: inland, short of dripstone caves
            ClimateSlices.ANY,                      // erosion: caves are not shaped by surface terrain
            ClimateSlices.ANY);                     // weirdness: open -> long connected vault systems

    private GoblinUndercityClimateZones() {}
}
