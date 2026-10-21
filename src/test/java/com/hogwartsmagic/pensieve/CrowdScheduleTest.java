package com.hogwartsmagic.pensieve;

import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.Crowd;
import com.hogwartsmagic.pensieve.scene.CrowdMesh;
import com.hogwartsmagic.pensieve.scene.CrowdSchedule;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The crowd renderer's rebuild schedule: only what moves is rebuilt and re-uploaded. */
class CrowdScheduleTest {
    private static Crowd crowd(String reacts) throws Exception {
        SceneDef s = SceneParser.parse("t", JsonParser.parseString("{\"length\":4000,\"events\":[" + reacts + "]}")
                .getAsJsonObject());
        return Crowd.layout(CrowdTest.bowl(14, 30, 20), new float[3], "mixed", 1, 3000, 41L).bind(Crowd.reactions(s.events()));
    }

    /** Plan a frame and mark everything it lists built; returns how many chunks it listed. */
    private static int frame(CrowdSchedule s, float now, long frame, float cx, float cy, float cz) {
        int n = s.plan(now, frame, cx, cy, cz);
        for (int k = 0; k < n; k++) s.built(s.chunk(k));
        return n;
    }

    @Test
    void idleFarStandIsStatic() throws Exception {
        // From high over the pitch every figure is two-box or strip: once built, an idle crowd is never rebuilt.
        CrowdSchedule s = new CrowdSchedule(crowd(""));
        int first = frame(s, 100, 1, 0, 70, 0);
        assertEquals(s.chunks.length, first, "everything is built once");
        assertEquals(0, s.full, "no full figures from up here");
        for (int f = 2; f < 200; f++) assertEquals(0, frame(s, 100 + f / 3f, f, 0, 70, 0), "idle frame " + f);
    }

    @Test
    void reactingChunksRebuildThenSettle() throws Exception {
        CrowdSchedule s = new CrowdSchedule(crowd(
                "{\"t\":200,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,25],\"spread\":[50,20,8],\"style\":\"goal\",\"duration\":60}"));
        frame(s, 150, 1, 0, 70, 0);
        int during = 0, after = 0;
        long f = 2;
        for (float t = 150; t < 400; t += 1 / 3f, f++) {
            int n = frame(s, t, f, 0, 70, 0);
            if (t > 205 && t < 260) during += n;
            if (t > 300) after += n;
        }
        assertTrue(during > 0, "the cheering stand is rebuilt while it moves");
        assertEquals(0, after, "and left alone once the goal is over");
        // Only the north stand reacted: the other chunks stayed put throughout.
        CrowdSchedule t = new CrowdSchedule(crowd(
                "{\"t\":200,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,25],\"spread\":[50,20,8],\"style\":\"goal\",\"duration\":60}"));
        frame(t, 150, 1, 0, 70, 0);
        int maxPerFrame = 0;
        f = 2;
        for (float time = 205; time < 260; time += 1 / 3f, f++) maxPerFrame = Math.max(maxPerFrame, frame(t, time, f, 0, 70, 0));
        assertTrue(maxPerFrame < t.chunks.length / 2, "a reaction rebuilds its own chunks only: " + maxPerFrame);
    }

    @Test
    void nearestChunksEveryFrame() throws Exception {
        CrowdSchedule s = new CrowdSchedule(crowd(
                "{\"t\":50,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0],\"style\":\"erupt\",\"duration\":200}"));
        frame(s, 100, 1, 0, 3, 18);
        // Mid-erupt, the same tick, a new frame: only the every-frame chunks (nearest the touchline camera) rebuild.
        int n = s.plan(100.1f, 2, 0, 3, 18);
        int figures = 0;
        for (int k = 0; k < n; k++) {
            int c = s.chunk(k);
            assertTrue(s.everyFrame(c), "within the tick only every-frame chunks rebuild");
            figures += s.chunks[c].length;
        }
        assertTrue(n > 0, "the nearest chunks rebuild every frame");
        assertTrue(figures <= CrowdSchedule.EVERY_FRAME_FIGURES, "at most 150 figures every frame: " + figures);
        for (int k = 0; k < n; k++) s.built(s.chunk(k));
        // Idle, the nearest chunks step with the tick instead.
        CrowdSchedule idle = new CrowdSchedule(crowd(""));
        frame(idle, 100, 1, 0, 3, 18);
        assertEquals(0, idle.plan(100.1f, 2, 0, 3, 18), "idle: nothing within the tick");
    }

    @Test
    void lightChangeRebuilds() throws Exception {
        CrowdSchedule s = new CrowdSchedule(crowd(""));
        frame(s, 100, 1, 0, 70, 0);
        assertEquals(0, frame(s, 101, 2, 0, 70, 0));
        s.light(3, 12345L);
        int n = s.plan(102, 3, 0, 70, 0);
        assertEquals(1, n);
        assertEquals(3, s.chunk(0));
    }

    @Test
    void stillFiguresAreExactlyIdleOutsideReactions() throws Exception {
        Crowd c = crowd("{\"t\":200,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0],\"style\":\"erupt\",\"duration\":80}");
        float[] a = new float[Crowd.CHANNELS], b = new float[Crowd.CHANNELS];
        for (int i = 0; i < c.size(); i += 11) {
            assertTrue(!c.moving(i, 150));
            c.pose(i, 150, a, 0, true);
            c.pose(i, 900, b, 0, true);
            for (int ch = 0; ch < Crowd.CHANNELS; ch++) assertEquals(a[ch], b[ch], 0f, "still idle is still");
            assertTrue(c.moving(i, c.onset(0, i) + 30));
            assertTrue(!c.moving(i, c.onset(0, i) + c.span(0) + 0.01f));
        }
        // And a still figure's mesh does not change between idle moments.
        CrowdMesh mesh = new CrowdMesh();
        int[] one = {5};
        float[] first = vertices(mesh, c, one, 150), second = vertices(mesh, c, one, 700);
        assertEquals(first.length, second.length);
        for (int k = 0; k < first.length; k++) assertEquals(first[k], second[k], 0f);
    }

    private static float[] vertices(CrowdMesh mesh, Crowd c, int[] chunk, float t) {
        List<Float> out = new java.util.ArrayList<>();
        mesh.build(c, chunk, t, 0, 70, 0, 0, 1, 0, 0f, 1e9f, new CrowdMesh.Sink() {
            @Override
            public void figure(int index) {
            }

            @Override
            public void vertex(float x, float y, float z, int argb) {
                out.add(x);
                out.add(y);
                out.add(z);
                out.add((float) argb);
            }
        });
        float[] a = new float[out.size()];
        for (int k = 0; k < a.length; k++) a[k] = out.get(k);
        return a;
    }
}
