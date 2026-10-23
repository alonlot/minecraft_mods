package com.hogwartsmagic.entity;

/**
 * What the Horntail model reads to pose itself (common code: no client types). The wild dragon's entity is one
 * ({@link HungarianHorntailEntity}: its synced state, with {@link HorntailFlightPose}'s flight maths); the memory dragon
 * poses from {@link MemoryHorntailPose} (its scene state, wing phase and amplitude).
 */
public interface HorntailPoseSource {
    boolean isFlying();

    HungarianHorntailEntity.AttackState getAttackState();

    /** The state's age the overlays animate by, in ticks. */
    float animStateTick();

    boolean isSweepingLeft();

    boolean isRaging();

    /** The flight beat, -1 (wings up) to 1 (wings down), for the model's beat rate in rad/tick. */
    float wingBeat(float ageInTicks, float rate);

    /** The flight beat's amplitude, given the model's own for the state. */
    float wingAmp(float amp);

    /** The takeoff overlay's beat at its progress p (0 to 1). */
    float takeoffBeat(float p);

    /** The body's bank in flight (radians, model zRot). */
    float flightBank();

    /** The body's pitch in flight (radians, model xRot), given the beat. */
    float flightPitch(float beat);

    /**
     * One wing bone's flight beat (bone 0 the humerus, 1 the forearm, 2 the finger: DragonFlight.HUMERUS and so on).
     * By default every bone beats with {@link #wingBeat}, as the wild dragon always has; the memory dragon lags the
     * outer bones.
     */
    default float wingBoneBeat(float ageInTicks, float rate, int bone) {
        return wingBeat(ageInTicks, rate);
    }

    /** One wing bone's takeoff beat at the takeoff's progress p; by default {@link #takeoffBeat} for every bone. */
    default float takeoffBoneBeat(float p, int bone) {
        return takeoffBeat(p);
    }

    /**
     * A roll (radians, as the model's body zRot) the neck counters in level flight on top of the body's own bank: 0
     * for the wild dragon, which banks its body; the memory dragon's whole-body roll, which her renderer applies.
     */
    default float neckCounterRoll() {
        return 0F;
    }

    /** The body's bob while breathing fire in the air, -1 to 1 ({@link HorntailFlightPose#airBreathBob}). */
    default float airBreathBob(float ageInTicks) {
        return HorntailFlightPose.airBreathBob(ageInTicks);
    }
}
