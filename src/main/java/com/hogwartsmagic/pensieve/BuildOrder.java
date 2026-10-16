package com.hogwartsmagic.pensieve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The order a set is built in: outward from the centre in rings, each ring bottom to top. Pure. */
public final class BuildOrder {
    private BuildOrder() {
    }

    /** A new list sorted by squared horizontal distance from {@code center}, then y ascending; stable. */
    public static List<int[]> sortOutward(List<int[]> positions, int[] center) {
        List<int[]> out = new ArrayList<>(positions);
        out.sort(Comparator.<int[]>comparingLong(p -> {
            long dx = p[0] - center[0];
            long dz = p[2] - center[2];
            return dx * dx + dz * dz;
        }).thenComparingInt(p -> p[1]));
        return out;
    }
}
