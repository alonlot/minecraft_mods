package com.hogwartsmagic.worldgen;

import net.minecraft.world.level.biome.Climate;

/**
 * Multi-noise parameter slice for {@code hogwartsmagic:black_lake_shallows} - the Black Lake as an
 * actual inland lake rather than an arm of the sea.
 *
 * <p>{@link BlackLakeClimateZones} claims a slice of the deep ocean band, which is the only way to get
 * water deep enough to need Gillyweed, but it means the Black Lake always turns up on a coastline.
 * This slice puts the same water inland instead.
 *
 * <p><b>The mechanism is the river band.</b> Vanilla carves its rivers by handing the two extreme
 * weirdness bands - below {@code -0.93333} and above {@code +0.93333} - to
 * {@code OverworldBiomeBuilder#addValleys}. A biome that claims part of that band gets a channel of
 * open water cut through whatever land is around it, which is exactly the shape of a lake. Taking the
 * <i>flattest</i> erosion bands as well ({@code 0.45 .. 1.0}, where vanilla puts swamps) is what turns
 * a narrow river gorge into a broad sheet of water.
 *
 * <p>Continentalness runs from the coast band into mid-inland, so these show up both as sea lochs just
 * behind a shoreline and as fully enclosed lakes further in.
 *
 * <p><b>These are shallow</b> - a few blocks, like any river valley. Water depth in 1.18+ terrain comes
 * from continentalness, and continentalness high enough to be inland cannot also be deep; there is no
 * parameter that buys both. That is why the merfolk village and the lakebed ruin are <i>not</i> tagged
 * for this biome: a twelve-block statue standing in four blocks of water looks like a bug. Merpeople
 * themselves do live here, in ones and twos, without a village.
 *
 * <p><b>Sizing note: the valley band caps this biome, so nothing else may narrow it.</b> Climate noise
 * is bell-shaped around zero, so those two extreme weirdness bands together account for only about
 * 0.34% of sampled terrain. That is the hard ceiling for <i>any</i> biome built on them — even with
 * the other four dimensions thrown wide open, this can never exceed roughly a tenth of
 * {@code forbidden_forest}'s share of the world. Narrowing temperature, humidity, continentalness and
 * erosion on top of it multiplied that 0.34% down to 0.004%, which is indistinguishable from the
 * biome not existing.
 *
 * <p>Widening the weirdness band instead would be the wrong fix and would break the biome: the deep
 * cut that fills with water comes from the terrain shaper reacting to extreme weirdness, not from
 * which biome claims the cell. Claim weirdness outside the valley band and you get a lake biome on
 * dry land. So the other four dimensions are opened up to roughly a third of the theoretical ceiling
 * instead, which leaves most of the world's rivers as ordinary rivers.
 *
 * <p>Low total share is also less alarming here than it looks: vanilla rivers occupy about the same
 * fraction of the world and you cannot walk far without meeting one, because valley biomes generate
 * as a connected network of strips rather than as isolated blobs.
 *
 * <p>Note: only affects newly generated chunks — existing worlds keep their current layout.
 */
public final class BlackLakeShallowsClimateZones {

    /** Vanilla's two valley bands, the ones addValleys is given. Rivers live here. */
    private static final Climate.Parameter VALLEY_LOW = Climate.Parameter.span(-1.0F, -0.93333334F);
    private static final Climate.Parameter VALLEY_HIGH = Climate.Parameter.span(0.93333334F, 1.0F);

    // The four dimensions below are deliberately near-open, and that is forced by the valley bands
    // above rather than being a stylistic choice. See the sizing note in the class javadoc.
    private static final Climate.Parameter TEMPERATURE = Climate.Parameter.span(-0.70F, 0.55F);
    /** Damp-ish country, but wide: the valley band is already doing all the narrowing. */
    private static final Climate.Parameter HUMIDITY = Climate.Parameter.span(-0.40F, 1.0F);
    /** Coast through inland — sea lochs and enclosed lakes both. */
    private static final Climate.Parameter CONTINENTALNESS = Climate.Parameter.span(-0.35F, 0.65F);
    /** Flatter erosion, so the valley spreads out into a sheet instead of cutting a gorge. */
    private static final Climate.Parameter EROSION = Climate.Parameter.span(0.0F, 1.0F);

    public static final Climate.ParameterPoint[] POINTS = concat(
            ClimateSlices.surface(TEMPERATURE, HUMIDITY, CONTINENTALNESS, EROSION, VALLEY_LOW),
            ClimateSlices.surface(TEMPERATURE, HUMIDITY, CONTINENTALNESS, EROSION, VALLEY_HIGH));

    private static Climate.ParameterPoint[] concat(Climate.ParameterPoint[] a, Climate.ParameterPoint[] b) {
        Climate.ParameterPoint[] all = new Climate.ParameterPoint[a.length + b.length];
        System.arraycopy(a, 0, all, 0, a.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return all;
    }

    private BlackLakeShallowsClimateZones() {}
}
