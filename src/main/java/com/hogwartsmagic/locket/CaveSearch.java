package com.hogwartsmagic.locket;

import java.util.*;

/** Nearest unlooted cave start among candidate chunks. Pure. */
public final class CaveSearch {
    private CaveSearch() {}

    public static long key(long chunkX, long chunkZ) { return chunkX << 32 | (chunkZ & 0xffffffffL); }

    public static Optional<long[]> nearestUnlooted(long px, long pz, List<long[]> candidates, Set<Long> looted) {
        return candidates.stream()
                .filter(c -> !looted.contains(key(c[0], c[1])))
                .min(Comparator.comparingLong(c -> (c[0] - px) * (c[0] - px) + (c[1] - pz) * (c[1] - pz)));
    }
}
