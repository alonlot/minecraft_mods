package com.hogwartsmagic.worldgen;

import net.minecraft.world.level.biome.Climate;

/**
 * Multi-noise parameter slice for {@code hogwartsmagic:horntail_peaks}.
 *
 * <p><b>Goal: real mountain terrain, in a dry warm niche, uncommon but worth the walk.</b> Unlike the
 * other two mod biomes the shape of this one is not negotiable — a dragon eyrie has to actually be
 * high up. That is decided entirely by <b>erosion</b>: vanilla reserves the lowest erosion band
 * ({@code -1.00 .. -0.78}) for peaks, and any biome claiming that band inherits the jagged, tall
 * terrain that goes with it. Sitting anywhere else in erosion would produce a hot dry <i>plateau</i>
 * with the right colours and none of the drama.
 *
 * <p>Rarity therefore comes from the climate dimensions instead: a narrow warm temperature band and a
 * narrow dry humidity band, which is the slice vanilla would otherwise hand to stony peaks. Far-inland
 * continentalness keeps the crags away from coastlines, and weirdness is left wide so a range that
 * does appear runs across ridges and valleys as one continuous massif rather than a single lonely
 * summit.
 *
 * <p>Note: only affects newly generated chunks — existing worlds keep their current layout.
 */
public final class HorntailPeaksClimateZones {

    public static final Climate.ParameterPoint[] POINTS = ClimateSlices.surface(
            Climate.Parameter.span(0.20F, 0.55F),   // temperature: warm, arid crags -> RARE
            Climate.Parameter.span(-0.60F, -0.30F), // humidity: dry                 -> RARE
            Climate.Parameter.span(0.30F, 1.00F),   // continentalness: inland -> far inland
            Climate.Parameter.span(-1.00F, -0.78F), // erosion: vanilla's peaks band -> HIGH terrain
            Climate.Parameter.span(-0.60F, 0.60F)); // weirdness: wide -> one continuous range

    private HorntailPeaksClimateZones() {}
}
