package com.hogwartsmagic.pensieve;

import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.hogwartsmagic.pensieve.scene.SceneValidator;
import com.hogwartsmagic.pensieve.scene.SmokeFlight;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Smoke flight: the smoke / unsmoke timeline and the ribbon, pure functions of the actions, the path and the clock. */
class SmokeFlightTest {
    private static SceneDef.ActionCue act(float t, String what) throws Exception {
        return new SceneDef.ActionCue(t, what, null, null, null, null);
    }

    @Test
    void smokeIsOnFromSmokeUntilTheNextUnsmoke() throws Exception {
        SmokeFlight.Timeline tl = SmokeFlight.Timeline.of(List.of(act(10, "smoke"), act(50, "unsmoke"), act(80, "smoke")));
        assertNotNull(tl);
        assertEquals(2, tl.periods());
        assertFalse(tl.on(0));
        assertFalse(tl.on(9.99f));
        assertTrue(tl.on(10));
        assertTrue(tl.on(49.99f));
        assertFalse(tl.on(50), "off from the unsmoke itself");
        assertFalse(tl.on(79));
        assertTrue(tl.on(80));
        assertTrue(tl.on(10000), "an open smoke runs to the end of the scene");
    }

    @Test
    void cuesInAnyOrderAndRedundantOnesAreHarmless() throws Exception {
        SmokeFlight.Timeline tl = SmokeFlight.Timeline.of(List.of(act(60, "unsmoke"), act(5, "unsmoke"), act(20, "smoke"),
                act(30, "smoke"), act(10, "hurt")));
        assertEquals(1, tl.periods());
        assertFalse(tl.on(19));
        assertTrue(tl.on(20));
        assertTrue(tl.on(59));
        assertFalse(tl.on(60));
        assertNull(SmokeFlight.Timeline.of(List.of(act(5, "unsmoke"), act(10, "die"))), "no smoke, no timeline");
    }

    private static SceneDef.ActorDef actor(String path) throws Exception {
        SceneDef s = SceneParser.parse("t", JsonParser.parseString(
                "{\"length\":400,\"actors\":[{\"id\":\"d\",\"type\":\"player\",\"path\":" + path + "}]}").getAsJsonObject());
        return s.actors().get(0);
    }

    /** A flier along +x at one block a tick from t 0 to 200. */
    private static final String FLIGHT = "[{\"t\":0,\"pos\":[0,10,0],\"ease\":\"linear\"},{\"t\":200,\"pos\":[200,10,0],\"ease\":\"linear\"}]";

    @Test
    void theRibbonReachesBackOneSecondAndStartsAtTheSmoke() throws Exception {
        SceneDef.ActorDef d = actor(FLIGHT);
        SmokeFlight.Timeline tl = SmokeFlight.Timeline.of(List.of(act(40, "smoke"), act(120, "unsmoke")));
        float[] out = new float[SmokeFlight.MAX_POINTS * SmokeFlight.STRIDE];
        assertEquals(0, SmokeFlight.trail(d, tl, 39f, out), "nothing before the smoke");
        // Just begun: a single point, the head.
        assertEquals(1, SmokeFlight.trail(d, tl, 40f, out));
        // Half a second in: the head and its samples back to the smoke's start (every STEP ticks).
        int n = SmokeFlight.trail(d, tl, 50f, out);
        assertEquals(6, n);
        assertEquals(50f, out[0], 1e-3);
        assertEquals(10f + SmokeFlight.BODY_MIDDLE, out[1], 1e-4);
        assertEquals(0f, out[3], 1e-6, "the head is age 0");
        assertEquals(40f, out[(n - 1) * SmokeFlight.STRIDE], 1e-3, "back to the smoke's start");
        assertEquals(10f, out[(n - 1) * SmokeFlight.STRIDE + 3], 1e-4);
        // Well into it: a full second, newest first, ages rising by the step.
        n = SmokeFlight.trail(d, tl, 100f, out);
        assertEquals(SmokeFlight.MAX_POINTS, n);
        for (int k = 0; k < n; k++) {
            assertEquals(100f - k * SmokeFlight.STEP, out[k * SmokeFlight.STRIDE], 1e-3);
            assertEquals(k * SmokeFlight.STEP, out[k * SmokeFlight.STRIDE + 3], 1e-4);
        }
        assertEquals(SmokeFlight.TRAIL_TICKS, out[(n - 1) * SmokeFlight.STRIDE + 3], 1e-4);
    }

