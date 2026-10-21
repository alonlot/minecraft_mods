package com.hogwartsmagic.pensieve;

import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.RoughSea;
import com.hogwartsmagic.pensieve.scene.Rowboat;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RowboatTest {

    /** The plan's review focus 5: the hull never sinks below or floats above the waves by more than 0.15. */
    @Test
    void boatRidesTheSurface() {
        Random r = new Random(7);
        double worst = 0;
        for (long seed = 0; seed < 6; seed++) {
            for (double intensity : new double[]{0.4, 0.75, 1.0}) {
                RoughSea.Sea sea = new RoughSea.Sea(0, 3, 0, 60, 60, intensity, 0f, 0, seed * 7919);
                for (int i = 0; i < 1500; i++) {
                    double x = r.nextDouble() * 80 - 40, z = r.nextDouble() * 80 - 40;
                    float t = r.nextFloat() * 3000f, yaw = r.nextFloat() * 720f - 360f;
                    float scale = i % 3 == 0 ? 0.75f : 1f;
                    Rowboat.Pose pose = Rowboat.ride(sea, x, 3, z, yaw, scale, t);
                    for (double[] p : Rowboat.WATERLINE) {
                        double[] local = {p[0] * scale, p[1] * scale, p[2] * scale};
                        double[] w = Rowboat.toWorld(pose, local);
                        double off = Math.abs(w[1] - sea.surface(w[0], w[2], t));
                        worst = Math.max(worst, off);
                    }
                }
            }
        }
        assertTrue(worst <= 0.15, "the hull leaves the surface by " + worst);
        assertTrue(worst > 0.01, "the waterline points do not test anything");
    }

    @Test
    void boatHeavesPitchesAndRolls() {
        RoughSea.Sea sea = new RoughSea.Sea(0, 0, 0, 60, 60, 1.0, 0f, 0, 42);
        double lo = 1e9, hi = -1e9, pitch = 0, roll = 0;
        for (float t = 0; t < 1200; t += 2) {
            Rowboat.Pose p = Rowboat.ride(sea, 3, 0, -4, 30f, 1f, t);
            lo = Math.min(lo, p.y());
            hi = Math.max(hi, p.y());
            pitch = Math.max(pitch, Math.abs(p.pitch()));
            roll = Math.max(roll, Math.abs(p.roll()));
            // The rotation is a proper rotation (orthonormal, determinant 1).
            double[] m = p.m();
            double det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6]);
            assertEquals(1.0, det, 1e-9);
        }
        assertTrue(hi - lo > 0.5, "heave range " + (hi - lo));
        assertTrue(pitch > 3, "pitch " + pitch);
        assertTrue(roll > 3, "roll " + roll);
        // No sea: the boat sits exactly on its path, level.
        Rowboat.Pose still = Rowboat.ride(null, 3, 1, -4, 30f, 1f, 50f);
        assertEquals(1.0, still.y(), 0);
        assertEquals(0.0, still.pitch(), 0);
        assertEquals(0.0, still.roll(), 0);
    }

    @Test
    void yawZeroFacesSouthLikeEveryActor() {
        Rowboat.Pose p = Rowboat.ride(null, 0, 0, 0, 0f, 1f, 0f);
        double[] bow = Rowboat.toWorld(p, new double[]{0, 0, 1});
        assertEquals(0, bow[0], 1e-12);
        assertEquals(1, bow[2], 1e-12);
        Rowboat.Pose east = Rowboat.ride(null, 0, 0, 0, -90f, 1f, 0f);
        assertEquals(1, Rowboat.toWorld(east, new double[]{0, 0, 1})[0], 1e-12);   // yaw -90 faces +X
    }

    private static final String SCENE = """
            {"length":"20s","radius":40,
             "actors":[
               {"id":"boat","type":"hogwartsmagic:memory_rowboat",
                "path":[{"t":0,"pos":[0,0,-10]},{"t":"10s","pos":[0,0,10],"ease":"linear"}],
                "look":[{"t":0,"yaw":0}]},
               {"id":"vernon","type":"player","ride":"boat","path":[{"t":0,"pos":[0,-0.352,0.15]}],
                "look":[{"t":0,"yaw":180}]},
               {"id":"petunia","type":"player","ride":"boat","path":[{"t":0,"pos":[0.44,-0.352,-1.3]}]}],
             "events":[%s]}""";

    private static SceneDef scene(String events) throws Exception {
        return SceneParser.parse("t", JsonParser.parseString(SCENE.formatted(events)).getAsJsonObject());
    }

    @Test
    void riderFollowsBoat() throws Exception {
        SceneDef s = scene("{\"t\":0,\"kind\":\"effect\",\"id\":\"rough_sea\",\"at\":[0,0,0],\"spread\":[30,0,30]}");
        SceneDef.ActorDef boat = s.actors().get(0), vernon = s.actors().get(1), petunia = s.actors().get(2);
        assertEquals("boat", vernon.ride());
        double lo = 1e9, hi = -1e9;
        for (float t = 0; t <= 400; t += 1.5f) {
            Rowboat.Pose b = Rowboat.place(s, boat, t);
            assertNotNull(b);
            for (SceneDef.ActorDef rider : new SceneDef.ActorDef[]{vernon, petunia}) {
                Rowboat.Seat seat = Rowboat.rider(s, rider, t);
                assertNotNull(seat);
                float[] off = rider.path().sample(t);
                double[] want = Rowboat.toWorld(b, new double[]{off[0], off[1], off[2]});
                assertEquals(want[0], seat.x(), 1e-9);
                assertEquals(want[1], seat.y(), 1e-9);
                assertEquals(want[2], seat.z(), 1e-9);
                assertEquals(b.pitch(), seat.pitch(), 0);
                assertEquals(b.roll(), seat.roll(), 0);
                assertEquals(b.yaw(), seat.boatYaw(), 0);
            }
            Rowboat.Seat p = Rowboat.rider(s, petunia, t);
            lo = Math.min(lo, p.y());
            hi = Math.max(hi, p.y());
        }
        // She heaves with the boat, and stays seated in it: never more than the seat drop plus 0.15 off the water.
        assertTrue(hi - lo > 0.3, "the rider does not heave: " + (hi - lo));
        // With the boat moving along +Z, she is carried along too.
        assertTrue(Rowboat.rider(s, petunia, 200).z() - Rowboat.rider(s, petunia, 0).z() > 19);
        // A rider's look yaw is relative to the boat: Vernon faces the stern.
        assertEquals(180f, Rowboat.rider(s, vernon, 30).yaw(), 1e-4);
        assertEquals(0f, Rowboat.rider(s, petunia, 30).yaw(), 1e-4);
    }

    @Test
    void riderOnStillWaterSitsAtTheOffset() throws Exception {
        SceneDef s = scene("");
        Rowboat.Seat p = Rowboat.rider(s, s.actors().get(2), 100);
        assertEquals(0.44, p.x(), 1e-6);
        assertEquals(-0.352, p.y(), 1e-6);
        assertEquals(-1.3, p.z(), 1e-6);     // the boat is at z 0 at 5 s
    }

    @Test
    void theBoatBindsToTheNearestRunningSeaThatCoversIt() throws Exception {
        String a = "{\"t\":0,\"kind\":\"effect\",\"id\":\"rough_sea\",\"at\":[0,0,0],\"spread\":[30,0,30]}";
        String b = "{\"t\":0,\"kind\":\"effect\",\"id\":\"rough_sea\",\"at\":[0,0,20],\"spread\":[30,0,30],\"intensity\":0.5}";
        String c = "{\"t\":\"15s\",\"kind\":\"effect\",\"id\":\"rough_sea\",\"at\":[0,0,8],\"spread\":[30,0,30]}";
        SceneDef s = scene(a + "," + b + "," + c);
        assertEquals(0.0, RoughSea.bind(s.events(), 10, 0, 2).cz(), 0);    // nearer a
        assertEquals(20.0, RoughSea.bind(s.events(), 10, 0, 15).cz(), 0);  // nearer b
        assertEquals(8.0, RoughSea.bind(s.events(), 300, 0, 6).cz(), 0);   // c has started and is nearest
        assertEquals(null, RoughSea.bind(s.events(), 10, 0, 60));          // outside every sea
    }

    @Test
    void oarsStrokeOnlyWhileTheBoatMoves() throws Exception {
        SceneDef s = scene("");
        SceneDef.ActorDef boat = s.actors().get(0);
        assertEquals(1f, Rowboat.strokeAmount(boat, 100), 1e-6);   // 1 block a second: rowing hard
        assertEquals(0f, Rowboat.strokeAmount(boat, 380), 1e-6);   // long since stopped
        Rowboat.Oar a = Rowboat.oar(0f, 1f), b = Rowboat.oar(0.5f, 1f), rest = Rowboat.oar(0.3f, 0f);
        assertTrue(a.sweep() > b.sweep() + 50, "the catch and the finish are a long sweep apart");
        assertEquals(Rowboat.oar(0.7f, 0f).sweep(), rest.sweep(), 1e-6);   // resting oars do not move
    }
}
