package com.hogwartsmagic.spell.damage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The block offsets a spherical blast touches, nearest first, so breaking ripples outward. */
public final class BlastShape {
    public static final double MAX_RADIUS = 4.0;

    private BlastShape() {
    }

    /** Every {dx, dy, dz} with dx² + dy² + dz² ≤ radius²; NaN and negatives mean 0, the radius is capped at 4. */
    public static List<int[]> offsets(double radius) {
        double r = Double.isNaN(radius) ? 0.0 : Math.max(0.0, Math.min(MAX_RADIUS, radius));
        int reach = (int) Math.floor(r);
        double limit = r * r + 1.0e-9;
        List<int[]> out = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= limit) {
                        out.add(new int[]{dx, dy, dz});
                    }
                }
            }
        }
        out.sort(Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1] + o[2] * o[2])
                .thenComparingInt(o -> o[1])
                .thenComparingInt(o -> o[0])
                .thenComparingInt(o -> o[2]));
        return out;
    }
}