    @Test
    void afterUnsmokeTheRibbonStaysPutAndFadesOutOverASecond() throws Exception {
        SceneDef.ActorDef d = actor(FLIGHT);
        SmokeFlight.Timeline tl = SmokeFlight.Timeline.of(List.of(act(40, "smoke"), act(120, "unsmoke")));
        float[] out = new float[SmokeFlight.MAX_POINTS * SmokeFlight.STRIDE];
        int n = SmokeFlight.trail(d, tl, 130f, out);
        assertTrue(n >= 2);
        assertEquals(120f, out[0], 1e-3, "the head is where the smoke ended");
        assertEquals(10f, out[3], 1e-4, "aged by the ticks since");
        assertTrue(SmokeFlight.taper(out[3]) < 1f && SmokeFlight.taper(out[3]) > 0f);
        assertEquals(0, SmokeFlight.trail(d, tl, 120f + SmokeFlight.TRAIL_TICKS, out), "gone after a second");
        assertEquals(0, SmokeFlight.trail(d, tl, 300f, out));
        assertEquals(1f, SmokeFlight.taper(0f), 1e-6);
        assertEquals(0f, SmokeFlight.taper(SmokeFlight.TRAIL_TICKS), 1e-6);
    }

    @Test
    void aCutInThePathEndsTheRibbonThere() throws Exception {
        SceneDef.ActorDef d = actor("[{\"t\":0,\"pos\":[0,10,0],\"ease\":\"linear\"},{\"t\":50,\"pos\":[0,10,0],\"ease\":\"linear\"},"
                + "{\"t\":50.001,\"pos\":[100,10,0],\"ease\":\"linear\"},{\"t\":200,\"pos\":[100,10,0],\"ease\":\"linear\"}]");
        SmokeFlight.Timeline tl = SmokeFlight.Timeline.of(List.of(act(0, "smoke")));
        float[] out = new float[SmokeFlight.MAX_POINTS * SmokeFlight.STRIDE];
        int n = SmokeFlight.trail(d, tl, 56f, out);
        assertTrue(n > 0 && n < SmokeFlight.MAX_POINTS, "stops at the jump: " + n);
        for (int k = 0; k < n; k++) assertEquals(100f, out[k * SmokeFlight.STRIDE], 1e-3);
    }

    private static final SceneNames NAMES = new SceneNames(Set.of("player"), Set.of(), Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(), Set.of(), Set.of());

    @Test
    void validatorKnowsTheActions() throws Exception {
        assertTrue(SceneNames.ACTIONS.contains("smoke") && SceneNames.ACTIONS.contains("unsmoke"));
        SceneDef def = SceneParser.parse("t", JsonParser.parseString("{\"length\":100,\"actors\":[{\"id\":\"d\",\"type\":\"player\","
                + "\"actions\":[{\"t\":10,\"do\":\"smoke\"},{\"t\":60,\"do\":\"unsmoke\"}]}]}").getAsJsonObject());
        assertEquals(List.of(), SceneValidator.validate(def, NAMES));
        def = SceneParser.parse("t", JsonParser.parseString("{\"length\":100,\"actors\":[{\"id\":\"d\",\"type\":\"player\","
                + "\"actions\":[{\"t\":10,\"do\":\"fume\"}]}]}").getAsJsonObject());
        List<String> m = SceneValidator.validate(def, NAMES);
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("unknown action 'fume'") && m.get(0).contains("unsmoke"), m.get(0));
    }
}
