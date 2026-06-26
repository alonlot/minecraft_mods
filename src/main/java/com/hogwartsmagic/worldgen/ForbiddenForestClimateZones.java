package com.hogwartsmagic.worldgen;

import net.minecraft.world.level.biome.Climate;

/**
 * Multi-noise parameter slices for {@code hogwartsmagic:forbidden_forest}.
 *
 * <p><b>Goal: very large patches, but still rare.</b> In multi-noise generation a biome's window
 * has two independent effects:
 * <ul>
 *   <li><b>How often</b> it appears is gated by the dimensions whose noise varies at the broad
 *       "climate zone" scale — temperature and humidity. A <i>narrow</i> temperature/humidity
 *       window means the forest only shows up in one specific, uncommon climate niche → rare.</li>
 *   <li><b>How large</b> each occurrence is depends on the terrain-shape dimensions —
 *       continentalness, erosion and weirdness. Opening those <i>wide</i> lets the forest claim the
 *       whole region across every terrain type (coast→inland, flat→hilly, ridge→valley) instead of
 *       being chopped into small pieces by terrain-based biomes → one big contiguous forest.</li>
 * </ul>
 *
 * <p>So: temperature/humidity are kept tight (a cool, wet niche) to stay rare, while
 * continentalness/erosion/weirdness are opened up to make each forest that does spawn very large.
 * Continentalness starts above the ocean threshold so the forest stays on land.
 *
 * <p>Note: only affects newly generated chunks — existing worlds keep their current layout.
 */
public final class ForbiddenForestClimateZones {

    private static final Climate.Parameter SURFACE_DEPTH = Climate.Parameter.point(0.0F);

    public static final Climate.ParameterPoint[] POINTS = new Climate.ParameterPoint[] {
            new Climate.ParameterPoint(
                    Climate.Parameter.span(-0.45F, -0.05F), // temperature: narrow cool niche  -> RARE
                    Climate.Parameter.span(0.28F, 0.72F),   // humidity: narrow humid/wet niche -> RARE
                    Climate.Parameter.span(-0.11F, 1.00F),  // continentalness: coast -> deep inland -> LARGE (above ocean)
                    Climate.Parameter.span(-1.00F, 0.55F),  // erosion: nearly all terrain shapes -> LARGE
                    SURFACE_DEPTH,
                    Climate.Parameter.span(-0.65F, 0.65F),  // weirdness: ridges + valleys -> LARGE
                    0L)
    };

    private ForbiddenForestClimateZones() {}
}
