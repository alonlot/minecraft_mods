package com.hogwartsmagic.pensieve;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.CameraPose;
import com.hogwartsmagic.pensieve.scene.CameraTrack;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}

