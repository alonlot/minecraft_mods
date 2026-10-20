package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.Ease;
import com.hogwartsmagic.pensieve.scene.HumanMotion;
import com.hogwartsmagic.pensieve.scene.Keyframe;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.Track;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hogwartsmagic.pensieve.scene.HumanMotion.BODY;
import static com.hogwartsmagic.pensieve.scene.HumanMotion.HEAD;
import static com.hogwartsmagic.pensieve.scene.HumanMotion.LEFT_ARM;
import static com.hogwartsmagic.pensieve.scene.HumanMotion.LEFT_LEG;
import static com.hogwartsmagic.pensieve.scene.HumanMotion.RIGHT_ARM;
import static com.hogwartsmagic.pensieve.scene.HumanMotion.RIGHT_LEG;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanMotionTest {
    private static final float[] FULL = {1, 1, 1, 1, 1, 1};
    /** The player model's rest pivots (px): head, body, right arm, left arm, right leg, left leg. */
    private static final float[] REST_POS = {0, 0, 0, 0, 0, 0, -5, 2, 0, 5, 2, 0, -1.9f, 12, 0, 1.9f, 12, 0};

    private static float[] rot() {
        return new float[18];
    }

    private static float[] pos() {
        return REST_POS.clone();
    }

    private static SceneDef.Cue cue(float t, String name, float blend) {
        return new SceneDef.Cue(t, name, blend);
    }

    /** The full stack for a standing actor at rest: life, then the clips. */
    private static float[][] compose(HumanMotion.Clips clips, long seed, float t, float scale, float speed, float phase) {
        float[] r = rot();
        float[] p = pos();
        float seat = clips.seat(t, 0f);
        HumanMotion.life(r, p, seed, t, scale, speed, phase, seat, FULL);
        clips.apply(r, p, t, scale, 0f);
        return new float[][]{r, p};
    }

    private static void assertFinite(float[] v, String what) {
        for (float x : v) assertTrue(Float.isFinite(x), what + " not finite");
    }

    @Test
    void clipNamesIgnoreCase() {
        assertEquals("CLAP", HumanMotion.clipName("clap"));
        assertEquals("CHEER_STAND", HumanMotion.clipName("Cheer_Stand"));
        assertNull(HumanMotion.clipName("moonwalk"));
        assertNull(HumanMotion.clipName(null));
        for (String c : List.of("IDLE", "CLAP", "CHEER", "CHEER_STAND", "TALK", "WHISPER", "NERVOUS", "WAVE",
                "HANDS_CLASPED", "READ_SCROLL", "REACH_PLACE", "SIT_DOWN", "STAND_UP")) {
            assertNotNull(HumanMotion.clipName(c), c);
        }
    }

    @Test
    void jointAnglesStayHuman() {
        for (String clip : HumanMotion.CLIPS) {
            for (int s = 0; s < 6; s++) {
                long seed = HumanMotion.seed("actor_" + s);
                HumanMotion.Clips clips = new HumanMotion.Clips(List.of(cue(10, clip, 4)), seed);
                for (float t = 0; t < 800; t += 0.5f) {
                    float[][] rp = compose(clips, seed, t, 0.9f, 0, 0);
                    assertFinite(rp[0], clip);
                    assertFinite(rp[1], clip);
                    for (int i = 0; i < 18; i++) {
                        assertTrue(Math.abs(rp[0][i]) <= 185, clip + " part " + i / 3 + " rot " + rp[0][i] + " at " + t);
                        assertTrue(Math.abs(rp[1][i] - REST_POS[i]) <= 16, clip + " part " + i / 3 + " off at " + t);
                    }
                    // Heads do not snap round: a nod or a glance, never an owl.
                    assertTrue(Math.abs(rp[0][HEAD * 3]) <= 45 && Math.abs(rp[0][HEAD * 3 + 1]) <= 45, clip + " head at " + t);
                }
            }
        }
    }

    @Test
    void walkingLifeStaysHuman() {
        long seed = HumanMotion.seed("walker");
        for (float speed : new float[]{0.5f, 1.4f, 3f, 6f}) {
            for (float t = 0; t < 400; t += 0.5f) {
                float[] r = rot();
                float[] p = pos();
                HumanMotion.life(r, p, seed, t, 1f, speed, t / 20f * speed / HumanMotion.strideLength(speed), 0, FULL);
                for (int i = 0; i < 18; i++) assertTrue(Math.abs(r[i]) <= 70, "speed " + speed + " part " + i / 3 + " " + r[i]);
            }
        }
    }

    @Test
    void deterministic() {
        long seed = HumanMotion.seed("hermione");
        assertEquals(seed, HumanMotion.seed("hermione"));
        assertTrue(seed != HumanMotion.seed("ron"));
        List<SceneDef.Cue> cues = List.of(cue(0, "NERVOUS", 0), cue(80, "CLAP", 6), cue(160, "CHEER_STAND", 4));
        for (float t = 0; t < 300; t += 7.3f) {
            float[][] a = compose(new HumanMotion.Clips(cues, seed), seed, t, 0.75f, 0.3f, 1.7f);
            float[][] b = compose(new HumanMotion.Clips(cues, seed), seed, t, 0.75f, 0.3f, 1.7f);
            assertArrayEquals(a[0], b[0]);
            assertArrayEquals(a[1], b[1]);
        }
    }

    /** The largest change between two samples 0.25 ticks apart, over [from, to]. */
    private static float maxStep(HumanMotion.Clips clips, long seed, float from, float to, boolean offsets) {
        float worst = 0;
        float[][] prev = compose(clips, seed, from, 0.9f, 0, 0);
        for (float t = from + 0.25f; t <= to; t += 0.25f) {
            float[][] now = compose(clips, seed, t, 0.9f, 0, 0);
            int k = offsets ? 1 : 0;
            for (int i = 0; i < 18; i++) worst = Math.max(worst, Math.abs(now[k][i] - prev[k][i]));
            prev = now;
        }
        return worst;
    }

    @Test
    void continuousAtClipBlends() {
        long seed = HumanMotion.seed("blend");
        HumanMotion.Clips clips = new HumanMotion.Clips(List.of(cue(20, "CLAP", 6), cue(100, "CHEER", 6),
                cue(200, "TALK", 6), cue(300, "HANDS_CLASPED", 8), cue(400, "IDLE", 8)), seed);
        for (float at : new float[]{20, 100, 200, 300, 400}) {
            assertTrue(maxStep(clips, seed, at - 4, at + 12, false) < 14f, "rotation jump at the cue at " + at);
            assertTrue(maxStep(clips, seed, at - 4, at + 12, true) < 1f, "offset jump at the cue at " + at);
        }
    }

    @Test
    void continuousAtLoopSeamsAndThroughOneShots() {
        for (String clip : HumanMotion.CLIPS) {
            long seed = HumanMotion.seed("loop_" + clip);
            HumanMotion.Clips clips = new HumanMotion.Clips(List.of(cue(0, clip, 1)), seed);
            assertTrue(maxStep(clips, seed, 2, 800, false) < 14f, clip + " rotation jumps");
            assertTrue(maxStep(clips, seed, 2, 800, true) < 1.2f, clip + " offset jumps");
        }
    }

    @Test
    void clipsDifferPerActor() {
        for (String clip : List.of("CLAP", "CHEER", "TALK", "NERVOUS", "WAVE")) {
            long a = HumanMotion.seed("gryffindor_01");
            long b = HumanMotion.seed("gryffindor_02");
            HumanMotion.Clips ca = new HumanMotion.Clips(List.of(cue(0, clip, 4)), a);
            HumanMotion.Clips cb = new HumanMotion.Clips(List.of(cue(0, clip, 4)), b);
            float diff = 0;
            for (float t = 20; t < 80; t += 1) {
                float[] ra = compose(ca, a, t, 0.9f, 0, 0)[0];
                float[] rb = compose(cb, b, t, 0.9f, 0, 0)[0];
                diff = Math.max(diff, Math.abs(ra[RIGHT_ARM * 3] - rb[RIGHT_ARM * 3]) + Math.abs(ra[RIGHT_ARM * 3 + 1] - rb[RIGHT_ARM * 3 + 1]));
            }
            assertTrue(diff > 4f, clip + " is in lockstep across actors (" + diff + ")");
        }
    }

    @Test
    void lifeAloneIsSubtle() {
        long seed = HumanMotion.seed("idle");
        float worstRot = 0;
        float worstOff = 0;
        for (float t = 0; t < 1200; t += 1) {
            float[] r = rot();
            float[] p = pos();
            HumanMotion.life(r, p, seed, t, 1f, 0, 0, 0, FULL);
            for (int i = 0; i < 18; i++) {
                worstRot = Math.max(worstRot, Math.abs(r[i]));
                worstOff = Math.max(worstOff, Math.abs(p[i] - REST_POS[i]));
            }
        }
        assertTrue(worstRot < 7f, "idle rotation " + worstRot);
        assertTrue(worstOff < 1.2f, "idle offset " + worstOff);
        assertTrue(worstRot > 0.5f, "idle life does nothing");
    }

    @Test
    void lifeGainDampsAPart() {
        long seed = HumanMotion.seed("damped");
        float full = 0;
        float damped = 0;
        float[] gain = {0.35f, 1, 1, 1, 1, 1};
        for (float t = 0; t < 600; t += 1) {
            float[] r = rot();
            HumanMotion.life(r, pos(), seed, t, 1f, 0, 0, 0, FULL);
            full = Math.max(full, Math.abs(r[HEAD * 3 + 1]));
            float[] d = rot();
            HumanMotion.life(d, pos(), seed, t, 1f, 0, 0, 0, gain);
            damped = Math.max(damped, Math.abs(d[HEAD * 3 + 1]));
        }
        assertTrue(damped < full * 0.5f, damped + " vs " + full);
    }

    @Test
    void gaitArmsOpposeLegsAndLegsOpposeEachOther() {
        long seed = HumanMotion.seed("gait");
        int opposed = 0;
        int samples = 0;
        for (float phase = 0.05f; phase < 3; phase += 0.1f) {
            float[] r = rot();
            HumanMotion.life(r, pos(), seed, 100, 1f, 1.4f, phase, 0, FULL);
            float rl = r[RIGHT_LEG * 3];
            float ll = r[LEFT_LEG * 3];
            float ra = r[RIGHT_ARM * 3];
            if (Math.abs(rl) > 8) {
                samples++;
                assertTrue(rl * ll < 0, "legs together at phase " + phase);
                if (rl * ra < 0) opposed++;
            }
        }
        assertTrue(samples > 10 && opposed >= samples * 0.8, opposed + " of " + samples);
    }

    @Test
    void strideGrowsWithSpeed() {
        assertTrue(HumanMotion.strideLength(0.5f) < HumanMotion.strideLength(1.4f));
        assertTrue(HumanMotion.strideLength(1.4f) < HumanMotion.strideLength(4f));
        float walk = HumanMotion.strideLength(1.4f);
        assertTrue(walk > 1.2f && walk < 1.7f, "a walking stride is about 1.4 blocks, got " + walk);
    }

    private static Track line(float t0, float t1, float x1) {
        return new Track(List.of(new Keyframe(t0, new float[]{0, 0, 0}, Ease.LINEAR),
                new Keyframe(t1, new float[]{x1, 0, 0}, Ease.LINEAR)));
    }

    @Test
    void gaitPhaseIsLockedToDistance() {
        HumanMotion.Odometer o = new HumanMotion.Odometer(line(20, 220, 10), 1f);   // 1 block/s
        assertEquals(0, o.distance(10), 1e-4);
        assertEquals(5, o.distance(120), 1e-3);
        assertEquals(10, o.distance(300), 1e-3);
        assertEquals(1f, o.speed(120), 1e-3);
        assertEquals(0, o.speed(400), 1e-4);
        float ratio = o.phase(80) / o.distance(80);
        assertEquals(ratio, o.phase(180) / o.distance(180), ratio * 0.01f);
        assertEquals(1f / HumanMotion.strideLength(1f), ratio, 0.02f);
        // Standing still, the feet do not slide: the phase holds.
        assertEquals(o.phase(230), o.phase(400), 1e-5);
        // The same distance at twice the size is half as many steps.
        HumanMotion.Odometer big = new HumanMotion.Odometer(line(20, 220, 20), 2f);
        assertEquals(o.phase(180), big.phase(180), 0.02f);
        // A path that never moves has no gait.
        HumanMotion.Odometer still = new HumanMotion.Odometer(new Track(List.of(
                new Keyframe(0, new float[]{3, 0, 3}, Ease.IN_OUT), new Keyframe(50, new float[]{3, 1, 3}, Ease.IN_OUT))), 1f);
        assertEquals(0, still.phase(40), 1e-6);
        assertEquals(0, still.speed(40), 1e-6);
    }

    @Test
    void bodyFollowsTheHeadLate() {
        Track look = new Track(List.of(new Keyframe(20, new float[]{0, 0}, Ease.LINEAR),
                new Keyframe(21, new float[]{90, 0}, Ease.LINEAR)));
        assertEquals(0, HumanMotion.followYaw(look, 18), 1e-4);
        float early = HumanMotion.followYaw(look, 22);
        assertTrue(early > 0 && early < 40, "the body turns with the head instead of after it: " + early);
        assertEquals(90, HumanMotion.followYaw(look, 60), 0.5f);
        float prev = -1;
        for (float t = 18; t < 60; t += 0.5f) {
            float y = HumanMotion.followYaw(look, t);
            assertTrue(y >= prev - 1e-3, "overshoot-free follow goes back at " + t);
            prev = y;
        }
    }

    @Test
    void sitDownEndsSeatedAndStandUpHandsTheLegsBack() {
        long seed = HumanMotion.seed("sitter");
        HumanMotion.Clips sit = new HumanMotion.Clips(List.of(cue(0, "SIT_DOWN", 1)), seed);
        float[] r = rot();
        float[] p = pos();
        sit.apply(r, p, 60, 0.75f, 0);
        assertEquals(-90, r[RIGHT_LEG * 3], 3);
        assertEquals(-90, r[LEFT_LEG * 3], 3);
        assertEquals(1f, sit.seat(60, 0f), 1e-3);
        assertEquals(0f, sit.seat(0.5f, 0f), 0.05f);

        HumanMotion.Clips up = new HumanMotion.Clips(List.of(cue(0, "STAND_UP", 1)), seed);
        float[] u = rot();
        assertEquals(1f, up.apply(u, pos(), 60, 0.75f, 0), 1e-3);   // the life legs take over
        assertEquals(0f, up.seat(60, 1f), 1e-3);
        float[] mid = rot();
        up.apply(mid, pos(), 6, 0.75f, 0);
        assertTrue(mid[BODY * 3] > 12, "weight forward before rising: " + mid[BODY * 3]);
    }

    @Test
    void cheerStandRisesAndSitsBackDown() {
        long seed = HumanMotion.seed("cheerer");
        HumanMotion.Clips c = new HumanMotion.Clips(List.of(cue(0, "CHEER_STAND", 1)), seed);
        float[] r = rot();
        float[] p = pos();
        c.apply(r, p, 30, 0.9f, 0);        // 1.5 s: up and cheering
        assertTrue(r[RIGHT_LEG * 3] > -30, "stood up: " + r[RIGHT_LEG * 3]);
        float ahead = (float) (HumanMotion.standAhead(0.9) / HumanMotion.pxBlocks(0.9));
        assertEquals(-ahead, p[BODY * 3 + 2], 1.5f, "the hips come forward, clear of the seat edge");
        assertTrue(r[RIGHT_ARM * 3] < -120 && r[LEFT_ARM * 3] < -120, "arms up");
        assertTrue(c.seat(30, 1f) < 0.1f);
        float[] e = rot();
        float[] ep = pos();
        c.apply(e, ep, 100, 0.9f, 0);      // well after: seated again, arms handed back
        assertEquals(-90, e[RIGHT_LEG * 3], 3);
        assertEquals(0, e[RIGHT_ARM * 3], 1e-3);
        assertEquals(REST_POS[BODY * 3 + 1], ep[BODY * 3 + 1], 1e-3);
        assertEquals(1f, c.seat(100, 0f), 1e-3);
    }

    @Test
    void reachPlaceArcsUpThenDownAndLetsGo() {
        long seed = HumanMotion.seed("mcgonagall");
        HumanMotion.Clips c = new HumanMotion.Clips(List.of(cue(0, "REACH_PLACE", 1)), seed);
        float[] high = rot();
        c.apply(high, pos(), HumanMotion.REACH_PEAK_TICKS, 1f, 0);
        float[] place = rot();
        c.apply(place, pos(), HumanMotion.REACH_PLACE_TICKS, 1f, 0);
        assertTrue(high[RIGHT_ARM * 3] < place[RIGHT_ARM * 3] - 25, "the arm arcs over, then lowers");
        assertTrue(place[RIGHT_ARM * 3] < -80, "placing in front at head height");
        float[] after = rot();
        c.apply(after, pos(), 80, 1f, 0);
        assertArrayEquals(rot(), after, 1e-3f);
    }

    // ---- seated legs stay on top of the seat -------------------------------------------------------------------

    private static final double SEAT_TOP = 0.5;

    /** A key on a path track (blocks, ticks). */
    private static Keyframe key(float t, double x, double y, double z, Ease ease) {
        return new Keyframe(t, new float[]{(float) x, (float) y, (float) z}, ease);
    }

    /**
     * Plays {@code clip} (cued at tick 10) for an actor facing +Z (yaw 0) whose seat is a bench under the seated
     * root: top at {@link #SEAT_TOP}, from 1 block behind the seated hip to {@link HumanMotion#SEAT_EDGE_AHEAD} in
     * front of it. Every leg box, sampled along its edges every quarter tick, must stay at or above the seat top
     * wherever it is over the seat. Returns the lowest leg point over the seat at the end (seated: the seat top).
     */
    private static double playOverSeat(String clip, float scale, Track path, long seed, boolean seatedBefore) {
        HumanMotion.Clips clips = new HumanMotion.Clips(List.of(cue(10, clip, 3)), seed, path);
        HumanMotion.Odometer odo = new HumanMotion.Odometer(path, scale);
        double lowestAtEnd = Double.POSITIVE_INFINITY;
        for (float t = 0; t <= 90; t += 0.25f) {
            float[] r = rot();
            float[] p = pos();
            float seat = clips.seat(t, seatedBefore ? 1f : 0f);
            HumanMotion.life(r, p, seed, t, scale, odo.speed(t) * (1 - clips.holdsGait(t)), odo.phase(t), seat, FULL);
            if (seatedBefore) {      // the seated pose the scene puts on before the clip
                r[RIGHT_LEG * 3] = -90;
                r[RIGHT_LEG * 3 + 1] = 8;
                r[LEFT_LEG * 3] = -90;
                r[LEFT_LEG * 3 + 1] = -8;
            }
            clips.apply(r, p, t, scale, 0f, 0f);
            float[] at = path.sample(t);
            double[] root = {at[0], at[1], at[2]};
            for (int leg = RIGHT_LEG; leg <= LEFT_LEG; leg++) {
                double[][] c = HumanMotion.legBox(root, 0, scale, r, p, leg);
                for (int i = 0; i < 8; i++) {
                    for (int j = i + 1; j < 8; j++) {
                        if (Integer.bitCount(i ^ j) != 1) continue;   // a box edge
                        for (int k = 0; k <= 8; k++) {
                            double f = k / 8.0;
                            double x = c[i][0] + (c[j][0] - c[i][0]) * f;
                            double y = c[i][1] + (c[j][1] - c[i][1]) * f;
                            double z = c[i][2] + (c[j][2] - c[i][2]) * f;
                            boolean over = z <= HumanMotion.SEAT_EDGE_AHEAD && z >= -1 && Math.abs(x) <= 1;
                            if (!over) continue;
                            assertTrue(y >= SEAT_TOP - 1e-3, clip + " at scale " + scale + ": a leg dips "
                                    + String.format("%.3f", SEAT_TOP - y) + " into the seat at tick " + t);
                            if (t == 90) lowestAtEnd = Math.min(lowestAtEnd, y);
                        }
                    }
                }
            }
        }
        return lowestAtEnd;
    }

    @Test
    void sitDownAndStandUpKeepTheLegsOutOfTheSeat() {
        for (float scale : new float[]{0.75f, 0.9f, 1.0f}) {
            double seatY = HumanMotion.seatedRootY(SEAT_TOP, scale);
            double ahead = HumanMotion.standAhead(scale);
            long seed = HumanMotion.seed("sitter_" + scale);
            // However the path gets from the standing spot to the seat within the clip's second, the clip steers it.
            Track[] sits = {
                    new Track(List.of(key(0, 0, 0, ahead, Ease.LINEAR), key(14, 0, 0, ahead, Ease.LINEAR),
                            key(27, 0, seatY, 0, Ease.IN_OUT))),
                    new Track(List.of(key(0, 0, 0, ahead, Ease.LINEAR), key(10, 0, 0, ahead, Ease.LINEAR),
                            key(30, 0, seatY, 0, Ease.LINEAR))),
                    new Track(List.of(key(0, 0, 0, ahead, Ease.LINEAR), key(10, 0, 0, ahead, Ease.LINEAR),
                            key(11, 0, seatY, 0, Ease.LINEAR)))};
            for (Track path : sits) {
                double end = playOverSeat("SIT_DOWN", scale, path, seed, false);
                assertEquals(SEAT_TOP, end, 0.01, "the thighs rest on the seat at scale " + scale);
            }
            Track[] stands = {
                    new Track(List.of(key(0, 0, seatY, 0, Ease.LINEAR), key(12, 0, seatY, 0, Ease.LINEAR),
                            key(26, 0, 0, ahead, Ease.IN_OUT))),
                    new Track(List.of(key(0, 0, seatY, 0, Ease.LINEAR), key(10, 0, seatY, 0, Ease.LINEAR),
                            key(30, 0, 0, ahead, Ease.LINEAR)))};
            for (Track path : stands) playOverSeat("STAND_UP", scale, path, seed, true);
        }
    }

    @Test
    void cheerStandKeepsTheLegsOutOfTheSeat() {
        for (float scale : new float[]{0.75f, 0.9f, 1.0f}) {
            Track held = new Track(List.of(key(0, 0, HumanMotion.seatedRootY(SEAT_TOP, scale), 0, Ease.LINEAR)));
            for (int i = 0; i < 4; i++) {
                double end = playOverSeat("CHEER_STAND", scale, held, HumanMotion.seed("cheer_" + i + "_" + scale), true);
                assertEquals(SEAT_TOP, end, 0.01, "back on the seat at scale " + scale);
            }
        }
    }

    @Test
    void seatedGeometry() {
        for (double scale : new double[]{0.75, 0.9, 1.0, 1.5}) {
            double hip = HumanMotion.seatedRootY(SEAT_TOP, scale) + HumanMotion.hipHeight(scale);
            assertEquals(SEAT_TOP, hip - HumanMotion.legLowestBelowHip(-90, scale), 1e-9, "level thighs on the seat");
            // The standing hip is a whole leg (with the pants layer below the sole) above the feet.
            assertEquals((HumanMotion.LEG_LENGTH_PX + HumanMotion.LAYER_PX) * HumanMotion.pxBlocks(scale),
                    HumanMotion.legLowestBelowHip(0, scale), 1e-9);
        }
        // The player model is 1.8 blocks tall: the hip is at 12 of its 32 px, scaled by 15/16.
        assertEquals(0.704, HumanMotion.hipHeight(1), 0.001);
    }

    /** The Python mirror (build_scripts/pensieve_scenes/human_motion.py) wrote these cases; the Java must agree. */
    @Test
    void pythonMirrorAgrees() throws Exception {
        com.google.gson.JsonObject cases = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
                java.nio.file.Path.of("build_scripts/pensieve_scenes/human_motion_cases.json"))).getAsJsonObject();
        com.google.gson.JsonObject k = cases.getAsJsonObject("constants");
        assertEquals(HumanMotion.MODEL_SCALE, k.get("MODEL_SCALE").getAsDouble(), 0);
        assertEquals(HumanMotion.MODEL_ORIGIN, k.get("MODEL_ORIGIN").getAsDouble(), 0);
        assertEquals(HumanMotion.HIP_PX, k.get("HIP_PX").getAsDouble(), 0);
        assertEquals(HumanMotion.LEG_LENGTH_PX, k.get("LEG_LENGTH_PX").getAsDouble(), 0);
        assertEquals(HumanMotion.LEG_HALF_WIDTH_PX, k.get("LEG_HALF_WIDTH_PX").getAsDouble(), 0);
        assertEquals(HumanMotion.LAYER_PX, k.get("LAYER_PX").getAsDouble(), 0);
        assertEquals(HumanMotion.SEAT_EDGE_AHEAD, k.get("SEAT_EDGE_AHEAD").getAsDouble(), 0);
        assertEquals(HumanMotion.STAND_CLEARANCE, k.get("STAND_CLEARANCE").getAsDouble(), 0);
        assertEquals(HumanMotion.SEAT_HEIGHT, k.get("SEAT_HEIGHT").getAsDouble(), 0);
        for (var e : cases.getAsJsonArray("scales")) {
            com.google.gson.JsonObject o = e.getAsJsonObject();
            double sc = o.get("scale").getAsDouble();
            assertEquals(o.get("hip_height").getAsDouble(), HumanMotion.hipHeight(sc), 1e-9);
            assertEquals(o.get("seated_root_y_0_5").getAsDouble(), HumanMotion.seatedRootY(0.5, sc), 1e-9);
            assertEquals(o.get("stand_ahead").getAsDouble(), HumanMotion.standAhead(sc), 1e-9);
            assertEquals(o.get("stand_rise").getAsDouble(), HumanMotion.standRise(sc), 1e-9);
            assertEquals(o.get("lowest_m90").getAsDouble(), HumanMotion.legLowestBelowHip(-90, sc), 1e-9);
            assertEquals(o.get("lowest_m65").getAsDouble(), HumanMotion.legLowestBelowHip(-65, sc), 1e-9);
            assertEquals(o.get("lowest_0").getAsDouble(), HumanMotion.legLowestBelowHip(0, sc), 1e-9);
        }
        for (var e : cases.getAsJsonArray("curves")) {
            com.google.gson.JsonObject o = e.getAsJsonObject();
            double s = o.get("s").getAsDouble();
            assertCurve(o, "sit_rise", HumanMotion.sitDownCurve(s, true));
            assertCurve(o, "sit_fall", HumanMotion.sitDownCurve(s, false));
            assertCurve(o, "stand_rise", HumanMotion.standUpCurve(s, true));
            assertCurve(o, "stand_fall", HumanMotion.standUpCurve(s, false));
            assertCurve(o, "cheer_075", HumanMotion.cheerStandCurve(s * 3 / 1.1, 0.75));
            assertCurve(o, "cheer_1", HumanMotion.cheerStandCurve(s * 3 / 1.1, 1.0));
        }
        for (var e : cases.getAsJsonArray("boxes")) {
            com.google.gson.JsonObject o = e.getAsJsonObject();
            var rootJ = o.getAsJsonArray("root");
            double[] root = {rootJ.get(0).getAsDouble(), rootJ.get(1).getAsDouble(), rootJ.get(2).getAsDouble()};
            int leg = "right".equals(o.get("side").getAsString()) ? RIGHT_LEG : LEFT_LEG;
            float[] r = rot();
            r[leg * 3] = o.get("pitch").getAsFloat();
            r[leg * 3 + 1] = o.get("splay").getAsFloat();
            double[][] c = HumanMotion.legBox(root, o.get("yaw").getAsDouble(), o.get("scale").getAsDouble(), r, pos(), leg);
            var want = o.getAsJsonArray("corners");
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 3; j++) {
                    assertEquals(want.get(i).getAsJsonArray().get(j).getAsDouble(), c[i][j], 1e-5, "corner " + i);
                }
            }
        }
    }

    private static void assertCurve(com.google.gson.JsonObject o, String name, double[] got) {
        var want = o.getAsJsonArray(name);
        for (int i = 0; i < 3; i++) assertEquals(want.get(i).getAsDouble(), got[i], 1e-9, name + "[" + i + "]");
    }

    @Test
    void unknownClipIsIgnored() {
        HumanMotion.Clips c = new HumanMotion.Clips(List.of(cue(0, "BOGUS", 1)), 1L);
        float[] r = rot();
        c.apply(r, pos(), 40, 1f, 0);
        assertArrayEquals(rot(), r, 0f);
        assertTrue(Float.isNaN(c.cueTime(40)) || c.cueTime(40) < 0);
    }
}
