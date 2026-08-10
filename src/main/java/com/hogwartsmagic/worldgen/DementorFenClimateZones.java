package com.hogwartsmagic.worldgen;

import net.minecraft.world.level.biome.Climate;

/**
 * Multi-noise parameter slice for {@code hogwartsmagic:dementor_fen}.
 *
 * <p><b>Goal: small and uncommon.</b> This is the mirror image of
 * {@link ForbiddenForestClimateZones}: that biome keeps temperature/humidity tight (rare) but opens
 * continentalness/erosion/weirdness wide (large). The fen keeps <i>every</i> dimension tight, so it
 * only wins the cells where a cool, very wet, flat, near-coast lowland all coincide. The result is a
 * pocket you stumble into rather than a region you cross — a few hundred blocks across, not a
 * biome-sized landmass.
 *
 * <p>Erosion is held high on purpose: in vanilla's parameter space high erosion means flat, low-lying
 * terrain, which is what makes a bog read as a bog instead of a wet hillside. Continentalness starts
 * just above the ocean threshold so the fen sits on land but stays near the coast, where the water
 * table pools.
 *
 * <p>A narrow window does not mean the biome can fail to place: multi-noise picks the <i>nearest</i>
 * parameter point, and {@code OverworldBiomeBuilderMixin} registers these before vanilla's, so inside
 * this box the fen always wins the tie. Narrow only bounds how far it can spread.
 *
 * <p>Note: only affects newly generated chunks — existing worlds keep their current layout.
 */
public final class DementorFenClimateZones {

    public static final Climate.ParameterPoint[] POINTS = ClimateSlices.surface(
            Climate.Parameter.span(-0.30F, -0.10F), // temperature: cool, no snow  -> RARE
            Climate.Parameter.span(0.55F, 0.85F),   // humidity: soaked            -> RARE
            Climate.Parameter.span(-0.05F, 0.35F),  // continentalness: coastal lowland (above ocean)
            Climate.Parameter.span(0.35F, 0.75F),   // erosion: high == flat, low-lying -> bog terrain
            Climate.Parameter.span(-0.35F, 0.05F)); // weirdness: narrow           -> SMALL

    private DementorFenClimateZones() {}
}
