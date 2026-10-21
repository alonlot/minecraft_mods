package com.hogwartsmagic.pensieve;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.CameraPose;
import com.hogwartsmagic.pensieve.scene.CameraTrack;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneException;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraTrackTest {
    private static CameraTrack track(String json) throws Exception {
        JsonArray a = JsonParser.parseString(json).getAsJsonArray();
        return CameraTrack.parse(a);
    }

    @Test
    void interpolatesPositionAndFov() throws Exception {
        CameraTrack c = track("""
                [{"t":0,"pos":[0,0,0],"look_at":[0,0,10],"fov":70,"ease":"linear"},
                 {"t":20,"pos":[20,0,0],"look_at":[20,0,10],"fov":50,"ease":"linear"}]""");
        CameraPose p = c.sample(10, id -> null);
        assertEquals(10, p.x(), 1e-6);
        assertEquals(60, p.fov(), 1e-4);
    }

    @Test
    void fovIsClampedToASaneRange() throws Exception {
        CameraTrack c = track("""
                [{"t":0,"pos":[0,0,0],"look_at":[0,0,10],"fov":1,"ease":"linear"},
                 {"t":20,"pos":[20,0,0],"look_at":[20,0,10],"fov":400,"ease":"linear"}]""");
        assertEquals(CameraTrack.MIN_FOV, c.sample(0, id -> null).fov(), 1e-4);
        assertEquals(CameraTrack.MAX_FOV, c.sample(20, id -> null).fov(), 1e-4);
        float mid = c.sample(10, id -> null).fov();
        assertTrue(mid >= CameraTrack.MIN_FOV && mid <= CameraTrack.MAX_FOV, "mid fov " + mid);
    }

    @Test
    void cutSnapsInsteadOfGliding() throws Exception {
        CameraTrack c = track("""
                [{"t":0,"pos":[0,0,0],"look_at":[0,0,10],"ease":"linear"},
                 {"t":20,"pos":[20,0,0],"look_at":[0,0,10],"cut":true}]""");
        assertEquals(0, c.sample(19, id -> null).x(), 1e-6);
        assertEquals(20, c.sample(20, id -> null).x(), 1e-6);
    }

    @Test
    void looksAtTarget() throws Exception {
        CameraTrack z = track("[{\"t\":0,\"pos\":[0,0,0],\"look_at\":[0,0,10]}]");
        CameraPose p = z.sample(0, id -> null);
        assertEquals(0, p.yaw(), 1e-4);
        assertEquals(0, p.pitch(), 1e-4);
        CameraTrack x = track("[{\"t\":0,\"pos\":[0,0,0],\"look_at\":[10,0,0]}]");
        assertEquals(-90, x.sample(0, id -> null).yaw(), 1e-4);
    }

    @Test
    void looksAtActor() throws Exception {
        CameraTrack c = track("[{\"t\":0,\"pos\":[0,0,0],\"look_at\":\"actor:a\"}]");
        CameraPose p = c.sample(0, id -> id.equals("a") ? new float[]{0, -10, 0} : null);
        assertEquals(90, p.pitch(), 1e-4);
    }

    @Test
    void defaultsAndEmpty() throws Exception {
        assertTrue(CameraTrack.EMPTY.isEmpty());
        CameraTrack c = track("[{\"t\":0,\"pos\":[1,2,3],\"look_at\":[1,2,9]}]");
        assertFalse(c.isEmpty());
        assertEquals(70, c.sample(0, id -> null).fov(), 1e-6);
    }

    @Test
    void sceneParserWiresCamera() throws Exception {
        SceneDef s = SceneParser.parse("t", JsonParser.parseString("""
                {"length":40,"camera":[{"t":0,"pos":[0,0,0],"look_at":[0,0,5]}]}""").getAsJsonObject());
        assertFalse(s.camera().isEmpty());
    }

    @Test
    void cutsAheadListTheShotsTheCameraJumpsToNext() throws Exception {
        CameraTrack c = track("""
                [{"t":0,"pos":[0,0,0],"look_at":[0,0,5]},
                 {"t":40,"pos":[1,0,0],"look_at":[0,0,5]},
                 {"t":40,"pos":[50,1,0],"look_at":[60,1,0],"cut":true},
                 {"t":90,"pos":[52,1,0],"look_at":[60,1,0]},
                 {"t":90,"pos":[-30,2,4],"look_at":"actor:boy","cut":true}]""");
        assertEquals(0, c.cutsAhead(0, 30).size());
        assertEquals(1, c.cutsAhead(0, 40).size());
        assertEquals(50f, c.cutsAhead(0, 40).get(0).pos()[0], 1e-6f);
        assertEquals(2, c.cutsAhead(10, 100).size());
        // A cut already taken is behind the camera, not ahead of it.
        assertEquals(1, c.cutsAhead(40, 60).size());
        assertEquals(-30f, c.cutsAhead(40, 60).get(0).pos()[0], 1e-6f);
        assertEquals(0, c.cutsAhead(90, 500).size());
        assertEquals(0, CameraTrack.EMPTY.cutsAhead(0, 500).size());
    }

    // ---- spline ----------------------------------------------------------------------------------------------

    /** Keys at the given times and x positions (z half of x, look along +Z from the same x), all spline. */
    private static CameraTrack splineX(float[] ts, float[] xs) throws Exception {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ts.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"t\":").append(ts[i]).append(",\"pos\":[").append(xs[i]).append(",2,").append(xs[i] * 0.5f)
                    .append("],\"look_at\":[").append(xs[i]).append(",2,").append(xs[i] * 0.5f + 20)
                    .append("],\"fov\":").append(60 + xs[i]).append(",\"ease\":\"spline\"}");
        }
        return track(sb.append(']').toString());
    }

    private static CameraTrack linearX(float[] ts, float[] xs) throws Exception {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ts.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"t\":").append(ts[i]).append(",\"pos\":[").append(xs[i]).append(",2,0],\"ease\":\"linear\"}");
        }
        return track(sb.append(']').toString());
    }

    private static double vx(CameraTrack c, double t0, double t1) {
        float a = (float) t0;
        float b = (float) t1;
        return (c.sample(b, id -> null).x() - c.sample(a, id -> null).x()) / ((double) b - a);
    }

    @Test
    void splinePassesThroughEveryKey() throws Exception {
        float[] ts = {0, 8, 16, 30, 38, 50};
        float[] xs = {0, 1, 3, 2, 6, 7};
        CameraTrack c = splineX(ts, xs);
        for (int i = 0; i < ts.length; i++) {
            CameraPose p = c.sample(ts[i], id -> null);
            assertEquals(xs[i], p.x(), 1e-5, "x at key " + i);
            assertEquals(xs[i] * 0.5, p.z(), 1e-5, "z at key " + i);
            assertEquals(60 + xs[i], p.fov(), 1e-4, "fov at key " + i);
            assertEquals(0, p.yaw(), 1e-3, "looks straight along +Z at key " + i);
        }
    }

    /** Velocity, by finite difference just either side of each inner key, is the same: C1, even for uneven spacing. */
    @Test
    void splineIsC1ThroughKeys() throws Exception {
        float[] ts = {0, 8, 16, 30, 38, 50};
        float[] xs = {0, 1, 3, 2, 6, 7};
        CameraTrack c = splineX(ts, xs);
        double h = 1e-3;
        for (int i = 1; i < ts.length - 1; i++) {
            double left = vx(c, ts[i] - h, ts[i]);
            double right = vx(c, ts[i], ts[i] + h);
            assertEquals(left, right, 0.01 * Math.max(0.05, Math.abs(left)), "velocity at key " + i);
        }
        // The same keys joined by linear segments step their velocity at the keys: what the spline is for.
        CameraTrack lin = linearX(ts, xs);
        assertTrue(Math.abs(vx(lin, 16 - h, 16) - vx(lin, 16, 16 + h)) > 0.1);
    }

    /** Evenly spaced keys: a step (the worst case for a cubic) overshoots by under 10% of the step. */
    @Test
    void splineOvershootIsBoundedForEvenlySpacedKeys() throws Exception {
        CameraTrack c = splineX(new float[]{0, 10, 20, 30, 40, 50}, new float[]{0, 0, 0, 1, 1, 1});
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (float t = 0; t <= 50; t += 0.05f) {
            double x = c.sample(t, id -> null).x();
            lo = Math.min(lo, x);
            hi = Math.max(hi, x);
        }
        assertTrue(lo >= -0.1 && hi <= 1.1, "range " + lo + ".." + hi);
        // Keys on a smooth curve, 8 ticks apart: the spline stays on the curve between them.
        float[] wt = new float[11];
        float[] ws = new float[11];
        for (int i = 0; i < ws.length; i++) {
            wt[i] = i * 8;
            ws[i] = (float) (5 * Math.sin(i * 0.6));
        }
        CameraTrack wave = splineX(wt, ws);
        for (float t = 8; t <= 72; t += 0.25f) {
            assertEquals(5 * Math.sin(t / 8 * 0.6), wave.sample(t, id -> null).x(), 0.1, "t " + t);
        }
    }

    @Test
    void twoSplineKeysAreAStraightLine() throws Exception {
        CameraTrack c = splineX(new float[]{0, 20}, new float[]{0, 10});
        assertEquals(5, c.sample(10, id -> null).x(), 1e-5);
        assertEquals(2.5, c.sample(5, id -> null).x(), 1e-5);
    }

    /** A spline run joins an eased segment at that segment's own speed, so it settles into in_out without a kink. */
    @Test
    void splineMatchesAnEasedNeighbour() throws Exception {
        CameraTrack c = track("""
                [{"t":0,"pos":[0,2,0],"look_at":[0,2,9],"ease":"linear"},
                 {"t":10,"pos":[4,2,0],"look_at":[4,2,9],"ease":"linear"},
                 {"t":20,"pos":[6,2,0],"look_at":[6,2,9],"ease":"in_out"},
                 {"t":30,"pos":[9,2,0],"look_at":[9,2,9],"ease":"spline"},
                 {"t":40,"pos":[12,2,0],"look_at":[12,2,9],"ease":"spline"},
                 {"t":50,"pos":[13,2,0],"look_at":[13,2,9],"ease":"in_out"}]""");
        double h = 1e-3;
        assertEquals(0, vx(c, 20, 20 + h), 0.01, "leaves the in_out key at rest");
        assertEquals(0, vx(c, 40 - h, 40), 0.01, "arrives at the in_out key at rest");
        assertEquals(vx(c, 30 - h, 30), vx(c, 30, 30 + h), 0.01, "C1 inside the run");
        CameraTrack d = track("""
                [{"t":0,"pos":[0,2,0],"ease":"linear"},
                 {"t":10,"pos":[5,2,0],"ease":"linear"},
                 {"t":20,"pos":[7,2,0],"ease":"spline"},
                 {"t":30,"pos":[12,2,0],"ease":"spline"}]""");
        assertEquals(0.5, vx(d, 10 - h, 10), 0.01);
        assertEquals(0.5, vx(d, 10, 10 + h), 0.01, "a linear neighbour hands over its own speed");
    }

    @Test
    void splineStopsAtACut() throws Exception {
        CameraTrack c = track("""
                [{"t":0,"pos":[0,2,0],"ease":"spline"},
                 {"t":10,"pos":[10,2,0],"ease":"spline"},
                 {"t":20,"pos":[100,2,0],"ease":"spline","cut":true},
                 {"t":30,"pos":[110,2,0],"ease":"spline"}]""");
        // The far side of the cut does not bend the run before it: two keys with natural ends are a straight line.
        assertEquals(5, c.sample(5, id -> null).x(), 1e-5);
        assertEquals(10, c.sample(19, id -> null).x(), 1e-5);
        assertEquals(100, c.sample(20, id -> null).x(), 1e-5);
        assertEquals(105, c.sample(25, id -> null).x(), 1e-5);
    }

    @Test
    void splineLooksAtAnActor() throws Exception {
        CameraTrack c = track("""
                [{"t":0,"pos":[0,2,0],"look_at":"actor:a","ease":"spline"},
                 {"t":10,"pos":[4,2,0],"look_at":"actor:a","ease":"spline"},
                 {"t":20,"pos":[8,2,0],"look_at":"actor:a","ease":"spline"}]""");
        CameraPose p = c.sample(10, id -> new float[]{5, 2, 10});
        assertEquals(4, p.x(), 1e-5);
        assertEquals(-Math.toDegrees(Math.atan2(1, 10)), p.yaw(), 1e-3);
    }

    @Test
    void splineIsForCameraKeysAndActorPathsNotLimbs() throws Exception {
        SceneParser.parse("t", JsonParser.parseString("""
                {"length":40,"actors":[{"id":"a","type":"player",
                  "path":[{"t":0,"pos":[0,0,0]},{"t":20,"pos":[1,0,0],"ease":"spline"}]}]}""").getAsJsonObject());
        assertThrows(SceneException.class, () -> SceneParser.parse("t", JsonParser.parseString("""
                {"length":40,"actors":[{"id":"a","type":"player","limbs":{"head":
                  {"rot":[{"t":0,"v":[0,0,0]},{"t":20,"v":[1,0,0],"ease":"spline"}]}}}]}""").getAsJsonObject()));
    }
}

