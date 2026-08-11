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
 * <p>That erosion band is <i>already</i> a small slice of the world — only a fraction of terrain is
 * peaks — so the climate dimensions do not need to narrow it further, and an earlier version that did
 * narrow them produced a biome nothing could find. Temperature and humidity now take the whole
 * warm-and-dry quadrant, which is the slice vanilla would otherwise hand to stony peaks: in practice
 * "the dry mountains are the dragon's mountains". Weirdness is left open so a range that does appear
 * runs across ridges and valleys as one continuous massif rather than a single lonely summit.
 *
 * <p>Note: only affects newly generated chunks — existing worlds keep their current layout.
 */
public final class HorntailPeaksClimateZones {

    public static final Climate.ParameterPoint[] POINTS = ClimateSlices.surface(
            Climate.Parameter.span(0.10F, 1.00F),   // temperature: the whole warm half
            Climate.Parameter.span(-1.00F, -0.10F), // humidity: the whole dry half
            Climate.Parameter.span(0.10F, 1.00F),   // continentalness: inland -> far inland
            ClimateSlices.PEAKS_EROSION,            // erosion: vanilla's peaks band -> HIGH terrain
            ClimateSlices.ANY);                     // weirdness: any -> one continuous range

    private HorntailPeaksClimateZones() {}
}
