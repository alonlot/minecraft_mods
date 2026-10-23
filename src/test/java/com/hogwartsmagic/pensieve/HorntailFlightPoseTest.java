package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.entity.HorntailFlightPose;
import com.hogwartsmagic.entity.HorntailPoseSource;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The model hooks' defaults are the maths HungarianHorntailModel did inline, so a wild Horntail flies as before. */
class HorntailFlightPoseTest {
    @Test
    void defaultsMatchTheOldMaths() {
        for (float age = 0; age < 400; age += 3.7f) {
            for (float rate : new float[]{0.26F, 0.42F}) {
                assertEquals(Mth.sin(age * rate), HorntailFlightPose.wingBeat(age, rate), 0f);
            }
        }
        for (float amp : new float[]{0.60F, 0.85F}) assertEquals(amp, HorntailFlightPose.wingAmp(amp), 0f);
        for (float yaw = -400; yaw < 400; yaw += 13.3f) {
            for (float d : new float[]{-200, -30, -2.5f, 0, 1, 9, 40, 190}) {
                float yawO = yaw - d;
                float old = Mth.clamp(Mth.wrapDegrees(yaw - yawO) * 0.055F, -0.7F, 0.7F);
                assertEquals(old, HorntailFlightPose.flightBank(yaw, yawO), 0f);
            }
        }
        for (double dy = -2; dy <= 2; dy += 0.037) {
            for (float beat = -1; beat <= 1; beat += 0.25f) {
                float old = Mth.clamp((float) -dy * 0.55F, -0.45F, 0.45F) + beat * 0.05F;
                assertEquals(old, HorntailFlightPose.flightPitch(dy, beat), 0f);
            }
        }
        for (float p = 0; p <= 1; p += 0.01f) {
            assertEquals(Mth.sin(p * Mth.PI * 3.0F), HorntailFlightPose.takeoffBeat(p), 0f);
        }
        for (float age = 0; age < 400; age += 3.7f) {
            assertEquals(Mth.sin(age * 0.42F), HorntailFlightPose.airBreathBob(age), 0f);
        }
        // The interface's defaults: every wing bone beats with the humerus, no neck counter-roll, the old bob.
        HorntailPoseSource wild = new HorntailPoseSource() {
            public boolean isFlying() { return true; }
            public HungarianHorntailEntity.AttackState getAttackState() { return HungarianHorntailEntity.AttackState.NONE; }
            public float animStateTick() { return 0; }
            public boolean isSweepingLeft() { return false; }
            public boolean isRaging() { return false; }
            public float wingBeat(float ageInTicks, float rate) { return HorntailFlightPose.wingBeat(ageInTicks, rate); }
            public float wingAmp(float amp) { return amp; }
            public float takeoffBeat(float p) { return HorntailFlightPose.takeoffBeat(p); }
            public float flightBank() { return 0; }
            public float flightPitch(float beat) { return 0; }
        };
        for (float age = 0; age < 200; age += 3.7f) {
            for (int bone = 0; bone < 3; bone++) {
                assertEquals(wild.wingBeat(age, 0.26F), wild.wingBoneBeat(age, 0.26F, bone), 0f);
                assertEquals(wild.takeoffBeat(age / 200f), wild.takeoffBoneBeat(age / 200f, bone), 0f);
            }
            assertEquals(Mth.sin(age * 0.42F), wild.airBreathBob(age), 0f);
        }
        assertEquals(0f, wild.neckCounterRoll(), 0f);
    }
}
