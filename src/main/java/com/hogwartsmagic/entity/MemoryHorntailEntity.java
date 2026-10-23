package com.hogwartsmagic.entity;

import com.hogwartsmagic.pensieve.scene.DragonFlight;
import com.hogwartsmagic.pensieve.scene.HorntailCues;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.Track;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The Hungarian Horntail as a Pensieve memory actor ({@code hogwartsmagic:memory_horntail}): the wild dragon's model and
 * animations, driven by the scene instead of an AI. It is a <b>client puppet</b>: MemoryActors makes one per actor and
 * never adds it to a level. If one is ever made on a server (a {@code /summon}), it discards itself on its first tick,
 * so no AI, fire or damage of the wild dragon ever runs for it.
 *
 * <p>Each frame the renderer ({@code client/MemoryHorntailRenderer}) sets {@link #frame} (the state and its cross-fade
 * from the {@code anims} cues, {@link HorntailCues}), {@link #wingPhase} and {@link #wingAmplitude} (from the path,
 * {@link DragonFlight}); the model poses from them through {@link MemoryHorntailPose}, and the getters and flight hooks
 * below answer the same way. MemoryActors places it each tick and sets {@link #pitch} and {@link #roll} (with their
 * previous values), which the renderer turns the whole dragon by about her body pivot. The renderer stores the drawn
 * mouth, {@link #snout()}, each frame for the fire. It also sets {@link #headYaw} and {@link #headPitch} from her
 * {@code limbs.head_look} channel and {@link #neckRoll} from her drawn roll, which the model reads for her head.
 */
public class MemoryHorntailEntity extends HungarianHorntailEntity {
    private static final HorntailCues.State REST = new HorntailCues.State("NONE", 0, false, 0);

    /** Nose-down pitch and right-side-down roll of the whole dragon in degrees, this tick and the last (DragonFlight). */
    public float pitch, roll, pitchO, rollO;
    /** The actor's cues and path, set by MemoryActors when it makes the puppet. */
    public List<SceneDef.Cue> anims = List.of();
    public Track path = Track.EMPTY;
    /** The cached wing phase of {@link #path}. */
    public DragonFlight.WingClock wingClock = new DragonFlight.WingClock(Track.EMPTY);

    /** What the model draws this frame (see the class comment). */
    public HorntailCues.Blend frame = new HorntailCues.Blend(REST, null, 1);
    /** The wing phase (radians) and amplitude this frame. */
    public double wingPhase;
    public float wingAmplitude = (float) DragonFlight.HOVER_AMP;
    /** Her {@code limbs.head_look} rot track (DragonFlight.headLook), set by MemoryActors; empty: none. */
    public Track headLook = Track.EMPTY;
    /** This frame's head look relative to her body (degrees, the model's netHeadYaw and headPitch). */
    public float headYaw, headPitch;
    /** This frame's roll for her neck to counter (radians, DragonFlight.neckRoll of her drawn roll). */
    public float neckRoll;

    /** The mouth drawn this frame, world space, and the head's forward direction there (unit); null before a frame. */
    private double[] snoutPoint, snoutDir;
    /** The render frame the snout was drawn in (the client's frame counter), so a stale one can be told apart. */
    private long snoutFrame = Long.MIN_VALUE;

    public MemoryHorntailEntity(EntityType<? extends MemoryHorntailEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        // A puppet: no goals at all.
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide) {
            // Only ever a client puppet: one made on a server (a /summon) leaves at once, before any AI runs.
            this.discard();
        }
        // Client puppets are never added to a level, so this does not run for them, and does nothing if it ever does.
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return true;
    }

    // ---- The scene-driven state, as the model reads it ------------------------------------------------------------

    /** This frame's model inputs for the state now (the model itself also poses the state it fades out of). */
    public MemoryHorntailPose pose() {
        HorntailCues.Blend f = this.frame;
        return new MemoryHorntailPose(f == null ? REST : f.now(), this.wingPhase, this.wingAmplitude, this.neckRoll);
    }

    @Override
    public AttackState getAttackState() {
        return pose().getAttackState();
    }

    @Override
    public int getStateTick() {
        return this.frame == null ? 0 : this.frame.now().stateTick();
    }

    @Override
    public float animStateTick() {
        return pose().animStateTick();
    }

    @Override
    public boolean isFlying() {
        return pose().isFlying();
    }

    @Override
    public boolean isRaging() {
        return false;
    }

    @Override
    public boolean isSweepingLeft() {
        return false;
    }

    @Override
    public float wingBeat(float ageInTicks, float rate) {
        return pose().wingBeat(ageInTicks, rate);
    }

    @Override
    public float wingAmp(float amp) {
        return pose().wingAmp(amp);
    }

    @Override
    public float takeoffBeat(float p) {
        return pose().takeoffBeat(p);
    }

    @Override
    public float wingBoneBeat(float ageInTicks, float rate, int bone) {
        return pose().wingBoneBeat(ageInTicks, rate, bone);
    }

    @Override
    public float takeoffBoneBeat(float p, int bone) {
        return pose().takeoffBoneBeat(p, bone);
    }

    @Override
    public float neckCounterRoll() {
        return this.neckRoll;
    }

    @Override
    public float airBreathBob(float ageInTicks) {
        return pose().airBreathBob(ageInTicks);
    }

    @Override
    public float flightBank() {
        return 0F;
    }

    @Override
    public float flightPitch(float beat) {
        return pose().flightPitch(beat);
    }

    // ---- The snout ------------------------------------------------------------------------------------------------

    /** Stores the mouth the renderer drew this frame (world space) and its forward direction. */
    public void setSnout(double[] point, double[] dir) {
        setSnout(point, dir, Long.MIN_VALUE);
    }

    /** As {@link #setSnout(double[], double[])}, stamped with the render frame it was drawn in. */
    public void setSnout(double[] point, double[] dir, long frame) {
        this.snoutPoint = point;
        this.snoutDir = dir;
        this.snoutFrame = frame;
    }

    /**
     * The snout as {@link #snout()} if it was drawn in render frame {@code frame}, else null: she was not drawn this
     * frame (hidden, or her renderer failed), so the stored mouth is an old one and the fire must not leave it.
     */
    public double[] snoutIn(long frame) {
        return frame == snoutFrame && frame != Long.MIN_VALUE ? snout() : null;
    }

    /**
     * The drawn mouth this frame: {@code {x, y, z, dx, dy, dz}}, world space, the direction the head faces (unit), or
     * null before the first frame.
     */
    public double[] snout() {
        if (snoutPoint == null || snoutDir == null) return null;
        return new double[]{snoutPoint[0], snoutPoint[1], snoutPoint[2], snoutDir[0], snoutDir[1], snoutDir[2]};
    }

    /** The whole dragon at any pitch and roll, wings spread, up to scale 4. */
    @Override
    public AABB getBoundingBoxForCulling() {
        double x = getX(), y = getY(), z = getZ();
        return new AABB(x - 40, y - 40, z - 40, x + 40, y + 40, z + 40);
    }
}
