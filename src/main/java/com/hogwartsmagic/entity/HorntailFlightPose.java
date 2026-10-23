package com.hogwartsmagic.entity;

import net.minecraft.util.Mth;

/**
 * The Horntail model's flight inputs, as pure statics (common code: no client types). The model asks the entity for
 * them ({@link HungarianHorntailEntity#wingBeat} and the rest) and the wild dragon's entity answers with these
 * defaults, which are exactly the maths the model used to do inline, so a wild Horntail flies as it always has.
 * The memory dragon ({@link MemoryHorntailEntity}) answers from the scene instead: its wing phase and amplitude from
 * its path (pensieve/scene/DragonFlight), and no bank or climb pitch of the body, since its renderer turns the
 * whole dragon.
 */
public final class HorntailFlightPose {
    private HorntailFlightPose() {
    }

    /** The beat, -1 (wings up) to 1 (wings down): a fixed-rate sine of the entity's age. */
    public static float wingBeat(float ageInTicks, float rate) {
        return Mth.sin(ageInTicks * rate);
    }

    /** The wing amplitude: the model's own. */
    public static float wingAmp(float amp) {
        return amp;
    }

    /** The body's bank into a turn (radians, model zRot): from the yaw turned this tick. */
    public static float flightBank(float yRot, float yRotO) {
        return Mth.clamp(Mth.wrapDegrees(yRot - yRotO) * 0.055F, -0.7F, 0.7F);
    }

    /** The body's pitch with the climb (radians, model xRot), plus the beat's small heave. */
    public static float flightPitch(double deltaY, float beat) {
        return Mth.clamp((float) -deltaY * 0.55F, -0.45F, 0.45F) + beat * 0.05F;
    }

    /** The takeoff overlay's own beat: three hammering downbeats over the state, from its progress p (0 to 1). */
    public static float takeoffBeat(float p) {
        return Mth.sin(p * Mth.PI * 3.0F);
    }
}
