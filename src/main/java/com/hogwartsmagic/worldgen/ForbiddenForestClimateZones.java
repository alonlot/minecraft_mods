package com.hogwartsmagic.worldgen;

import net.minecraft.world.level.biome.Climate;

/**
 * Multi-noise parameter slices for {@code hogwartsmagic:forbidden_forest}.
 *
 * <p>This slice stays wide enough to produce large contiguous regions, but is narrow enough to
 * keep the Forbidden Forest uncommon in normal overworld generation.
 */
public final class ForbiddenForestClimateZones {

    private static final Climate.Parameter SURFACE_DEPTH = Climate.Parameter.point(0.0F);

    public static final Climate.ParameterPoint[] POINTS = new Climate.ParameterPoint[] {
            new Climate.ParameterPoint(
                    Climate.Parameter.span(-0.58F, 0.02F),
                    Climate.Parameter.span(0.12F, 0.82F),
                    Climate.Parameter.span(-0.32F, 0.34F),
                    Climate.Parameter.span(-0.88F, -0.12F),
                    SURFACE_DEPTH,
                    Climate.Parameter.span(-0.28F, 0.28F),
                    0L)
    };

    private ForbiddenForestClimateZones() {}
}
