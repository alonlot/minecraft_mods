package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.pensieve.scene.SceneDef;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

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
    int swingTicks = -1;
    /** Scene time the current use_item began, or -1. */
    float useStart = -1;

    /** Task 10: the last pose/anim cue applied, so a cue is only (re)started once. */
    public int appliedPoseCue = -1;
    public int appliedAnimCue = -1;

    ActorRuntime(SceneDef.ActorDef def, Entity entity, boolean player, List<SceneDef.ActionCue> actions) {
        this.def = def;
        this.entity = entity;
        this.living = entity instanceof LivingEntity l ? l : null;
        this.player = player;
        this.actions = actions;
    }

    /** True when the scene gave this actor a display name. */
    public boolean hasName() {
        return def.name() != null && !def.name().isBlank();
    }
}
