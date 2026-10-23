package com.hogwartsmagic.entity;

import com.hogwartsmagic.pensieve.scene.HorntailCues;

import java.util.HashMap;
import java.util.Map;

/**
 * The memory dragon's model inputs for one state (see {@link MemoryHorntailEntity}): the state from her cues, the beat
 * from her wing phase and the amplitude from her path (pensieve/scene/HorntailCues, DragonFlight), no bank and no
 * climb pitch of the body (her renderer turns the whole dragon), never raging, the tail sweeping right.
 */
public final class MemoryHorntailPose implements HorntailPoseSource {
    private static final Map<String, HungarianHorntailEntity.AttackState> STATES = new HashMap<>();

    static {
        for (HungarianHorntailEntity.AttackState s : HungarianHorntailEntity.AttackState.values()) STATES.put(s.name(), s);
    }

    private final HorntailCues.State state;
    private final float beat, amp;

    /** {@code wingPhase} in radians: the beat is its sine. */
    public MemoryHorntailPose(HorntailCues.State state, double wingPhase, float amp) {
        this.state = state;
        this.beat = (float) Math.sin(wingPhase);
        this.amp = amp;
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

    @Override
    public float wingBeat(float ageInTicks, float rate) {
        return beat;
    }

    @Override
    public float wingAmp(float ignored) {
        return amp;
    }

    @Override
    public float takeoffBeat(float p) {
        return beat;
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
}
