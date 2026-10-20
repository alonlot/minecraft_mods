package com.hogwartsmagic.pensieve.scene;

/**
 * Geometry of a {@code memory_door} leaf. At scale 1 the leaf is {@link #WIDTH} wide (local +X from the
 * hinge), {@link #HEIGHT} tall and {@link #THICKNESS} thick (local +Z). Its origin is the hinge's bottom
 * corner on the near face, so rotating by yaw swings the leaf about that edge.
 *
 * <p>Corner index = {@code y | x << 1 | z << 2}, each bit 0 = near the origin, 1 = far. Corners 0 and 1
 * therefore are the hinge edge, bottom and top.
 *
 * <p>Yaw convention: a corner at local (x, z) lands at world offset
 * {@code (x cos yaw + z sin yaw, y, -x sin yaw + z cos yaw)}. Yaw 0 puts the free edge on +X, yaw -90 on
 * +Z, yaw 90 on -Z and yaw 180 on -X.
 */
public final class DoorHinge {
    public static final double WIDTH = 1.0;
    public static final double HEIGHT = 3.0;
    public static final double THICKNESS = 0.2;

    /**
     * The six faces as corner indices, each counter-clockwise seen from outside and starting at the
     * bottom-left as seen from outside: front (+z), back (-z), free edge (+x), hinge edge (-x), top, bottom.
     */
    public static final int[][] FACES = {
            {4, 6, 7, 5}, {2, 0, 1, 3}, {6, 2, 3, 7}, {0, 4, 5, 1}, {5, 7, 3, 1}, {0, 2, 6, 4},
    };

    private DoorHinge() {}

    /** World offset of a leaf corner from the hinge, for a leaf turned to {@code yawDeg} and scaled by {@code scale}. */
    public static double[] corner(float yawDeg, float scale, int corner) {
        double x = (corner & 2) != 0 ? WIDTH * scale : 0.0;
        double y = (corner & 1) != 0 ? HEIGHT * scale : 0.0;
        double z = (corner & 4) != 0 ? THICKNESS * scale : 0.0;
        double r = Math.toRadians(yawDeg);
        double s = Math.sin(r), c = Math.cos(r);
        return new double[] {x * c + z * s, y, -x * s + z * c};
    }

    /** Unit outward normal of face {@code face} (an index into {@link #FACES}) from its corner winding. */
    public static double[] faceNormal(float yawDeg, float scale, int face) {
        int[] q = FACES[face];
        double[] a = corner(yawDeg, scale, q[0]);
        double[] b = corner(yawDeg, scale, q[1]);
        double[] d = corner(yawDeg, scale, q[3]);
        double[] u = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        double[] v = {d[0] - a[0], d[1] - a[1], d[2] - a[2]};
        double nx = u[1] * v[2] - u[2] * v[1];
        double ny = u[2] * v[0] - u[0] * v[2];
        double nz = u[0] * v[1] - u[1] * v[0];
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return new double[] {nx / len, ny / len, nz / len};
    }
}
