package com.hogwartsmagic.pensieve;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.Ease;
import com.hogwartsmagic.pensieve.scene.Keyframe;
import com.hogwartsmagic.pensieve.scene.MemoryBall;
import com.hogwartsmagic.pensieve.scene.Track;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryBallTest {
    private static final double[] UP = {0, 1, 0};

    @Test
    void rollFollowsDistance() {
        // A ball that travels distance d rolls through d / radius radians, whatever the direction (and the speed it
        // took, in however many ticks).
        double r = 0.275;
        double[][] directions = {{1, 0, 0}, {0, 0, -1}, {0.6, 0, 0.8}, {0.5, 0.5, 0.70710678}};
        for (double[] d : directions) {
            double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            double horizontal = Math.sqrt(d[0] * d[0] + d[2] * d[2]) / len;
            for (int steps : new int[]{1, 7, 40}) {
                MemoryBall.Spin s = new MemoryBall.Spin();
                double total = 0.6;
                for (int i = 0; i < steps; i++) {
                    s.roll(d[0] / len * total / steps, d[1] / len * total / steps, d[2] / len * total / steps, r);
                }
                assertEquals(total * horizontal / r, s.angle(), 1e-9, "angle for " + java.util.Arrays.toString(d) + " in " + steps);
            }
        }
    }

    @Test
    void rollsTopForwardAboutTheTravelAxis() {
        // Rolling along +x a quarter turn takes the top of the ball to the front, about the horizontal axis
        // across the travel (up x travel), as a ball rolls on the ground.
        MemoryBall.Spin s = new MemoryBall.Spin();
        double r = 0.25;
        s.roll(r * Math.PI / 2, 0, 0, r);
        double[] top = s.turn(UP);
        assertArrayEquals(new double[]{1, 0, 0}, top, 1e-9);
        // The same ball heading -z: the top goes to -z.
        MemoryBall.Spin z = new MemoryBall.Spin();
        z.roll(0, 0, -r * Math.PI / 2, r);
        assertArrayEquals(new double[]{0, 0, -1}, z.turn(UP), 1e-9);
    }

    @Test
    void stillBallDoesNotRoll() {
        MemoryBall.Spin s = new MemoryBall.Spin();
        for (int i = 0; i < 100; i++) s.roll(0, 0, 0, 0.275);
        assertEquals(0, s.angle(), 0);
        assertArrayEquals(new double[]{0.3, -0.4, 0.5}, s.turn(new double[]{0.3, -0.4, 0.5}), 0);
        // A hair of numerical noise is not a roll either.
        s.roll(1e-12, 0, -1e-12, 0.275);
        assertEquals(0, s.angle(), 1e-9);
    }

    @Test
    void rollNeverBlowsUp() {
        // Many ticks of arbitrary motion keep the quaternion a unit one (the entity runs for minutes).
        MemoryBall.Spin s = new MemoryBall.Spin();
        java.util.Random rnd = new java.util.Random(5);
        for (int i = 0; i < 20_000; i++) {
            s.roll(rnd.nextGaussian() * 0.8, rnd.nextGaussian() * 0.3, rnd.nextGaussian() * 0.8, 0.225);
        }
        double[] v = s.turn(new double[]{0.6, 0, 0.8});
        assertEquals(1.0, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]), 1e-9);
    }

    @Test
    void copyKeepsThePreviousTick() {
        MemoryBall.Spin a = new MemoryBall.Spin();
        a.roll(1, 0, 0, 0.3);
        MemoryBall.Spin b = new MemoryBall.Spin();
        b.set(a);
        a.roll(0, 0, 1, 0.3);
        assertNotEquals(a.angle(), 0.0);
        assertEquals(1.0 / 0.3, b.angle(), 1e-9);
    }

    @Test
    void sizesAreTheBriefsDiameters() {
        assertEquals(0.55, MemoryBall.diameter(SceneNames.QUAFFLE_ACTOR), 0);
        assertEquals(0.45, MemoryBall.diameter(SceneNames.BLUDGER_ACTOR), 0);
        assertEquals(0.22, MemoryBall.diameter(SceneNames.REMEMBRALL_ACTOR), 0);
        assertEquals(0.55 / 2 * 0.85, MemoryBall.radius(SceneNames.QUAFFLE_ACTOR, 0.85f), 1e-6);
        double[] box = MemoryBall.box(SceneNames.BLUDGER_ACTOR, 2f);
        assertArrayEquals(new double[]{-0.45, -0.45, -0.45, 0.45, 0.45, 0.45}, box, 1e-6);
        assertTrue(MemoryBall.isBall(SceneNames.REMEMBRALL_ACTOR));
        assertFalse(MemoryBall.isBall(SceneNames.BROOM_ACTOR));
        assertFalse(MemoryBall.isBall("hogwartsmagic:golden_snitch"));
        assertEquals(SceneNames.BALLS, java.util.Set.of(SceneNames.QUAFFLE_ACTOR, SceneNames.BLUDGER_ACTOR,
                SceneNames.REMEMBRALL_ACTOR));
    }

    @Test
    void bludgerOnlyShakesWhenFast() {
        for (int age = 0; age < 200; age++) {
            assertArrayEquals(new double[]{0, 0, 0}, MemoryBall.vibration(0, age), 0);
            assertArrayEquals(new double[]{0, 0, 0}, MemoryBall.vibration(MemoryBall.VIBRATE_FROM * 0.99, age), 0);
        }
        double peak = 0;
        for (int age = 0; age < 200; age++) {
            double[] v = MemoryBall.vibration(2.0, age + 0.37);
            double m = Math.max(Math.abs(v[0]), Math.max(Math.abs(v[1]), Math.abs(v[2])));
            assertTrue(m <= MemoryBall.VIBRATE_MAX + 1e-12, "bounded " + m);
            peak = Math.max(peak, m);
        }
        assertTrue(peak > MemoryBall.VIBRATE_MAX * 0.5, "a fast Bludger visibly shakes: " + peak);
        // It fades in with speed, no snap.
        for (double s = 0; s < 3; s += 0.01) {
            double a = Math.abs(MemoryBall.vibrationGain(s + 0.01) - MemoryBall.vibrationGain(s));
            assertTrue(a < 0.2, "gain step " + a);
        }
    }

    @Test
    void sphereFacesOutward() {
        int lat = 10, lon = 16;
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                double[] a = MemoryBall.unit(i, j, lat, lon), b = MemoryBall.unit(i, j + 1, lat, lon),
                        c = MemoryBall.unit(i + 1, j + 1, lat, lon);
                double[] ab = sub(b, a), ac = sub(c, a);
                double[] n = {ab[1] * ac[2] - ab[2] * ac[1], ab[2] * ac[0] - ab[0] * ac[2], ab[0] * ac[1] - ab[1] * ac[0]};
                double len = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
                if (len < 1e-9) continue; // a pole's degenerate quad
                double dot = (n[0] * (a[0] + c[0]) + n[1] * (a[1] + c[1]) + n[2] * (a[2] + c[2])) / len;
                assertTrue(dot > 0, "quad " + i + "," + j + " is wound inward");
            }
        }
        for (int i = 0; i <= lat; i++) {
            double[] u = MemoryBall.unit(i, 3, lat, lon);
            assertEquals(1.0, Math.sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2]), 1e-12);
        }
        // The seam: the last column closes on the first.
        assertArrayEquals(MemoryBall.unit(4, 0, lat, lon), MemoryBall.unit(4, lon, lat, lon), 1e-12);
    }

    @Test
    void bludgerIsDentedAndTheOthersAreRound() {
        double lowest = 1;
        for (int i = 0; i <= 20; i++) {
            for (int j = 0; j < 40; j++) {
                double[] u = MemoryBall.unit(i, j, 20, 40);
                double f = MemoryBall.dent(SceneNames.BLUDGER_ACTOR, u[0], u[1], u[2]);
                assertTrue(f <= 1.0 && f >= 0.88, "dent factor " + f);
                lowest = Math.min(lowest, f);
                assertEquals(1.0, MemoryBall.dent(SceneNames.QUAFFLE_ACTOR, u[0], u[1], u[2]), 0);
                assertEquals(1.0, MemoryBall.dent(SceneNames.REMEMBRALL_ACTOR, u[0], u[1], u[2]), 0);
            }
        }
        assertTrue(lowest < 0.97, "the dents are deep enough to see: " + lowest);
    }

    @Test
    void smokeStaysInsideTheGlassAndSwirls() {
        int seed = MemoryBall.seedFor("remembrall");
        double minX = 9, maxX = -9;
        for (double t = 0; t < 600; t += 0.5) {
            for (int i = 0; i < MemoryBall.PUFFS; i++) {
                double[] p = MemoryBall.smoke(seed, i, t);
                double reach = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]) + p[3];
                assertTrue(reach <= MemoryBall.SMOKE_REACH + 1e-9, "puff " + i + " at " + t + " reaches " + reach);
                assertTrue(p[3] > 0.05);
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
            }
        }
        assertTrue(maxX - minX > 0.6, "the smoke moves across the ball");
        // Smooth: no puff jumps between half-ticks.
        for (double t = 0; t < 300; t += 0.5) {
            for (int i = 0; i < MemoryBall.PUFFS; i++) {
                double[] a = MemoryBall.smoke(seed, i, t), b = MemoryBall.smoke(seed, i, t + 0.5);
                assertTrue(Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]) < 0.2);
            }
        }
    }

    @Test
    void smokeClockComesFromTheActorId() {
        int a = MemoryBall.seedFor("remembrall"), b = MemoryBall.seedFor("remembrall_2");
        assertEquals(a, MemoryBall.seedFor(new String("remembrall")));
        assertNotEquals(a, b);
        assertArrayEquals(MemoryBall.smoke(a, 2, 77.25), MemoryBall.smoke(a, 2, 77.25), 0);
        assertNotEquals(MemoryBall.smoke(a, 2, 77.25)[0], MemoryBall.smoke(b, 2, 77.25)[0]);
    }

    @Test
    void pythonMirrorAgrees() throws Exception {
        JsonObject all = JsonParser.parseString(Files.readString(
                Path.of("build_scripts/pensieve_scenes/ball_cases.json"))).getAsJsonObject();
        JsonObject diameters = all.getAsJsonObject("diameter");
        for (String type : SceneNames.BALLS) {
            assertEquals(MemoryBall.diameter(type), diameters.get(type).getAsDouble(), 0, type);
        }
        assertEquals(SceneNames.BALLS.size(), diameters.size());
        JsonArray boxes = all.getAsJsonArray("boxes");
        assertTrue(boxes.size() >= 6);
        for (var e : boxes) {
            JsonObject c = e.getAsJsonObject();
            double[] want = MemoryBall.box(c.get("type").getAsString(), c.get("scale").getAsFloat());
            JsonArray lo = c.getAsJsonArray("lo"), hi = c.getAsJsonArray("hi");
            for (int k = 0; k < 3; k++) {
                assertEquals(want[k], lo.get(k).getAsDouble(), 1e-6);
                assertEquals(want[3 + k], hi.get(k).getAsDouble(), 1e-6);
            }
        }
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }
    private static Track line(double vx, double vz, int ticks) {
        return new Track(List.of(
                new Keyframe(0, new float[]{0, 2, 0}, Ease.LINEAR),
                new Keyframe(ticks, new float[]{(float) (vx * ticks), 2, (float) (vz * ticks)}, Ease.LINEAR)));
    }

    @Test
    void verticalDropDoesNotSpin() {
        Track drop = new Track(List.of(
                new Keyframe(0, new float[]{1, 5, 1}, Ease.LINEAR),
                new Keyframe(30, new float[]{1, 0.3f, 1}, Ease.LINEAR)));
        MemoryBall.Roll roll = new MemoryBall.Roll(drop, 0.275);
        for (double t = 0; t <= 40; t += 0.25) assertEquals(0, roll.at(t).angle(), 1e-9, "t " + t);
    }

    @Test
    void fastBallsSpinForwardAtEveryPartial() {
        // A shortest-arc slerp between ticks runs backwards above pi x radius per tick (0.71 blocks/tick for a
        // Bludger). Each tick's own axis and angle are applied in proportion, so any speed turns forward and the
        // turn is exactly the distance rolled so far, at 10 partials a tick.
        double r = 0.225;
        for (double v : new double[]{0.5, 1.0, 1.5, 1.9}) {
            MemoryBall.Roll roll = new MemoryBall.Roll(line(v, 0, 40), r);
            for (int i = 0; i <= 300; i++) {
                double t = i / 10.0;
                MemoryBall.Spin want = new MemoryBall.Spin();
                want.rollAbout(0, -1, v * t / r);   // the same turn in one go: the axis across a +x travel
                for (double[] probe : new double[][]{{0, 1, 0}, {0.6, 0, 0.8}}) {
                    assertArrayEquals(want.turn(probe), roll.at(t).turn(probe), 1e-4, "v " + v + " t " + t);
                }
                if (i > 0) {
                    // Each tenth of a tick turns the top toward +x by a tenth of that tick's angle (mod a turn).
                    double step = v / 10 / r;
                    double[] a = roll.at(t - 0.1).turn(new double[]{0, 1, 0}), b = roll.at(t).turn(new double[]{0, 1, 0});
                    double ang = Math.atan2(a[1] * b[0] - a[0] * b[1], a[0] * b[0] + a[1] * b[1]);
                    assertEquals(Math.atan2(Math.sin(step), Math.cos(step)), ang, 1e-4, "step v " + v + " t " + t);
                    assertTrue(step < Math.PI, "a tenth of a tick turns less than half a turn");
                }
            }
        }
    }

    @Test
    void lateJoinerGetsTheSameRoll() {
        // The roll is a pure function of the path and the clock: a second Roll (a late joiner) asked out of order
        // agrees with one asked in order, on a curving path.
        Track curve = new Track(List.of(
                new Keyframe(0, new float[]{0, 2, 0}, Ease.LINEAR),
                new Keyframe(20, new float[]{10, 3, 2}, Ease.LINEAR),
                new Keyframe(40, new float[]{12, 2, 14}, Ease.LINEAR),
                new Keyframe(60, new float[]{-3, 2, 15}, Ease.LINEAR)));
        MemoryBall.Roll first = new MemoryBall.Roll(curve, 0.2), late = new MemoryBall.Roll(curve, 0.2);
        double[] ts = {59.5, 3.25, 40, 12.75, 0, 33.3, 61, 25};
        double[] got = new double[ts.length];
        for (int i = 0; i < ts.length; i++) got[i] = late.at(ts[i]).angle();
        for (double t = 0; t <= 61; t += 0.25) first.at(t);
        for (int i = 0; i < ts.length; i++) assertEquals(first.at(ts[i]).angle(), got[i], 0, "t " + ts[i]);
        assertTrue(first.at(59.5).angle() > 1, "it did roll");
    }

    @Test
    void aCutDoesNotRollTheBall() {
        Track cut = new Track(List.of(
                new Keyframe(0, new float[]{0, 2, 0}, Ease.LINEAR),
                new Keyframe(10, new float[]{0, 2, 0}, Ease.LINEAR),
                new Keyframe(10.001f, new float[]{30, 2, 0}, Ease.LINEAR),
                new Keyframe(20, new float[]{30, 2, 0}, Ease.LINEAR)));
        assertEquals(0, new MemoryBall.Roll(cut, 0.25).at(20).angle(), 1e-9);
    }

    @Test
    void speedIsThePathsOwnStep() {
        MemoryBall.Roll roll = new MemoryBall.Roll(line(0.8, 0.6, 40), 0.25);
        assertEquals(1.0, roll.speed(20.5), 1e-4);
        assertEquals(0.0, roll.speed(100), 1e-9);
    }

    /** Extent of the item model's elements along any axis, in model units (rotated cubes included). */
    private static double modelDiameter(JsonObject model) {
        double lo = 1e9, hi = -1e9;
        for (var e : model.getAsJsonArray("elements")) {
            JsonObject el = e.getAsJsonObject();
            JsonArray from = el.getAsJsonArray("from"), to = el.getAsJsonArray("to");
            double deg = 0;
            String axis = "y";
            if (el.has("rotation")) {
                deg = el.getAsJsonObject("rotation").get("angle").getAsDouble();
                axis = el.getAsJsonObject("rotation").get("axis").getAsString();
            }
            double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
            for (int i = 0; i < 8; i++) {
                double[] p = new double[3];
                for (int k = 0; k < 3; k++) {
                    p[k] = ((i >> k & 1) == 0 ? from.get(k).getAsDouble() : to.get(k).getAsDouble()) - 8;
                }
                double[] q = p.clone();
                if (axis.equals("y")) {
                    q[0] = p[0] * c + p[2] * s;
                    q[2] = -p[0] * s + p[2] * c;
                } else if (axis.equals("x")) {
                    q[1] = p[1] * c - p[2] * s;
                    q[2] = p[1] * s + p[2] * c;
                } else {
                    q[0] = p[0] * c - p[1] * s;
                    q[1] = p[0] * s + p[1] * c;
                }
                for (double v : q) {
                    lo = Math.min(lo, v);
                    hi = Math.max(hi, v);
                }
            }
        }
        return hi - lo;
    }

    @Test
    void remembrallItemMatchesTheActorSize() throws Exception {
        // The item model is a small glass sphere of cubes whose in-hand diameter (model extent x display scale) is the
        // actor's 0.22 in third and first person, both hands, so the swap at the catch does not pop. The held item is
        // drawn inside the holder's scale (the PoseStack), so give the Remembrall actor its holder's scale.
        JsonObject model = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/hogwartsmagic/models/item/memory_remembrall.json"))).getAsJsonObject();
        double diameter = modelDiameter(model) / 16.0;   // blocks at display scale 1
        JsonObject display = model.getAsJsonObject("display");
        for (String slot : new String[]{"thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand",
                "firstperson_lefthand"}) {
            JsonArray scale = display.getAsJsonObject(slot).getAsJsonArray("scale");
            assertEquals(scale.get(0).getAsDouble(), scale.get(1).getAsDouble(), 0, slot);
            assertEquals(scale.get(0).getAsDouble(), scale.get(2).getAsDouble(), 0, slot);
            assertEquals(MemoryBall.diameter(SceneNames.REMEMBRALL_ACTOR), diameter * scale.get(0).getAsDouble(), 0.004, slot);
        }
    }
}
