package com.hogwartsmagic.locket;

/** Bounds relative to the basin. Pure. */
public final class CaveGeometry {
    public static final int ISLAND_RADIUS = 6;
    public static final int ISLAND_DY = 3;
    public static final int CAVE_RADIUS = 34;
    public static final int CAVE_DOWN = 14;
    public static final int CAVE_UP = 26;

    private CaveGeometry() {}

    public static boolean onIsland(int dx, int dy, int dz) {
        return dx * dx + dz * dz <= ISLAND_RADIUS * ISLAND_RADIUS && Math.abs(dy) <= ISLAND_DY;
    }

    public static boolean inCave(int dx, int dy, int dz) {
        return Math.abs(dx) <= CAVE_RADIUS && Math.abs(dz) <= CAVE_RADIUS && dy >= -CAVE_DOWN && dy <= CAVE_UP;
    }
}
