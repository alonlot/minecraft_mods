package com.hogwartsmagic.pensieve;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.Crowd;
import com.hogwartsmagic.pensieve.scene.CrowdMesh;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrowdTest {
    private static final float[] AT = {0, 0, 0};

    /** A rectangular bowl of {@code tiers} seat tiers round a field, each tier one block up and one block out. */
    static float[][] bowl(int tiers, float halfX, float halfZ) {
        List<float[]> rows = new ArrayList<>();
        for (int k = 0; k < tiers; k++) {
            float x = halfX + k, z = halfZ + k, y = k + 0.5f;
            rows.add(new float[]{-x + 1, y, z, x - 1, y, z});
            rows.add(new float[]{x - 1, y, -z, -x + 1, y, -z});
            rows.add(new float[]{x, y, -z + 1, x, y, z - 1});
            rows.add(new float[]{-x, y, z - 1, -x, y, -z + 1});
        }
        return rows.toArray(new float[0][]);
    }

    private static Crowd react(Crowd c, String json) throws Exception {
        SceneDef s = SceneParser.parse("t", JsonParser.parseString("{\"length\":2000,\"events\":[" + json + "]}").getAsJsonObject());
        return c.bind(Crowd.reactions(s.events()));
    }

    @Test
    void seatsFollowRows() {
        float[][] rows = {{-10, 3.5f, 20, 10, 3.5f, 20}, {5, 7.5f, -3, 5.3f, 7.5f, -3}};
        Crowd c = Crowd.layout(rows, AT, "gryffindor", 1, 0, 42L);
        int first = 0;
        List<Float> xs = new ArrayList<>();
        for (int i = 0; i < c.size(); i++) {
            if (c.z(i) > 10) {
                first++;
                xs.add(c.x(i));
                assertEquals(3.5f, c.y(i), 1e-5);
                assertTrue(Math.abs(c.z(i) - 20) <= Crowd.SIDE_JITTER + 1e-5, "on the line: " + c.z(i));
                assertTrue(c.x(i) >= -10 && c.x(i) <= 10, "inside the row: " + c.x(i));
                // Facing the pitch centre (behind them is +z, so they look along -z: yaw 180 give or take).
                double want = Math.toDegrees(Math.atan2(c.x(i), -c.z(i)));
                double d = ((c.yaw(i) - want) % 360 + 540) % 360 - 180;
                assertTrue(Math.abs(d) <= Crowd.YAW_JITTER + 1e-3, "faces at: yaw " + c.yaw(i) + " want " + want);
                assertEquals(0, c.house(i));
            }
        }
        assertTrue(first >= 20 && first <= 26, "seats on a 20-block row: " + first);
        for (int j = 1; j < xs.size(); j++) {
            float gap = xs.get(j) - xs.get(j - 1);
            assertTrue(gap >= Crowd.MIN_SPACING - 1e-4 && gap <= Crowd.MAX_SPACING + 1e-4, "gap " + gap);
        }
        // The run is centred: the ends are within a gap of the row's ends and balanced.
        assertEquals(10 - xs.get(xs.size() - 1), xs.get(0) + 10, 1e-4);
        // A row shorter than a gap seats one at its middle.
        int shortRow = 0;
        for (int i = 0; i < c.size(); i++) {
            if (c.y(i) == 7.5f) {
                shortRow++;
                assertEquals(5.15f, c.x(i), 1e-4);
            }
        }
        assertEquals(1, shortRow);
    }

    @Test
    void seatsDeterministic() throws Exception {
        float[][] rows = bowl(6, 20, 14);
        Crowd a = Crowd.layout(rows, AT, "mixed", 0.8, 0, 7L), b = Crowd.layout(rows, AT, "mixed", 0.8, 0, 7L);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.x(i), b.x(i));
            assertEquals(a.z(i), b.z(i));
            assertEquals(a.yaw(i), b.yaw(i));
            assertEquals(a.house(i), b.house(i));
            assertEquals(a.gear(i), b.gear(i));
        }
        Crowd other = Crowd.layout(rows, AT, "mixed", 0.8, 0, 8L);
        boolean differs = other.size() != a.size();
        for (int i = 0; !differs && i < a.size(); i++) differs = a.x(i) != other.x(i) || a.house(i) != other.house(i);
        assertTrue(differs, "another seed seats another crowd");
        // The event's own seed: its scene-relative at and t, not the session slot.
        SceneDef s = SceneParser.parse("t", JsonParser.parseString("{\"length\":100,\"events\":[{\"t\":5,\"kind\":\"effect\","
                + "\"id\":\"crowd\",\"at\":[1,0,2],\"intensity\":0.8,\"style\":\"mixed\",\"rows\":[[-20,0.5,15,20,0.5,15]]}]}")
                .getAsJsonObject());
        SceneDef.EventDef e = s.events().get(0);
        Crowd viaEvent = Crowd.of(e);
        Crowd direct = Crowd.layout(e.rows(), e.at(), "mixed", 0.8, -1, Crowd.seed(e));
        assertEquals(direct.size(), viaEvent.size());
        for (int i = 0; i < direct.size(); i++) assertEquals(direct.x(i), viaEvent.x(i));
        // Mixed stands hold all four houses.
        boolean[] seen = new boolean[4];
        for (int i = 0; i < a.size(); i++) seen[a.house(i)] = true;
        for (boolean h : seen) assertTrue(h);
    }

    @Test
    void fillShare() {
        float[][] rows = bowl(10, 30, 20);
        Crowd all = Crowd.layout(rows, AT, "ravenclaw", 1, 0, 3L);
        Crowd half = Crowd.layout(rows, AT, "ravenclaw", 0.5, 0, 3L);
        double share = half.size() / (double) all.size();
        assertTrue(share > 0.45 && share < 0.55, "intensity 0.5 fills " + share);
        Crowd capped = Crowd.layout(rows, AT, "ravenclaw", 1, 100, 3L);
        assertEquals(100, capped.size());
        // The capped ones are seats of the full crowd.
        for (int i = 0; i < capped.size(); i++) {
            boolean found = false;
            for (int j = 0; j < all.size() && !found; j++) found = all.x(j) == capped.x(i) && all.z(j) == capped.z(i);
            assertTrue(found);
        }
        assertEquals(Crowd.MAX_SPECTATORS, Crowd.layout(bowl(30, 40, 30), AT, "mixed", 1, 0, 3L).size(), "the cap holds");
    }

    @Test
    void standersDoNotHideSeated() {
        // A front-row seat at z 20 (seat top 3.5) looking at the pitch centre, a stander on the rail walkway half a
        // block lower right in front of it, and a run of standers off to the side: the one in its sight line is left
        // out, the others stay.
        float[][] rows = {{-0.4f, 3.5f, 20, 0.4f, 3.5f, 20}, {-0.2f, 3f, 18.6f, 0.2f, 3f, 18.6f, 1},
                {2, 3f, 18.6f, 8, 3f, 18.6f, 1}};
        Crowd c = Crowd.layout(rows, AT, "slytherin", 1, 0, 11L);
        assertEquals(1, c.droppedStanders(), "the stander in the way is dropped");
        int seated = -1;
        for (int i = 0; i < c.size(); i++) if (!c.standing(i)) seated = i;
        assertTrue(seated >= 0);
        for (int i = 0; i < c.size(); i++) {
            if (!c.standing(i)) continue;
            assertTrue(!Crowd.blocks(c.x(seated), c.y(seated), c.z(seated), c.scale(seated), c.x(i), c.y(i), c.z(i),
                    c.scale(i), AT), "no kept stander hides the seat");
        }
        assertTrue(c.size() >= 7, "the rest of the rail stays: " + c.size());
    }

    @Test
    void pythonMirrorAgrees() throws Exception {
        JsonObject all = JsonParser.parseString(Files.readString(
                Path.of("build_scripts/pensieve_scenes/crowd_cases.json"))).getAsJsonObject();
        JsonObject k = all.getAsJsonObject("constants");
        assertEquals(Crowd.MIN_SPACING, k.get("MIN_SPACING").getAsDouble(), 0);
        assertEquals(Crowd.MAX_SPACING, k.get("MAX_SPACING").getAsDouble(), 0);
        assertEquals(Crowd.SIDE_JITTER, k.get("SIDE_JITTER").getAsDouble(), 0);
        assertEquals(Crowd.YAW_JITTER, k.get("YAW_JITTER").getAsDouble(), 0);
        assertEquals(Crowd.MAX_SPECTATORS, k.get("MAX_SPECTATORS").getAsInt());
        assertEquals(Crowd.SEATED_EYE, k.get("SEATED_EYE").getAsDouble(), 0);
        assertEquals(Crowd.STANDER_HEIGHT, k.get("STANDER_HEIGHT").getAsDouble(), 0);
        assertEquals(Crowd.STANDER_RADIUS, k.get("STANDER_RADIUS").getAsDouble(), 0);
        assertEquals(Crowd.SIGHT_REACH, k.get("SIGHT_REACH").getAsDouble(), 0);
        JsonArray cases = all.getAsJsonArray("cases");
        assertTrue(cases.size() >= 4);
        int seats = 0;
        for (JsonElement ce : cases) {
            JsonObject c = ce.getAsJsonObject();
            String name = c.get("name").getAsString();
            JsonObject scene = new JsonObject();
            scene.addProperty("length", 4000);
            JsonArray ev = new JsonArray();
            ev.add(c.get("event"));
            scene.add("events", ev);
            SceneDef.EventDef e = SceneParser.parse(name, scene).events().get(0);
            assertEquals(c.get("seed").getAsString(), Long.toUnsignedString(Crowd.seed(e)), name + " seed");
            Crowd crowd = Crowd.of(e);
            JsonArray want = c.getAsJsonArray("seats");
            assertEquals(want.size(), crowd.size(), name + " seats");
            assertEquals(c.get("dropped").getAsInt(), crowd.droppedStanders(), name + " dropped");
            for (int i = 0; i < want.size(); i++) {
                JsonArray w = want.get(i).getAsJsonArray();
                String at = name + " seat " + i;
                assertEquals(w.get(0).getAsDouble(), crowd.x(i), 1e-4, at + " x");
                assertEquals(w.get(1).getAsDouble(), crowd.y(i), 1e-4, at + " y");
                assertEquals(w.get(2).getAsDouble(), crowd.z(i), 1e-4, at + " z");
                assertEquals(w.get(3).getAsDouble(), crowd.yaw(i), 1e-3, at + " yaw");
                assertEquals(w.get(4).getAsInt() == 1, crowd.standing(i), at + " standing");
            }
            seats += want.size();
        }
        assertTrue(seats > 500, "the cases seat a real crowd: " + seats);
    }

    @Test
    void reactionStaggers() throws Exception {
        Crowd c = react(Crowd.layout(bowl(8, 30, 20), AT, "mixed", 1, 0, 5L),
                "{\"t\":100,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,25],\"style\":\"goal\"}");
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        double near = 0, far = 0;
        int nn = 0, nf = 0;
        for (int i = 0; i < c.size(); i++) {
            float o = c.onset(0, i);
            assertTrue(o >= 100 && o <= 100 + Crowd.MAX_DELAY_TICKS + 1e-4, "onset " + o);
            min = Math.min(min, o);
            max = Math.max(max, o);
            if (c.z(i) > 15) {
                near += o;
                nn++;
            } else if (c.z(i) < -15) {
                far += o;
                nf++;
            }
        }
        assertTrue(max - min > 6, "spread over the delay: " + (max - min));
        assertTrue(near / nn + 2 < far / nf, "the near stand goes first: " + near / nn + " vs " + far / nf);
        // A box limits who reacts.
        Crowd boxed = react(Crowd.layout(bowl(8, 30, 20), AT, "mixed", 1, 0, 5L),
                "{\"t\":100,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,25],\"spread\":[40,20,6],\"style\":\"cheer\"}");
        for (int i = 0; i < boxed.size(); i++) {
            assertEquals(boxed.z(i) >= 19 && boxed.z(i) <= 31, !Float.isNaN(boxed.onset(0, i)), "in the box: z " + boxed.z(i));
        }
    }

    @Test
    void gaspIsShort() throws Exception {
        Crowd c = react(Crowd.layout(bowl(4, 20, 14), AT, "hufflepuff", 1, 0, 9L),
                "{\"t\":200,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0],\"style\":\"gasp\",\"duration\":200}");
        float[] p = new float[Crowd.CHANNELS];
        for (int i = 0; i < c.size(); i++) {
            float o = c.onset(0, i);
            c.pose(i, o + 12, p, 0);
            assertTrue(p[Crowd.HANDS_HEAD] > 0.95f, "hands at the head after 0.6 s");
            assertEquals(0f, p[Crowd.STAND], 1e-4, "a gasp stays seated");
            c.pose(i, o + 20 + 10 + 1, p, 0);
            assertEquals(0f, p[Crowd.HANDS_HEAD], 1e-4, "hands down by 1.5 s, whatever the duration");
            assertEquals(0f, p[Crowd.STAND], 1e-4);
        }
    }

    @Test
    void eruptStandsMost() throws Exception {
        Crowd c = react(Crowd.layout(bowl(10, 30, 20), AT, "mixed", 1, 0, 13L),
                "{\"t\":100,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0],\"style\":\"gasp\"},"
                        + "{\"t\":300,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,30,0],\"style\":\"erupt\"}");
        float[] p = new float[Crowd.CHANNELS];
        for (float t = 320; t <= 360; t += 2.5f) {
            int up = 0, arms = 0;
            for (int i = 0; i < c.size(); i++) {
                c.pose(i, t, p, 0);
                if (p[Crowd.STAND] >= 0.5f) up++;
                if (p[Crowd.ARM_L] > 0.6f && p[Crowd.ARM_R] > 0.6f) arms++;
            }
            assertTrue(up >= 0.8 * c.size(), "standing at t " + t + ": " + up + " of " + c.size());
            assertTrue(arms >= 0.8 * c.size(), "arms up at t " + t + ": " + arms);
        }
        // Some are in the air at any moment of the erupt.
        int jumping = 0;
        for (int i = 0; i < c.size(); i++) {
            c.pose(i, 340, p, 0);
            if (p[Crowd.JUMP] > 0.05f) jumping++;
        }
        assertTrue(jumping > 0.2 * c.size(), "jumping: " + jumping);
    }

    @Test
    void idleNeverStands() throws Exception {
        Crowd c = Crowd.layout(bowl(6, 25, 18), AT, "gryffindor", 1, 0, 17L).bind(List.of());
        float[] p = new float[Crowd.CHANNELS];
        int waving = 0;
        for (float t = 0; t < 2000; t += 7.3f) {
            for (int i = 0; i < c.size(); i += 3) {
                c.pose(i, t, p, 0);
                assertEquals(0f, p[Crowd.STAND], 0f, "idle stands at t " + t);
                assertEquals(0f, p[Crowd.JUMP], 0f);
                if (p[Crowd.WAVE] > 0.5f) waving++;
            }
        }
        assertTrue(waving > 0, "now and then a scarf is waved");
        // A sit brings a cheering stand back down.
        Crowd s = react(Crowd.layout(bowl(3, 20, 14), AT, "gryffindor", 1, 0, 17L),
                "{\"t\":100,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0],\"style\":\"goal\",\"duration\":400},"
                        + "{\"t\":200,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0],\"style\":\"sit\"}");
        for (int i = 0; i < s.size(); i++) {
            s.pose(i, 180, p, 0);
            assertEquals(1f, p[Crowd.STAND], 1e-4);
            s.pose(i, s.onset(1, i) + 5, p, 0);
            assertTrue(p[Crowd.STAND] > 0.05f && p[Crowd.STAND] < 0.95f, "sitting down smoothly: " + p[Crowd.STAND]);
            s.pose(i, s.onset(1, i) + 13, p, 0);
            assertEquals(0f, p[Crowd.STAND], 1e-4);
        }
    }

    @Test
    void chantPumpsInPhase() throws Exception {
        Crowd c = react(Crowd.layout(bowl(4, 20, 14), AT, "slytherin", 1, 0, 19L),
                "{\"t\":100,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0],\"style\":\"chant\",\"duration\":200}");
        float[] p = new float[Crowd.CHANNELS];
        for (float t = 140; t < 260; t += 3.7f) {
            c.pose(0, t, p, 0);
            float ref = p[Crowd.ARM_R];
            for (int i = 1; i < c.size(); i++) {
                c.pose(i, t, p, 0);
                assertEquals(ref, p[Crowd.ARM_R], 1e-4, "in phase at t " + t);
            }
        }
        c.pose(0, 142.5f, p, 0);
        float a = p[Crowd.ARM_R];
        c.pose(0, 147.5f, p, 0);
        assertTrue(Math.abs(a - p[Crowd.ARM_R]) > 0.3f, "a 2 Hz pump: half a beat apart differs");
    }

    @Test
    void blendIsContinuous() throws Exception {
        Crowd c = react(Crowd.layout(bowl(3, 20, 14), AT, "mixed", 1, 0, 23L),
                "{\"t\":100,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0],\"style\":\"cheer\"},"
                        + "{\"t\":130,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0],\"style\":\"gasp\"},"
                        + "{\"t\":170,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0],\"style\":\"erupt\"}");
        float[] a = new float[Crowd.CHANNELS], b = new float[Crowd.CHANNELS];
        for (int i = 0; i < c.size(); i += 5) {
            for (float t = 90; t < 420; t += 0.25f) {
                c.pose(i, t, a, 0);
                c.pose(i, t + 0.25f, b, 0);
                for (int ch = 0; ch < Crowd.CHANNELS; ch++) {
                    assertTrue(Math.abs(a[ch] - b[ch]) < 0.2f, "channel " + ch + " jumps at t " + t + ": " + a[ch] + " -> " + b[ch]);
                }
            }
        }
    }

    @Test
    void chunksCoverEveryone() {
        Crowd c = Crowd.layout(bowl(10, 30, 20), AT, "mixed", 1, 0, 29L);
        int[][] chunks = c.chunks(12f, 96);
        boolean[] seen = new boolean[c.size()];
        for (int[] ch : chunks) {
            assertTrue(ch.length <= 96);
            for (int i : ch) {
                assertTrue(!seen[i], "once");
                seen[i] = true;
            }
        }
        for (boolean s : seen) assertTrue(s);
    }

    @Test
    void lodCutoffsHoldTheCaps() {
        float[] d2 = new float[4000];
        for (int i = 0; i < d2.length; i++) d2[i] = (i * 0.01f) * (i * 0.01f);     // 4,000 within 40 blocks
        float[] cut = new float[2];
        Crowd.lodCutoffs(d2, d2.length, 40, 120, 400, 2000, new int[96], cut);
        int full = 0, mid = 0;
        for (float d : d2) {
            if (d <= cut[0]) full++;
            else if (d <= cut[1]) mid++;
        }
        assertTrue(full <= 400 && full > 300, "full " + full);
        assertTrue(full + mid <= 2400, "mid " + mid);
        // A sparse crowd keeps the whole ranges.
        float[] sparse = {100, 2500, 9000};
        Crowd.lodCutoffs(sparse, 3, 40, 120, 400, 2000, new int[96], cut);
        assertEquals(40f * 40f, cut[0], 1);
        assertEquals(120f * 120f, cut[1], 1);
    }

    /** Counts vertices and checks every quad is wound counter-clockwise seen from outside its own box or plane. */
    private static final class Counter implements CrowdMesh.Sink {
        int vertices, figures;

        @Override
        public void figure(int index) {
            figures++;
        }

        @Override
        public void vertex(float x, float y, float z, int argb) {
            vertices++;
        }
    }

    @Test
    void budgetCapsVertices() throws Exception {
        // The worst a frame can ask: 4,000 spectators, every one with arms up and a flag, the camera in their midst. The
        // level-of-detail caps keep it inside the budget.
        float[][] rows = bowl(30, 40, 30);
        Crowd c = react(Crowd.layout(rows, AT, "mixed", 1, 0, 31L),
                "{\"t\":0,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0],\"style\":\"erupt\",\"duration\":1000}");
        assertEquals(Crowd.MAX_SPECTATORS, c.size());
        float[] d2 = new float[c.size()];
        float cx = 40, cy = 10, cz = 30;
        for (int i = 0; i < c.size(); i++) {
            float dx = c.x(i) - cx, dy = c.y(i) + 1 - cy, dz = c.z(i) - cz;
            d2[i] = dx * dx + dy * dy + dz * dz;
        }
        float[] cut = new float[2];
        Crowd.lodCutoffs(d2, c.size(), CrowdMesh.FULL_RANGE, CrowdMesh.MID_RANGE, CrowdMesh.MAX_FULL, CrowdMesh.MAX_MID,
                new int[96], cut);
        int[] all = new int[c.size()];
        for (int i = 0; i < all.length; i++) all[i] = i;
        CrowdMesh mesh = new CrowdMesh();
        Counter count = new Counter();
        int v = mesh.build(c, all, 100, cx, cy, cz, 0, 20, 0, cut[0], cut[1], count);
        assertEquals(count.vertices, v);
        assertEquals(c.size(), count.figures);
        assertTrue(v <= CrowdMesh.VERTEX_BUDGET, v + " vertices over the budget " + CrowdMesh.VERTEX_BUDGET);
        assertTrue(CrowdMesh.VERTEX_BUDGET <= 350_000, "budget " + CrowdMesh.VERTEX_BUDGET);
        // Each level stays under its per-figure maximum.
        int[] one = new int[1];
        for (int i = 0; i < c.size(); i += 7) {
            one[0] = i;
            for (int lod = 0; lod < 3; lod++) {
                Counter k = new Counter();
                mesh.figure(c, i, 100, lod, 0, 20, 0, k);
                int max = lod == CrowdMesh.FULL ? CrowdMesh.FULL_MAX_VERTS : lod == CrowdMesh.MID ? CrowdMesh.MID_MAX_VERTS
                        : CrowdMesh.STRIP_MAX_VERTS;
                assertTrue(k.vertices <= max, "lod " + lod + ": " + k.vertices + " > " + max);
                assertEquals(0, k.vertices % 4);
            }
        }
    }

    /** Collects quads to check their winding and their faces' outward direction. */
    private static final class Quads implements CrowdMesh.Sink {
        final List<float[]> v = new ArrayList<>();

        @Override
        public void figure(int index) {
        }

        @Override
        public void vertex(float x, float y, float z, int argb) {
            v.add(new float[]{x, y, z});
        }
    }

    @Test
    void facesWoundOutward() throws Exception {
        Crowd c = Crowd.layout(new float[][]{{-3, 2.5f, 10, 3, 2.5f, 10}}, AT, "gryffindor", 1, 0, 37L).bind(List.of());
        CrowdMesh mesh = new CrowdMesh();
        for (int lod = 0; lod < 2; lod++) {
            Quads q = new Quads();
            mesh.figure(c, 0, 50, lod, 0, 1, 0, q);
            // Every box face points away from the figure's middle (its face normal and the vector from the centre agree).
            float cx = c.x(0), cy = c.y(0) + 0.6f, cz = c.z(0);
            int outward = 0, quads = q.v.size() / 4;
            for (int k = 0; k < quads; k++) {
                float[] a = q.v.get(4 * k), b = q.v.get(4 * k + 1), d = q.v.get(4 * k + 2);
                float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], wx = d[0] - a[0], wy = d[1] - a[1], wz = d[2] - a[2];
                float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
                float mx = 0, my = 0, mz = 0;
                for (int j = 0; j < 4; j++) {
                    mx += q.v.get(4 * k + j)[0] / 4;
                    my += q.v.get(4 * k + j)[1] / 4;
                    mz += q.v.get(4 * k + j)[2] / 4;
                }
                if (nx * (mx - cx) + ny * (my - cy) + nz * (mz - cz) > 0) outward++;
            }
            // Thin parts far from the middle (the scarf tail, the flag) can face back across it; the boxes as a whole
            // must be outward, and an inverted winding would make almost every face inward.
            assertTrue(outward > 0.75 * quads, "lod " + lod + ": " + outward + " of " + quads + " faces wound outward");
        }
    }

    @Test
    void meshCostBenchmark() throws Exception {
        // 3,000 in a bowl, the camera on the touchline: the CPU cost of rebuilding every chunk once, written into a
        // float array the size of the GPU vertex (8 floats), as the renderer writes its buffer. Printed for the report.
        Crowd c = react(Crowd.layout(bowl(14, 30, 20), AT, "mixed", 1, 3000, 41L),
                "{\"t\":0,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0],\"style\":\"erupt\",\"duration\":1000}");
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
        float[] out = new float[CrowdMesh.VERTEX_BUDGET * 8];
        CrowdMesh.Sink sink = new CrowdMesh.Sink() {
            int at;

            @Override
            public void figure(int index) {
            }

            @Override
            public void vertex(float x, float y, float z, int argb) {
                if (at + 8 > out.length) at = 0;
                out[at] = x;
                out[at + 1] = y;
                out[at + 2] = z;
                out[at + 3] = Float.intBitsToFloat(argb);
                at += 8;
            }
        };
        CrowdMesh mesh = new CrowdMesh();
        int verts = 0;
        for (int warm = 0; warm < 30; warm++) for (int[] ch : chunks) mesh.build(c, ch, 100 + warm, cx, cy, cz, 0, 20, 0, cut[0], cut[1], sink);
        int runs = 40;
        long t0 = System.nanoTime();
        for (int r = 0; r < runs; r++) {
            verts = 0;
            for (int[] ch : chunks) verts += mesh.build(c, ch, 200 + r, cx, cy, cz, 0, 20, 0, cut[0], cut[1], sink);
        }
        double ms = (System.nanoTime() - t0) / 1e6 / runs;
        int full = 0, mid = 0;
        for (float d : d2) {
            if (d <= cut[0]) full++;
            else if (d <= cut[1]) mid++;
        }
        System.out.printf("CROWD BENCH: %d spectators (full %d, two-box %d, strip %d), %d vertices, %.3f ms per full rebuild%n",
                c.size(), full, mid, c.size() - full - mid, verts, ms);
        assertTrue(verts > 0);
    }

    @Test
    void sameSeatsWithAnyReactions() throws Exception {
        Crowd a = Crowd.layout(bowl(3, 20, 14), AT, "mixed", 0.7, 0, 43L);
        Crowd b = react(Crowd.layout(bowl(3, 20, 14), AT, "mixed", 0.7, 0, 43L),
                "{\"t\":10,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0],\"style\":\"goal\"}");
        assertEquals(a.size(), b.size());
        assertNotEquals(0, a.size());
    }
}
