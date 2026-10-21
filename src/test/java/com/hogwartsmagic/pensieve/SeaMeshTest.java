package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.RoughSea;
import com.hogwartsmagic.pensieve.scene.Rowboat;
import com.hogwartsmagic.pensieve.scene.SeaMesh;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeaMeshTest {
    /** The brief's sea: 120 x 120 blocks, intensity 1. */
    private static final RoughSea.Sea SEA = new RoughSea.Sea(0, 0, 0, 60, 60, 1.0, 0f, 0, 0x5EA5EAL);

    @Test
    void vertexCountIsBoundedAndTheSeaIsCovered() {
        SeaMesh m = new SeaMesh();
        for (float t = 0; t < 400; t += 37) {
            for (double[] cam : new double[][]{{0, 2, 0}, {31.7, 1.5, -12.2}, {-59, 6, 59}, {90, 20, 0}}) {
                m.build(SEA, t, cam[0], cam[1], cam[2], 0f, null);
                assertTrue(m.quadCount * 4 <= 16384, "water vertices " + m.quadCount * 4);
                assertTrue(m.foamQuadCount <= SeaMesh.FOAM_QUAD_CAP);
                assertTrue(m.vertexCount() < 20000, "vertices " + m.vertexCount());
                // With boats, only the patches' own budget is added.
                m.build(SEA, t, cam[0], cam[1], cam[2], 0f, null, BOATS, 4);
                assertTrue(m.patchQuadCount <= SeaMesh.PATCH_QUAD_CAP);
                assertTrue(m.vertexCount() < 20000 + SeaMesh.PATCH_QUAD_CAP * 4, "vertices with boats " + m.vertexCount());
            }
        }
        // From the middle, every square block of the 120 x 120 area is under some quad.
        m.build(SEA, 100f, 0, 2, 0, 0f, null);
        double area = 0;
        for (int n = 0; n < m.quadCount; n++) {
            int a = m.quads[n * 4], c = m.quads[n * 4 + 2];
            area += Math.abs(m.x[c] - m.x[a]) * Math.abs(m.z[c] - m.z[a]);
        }
        assertEquals(120.0 * 120.0, area, 1e-3, "the quads tile the area exactly once");
    }

    /** The renderer's exact-water margin past the hull (RoughSeaRenderer.BOAT_MARGIN). */
    private static final double RADIUS_MARGIN = 2.0;
    /** Four boats on the 120 x 120 sea, sea-local {x, z, radius} (radius: hull half-length 2 plus the margin). */
    private static final double[] BOATS = {40, 30, 4, -45, 10, 4, 10, -50, 4, -20, 52, 4};

    /** A 200 x 200 sea, so a boat can sit 100 blocks from the camera. */
    private static final RoughSea.Sea BIG = new RoughSea.Sea(0, 0, 0, 100, 100, 1.0, 0f, 0, 0xB0A7L);

    /** The drawn surface at (x, z): the quad under it, split into the two triangles the GPU draws (a b c, a c d). */
    private static double drawn(SeaMesh m, double x, double z) {
        for (int n = 0; n < m.quadCount; n++) {
            int[] v = {m.quads[n * 4], m.quads[n * 4 + 1], m.quads[n * 4 + 2], m.quads[n * 4 + 3]};
            for (int[] tri : new int[][]{{v[0], v[1], v[2]}, {v[0], v[2], v[3]}}) {
                double x0 = m.x[tri[0]], z0 = m.z[tri[0]], x1 = m.x[tri[1]], z1 = m.z[tri[1]], x2 = m.x[tri[2]], z2 = m.z[tri[2]];
                double det = (z1 - z2) * (x0 - x2) + (x2 - x1) * (z0 - z2);
                if (Math.abs(det) < 1e-12) continue;
                double l0 = ((z1 - z2) * (x - x2) + (x2 - x1) * (z - z2)) / det;
                double l1 = ((z2 - z0) * (x - x2) + (x0 - x2) * (z - z2)) / det;
                double l2 = 1 - l0 - l1;
                if (l0 < -1e-7 || l1 < -1e-7 || l2 < -1e-7) continue;
                return l0 * m.y[tri[0]] + l1 * m.y[tri[1]] + l2 * m.y[tri[2]];
            }
        }
        throw new AssertionError("no water drawn at " + x + ", " + z);
    }

    /** The worst gap between the drawn water and the exact surface under a hull's waterline, over many poses. */
    private static double worstUnderHull(boolean patch) {
        SeaMesh m = new SeaMesh();
        java.util.Random r = new java.util.Random(11);
        double worst = 0;
        for (int i = 0; i < 40; i++) {
            float t = r.nextFloat() * 2000f;
            double bx = 5 + r.nextDouble() * 10, bz = r.nextDouble() * 40 - 20;
            float yaw = r.nextFloat() * 360f;
            double[] boats = {bx, bz, Rowboat.HALF_LENGTH + RADIUS_MARGIN};
            m.build(BIG, t, -95, 6, 0, 0f, null, patch ? boats : null, patch ? 1 : 0);
            Rowboat.Pose pose = Rowboat.ride(BIG, bx, 0, bz, yaw, 1f, t);
            for (double[] p : Rowboat.WATERLINE) {
                double[] w = Rowboat.toWorld(pose, p);
                double exact = BIG.surface(w[0], w[2], t);
                worst = Math.max(worst, Math.abs(drawn(m, w[0], w[2]) - exact));
            }
        }
        return worst;
    }

    /** Wide shots: a boat 100 blocks from the camera sits on exact water, not the 8-block outer ring. */
    @Test
    void waterUnderAFarBoatIsExact() {
        double with = worstUnderHull(true), without = worstUnderHull(false);
        System.out.printf("Far boat: drawn water under the hull off by %.4f with the patch, %.4f without%n", with, without);
        assertTrue(with <= 0.05, "drawn water under the hull is off by " + with);
        assertTrue(without > 0.1, "the test would not catch a coarse ring (" + without + ")");
    }

    /** A sea away from the scene origin: boats are bound by their scene position, the patch built in sea-local ones. */
    @Test
    void boatsOnAnOffsetSeaArePatched() {
        RoughSea.Sea off = new RoughSea.Sea(37, 0, -23, 100, 100, 1.0, 0f, 0, 0xC0FFEEL);
        double[] boats = new double[SeaMesh.MAX_BOATS * 3];
        assertEquals(1, SeaMesh.addBoat(off, 47, -18, 4, boats, 0));          // over the sea: sea-local (10, 5)
        assertEquals(10.0, boats[0], 1e-12);
        assertEquals(5.0, boats[1], 1e-12);
        assertEquals(0, SeaMesh.addBoat(off, 147, -23, 4, boats, 0));          // 110 east of the centre: off the sea
        // The far-boat check through the whole chain: scene position, binding, patch, drawn water under the hull.
        SeaMesh m = new SeaMesh();
        java.util.Random r = new java.util.Random(5);
        double worst = 0;
        for (int i = 0; i < 25; i++) {
            float t = r.nextFloat() * 2000f;
            double sx = 37 + 5 + r.nextDouble() * 10, sz = -23 + r.nextDouble() * 40 - 20;
            int n = SeaMesh.addBoat(off, sx, sz, Rowboat.HALF_LENGTH + RADIUS_MARGIN, boats, 0);
            assertEquals(1, n);
            m.build(off, t, -95, 6, 0, 0f, null, boats, n);
            Rowboat.Pose pose = Rowboat.ride(off, sx, 0, sz, r.nextFloat() * 360f, 1f, t);
            for (double[] p : Rowboat.WATERLINE) {
                double[] w = Rowboat.toWorld(pose, p);
                double exact = off.surface(w[0], w[2], t) - off.cy();
                worst = Math.max(worst, Math.abs(drawn(m, w[0] - off.cx(), w[2] - off.cz()) - exact));
            }
        }
        assertTrue(worst <= 0.05, "offset sea: drawn water under the hull is off by " + worst);
    }

    /**
     * Moving shots: the patch fades into the coarse surface, so beyond a boat's radius plus a cell nothing differs from
     * the coarse water, and as the boat moves a little the drawn water near it changes only a little (no cell pops).
     */
    @Test
    void thePatchBlendsIntoTheRingsWithoutPopping() {
        SeaMesh with = new SeaMesh(), without = new SeaMesh(), moved = new SeaMesh();
        java.util.Random r = new java.util.Random(9);
        double far = 0, pop = 0;
        for (int i = 0; i < 12; i++) {
            float t = r.nextFloat() * 1500f;
            double bx = 5 + r.nextDouble() * 10, bz = r.nextDouble() * 30 - 15, rad = Rowboat.HALF_LENGTH + RADIUS_MARGIN;
            with.build(BIG, t, -95, 6, 0, 0f, null, new double[]{bx, bz, rad}, 1);
            without.build(BIG, t, -95, 6, 0, 0f, null);
            moved.build(BIG, t, -95, 6, 0, 0f, null, new double[]{bx + 0.37, bz - 0.21, rad}, 1);
            for (int k = 0; k < 300; k++) {
                // Within the outer ring's reach (the camera at x -95 draws water up to x 32).
                double px = Math.min(30, bx + (r.nextDouble() * 2 - 1) * 30), pz = bz + (r.nextDouble() * 2 - 1) * 30;
                double d = Math.max(Math.abs(px - bx), Math.abs(pz - bz));
                double h = drawn(with, px, pz);
                if (d >= rad + 8.5) far = Math.max(far, Math.abs(h - drawn(without, px, pz)));
                pop = Math.max(pop, Math.abs(h - drawn(moved, px, pz)));
            }
        }
        System.out.printf("Patch blend: %.6f off the coarse water beyond radius + a cell, %.4f change for a 0.43-block move%n", far, pop);
        assertTrue(far < 1e-4, "the patch changes the water beyond its blend by " + far);
        assertTrue(pop < 0.06, "a small boat move changes the drawn water by " + pop);
    }

    /**
     * Round a boat patch nothing is cracked: every vertex that lies inside another quad's edge (a T-junction, at ring
     * seams and patch borders) has exactly that edge's straight-line height.
     */
    @Test
    void boatPatchesMeetTheRingsWithoutCracks() {
        SeaMesh m = new SeaMesh();
        int checked = 0;
        for (float t = 0; t < 600; t += 97) {
            double bx = 12 + t * 0.01, bz = -7;
            m.build(BIG, t, -95, 6, 0, 0f, null, new double[]{bx, bz, 4}, 1);
            assertTrue(m.patchQuadCount > 0, "no patch built");
            java.util.List<int[]> edges = new java.util.ArrayList<>();
            java.util.Set<Integer> verts = new java.util.HashSet<>();
            for (int n = 0; n < m.quadCount; n++) {
                int[] v = {m.quads[n * 4], m.quads[n * 4 + 1], m.quads[n * 4 + 2], m.quads[n * 4 + 3]};
                boolean near = false;
                for (int k : v) near |= Math.abs(m.x[k] - bx) < 24 && Math.abs(m.z[k] - bz) < 24;
                if (!near) continue;
                for (int e = 0; e < 4; e++) edges.add(new int[]{v[e], v[(e + 1) % 4]});
                for (int k : v) verts.add(k);
            }
            for (int k : verts) {
                for (int[] e : edges) {
                    double ax = m.x[e[0]], az = m.z[e[0]], cx = m.x[e[1]], cz = m.z[e[1]];
                    double len = Math.hypot(cx - ax, cz - az);
                    if (len < 1e-9) continue;
                    double u = ((m.x[k] - ax) * (cx - ax) + (m.z[k] - az) * (cz - az)) / (len * len);
                    if (u <= 1e-6 || u >= 1 - 1e-6) continue;
                    double off = Math.abs((m.x[k] - ax) * (cz - az) - (m.z[k] - az) * (cx - ax)) / len;
                    if (off > 1e-5) continue;
                    double want = m.y[e[0]] + (m.y[e[1]] - m.y[e[0]]) * u;
                    assertEquals(want, m.y[k], 1e-4, "crack at (" + m.x[k] + ", " + m.z[k] + ")");
                    checked++;
                }
            }
        }
        assertTrue(checked > 50, "only " + checked + " T-junctions checked");
    }

    /** Each finer ring's rim takes the coarser ring's straight-line heights, so no crack opens between rings. */
    @Test
    void ringsMeetWithoutCracks() {
        SeaMesh m = new SeaMesh();
        int checked = 0;
        for (float t = 0; t < 300; t += 53) {
            m.build(SEA, t, 7.3, 2, -4.6, 0f, null);
            for (int L = 0; L < SeaMesh.LEVELS - 1; L++) {
                double s = SeaMesh.spacing(L), cs = SeaMesh.spacing(L + 1);
                for (int j = 0; j <= m.nz(L); j++) {
                    for (int i = 0; i <= m.nx(L); i++) {
                        boolean rim = i == 0 || j == 0 || i == m.nx(L) || j == m.nz(L);
                        if (!rim) continue;
                        int k = m.index(L, i, j);
                        double x = m.x[k], z = m.z[k];
                        if (Math.abs(x) >= 59.99 || Math.abs(z) >= 59.99) continue;   // the area edge, not a ring seam
                        // The coarse ring's surface along that edge: its vertices either side.
                        double ci = x / cs, cj = z / cs;
                        int ai = (int) Math.floor(ci - m.i0(L + 1) + 1e-9), aj = (int) Math.floor(cj - m.j0(L + 1) + 1e-9);
                        double fi = ci - m.i0(L + 1) - ai, fj = cj - m.j0(L + 1) - aj;
                        if (ai < 0 || aj < 0 || ai > m.nx(L + 1) || aj > m.nz(L + 1)) continue;
                        double h;
                        if (fi < 1e-6 && fj < 1e-6) {
                            h = m.y[m.index(L + 1, ai, aj)];
                        } else if (fj < 1e-6) {
                            h = m.y[m.index(L + 1, ai, aj)] * (1 - fi) + m.y[m.index(L + 1, ai + 1, aj)] * fi;
                        } else if (fi < 1e-6) {
                            h = m.y[m.index(L + 1, ai, aj)] * (1 - fj) + m.y[m.index(L + 1, ai, aj + 1)] * fj;
                        } else {
                            continue;
                        }
                        assertEquals(h, m.y[k], 1e-5, "crack at level " + L + " (" + x + ", " + z + ") spacing " + s);
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 500, "only " + checked + " seam vertices checked");
    }

    @Test
    void repeatedBuildsAllocateNothingAndAreFast() {
        SeaMesh m = new SeaMesh();
        SeaMesh.Shore shore = (x, z) -> Math.abs(x - 20) < 3 && Math.abs(z - 20) < 3 ? 1f : 0f;
        for (int i = 0; i < 200; i++) m.build(SEA, i * 0.5f, 3, 2, -2, 0.2f, shore, BOATS, 4);   // warm up the JIT
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().getId();
        int runs = 200;
        long before = bean.getThreadAllocatedBytes(tid);
        long start = System.nanoTime();
        for (int i = 0; i < runs; i++) m.build(SEA, 100 + i * 0.5f, 3 + i * 0.01, 2, -2, 0.2f, shore, BOATS, 4);
        long nanos = System.nanoTime() - start;
        long allocated = bean.getThreadAllocatedBytes(tid) - before;
        double ms = nanos / 1e6 / runs;
        System.out.printf("SeaMesh 120x120 with 4 boats: %d water (%d patch) + %d foam quads (%d vertices), %.3f ms per build, %d bytes over %d builds%n",
                m.quadCount, m.patchQuadCount, m.foamQuadCount, m.vertexCount(), ms, allocated, runs);
        // A handful of bytes may come from the measurement itself; a per-vertex array would be megabytes.
        assertTrue(allocated < 4096, "builds allocated " + allocated + " bytes");
        assertTrue(ms < 8.0, "a build took " + ms + " ms");
    }

    /**
     * Guard for the shared-BufferSource trap: taking a second consumer closes the first, so the boat renderer may take
     * a buffer in exactly one place (its Mesh.flush, which writes and is done before the next).
     */
    @Test
    void theBoatRendererTakesABufferInOnePlaceOnly() throws Exception {
        String src = Files.readString(Path.of("src/main/java/com/hogwartsmagic/client/MemoryRowboatRenderer.java"),
                StandardCharsets.UTF_8);
        int count = src.split("getBuffer\\(", -1).length - 1;
        assertEquals(1, count, "MemoryRowboatRenderer must take buffers only in Mesh.flush");
        int at = src.indexOf("getBuffer(");
        assertTrue(src.lastIndexOf("void flush(", at) > 0 && at - src.lastIndexOf("void flush(", at) < 200);
    }
}
