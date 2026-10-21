package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.RoughSea;
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
        for (int i = 0; i < 200; i++) m.build(SEA, i * 0.5f, 3, 2, -2, 0.2f, shore);   // warm up the JIT
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().getId();
        int runs = 200;
        long before = bean.getThreadAllocatedBytes(tid);
        long start = System.nanoTime();
        for (int i = 0; i < runs; i++) m.build(SEA, 100 + i * 0.5f, 3 + i * 0.01, 2, -2, 0.2f, shore);
        long nanos = System.nanoTime() - start;
        long allocated = bean.getThreadAllocatedBytes(tid) - before;
        double ms = nanos / 1e6 / runs;
        System.out.printf("SeaMesh 120x120: %d water + %d foam quads (%d vertices), %.3f ms per build, %d bytes over %d builds%n",
                m.quadCount, m.foamQuadCount, m.vertexCount(), ms, allocated, runs);
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
