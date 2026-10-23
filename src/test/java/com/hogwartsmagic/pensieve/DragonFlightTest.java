package com.hogwartsmagic.pensieve;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.DragonFlight;
import com.hogwartsmagic.pensieve.scene.Ease;
import com.hogwartsmagic.pensieve.scene.HorntailCues;
import com.hogwartsmagic.pensieve.scene.Keyframe;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.hogwartsmagic.pensieve.scene.SceneValidator;
import com.hogwartsmagic.pensieve.scene.Track;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonFlightTest {
    /** A circle of radius r at speed v (blocks per tick), turning right (yaw increasing) when right. */
    private static Track circle(double r, double v, boolean right) {
        List<Keyframe> keys = new ArrayList<>();
        double omega = v / r;
        for (int i = 0; i <= 120; i++) {
            double th = omega * i * 2;
            double x = (right ? -1 : 1) * r * (1 - Math.cos(th));
            double z = r * Math.sin(th);
            keys.add(new Keyframe(i * 2, new float[]{(float) x, 20, (float) z}, i == 0 ? Ease.IN_OUT : Ease.SPLINE));
        }
        return new Track(keys);
    }

    private static Track line(double vx, double vy, double vz, int ticks) {
        List<Keyframe> keys = new ArrayList<>();
        for (int i = 0; i <= ticks; i += 5) {
            keys.add(new Keyframe(i, new float[]{(float) (vx * i), (float) (40 + vy * i), (float) (vz * i)},
                    i == 0 ? Ease.IN_OUT : Ease.SPLINE));
        }
        return new Track(keys);
    }

    @Test
    void beatRateFollowsSpeed() {
        double hover = DragonFlight.beatRate(0, 0), cruise = DragonFlight.beatRate(0.8, 0);
        double glide = DragonFlight.beatRate(1.0, -0.3);
        assertTrue(hover > cruise && cruise > glide, hover + " " + cruise + " " + glide);
        assertEquals(0.24, hover, 1e-12);
        assertEquals(0.14, cruise, 1e-12);
        assertEquals(0, glide, 1e-12);
        // Climbing beats harder: x 1.35 at 0.3 blocks a tick up.
        assertEquals(0.24 * 1.35, DragonFlight.beatRate(0, 0.3), 1e-12);
        assertEquals(0.14 * 1.35, DragonFlight.beatRate(0.8, 0.5), 1e-12);
        // A hover stroke lasts about 1.3 s, a cruise stroke about 2.2 s: heavy, for her size.
        assertEquals(1.3, 2 * Math.PI / hover / 20, 0.05);
        assertEquals(2.24, 2 * Math.PI / cruise / 20, 0.05);
        // Amplitudes: hover or climb 0.70, cruise 0.60, glide 0.15.
        assertEquals(0.70, DragonFlight.wingAmp(0, 0), 1e-12);
        assertEquals(0.60, DragonFlight.wingAmp(0.8, 0), 1e-12);
        assertEquals(0.70, DragonFlight.wingAmp(0.8, 0.3), 1e-12);
        assertEquals(0.15, DragonFlight.wingAmp(1.0, -0.3), 1e-12);
    }

    @Test
    void beatRateIsContinuous() {
        double step = 0.002, worst = 0, worstAmp = 0;
        for (double s = 0; s <= 1.4; s += step) {
            for (double c : new double[]{-0.4, -0.15, -0.1, 0, 0.1, 0.3, 0.6}) {
                worst = Math.max(worst, Math.abs(DragonFlight.beatRate(s + step, c) - DragonFlight.beatRate(s, c)));
                worstAmp = Math.max(worstAmp, Math.abs(DragonFlight.wingAmp(s + step, c) - DragonFlight.wingAmp(s, c)));
            }
        }
        for (double c = -0.6; c <= 0.6; c += step) {
            for (double s : new double[]{0, 0.3, 0.8, 0.85, 0.9, 1.2}) {
                worst = Math.max(worst, Math.abs(DragonFlight.beatRate(s, c + step) - DragonFlight.beatRate(s, c)));
                worstAmp = Math.max(worstAmp, Math.abs(DragonFlight.wingAmp(s, c + step) - DragonFlight.wingAmp(s, c)));
            }
        }
        assertTrue(worst <= 0.01, "the rate steps by " + worst + " rad/tick");
        assertTrue(worstAmp <= 0.03, "the amplitude steps by " + worstAmp);
    }

    @Test
    void wingPhaseIsPureOfTheClock() {
        Track path = circle(18, 0.6, true);
        // The same phase whichever way it is asked for: from scratch, in order, backwards, from a late start.
        DragonFlight.WingClock forward = new DragonFlight.WingClock(path), late = new DragonFlight.WingClock(path);
        double[] want = new double[240];
        for (int i = 0; i < want.length; i++) want[i] = forward.phase(i * 0.75);
        for (int i = want.length - 1; i >= 0; i -= 7) {
            assertEquals(want[i], DragonFlight.wingPhase(path, i * 0.75), 1e-9, "fresh at " + i * 0.75);
            assertEquals(want[i], late.phase(i * 0.75), 1e-9, "late joiner at " + i * 0.75);
        }
        // Continuous across whole ticks, and increasing at the rate.
        for (int k = 1; k < 170; k++) {
            double before = forward.phase(k - 1e-6), at = forward.phase(k);
            assertEquals(before, at, 1e-5, "jump at tick " + k);
            assertEquals(DragonFlight.rateAt(path, k), 2 * (forward.phase(k + 0.5) - forward.phase(k)), 1e-9);
        }
    }

    @Test
    void strokeIsQuickDownAndSmooth() {
        double d = DragonFlight.DOWNSTROKE_SHARE;
        // The top stays at -pi/2, the bottom comes DOWNSTROKE_SHARE of a beat later, mid-downstroke halfway there.
        assertEquals(-Math.PI / 2, DragonFlight.strokeAngle(-Math.PI / 2), 1e-12);
        assertEquals(Math.PI / 2, DragonFlight.strokeAngle(-Math.PI / 2 + 2 * Math.PI * d), 1e-12);
        assertEquals(0, DragonFlight.strokeAngle(DragonFlight.MID_DOWNSTROKE), 1e-12);
        assertEquals(4 * Math.PI, DragonFlight.strokeAngle(4 * Math.PI + DragonFlight.MID_DOWNSTROKE), 1e-9);
        // Monotone, and the beat is smooth: no step in its value or its slope.
        double step = 1e-3, last = DragonFlight.strokeAngle(-10), lastSlope = Double.NaN, worstKink = 0;
        for (double ph = -10 + step; ph < 10; ph += step) {
            double a = DragonFlight.strokeAngle(ph);
            assertTrue(a > last, "the stroke angle runs backwards at " + ph);
            last = a;
            double slope = (DragonFlight.boneBeat(ph, 0) - DragonFlight.boneBeat(ph - step, 0)) / step;
            if (!Double.isNaN(lastSlope)) worstKink = Math.max(worstKink, Math.abs(slope - lastSlope));
            lastSlope = slope;
        }
        assertTrue(worstKink < 0.01, "the beat kinks by " + worstKink);
        // The outer bones trail the humerus.
        for (double ph = 0; ph < 7; ph += 0.3) {
            assertEquals(DragonFlight.boneBeat(ph - DragonFlight.FOREARM_LAG, 0), DragonFlight.boneBeat(ph, 1), 1e-12);
            assertEquals(DragonFlight.boneBeat(ph - DragonFlight.FINGER_LAG, 0), DragonFlight.boneBeat(ph, 2), 1e-12);
        }
    }

    @Test
    void headLookIsClampedAndNeckCountersTheRoll() {
        Track rot = new Track(List.of(new Keyframe(0, new float[]{10, -20}, Ease.IN_OUT),
                new Keyframe(10, new float[]{80, 100}, Ease.IN_OUT)));
        float[] at0 = DragonFlight.headLook(rot, 0);
        assertEquals(-20, at0[0], 1e-6);
        assertEquals(10, at0[1], 1e-6);
        float[] at10 = DragonFlight.headLook(rot, 10);
        assertEquals(DragonFlight.HEAD_YAW_LIMIT, at10[0], 1e-6);
        assertEquals(DragonFlight.HEAD_PITCH_LIMIT, at10[1], 1e-6);
        assertArrayEquals(new float[]{0, 0}, DragonFlight.headLook(Track.EMPTY, 5), 0f);
        assertEquals(-Math.toRadians(30), DragonFlight.neckRoll(30), 1e-6);
        assertEquals(Math.toRadians(DragonFlight.MAX_BANK), DragonFlight.neckRoll(-170), 1e-6);
    }

    @Test
    void downstrokesAreMidDownstroke() {
        Track path = line(0, 0.1, 0.5, 300);
        int[] d = DragonFlight.downstrokes(path, 0, 300);
        assertTrue(d.length > 8, "expected a stroke every 30 ticks or so, got " + d.length);
        DragonFlight.WingClock clock = new DragonFlight.WingClock(path);
        for (int tick : d) {
            // The nearest tick to the moment the stroke angle is 0 (the beat rising through 0): within half a tick's
            // turn of it, which mid-downstroke is the rate / (2 DOWNSTROKE_SHARE).
            double half = DragonFlight.rateAt(path, tick) / (4 * DragonFlight.DOWNSTROKE_SHARE) + 1e-9;
            double ang = DragonFlight.strokeAngle(clock.phase(tick));
            assertTrue(Math.cos(ang) >= Math.cos(half) - 1e-6, "tick " + tick + " stroke angle " + ang);
            // ... and on the way down: the humerus beat rising.
            assertTrue(DragonFlight.boneBeat(clock.phase(tick) + 0.05, 0) > DragonFlight.boneBeat(clock.phase(tick) - 0.05, 0));
        }
        for (int i = 1; i < d.length; i++) {
            int gap = d[i] - d[i - 1];
            double period = 2 * Math.PI / DragonFlight.rateAt(path, d[i]);
            assertTrue(Math.abs(gap - period) <= 2, "gap " + gap + " vs period " + period);
        }
        // A glide has none; on the ground (IDLE) none counts.
        assertEquals(0, DragonFlight.downstrokes(line(0, -0.3, 1.0, 200), 20, 180).length);
        List<SceneDef.Cue> ground = List.of(new SceneDef.Cue(0, "IDLE", 0));
        assertEquals(0, DragonFlight.downstrokes(path, ground, 0, 300).length);
        List<SceneDef.Cue> flying = List.of(new SceneDef.Cue(0, "FLY", 0), new SceneDef.Cue(100, "DIVE", 0));
        int[] f = DragonFlight.downstrokes(path, flying, 0, 300);
        for (int tick : f) assertTrue(tick < 100 || tick >= 144, "a dive holds the wings, yet a stroke at " + tick);
        assertTrue(f.length > 3);
    }

    @Test
    void bankFollowsTurn() {
        Track right = circle(12, 0.5, true), left = circle(12, 0.5, false);
        double r = DragonFlight.bank(right, 100), l = DragonFlight.bank(left, 100);
        assertTrue(r > 10, "a right turn banks right side down: " + r);
        assertEquals(-r, l, 1e-3);
        // 1000 x v^2 / r, under the 50 limit.
        assertEquals(50 * Math.tanh(1000 * 0.5 * 0.5 / 12 / 50), r, 1.5);
        // A hard turn saturates softly below 50.
        double hard = DragonFlight.bank(circle(3, 0.9, true), 60);
        assertTrue(hard > 45 && hard <= 50, "" + hard);
        // Straight flight is level.
        assertEquals(0, DragonFlight.bank(line(0, 0, 0.8, 200), 100), 1e-6);
        // The climb pitch is clamped to 45.
        assertEquals(-45, DragonFlight.climbPitch(line(0, 0.9, 0.2, 200), 100), 1e-6);
    }

    @Test
    void bankIsContinuous() {
        // Slowing from a turn to a hover and back: the bank never steps.
        List<Keyframe> keys = new ArrayList<>();
        double x = 0, z = 0, heading = 0;
        for (int i = 0; i <= 200; i++) {
            double v = 0.6 * Math.abs(Math.cos(i * Math.PI / 100));
            heading += v / 10;
            x += v * Math.sin(heading);
            z += v * Math.cos(heading);
            if (i % 4 == 0) keys.add(new Keyframe(i, new float[]{(float) x, 10, (float) z}, i == 0 ? Ease.IN_OUT : Ease.SPLINE));
        }
        Track path = new Track(keys);
        double worst = 0;
        for (double t = 5; t < 195; t += 0.25) {
            worst = Math.max(worst, Math.abs(DragonFlight.bank(path, t + 0.25) - DragonFlight.bank(path, t)));
        }
        assertTrue(worst < 2.5, "the bank steps by " + worst + " degrees in a quarter tick");
        assertEquals(0, DragonFlight.bank(new Track(List.of(new Keyframe(0, new float[]{0, 0, 0}, Ease.IN_OUT),
                new Keyframe(10, new float[]{0, 0, 0}, Ease.IN_OUT))), 5), 1e-9);
    }

    @Test
    void wholeBodyTurnsAboutThePivot() {
        SceneDef.ActorDef a = actor("{\"id\":\"h\",\"type\":\"hogwartsmagic:memory_horntail\",\"path\":[{\"t\":0,"
                + "\"pos\":[1,2,3]}],\"look\":[{\"t\":0,\"yaw\":30,\"pitch\":40,\"roll\":25}]}");
        DragonFlight.Pose p = DragonFlight.place(a, 0);
        assertEquals(30, p.yaw(), 1e-6);
        assertEquals(40, p.pitch(), 1e-6);
        assertEquals(25, p.roll(), 1e-6);
        // The pivot does not move; the root swings about it.
        double[] pivot = DragonFlight.toWorld(p, 2, new double[]{0, DragonFlight.PIVOT_Y, 0});
        assertArrayEquals(new double[]{1, 2 + 2 * DragonFlight.PIVOT_Y, 3}, pivot, 1e-9);
        assertEquals(1.078125, DragonFlight.PIVOT_Y, 1e-12);
        // Nose down: a point ahead of the pivot drops.
        DragonFlight.Pose level = DragonFlight.place(actor("{\"id\":\"h\",\"type\":\"hogwartsmagic:memory_horntail\","
                + "\"look\":[{\"t\":0,\"yaw\":0,\"pitch\":30}]}"), 0);
        double[] nose = DragonFlight.toWorld(level, 1, new double[]{0, DragonFlight.PIVOT_Y, 4});
        assertEquals(DragonFlight.PIVOT_Y - 4 * Math.sin(Math.toRadians(30)), nose[1], 1e-6);
        assertEquals(4 * Math.cos(Math.toRadians(30)), nose[2], 1e-6);
    }

    private static SceneDef.ActorDef actor(String json) {
        try {
            return SceneParser.parse("t", JsonParser.parseString("{\"length\":100,\"actors\":[" + json + "]}")
                    .getAsJsonObject()).actors().get(0);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static double[] doubles(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        double[] out = new double[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsDouble();
        return out;
    }

    private static void close(double want, double got, double tol, String what) {
        assertTrue(Math.abs(want - got) <= tol, what + ": python " + want + ", java " + got);
    }

    /** The fire review's probes (dragon_engine.heading_hold_check): her heading holds through a dead hover. */
    @Test
    void holdsHerHeadingThroughAHover() throws Exception {
        // A dead hover, then a spline departure that speeds up: its start runs 0.375 blocks backwards.
        SceneDef.ActorDef depart = dragon("[{\"t\":0,\"pos\":[0,10,0]},{\"t\":80,\"pos\":[0,10,0],\"ease\":\"in_out\"},"
                + "{\"t\":100,\"pos\":[0,10,2],\"ease\":\"spline\"},{\"t\":120,\"pos\":[0,10,22],\"ease\":\"spline\"},"
                + "{\"t\":140,\"pos\":[0,10,42],\"ease\":\"spline\"}]");
        double last = 0;
        for (int i = 0; i < 280; i++) {
            double yaw = DragonFlight.place(depart, i / 2.0).yaw();
            assertTrue(Math.abs(wrap(yaw)) <= 2, "she turns round at t " + i / 2.0 + ": " + yaw);
            assertTrue(Math.abs(wrap(yaw - last)) <= 3, "a step at t " + i / 2.0);
            last = yaw;
        }
        // Arriving heading south (+z), hovering 80 ticks, leaving west (-x): the arrival heading until she leaves.
        SceneDef.ActorDef turn = dragon("[{\"t\":0,\"pos\":[0,10,-40]},{\"t\":20,\"pos\":[0,10,-20],\"ease\":\"spline\"},"
                + "{\"t\":40,\"pos\":[0,10,0],\"ease\":\"in_out\"},{\"t\":120,\"pos\":[0,10,0],\"ease\":\"in_out\"},"
                + "{\"t\":140,\"pos\":[-2,10,0],\"ease\":\"spline\"},{\"t\":160,\"pos\":[-22,10,0],\"ease\":\"spline\"},"
                + "{\"t\":180,\"pos\":[-42,10,0],\"ease\":\"spline\"}]");
        for (int t = 20; t <= 120; t++) {
            assertTrue(Math.abs(wrap(DragonFlight.place(turn, t).yaw())) <= 2, "hovering at t " + t);
        }
        assertEquals(90, DragonFlight.place(turn, 170).yaw(), 2, "leaving west");
    }

    private static SceneDef.ActorDef dragon(String path) throws Exception {
        String json = "{\"length\":200,\"actors\":[{\"id\":\"h\",\"type\":\"hogwartsmagic:memory_horntail\","
                + "\"anims\":[{\"t\":0,\"name\":\"FLY\"}],\"path\":" + path + "}]}";
        return SceneParser.parse("probe", JsonParser.parseString(json).getAsJsonObject()).actors().get(0);
    }

    private static double wrap(double deg) {
        return ((deg % 360) + 540) % 360 - 180;
    }

    @Test
    void pythonMirrorAgrees() throws Exception {
        JsonObject all = JsonParser.parseString(Files.readString(
                Path.of("build_scripts/pensieve_scenes/horntail_cases.json"))).getAsJsonObject();
        JsonObject k = all.getAsJsonObject("constants");
        assertEquals(DragonFlight.PIVOT_Y, k.get("PIVOT_Y").getAsDouble(), 0);
        assertEquals(DragonFlight.BANK_GAIN, k.get("BANK_GAIN").getAsDouble(), 0);
        assertEquals(DragonFlight.MAX_BANK, k.get("MAX_BANK").getAsDouble(), 0);
        assertEquals(DragonFlight.BANK_FADE_SPEED, k.get("BANK_FADE_SPEED").getAsDouble(), 0);
        assertEquals(DragonFlight.MAX_CLIMB, k.get("MAX_CLIMB").getAsDouble(), 0);
        assertEquals(DragonFlight.HOVER_RATE, k.get("HOVER_RATE").getAsDouble(), 0);
        assertEquals(DragonFlight.CRUISE_RATE, k.get("CRUISE_RATE").getAsDouble(), 0);
        assertEquals(DragonFlight.HOVER_AMP, k.get("HOVER_AMP").getAsDouble(), 0);
        assertEquals(DragonFlight.CRUISE_AMP, k.get("CRUISE_AMP").getAsDouble(), 0);
        assertEquals(DragonFlight.GLIDE_AMP, k.get("GLIDE_AMP").getAsDouble(), 0);
        assertEquals(HorntailCues.DEFAULT_BLEND, k.get("DEFAULT_BLEND").getAsDouble(), 0);
        assertEquals(DragonFlight.CLIMB_BOOST, k.get("CLIMB_BOOST").getAsDouble(), 0);
        assertEquals(DragonFlight.TAKEOFF_GAIN, k.get("TAKEOFF_GAIN").getAsDouble(), 0);
        assertEquals(DragonFlight.DOWNSTROKE_SHARE, k.get("DOWNSTROKE_SHARE").getAsDouble(), 0);
        assertEquals(DragonFlight.FOREARM_LAG, k.get("FOREARM_LAG").getAsDouble(), 0);
        assertEquals(DragonFlight.FINGER_LAG, k.get("FINGER_LAG").getAsDouble(), 0);
        assertEquals(DragonFlight.HEAD_YAW_LIMIT, k.get("HEAD_YAW_LIMIT").getAsDouble(), 0);
        assertEquals(DragonFlight.HEAD_PITCH_LIMIT, k.get("HEAD_PITCH_LIMIT").getAsDouble(), 0);
        assertEquals(DragonFlight.MIDPLANE_CLEARANCE, k.get("MIDPLANE_CLEARANCE").getAsDouble(), 0);
        for (Map.Entry<String, JsonElement> d : k.getAsJsonObject("DURATIONS").entrySet()) {
            assertEquals(HorntailCues.duration(d.getKey()), d.getValue().getAsInt(), d.getKey());
        }
        Map<String, SceneDef> scenes = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : all.getAsJsonObject("scenes").entrySet()) {
            scenes.put(e.getKey(), SceneParser.parse(e.getKey(), e.getValue().getAsJsonObject()));
        }
        JsonArray cases = all.getAsJsonArray("cases");
        assertEquals(44, cases.size());
        int blending = 0, beating = 0, nested = 0, looking = 0;
        for (JsonElement ce : cases) {
            JsonObject c = ce.getAsJsonObject();
            SceneDef s = scenes.get(c.get("scene").getAsString());
            float t = c.get("t").getAsFloat();
            String at = c.get("scene").getAsString() + " t " + t;
            SceneDef.ActorDef h = s.actors().get(0);
            DragonFlight.Pose p = DragonFlight.place(h, t);
            double[] pos = doubles(c.get("pos"));
            close(pos[0], p.x(), 1e-3, at + " x");
            close(pos[1], p.y(), 1e-3, at + " y");
            close(pos[2], p.z(), 1e-3, at + " z");
            close(c.get("yaw").getAsDouble(), p.yaw(), 0.02, at + " yaw");
            close(c.get("pitch").getAsDouble(), p.pitch(), 0.02, at + " pitch");
            close(c.get("roll").getAsDouble(), p.roll(), 0.02, at + " roll");
            close(c.get("speed").getAsDouble(), p.speed(), 1e-4, at + " speed");
            close(c.get("climb").getAsDouble(), p.climb(), 1e-4, at + " climb");
            close(c.get("bank").getAsDouble(), DragonFlight.bank(h.path(), t), 0.02, at + " bank");
            close(c.get("climb_pitch").getAsDouble(), DragonFlight.climbPitch(h.path(), t), 0.02, at + " climb pitch");
            close(c.get("beat_rate").getAsDouble(), DragonFlight.beatRate(p.speed(), p.climb()), 1e-6, at + " rate");
            close(c.get("wing_amp").getAsDouble(), DragonFlight.wingAmp(p.speed(), p.climb()), 1e-6, at + " amp");
            close(c.get("phase").getAsDouble(), DragonFlight.wingPhase(h.path(), t), 1e-3, at + " phase");
            JsonObject st = c.getAsJsonObject("state");
            HorntailCues.State js = HorntailCues.at(h.anims(), t);
            assertEquals(st.get("name").getAsString(), js.name(), at + " state");
            assertEquals(st.get("tick").getAsInt(), js.stateTick(), at + " state tick");
            assertEquals(st.get("flying").getAsBoolean(), js.flying(), at + " flying");
            close(st.get("time").getAsDouble(), js.time(), 1e-4, at + " state time");
            HorntailCues.Blend b = HorntailCues.blendAt(h.anims(), t);
            if (c.get("before").isJsonNull()) {
                assertEquals(null, b.before(), at + " blend");
            } else {
                assertEquals(c.get("before").getAsString(), b.before().name(), at + " blend from");
                blending++;
            }
            close(c.get("weight").getAsDouble(), b.weight(), 1e-6, at + " weight");
            // The whole chain of nested fades.
            JsonArray chain = c.getAsJsonArray("blend_chain");
            HorntailCues.Blend link = b;
            for (int i = 0; i < chain.size(); i++, link = link.older()) {
                JsonArray want = chain.get(i).getAsJsonArray();
                assertTrue(link != null, at + " blend chain ends at " + i);
                assertEquals(want.get(0).getAsString(), link.now().name(), at + " chain " + i);
                assertEquals(want.get(1).isJsonNull() ? null : want.get(1).getAsString(),
                        link.before() == null ? null : link.before().name(), at + " chain " + i + " before");
                close(want.get(2).getAsDouble(), link.weight(), 1e-6, at + " chain " + i + " weight");
            }
            assertEquals(null, link, at + " blend chain longer than the mirror's");
            if (chain.size() > 1) nested++;
            // Her head look and the neck's counter-roll.
            com.hogwartsmagic.pensieve.scene.PartTrack hl = h.limbs().get(DragonFlight.HEAD_LOOK);
            float[] look = DragonFlight.headLook(hl == null ? null : hl.rot(), t);
            double[] wantLook = doubles(c.get("head_look"));
            close(wantLook[0], look[0], 1e-3, at + " head yaw");
            close(wantLook[1], look[1], 1e-3, at + " head pitch");
            if (look[0] != 0 || look[1] != 0) looking++;
            close(c.get("neck_roll").getAsDouble(), DragonFlight.neckRoll(p.roll()), 1e-3, at + " neck roll");
            int t0 = Math.max(0, (int) Math.floor(t) - 40), t1 = (int) Math.floor(t);
            int[] want = ints(c.get("downstrokes"));
            assertArrayEquals(want, DragonFlight.downstrokes(h.path(), t0, t1), at + " downstrokes");
            int[] wantBeating = ints(c.get("beating_downstrokes"));
            assertArrayEquals(wantBeating, DragonFlight.downstrokes(h.path(), h.anims(), t0, t1), at + " beating");
            beating += wantBeating.length;
        }
        assertTrue(blending >= 3, "the cases cover cross-fades: " + blending);
        assertTrue(beating >= 10, "the cases cover wingbeats: " + beating);
        assertTrue(nested >= 2, "the cases cover nested fades: " + nested);
        assertTrue(looking >= 4, "the cases cover the head look: " + looking);
    }

    private static int[] ints(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsInt();
        return out;
    }

    /** A name set that knows every name (the dev scene's sounds and blocks are the game's own). */
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

    /**
     * The dev scene (written by build_scripts/pensieve_scenes/dev_horntail.py into the ignored run/pensieve_dev)
     * validates, and plays every cue. Skipped where it has not been generated.
     */
    @Test
    void devHorntailSceneValidates() throws Exception {
        Path f = Path.of("run/pensieve_dev/dev_horntail.json");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(f),
                "run/pensieve_dev/dev_horntail.json not generated (python build_scripts/pensieve_scenes/dev_horntail.py)");
        SceneDef def = SceneParser.parse("dev_horntail", JsonParser.parseString(Files.readString(f)).getAsJsonObject());
        SceneNames names = new SceneNames(ANY, ANY, ANY, ANY, ANY, ANY, ANY, ANY, ANY);
        assertEquals(List.of(), SceneValidator.validate(def, names));
        java.util.Set<String> cued = new java.util.HashSet<>();
        for (SceneDef.ActorDef a : def.actors()) {
            if (!SceneNames.HORNTAIL_ACTOR.equals(a.type())) continue;
            for (SceneDef.Cue c : a.anims()) cued.add(c.name().toUpperCase(java.util.Locale.ROOT));
        }
        for (String n : List.of("IDLE", "ROAR", "BREATH_WINDUP", "BREATH", "TAKEOFF", "FLY", "AIR_BREATH", "BITE",
                "STAGGER")) {
            assertTrue(cued.contains(n), "dev_horntail cues " + n);
        }
    }
}
