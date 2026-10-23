package com.hogwartsmagic.entity;

import com.hogwartsmagic.pensieve.scene.DragonFlight;
import com.hogwartsmagic.pensieve.scene.HorntailCues;

import java.util.HashMap;
import java.util.Map;

/**
 * The memory dragon's model inputs for one state (see {@link MemoryHorntailEntity}): the state from her cues, the beat
 * of each wing bone from her wing phase (shaped and lagged by DragonFlight.boneBeat) and the amplitude from her path
 * (pensieve/scene/HorntailCues, DragonFlight), no bank and no climb pitch of the body (her renderer turns the whole
 * dragon), her whole-body roll for the neck to counter, never raging, the tail sweeping right.
 */
public final class MemoryHorntailPose implements HorntailPoseSource {
    private static final Map<String, HungarianHorntailEntity.AttackState> STATES = new HashMap<>();

    static {
        for (HungarianHorntailEntity.AttackState s : HungarianHorntailEntity.AttackState.values()) STATES.put(s.name(), s);
    }

    private final HorntailCues.State state;
    private final float amp, neckRoll;
    /** The humerus, forearm and finger beats. */
    private final float[] beats = new float[3];

    /** {@code wingPhase} in radians, level (no roll for the neck to counter). */
    public MemoryHorntailPose(HorntailCues.State state, double wingPhase, float amp) {
        this(state, wingPhase, amp, 0F);
    }

    /** {@code neckRoll}: her whole-body roll as DragonFlight.neckRoll gives it (radians). */
    public MemoryHorntailPose(HorntailCues.State state, double wingPhase, float amp, float neckRoll) {
        this.state = state;
        this.amp = amp;
        this.neckRoll = neckRoll;
        for (int b = 0; b < 3; b++) beats[b] = (float) DragonFlight.boneBeat(wingPhase, b);
    }

    /** The attack state of a cue state name ({@code NONE} for one the dragon does not know). */
    public static HungarianHorntailEntity.AttackState attackState(String name) {
        HungarianHorntailEntity.AttackState s = STATES.get(name);
        return s == null ? HungarianHorntailEntity.AttackState.NONE : s;
    }

    @Override
    public boolean isFlying() {
        return state.flying();
    }

    @Override
    public HungarianHorntailEntity.AttackState getAttackState() {
        return attackState(state.name());
    }

    @Override
    public float animStateTick() {
        return (float) state.time();
    }

    @Override
    public boolean isSweepingLeft() {
        return false;
    }

    @Override
    public boolean isRaging() {
        return false;
    }

    /** The humerus beat (the tail, the heave and the air breath's bob follow it too). */
    @Override
    public float wingBeat(float ageInTicks, float rate) {
        return beats[0];
    }

    @Override
    public float wingBoneBeat(float ageInTicks, float rate, int bone) {
        return beats[Math.max(0, Math.min(2, bone))];
    }

    @Override
    public float wingAmp(float ignored) {
        return amp;
    }

    /** The takeoff beats on the scene phase too, scaled by DragonFlight.TAKEOFF_GAIN (its own gains are big). */
    @Override
    public float takeoffBeat(float p) {
        return beats[0] * (float) DragonFlight.TAKEOFF_GAIN;
    }

    @Override
    public float takeoffBoneBeat(float p, int bone) {
        return beats[Math.max(0, Math.min(2, bone))] * (float) DragonFlight.TAKEOFF_GAIN;
    }

    @Override
    public float flightBank() {
        return 0F;
    }

    /** Only the beat's small heave (the wild dragon's {@code beat * 0.05}); the climb pitch is the whole-body pitch. */
    @Override
    public float flightPitch(float beat) {
        return beat * 0.05F;
    }

    @Override
    public float neckCounterRoll() {
        return neckRoll;
    }

    /** Her air breath bobs with her own wingbeat, not the wild dragon's fixed rate. */
    @Override
    public float airBreathBob(float ageInTicks) {
        return beats[0];
    }
}
