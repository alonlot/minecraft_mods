package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.pensieve.scene.HumanMotion;
import com.hogwartsmagic.pensieve.scene.PartTrack;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The client-side state of one scene actor: the puppet entity (never added to the level) and everything the
 * actor's timed actions have done to it so far. Advanced once per client tick by {@link MemoryActors#tick()}.
 * Poses, anims and limb tracks (Task 10) and casts (Task 11) hang their per-actor state off this class too.
 */
public final class ActorRuntime {
    public final SceneDef.ActorDef def;
    public final Entity entity;
    /** Same object as {@link #entity} when it is a living entity, else null. */
    public final LivingEntity living;
    public final boolean player;
    /** The actor's actions in time order. */
    final List<SceneDef.ActionCue> actions;

    /** Index of the next action not yet fired. */
    int nextAction;
    /** Scene time of the previous tick, or -1 before the first one. */
    float lastT = -1;
    double lastX;
    double lastZ;
    boolean placed;
    float bodyYaw;
    float headYaw;

    /** A rider ({@code ride}): the vehicle's pitch, roll and yaw it rocks with, this tick and the last (degrees). */
    float ridePitch, rideRoll, rideYaw, ridePitchO, rideRollO, rideYawO;
    /** On a broom this tick and the last. */
    boolean rideBroom, rideBroomO;
    /** Set by ActorPoser for the frame being drawn: the broom seat at frame time, or null when not on a broom. */
    com.hogwartsmagic.pensieve.scene.Vehicle.Seat broomSeat;
    /** The life layer's seat on a broom at frame time, 0 standing (landed) to 1 seated (flying): BroomFlight.seatFraction. */
    float frameRiderSeat;
    /** Per part: the life gain while on a broom (arms on the handle and legs astride it get none). */
    final float[] riderGain = new float[HumanMotion.PARTS];

    /** A recorded memory's track for this actor, or null in a story memory (see {@link RecordedActors}). */
    com.hogwartsmagic.pensieve.capture.Recording.Track track;
    /** Recorded actors: the next synched-data and equipment patch not yet applied, and whether decoding failed. */
    int nextData;
    int nextEquip;
    boolean patchesFailed;

    boolean visible = true;
    boolean stone;
    boolean dying;
    /** Set while a late joiner catches up on a die cue, so its poof is not replayed. */
    boolean skipPoof;
    int swingTicks = -1;
    /** Scene time the current use_item began, or -1. */
    float useStart = -1;
    /**
     * The item id last put in each slot (by the tick or by {@link MemoryActors#renderAll}'s per-frame sync), and
     * whether any action changes a worn item at all: with none, nothing is synced per frame.
     */
    final Map<String, String> wornItems = new java.util.HashMap<>();
    final boolean changesItems;

    /** Task 10: the last pose/anim cue applied, so a cue is only (re)started once. */
    public int appliedPoseCue = -1;
    public int appliedAnimCue = -1;

    /** The actor pose and animation cues in time order, and what each resolved to (null where unknown). */
    final List<SceneDef.Cue> poseCues;
    final List<SceneDef.Cue> animCues;
    PosePresets.Preset[] poseSets = new PosePresets.Preset[0];
    final AnimationDefinition[] animDefs;

    /** Player actors: the motion seed (from the id), the clip cues, and lazily the path odometer for the gait. */
    final long seed;
    final HumanMotion.Clips clips;
    private HumanMotion.Odometer odometer;
    /** Per humanoid part (HumanMotion order): how much life it gets; damped where the scene keys that part. */
    final float[] lifeGain = new float[HumanMotion.PARTS];

    private float[] crouchTimes;
    private boolean[] crouchPoses;

