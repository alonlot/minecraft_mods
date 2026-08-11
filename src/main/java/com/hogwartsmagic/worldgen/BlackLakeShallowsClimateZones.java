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
 * <p>Temperature and humidity match the ocean slice, kept narrow so most of the world's rivers stay
 * rivers.
 *
 * <p>Note: only affects newly generated chunks — existing worlds keep their current layout.
 */
public final class BlackLakeShallowsClimateZones {

    /** Vanilla's two valley bands, the ones addValleys is given. Rivers live here. */
    private static final Climate.Parameter VALLEY_LOW = Climate.Parameter.span(-1.0F, -0.93333334F);
    private static final Climate.Parameter VALLEY_HIGH = Climate.Parameter.span(0.93333334F, 1.0F);

    private static final Climate.Parameter TEMPERATURE = Climate.Parameter.span(-0.45F, 0.20F);
    /** The damp half of the range: a lake sits in wet country. */
    private static final Climate.Parameter HUMIDITY = Climate.Parameter.span(0.10F, 0.60F);
    /** Coast through mid-inland — sea lochs and enclosed lakes both. */
    private static final Climate.Parameter CONTINENTALNESS = Climate.Parameter.span(-0.19F, 0.30F);
    /** The two flattest erosion bands, so the valley spreads out instead of cutting a gorge. */
    private static final Climate.Parameter EROSION = Climate.Parameter.span(0.45F, 1.0F);

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
