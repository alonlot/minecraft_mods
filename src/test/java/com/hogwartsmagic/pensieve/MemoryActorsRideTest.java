package com.hogwartsmagic.pensieve;

import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.BroomFlight;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.hogwartsmagic.pensieve.scene.Vehicle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ride seam MemoryActors asks ({@link Vehicle}, pure): where a rider with a ride window stands, inside the window
 * and out of it, and that a bracketed mount and dismount do not jump.
 */
class MemoryActorsRideTest {
    private static final double SEAT_Y = BroomFlight.seat(0.75)[1], SEAT_Z = BroomFlight.seat(0.75)[2];

    /**
     * A broom held level facing +X (yaw -90) at (4, 1.2, 3) until t 40, then flying off along +X and climbing; a boy
     * walks to it, mounts at 40 (a world key at 39.999 where the seat puts him, the seat at 40), rides, and steps off at
     * 120 where the broom has landed again.
     */
    private static SceneDef scene() throws Exception {
        // At yaw -90 the broom frame's +z (forward) is world +X and its +x (left) is world +Z... and -z is behind.
        double mountX = 4 + SEAT_Z * 1.0, mountY = 1.2 + SEAT_Y;   // yaw -90: local z maps to world +x
        String json = """
                {"length":200,"actors":[
                 {"id":"b","type":"hogwartsmagic:memory_broom",
                  "path":[{"t":0,"pos":[4,1.2,3]},{"t":40,"pos":[4,1.2,3]},
                          {"t":80,"pos":[20,6,3],"ease":"spline"},{"t":115,"pos":[30,1.2,3],"ease":"in_out"}],
                  "look":[{"t":0,"yaw":-90}]},
                 {"id":"boy","type":"player","scale":0.75,"ride":{"on":"b","from":40,"until":120},
                  "path":[{"t":0,"pos":[-6,%2$s,3]},{"t":30,"pos":[%1$s,%2$s,3]},{"t":39.999,"pos":[%1$s,%2$s,3]},
                          {"t":40,"pos":[0,%3$s,%4$s]},{"t":119.999,"pos":[0,%3$s,%4$s]},
                          {"t":120,"pos":[%5$s,%6$s,3]},{"t":160,"pos":[34,%6$s,8]}],
                  "look":[{"t":0,"yaw":-90},{"t":39.999,"yaw":-90},{"t":40,"yaw":0},{"t":119.999,"yaw":0},
                          {"t":120,"yaw":-90}]}]}""".formatted(mountX, mountY, SEAT_Y, SEAT_Z, 30 + SEAT_Z, 1.2 + SEAT_Y);
        return SceneParser.parse("ride", JsonParser.parseString(json).getAsJsonObject());
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
    }

    @Test
    void riderOutsideWindowIsWorldSpace() throws Exception {
        SceneDef s = scene();
        SceneDef.ActorDef boy = s.actors().get(1);
        for (float t : new float[]{0, 15, 30, 39.5f, 120, 140, 180}) {
            assertNull(Vehicle.seat(s, boy, t), "no seat at " + t);
            float[] own = Vehicle.path(boy, t);
            double[] root = Vehicle.root(s, boy, t);
            for (int i = 0; i < 3; i++) assertEquals(own[i], root[i], 1e-9, "world path at " + t);
            // Only the keys of its own part count: before the mount the vehicle-frame seat key never pulls at it.
            assertTrue(root[1] > 0.5, "standing on the ground at " + t + ", not at a seat offset: " + root[1]);
        }
        assertEquals(-6, Vehicle.root(s, boy, 0)[0], 1e-6);
        assertEquals(34, Vehicle.root(s, boy, 180)[0], 1e-6);
        // The look is world space outside the window and relative to the broom inside it.
        assertEquals(-90, Vehicle.look(boy, 20)[0], 1e-6);
        assertEquals(0, Vehicle.look(boy, 60)[0], 1e-6);
        Vehicle.Seat in = Vehicle.seat(s, boy, 60);
        assertNotNull(in);
        BroomFlight.Pose b = BroomFlight.place(s, s.actors().get(0), 60);
        assertEquals(b.yaw(), in.yaw(), 1e-6, "inside the window the rider faces with the broom");
        double[] want = BroomFlight.toWorld(b.x(), b.y(), b.z(), b.m(), new double[]{0, SEAT_Y, SEAT_Z});
        assertTrue(dist(want, new double[]{in.x(), in.y(), in.z()}) < 1e-4, "inside the window: the seat offset");
    }

    @Test
    void windowEdgeIsContinuousWhenBracketed() throws Exception {
        SceneDef s = scene();
        SceneDef.ActorDef boy = s.actors().get(1);
        for (float edge : new float[]{40, 120}) {
            double[] before = Vehicle.root(s, boy, edge - 0.0005f);
            double[] at = Vehicle.root(s, boy, edge);
            double[] after = Vehicle.root(s, boy, edge + 0.05f);
            assertTrue(dist(before, at) < 0.02, "the root jumps " + dist(before, at) + " at " + edge);
            assertTrue(dist(at, after) < 0.05, "and keeps going smoothly after " + edge);
            // Sampled a tick apart either side the motion stays small: no frame lands on a blended key.
            for (float t = edge - 3; t <= edge + 3; t += 0.125f) {
                assertTrue(dist(Vehicle.root(s, boy, t), Vehicle.root(s, boy, t + 0.125f)) < 0.1, "step at " + t);
            }
        }
        // The rider layer eases in from the mount and out before the dismount.
        assertEquals(0, Vehicle.seat(s, boy, 40).weight(), 1e-6);
        assertEquals(1, Vehicle.seat(s, boy, 80).weight(), 1e-6);
        assertTrue(Vehicle.seat(s, boy, 119.9f).weight() < 0.01);
    }
}
