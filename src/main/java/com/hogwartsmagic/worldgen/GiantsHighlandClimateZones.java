package com.hogwartsmagic.worldgen;

import net.minecraft.world.level.biome.Climate;

/**
 * Multi-noise parameter slice for {@code hogwartsmagic:giants_highland}.
 *
 * <p><b>Goal: cold, dry high country with room to camp in.</b> The giants of the books live in a
 * mountain valley, and a giant is five and a half blocks tall: the biome has to be high and bare,
 * but it also needs ground flat enough for a camp forty blocks across. So erosion takes the
 * mountain band and the rough plateau band below the peaks, and stops short of the lowest band,
 * which is all spires and no floor.
 *
 * <p><b>Where it sits next to the other high biomes.</b> Horntail Peaks owns the warm, dry high
 * country and the Forbidden Forest owns the humid country at every height. This takes what is left
 * between them: cool to cold, and dry. The three boxes touch but do not overlap, so none of them
 * eats another; the mixin also registers this one after both, so a tie on a shared face goes to
 * the older biome.
 *
 * <p>Note: only affects newly generated chunks — existing worlds keep their current layout.
 */
public final class GiantsHighlandClimateZones {

    public static final Climate.ParameterPoint[] POINTS = ClimateSlices.surface(
            Climate.Parameter.span(-0.70F, -0.20F), // temperature: cold, not frozen
            Climate.Parameter.span(-1.00F, 0.15F),  // humidity: dry through middling (forest is above)
            Climate.Parameter.span(0.03F, 1.00F),   // continentalness: inland, off the coast
            Climate.Parameter.span(-0.78F, -0.10F), // erosion: mountains and plateaus, not spires
            ClimateSlices.ANY);                     // weirdness: valleys and ridges alike

    private GiantsHighlandClimateZones() {}
}
