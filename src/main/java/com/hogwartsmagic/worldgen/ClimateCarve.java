package com.hogwartsmagic.worldgen;

import net.minecraft.world.level.biome.Climate;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a mod biome's climate boxes out of the vanilla boxes that overlap them.
 *
 * <p><b>Why this is needed.</b> Multi-noise picks the nearest parameter point, and inside a mod box
 * that overlaps a vanilla box both are at distance zero: a tie. Vanilla's R-tree search keeps the
 * leaf it found on the previous lookup unless a new one is <i>strictly</i> nearer, so a tie goes to
 * whichever biome the last lookup on that thread happened to return. The first Cursebreaker's Desert
 * slice was measured by the biome census at 0.15% of the world, against about 1.1% expected from
 * its climate box, and the same census showed the Forbidden Forest's share move between two runs
 * with no change to it. Registering first does not win ties; only removing the overlap does.
 *
 * <p>The cut is a box difference: for each dimension in turn, the parts of the vanilla box below
 * and above the mod box are kept as new boxes, and the part inside goes on to the next dimension.
 * What is left after the last dimension lies inside the mod box and is dropped. A box that only
 * touches the mod box on a face is kept whole.
 *
 * <p>Common-safe: only {@link Climate}.
 */
public final class ClimateCarve {

    private ClimateCarve() {}

    /** {@code point} with every box in {@code holes} cut out of it: zero or more points. */
    public static List<Climate.ParameterPoint> carve(Climate.ParameterPoint point, Climate.ParameterPoint[] holes) {
        List<Climate.ParameterPoint> pieces = new ArrayList<>(List.of(point));
        for (Climate.ParameterPoint hole : holes) {
            List<Climate.ParameterPoint> next = new ArrayList<>();
            for (Climate.ParameterPoint piece : pieces) {
                next.addAll(subtract(piece, hole));
            }
            pieces = next;
        }
        return pieces;
    }

    private static List<Climate.ParameterPoint> subtract(Climate.ParameterPoint v, Climate.ParameterPoint hole) {
        Climate.Parameter[] cur = dims(v);
        Climate.Parameter[] h = dims(hole);
        if (!overlaps(cur, h)) {
            return List.of(v);
        }
        List<Climate.ParameterPoint> out = new ArrayList<>();
        for (int d = 0; d < cur.length; d++) {
            if (cur[d].min() < h[d].min()) {
                Climate.Parameter[] below = cur.clone();
                below[d] = new Climate.Parameter(cur[d].min(), h[d].min());
                out.add(point(below, v.offset()));
            }
            if (cur[d].max() > h[d].max()) {
                Climate.Parameter[] above = cur.clone();
                above[d] = new Climate.Parameter(h[d].max(), cur[d].max());
                out.add(point(above, v.offset()));
            }
            cur[d] = new Climate.Parameter(Math.max(cur[d].min(), h[d].min()), Math.min(cur[d].max(), h[d].max()));
        }
        return out;
    }

    /**
     * True when the boxes share volume. Depth is compared closed, because surface biomes use single
     * depth points (0 and 1) and two equal points do overlap; the five climate dimensions must share
     * more than a face.
     */
    private static boolean overlaps(Climate.Parameter[] a, Climate.Parameter[] b) {
        for (int d = 0; d < a.length; d++) {
            boolean depth = d == 4;
            boolean hit = depth
                    ? a[d].min() <= b[d].max() && b[d].min() <= a[d].max()
                    : a[d].min() < b[d].max() && b[d].min() < a[d].max();
            if (!hit) {
                return false;
            }
        }
        return true;
    }

    private static Climate.Parameter[] dims(Climate.ParameterPoint p) {
        return new Climate.Parameter[] {p.temperature(), p.humidity(), p.continentalness(), p.erosion(),
                p.depth(), p.weirdness()};
    }

    private static Climate.ParameterPoint point(Climate.Parameter[] d, long offset) {
        return new Climate.ParameterPoint(d[0], d[1], d[2], d[3], d[4], d[5], offset);
    }
}