    /** How far into a crouch this actor is at scene time {@code t} (0 standing, 1 crouched), eased. */
    float crouchFraction(float t) {
        if (crouchTimes == null) {
            int n = 0;
            for (SceneDef.ActionCue c : actions) if ("sneak".equals(c.action()) || "stand".equals(c.action())) n++;
            crouchTimes = new float[n];
            crouchPoses = new boolean[n];
            int i = 0;
            for (SceneDef.ActionCue c : actions) {
                if ("sneak".equals(c.action()) || "stand".equals(c.action())) {
                    crouchTimes[i] = c.t();
                    crouchPoses[i++] = "sneak".equals(c.action());
                }
            }
        }
        return com.hogwartsmagic.pensieve.scene.EyeEase.fraction(crouchTimes, crouchPoses, t);
    }

    ActorRuntime(SceneDef.ActorDef def, Entity entity, boolean player, List<SceneDef.ActionCue> actions) {
        this.poseCues = byTime(def.poses());
        this.animCues = byTime(def.anims());
        this.animDefs = new AnimationDefinition[animCues.size()];
        for (int i = 0; i < animDefs.length; i++) {
            animDefs[i] = ActorAnimationLibrary.find(def.type(), animCues.get(i).name()).orElse(null);
        }
        this.def = def;
        this.entity = entity;
        this.living = entity instanceof LivingEntity l ? l : null;
        this.player = player;
        this.actions = actions;
        boolean changes = false;
        for (SceneDef.ActionCue c : actions) {
            String act = c.action();
            if ("equip".equals(act) || "hold".equals(act) || ("use_item".equals(act) && c.item() != null)) changes = true;
        }
        this.changesItems = changes;
        this.seed = HumanMotion.seed(def.id());
        this.clips = new HumanMotion.Clips(player ? animCues : List.of(), seed, def.path());
        for (int p = 0; p < HumanMotion.PARTS; p++) {
            PartTrack limb = limbTrack(def, HumanMotion.PART_NAMES.get(p));
            lifeGain[p] = limb == null ? 1f : limb.set() ? 0f : 0.35f;
            riderGain[p] = p >= HumanMotion.RIGHT_ARM ? 0f : lifeGain[p];
        }
    }

    private static PartTrack limbTrack(SceneDef.ActorDef def, String part) {
        for (Map.Entry<String, PartTrack> e : def.limbs().entrySet()) {
            if (part.equals(e.getKey()) || part.equals(PoseMath.snake(e.getKey()))) return e.getValue();
        }
        return null;
    }

    /** True while the procedural life layer runs: a living player actor that is not stone and not dying. */
    boolean lifeOn() {
        return player && def.life() && !stone && !dying;
    }

    HumanMotion.Odometer odometer() {
        if (odometer == null) {
            boolean windowed = def.ride() != null && !(Float.isInfinite(def.rideFrom()) && Float.isInfinite(def.rideUntil()));
            odometer = windowed ? new HumanMotion.Odometer(def.path(), def.scale(), def.rideFrom(), def.rideUntil())
                    : new HumanMotion.Odometer(def.path(), def.scale());
        }
        return odometer;
    }

    private static List<SceneDef.Cue> byTime(List<SceneDef.Cue> cues) {
        List<SceneDef.Cue> sorted = new ArrayList<>(cues);
        sorted.sort(Comparator.comparingDouble(SceneDef.Cue::t));
        return List.copyOf(sorted);
    }

    /** Look every pose cue up: the scene own poses first, then the built-in ones. */
    void resolvePoses(Map<String, Map<String, float[]>> scenePoses) {
        poseSets = new PosePresets.Preset[poseCues.size()];
        for (int i = 0; i < poseSets.length; i++) {
            String name = poseCues.get(i).name();
            Map<String, float[]> custom = scenePoses == null ? null : scenePoses.get(name);
            poseSets[i] = custom != null ? new PosePresets.Preset(custom, Map.of()) : PosePresets.get(name);
        }
    }

    /** True when the scene gave this actor a display name. */
    public boolean hasName() {
        return def.name() != null && !def.name().isBlank();
    }
}
