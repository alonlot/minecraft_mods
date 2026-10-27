package com.hogwartsmagic.pensieve;

import com.google.gson.JsonParser;
import com.hogwartsmagic.entity.LivingStatueEntity;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.hogwartsmagic.pensieve.scene.SceneValidator;
import com.hogwartsmagic.pensieve.scene.StatueCues;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The memory Living Statue: its wake, crumble and blows as a pure function of the cues and the scene clock. */
class StatueCuesTest {
    private static final int WAKE = LivingStatueEntity.WAKE_TICKS, CRUMBLE = LivingStatueEntity.CRUMBLE_TICKS;

    private static SceneDef.Cue cue(float t, String name) throws Exception {
        return new SceneDef.Cue(t, name, 0f);
    }

    private static SceneDef.ActionCue swing(float t) throws Exception {
        return new SceneDef.ActionCue(t, "swing", null, null, null, null);
    }

    @Test
    void dormantBeforeTheFirstCue() throws Exception {
        assertEquals(new StatueCues.State(0, 0), StatueCues.at(List.of(), 100f));
        assertEquals(new StatueCues.State(0, 0), StatueCues.at(List.of(cue(40, "WAKE")), 39.9f));
    }

    @Test
    void wakeRisesOverTheRealWakeTicksAndStaysUp() throws Exception {
        List<SceneDef.Cue> cues = List.of(cue(40, "WAKE"));
        // The real counter is 1 on the tick of wake() and one more each tick up to WAKE_TICKS.
        assertEquals(1, StatueCues.at(cues, 40f).wakeTicks());
        assertEquals(1, StatueCues.at(cues, 40.9f).wakeTicks());
        assertEquals(11, StatueCues.at(cues, 50f).wakeTicks());
        assertEquals(WAKE, StatueCues.at(cues, 40f + WAKE - 1).wakeTicks());
        assertEquals(WAKE, StatueCues.at(cues, 1000f).wakeTicks());
        float last = 0;
        for (float t = 0; t < 120; t += 0.5f) {
            StatueCues.State s = StatueCues.at(cues, t);
            assertTrue(s.wakeTicks() >= last, "never falls back at " + t);
            last = s.wakeTicks();
            assertEquals(0, s.crumbleTicks());
        }
    }

    @Test
    void idleIsAwakeAtOnceAndWhatAFinishedWakeBecomes() throws Exception {
        assertEquals(new StatueCues.State(WAKE, 0), StatueCues.at(List.of(cue(10, "idle")), 10f));
        assertEquals(StatueCues.at(List.of(cue(0, "WAKE")), 500f), StatueCues.at(List.of(cue(0, "WAKE"), cue(200, "IDLE")), 500f));
    }

    @Test
    void crumbleFoldsOverTheCrumbleTicksAndHolds() throws Exception {
        List<SceneDef.Cue> cues = List.of(cue(0, "WAKE"), cue(100, "CRUMBLE"));
        assertEquals(new StatueCues.State(WAKE, 0), StatueCues.at(cues, 99.9f));
        assertEquals(new StatueCues.State(WAKE, 1), StatueCues.at(cues, 100f));
        assertEquals(new StatueCues.State(WAKE, 6), StatueCues.at(cues, 105f));
        assertEquals(new StatueCues.State(WAKE, CRUMBLE), StatueCues.at(cues, 100f + CRUMBLE));
        assertEquals(new StatueCues.State(WAKE, CRUMBLE), StatueCues.at(cues, 900f));
    }

    @Test
    void aWakeAfterACrumbleStartsOverAndCuesCanComeInAnyOrder() throws Exception {
        List<SceneDef.Cue> shuffled = List.of(cue(300, "WAKE"), cue(100, "CRUMBLE"), cue(0, "IDLE"));
        assertEquals(new StatueCues.State(WAKE, 0), StatueCues.at(shuffled, 50f));
        assertEquals(new StatueCues.State(WAKE, 3), StatueCues.at(shuffled, 102f));
        assertEquals(new StatueCues.State(1, 0), StatueCues.at(shuffled, 300f));
        assertEquals(new StatueCues.State(21, 0), StatueCues.at(shuffled, 320f));
    }

    @Test
    void blowsCycleThroughTheComboWithTheirOwnLengths() throws Exception {
        List<SceneDef.ActionCue> a = List.of(swing(100), swing(200), swing(300), swing(400));
        assertNull(StatueCues.swing(a, 99.9f));
        StatueCues.Swing s = StatueCues.swing(a, 100f);
        assertNotNull(s);
        assertEquals(0, s.combo());
        assertEquals(0, s.ticks());
        assertEquals(10, StatueCues.swing(a, 110.5f).ticks());
        assertNull(StatueCues.swing(a, 100f + StatueCues.SWING_TICKS[0]), "the blow is over");
        assertEquals(1, StatueCues.swing(a, 205f).combo());
        assertEquals(2, StatueCues.swing(a, 305f).combo());
        assertEquals(StatueCues.SWING_TICKS[2] - 1,
                StatueCues.swing(a, 300f + StatueCues.SWING_TICKS[2] - 0.5f).ticks());
        assertEquals(0, StatueCues.swing(a, 405f).combo(), "the fourth blow starts the combo over");
        // Other actions are not blows.
        assertNull(StatueCues.swing(List.of(new SceneDef.ActionCue(5, "hurt", null, null, null, null)), 6f));
    }

    private static final SceneNames NAMES = new SceneNames(Set.of("hogwartsmagic:living_statue", "player"), Set.of(),
            Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

    private static List<String> validate(String actor) throws Exception {
        SceneDef def = SceneParser.parse("t", JsonParser.parseString("{\"length\":400,\"actors\":[" + actor + "]}").getAsJsonObject());
        return SceneValidator.validate(def, NAMES);
    }

    @Test
    void validatorChecksTheStatueCueNames() throws Exception {
        assertEquals(List.of(), validate("{\"id\":\"s\",\"type\":\"hogwartsmagic:living_statue\",\"anims\":["
                + "{\"t\":0,\"name\":\"idle\"},{\"t\":20,\"name\":\"WAKE\"},{\"t\":200,\"name\":\"Crumble\"}],"
                + "\"actions\":[{\"t\":100,\"do\":\"swing\"}]}"));
        List<String> m = validate("{\"id\":\"s\",\"type\":\"hogwartsmagic:living_statue\",\"anims\":[{\"t\":0,\"name\":\"RISE\"}]}");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("actors[0].anims[0].name: unknown Living Statue cue 'RISE'") && m.get(0).contains("CRUMBLE"), m.get(0));
        assertEquals(List.of("IDLE", "WAKE", "CRUMBLE"), SceneNames.STATUE_ANIMS);
    }
}
