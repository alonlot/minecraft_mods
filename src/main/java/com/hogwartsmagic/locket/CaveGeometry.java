package com.hogwartsmagic.locket;

/** Bounds relative to the basin. Pure. */
public final class CaveGeometry {
    public static final int ISLAND_RADIUS = 6;
    public static final int ISLAND_DY = 3;
    public static final int CAVE_RADIUS = 34;
    public static final int CAVE_DOWN = 14;
    public static final int CAVE_UP = 26;

    /** The lake is the cavern ellipse (radii 24 x 20 around the basin) with a block of margin each way. */
    public static final int LAKE_RADIUS_X = 25;
    public static final int LAKE_RADIUS_Z = 21;
    /** Lake water is local Y 12..21 and the basin sits at Y 23, so a body in the water is 11..2 below it. */
    public static final int LAKE_MIN_DY = -11;
    public static final int LAKE_MAX_DY = -1;

    private CaveGeometry() {}

    /**
     * The lake itself: unlike {@link #inCave} this excludes the antechamber, its entry pool (dz about -30)
     * and the tunnel, and anything standing above the water.
     */
    public static boolean inLake(int dx, int dy, int dz) {
        if (dy < LAKE_MIN_DY || dy > LAKE_MAX_DY) {
            return false;
        }
        long rx = LAKE_RADIUS_X;
        long rz = LAKE_RADIUS_Z;
        return (long) dx * dx * rz * rz + (long) dz * dz * rx * rx <= rx * rx * rz * rz;
    }

    public static boolean onIsland(int dx, int dy, int dz) {
        return dx * dx + dz * dz <= ISLAND_RADIUS * ISLAND_RADIUS && Math.abs(dy) <= ISLAND_DY;
    }

    public static boolean inCave(int dx, int dy, int dz) {
        return Math.abs(dx) <= CAVE_RADIUS && Math.abs(dz) <= CAVE_RADIUS && dy >= -CAVE_DOWN && dy <= CAVE_UP;
    }
}
