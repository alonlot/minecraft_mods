package com.hogwartsmagic.pensieve;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.GoldenEgg;
import com.hogwartsmagic.pensieve.scene.HumanMotion;
import com.hogwartsmagic.pensieve.scene.MemoryBall;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The golden egg actor: its size and shape, the glint's rhythm, the held item's size, and the Python mirror. */
class GoldenEggTest {
    private static final String[] IDS = {"egg", "golden_egg", "prize", "a", "b"};

    @Test
    void boxIsTheEggAtAnyScale() {
        double[] b = GoldenEgg.box(0.85f);
        assertEquals(-0.26 / 2 * 0.85, b[0], 1e-6);
        assertEquals(-0.36 / 2 * 0.85, b[1], 1e-6);
        assertEquals(0.26 / 2 * 0.85, b[3], 1e-6);
        assertEquals(0.36 / 2 * 0.85, b[4], 1e-6);
        assertEquals(b[0], b[2], 0);
        assertEquals(b[3], b[5], 0);
        assertTrue(GoldenEgg.isEgg(SceneNames.GOLDEN_EGG_ACTOR));
        assertFalse(SceneNames.BALLS.contains(SceneNames.GOLDEN_EGG_ACTOR), "the egg is no ball: it does not roll");
        assertFalse(SceneNames.ROLL_TYPES.contains(SceneNames.GOLDEN_EGG_ACTOR), "and it takes no roll");
    }

    @Test
    void shapeIsAnEggWithItsWidestPointExactlyTheWidth() {
        double widest = 0;
        for (int i = 0; i <= 2000; i++) widest = Math.max(widest, GoldenEgg.radiusAt(-1 + i / 1000.0));
        assertEquals(GoldenEgg.WIDTH / 2, widest, 1e-9);
        assertEquals(0, GoldenEgg.radiusAt(1), 1e-12);
        assertEquals(0, GoldenEgg.radiusAt(-1), 1e-12);
        assertTrue(GoldenEgg.radiusAt(-0.5) > GoldenEgg.radiusAt(0.5), "broad below, narrower toward the top");
    }

    @Test
    void glintPeriod() {
        // A flash every 1.5 to 2.5 s (30 to 50 ticks): consecutive starts are never closer or farther, for any seed.
        for (String id : IDS) {
            int seed = MemoryBall.seedFor(id);
            double prev = GoldenEgg.glintStart(seed, 0), sum = 0;
            double shortest = 1e9, longest = 0;
            int n = 4000;
            for (long k = 1; k <= n; k++) {
                double s = GoldenEgg.glintStart(seed, k);
                double gap = s - prev;
                shortest = Math.min(shortest, gap);
                longest = Math.max(longest, gap);
                assertTrue(gap >= 30 - 1e-9 && gap <= 50 + 1e-9, id + ": gap " + gap + " ticks at flash " + k);
                sum += gap;
                prev = s;
            }
            assertEquals(40, sum / n, 0.5, id + ": the mean gap is a slot");
            assertTrue(shortest < 33 && longest > 47, id + ": the gaps use the range, " + shortest + " to " + longest);
        }
    }

    @Test
    void glintIsAShortFlashAtTheStartOnly() {
        for (String id : IDS) {
            int seed = MemoryBall.seedFor(id);
            for (long k = 0; k < 300; k++) {
                double start = GoldenEgg.glintStart(seed, k);
                assertEquals(0, GoldenEgg.glint(start, seed), 1e-12, "it starts at zero");
                assertEquals(1, GoldenEgg.glint(start + GoldenEgg.FLASH / 2, seed), 1e-12, "full mid-flash");
                assertEquals(0, GoldenEgg.glint(start + GoldenEgg.FLASH, seed), 1e-12, "and is gone after FLASH ticks");
                assertTrue(GoldenEgg.glint(start + 1, seed) > 0 && GoldenEgg.glint(start + 7, seed) > 0);
                assertEquals(0, GoldenEgg.glint(start - 0.5, seed), 0, "nothing before");
                assertEquals(0, GoldenEgg.glint(start + GoldenEgg.FLASH + 0.5, seed), 0, "nothing after");
            }
            // Scanned at quarter ticks: it is lit for 8 ticks in every slot, and never outside a flash.
            double lit = 0;
            for (double t = 0; t < 4000; t += 0.25) {
                double g = GoldenEgg.glint(t, seed);
                assertTrue(g >= 0 && g <= 1);
                if (g > 0) lit += 0.25;
            }
            assertEquals(100 * GoldenEgg.FLASH, lit, 2.0, id + ": lit 8 of every 40 ticks (flash starts every slot)");
        }
    }

    @Test
    void glintIsPureAndPerEgg() {
        int a = MemoryBall.seedFor("egg"), b = MemoryBall.seedFor("prize");
        // Late joiners ask in any order and get the same: no state.
        double[] times = {900.5, 12.25, 3000.75, 12.25, 77.5};
        double[] first = new double[times.length];
        for (int i = 0; i < times.length; i++) first[i] = GoldenEgg.glint(times[i], a);
        for (int i = times.length - 1; i >= 0; i--) assertEquals(first[i], GoldenEgg.glint(times[i], a), 0);
        assertEquals(GoldenEgg.glint(500, "egg"), GoldenEgg.glint(500, a), 0);
        // Two eggs do not flash in unison.
        int different = 0;
        for (long k = 0; k < 50; k++) if (GoldenEgg.glintStart(a, k) - 40 * k != GoldenEgg.glintStart(b, k) - 40 * k) different++;
        assertTrue(different > 40);
        assertNotEquals(0, MemoryBall.seedFor("egg") - MemoryBall.seedFor("prize"));
    }

    // ---- the held item ----------------------------------------------------------------------------------------------

