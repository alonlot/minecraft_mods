package com.hogwartsmagic.pensieve.capture;

/** Keeps replayed actors on the memory island. */
public final class IslandBounds {
    private IslandBounds() {}

    public static double[] clamp(double x, double y, double z, double cx, double cy, double cz, double radius) {
        double dx = x - cx;
        double dy = y - cy;
        double dz = z - cz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len <= radius) {
            return new double[] {x, y, z};
        }
        double k = radius / len;
        return new double[] {cx + dx * k, cy + dy * k, cz + dz * k};
    }

    public static boolean outside(double x, double y, double z, double cx, double cy, double cz, double limit) {
        double dx = x - cx;
        double dy = y - cy;
        double dz = z - cz;
        return dx * dx + dy * dy + dz * dz > limit * limit;
    }
}
