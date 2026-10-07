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
 * <p>So: temperature/humidity stay a recognisable niche (cool and wet) so the forest is still
 * something you travel to, while continentalness/erosion/weirdness are opened all the way up so the
 * forest that does spawn claims its whole region instead of being diced up by terrain biomes. The
 * climate niche is a touch wider than it was, which grows each forest as well as finding a few more
 * of them — the deliberate trade for a forest you can get lost in.
 * Continentalness starts above the ocean threshold so the forest stays on land.
 *
 * <p>Note: only affects newly generated chunks — existing worlds keep their current layout.
 */
public final class ForbiddenForestClimateZones {

    // Surface AND underground depth points, like every other surface biome here (see
    // ClimateSlices): with only the depth-0 point the forest flipped to a neighbouring biome as
    // soon as you dug down into it.
    public static final Climate.ParameterPoint[] POINTS = ClimateSlices.surface(
            Climate.Parameter.span(-0.55F, 0.10F),  // temperature: cool niche, widened  -> RARE-ish
            Climate.Parameter.span(0.18F, 0.85F),   // humidity: humid/wet niche, widened -> RARE-ish
            Climate.Parameter.span(-0.11F, 1.00F),  // continentalness: coast -> deep inland -> LARGE (above ocean)
            Climate.Parameter.span(-1.00F, 0.70F),  // erosion: nearly all terrain shapes -> LARGE
            Climate.Parameter.span(-1.00F, 1.00F)); // weirdness: every ridge and valley -> LARGE

    private ForbiddenForestClimateZones() {}
}
