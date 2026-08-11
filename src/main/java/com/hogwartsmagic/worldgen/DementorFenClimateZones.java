package com.hogwartsmagic.worldgen;

import net.minecraft.world.level.biome.Climate;

/**
 * Multi-noise parameter slice for {@code hogwartsmagic:dementor_fen}.
 *
 * <p><b>Goal: small patches, but ones you actually run into.</b> Small and rare are different knobs,
 * and conflating them is the easy mistake here. As {@link ForbiddenForestClimateZones} sets out:
 * temperature and humidity vary at the broad climate-zone scale and so control <i>how often</i> a
 * biome appears, while continentalness, erosion and weirdness are terrain-scale and control <i>how
 * big</i> each occurrence is. Narrowing all five at once gives a biome that is both tiny and
 * effectively unfindable — you can walk for thousands of blocks without meeting one.
 *
 * <p>So temperature and humidity are opened up to a wide, ordinarily-damp climate (the fen will take
 * over a share of the world's swampland, which is the price of it existing at all), while weirdness
 * and continentalness stay tight. That combination is what keeps each fen a pocket you stumble into
 * rather than a region you cross, without making the pocket itself hard to come across.
 *
 * <p>Erosion is held high on purpose: in vanilla's parameter space high erosion means flat, low-lying
 * terrain, which is what makes a bog read as a bog instead of a wet hillside. Continentalness starts
 * just above the ocean threshold so the fen sits on land but stays inland of the coast, where the
 * water table pools.
 *
 * <p>Inside its box the fen always wins: multi-noise picks the <i>nearest</i> parameter point, and
 * {@code OverworldBiomeBuilderMixin} registers these before vanilla's, so ties go to the mod.
 *
 * <p>Note: only affects newly generated chunks — existing worlds keep their current layout.
 */
public final class DementorFenClimateZones {

    public static final Climate.ParameterPoint[] POINTS = ClimateSlices.surface(
            Climate.Parameter.span(-0.45F, 0.20F),  // temperature: two full bands -> COMMON
            Climate.Parameter.span(0.35F, 1.00F),   // humidity: the whole wet band -> COMMON
            Climate.Parameter.span(-0.11F, 0.55F),  // continentalness: near/mid inland (above ocean)
            Climate.Parameter.span(0.45F, 1.00F),   // erosion: high == flat, low-lying -> bog terrain
            Climate.Parameter.span(-0.50F, 0.10F)); // weirdness: still narrow     -> SMALL

    private DementorFenClimateZones() {}
}
