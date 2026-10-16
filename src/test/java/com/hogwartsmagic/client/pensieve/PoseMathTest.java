package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PoseMathTest {
    @Test
    void camelCaseBecomesSnakeCase() {
        assertEquals("right_arm", PoseMath.snake("rightArm"));
        assertEquals("right_hind_leg", PoseMath.snake("rightHindLeg"));
        assertEquals("head", PoseMath.snake("head"));
        assertEquals("left_sleeve", PoseMath.snake("left_sleeve"));
        assertEquals("leg_0", PoseMath.snake("leg0"));
    }

    @Test
    void presetsAreExactlyTheSceneVocabulary() {
        assertEquals(SceneNames.POSE_PRESETS, PosePresets.names());
        for (String name : SceneNames.POSE_PRESETS) assertNotNull(PosePresets.get(name), name);
        assertNull(PosePresets.get("bogus"));
    }

    @Test
    void presetVectorsAreThreeLong() {
        for (String name : PosePresets.names()) {
            PosePresets.get(name).rot().forEach((part, v) -> assertEquals(3, v.length, name + "." + part));
            PosePresets.get(name).offset().forEach((part, v) -> assertEquals(3, v.length, name + "." + part));
        }
    }

    @Test
    void poseBlendsFromTheModelToThePose() {
        float nan = Float.NaN;
        assertEquals(10f, PoseMath.blendPose(10, nan, -90, 0f), 1e-5f);
        assertEquals(-40f, PoseMath.blendPose(10, nan, -90, 0.5f), 1e-5f);
        assertEquals(-90f, PoseMath.blendPose(10, nan, -90, 1f), 1e-5f);
        // From the previous pose, not from the base.
        assertEquals(-60f, PoseMath.blendPose(10, -90, -30, 0.5f), 1e-5f);
        // A part the new pose lets go of returns to the base.
        assertEquals(-40f, PoseMath.blendPose(10, -90, nan, 0.5f), 1e-5f);
    }

    @Test
    void cueWeightRampsOverTheBlendAndDefaultsToFiveTicks() {
        SceneDef.Cue explicit = new SceneDef.Cue(10, "kneel", 20);
        assertEquals(0f, PoseMath.weight(explicit, 10, 5), 1e-5f);
        assertEquals(0.5f, PoseMath.weight(explicit, 20, 5), 1e-5f);
        assertEquals(1f, PoseMath.weight(explicit, 99, 5), 1e-5f);
        SceneDef.Cue unspecified = new SceneDef.Cue(10, "kneel", 0);
        assertEquals(0.5f, PoseMath.weight(unspecified, 12.5f, PoseMath.DEFAULT_POSE_BLEND), 1e-5f);
        assertEquals(1f, PoseMath.weight(unspecified, 10, 0), 1e-5f);
    }

    @Test
    void activeCueIsTheLatestOneThatHasStarted() {
        List<SceneDef.Cue> cues = List.of(new SceneDef.Cue(5, "a", 0), new SceneDef.Cue(20, "b", 0));
        assertEquals(-1, PoseMath.activeCue(cues, 4.9f));
        assertEquals(0, PoseMath.activeCue(cues, 5));
        assertEquals(0, PoseMath.activeCue(cues, 19));
        assertEquals(1, PoseMath.activeCue(cues, 20));
        assertEquals(-1, PoseMath.activeCue(List.of(), 50));
    }

    @Test
    void limbAddsByDefaultAndReplacesInSetMode() {
        assertEquals(-60f, PoseMath.limb(30, -90, false), 1e-5f);
        assertEquals(-90f, PoseMath.limb(30, -90, true), 1e-5f);
    }
}
