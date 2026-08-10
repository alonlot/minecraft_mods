package com.hogwartsmagic.client.model;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Keyframe clips for the Giant, exported from blockbench_models/giant.bbmodel
 * through the Modded Entity animation codec.
 *
 * <p>Bone names here must match the {@code addOrReplaceChild} names in
 * {@link GiantModel}; a mismatch fails silently at runtime.
 */
public class GiantAnimations {

    public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(5.0F).looping()
    .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.25F, KeyframeAnimations.degreeVec(1.5F, 0.0F, 1.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(2.5F, KeyframeAnimations.degreeVec(-1.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(3.75F, KeyframeAnimations.degreeVec(1.5F, 0.0F, -1.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(5.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.4167F, KeyframeAnimations.degreeVec(-2.0F, -9.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(2.5833F, KeyframeAnimations.degreeVec(1.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(3.7917F, KeyframeAnimations.degreeVec(-3.0F, 11.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(5.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.7083F, KeyframeAnimations.degreeVec(-4.0F, 0.0F, -2.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(3.2917F, KeyframeAnimations.degreeVec(2.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(5.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2083F, KeyframeAnimations.degreeVec(2.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(2.9167F, KeyframeAnimations.degreeVec(-4.0F, 0.0F, 2.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(5.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("forearm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(2.2083F, KeyframeAnimations.degreeVec(-7.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(5.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("forearm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(3.0F, KeyframeAnimations.degreeVec(-6.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(5.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("hand_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0F, KeyframeAnimations.degreeVec(-9.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(2.4167F, KeyframeAnimations.degreeVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(3.5833F, KeyframeAnimations.degreeVec(-7.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(5.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("hand_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(4.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(2.0833F, KeyframeAnimations.degreeVec(-8.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(3.9167F, KeyframeAnimations.degreeVec(3.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(5.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("root", new AnimationChannel(AnimationChannel.Targets.POSITION,
    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(2.5F, KeyframeAnimations.posVec(0.0F, -0.7F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(5.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .build();

    public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(1.6F).looping()
    .addAnimation("leg_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-24.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2083F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-24.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("leg_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(-24.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2083F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5833F, KeyframeAnimations.degreeVec(20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("shin_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(3.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.2917F, KeyframeAnimations.degreeVec(26.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(2.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2083F, KeyframeAnimations.degreeVec(10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5833F, KeyframeAnimations.degreeVec(3.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("shin_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(2.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(3.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0833F, KeyframeAnimations.degreeVec(26.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5833F, KeyframeAnimations.degreeVec(2.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("foot_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-7.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(-9.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2083F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-7.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("foot_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-9.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(-7.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2083F, KeyframeAnimations.degreeVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-9.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(17.0F, 0.0F, -3.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -3.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(-19.0F, 0.0F, -3.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2083F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -3.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5833F, KeyframeAnimations.degreeVec(17.0F, 0.0F, -3.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-19.0F, 0.0F, 3.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 3.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(17.0F, 0.0F, 3.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2083F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 3.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-19.0F, 0.0F, 3.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("forearm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-14.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(-22.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-14.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("forearm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-22.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(-14.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-22.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(4.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(4.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(4.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2083F, KeyframeAnimations.degreeVec(4.0F, 0.0F, -5.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5833F, KeyframeAnimations.degreeVec(4.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-3.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(0.0F, 4.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(-3.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2083F, KeyframeAnimations.degreeVec(0.0F, -4.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-3.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("root", new AnimationChannel(AnimationChannel.Targets.POSITION,
    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.posVec(0.0F, -1.6F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2083F, KeyframeAnimations.posVec(0.0F, -1.6F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5833F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .build();

    public static final AnimationDefinition CHARGE = AnimationDefinition.Builder.withLength(1.0F).looping()
    .addAnimation("leg_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-36.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0F, KeyframeAnimations.degreeVec(-36.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("leg_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-36.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0F, KeyframeAnimations.degreeVec(30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("shin_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.2083F, KeyframeAnimations.degreeVec(40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(3.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.75F, KeyframeAnimations.degreeVec(16.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0F, KeyframeAnimations.degreeVec(6.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("shin_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(3.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.25F, KeyframeAnimations.degreeVec(16.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(6.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0F, KeyframeAnimations.degreeVec(3.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("foot_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.25F, KeyframeAnimations.degreeVec(8.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-12.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("foot_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-12.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.75F, KeyframeAnimations.degreeVec(8.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0F, KeyframeAnimations.degreeVec(-12.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(32.0F, 0.0F, -6.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -6.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-34.0F, 0.0F, -6.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -6.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0F, KeyframeAnimations.degreeVec(32.0F, 0.0F, -6.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-34.0F, 0.0F, 6.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.25F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 6.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(32.0F, 0.0F, 6.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.75F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 6.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0F, KeyframeAnimations.degreeVec(-34.0F, 0.0F, 6.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("forearm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-42.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0F, KeyframeAnimations.degreeVec(-30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("forearm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-42.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0F, KeyframeAnimations.degreeVec(-42.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(13.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.25F, KeyframeAnimations.degreeVec(13.0F, 0.0F, 6.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(13.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.75F, KeyframeAnimations.degreeVec(13.0F, 0.0F, -6.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0F, KeyframeAnimations.degreeVec(13.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-12.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.25F, KeyframeAnimations.degreeVec(-10.0F, 5.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-12.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.75F, KeyframeAnimations.degreeVec(-10.0F, -5.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0F, KeyframeAnimations.degreeVec(-12.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("root", new AnimationChannel(AnimationChannel.Targets.POSITION,
    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.25F, KeyframeAnimations.posVec(0.0F, -2.4F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, -2.4F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .build();

    public static final AnimationDefinition ROAR = AnimationDefinition.Builder.withLength(2.0F)
    .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-6.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0F, KeyframeAnimations.degreeVec(-9.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-2.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-26.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0833F, KeyframeAnimations.degreeVec(-31.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.7083F, KeyframeAnimations.degreeVec(-8.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5833F, KeyframeAnimations.degreeVec(22.0F, 0.0F, -38.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0833F, KeyframeAnimations.degreeVec(27.0F, 0.0F, -48.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.7083F, KeyframeAnimations.degreeVec(6.0F, 0.0F, -12.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5833F, KeyframeAnimations.degreeVec(22.0F, 0.0F, 38.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0833F, KeyframeAnimations.degreeVec(27.0F, 0.0F, 48.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.7083F, KeyframeAnimations.degreeVec(6.0F, 0.0F, 12.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("forearm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5833F, KeyframeAnimations.degreeVec(42.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0833F, KeyframeAnimations.degreeVec(52.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.7083F, KeyframeAnimations.degreeVec(12.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("forearm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5833F, KeyframeAnimations.degreeVec(42.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0833F, KeyframeAnimations.degreeVec(52.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.7083F, KeyframeAnimations.degreeVec(12.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("hand_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(18.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.4167F, KeyframeAnimations.degreeVec(22.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("hand_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(18.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.4167F, KeyframeAnimations.degreeVec(22.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("leg_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(6.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.4167F, KeyframeAnimations.degreeVec(4.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("leg_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(6.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.4167F, KeyframeAnimations.degreeVec(4.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .build();

    public static final AnimationDefinition SWIPE = AnimationDefinition.Builder.withLength(1.3F)
    .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.3333F, KeyframeAnimations.degreeVec(2.0F, -26.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(6.0F, 32.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0F, KeyframeAnimations.degreeVec(2.0F, 10.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.3333F, KeyframeAnimations.degreeVec(0.0F, -16.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(0.0F, 21.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.3333F, KeyframeAnimations.degreeVec(48.0F, 0.0F, -26.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(-56.0F, 0.0F, 16.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0F, KeyframeAnimations.degreeVec(-20.0F, 0.0F, 4.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("forearm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.3333F, KeyframeAnimations.degreeVec(32.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(-26.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("hand_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.3333F, KeyframeAnimations.degreeVec(14.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(-18.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.3333F, KeyframeAnimations.degreeVec(-22.0F, 0.0F, 8.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(26.0F, 0.0F, -6.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("forearm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.3333F, KeyframeAnimations.degreeVec(-18.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(14.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.2917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .build();

    public static final AnimationDefinition KICK = AnimationDefinition.Builder.withLength(1.5F)
    .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(-7.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(-15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0833F, KeyframeAnimations.degreeVec(-4.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("leg_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(32.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(-62.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0833F, KeyframeAnimations.degreeVec(-20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("shin_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(48.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(-6.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0833F, KeyframeAnimations.degreeVec(12.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("foot_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(-16.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(22.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.0833F, KeyframeAnimations.degreeVec(4.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("leg_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(-6.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(9.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("shin_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(8.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(14.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(-26.0F, 0.0F, -10.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(32.0F, 0.0F, -18.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(26.0F, 0.0F, 10.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(-32.0F, 0.0F, 18.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(6.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(12.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("root", new AnimationChannel(AnimationChannel.Targets.POSITION,
    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.posVec(0.0F, -2.2F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(1.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .build();

    public static final AnimationDefinition GRAB = AnimationDefinition.Builder.withLength(0.7083F)
    .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(13.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-72.0F, 0.0F, -13.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(-79.0F, 0.0F, -9.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-72.0F, 0.0F, 13.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(-79.0F, 0.0F, 9.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("forearm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(36.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("forearm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(36.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("hand_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4583F, KeyframeAnimations.degreeVec(-22.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(12.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("hand_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4583F, KeyframeAnimations.degreeVec(-22.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(12.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(13.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(11.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("leg_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(-8.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("leg_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7083F, KeyframeAnimations.degreeVec(-8.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .build();

    public static final AnimationDefinition THROW = AnimationDefinition.Builder.withLength(0.9167F)
    .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(13.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.2083F, KeyframeAnimations.degreeVec(-4.0F, 16.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(-17.0F, -21.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5833F, KeyframeAnimations.degreeVec(-5.0F, -8.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.9167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-79.0F, 0.0F, -9.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.2083F, KeyframeAnimations.degreeVec(-28.0F, 0.0F, -16.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(88.0F, 0.0F, -10.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5833F, KeyframeAnimations.degreeVec(58.0F, 0.0F, -4.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.9167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("forearm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.2083F, KeyframeAnimations.degreeVec(8.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(-22.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.9167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("hand_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(12.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.2083F, KeyframeAnimations.degreeVec(-4.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(-32.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.9167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-79.0F, 0.0F, 9.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.25F, KeyframeAnimations.degreeVec(-18.0F, 0.0F, 14.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(32.0F, 0.0F, 6.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.9167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("forearm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.25F, KeyframeAnimations.degreeVec(6.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-16.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.9167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("hand_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(12.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(-26.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.9167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(11.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(-12.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.9167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("leg_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-8.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.9167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("leg_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(-8.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4167F, KeyframeAnimations.degreeVec(10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.9167F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .build();

    public static final AnimationDefinition STAGGER = AnimationDefinition.Builder.withLength(0.8F)
    .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.2083F, KeyframeAnimations.degreeVec(-17.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.4583F, KeyframeAnimations.degreeVec(-6.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.2083F, KeyframeAnimations.degreeVec(-23.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-4.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.2083F, KeyframeAnimations.degreeVec(32.0F, 0.0F, -26.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-8.0F, 0.0F, -8.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("arm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.2083F, KeyframeAnimations.degreeVec(32.0F, 0.0F, 26.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-8.0F, 0.0F, 8.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("forearm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.2083F, KeyframeAnimations.degreeVec(20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-6.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("forearm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.2083F, KeyframeAnimations.degreeVec(20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-6.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("leg_right", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.25F, KeyframeAnimations.degreeVec(13.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(-5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("leg_left", new AnimationChannel(AnimationChannel.Targets.ROTATION,
    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.25F, KeyframeAnimations.degreeVec(-9.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.degreeVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .addAnimation("root", new AnimationChannel(AnimationChannel.Targets.POSITION,
    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.2083F, KeyframeAnimations.posVec(0.0F, 0.0F, 2.5F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.8F), AnimationChannel.Interpolations.LINEAR),
    new Keyframe(0.7917F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
    ))
    .build();
}
