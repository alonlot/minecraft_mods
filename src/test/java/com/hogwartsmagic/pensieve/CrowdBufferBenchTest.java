package com.hogwartsmagic.pensieve;

import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.Crowd;
import com.hogwartsmagic.pensieve.scene.CrowdMesh;
import com.hogwartsmagic.pensieve.scene.CrowdSchedule;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crowd's CPU cost and upload per frame as CrowdRenderer spends them, measured off the GPU: 3,000 spectators in the
 * dev bowl, idle, during a goal for one stand and during an erupt for all, each dirty chunk meshed into the game's own
 * BufferBuilder (the block format, the fast vertex path) and built into a MeshData, on the renderer's own schedule
 * ({@link CrowdSchedule}) at 60 fps from the touchline. Printed for the report; the GPU upload itself and the draw calls
 * are not timed (the bytes are counted).
 */
class CrowdBufferBenchTest {
    @Test
    void frameCostAt60Fps() throws Exception {
        ByteBufferBuilder bytes;
        try {
            bytes = new ByteBufferBuilder(1 << 20);
        } catch (Throwable t) {
            Assumptions.abort("no native memory in this test JVM: " + t);
            return;
        }
        // The dev bowl's moments: idle, a goal for the north stand, then an erupt everywhere, the camera on the touchline.
        SceneDef s = SceneParser.parse("t", JsonParser.parseString("{\"length\":4000,\"events\":["
                + "{\"t\":400,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,4,26],\"spread\":[46,20,9],\"style\":\"goal\",\"duration\":80},"
                + "{\"t\":800,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,6,0],\"style\":\"erupt\",\"duration\":120}]}")
                .getAsJsonObject());
        Crowd c = Crowd.layout(CrowdTest.bowl(14, 30, 20), new float[3], "mixed", 1, 3000, 41L).bind(Crowd.reactions(s.events()));
        CrowdSchedule plan = new CrowdSchedule(c);
        CrowdMesh mesh = new CrowdMesh();
        float cx = 0, cy = 3, cz = 18;
        BufferBuilder[] bb = new BufferBuilder[1];
        CrowdMesh.Sink sink = new CrowdMesh.Sink() {
            @Override
            public void figure(int index) {
            }

            @Override
            public void vertex(float x, float y, float z, int argb) {
                bb[0].addVertex(x, y, z, argb, 0.5f, 0.5f, 0, 0xF000F0, 0f, 1f, 0f);
            }
        };
        String[] names = {"idle", "goal", "erupt"};
        float[] from = {200, 410, 820};
        long frame = 0;
        // Warm up (and build everything once).
        for (int f = 0; f < 300; f++) run(plan, mesh, c, bb, sink, bytes, 100 + f / 3f, ++frame, cx, cy, cz);
        for (int p = 0; p < 3; p++) {
            long verts = 0;
            int frames = 180;                   // 3 s at 60 fps
            long t0 = System.nanoTime();
            for (int f = 0; f < frames; f++) verts += run(plan, mesh, c, bb, sink, bytes, from[p] + f / 3f, ++frame, cx, cy, cz);
            double ms = (System.nanoTime() - t0) / 1e6 / frames;
            System.out.printf("CROWD FRAME %s: %d spectators (full %d, two-box %d, strip %d), %.0f vertices (%.0f KB) uploaded per frame, %.3f ms CPU per frame%n",
                    names[p], c.size(), plan.full, plan.mid, plan.strip, verts / (double) frames,
                    verts * 32.0 / frames / 1024, ms);
        }
        bytes.close();
        assertTrue(frame > 0);
    }

    /** One frame as CrowdRenderer runs it (60k-vertex budget beyond the every-frame chunks); returns vertices rebuilt. */
    private static long run(CrowdSchedule plan, CrowdMesh mesh, Crowd c, BufferBuilder[] bb, CrowdMesh.Sink sink,
                            ByteBufferBuilder bytes, float now, long frame, float cx, float cy, float cz) {
        int dirty = plan.plan(now, frame, cx, cy, cz);
        int budget = 60_000;
        long verts = 0;
        for (int k = 0; k < dirty; k++) {
            int ch = plan.chunk(k);
            boolean always = plan.everyFrame(ch);
            if (!always && budget <= 0) continue;
            bb[0] = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            int n = mesh.build(c, plan.chunks[ch], now, cx, cy, cz, 0, 20, 0, plan.cut()[0], plan.cut()[1], sink);
            MeshData m = bb[0].build();
            if (m != null) m.close();
            if (!always) budget -= Math.max(1, n);
            plan.built(ch);
            verts += n;
        }
        return verts;
    }
}
