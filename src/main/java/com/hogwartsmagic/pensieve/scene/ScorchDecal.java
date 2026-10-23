package com.hogwartsmagic.pensieve.scene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The memory Horntail's scorch decals, the pure part ({@link DragonFire}'s soot and embers, drawn by
 * {@code client/pensieve/DragonFireRenderer}): each scorched block face split {@link #SPLIT} x {@link #SPLIT}, its
 * alpha falling off with the distance from the spot's centre, baked once for a spot's radius and replayed every frame
 * with only the camera offset added. The frame's cost is bounded: spots are drawn nearest first while their cells fit
 * in {@link #MAX_FRAME_CELLS} a pass ({@link #plan}), and only those in view within {@link #CULL_DISTANCE}.
 */
public final class ScorchDecal {
    private ScorchDecal() {
    }

    /** A face is split this many cells a side. */
    public static final int SPLIT = 3;
    /** At most this many scorch spots a jet, and faces a spot. */
    public static final int MAX_SPOTS = 24, MAX_FACES = 120;
    /** At most this many decal cells drawn a pass (soot, then embers) a frame, over every jet. */
    public static final int MAX_FRAME_CELLS = 6000;
    /** A spot farther than this from the camera (blocks, plus its radius) is not drawn. */
    public static final double CULL_DISTANCE = 96;
    /** At most this many spots have their faces listed a frame (the rest wait a frame: no hitch on joining). */
    public static final int MAX_BUILDS_PER_FRAME = 2;
    /** The decals sit this far off their face, and further with the camera's distance (depth precision). */
    public static final double LIFT = 0.012, LIFT_PER_BLOCK = 1.5e-4;
    /** The soot texture's world-space scale (texture repeats a block). */
    public static final double UV_SCALE = 0.42;

    /**
     * A block face near a scorch spot: its four corners (world) in order round it, its outward normal (a unit axis),
     * a hash for the embers' flicker and the block it belongs to ({@code BlockPos.asLong}).
     */
    public record Face(double[] c0, double[] c1, double[] c2, double[] c3, int nx, int ny, int nz, int hash, long block) {
        /** A point on the face at (u, v), 0..1 each way. */
        public double[] at(double u, double v) {
            double[] out = new double[3];
            for (int i = 0; i < 3; i++) {
                double top = c0[i] + (c1[i] - c0[i]) * u;
                double bot = c3[i] + (c2[i] - c3[i]) * u;
                out[i] = top + (bot - top) * v;
            }
            return out;
        }
    }

    /**
     * A spot's decal for one reach, ready to replay: per cell its four corners relative to the spot's centre
     * ({@code xyz}, 12 floats), their fall-off 0..1 ({@code fall}, 4), texture coordinates ({@code uv}, 8) and the
     * index of its face ({@code face}).
     */
    public static final class Baked {
        public final double reach;
        public final int cells;
        public final float[] xyz, fall, uv;
        public final int[] face;

        Baked(double reach, int cells, float[] xyz, float[] fall, float[] uv, int[] face) {
            this.reach = reach;
            this.cells = cells;
            this.xyz = xyz;
            this.fall = fall;
            this.uv = uv;
            this.face = face;
        }
    }

    /** The fall-off at {@code x} = distance / reach: 1 to half the reach, smoothly 0 at the reach. */
    public static double fall(double x) {
        return 1 - DragonFire.smooth((x - 0.5) / 0.5);
    }

    /** The lift off the face for a camera {@code distance} blocks away. */
    public static double lift(double distance) {
        return Math.max(LIFT, LIFT_PER_BLOCK * distance);
    }

    /**
     * Bakes the cells of {@code faces} round the spot centred at (cx, cy, cz) out to {@code reach}: a cell all of
     * whose corners have faded to nothing is left out. At most {@code faces.size() x SPLIT x SPLIT} cells.
     */
    public static Baked bake(List<Face> faces, double cx, double cy, double cz, double reach) {
        int max = faces.size() * SPLIT * SPLIT;
        float[] xyz = new float[max * 12], fall = new float[max * 4], uv = new float[max * 8];
        int[] face = new int[max];
        // The texture is laid in world space, from a corner a whole repeat below the centre, so faces join.
        double ax = Math.floor(cx * UV_SCALE) / UV_SCALE, ay = Math.floor(cy * UV_SCALE) / UV_SCALE;
        double az = Math.floor(cz * UV_SCALE) / UV_SCALE;
        int cells = 0;
        double[] fl = new double[4];
        double[][] q = new double[4][];
        for (int fi = 0; fi < faces.size(); fi++) {
            Face f = faces.get(fi);
            for (int a = 0; a < SPLIT; a++) {
                for (int b = 0; b < SPLIT; b++) {
                    q[0] = f.at((double) a / SPLIT, (double) b / SPLIT);
                    q[1] = f.at((double) (a + 1) / SPLIT, (double) b / SPLIT);
                    q[2] = f.at((double) (a + 1) / SPLIT, (double) (b + 1) / SPLIT);
                    q[3] = f.at((double) a / SPLIT, (double) (b + 1) / SPLIT);
                    boolean any = false;
                    for (int i = 0; i < 4; i++) {
                        double dx = q[i][0] - cx, dy = q[i][1] - cy, dz = q[i][2] - cz;
                        fl[i] = reach <= 0 ? 0 : fall(Math.sqrt(dx * dx + dy * dy + dz * dz) / reach);
                        any |= fl[i] > 0.003;
                    }
                    if (!any) continue;
                    for (int i = 0; i < 4; i++) {
                        xyz[cells * 12 + i * 3] = (float) (q[i][0] - cx);
                        xyz[cells * 12 + i * 3 + 1] = (float) (q[i][1] - cy);
                        xyz[cells * 12 + i * 3 + 2] = (float) (q[i][2] - cz);
                        fall[cells * 4 + i] = (float) fl[i];
                        double u, v;
                        if (f.ny() != 0) {
                            u = q[i][0] - ax;
                            v = q[i][2] - az;
                        } else if (f.nx() != 0) {
                            u = q[i][2] - az;
                            v = q[i][1] - ay;
                        } else {
                            u = q[i][0] - ax;
                            v = q[i][1] - ay;
                        }
                        uv[cells * 8 + i * 2] = (float) (u * UV_SCALE);
                        uv[cells * 8 + i * 2 + 1] = (float) (v * UV_SCALE);
                    }
                    face[cells] = fi;
                    cells++;
                }
            }
        }
        return new Baked(reach, cells, xyz, fall, uv, face);
    }

    /**
     * Which spots to draw this pass: those {@code visible}, within {@link #CULL_DISTANCE} plus their {@code radius},
     * nearest first, while their {@code cells} add up to at most {@code cap}; a spot that does not fit is skipped
     * and a smaller farther one may still fit. Returns their indices, nearest first.
     */
    public static int[] plan(double[] distance, double[] radius, boolean[] visible, int[] cells, int cap) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < distance.length; i++) {
            if (visible[i] && distance[i] <= CULL_DISTANCE + radius[i] && cells[i] > 0) order.add(i);
        }
        order.sort(Comparator.comparingDouble(i -> distance[i]));
        int[] out = new int[order.size()];
        int n = 0, used = 0;
        for (int i : order) {
            if (used + cells[i] > cap) continue;
            used += cells[i];
            out[n++] = i;
        }
        return java.util.Arrays.copyOf(out, n);
    }
}
