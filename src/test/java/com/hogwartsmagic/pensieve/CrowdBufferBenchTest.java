package com.hogwartsmagic.pensieve;

import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.Crowd;
import com.hogwartsmagic.pensieve.scene.CrowdMesh;
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
 * The crowd's CPU cost per frame as CrowdRenderer spends it, measured off the GPU: 3,000 spectators in the dev bowl
 * mid-erupt (arms up: the heaviest pose), every chunk meshed into the game's own BufferBuilder (the block format, the
 * fast vertex path) and built into a MeshData, on the renderer's schedule (near chunks every tick, two-box every 2,
 * strips every 5) at 60 fps. Printed for the report; the upload and the draw calls are not in it.
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
        SceneDef s = SceneParser.parse("t", JsonParser.parseString("{\"length\":2000,\"events\":[{\"t\":0,\"kind\":\"effect\","
                + "\"id\":\"crowd_react\",\"at\":[0,0,0],\"style\":\"erupt\",\"duration\":1500}]}").getAsJsonObject());
        Crowd c = Crowd.layout(CrowdTest.bowl(14, 30, 20), new float[3], "mixed", 1, 3000, 41L).bind(Crowd.reactions(s.events()));
        float cx = 0, cy = 3, cz = 18;
        float[] d2 = new float[c.size()];
        for (int i = 0; i < c.size(); i++) {
            float dx = c.x(i) - cx, dy = c.y(i) + 1 - cy, dz = c.z(i) - cz;
            d2[i] = dx * dx + dy * dy + dz * dz;
        }
        float[] cut = new float[2];
        Crowd.lodCutoffs(d2, c.size(), CrowdMesh.FULL_RANGE, CrowdMesh.MID_RANGE, CrowdMesh.MAX_FULL, CrowdMesh.MAX_MID,
                new int[96], cut);
        int[][] chunks = c.chunks(12f, 96);
        int[] period = new int[chunks.length];
        for (int k = 0; k < chunks.length; k++) {
            float best = Float.MAX_VALUE;
            for (int i : chunks[k]) best = Math.min(best, d2[i]);
            period[k] = best <= cut[0] ? 1 : best <= cut[1] ? 2 : 5;
        }
        CrowdMesh mesh = new CrowdMesh();
        long verts = 0;
        int frames = 600;                       // 10 s at 60 fps = 200 ticks
        long t0 = 0;
        for (int pass = 0; pass < 2; pass++) {  // a warm-up pass, then the timed one
            verts = 0;
            long[] built = new long[chunks.length];
            java.util.Arrays.fill(built, Long.MIN_VALUE);
            t0 = System.nanoTime();
            for (int f = 0; f < frames; f++) {
                float now = 100 + f / 3f;
                for (int k = 0; k < chunks.length; k++) {
                    long step = (long) Math.floor((now + (k * 7) % 10) / period[k]);
                    if (step == built[k]) continue;
                    built[k] = step;
                    BufferBuilder bb = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                    CrowdMesh.Sink sink = new CrowdMesh.Sink() {
                        @Override
                        public void figure(int index) {
                        }

                        @Override
                        public void vertex(float x, float y, float z, int argb) {
                            bb.addVertex(x, y, z, argb, 0.5f, 0.5f, 0, 0xF000F0, 0f, 1f, 0f);
                        }
                    };
                    verts += mesh.build(c, chunks[k], now, cx, cy, cz, 0, 20, 0, cut[0], cut[1], sink);
                    MeshData m = bb.build();
                    if (m != null) m.close();
                }
            }
        }
        double perFrame = (System.nanoTime() - t0) / 1e6 / frames;
        System.out.printf("CROWD FRAME: %d spectators, %d chunks, %.0f vertices rebuilt per frame, %.3f ms CPU per frame%n",
                c.size(), chunks.length, verts / (double) frames, perFrame);
        bytes.close();
        assertTrue(verts > 0);
    }
}
