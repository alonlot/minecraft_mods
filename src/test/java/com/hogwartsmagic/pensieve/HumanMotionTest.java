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
        assertTrue(p[BODY * 3 + 1] < -1.5f, "the body rises off the bench: " + p[BODY * 3 + 1]);
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

    @Test
    void unknownClipIsIgnored() {
        HumanMotion.Clips c = new HumanMotion.Clips(List.of(cue(0, "BOGUS", 1)), 1L);
        float[] r = rot();
        c.apply(r, pos(), 40, 1f, 0);
        assertArrayEquals(rot(), r, 0f);
        assertTrue(Float.isNaN(c.cueTime(40)) || c.cueTime(40) < 0);
    }
}