    /** Extents of the item model's elements, {x, y, z}, in model units (boxes turned about y included). */
    private static double[] extents(JsonObject model) {
        double[] lo = {1e9, 1e9, 1e9}, hi = {-1e9, -1e9, -1e9};
        for (JsonElement e : model.getAsJsonArray("elements")) {
            JsonObject el = e.getAsJsonObject();
            JsonArray from = el.getAsJsonArray("from"), to = el.getAsJsonArray("to");
            double deg = 0;
            if (el.has("rotation")) {
                JsonObject rot = el.getAsJsonObject("rotation");
                assertEquals("y", rot.get("axis").getAsString(), "the egg's slices turn only about y");
                deg = rot.get("angle").getAsDouble();
            }
            double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
            for (int i = 0; i < 8; i++) {
                double[] p = new double[3];
                for (int k = 0; k < 3; k++) {
                    p[k] = ((i >> k & 1) == 0 ? from.get(k).getAsDouble() : to.get(k).getAsDouble()) - 8;
                }
                double[] q = {p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c};
                for (int k = 0; k < 3; k++) {
                    lo[k] = Math.min(lo[k], q[k]);
                    hi[k] = Math.max(hi[k], q[k]);
                }
            }
        }
        return new double[]{hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]};
    }

    @Test
    void itemMatchesTheActorSize() throws Exception {
        // The held egg is a stack of slices whose in-hand size (model extent x display scale) is the actor's 0.36 tall
        // and 0.26 across, in third and first person, both hands, so the swap at the grab does not pop. The held item
        // is drawn inside the holder's scale (the PoseStack), so give the egg actor its holder's scale (0.85 for the
        // champion).
        JsonObject model = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/hogwartsmagic/models/item/golden_egg_prop.json"))).getAsJsonObject();
        double[] ext = extents(model);
        JsonObject display = model.getAsJsonObject("display");
        for (String slot : new String[]{"thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand",
                "firstperson_lefthand"}) {
            JsonArray scale = display.getAsJsonObject(slot).getAsJsonArray("scale");
            // Third person draws the item inside the player model, which is scaled by 15/16; first person does not.
            double d = scale.get(0).getAsDouble() * (slot.startsWith("third") ? HumanMotion.MODEL_SCALE : 1.0);
            assertEquals(scale.get(0).getAsDouble(), scale.get(1).getAsDouble(), 0, slot);
            assertEquals(scale.get(0).getAsDouble(), scale.get(2).getAsDouble(), 0, slot);
            assertEquals(GoldenEgg.LENGTH, ext[1] / 16.0 * d, 0.002, slot + ": the height");
            assertEquals(GoldenEgg.WIDTH, ext[0] / 16.0 * d, 0.002, slot + ": the width (x)");
            assertEquals(GoldenEgg.WIDTH, ext[2] / 16.0 * d, 0.002, slot + ": the width (z)");
        }
        // And its slices and textures exist.
        assertTrue(model.getAsJsonArray("elements").size() >= 48, "fine slices, not an octagram of blunt steps");
        for (String f : new String[]{"textures/item/golden_egg_prop.png", "textures/entity/memory_golden_egg.png",
                "textures/entity/golden_egg_glint.png"}) {
            assertTrue(Files.exists(Path.of("src/main/resources/assets/hogwartsmagic/" + f)), f);
        }
    }

    // ---- the Python mirror ------------------------------------------------------------------------------------------

    @Test
    void pythonMirrorAgrees() throws Exception {
        JsonObject all = JsonParser.parseString(Files.readString(
                Path.of("build_scripts/pensieve_scenes/egg_cases.json"))).getAsJsonObject();
        JsonObject k = all.getAsJsonObject("constants");
        assertEquals(GoldenEgg.LENGTH, k.get("LENGTH").getAsDouble(), 0);
        assertEquals(GoldenEgg.WIDTH, k.get("WIDTH").getAsDouble(), 0);
        assertEquals(GoldenEgg.TAPER, k.get("TAPER").getAsDouble(), 0);
        assertEquals(GoldenEgg.SLOT, k.get("SLOT").getAsDouble(), 0);
        assertEquals(GoldenEgg.JITTER, k.get("JITTER").getAsDouble(), 0);
        assertEquals(GoldenEgg.FLASH, k.get("FLASH").getAsDouble(), 0);
        for (JsonElement e : all.getAsJsonArray("boxes")) {
            JsonObject b = e.getAsJsonObject();
            double[] box = GoldenEgg.box(b.get("scale").getAsFloat());
            for (int i = 0; i < 3; i++) {
                assertEquals(b.getAsJsonArray("lo").get(i).getAsDouble(), box[i], 1e-6);   // the scale is a float
                assertEquals(b.getAsJsonArray("hi").get(i).getAsDouble(), box[3 + i], 1e-6);
            }
        }
        for (JsonElement e : all.getAsJsonArray("radii")) {
            JsonObject r = e.getAsJsonObject();
            assertEquals(r.get("r").getAsDouble(), GoldenEgg.radiusAt(r.get("v").getAsDouble()), 1e-9);
        }
        int lit = 0;
        for (JsonElement e : all.getAsJsonArray("glints")) {
            JsonObject g = e.getAsJsonObject();
            String id = g.get("id").getAsString();
            int seed = g.get("seed").getAsInt();
            assertEquals(MemoryBall.seedFor(id), seed, id + ": the seed");
            double t = g.get("t").getAsDouble();
            assertEquals(g.get("glint").getAsDouble(), GoldenEgg.glint(t, seed), 1e-9, id + " t " + t);
            if (g.get("glint").getAsDouble() > 0) lit++;
        }
        assertTrue(lit > 80, "the cases cover lit moments: " + lit);
    }
}
