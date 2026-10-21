package com.hogwartsmagic.pensieve;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.MemoryBall;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

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
        double[][] directions = {{1, 0, 0}, {0, 0, -1}, {0.6, 0, 0.8}, {0.5, 0.5, 0.70710678}, {0, 1, 0}};
        for (double[] d : directions) {
            double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            for (int steps : new int[]{1, 7, 40}) {
                MemoryBall.Spin s = new MemoryBall.Spin();
                double total = 0.6;
                for (int i = 0; i < steps; i++) {
                    s.roll(d[0] / len * total / steps, d[1] / len * total / steps, d[2] / len * total / steps, r);
                }
                assertEquals(total / r, s.angle(), 1e-9, "angle for " + java.util.Arrays.toString(d) + " in " + steps);
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
}
