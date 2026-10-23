package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.pensieve.scene.HorntailCues;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorntailCuesTest {
    private static SceneDef.Cue cue(float t, String name) {
        return new SceneDef.Cue(t, name, 0);
    }

    @Test
    void overlayEndsReturnsToFlight() {
        List<SceneDef.Cue> air = List.of(cue(0, "FLY"), cue(20, "AIR_BREATH"));
        HorntailCues.State during = HorntailCues.at(air, 20 + HungarianHorntailEntity.AIR_BREATH_DURATION - 0.5);
        assertEquals("AIR_BREATH", during.name());
        assertTrue(during.flying());
        HorntailCues.State after = HorntailCues.at(air, 20 + HungarianHorntailEntity.AIR_BREATH_DURATION + 0.5);
        assertEquals("FLY", after.name());
        assertTrue(after.flying());
        // On the ground a one-shot ends in the rest.
        List<SceneDef.Cue> ground = List.of(cue(0, "IDLE"), cue(10, "ROAR"));
        assertEquals("ROAR", HorntailCues.at(ground, 10 + HungarianHorntailEntity.ROAR_DURATION - 1).name());
        HorntailCues.State rest = HorntailCues.at(ground, 10 + HungarianHorntailEntity.ROAR_DURATION + 1);
        assertEquals("NONE", rest.name());
        assertFalse(rest.flying());
        // The takeoff lifts her: after it, she flies.
        List<SceneDef.Cue> up = List.of(cue(0, "IDLE"), cue(5, "TAKEOFF"));
        assertFalse(HorntailCues.at(up, 4).flying());
        assertTrue(HorntailCues.at(up, 5).flying());
        assertEquals("FLY", HorntailCues.at(up, 5 + HungarianHorntailEntity.TAKEOFF_DURATION + 1).name());
    }

    @Test
    void landClearsFlyingAtItsEnd() {
        List<SceneDef.Cue> c = List.of(cue(0, "FLY"), cue(40, "LAND"));
        assertTrue(HorntailCues.at(c, 39).flying());
        HorntailCues.State landing = HorntailCues.at(c, 40 + HungarianHorntailEntity.LAND_DURATION - 0.25);
        assertEquals("LAND", landing.name());
        assertTrue(landing.flying(), "she flares in the air until the landing is over");
        HorntailCues.State down = HorntailCues.at(c, 40 + HungarianHorntailEntity.LAND_DURATION);
        assertEquals("NONE", down.name());
        assertFalse(down.flying());
        // A cue before the landing ends: she is down by then.
        List<SceneDef.Cue> cut = List.of(cue(0, "FLY"), cue(40, "LAND"), cue(50, "ROAR"));
        HorntailCues.State roar = HorntailCues.at(cut, 51);
        assertEquals("ROAR", roar.name());
        assertFalse(roar.flying());
        assertEquals("NONE", HorntailCues.at(cut, 50 + HungarianHorntailEntity.ROAR_DURATION + 1).name());
    }

    @Test
    void idleGrounds() {
        List<SceneDef.Cue> c = List.of(cue(0, "FLY"), cue(30, "BITE"), cue(60, "IDLE"));
        assertTrue(HorntailCues.at(c, 35).flying(), "a bite keeps the flying flag");
        assertEquals("BITE", HorntailCues.at(c, 35).name());
        HorntailCues.State idle = HorntailCues.at(c, 61);
        assertEquals("NONE", idle.name());
        assertFalse(idle.flying());
        // Before any cue she is grounded at rest.
        HorntailCues.State before = HorntailCues.at(List.of(cue(10, "FLY")), 5);
        assertEquals("NONE", before.name());
        assertFalse(before.flying());
        assertEquals(0, before.stateTick());
    }

    @Test
    void cueNamesIgnoreCase() {
        List<SceneDef.Cue> c = List.of(cue(0, "fly"), cue(10, "Air_Breath"), cue(100, "idle"));
        assertEquals("FLY", HorntailCues.at(c, 5).name());
        assertTrue(HorntailCues.at(c, 5).flying());
        assertEquals("AIR_BREATH", HorntailCues.at(c, 12).name());
        assertEquals("NONE", HorntailCues.at(c, 101).name());
        assertEquals(HungarianHorntailEntity.AIR_BREATH_DURATION, HorntailCues.duration("AIR_BREATH"));
        assertEquals(-1, HorntailCues.duration("FLY"));
    }

    @Test
    void stateTickCountsFromCue() {
        List<SceneDef.Cue> c = List.of(cue(0, "IDLE"), cue(12.5f, "BREATH_WINDUP"), cue(34.5f, "BREATH"));
        HorntailCues.State s = HorntailCues.at(c, 20);
        assertEquals("BREATH_WINDUP", s.name());
        assertEquals(7, s.stateTick());
        assertEquals(7.5, s.time(), 1e-6);
        HorntailCues.State b = HorntailCues.at(c, 34.5f + 10.75f);
        assertEquals("BREATH", b.name());
        assertEquals(10, b.stateTick());
        assertEquals(10.75, b.time(), 1e-4);
        // The fallback after a one-shot counts from the one-shot's end.
        HorntailCues.State after = HorntailCues.at(c, 34.5f + HungarianHorntailEntity.BREATH_DURATION + 3);
        assertEquals("NONE", after.name());
        assertEquals(3, after.stateTick());
    }

    @Test
    void changesCrossFade() {
        List<SceneDef.Cue> c = List.of(cue(0, "IDLE"), new SceneDef.Cue(20, "ROAR", 10));
        HorntailCues.Blend start = HorntailCues.blendAt(c, 20);
        assertEquals("ROAR", start.now().name());
        assertEquals("NONE", start.before().name());
        assertEquals(0, start.weight(), 1e-9);
        HorntailCues.Blend mid = HorntailCues.blendAt(c, 25);
        assertEquals(0.5, mid.weight(), 1e-9);
        assertNull(HorntailCues.blendAt(c, 30).before());
        assertEquals(1, HorntailCues.blendAt(c, 30).weight(), 1e-9);
        // The end of a one-shot fades over the default 6 ticks from its last frame.
        double end = 20 + HungarianHorntailEntity.ROAR_DURATION;
        HorntailCues.Blend out = HorntailCues.blendAt(c, end + 3);
        assertEquals("NONE", out.now().name());
        assertEquals("ROAR", out.before().name());
        assertTrue(out.before().time() > HungarianHorntailEntity.ROAR_DURATION, "the roar holds its last frame");
        assertEquals(0.5, out.weight(), 1e-9);
        assertNull(HorntailCues.blendAt(c, end + HorntailCues.DEFAULT_BLEND).before());
        // The weight rises monotonically and smoothly.
        double last = -1;
        for (double t = 20; t <= 30; t += 0.1) {
            double w = HorntailCues.blendAt(c, t).weight();
            assertTrue(w >= last - 1e-12);
            assertTrue(w - last < 0.03 || last < 0, "step at " + t);
            last = w;
        }
    }
}
