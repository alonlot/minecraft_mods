package com.hogwartsmagic.pensieve.capture;

import java.util.Set;
import java.util.function.IntPredicate;

/** Drops cells that can never be seen: outside the sphere, air, or buried in opaque neighbours. */
public final class SnapshotCull {
    private SnapshotCull() {}

    /**
     * cells is a (2*size+1)^3 grid indexed (x*d+y)*d+z with coordinates offset by size from the centre.
     * Culled cells become -1. keep holds indices that change during the window and must survive burying.
     * A neighbour outside the sphere or the grid is not kept, so it hides nothing.
     */
    public static void cull(int[] cells, int size, int radius, IntPredicate opaque, Set<Integer> keep) {
        int d = 2 * size + 1;
        int[] before = cells.clone();
        long r2 = (long) radius * radius;
        for (int x = 0; x < d; x++) {
            for (int y = 0; y < d; y++) {
                for (int z = 0; z < d; z++) {
                    int i = (x * d + y) * d + z;
                    if (!inside(d, size, r2, x, y, z) || before[i] == 0) {
                        cells[i] = -1;
                    } else if (!keep.contains(i) && buried(before, d, size, r2, x, y, z, opaque)) {
                        cells[i] = -1;
                    }
                }
            }
        }
    }

    private static boolean inside(int d, int size, long r2, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= d || y >= d || z >= d) {
            return false;
        }
        long dx = x - size;
        long dy = y - size;
        long dz = z - size;
        return dx * dx + dy * dy + dz * dz <= r2;
    }

    private static boolean buried(int[] before, int d, int size, long r2, int x, int y, int z, IntPredicate opaque) {
        return hides(before, d, size, r2, x + 1, y, z, opaque) && hides(before, d, size, r2, x - 1, y, z, opaque)
                && hides(before, d, size, r2, x, y + 1, z, opaque) && hides(before, d, size, r2, x, y - 1, z, opaque)
                && hides(before, d, size, r2, x, y, z + 1, opaque) && hides(before, d, size, r2, x, y, z - 1, opaque);
    }

    private static boolean hides(int[] before, int d, int size, long r2, int x, int y, int z, IntPredicate opaque) {
        if (!inside(d, size, r2, x, y, z)) {
            return false;
        }
        int s = before[(x * d + y) * d + z];
        return s > 0 && opaque.test(s);
    }
}
