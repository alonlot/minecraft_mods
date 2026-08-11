package com.hogwartsmagic.client.model;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Keyframe clips for the Grindylow, exported from
 * blockbench_models/grindylow.bbmodel through the Modded Entity animation codec.
 *
 * <p>Rotation-only, and none of them touch the six tentacle chains: those are a
 * curl plus a travelling wave solved in Java by {@link GrindylowModel}, because
 * how tightly they are drawn in depends on what the creature is doing and a
 * fixed clip cannot know that.
 */
public class GrindylowAnimations {

    public static final AnimationDefinition LURK = AnimationDefinition.Builder.withLength(5.0F).looping()
        .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(12.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.5F, KeyframeAnimations.degreeVec(14.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(5.0F, KeyframeAnimations.degreeVec(12.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(18.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.5F, KeyframeAnimations.degreeVec(20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(5.0F, KeyframeAnimations.degreeVec(18.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(5.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("arm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-72.0F, 0.0F, 10.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.5F, KeyframeAnimations.degreeVec(-76.0F, 0.0F, 12.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(5.0F, KeyframeAnimations.degreeVec(-72.0F, 0.0F, 10.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("arm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-72.0F, 0.0F, -10.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.5F, KeyframeAnimations.degreeVec(-76.0F, 0.0F, -12.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(5.0F, KeyframeAnimations.degreeVec(-72.0F, 0.0F, -10.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("forearm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(95.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.5F, KeyframeAnimations.degreeVec(92.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(5.0F, KeyframeAnimations.degreeVec(95.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("forearm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(95.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.5F, KeyframeAnimations.degreeVec(92.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(5.0F, KeyframeAnimations.degreeVec(95.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l1_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-18.0F, 0.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.5F, KeyframeAnimations.degreeVec(-21.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(5.0F, KeyframeAnimations.degreeVec(-18.0F, 0.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l2_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-19.26F, 0.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.5F, KeyframeAnimations.degreeVec(-22.47F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(5.0F, KeyframeAnimations.degreeVec(-19.26F, 0.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l3_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-20.52F, 0.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.5F, KeyframeAnimations.degreeVec(-23.94F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(5.0F, KeyframeAnimations.degreeVec(-20.52F, 0.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r1_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-18.0F, -0.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.5F, KeyframeAnimations.degreeVec(-21.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(5.0F, KeyframeAnimations.degreeVec(-18.0F, -0.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r2_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-19.26F, -0.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.5F, KeyframeAnimations.degreeVec(-22.47F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(5.0F, KeyframeAnimations.degreeVec(-19.26F, -0.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r3_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-20.52F, -0.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.5F, KeyframeAnimations.degreeVec(-23.94F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(5.0F, KeyframeAnimations.degreeVec(-20.52F, -0.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_top_l_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-18.0F, 0.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.5F, KeyframeAnimations.degreeVec(-21.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(5.0F, KeyframeAnimations.degreeVec(-18.0F, 0.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_top_r_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-19.26F, -0.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.5F, KeyframeAnimations.degreeVec(-22.47F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(5.0F, KeyframeAnimations.degreeVec(-19.26F, -0.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .build();

    public static final AnimationDefinition DRIFT = AnimationDefinition.Builder.withLength(4.0F).looping()
        .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(4.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(1.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(4.0F, KeyframeAnimations.degreeVec(4.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, -4.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2917F, KeyframeAnimations.degreeVec(4.0F, 4.0F, 2.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.5833F, KeyframeAnimations.degreeVec(-2.0F, -3.0F, -2.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(4.0F, KeyframeAnimations.degreeVec(0.0F, -4.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(3.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(11.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(4.0F, KeyframeAnimations.degreeVec(3.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("arm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, -16.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(-4.0F, 0.0F, -22.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(4.0F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, -16.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("arm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 16.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(-4.0F, 0.0F, 22.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(4.0F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 16.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("forearm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(4.0F, KeyframeAnimations.degreeVec(40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("forearm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(34.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(4.0F, KeyframeAnimations.degreeVec(40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l1_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.0F, 3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(-3.0F, 12.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(4.0F, KeyframeAnimations.degreeVec(6.0F, 3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l2_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.42F, 3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(-3.21F, 12.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(4.0F, KeyframeAnimations.degreeVec(6.42F, 3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l3_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.84F, 3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(-3.42F, 12.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(4.0F, KeyframeAnimations.degreeVec(6.84F, 3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r1_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.0F, -3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(-3.0F, -12.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(4.0F, KeyframeAnimations.degreeVec(6.0F, -3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r2_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.42F, -3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(-3.21F, -12.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(4.0F, KeyframeAnimations.degreeVec(6.42F, -3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r3_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.84F, -3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(-3.42F, -12.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(4.0F, KeyframeAnimations.degreeVec(6.84F, -3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_top_l_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.0F, 3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(-3.0F, 12.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(4.0F, KeyframeAnimations.degreeVec(6.0F, 3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_top_r_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.42F, -3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(2.0F, KeyframeAnimations.degreeVec(-3.21F, -12.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(4.0F, KeyframeAnimations.degreeVec(6.42F, -3.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .build();

    public static final AnimationDefinition LUNGE = AnimationDefinition.Builder.withLength(0.7083F)
        .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.1667F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(14.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.7083F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.1667F, KeyframeAnimations.degreeVec(-14.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(22.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.7083F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.1667F, KeyframeAnimations.degreeVec(20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(58.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5417F, KeyframeAnimations.degreeVec(40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.7083F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("arm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, -16.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.1667F, KeyframeAnimations.degreeVec(30.0F, 0.0F, -30.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(-80.0F, 0.0F, -10.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.7083F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, -16.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("arm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 16.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.1667F, KeyframeAnimations.degreeVec(30.0F, 0.0F, 30.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(-80.0F, 0.0F, 10.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.7083F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 16.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("forearm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.1667F, KeyframeAnimations.degreeVec(70.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(8.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.7083F, KeyframeAnimations.degreeVec(40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("forearm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.1667F, KeyframeAnimations.degreeVec(70.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(8.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.7083F, KeyframeAnimations.degreeVec(40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l1_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.0F, 8.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2083F, KeyframeAnimations.degreeVec(42.0F, 34.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5F, KeyframeAnimations.degreeVec(38.0F, 34.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.7083F, KeyframeAnimations.degreeVec(6.0F, 8.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l2_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.42F, 8.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2083F, KeyframeAnimations.degreeVec(44.94F, 34.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5F, KeyframeAnimations.degreeVec(40.66F, 34.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.7083F, KeyframeAnimations.degreeVec(6.42F, 8.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l3_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.84F, 8.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2083F, KeyframeAnimations.degreeVec(47.88F, 34.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5F, KeyframeAnimations.degreeVec(43.32F, 34.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.7083F, KeyframeAnimations.degreeVec(6.84F, 8.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r1_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.0F, -8.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2083F, KeyframeAnimations.degreeVec(42.0F, -34.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5F, KeyframeAnimations.degreeVec(38.0F, -34.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.7083F, KeyframeAnimations.degreeVec(6.0F, -8.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r2_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.42F, -8.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2083F, KeyframeAnimations.degreeVec(44.94F, -34.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5F, KeyframeAnimations.degreeVec(40.66F, -34.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.7083F, KeyframeAnimations.degreeVec(6.42F, -8.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r3_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.84F, -8.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2083F, KeyframeAnimations.degreeVec(47.88F, -34.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5F, KeyframeAnimations.degreeVec(43.32F, -34.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.7083F, KeyframeAnimations.degreeVec(6.84F, -8.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_top_l_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.0F, 8.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2083F, KeyframeAnimations.degreeVec(42.0F, 34.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5F, KeyframeAnimations.degreeVec(38.0F, 34.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.7083F, KeyframeAnimations.degreeVec(6.0F, 8.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_top_r_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.42F, -8.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2083F, KeyframeAnimations.degreeVec(44.94F, -34.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5F, KeyframeAnimations.degreeVec(40.66F, -34.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.7083F, KeyframeAnimations.degreeVec(6.42F, -8.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .build();

    public static final AnimationDefinition CLUTCH = AnimationDefinition.Builder.withLength(1.2083F).looping()
        .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.degreeVec(6.0F, 0.0F, 3.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2083F, KeyframeAnimations.degreeVec(10.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(8.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2917F, KeyframeAnimations.degreeVec(14.0F, -4.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.degreeVec(8.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.9167F, KeyframeAnimations.degreeVec(14.0F, 4.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2083F, KeyframeAnimations.degreeVec(8.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2917F, KeyframeAnimations.degreeVec(6.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.degreeVec(32.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.9167F, KeyframeAnimations.degreeVec(8.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2083F, KeyframeAnimations.degreeVec(30.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("arm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-70.0F, 0.0F, 14.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.degreeVec(-76.0F, 0.0F, 18.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2083F, KeyframeAnimations.degreeVec(-70.0F, 0.0F, 14.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("arm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-70.0F, 0.0F, -14.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.degreeVec(-76.0F, 0.0F, -18.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2083F, KeyframeAnimations.degreeVec(-70.0F, 0.0F, -14.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("forearm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(80.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.degreeVec(88.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2083F, KeyframeAnimations.degreeVec(80.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("forearm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(80.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.degreeVec(88.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2083F, KeyframeAnimations.degreeVec(80.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l1_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(14.0F, 5.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.degreeVec(24.0F, 22.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2083F, KeyframeAnimations.degreeVec(14.0F, 5.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l2_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(14.98F, 5.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.degreeVec(25.68F, 22.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2083F, KeyframeAnimations.degreeVec(14.98F, 5.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l3_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(15.96F, 5.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.degreeVec(27.36F, 22.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2083F, KeyframeAnimations.degreeVec(15.96F, 5.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r1_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(14.0F, -5.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.degreeVec(24.0F, -22.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2083F, KeyframeAnimations.degreeVec(14.0F, -5.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r2_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(14.98F, -5.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.degreeVec(25.68F, -22.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2083F, KeyframeAnimations.degreeVec(14.98F, -5.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r3_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(15.96F, -5.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.degreeVec(27.36F, -22.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2083F, KeyframeAnimations.degreeVec(15.96F, -5.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_top_l_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(14.0F, 5.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.degreeVec(24.0F, 22.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2083F, KeyframeAnimations.degreeVec(14.0F, 5.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_top_r_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(14.98F, -5.5F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5833F, KeyframeAnimations.degreeVec(25.68F, -22.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.2083F, KeyframeAnimations.degreeVec(14.98F, -5.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .build();

    public static final AnimationDefinition HURT = AnimationDefinition.Builder.withLength(0.35F)
        .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.0833F, KeyframeAnimations.degreeVec(-12.0F, 0.0F, 6.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.0833F, KeyframeAnimations.degreeVec(-16.0F, 0.0F, -8.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.0833F, KeyframeAnimations.degreeVec(40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("arm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, -16.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.0833F, KeyframeAnimations.degreeVec(20.0F, 0.0F, -34.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, -16.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("arm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 16.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.0833F, KeyframeAnimations.degreeVec(20.0F, 0.0F, 34.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 16.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l1_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.0F, 7.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.0833F, KeyframeAnimations.degreeVec(46.0F, 28.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(6.0F, 7.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l2_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.42F, 7.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.0833F, KeyframeAnimations.degreeVec(49.22F, 28.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(6.42F, 7.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l3_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.84F, 7.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.0833F, KeyframeAnimations.degreeVec(52.44F, 28.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(6.84F, 7.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r1_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.0F, -7.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.0833F, KeyframeAnimations.degreeVec(46.0F, -28.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(6.0F, -7.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r2_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.42F, -7.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.0833F, KeyframeAnimations.degreeVec(49.22F, -28.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(6.42F, -7.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r3_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.84F, -7.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.0833F, KeyframeAnimations.degreeVec(52.44F, -28.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(6.84F, -7.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_top_l_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.0F, 7.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.0833F, KeyframeAnimations.degreeVec(46.0F, 28.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(6.0F, 7.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_top_r_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.42F, -7.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.0833F, KeyframeAnimations.degreeVec(49.22F, -28.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.3333F, KeyframeAnimations.degreeVec(6.42F, -7.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .build();

    public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(1.6F)
        .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.4167F, KeyframeAnimations.degreeVec(-14.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5833F, KeyframeAnimations.degreeVec(8.0F, 0.0F, 20.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.4167F, KeyframeAnimations.degreeVec(-18.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-26.0F, 0.0F, -16.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.2917F, KeyframeAnimations.degreeVec(46.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5833F, KeyframeAnimations.degreeVec(28.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("arm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, -16.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5F, KeyframeAnimations.degreeVec(24.0F, 0.0F, -34.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-30.0F, 0.0F, -8.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("arm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(-10.0F, 0.0F, 16.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(0.5F, KeyframeAnimations.degreeVec(24.0F, 0.0F, 34.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-30.0F, 0.0F, 8.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("forearm_left", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5833F, KeyframeAnimations.degreeVec(6.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("forearm_right", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(40.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5833F, KeyframeAnimations.degreeVec(6.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l1_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-32.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l2_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.42F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-34.24F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_l3_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.84F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-36.48F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r1_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-32.0F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r2_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.42F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-34.24F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_r3_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.84F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-36.48F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_top_l_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-32.0F, -2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .addAnimation("spine_top_r_g", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
            new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.42F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
            new Keyframe(1.5833F, KeyframeAnimations.degreeVec(-34.24F, 2.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
        ))
        .build();
}
