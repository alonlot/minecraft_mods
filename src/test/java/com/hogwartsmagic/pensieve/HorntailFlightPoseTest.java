package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.entity.HorntailFlightPose;
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
    }
}
