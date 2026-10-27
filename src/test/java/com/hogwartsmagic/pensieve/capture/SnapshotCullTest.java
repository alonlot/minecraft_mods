package com.hogwartsmagic.pensieve.capture;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapshotCullTest {
    private static final int SIZE = 2;
    private static final int D = 5;
    private static final int STONE = 7;

    private static int idx(int x, int y, int z) {
        return ((x + SIZE) * D + (y + SIZE)) * D + (z + SIZE);
    }

    private static boolean inSphere(int x, int y, int z) {
        return x * x + y * y + z * z <= 4;
    }

    private static int[] cube() {
        int[] c = new int[D * D * D];
        Arrays.fill(c, STONE);
        return c;
    }

    @Test
    void keepsOnlySphereSurface() {
        int[] cells = cube();
        SnapshotCull.cull(cells, SIZE, 2, s -> s == STONE, Set.of());
        int kept = 0;
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    boolean surface = inSphere(x, y, z) && !(inSphere(x + 1, y, z) && inSphere(x - 1, y, z)
                            && inSphere(x, y + 1, z) && inSphere(x, y - 1, z)
                            && inSphere(x, y, z + 1) && inSphere(x, y, z - 1));
                    assertEquals(surface ? STONE : -1, cells[idx(x, y, z)], x + "," + y + "," + z);
                    if (surface) {
                        kept++;
                    }
                }
            }
        }
        assertEquals(26, kept);
        assertEquals(-1, cells[idx(0, 0, 0)]);
        assertEquals(-1, cells[idx(2, 2, 2)]);
    }

    @Test
    void keepSurvivesBurying() {
        int[] cells = cube();
        SnapshotCull.cull(cells, SIZE, 2, s -> s == STONE, Set.of(idx(0, 0, 0)));
        assertEquals(STONE, cells[idx(0, 0, 0)]);
    }

    @Test
    void airIsCulledAndExposesNeighbour() {
        int[] cells = cube();
        cells[idx(1, 0, 0)] = 0;
        SnapshotCull.cull(cells, SIZE, 2, s -> s == STONE, Set.of());
        assertEquals(-1, cells[idx(1, 0, 0)]);
        // the centre now touches air, so it is no longer fully hidden
        assertEquals(STONE, cells[idx(0, 0, 0)]);
    }
}
