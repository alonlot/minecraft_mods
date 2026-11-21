package com.hogwartsmagic.worldgen;

import net.minecraft.world.level.biome.Climate;

/**
 * Multi-noise parameter slice for {@code hogwartsmagic:cursebreakers_desert}.
 *
 * <p><b>Goal: a desert of its own, next to the vanilla one, not in place of it.</b> In vanilla the
 * hot temperature band is desert on all middle terrain, at every humidity. A slice that took the
 * whole band would turn every desert into this one, and vanilla desert villages and pyramids would
 * never generate again. The first draft avoided that by taking only the driest humidity bucket, and
 * the biome census measured it at 0.15% of the world: unfindable. Climate noise is bell-shaped, and
 * a vanilla desert's humidity sits round zero, not out at the dry edge.
 *
 * <p>So the slice was chosen from a dump of the census samples instead of by guessing (the dump
 * was a temporary game test). These spans measure at about 1.1% of the overworld, close to the
 * Dementor Fen, and leave about 60% of the vanilla desert where it was. Each span has a reason:
 *
 * <ul>
 *   <li><b>Humidity up to 0.15</b>: the dry two thirds of a desert, the wet third stays vanilla.</li>
 *   <li><b>Erosion from 0.0</b>: badlands sit at negative erosion in the same hot band. Starting at
 *       {@code -0.30} (where Horntail Peaks stops) ate a third of the world's badlands.</li>
 *   <li><b>Weirdness off the zero line</b>: vanilla puts its rivers at {@code |weirdness| < 0.05}.
 *       A mod point registered first wins ties, so an open weirdness span ate every river that
 *       crossed the desert. Two spans, one each side, leave the rivers in place.</li>
 * </ul>
 *
 * <p>Note: only affects newly generated chunks. Existing worlds keep their current layout.
 */
public final class CursebreakersDesertClimateZones {

    private static final Climate.Parameter TEMPERATURE = Climate.Parameter.span(0.55F, 1.00F); // the hot band
    private static final Climate.Parameter HUMIDITY = Climate.Parameter.span(-1.00F, 0.15F);   // dry two thirds
    private static final Climate.Parameter CONTINENTALNESS = Climate.Parameter.span(0.03F, 1.00F); // inland
    private static final Climate.Parameter EROSION = Climate.Parameter.span(0.00F, 0.55F);     // off the badlands

    public static final Climate.ParameterPoint[] POINTS = concat(
            ClimateSlices.surface(TEMPERATURE, HUMIDITY, CONTINENTALNESS, EROSION,
                    Climate.Parameter.span(-1.00F, -0.05F)),
            ClimateSlices.surface(TEMPERATURE, HUMIDITY, CONTINENTALNESS, EROSION,
                    Climate.Parameter.span(0.05F, 1.00F)));

    private static Climate.ParameterPoint[] concat(Climate.ParameterPoint[] a, Climate.ParameterPoint[] b) {
        Climate.ParameterPoint[] out = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private CursebreakersDesertClimateZones() {}
}
