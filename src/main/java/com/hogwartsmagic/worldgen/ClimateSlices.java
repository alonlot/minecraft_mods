package com.hogwartsmagic.worldgen;

import net.minecraft.world.level.biome.Climate;

/**
 * Builds the multi-noise parameter points for a mod biome, so each {@code *ClimateZones} class only
 * has to state the five interesting dimensions and not re-derive how <b>depth</b> works.
 *
 * <p>Depth is the dimension that decides whether a biome is a place you stand on or a place you dig
 * into, and it is the easiest one to get subtly wrong:
 *
 * <ul>
 *   <li>A <b>surface</b> biome registers <i>two</i> points, at depth {@code 0.0} and depth {@code 1.0}.
 *       This is what vanilla's own {@code addSurfaceBiome} does, and it is why a vanilla biome still
 *       reads as itself when you dig down through it. Registering only the {@code 0.0} point leaves
 *       the rock underneath to whichever neighbouring biome happens to be nearest — the biome name
 *       flips underground, and cave mobs come from someone else's spawn list.</li>
 *   <li>A <b>cave</b> biome registers one point in the {@code 0.2 .. 0.9} depth band that vanilla
 *       reserves for dripstone and lush caves. Surface biomes only reach depth {@code 1.0}, so inside
 *       this band a cave biome always wins on distance and never disturbs the surface above it.</li>
 * </ul>
 *
 * <p>Note: only affects newly generated chunks — existing worlds keep their current layout.
 */
public final class ClimateSlices {

    private static final Climate.Parameter SURFACE_DEPTH = Climate.Parameter.point(0.0F);
    private static final Climate.Parameter UNDERGROUND_DEPTH = Climate.Parameter.point(1.0F);
    private static final Climate.Parameter CAVE_DEPTH = Climate.Parameter.span(0.2F, 0.9F);

    /** Full {@code -1..1}, for a dimension a biome does not care about. */
    public static final Climate.Parameter ANY = Climate.Parameter.span(-1.0F, 1.0F);

    /** A biome you walk on, which also owns the stone beneath it. */
    public static Climate.ParameterPoint[] surface(
            Climate.Parameter temperature,
            Climate.Parameter humidity,
            Climate.Parameter continentalness,
            Climate.Parameter erosion,
            Climate.Parameter weirdness) {
        return new Climate.ParameterPoint[] {
                new Climate.ParameterPoint(temperature, humidity, continentalness, erosion,
                        SURFACE_DEPTH, weirdness, 0L),
                new Climate.ParameterPoint(temperature, humidity, continentalness, erosion,
                        UNDERGROUND_DEPTH, weirdness, 0L),
        };
    }

    /** A biome that only exists underground, leaving whatever is above it untouched. */
    public static Climate.ParameterPoint[] cave(
            Climate.Parameter temperature,
            Climate.Parameter humidity,
            Climate.Parameter continentalness,
            Climate.Parameter erosion,
            Climate.Parameter weirdness) {
        return new Climate.ParameterPoint[] {
                new Climate.ParameterPoint(temperature, humidity, continentalness, erosion,
                        CAVE_DEPTH, weirdness, 0L),
        };
    }

    private ClimateSlices() {}
}
