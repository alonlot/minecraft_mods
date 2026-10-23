package com.hogwartsmagic.pensieve;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.DragonFire;
import com.hogwartsmagic.pensieve.scene.EffectSeed;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.hogwartsmagic.pensieve.scene.SceneValidator;
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
    void deterministicAcrossOrigins() {
        // The seed is the breathe's scene-relative time and place: the session slot's origin never enters it.
        float[] root = {3.5f, 0f, -12f};
        long a = DragonFire.seed("horntail", 120f, root);
        assertEquals(a, DragonFire.seed("horntail", 120f, root.clone()));
        assertTrue(a != DragonFire.seed("horntail2", 120f, root), "two dragons breathing at once differ");
        assertTrue(a != DragonFire.seed("horntail", 121f, root), "two breaths differ");
        // EffectSeed of the same relative point from two slot origins is the same.
        assertEquals(EffectSeed.of(1003.5, 64, 988, 1000, 64, 1000, 120f), EffectSeed.of(-4996.5, 64, 4988, -5000, 64, 5000, 120f));
        // Every puff, flame and smoke is the same about two origins, shifted by the origin.
        double[] o1 = {1000.5, 70, 1000.5}, o2 = {-30000.5, 70, 52000.5};
        double[] d = norm(new double[]{0.3, -0.4, 1});
        double[] n = {0, 1, 0};
        for (int k = 0; k < DragonFire.puffs(40); k++) {
            DragonFire.Look l1 = DragonFire.look(a, k), l2 = DragonFire.look(a, k);
            assertEquals(l1, l2);
            for (double age : new double[]{0.3, 4, 9.5, 14, 20, 45}) {
                for (double hit : new double[]{7.5, Double.POSITIVE_INFINITY}) {
                    DragonFire.Flame f1 = DragonFire.flame(o1, d, hit, n, l1, age, 1);
                    DragonFire.Flame f2 = DragonFire.flame(o2, d, hit, n, l2, age, 1);
                    assertEquals(f1 == null, f2 == null);
                    if (f1 != null) {
                        assertEquals(f1.x() - o1[0], f2.x() - o2[0], 1e-6);
                        assertEquals(f1.y() - o1[1], f2.y() - o2[1], 1e-6);
                        assertEquals(f1.z() - o1[2], f2.z() - o2[2], 1e-6);
                        assertEquals(f1.radius(), f2.radius(), 0);
                        assertEquals(f1.heat(), f2.heat(), 0);
                    }
                    DragonFire.Smoke s1 = DragonFire.smoke(o1, d, hit, n, l1, age, 1);
                    DragonFire.Smoke s2 = DragonFire.smoke(o2, d, hit, n, l2, age, 1);
                    assertEquals(s1 == null, s2 == null);
                    if (s1 != null) assertEquals(s1.y() - o1[1], s2.y() - o2[1], 1e-6);
                }
            }
        }
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
