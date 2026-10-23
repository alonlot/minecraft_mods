package com.hogwartsmagic.pensieve;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.DragonFire;
import com.hogwartsmagic.pensieve.scene.DragonFlight;
import com.hogwartsmagic.pensieve.scene.EffectSeed;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.hogwartsmagic.pensieve.scene.SceneValidator;
import com.hogwartsmagic.pensieve.scene.ScorchDecal;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonFireTest {
    @Test
    void frontGrowsAndCaps() {
        assertEquals(0, DragonFire.front(-3), 0);
        assertEquals(0, DragonFire.front(0), 0);
        double last = 0;
        for (int i = 1; i <= 400; i++) {
            double age = i * 0.1;
            double f = DragonFire.front(age);
            assertTrue(f >= last, "the front never draws back");
            assertTrue(f - last <= DragonFire.SPEED * 0.1 + 1e-9, "it grows at most SPEED a tick");
            last = f;
        }
        assertEquals(6.0, DragonFire.front(5), 1e-9);
        assertEquals(DragonFire.MAX_REACH, DragonFire.front(15), 1e-9);
        assertEquals(DragonFire.MAX_REACH, DragonFire.front(1000), 0);
    }

    @Test
    void strengthGutters() {
        int d = 40;
        assertEquals(0, DragonFire.strength(-1, d), 0);
        assertEquals(0, DragonFire.strength(0, d), 0);
        assertEquals(1, DragonFire.strength(DragonFire.RISE_TICKS, d), 1e-9);
        assertEquals(1, DragonFire.strength(20, d), 0);
        assertEquals(1, DragonFire.strength(d - DragonFire.GUTTER_TICKS, d), 1e-9);
        assertEquals(0, DragonFire.strength(d, d), 1e-9);
        assertEquals(0, DragonFire.strength(d + 1, d), 0);
        // Up over the rise, down over the gutter, smoothly.
        double last = 1;
        for (double a = d - DragonFire.GUTTER_TICKS; a <= d; a += 0.25) {
            double s = DragonFire.strength(a, d);
            assertTrue(s <= last + 1e-12, "guttering only falls");
            assertTrue(last - s < 0.1, "no step");
            last = s;
        }
        // The guttering jet sputters: some puffs born late are skipped, none in full flow.
        long seed = 1234;
        int skippedLate = 0, skippedFull = 0;
        for (int k = 0; k < DragonFire.puffs(d); k++) {
            double age = DragonFire.birth(k);
            boolean on = DragonFire.emitted(DragonFire.look(seed, k), age, d);
            if (!on && age > d - DragonFire.GUTTER_TICKS * 0.6) skippedLate++;
            if (!on && age > DragonFire.RISE_TICKS && age < d - DragonFire.GUTTER_TICKS) skippedFull++;
        }
        assertTrue(skippedLate > 0, "the end sputters");
        assertEquals(0, skippedFull, "full flow skips nothing");
    }

    @Test
    void scorchGrowsWithHitTime() {
        assertEquals(0, DragonFire.scorchRadius(0), 0);
        assertEquals(0, DragonFire.scorchRadius(-5), 0);
        assertEquals(DragonFire.SCORCH_MAX, DragonFire.scorchRadius(DragonFire.SCORCH_TICKS), 1e-9);
        assertEquals(DragonFire.SCORCH_MAX, DragonFire.scorchRadius(500), 0);
        double last = 0;
        for (int i = 1; i <= 60; i++) {
            double r = DragonFire.scorchRadius(i * 0.5);
            assertTrue(r >= last);
            last = r;
        }
        // A spot grows with the hits it takes, not with the clock: no more hits, no more growth.
        List<DragonFire.Spot> spots = new ArrayList<>();
        double[] up = {0, 1, 0};
        for (int k = 0; k < 40; k++) DragonFire.addHit(spots, 0.1 * (k % 3), 0, 0, up, 100 + k * 0.25, 24);
        assertEquals(1, spots.size(), "nearby hits join one spot");
        DragonFire.Spot s = spots.get(0);
        assertEquals(10, s.hitAge(200), 1e-9);
        assertEquals(DragonFire.scorchRadius(10), s.radius(200), 1e-12);
        assertEquals(s.radius(110), s.radius(1000), 0);
        assertTrue(s.radius(102) < s.radius(108));
        // A far hit makes a second spot; the embers glow while hit and fade after.
        DragonFire.addHit(spots, 5, 0, 0, up, 120, 24);
        assertEquals(2, spots.size());
        assertEquals(1, DragonFire.emberGlow(109, s.lastHit(109)), 0);
        assertTrue(DragonFire.emberGlow(150, s.lastHit(150)) < 1);
        assertEquals(0, DragonFire.emberGlow(110 + DragonFire.EMBER_TICKS + 3, s.lastHit(500)), 1e-9);
    }

    @Test
    void deterministicAcrossOrigins() throws Exception {
        // The seed is the breathe's scene-relative time and place: the session slot's origin never enters it.
        float[] root = {3.5f, 0f, -12f};
        long a = DragonFire.seed("horntail", 120f, root);
        assertEquals(a, DragonFire.seed("horntail", 120f, root.clone()));
        assertTrue(a != DragonFire.seed("horntail2", 120f, root), "two dragons breathing at once differ");
        assertTrue(a != DragonFire.seed("horntail", 121f, root), "two breaths differ");
        // EffectSeed of the same relative point from two slot origins is the same.
        assertEquals(EffectSeed.of(1003.5, 64, 988, 1000, 64, 1000, 120f), EffectSeed.of(-4996.5, 64, 4988, -5000, 64, 5000, 120f));
        // Each puff's birth (DragonFire.birth, as the renderer calls it) about two session origins: the drawn snout
        // between two frames, this frame's, or her pose's estimate, aimed at a point target or along her head.
        SceneDef.ActorDef dragon = mouthScene().actors().get(0);
        float[] point = {4f, 0.5f, 13f};
        double[] g1 = {1000, 64, 1000}, g2 = {-30000, 70, 52000};
        int[] seen = new int[3];
        for (double tb = 20; tb < 60; tb += 0.25) {
            double now = Math.floor(tb) + 0.6, lastT = now - 0.55;
            double[] relLast = {0.3, 4.6 + 0.01 * tb, 3.0}, relCur = {0.35, 4.62 + 0.01 * tb, 3.1};
            double[] dLast = norm(new double[]{0.1, -0.6, 0.8}), dCur = norm(new double[]{0.12, -0.58, 0.8});
            boolean drawn = ((int) tb) % 3 != 0;
            for (float[] target : new float[][]{point, null}) {
                DragonFire.Birth b1 = birthAbout(g1, tb, now, relLast, dLast, lastT, drawn ? relCur : null, dCur, dragon, target);
                DragonFire.Birth b2 = birthAbout(g2, tb, now, relLast, dLast, lastT, drawn ? relCur : null, dCur, dragon, target);
                assertEquals(b1.from(), b2.from());
                seen[b1.from()]++;
                for (int i = 0; i < 3; i++) {
                    assertEquals(b1.o()[i] - g1[i], b2.o()[i] - g2[i], 1e-6, "mouth at " + tb);
                    assertEquals(b1.d()[i], b2.d()[i], 1e-9, "aim at " + tb);
                    assertEquals(b1.head()[i], b2.head()[i], 1e-9, "head at " + tb);
                }
                if (target == null) assertArrayEquals(b1.head(), b1.d(), 0, "no target: along her head");
            }
        }
        assertTrue(seen[DragonFire.FROM_LERP] > 0 && seen[DragonFire.FROM_NOW] > 0 && seen[DragonFire.FROM_POSE] > 0,
                "every mouth source is exercised: " + java.util.Arrays.toString(seen));
        // The pose's mouth is scene-relative: the same estimate about any origin.
        double[] m = DragonFlight.mouthEstimate(dragon, 30);
        DragonFire.Birth p1 = birthAbout(g1, 30, 40, null, null, Double.NaN, null, null, dragon, point);
        assertEquals(DragonFire.FROM_POSE, p1.from());
        assertEquals(m[0], p1.o()[0] - g1[0], 1e-9);
    }

    /** DragonFire.birth with every world input built from scene-relative ones about the slot origin {@code g}. */
    private static DragonFire.Birth birthAbout(double[] g, double tb, double now, double[] relLast, double[] dLast,
                                               double lastT, double[] relCur, double[] dCur, SceneDef.ActorDef dragon,
                                               float[] target) {
        double[] last = relLast == null ? null : new double[]{relLast[0] + g[0], relLast[1] + g[1], relLast[2] + g[2]};
        double[] cur = relCur == null ? null : new double[]{relCur[0] + g[0], relCur[1] + g[1], relCur[2] + g[2]};
        return DragonFire.birth(tb, now, last, dLast, lastT, cur, dCur, () -> {
            double[] e = DragonFlight.mouthEstimate(dragon, tb);
            return new double[]{e[0] + g[0], e[1] + g[1], e[2] + g[2], e[3], e[4], e[5]};
        }, target == null ? null : DragonFire.pointTarget(target, g));
    }

    private static SceneDef mouthScene() throws Exception {
        JsonObject all = JsonParser.parseString(Files.readString(
                Path.of("build_scripts/pensieve_scenes/dragon_fire_cases.json"))).getAsJsonObject();
        return SceneParser.parse("breath", all.getAsJsonObject("mouth_scenes").getAsJsonObject("breath"));
    }

    @Test
    void splashIsHeldOffAWall() {
        // A jet from up and back lands on a floor (y 0) 2 blocks before a wall (z 3): every splashing puff, its
        // spread held by its own splash ray (as the renderer casts it), stays on this side of the wall.
        double[] o = {0.5, 6, -8}, target = {0.5, 0, 1};
        double[] d = norm(new double[]{target[0] - o[0], target[1] - o[1], target[2] - o[2]});
        double hit = (0 - o[1]) / d[1];
        double[] n = {0, 1, 0};
        long seed = DragonFire.seed("h", 40f, new float[]{0, 0, 0});
        double worstHeld = -1e9, worstLoose = -1e9;
        for (int k = 0; k < DragonFire.puffs(40); k++) {
            DragonFire.Look look = DragonFire.look(seed, k);
            double[] e = DragonFire.splashDir(d, n, look);
            assertEquals(0, e[0] * n[0] + e[1] * n[1] + e[2] * n[2], 1e-9, "the splash runs along the face");
            double startZ = o[2] + d[2] * hit + n[2] * DragonFire.SPLASH_RAY_LIFT;
            double free = e[2] > 1e-9 ? (3 - startZ) / e[2] : Double.POSITIVE_INFINITY;
            if (free > DragonFire.SPLASH_SPREAD) free = Double.POSITIVE_INFINITY;
            double reach = DragonFire.splashReach(free);
            for (double a = hit / DragonFire.SPEED; a <= DragonFire.flameLife(hit); a += 0.25) {
                DragonFire.Flame f = DragonFire.flame(o, d, hit, n, look, a, 1, reach);
                if (f != null && f.stage() == DragonFire.Flame.SPLASHING) worstHeld = Math.max(worstHeld, f.z());
                DragonFire.Flame g = DragonFire.flame(o, d, hit, n, look, a, 1);
                if (g != null && g.stage() == DragonFire.Flame.SPLASHING) worstLoose = Math.max(worstLoose, g.z());
            }
            DragonFire.Smoke sm = DragonFire.smoke(o, d, hit, n, look, DragonFire.flameLife(hit), 1, reach);
            if (sm != null) assertTrue(sm.z() < 3, "its smoke rises from this side of the wall");
        }
        assertTrue(worstHeld < 3 - 0.05, "held short of the wall: " + worstHeld);
        assertTrue(worstLoose > 3, "unheld, the splash would run on into the wall: " + worstLoose);
        assertEquals(0, DragonFire.splashReach(0.1), 0);
        assertEquals(Double.POSITIVE_INFINITY, DragonFire.splashReach(Double.POSITIVE_INFINITY), 0);
    }

    @Test
    void drawCountsBounded() {
        // Two 40-tick jets hitting a wall 12 out, sampled every quarter tick: flame and smoke puffs drawn a jet.
        double[] o = {0, 10, 0}, d = {0, 0, 1}, n = {0, 0, -1};
        int peakFlame = 0, peakSmoke = 0;
        for (long seed : new long[]{DragonFire.seed("h", 100f, new float[]{0, 0, 0}), DragonFire.seed("h2", 100f, new float[]{5, 0, 0})}) {
            for (double t = 0; t <= DragonFire.lifetime(40) + 1; t += 0.25) {
                int flames = 0, smoke = 0;
                for (int k = 0; k < DragonFire.puffs(40); k++) {
                    double age = DragonFire.birth(k);
                    if (age > t) break;
                    DragonFire.Look look = DragonFire.look(seed, k);
                    if (!DragonFire.emitted(look, age, 40)) continue;
                    if (DragonFire.flame(o, d, 12, n, look, t - age, 1) != null) flames++;
                    if (DragonFire.smokes(look, age, 40) && DragonFire.smoke(o, d, 12, n, look, t - age, 1) != null) smoke++;
                }
                peakFlame = Math.max(peakFlame, flames);
                peakSmoke = Math.max(peakSmoke, smoke);
            }
        }
        assertTrue(peakFlame <= 110 && peakFlame > 60, "flame puffs a jet at the peak: " + peakFlame);
        assertTrue(peakSmoke <= 180 && peakSmoke > 60, "smoke puffs a jet at the peak: " + peakSmoke);
        // The scorch: whatever the jets leave, a pass draws at most MAX_FRAME_CELLS cells (the worst case: three
        // breathes of full spots, every face a full 3 x 3), nearest first, none out of view or past the cull distance.
        int spots = 3 * ScorchDecal.MAX_SPOTS;
        double[] dist = new double[spots], rad = new double[spots];
        boolean[] vis = new boolean[spots];
        int[] cells = new int[spots];
        Random r = new Random(4);
        for (int i = 0; i < spots; i++) {
            dist[i] = r.nextDouble() * 140;
            rad[i] = DragonFire.SCORCH_MAX;
            vis[i] = r.nextInt(5) != 0;
            cells[i] = ScorchDecal.MAX_FACES * ScorchDecal.SPLIT * ScorchDecal.SPLIT;
        }
        int[] plan = ScorchDecal.plan(dist, rad, vis, cells, ScorchDecal.MAX_FRAME_CELLS);
        int total = 0;
        double last = -1;
        for (int i : plan) {
            total += cells[i];
            assertTrue(vis[i] && dist[i] <= ScorchDecal.CULL_DISTANCE + rad[i], "culled spot " + i + " drawn");
            assertTrue(dist[i] >= last, "nearest first");
            last = dist[i];
        }
        assertTrue(total <= ScorchDecal.MAX_FRAME_CELLS, "cells a pass: " + total);
        assertTrue(plan.length > 0);
        // A realistic sweep (24 spots of about 180 cells) is drawn whole.
        int[] small = new int[24];
        double[] near = new double[24], r24 = new double[24];
        boolean[] all = new boolean[24];
        java.util.Arrays.fill(small, 180);
        java.util.Arrays.fill(r24, 2.5);
        java.util.Arrays.fill(all, true);
        for (int i = 0; i < 24; i++) near[i] = 10 + i;
        assertEquals(24, ScorchDecal.plan(near, r24, all, small, ScorchDecal.MAX_FRAME_CELLS).length);
    }

    @Test
    void scorchDecalBakesOnceAndFallsOff() {
        // A flat 7 x 7 patch of block tops round a spot at its centre.
        List<ScorchDecal.Face> faces = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                faces.add(new ScorchDecal.Face(new double[]{x, 1, z}, new double[]{x + 1, 1, z}, new double[]{x + 1, 1, z + 1},
                        new double[]{x, 1, z + 1}, 0, 1, 0, x * 31 + z, 0L));
            }
        }
        ScorchDecal.Baked b = ScorchDecal.bake(faces, 0.5, 1, 0.5, 2.5);
        assertTrue(b.cells > 0 && b.cells <= faces.size() * ScorchDecal.SPLIT * ScorchDecal.SPLIT, "cells " + b.cells);
        assertTrue(b.cells < faces.size() * 9, "the far corners fade to nothing and are left out: " + b.cells);
        for (int c = 0; c < b.cells; c++) {
            for (int i = 0; i < 4; i++) {
                double x = b.xyz[c * 12 + i * 3], y = b.xyz[c * 12 + i * 3 + 1], z = b.xyz[c * 12 + i * 3 + 2];
                assertEquals(0, y, 1e-6, "relative to the spot's centre, on the face");
                double dd = Math.sqrt(x * x + z * z);
                assertEquals(ScorchDecal.fall(dd / 2.5), b.fall[c * 4 + i], 1e-5);
            }
        }
        assertEquals(1, ScorchDecal.fall(0.4), 0);
        assertEquals(0, ScorchDecal.fall(1.0), 1e-12);
        // The lift grows with the camera's distance (depth precision), never under the base.
        assertEquals(ScorchDecal.LIFT, ScorchDecal.lift(5), 0);
        assertTrue(ScorchDecal.lift(100) > 0.012);
        assertEquals(b.cells, ScorchDecal.bake(faces, 0.5, 1, 0.5, 2.5).cells, "baking is pure");
        // The soot texture is laid in world space: two spots (here 1.7 apart) give a shared corner texture
        // coordinates a whole repeat apart, so overlapping soot and neighbouring faces join without a seam.
        ScorchDecal.Baked other = ScorchDecal.bake(faces, 2.2, 1, 0.5, 2.5);
        int shared = 0;
        for (int c = 0; c < b.cells; c++) {
            for (int i = 0; i < 4; i++) {
                double wx = b.xyz[c * 12 + i * 3] + 0.5, wz = b.xyz[c * 12 + i * 3 + 2] + 0.5;
                for (int c2 = 0; c2 < other.cells; c2++) {
                    for (int i2 = 0; i2 < 4; i2++) {
                        double ox = other.xyz[c2 * 12 + i2 * 3] + 2.2, oz = other.xyz[c2 * 12 + i2 * 3 + 2] + 0.5;
                        if (Math.abs(ox - wx) > 1e-4 || Math.abs(oz - wz) > 1e-4) continue;
                        double du = b.uv[c * 8 + i * 2] - other.uv[c2 * 8 + i2 * 2];
                        double dv = b.uv[c * 8 + i * 2 + 1] - other.uv[c2 * 8 + i2 * 2 + 1];
                        assertEquals(Math.rint(du), du, 1e-4, "u apart by whole repeats");
                        assertEquals(Math.rint(dv), dv, 1e-4, "v apart by whole repeats");
                        shared++;
                    }
                }
            }
        }
        assertTrue(shared > 20, "shared corners " + shared);
    }

    @Test
    void flameFliesSplashesAndSmokes() {
        double[] o = {0, 10, 0}, d = {0, 0, 1}, n = {0, 0, -1};
        DragonFire.Look look = DragonFire.look(77, 5);
        // Flying out along its ray at SPEED, widening and cooling.
        DragonFire.Flame a = DragonFire.flame(o, d, 8, n, look, 2, 1), b = DragonFire.flame(o, d, 8, n, look, 5, 1);
        assertEquals(DragonFire.Flame.FLYING, a.stage());
        assertEquals(2.4, a.z(), 0.8);
        assertTrue(b.z() > a.z() && b.radius() > a.radius() && b.heat() < a.heat());
        // Past the wall it splashes on the wall's face (never through it) and then burns out.
        DragonFire.Flame c = DragonFire.flame(o, d, 8, n, look, 8 / DragonFire.SPEED + 3, 1);
        assertEquals(DragonFire.Flame.SPLASHING, c.stage());
        assertTrue(c.z() < 8, "the splash stays on the near side of the wall");
        assertNull(DragonFire.flame(o, d, 8, n, look, DragonFire.flameLife(8) + 0.1, 1));
        // With nothing to hit it thins out past the reach.
        DragonFire.Flame e = DragonFire.flame(o, d, Double.POSITIVE_INFINITY, n, look, DragonFire.MAX_REACH / DragonFire.SPEED + 1, 1);
        assertEquals(DragonFire.Flame.FADING, e.stage());
        assertNull(DragonFire.flame(o, d, Double.POSITIVE_INFINITY, n, look, DragonFire.flameLife(Double.POSITIVE_INFINITY) + 0.1, 1));
        // Its smoke starts as the fire dies and rises.
        double life = DragonFire.flameLife(8);
        DragonFire.Smoke s0 = DragonFire.smoke(o, d, 8, n, look, life, 1), s1 = DragonFire.smoke(o, d, 8, n, look, life + 30, 1);
        assertNotNull(s0);
        assertTrue(s1.y() > s0.y() + 1 && s1.radius() > s0.radius());
        assertNull(DragonFire.smoke(o, d, 8, n, look, life + DragonFire.SMOKE_TICKS + 4, 1));
    }

    @Test
    void particleCapHolds() {
        DragonFire.Budget b = new DragonFire.Budget(DragonFire.MAX_PARTICLES);
        Random r = new Random(9);
        int worst = 0, total = 0;
        // A tick's spawns of each kind (lives 44 and 80) far over the cap, for 400 ticks.
        List<long[]> made = new ArrayList<>();
        for (long t = 0; t < 400; t++) {
            int[][] want = {{7 + r.nextInt(8), 44}, {2 + r.nextInt(3), 80}, {1, 44}};
            for (int[] w : want) {
                int ok = b.allow(t, w[0], w[1]);
                assertTrue(ok >= 0 && ok <= w[0]);
                if (ok > 0) made.add(new long[]{t, ok, w[1]});
                total += ok;
            }
            // Recount independently: everything spawned that can still be alive.
            int alive = 0;
            for (long[] m : made) if (m[0] + m[2] > t) alive += (int) m[1];
            worst = Math.max(worst, alive);
            assertTrue(alive <= DragonFire.MAX_PARTICLES, "alive " + alive + " at " + t);
        }
        assertTrue(worst > DragonFire.MAX_PARTICLES * 0.9, "the budget is used: " + worst);
        assertTrue(total > 1000);
    }

    @Test
    void pythonMirrorAgrees() throws Exception {
        JsonObject all = JsonParser.parseString(Files.readString(
                Path.of("build_scripts/pensieve_scenes/dragon_fire_cases.json"))).getAsJsonObject();
        JsonObject k = all.getAsJsonObject("constants");
        assertEquals(DragonFire.SPEED, k.get("speed").getAsDouble(), 0);
        assertEquals(DragonFire.MAX_REACH, k.get("max_reach").getAsDouble(), 0);
        assertEquals(DragonFire.SCORCH_MAX, k.get("scorch_max").getAsDouble(), 0);
        assertEquals(DragonFire.SCORCH_TICKS, k.get("scorch_ticks").getAsDouble(), 0);
        assertEquals(DragonFire.EMBER_TICKS, k.get("ember_ticks").getAsDouble(), 0);
        assertEquals(DragonFire.MIN_DURATION, k.get("min_duration").getAsInt());
        assertEquals(DragonFire.MAX_DURATION, k.get("max_duration").getAsInt());
        assertEquals(DragonFire.DEFAULT_DURATION, k.get("default_duration").getAsInt());
        assertEquals(DragonFire.AIR_BREATH_OPEN, k.get("air_breath_open").getAsInt());
        for (JsonElement e : all.getAsJsonArray("front")) {
            JsonArray r = e.getAsJsonArray();
            assertEquals(r.get(1).getAsDouble(), DragonFire.front(r.get(0).getAsDouble()), 1e-4, "front " + r);
        }
        for (JsonElement e : all.getAsJsonArray("strength")) {
            JsonArray r = e.getAsJsonArray();
            assertEquals(r.get(2).getAsDouble(), DragonFire.strength(r.get(0).getAsDouble(), r.get(1).getAsDouble()), 1e-4, "strength " + r);
        }
        for (JsonElement e : all.getAsJsonArray("scorch")) {
            JsonArray r = e.getAsJsonArray();
            assertEquals(r.get(1).getAsDouble(), DragonFire.scorchRadius(r.get(0).getAsDouble()), 1e-4, "scorch " + r);
        }
        for (JsonElement e : all.getAsJsonArray("smoke")) {
            JsonArray r = e.getAsJsonArray();
            assertEquals(r.get(2).getAsDouble(), DragonFire.smokeRate(r.get(0).getAsDouble(), r.get(1).getAsDouble()), 1e-4, "smoke " + r);
        }
        for (JsonElement e : all.getAsJsonArray("radius")) {
            JsonArray r = e.getAsJsonArray();
            assertEquals(r.get(1).getAsDouble(), DragonFire.radius(r.get(0).getAsDouble()), 1e-4, "radius " + r);
            assertEquals(r.get(2).getAsDouble(), DragonFire.visibleRadius(r.get(0).getAsDouble()), 1e-4, "visible " + r);
        }
        Map<String, List<SceneDef.Cue>> anims = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : all.getAsJsonObject("anims").entrySet()) {
            List<SceneDef.Cue> cues = new ArrayList<>();
            for (JsonElement c : e.getValue().getAsJsonArray()) {
                JsonObject o = c.getAsJsonObject();
                cues.add(new SceneDef.Cue(o.get("t").getAsFloat(), o.get("name").getAsString(),
                        o.has("blend") ? o.get("blend").getAsFloat() : 0f));
            }
            anims.put(e.getKey(), cues);
        }
        int windows = 0;
        for (JsonElement e : all.getAsJsonArray("windows")) {
            JsonObject w = e.getAsJsonObject();
            double[] got = DragonFire.fireWindow(anims.get(w.get("anims").getAsString()), w.get("t").getAsDouble());
            String what = w.get("anims").getAsString() + " t " + w.get("t");
            if (w.get("window").isJsonNull()) {
                assertNull(got, what);
            } else {
                assertNotNull(got, what);
                assertEquals(w.getAsJsonArray("window").get(0).getAsDouble(), got[0], 1e-4, what);
                assertEquals(w.getAsJsonArray("window").get(1).getAsDouble(), got[1], 1e-4, what);
                windows++;
            }
        }
        assertTrue(windows >= 10, "the cases cover open windows: " + windows);
        JsonObject sk = all.getAsJsonObject("splash_constants");
        assertEquals(DragonFire.SPLASH_SPREAD, sk.get("spread").getAsDouble(), 0);
        assertEquals(DragonFire.SPLASH_RAY_LIFT, sk.get("ray_lift").getAsDouble(), 0);
        assertEquals(DragonFire.SPLASH_WALL_GAP, sk.get("wall_gap").getAsDouble(), 0);
        int splashes = 0;
        for (JsonElement e : all.getAsJsonArray("splashes")) {
            JsonObject c = e.getAsJsonObject();
            double[] d = doubles(c.get("d")), n = doubles(c.get("n"));
            double b = c.get("b").getAsDouble();
            double reach = c.get("reach").isJsonNull() ? Double.POSITIVE_INFINITY : c.get("reach").getAsDouble();
            DragonFire.Look look = new DragonFire.Look(0, 0, 0, 0, 0, 0.9, c.get("angle").getAsDouble(), 0, 0, 0, 0);
            double[] dir = DragonFire.splashDir(d, n, look), want = doubles(c.get("dir"));
            double[] o = {1.5, 6.0, -2.0};
            DragonFire.Flame f = DragonFire.flame(o, d, 7.25, n, look, 7.25 / DragonFire.SPEED + b, 1.3, reach);
            assertEquals(DragonFire.Flame.SPLASHING, f.stage(), c.toString());
            double[] centre = doubles(c.get("centre"));
            for (int i = 0; i < 3; i++) {
                assertEquals(want[i], dir[i], 1e-4, "splash dir " + c);
            }
            assertEquals(centre[0], f.x(), 1e-4, "splash x " + c);
            assertEquals(centre[1], f.y(), 1e-4, "splash y " + c);
            assertEquals(centre[2], f.z(), 1e-4, "splash z " + c);
            assertEquals(c.get("radius").getAsDouble(), f.radius(), 1e-4, "splash radius " + c);
            splashes++;
        }
        assertEquals(80, splashes);
        Map<String, SceneDef> scenes = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : all.getAsJsonObject("mouth_scenes").entrySet()) {
            scenes.put(e.getKey(), SceneParser.parse(e.getKey(), e.getValue().getAsJsonObject()));
        }
        int settled = 0;
        for (JsonElement e : all.getAsJsonArray("mouths")) {
            JsonObject c = e.getAsJsonObject();
            SceneDef.ActorDef h = scenes.get(c.get("scene").getAsString()).actors().get(0);
            double t = c.get("t").getAsDouble();
            double[] m = DragonFlight.mouthEstimate(h, t), want = doubles(c.get("mouth")), dir = doubles(c.get("dir"));
            String at = c.get("scene").getAsString() + " t " + t;
            for (int i = 0; i < 3; i++) {
                assertEquals(want[i], m[i], 1e-3, "mouth " + at);
                assertEquals(dir[i], m[3 + i], 1e-3, "mouth dir " + at);
            }
            // Wherever she can breathe, the estimate is near her drawn snout (the mirror's horntail_snout).
            if (c.get("settled").getAsBoolean()) {
                double[] sn = doubles(c.get("snout"));
                double gap = Math.sqrt(Math.pow(m[0] - sn[0], 2) + Math.pow(m[1] - sn[1], 2) + Math.pow(m[2] - sn[2], 2));
                assertTrue(gap <= 0.5, "the estimate is " + gap + " from the snout at " + at);
                settled++;
            }
        }
        assertTrue(settled >= 10, "settled mouth cases " + settled);
    }

    private static double[] doubles(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        double[] out = new double[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsDouble();
        return out;
    }

    /** Every name is accepted: the dev scene is validated for its structure and the fire rules. */
    private static final Set<String> ANY = new AbstractSet<>() {
        @Override
        public boolean contains(Object o) {
            return true;
        }

        @Override
        public Iterator<String> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public int size() {
            return 0;
        }
    };

    @Test
    void devDragonFireSceneValidates() throws Exception {
        Path f = Path.of("run/pensieve_dev/dev_dragon_fire.json");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(f),
                "run/pensieve_dev/dev_dragon_fire.json not generated (python build_scripts/pensieve_scenes/dev_dragon_fire.py)");
        SceneDef def = SceneParser.parse("dev_dragon_fire", JsonParser.parseString(Files.readString(f)).getAsJsonObject());
        SceneNames names = new SceneNames(ANY, ANY, ANY, ANY, ANY, ANY, ANY, ANY, ANY);
        assertEquals(List.of(), SceneValidator.validate(def, names));
        int breaths = 0;
        for (SceneDef.ActorDef a : def.actors()) {
            for (SceneDef.ActionCue c : a.actions()) if (DragonFire.ACTION.equals(c.action())) breaths++;
        }
        assertTrue(breaths >= 2, "dev_dragon_fire breathes on the ground and in the air");
    }

    private static double[] norm(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }
}
