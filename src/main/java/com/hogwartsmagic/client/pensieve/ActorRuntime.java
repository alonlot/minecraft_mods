package com.hogwartsmagic.client.pensieve;

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

    boolean visible = true;
    boolean stone;
    boolean dying;
    /** Set while a late joiner catches up on a die cue, so its poof is not replayed. */
    boolean skipPoof;
    int swingTicks = -1;
    /** Scene time the current use_item began, or -1. */
    float useStart = -1;

    /** Task 10: the last pose/anim cue applied, so a cue is only (re)started once. */
    public int appliedPoseCue = -1;
    public int appliedAnimCue = -1;

    /** The actor pose and animation cues in time order, and what each resolved to (null where unknown). */
    final List<SceneDef.Cue> poseCues;
    final List<SceneDef.Cue> animCues;
    PosePresets.Preset[] poseSets = new PosePresets.Preset[0];
    final AnimationDefinition[] animDefs;

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
