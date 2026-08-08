package com.hogwartsmagic.entity;

import net.minecraft.world.entity.AnimationState;

/**
 * Implemented by the goblin entities so {@code GoblinModel} can drive the shared
 * animation set without doing {@code instanceof} checks in the render path.
 *
 * <p>{@link AnimationState} lives in the common entity package, so nothing here
 * pulls client-only classes onto a dedicated server.
 */
public interface GoblinAnimated {

    /** Always-running ambient loop: breathing, slow looking about, ear twitches. */
    AnimationState goblinIdleAnimationState();

    /** Standing-still busy-work; only played when {@link #goblinCountsCoins()}. */
    AnimationState goblinBusyAnimationState();

    /** One-shot swing, started from a broadcast entity event. */
    AnimationState goblinAttackAnimationState();

    /**
     * Tellers fidget with a ledger and a handful of coins when they stand still;
     * guards stand at attention instead.
     */
    boolean goblinCountsCoins();

    /** Guards carry a blade in hand, which the renderer draws on the swinging arm. */
    boolean goblinHoldsWeapon();
}
