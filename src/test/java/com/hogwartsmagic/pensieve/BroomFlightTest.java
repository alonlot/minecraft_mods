package com.hogwartsmagic.pensieve;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.BroomFlight;
import com.hogwartsmagic.pensieve.scene.Ease;
import com.hogwartsmagic.pensieve.scene.HumanMotion;
import com.hogwartsmagic.pensieve.scene.Keyframe;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.hogwartsmagic.pensieve.scene.Track;
import com.hogwartsmagic.pensieve.scene.Vehicle;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BroomFlightTest {
    /** A circle of radius r at speed v (blocks per tick), turning right (Minecraft yaw increasing) when right. */
    private static Track circle(double r, double v, boolean right) {
        List<Keyframe> keys = new ArrayList<>();
        double omega = v / r;
        for (int i = 0; i <= 120; i++) {
            double th = omega * i * 2;
            // Start heading +Z at the origin; a right turn bends toward -X.
            double x = (right ? -1 : 1) * r * (1 - Math.cos(th));
            double z = r * Math.sin(th);
            keys.add(new Keyframe(i * 2, new float[]{(float) x, 10, (float) z}, Ease.LINEAR));
        }
        return new Track(keys);
    }

    private static Track line(float[] a, float[] b, float t1) {
        return new Track(List.of(new Keyframe(0, a, Ease.LINEAR), new Keyframe(t1, b, Ease.LINEAR)));
    }

    @Test
    void bankFollowsTurn() {
        Track straight = line(new float[]{0, 5, 0}, new float[]{40, 9, 30}, 100);
        for (float t = 5; t < 95; t += 3.3f) assertEquals(0, BroomFlight.bank(straight, t), 1e-3, "t " + t);
        double right = BroomFlight.bank(circle(10, 0.5, true), 60);
        double left = BroomFlight.bank(circle(10, 0.5, false), 60);
        assertTrue(right > 10, "a right turn banks right side down: " + right);
        assertTrue(left < -10, "a left turn banks left side down: " + left);
        assertEquals(right, -left, 1e-3);
        // Coming round on a tight, fast turn the bank saturates at 45, never past it.
        double tight = BroomFlight.bank(circle(2, 1.0, true), 60);
        assertTrue(tight > 40 && tight <= 45, "tight " + tight);
        // The yaw the broom faces through the right turn increases: the bank has the turn's sign.
        Track c = circle(10, 0.5, true);
        assertTrue(BroomFlight.heading(c, 40) > BroomFlight.heading(c, 20) + 30);
    }

    @Test
    void bankIsContinuous() {
        // Straight, a turn, a stop on the spot and a start back the other way: no jump anywhere.
        List<Keyframe> keys = new ArrayList<>();
        keys.add(new Keyframe(0, new float[]{0, 5, 0}, Ease.LINEAR));
        keys.add(new Keyframe(30, new float[]{0, 5, 10}, Ease.SPLINE));
        keys.add(new Keyframe(50, new float[]{-6, 5, 16}, Ease.SPLINE));
        keys.add(new Keyframe(70, new float[]{-14, 5, 16}, Ease.SPLINE));
        keys.add(new Keyframe(90, new float[]{-16, 5, 16}, Ease.IN_OUT));
        keys.add(new Keyframe(120, new float[]{-6, 5, 18}, Ease.IN_OUT));
        Track path = new Track(keys);
        double prev = BroomFlight.bank(path, -5);
        double most = 0;
        for (double t = -5; t <= 130; t += 0.05) {
            double b = BroomFlight.bank(path, t);
            most = Math.max(most, Math.abs(b - prev));
            prev = b;
        }
        assertTrue(most < 1.0, "the bank changes by " + most + " degrees in a twentieth of a tick");
    }

    @Test
    void riderLeansWithSpeed() {
        float rest = BroomFlight.riderPose(0, 0)[HumanMotion.BODY][0];
        float half = BroomFlight.riderPose(0.3, 0)[HumanMotion.BODY][0];
        float full = BroomFlight.riderPose(0.6, 0)[HumanMotion.BODY][0];
        float faster = BroomFlight.riderPose(1.5, 0)[HumanMotion.BODY][0];
        assertEquals(8, rest, 1e-5);
        assertEquals(19, half, 1e-5);
        assertEquals(30, full, 1e-5);
        assertEquals(30, faster, 1e-5);
        float[][] p = BroomFlight.riderPose(0.3, 0);
        assertEquals(-19, p[HumanMotion.HEAD][0], 1e-5, "the head lifts to hold the gaze");
        assertEquals(-30, p[HumanMotion.RIGHT_LEG][0], 0);
        assertEquals(10, p[HumanMotion.RIGHT_LEG][1], 0);
        assertEquals(-10, p[HumanMotion.LEFT_LEG][1], 0);
        assertEquals(-40.5, p[HumanMotion.RIGHT_ARM][0], 1e-5, "the arms swing back with the lean");
        assertEquals(-18, p[HumanMotion.RIGHT_ARM][1], 0);
        assertEquals(18, p[HumanMotion.LEFT_ARM][1], 0);
    }

    @Test
    void diveTucks() {
        assertEquals(30, BroomFlight.riderPose(0.6, 20)[HumanMotion.BODY][0], 1e-5, "no tuck at pitch 20");
        assertEquals(40, BroomFlight.riderPose(0.6, 40)[HumanMotion.BODY][0], 1e-5, "full tuck at pitch 40");
        assertEquals(40, BroomFlight.riderPose(0.2, 60)[HumanMotion.BODY][0], 1e-5, "tucked at any speed");
        assertEquals(-40, BroomFlight.riderPose(0.6, 60)[HumanMotion.HEAD][0], 1e-5);
        float mid = BroomFlight.riderPose(0.6, 30)[HumanMotion.BODY][0];
        assertTrue(mid > 30 && mid < 40, "half way through the tuck " + mid);
        assertEquals(30, BroomFlight.riderPose(0.6, -40)[HumanMotion.BODY][0], 1e-5, "a climb does not tuck");
    }

    @Test
    void riderPoseIsContinuous() {
        // C13: the tuck eases in, it does not snap at pitch 30.
        for (double speed : new double[]{0, 0.3, 0.6}) {
            float prev = BroomFlight.riderPose(speed, -10)[HumanMotion.BODY][0];
            for (double pitch = -10; pitch <= 80; pitch += 0.01) {
                float now = BroomFlight.riderPose(speed, pitch)[HumanMotion.BODY][0];
                assertTrue(Math.abs(now - prev) < 0.03, "speed " + speed + " pitch " + pitch + ": " + prev + " -> " + now);
                prev = now;
            }
        }
        float prev = BroomFlight.riderPose(0, 0)[HumanMotion.BODY][0];
        for (double s = 0; s <= 1; s += 0.001) {
            float now = BroomFlight.riderPose(s, 0)[HumanMotion.BODY][0];
            assertTrue(Math.abs(now - prev) < 0.05);
            prev = now;
        }
    }

    private static SceneDef scene(String json) throws Exception {
        return SceneParser.parse("t", JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void hoverBobOnlyWhenSlowAndRidden() throws Exception {
        long seed = HumanMotion.seed("b");
        double most = 0;
        for (double t = 0; t < 100; t += 0.5) most = Math.max(most, Math.abs(BroomFlight.hoverBob(0, t, seed)));
        assertTrue(most > 0.035 && most <= 0.04 + 1e-9, "amplitude " + most);
        for (double t = 0; t < 100; t += 0.5) assertEquals(0, BroomFlight.hoverBob(0.1, t, seed), 0);
        for (double t = 0; t < 100; t += 0.5) assertTrue(Math.abs(BroomFlight.hoverBob(0.05, t, seed)) < 0.021);
        // 0.9 Hz: one period is 22.2 ticks.
        assertEquals(BroomFlight.hoverBob(0, 3, seed), BroomFlight.hoverBob(0, 3 + 20 / 0.9, seed), 1e-9);
        String broom = "{\"id\":\"b\",\"type\":\"hogwartsmagic:memory_broom\",\"path\":[{\"t\":0,\"pos\":[0,3,0]}]}";
        SceneDef empty = scene("{\"length\":100,\"actors\":[" + broom + "]}");
        SceneDef ridden = scene("{\"length\":100,\"actors\":[" + broom
                + ",{\"id\":\"r\",\"type\":\"player\",\"ride\":{\"on\":\"b\",\"from\":20,\"until\":80}}]}");
        double moved = 0;
        for (float t = 0; t < 100; t += 0.5f) {
            assertEquals(3, BroomFlight.place(empty, empty.actors().get(0), t).y(), 0, "an empty broom holds still");
            double y = BroomFlight.place(ridden, ridden.actors().get(0), t).y();
            if (t < 20 || t >= 80) assertEquals(3, y, 0, "no rider yet, or none any more, at " + t);
            moved = Math.max(moved, Math.abs(y - 3));
        }
        assertTrue(moved > 0.03, "a ridden hovering broom bobs " + moved);
        // It eases in as the rider mounts: no jump at the window edge.
        assertEquals(3, BroomFlight.place(ridden, ridden.actors().get(0), 20.05f).y(), 1e-3);
    }

    @Test
    void seatLandsOnHandle() throws Exception {
        // C14: the seat offset is HumanMotion's seated root, the plan's -0.5722 x scale + 0.06.
        for (double s : new double[]{0.75, 0.85, 1.0}) {
            double[] seat = BroomFlight.seat(s);
            assertEquals(-0.5722 * s + 0.06, seat[1], 1e-4, "scale " + s);
            assertEquals(HumanMotion.seatedRootY(BroomFlight.SEAT_TOP, s), seat[1], 0);
        }
        Random rnd = new Random(7);
        for (int i = 0; i < 300; i++) {
            float yaw = rnd.nextFloat() * 720 - 360, pitch = rnd.nextFloat() * 140 - 70, roll = rnd.nextFloat() * 90 - 45;
            float scale = new float[]{0.75f, 0.85f, 1f}[i % 3];
            String json = "{\"length\":100,\"actors\":[{\"id\":\"b\",\"type\":\"hogwartsmagic:memory_broom\","
                    + "\"path\":[{\"t\":0,\"pos\":[3,7,-2]}],\"look\":[{\"t\":0,\"yaw\":" + yaw + ",\"pitch\":" + pitch
                    + ",\"roll\":" + roll + "}]},{\"id\":\"r\",\"type\":\"player\",\"scale\":" + scale
                    + ",\"ride\":{\"on\":\"b\",\"from\":0,\"until\":100}}]}";
            SceneDef s = scene(json);
            float t = 50;
            Vehicle.Seat seat = Vehicle.seat(s, s.actors().get(1), t);
            assertNotNull(seat);
            BroomFlight.Pose b = BroomFlight.place(s, s.actors().get(0), t);
            // The hip pivot as the renderer draws it: the model about its root at the rider's yaw, tilted by
            // M * Yaw^-1 (MemoryActors.rideTilt).
            double[] v = HumanMotion.modelToWorld(new double[]{0, 0, 0}, seat.yaw(), scale, 0, HumanMotion.HIP_PX, 0);
            double[] yawOnly = BroomFlight.frame(seat.vehicleYaw(), 0, 0);
            double[] local = {yawOnly[0] * v[0] + yawOnly[3] * v[1] + yawOnly[6] * v[2],
                    yawOnly[1] * v[0] + yawOnly[4] * v[1] + yawOnly[7] * v[2],
                    yawOnly[2] * v[0] + yawOnly[5] * v[1] + yawOnly[8] * v[2]};
            double[] hip = BroomFlight.toWorld(seat.x(), seat.y(), seat.z(), b.m(), local);
            // Where the hips belong: over the seat point, half a thigh above the seat top, just behind it.
            double[] want = BroomFlight.toWorld(b.x(), b.y(), b.z(), b.m(),
                    new double[]{0, BroomFlight.SEAT_TOP + HumanMotion.seatedHipLift(scale), -BroomFlight.SEAT_BACK});
            double d = Math.sqrt(Math.pow(hip[0] - want[0], 2) + Math.pow(hip[1] - want[1], 2) + Math.pow(hip[2] - want[2], 2));
            assertTrue(d < 0.01, "yaw " + yaw + " pitch " + pitch + " roll " + roll + ": the hips are " + d + " off");
            // And the seat point is on the handle: the hips sit exactly half a thigh above the seat top.
            assertTrue(Math.abs(Math.sqrt(Math.pow(want[0] - b.x(), 2) + Math.pow(want[1] - b.y(), 2)
                    + Math.pow(want[2] - b.z(), 2)) - Math.hypot(BroomFlight.SEAT_TOP + HumanMotion.seatedHipLift(scale),
                    BroomFlight.SEAT_BACK)) < 1e-9);
        }
    }

    // ---- the Python mirror ----------------------------------------------------------------------------------------

    private static double[] doubles(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        double[] out = new double[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsDouble();
        return out;
    }

    private static void close(double want, double got, double tol, String what) {
        assertTrue(Math.abs(want - got) <= tol, what + ": python " + want + ", java " + got);
    }

    @Test
    void pythonMirrorAgrees() throws Exception {
        JsonObject all = JsonParser.parseString(Files.readString(
                Path.of("build_scripts/pensieve_scenes/broom_cases.json"))).getAsJsonObject();
        JsonObject k = all.getAsJsonObject("constants");
        assertEquals(BroomFlight.SEAT_TOP, k.get("SEAT_TOP").getAsDouble(), 0);
        assertEquals(BroomFlight.SEAT_BACK, k.get("SEAT_BACK").getAsDouble(), 0);
        assertEquals(BroomFlight.BANK_GAIN, k.get("BANK_GAIN").getAsDouble(), 0);
        assertEquals(BroomFlight.MAX_BANK, k.get("MAX_BANK").getAsDouble(), 0);
        assertEquals(BroomFlight.BANK_WINDOW, k.get("BANK_WINDOW").getAsDouble(), 0);
        assertEquals(BroomFlight.BOB_AMPLITUDE, k.get("BOB_AMPLITUDE").getAsDouble(), 0);
        assertEquals(BroomFlight.BOB_HZ, k.get("BOB_HZ").getAsDouble(), 0);
        assertEquals(BroomFlight.RIDER_BLEND, k.get("RIDER_BLEND").getAsDouble(), 0);
        JsonArray school = k.getAsJsonArray("BOX_SCHOOL"), racing = k.getAsJsonArray("BOX_RACING");
        for (int i = 0; i < 6; i++) {
            assertEquals(BroomFlight.BOX_SCHOOL[i], school.get(i / 3).getAsJsonArray().get(i % 3).getAsDouble(), 0);
            assertEquals(BroomFlight.BOX_RACING[i], racing.get(i / 3).getAsJsonArray().get(i % 3).getAsDouble(), 0);
        }
        Map<String, SceneDef> scenes = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : all.getAsJsonObject("scenes").entrySet()) {
            scenes.put(e.getKey(), SceneParser.parse(e.getKey(), e.getValue().getAsJsonObject()));
        }
        JsonArray cases = all.getAsJsonArray("cases");
        assertEquals(40, cases.size());
        int ridden = 0, windowed = 0;
        for (JsonElement ce : cases) {
            JsonObject c = ce.getAsJsonObject();
            SceneDef s = scenes.get(c.get("scene").getAsString());
            float t = c.get("t").getAsFloat();
            String at = c.get("scene").getAsString() + " t " + t;
            SceneDef.ActorDef b = s.actors().get(0), r = s.actors().get(1);
            BroomFlight.Pose p = BroomFlight.place(s, b, t);
            double[] pos = doubles(c.get("pos"));
            close(pos[0], p.x(), 1e-3, at + " x");
            close(pos[1], p.y(), 1e-3, at + " y");
            close(pos[2], p.z(), 1e-3, at + " z");
            close(c.get("yaw").getAsDouble(), p.yaw(), 0.02, at + " yaw");
            close(c.get("pitch").getAsDouble(), p.pitch(), 0.02, at + " pitch");
            close(c.get("roll").getAsDouble(), p.roll(), 0.02, at + " roll");
            close(c.get("speed").getAsDouble(), p.speed(), 1e-4, at + " speed");
            close(c.get("bank").getAsDouble(), BroomFlight.bank(b.path(), t), 0.02, at + " bank");
            close(c.get("climb").getAsDouble(), BroomFlight.climb(b.path(), t), 0.02, at + " climb");
            close(c.get("heading").getAsDouble(), BroomFlight.heading(b.path(), t), 0.02, at + " heading");
            close(c.get("seed_bob").getAsDouble(), BroomFlight.hoverBob(0, t, HumanMotion.seed("b")), 1e-9, at + " bob");
            double[] root = doubles(c.get("rider_root"));
            double[] jr = Vehicle.root(s, r, t);
            for (int i = 0; i < 3; i++) close(root[i], jr[i], 1e-3, at + " rider root " + i);
            Vehicle.Seat seat = Vehicle.seat(s, r, t);
            if (c.get("rider_m").isJsonNull()) {
                assertNull(seat, at);
            } else {
                assertNotNull(seat, at);
                ridden++;
                double[] m = doubles(c.get("rider_m"));
                for (int i = 0; i < 9; i++) close(m[i], seat.m()[i], 1e-3, at + " m" + i);
                double w = c.get("rider_weight").getAsDouble();
                close(w, seat.weight(), 1e-5, at + " weight");
                if (w < 1) windowed++;
            }
            float[][] pose = BroomFlight.riderPose(p.speed(), p.pitch());
            JsonArray want = c.getAsJsonArray("rider_pose");
            for (int part = 0; part < 6; part++) {
                double[] wv = doubles(want.get(part));
                for (int i = 0; i < 3; i++) close(wv[i], pose[part][i], 0.02, at + " pose " + part + "." + i);
            }
        }
        assertTrue(ridden > 30, "most cases are ridden: " + ridden);
        assertTrue(windowed > 0, "a case falls in a window's easing");
    }

    // ---- the grip, the head turn, the seated life -------------------------------------------------------------------

    /** (x, y, z) turned as a model part turns it: X, then Y, then Z (HumanMotion's turned). */
    private static double[] turned(double x, double y, double z, double a, double b, double c) {
        double y1 = y * Math.cos(a) - z * Math.sin(a), z1 = y * Math.sin(a) + z * Math.cos(a);
        double x2 = x * Math.cos(b) + z1 * Math.sin(b), z2 = -x * Math.sin(b) + z1 * Math.cos(b);
        return new double[]{x2 * Math.cos(c) - y1 * Math.sin(c), x2 * Math.sin(c) + y1 * Math.cos(c), z2};
    }

    /** The inverse of {@link #turned}: Z^-1, then Y^-1, then X^-1. */
    private static double[] unturned(double[] v, double a, double b, double c) {
        double x1 = v[0] * Math.cos(c) + v[1] * Math.sin(c), y1 = -v[0] * Math.sin(c) + v[1] * Math.cos(c);
        double x2 = x1 * Math.cos(b) - v[2] * Math.sin(b), z2 = x1 * Math.sin(b) + v[2] * Math.cos(b);
        return new double[]{x2, y1 * Math.cos(a) + z2 * Math.sin(a), -y1 * Math.sin(a) + z2 * Math.cos(a)};
    }

    @Test
    void handsOnHandle() {
        // The whole fist (the arm's last 4 px with its sleeve, wide and slim arms) against the whole thigh (with its
        // pants), both ways over their faces, and against the handle, at every rider scale, speed and dive pitch.
        double worstGap = -9, deepest = 9, leastClear = 9;
        double[][] thigh = {{-2.25, 2.25}, {-0.25, 12.25}, {-2.25, 2.25}};
        for (boolean slim : new boolean[]{false, true}) {
            for (double scale : new double[]{0.75, 0.85, 1.0}) {
                double k = HumanMotion.pxBlocks(scale);
                double axisY = HumanMotion.HIP_PX + (BroomFlight.SEAT_TOP + HumanMotion.legHalfWidth(scale)) / k;
                double radius = (1 / 16.0) / k;
                for (double speed = 0; speed <= 0.6001; speed += 0.15) {
                    for (double pitch : new double[]{0, 20, 25, 30, 35, 45, 70}) {
                        float armY = slim ? 2.5f : 2f;
                        float[] rot = new float[18];
                        float[] pos = {0, 0, 0, 0, 0, 0, -5, armY, 0, 5, armY, 0, -1.9f, 12, 0, 1.9f, 12, 0};
                        BroomFlight.riderLayer(rot, pos, speed, pitch, 1f);
                        for (int side = 0; side < 2; side++) {
                            int arm = HumanMotion.RIGHT_ARM + side, leg = HumanMotion.RIGHT_LEG + side;
                            double w = slim ? 3 : 4;
                            double[][] fist = side == 0 ? new double[][]{{1 - w - 0.25, 1.25}, {6, 10.25}, {-2.25, 2.25}}
                                    : new double[][]{{-1.25, w - 1 + 0.25}, {6, 10.25}, {-2.25, 2.25}};
                            double[] ar = angles(rot, arm), lr = angles(rot, leg);
                            double[] ap = {pos[arm * 3], pos[arm * 3 + 1], pos[arm * 3 + 2]};
                            double[] lp = {pos[leg * 3], pos[leg * 3 + 1], pos[leg * 3 + 2]};
                            String at = (slim ? "slim" : "wide") + " scale " + scale + " speed " + speed + " pitch " + pitch;
                            double gap = 9, clear = 9;
                            for (double[] q : faces(fist, 8)) {
                                double[] m = place(ap, ar, q);
                                gap = Math.min(gap, Math.hypot(m[0], m[1] - axisY) - radius);
                                clear = Math.min(clear, outside(local(lp, lr, m), thigh));
                            }
                            for (double[] q : faces(thigh, 8)) clear = Math.min(clear, outside(local(ap, ar, place(lp, lr, q)), fist));
                            gap *= k;
                            worstGap = Math.max(worstGap, gap);
                            deepest = Math.min(deepest, gap);
                            leastClear = Math.min(leastClear, clear);
                            assertTrue(gap <= 0.06, at + ": the fist is " + gap + " off the handle");
                            assertTrue(gap >= -0.005, at + ": the fist sinks " + -gap + " into the handle");
                            assertTrue(clear >= 0.1, at + ": the fist is only " + clear + " px from the thigh");
                        }
                    }
                }
            }
        }
        assertTrue(worstGap > 0.02 && leastClear > 1, "worst gap " + worstGap + ", deepest " + deepest + ", clear " + leastClear);
    }

    private static double[] angles(float[] rot, int part) {
        return new double[]{Math.toRadians(rot[part * 3]), Math.toRadians(rot[part * 3 + 1]), Math.toRadians(rot[part * 3 + 2])};
    }

    /** A part-space point turned and moved to model px. */
    private static double[] place(double[] pivot, double[] r, double[] q) {
        double[] v = turned(q[0], q[1], q[2], r[0], r[1], r[2]);
        return new double[]{pivot[0] + v[0], pivot[1] + v[1], pivot[2] + v[2]};
    }

    /** A model px point in a part's own frame. */
    private static double[] local(double[] pivot, double[] r, double[] m) {
        return unturned(new double[]{m[0] - pivot[0], m[1] - pivot[1], m[2] - pivot[2]}, r[0], r[1], r[2]);
    }

    /** Points over a box's six faces, an (n+1)^2 grid on each. */
    private static List<double[]> faces(double[][] box, int n) {
        List<double[]> out = new ArrayList<>();
        for (int axis = 0; axis < 3; axis++) {
            int u = axis == 0 ? 1 : 0, w = axis == 2 ? 1 : 2;
            for (double s : box[axis]) {
                for (int i = 0; i <= n; i++) {
                    for (int j = 0; j <= n; j++) {
                        double[] q = new double[3];
                        q[axis] = s;
                        q[u] = box[u][0] + (box[u][1] - box[u][0]) * i / n;
                        q[w] = box[w][0] + (box[w][1] - box[w][0]) * j / n;
                        out.add(q);
                    }
                }
            }
        }
        return out;
    }

    /** Distance from p to the box, or minus its depth when inside. */
    private static double outside(double[] p, double[][] box) {
        double d2 = 0, depth = 9;
        for (int i = 0; i < 3; i++) {
            double d = Math.max(box[i][0] - p[i], Math.max(0, p[i] - box[i][1]));
            d2 += d * d;
            depth = Math.min(depth, Math.min(p[i] - box[i][0], box[i][1] - p[i]));
        }
        return d2 > 0 ? Math.sqrt(d2) : -depth;
    }

    @Test
    void riderBodyFacesTheBroomAndTheLookTurnsTheHead() throws Exception {
        SceneDef s = scene("{\"length\":100,\"actors\":[{\"id\":\"b\",\"type\":\"hogwartsmagic:memory_broom\","
                + "\"path\":[{\"t\":0,\"pos\":[0,5,0]}],\"look\":[{\"t\":0,\"yaw\":30}]},"
                + "{\"id\":\"r\",\"type\":\"player\",\"ride\":\"b\",\"look\":[{\"t\":0,\"yaw\":40},{\"t\":50,\"yaw\":200}]}]}");
        for (float t = 0; t <= 60; t += 5) {
            Vehicle.Seat seat = Vehicle.seat(s, s.actors().get(1), t);
            assertEquals(30, seat.yaw(), 1e-6, "the body faces the broom at " + t);
            assertEquals(30, seat.vehicleYaw(), 1e-6);
        }
        assertEquals(40, BroomFlight.headTurn(40), 1e-6);
        assertEquals(75, BroomFlight.headTurn(120), 1e-6);
        assertEquals(-75, BroomFlight.headTurn(200), 1e-6, "200 is 160 the other way: clamped to -75");
        assertEquals(-30, BroomFlight.headTurn(330), 1e-4);
        assertEquals(10, BroomFlight.headTurn(-350), 1e-4);
    }

    @Test
    void seatedLifeNeedsNoHistory() {
        // Hover (slow), fly off, slow to a hover: the seat fraction is a pure function of t, ramps over SEAT_EASE
        // ticks, and never steps by more than a tick's share.
        List<Keyframe> keys = new ArrayList<>();
        keys.add(new Keyframe(0, new float[]{0, 5, 0}, Ease.LINEAR));
        keys.add(new Keyframe(40, new float[]{0, 5, 0.3f}, Ease.LINEAR));
        keys.add(new Keyframe(80, new float[]{0, 5, 12}, Ease.IN_OUT));
        keys.add(new Keyframe(140, new float[]{0, 5, 12.4f}, Ease.OUT));
        Track path = new Track(keys);
        assertEquals(0, BroomFlight.seatFraction(path, 20, 0), 0, "hovering: standing life");
        assertEquals(1, BroomFlight.seatFraction(path, 65, 0), 0, "flying: seated life");
        assertEquals(0, BroomFlight.seatFraction(path, 65, 70), 0, "not before the window");
        float prev = BroomFlight.seatFraction(path, 0, 0);
        for (float t = 0; t < 160; t += 0.25f) {
            float now = BroomFlight.seatFraction(path, t, 0);
            assertTrue(Math.abs(now - prev) <= 1f / (BroomFlight.SEAT_EASE + 1) + 1e-6, "step at " + t);
            assertEquals(now, BroomFlight.seatFraction(path, t, 0), 0, "same answer every time");
            prev = now;
        }
    }
}
