package com.hogwartsmagic.worldgen;

import net.minecraft.world.level.biome.Climate;

/**
 * Multi-noise parameter slice for {@code hogwartsmagic:black_lake}.
 *
 * <p><b>This generates as a deep, cold, very dark ocean, not as a lake.</b> That is a limitation of the
 * generator, not a choice: Minecraft's lakes are a decoration feature placed inside other biomes and
 * have no biome of their own, so the only way to own a large body of open water is to claim a slice of
 * the ocean continentalness band. Expect to find it on a coastline as a stretch of black water, deep
 * enough that the bottom is out of breath-hold range — which is exactly the situation Gillyweed exists
 * to solve.
 *
 * <p>Continentalness sits in vanilla's <i>deep</i> ocean band ({@code -1.05 .. -0.455}) so the floor is
 * far enough down to matter. Temperature is held cold but above the freezing threshold — a frozen
 * surface would defeat the point — and humidity and weirdness are kept narrow so most of the world's
 * oceans are still ordinary ocean.
 *
 * <p>Note: only affects newly generated chunks — existing worlds keep their current layout.
 */
public final class BlackLakeClimateZones {

    public static final Climate.ParameterPoint[] POINTS = ClimateSlices.surface(
            Climate.Parameter.span(-0.60F, 0.35F),  // temperature: cold, but not freezing over
            Climate.Parameter.span(-0.50F, 0.70F),  // humidity: wide
            Climate.Parameter.span(-1.05F, -0.455F),// continentalness: vanilla's DEEP ocean band
            ClimateSlices.ANY,                      // erosion: the sea floor can be any shape
            ClimateSlices.ANY);                     // weirdness: any

    private BlackLakeClimateZones() {}
}
