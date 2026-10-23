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
}
