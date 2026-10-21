package com.hogwartsmagic.pensieve.scene;

/**
 * Geometry of a {@code memory_door} (or {@code memory_door_mirrored}) leaf. At scale 1 the leaf is
 * {@link #WIDTH} wide (local +X from the hinge), {@link #HEIGHT} tall and {@link #THICKNESS} thick (local +Z).
 * Its origin is the hinge's bottom corner on the near face, so rotating by yaw swings the leaf about that edge.
 *
 * <p>Corner index = {@code y | x << 1 | z << 2}, each bit 0 = near the origin, 1 = far. Corners 0 and 1
 * therefore are the hinge edge, bottom and top.
 *
 * <p>Yaw convention: a corner at local (x, z) lands at world offset
 * {@code (x cos yaw + z sin yaw, y, -x sin yaw + z cos yaw)}. Yaw 0 puts the free edge on +X, yaw -90 on
 * +Z, yaw 90 on -Z and yaw 180 on -X.
 *
 * <p>The mirrored leaf is the same box with local x negated (it extends from the hinge toward local -X, the
 * thickness stays on +Z), so it turns the other way: yaw 0 puts its free edge on -X, yaw -90 on -Z, yaw 90 on
 * +Z and yaw 180 on +X. Mirroring reverses the winding of every face, so {@link #winding} re-winds them.
 * A pair is one of each at the same yaw: both leaves' thickness is then on the same side.
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
        return corner(yawDeg, scale, corner, false);
    }

    /** As {@link #corner(float, float, int)}, for the mirrored leaf when {@code mirrored}. */
    public static double[] corner(float yawDeg, float scale, int corner, boolean mirrored) {
        return corner(yawDeg, 0f, scale, corner, mirrored);
    }

    /** As {@link #corner(float, float, int, boolean)}, with the leaf also tipped by {@code pitchDeg} (see below). */
    public static double[] corner(float yawDeg, float pitchDeg, float scale, int corner, boolean mirrored) {
        double x = (corner & 2) != 0 ? WIDTH * scale : 0.0;
        double y = (corner & 1) != 0 ? HEIGHT * scale : 0.0;
        double z = (corner & 4) != 0 ? THICKNESS * scale : 0.0;
        return toWorld(yawDeg, pitchDeg, mirrored, THICKNESS * scale, x, y, z);
    }

    /**
     * Pitch: the leaf tips about its bottom edge on the front face (the line along local X at y 0, z
     * {@link #THICKNESS}), applied in the leaf frame before the yaw. Pitch 0 is upright; -90 has the top fall
     * toward local +Z until the leaf lies flat on its front face, a slab of its own thickness on the floor, the
     * top edge a leaf-height from the bottom edge. Pitch 0 gives exactly the yaw-only leaf.
     */
    public static double[] toWorld(float yawDeg, float pitchDeg, boolean mirrored, double pivotZ,
                                   double x, double y, double z) {
        if (pitchDeg != 0f) {
            double a = Math.toRadians(-pitchDeg);
            double sa = Math.sin(a), ca = Math.cos(a);
            double dz = z - pivotZ;
            double ny = y * ca - dz * sa;
            double nz = pivotZ + y * sa + dz * ca;
            y = ny;
            z = nz;
        }
        return toWorld(yawDeg, mirrored, x, y, z);
    }

    /** A point at scale 1 in the leaf frame, tipped by pitch and turned by yaw, as a world offset from the hinge. */
    public static double[] point(float yawDeg, float pitchDeg, boolean mirrored, double x, double y, double z) {
        return toWorld(yawDeg, pitchDeg, mirrored, THICKNESS, x, y, z);
    }

    /** A direction (a normal) in the leaf frame, tipped by pitch and turned by yaw: no pivot, so no offset. */
    public static double[] direction(float yawDeg, float pitchDeg, boolean mirrored, double x, double y, double z) {
        return toWorld(yawDeg, pitchDeg, mirrored, 0.0, x, y, z);
    }

    /** A point or direction in the leaf frame (x along the leaf, z through it) as a world offset from the hinge. */
    public static double[] toWorld(float yawDeg, boolean mirrored, double x, double y, double z) {
        if (mirrored) x = -x;
        double r = Math.toRadians(yawDeg);
        double s = Math.sin(r), c = Math.cos(r);
        return new double[] {x * c + z * s, y, -x * s + z * c};
    }

    /**
     * The corner order to emit face {@code face} in so that it winds counter-clockwise seen from outside. Mirroring
     * reverses the winding, so the mirrored leaf emits {@code q0, q3, q2, q1}; each corner keeps its own texture
     * coordinate, so the straps stay on the hinge side.
     */
    public static int[] winding(int face, boolean mirrored) {
        int[] q = FACES[face];
        return mirrored ? new int[] {q[0], q[3], q[2], q[1]} : q.clone();
    }

    /** Unit outward normal of face {@code face} (an index into {@link #FACES}) from its corner winding. */
    public static double[] faceNormal(float yawDeg, float scale, int face) {
        return faceNormal(yawDeg, scale, face, false);
    }

    /** As {@link #faceNormal(float, float, int)}, from the winding {@link #winding} gives. */
    public static double[] faceNormal(float yawDeg, float scale, int face, boolean mirrored) {
        return faceNormal(yawDeg, 0f, scale, face, mirrored);
    }

    /** As {@link #faceNormal(float, float, int, boolean)}, for the leaf tipped by {@code pitchDeg}. */
    public static double[] faceNormal(float yawDeg, float pitchDeg, float scale, int face, boolean mirrored) {
        int[] q = winding(face, mirrored);
        double[] a = corner(yawDeg, pitchDeg, scale, q[0], mirrored);
        double[] b = corner(yawDeg, pitchDeg, scale, q[1], mirrored);
        double[] d = corner(yawDeg, pitchDeg, scale, q[3], mirrored);
        double[] u = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
        double[] v = {d[0] - a[0], d[1] - a[1], d[2] - a[2]};
        double nx = u[1] * v[2] - u[2] * v[1];
        double ny = u[2] * v[0] - u[0] * v[2];
        double nz = u[0] * v[1] - u[1] * v[0];
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return new double[] {nx / len, ny / len, nz / len};
    }
}
