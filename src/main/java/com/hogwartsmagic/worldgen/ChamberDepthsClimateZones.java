package com.hogwartsmagic.worldgen;

import net.minecraft.world.level.biome.Climate;

/**
 * Multi-noise parameter slice for {@code hogwartsmagic:chamber_depths}.
 *
 * <p><b>Goal: a cave biome that does not collide with vanilla's three.</b> Underground biomes are
 * chosen from the same parameter space as surface ones, so the only thing keeping them apart is that
 * each claims a different corner of it. Vanilla uses continentalness for dripstone caves
 * ({@code 0.8 .. 1.0}) and humidity for lush caves ({@code 0.7 .. 1.0}); this slice deliberately stops
 * short of both — continentalness caps at {@code 0.70} and humidity at {@code 0.55} — so the three
 * never fight over the same cell and no vanilla cave is displaced.
 *
 * <p>Within that corner it takes a cold, damp niche and leaves erosion and weirdness open, because
 * cave biomes are not shaped by terrain the way surface ones are: opening those dimensions just means
 * a chamber that does appear is a long connected system rather than a handful of disconnected pockets.
 *
 * <p>Depth comes from {@link ClimateSlices#cave}, which keeps this strictly below the surface.
 *
 * <p>Note: only affects newly generated chunks — existing worlds keep their current layout.
 */
public final class ChamberDepthsClimateZones {

    public static final Climate.ParameterPoint[] POINTS = ClimateSlices.cave(
            Climate.Parameter.span(-0.45F, -0.15F), // temperature: cold, still air
            Climate.Parameter.span(0.20F, 0.55F),   // humidity: damp, but short of lush caves
            Climate.Parameter.span(-0.11F, 0.70F),  // continentalness: inland, short of dripstone caves
            ClimateSlices.ANY,                      // erosion: caves are not shaped by surface terrain
            ClimateSlices.ANY);                     // weirdness: open -> long connected systems

    private ChamberDepthsClimateZones() {}
}
