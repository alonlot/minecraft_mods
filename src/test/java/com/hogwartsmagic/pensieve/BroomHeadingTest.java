package com.hogwartsmagic.pensieve;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.BroomFlight;
import com.hogwartsmagic.pensieve.scene.Ease;
import com.hogwartsmagic.pensieve.scene.Keyframe;
import com.hogwartsmagic.pensieve.scene.Track;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BroomFlight#heldHeading}: the opt-in heading that holds through a hover. The tracks are heading_cases.json's
 * (written by {@code python -I scene_engine.py --write-heading-cases}): the First Task fire review's two probes (a dead
 * hover, then a departure whose spline backs up 0.375 blocks first; arriving north, hovering, leaving west), a
 * straight climb, and the broom cases' straight and turning flights.
 */
class BroomHeadingTest {

    private static JsonObject cases() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("build_scripts/pensieve_scenes/heading_cases.json")))
                .getAsJsonObject();
    }

    private static Map<String, Track> tracks(JsonObject all) {
        Map<String, Track> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : all.getAsJsonObject("tracks").entrySet()) {
            List<Keyframe> keys = new ArrayList<>();
            for (JsonElement ke : e.getValue().getAsJsonArray()) {
                JsonObject k = ke.getAsJsonObject();
                JsonArray p = k.getAsJsonArray("pos");
                Ease ease = Ease.parse(k.has("ease") ? k.get("ease").getAsString() : null);
                keys.add(new Keyframe(k.get("t").getAsFloat(),
                        new float[]{p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat()}, ease));
            }
            out.put(e.getKey(), new Track(keys));
        }
        return out;
    }

    private static double wrap(double d) {
        return ((d % 360) + 540) % 360 - 180;
    }

    @Test
    void pythonMirrorAgrees() throws Exception {
        JsonObject all = cases();
        JsonObject k = all.getAsJsonObject("constants");
        assertEquals(BroomFlight.HEADING_HOLD_SPEED, k.get("HEADING_HOLD_SPEED").getAsDouble(), 0);
        assertEquals(BroomFlight.HEADING_FULL_SPEED, k.get("HEADING_FULL_SPEED").getAsDouble(), 0);
        assertEquals(BroomFlight.REVERSE_CREEP, k.get("REVERSE_CREEP").getAsDouble(), 0);
        assertEquals(BroomFlight.HEADING_SEARCH, k.get("HEADING_SEARCH").getAsInt());
        Map<String, Track> tracks = tracks(all);
        int n = 0;
        for (JsonElement ce : all.getAsJsonArray("cases")) {
            JsonObject c = ce.getAsJsonObject();
            Track tr = tracks.get(c.get("track").getAsString());
            double t = c.get("t").getAsDouble();
            String at = c.get("track").getAsString() + " t " + t;
            assertEquals(0, wrap(c.get("held").getAsDouble() - BroomFlight.heldHeading(tr, t)), 0.05, at + " held");
            assertEquals(0, wrap(c.get("heading").getAsDouble() - BroomFlight.heading(tr, t)), 0.05, at + " heading");
            double[] v = BroomFlight.firmVelocity(tr, t);
            assertEquals(c.getAsJsonArray("firm").get(1).getAsDouble(), v[1], 1e-4, at + " firm speed");
            n++;
        }
        assertTrue(n > 300, "cases: " + n);
    }

    /** The fire review's case 1: a dead hover then a departure that backs up first: held north (0) throughout. */
    @Test
    void heldHeadingIgnoresAnOvershoot() throws Exception {
        Track tr = tracks(cases()).get("overshoot");
        for (double t = 0; t <= 140; t += 0.25) {
            assertEquals(0, wrap(BroomFlight.heldHeading(tr, t)), 2, "t " + t);
        }
        // the old heading faces backwards through the hover: the bug this guards against
        assertEquals(180, Math.abs(wrap(BroomFlight.heading(tr, 70))), 1);
    }

    /** Case 2: north through the hover, then it turns to west (90) as it leaves, with no snap. */
    @Test
    void heldHeadingHoldsThroughAHover() throws Exception {
        Track tr = tracks(cases()).get("north_then_west");
        for (double t = 41; t <= 120; t += 0.25) assertEquals(0, wrap(BroomFlight.heldHeading(tr, t)), 1, "t " + t);
        for (double t = 135; t <= 175; t += 0.25) assertEquals(90, BroomFlight.heldHeading(tr, t), 1, "t " + t);
        double prev = BroomFlight.heldHeading(tr, 0);
        for (double t = 0.05; t <= 183; t += 0.05) {
            double h = BroomFlight.heldHeading(tr, t);
            assertTrue(Math.abs(wrap(h - prev)) < 3, "a step of " + wrap(h - prev) + " at t " + t);
            prev = h;
        }
    }

    /** Wherever the firm speed is full, it is exactly heading. */
    @Test
    void heldHeadingIsHeadingInFlight() throws Exception {
        int n = 0;
        for (Track tr : tracks(cases()).values()) {
            float end = tr.keys().get(tr.keys().size() - 1).t();
            for (double t = 0; t <= end; t += 0.5) {
                if (BroomFlight.firmVelocity(tr, t)[1] < BroomFlight.HEADING_FULL_SPEED) continue;
                assertEquals(BroomFlight.heading(tr, t), BroomFlight.heldHeading(tr, t), 1e-4, "t " + t);
                n++;
            }
        }
        assertTrue(n > 200, "in flight: " + n);
    }

    /** Continuity on the flights that are not hovers: no step over 1.5 degrees in 0.05 ticks. */
    @Test
    void heldHeadingIsContinuousInFlight() throws Exception {
        Map<String, Track> tracks = tracks(cases());
        for (String name : List.of("straight", "turning", "overshoot")) {
            Track tr = tracks.get(name);
            double prev = BroomFlight.heldHeading(tr, 0);
            float end = tr.keys().get(tr.keys().size() - 1).t();
            for (double t = 0.05; t <= end; t += 0.05) {
                double h = BroomFlight.heldHeading(tr, t);
                assertTrue(Math.abs(wrap(h - prev)) < 1.5, name + ": a step of " + wrap(h - prev) + " at t " + t);
                prev = h;
            }
        }
    }
}
