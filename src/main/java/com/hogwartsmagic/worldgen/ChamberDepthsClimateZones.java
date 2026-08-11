package com.hogwartsmagic.worldgen;

import net.minecraft.world.level.biome.Climate;

/**
 * Multi-noise parameter slice for {@code hogwartsmagic:chamber_depths}.
 *
 * <p><b>Goal: as findable as a vanilla cave biome, without displacing one.</b> Vanilla makes its cave
 * biomes easy to come across by constraining exactly <i>one</i> dimension and leaving the rest open:
 * dripstone is "continentalness above {@code 0.8}", lush is "humidity above {@code 0.7}", and nothing
 * else. This slice follows the same shape — one humidity band, everything else open — and simply sits
 * in the damp band immediately <i>below</i> lush, so the two sit beside each other rather than fight.
 *
 * <p>Erosion and weirdness stay open because cave biomes are not shaped by surface terrain the way
 * surface ones are; opening them just means a chamber system runs a long way once it starts.
 *
 * <p>Being a biome does not by itself make a cavern — see the biome JSON, which pairs this with its
 * own beefed-up carver and with stone-replacing features, because a cave biome laid over solid rock
 * is otherwise just an invisible label on some ordinary stone.
 *
 * <p>Depth comes from {@link ClimateSlices#cave}, which keeps this strictly below the surface.
 *
 * <p>Note: only affects newly generated chunks — existing worlds keep their current layout.
 */
public final class ChamberDepthsClimateZones {

    public static final Climate.ParameterPoint[] POINTS = ClimateSlices.cave(
            ClimateSlices.ANY,                      // temperature: any -- humidity alone defines this one
            Climate.Parameter.span(0.28F, 0.68F),   // humidity: the damp band just under lush caves
            ClimateSlices.BELOW_DRIPSTONE,          // continentalness: stop short of dripstone caves
            ClimateSlices.ANY,                      // erosion: caves are not shaped by surface terrain
            ClimateSlices.ANY);                     // weirdness: open -> long connected systems

    private ChamberDepthsClimateZones() {}
}
