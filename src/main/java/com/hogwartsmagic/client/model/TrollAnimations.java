package com.hogwartsmagic.client.model;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Keyframe clips for the mountain troll. Every value is a delta from the rest pose in {@link TrollModel}.
 *
 * <p>Java model signs, for reading the numbers: a part hanging below its pivot swings forward on -X, a
 * mass above its pivot tips forward on +X, the jaw opens on +X, -Y and +Y turn toward the troll's left and
 * right, and on Z a limb flares outward with +Z on the right and -Z on the left. Positions are up on +Y.
 *
 * <p>The attack clips are timed to the entity: {@code SLAM_WINDUP} is 18 ticks and {@code SLAM_STRIKE}
 * opens on the pose it ends on and lands at its second key, with the club head at
 * {@code TrollEntity.SLAM_REACH}. {@code SWEEP} and {@code BACKHAND} peak on their strike ticks.
 * The field names are also what Pensieve scenes call these clips by.
 */
public final class TrollAnimations {

    private TrollAnimations() {
    }

    private static Keyframe r(float t, float x, float y, float z) {
        return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
    }

    /** A rotation key reached in a straight line: for hits, where a spline would overshoot the blow. */
    private static Keyframe rl(float t, float x, float y, float z) {
        return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
    }

    private static Keyframe p(float t, float x, float y, float z) {
        return new Keyframe(t, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
    }

    private static Keyframe pl(float t, float x, float y, float z) {
        return new Keyframe(t, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
    }

    private static AnimationChannel rot(Keyframe... keys) {
        return new AnimationChannel(AnimationChannel.Targets.ROTATION, keys);
    }

    private static AnimationChannel pos(Keyframe... keys) {
        return new AnimationChannel(AnimationChannel.Targets.POSITION, keys);
    }

    public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(4.0F).looping()
            .addAnimation("body", rot(r(0, 0, 0, 0), r(2, 1.6F, 0, 0), r(4, 0, 0, 0)))
            .addAnimation("belly", pos(p(0, 0, 0, 0), p(2, 0, -0.5F, -0.4F), p(4, 0, 0, 0)))
            .addAnimation("head", rot(r(0, 0, 0, 0), r(1.1F, 2, 12, 0), r(2.2F, 0, 0, 0), r(3.2F, 3, -14, 0), r(4, 0, 0, 0)))
            .addAnimation("jaw", rot(r(0, 3, 0, 0), r(1.5F, 9, 0, 0), r(2.2F, 3, 0, 0), r(3.3F, 7, 0, 0), r(4, 3, 0, 0)))
            .addAnimation("ear_r", rot(rl(0, 0, 0, 0), rl(1.0F, 0, 0, 0), rl(1.1F, 0, 0, 12), rl(1.25F, 0, 0, 0), rl(4, 0, 0, 0)))
            .addAnimation("ear_l", rot(rl(0, 0, 0, 0), rl(2.9F, 0, 0, 0), rl(3.0F, 0, 0, -12), rl(3.15F, 0, 0, 0), rl(4, 0, 0, 0)))
            .addAnimation("arm_l", rot(r(0, 0, 0, 0), r(2, -3, 0, -2), r(4, 0, 0, 0)))
            .addAnimation("arm_r", rot(r(0, 0, 0, 0), r(2, -1.5F, 0, 1), r(4, 0, 0, 0)))
            .build();

    /** Heavy lumbering step: the club arm barely swings, so the club trails along by its own weight. */
    public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(1.6F).looping()
            .addAnimation("hull", pos(p(0, 0, -0.8F, 0), p(0.4F, 0, 0.5F, 0), p(0.8F, 0, -0.8F, 0), p(1.2F, 0, 0.5F, 0), p(1.6F, 0, -0.8F, 0)))
            .addAnimation("body", rot(r(0, 2, 7, -3.5F), r(0.4F, 3, 0, 0), r(0.8F, 2, -7, 3.5F), r(1.2F, 3, 0, 0), r(1.6F, 2, 7, -3.5F)))
            .addAnimation("belly", pos(p(0, 0, -0.7F, 0), p(0.4F, 0, 0.5F, 0), p(0.8F, 0, -0.7F, 0), p(1.2F, 0, 0.5F, 0), p(1.6F, 0, -0.7F, 0)))
            .addAnimation("head", rot(r(0, -3, -6, 3), r(0.4F, -1, 0, 0), r(0.8F, -3, 6, -3), r(1.2F, -1, 0, 0), r(1.6F, -3, -6, 3)))
            .addAnimation("jaw", rot(r(0, 6, 0, 0), r(0.8F, 3, 0, 0), r(1.6F, 6, 0, 0)))
            .addAnimation("loin_front", rot(r(0, -6, 0, 0), r(0.4F, 4, 0, 0), r(0.8F, -6, 0, 0), r(1.2F, 4, 0, 0), r(1.6F, -6, 0, 0)))
            .addAnimation("loin_back", rot(r(0, 6, 0, 0), r(0.4F, -4, 0, 0), r(0.8F, 6, 0, 0), r(1.2F, -4, 0, 0), r(1.6F, 6, 0, 0)))
            .addAnimation("arm_l", rot(r(0, 24, 0, -3), r(0.8F, -26, 0, -3), r(1.6F, 24, 0, -3)))
            .addAnimation("forearm_l", rot(r(0, -4, 0, 0), r(0.8F, -24, 0, 0), r(1.6F, -4, 0, 0)))
            .addAnimation("arm_r", rot(r(0, -6, 0, 2), r(0.8F, 6, 0, 2), r(1.6F, -6, 0, 2)))
            .addAnimation("hand_r", rot(r(0, 5, 0, 0), r(0.8F, -5, 0, 0), r(1.6F, 5, 0, 0)))
            .addAnimation("leg_r", rot(r(0, -30, 0, 0), r(0.4F, -6, 0, 0), r(0.8F, 30, 0, 0), r(1.2F, 8, 0, 0), r(1.6F, -30, 0, 0)))
            .addAnimation("shin_r", rot(r(0, 8, 0, 0), r(0.4F, 2, 0, 0), r(0.8F, 6, 0, 0), r(1.2F, 46, 0, 0), r(1.6F, 8, 0, 0)))
            .addAnimation("foot_r", rot(r(0, 20, 0, 0), r(0.4F, 2, 0, 0), r(0.8F, -18, 0, 0), r(1.2F, -22, 0, 0), r(1.6F, 20, 0, 0)))
            .addAnimation("leg_l", rot(r(0, 30, 0, 0), r(0.4F, 8, 0, 0), r(0.8F, -30, 0, 0), r(1.2F, -6, 0, 0), r(1.6F, 30, 0, 0)))
            .addAnimation("shin_l", rot(r(0, 6, 0, 0), r(0.4F, 46, 0, 0), r(0.8F, 8, 0, 0), r(1.2F, 2, 0, 0), r(1.6F, 6, 0, 0)))
            .addAnimation("foot_l", rot(r(0, -18, 0, 0), r(0.4F, -22, 0, 0), r(0.8F, 20, 0, 0), r(1.2F, 2, 0, 0), r(1.6F, -18, 0, 0)))
            .build();

    /** The rage run: bent low, club cocked up at the shoulder, left arm pumping. */
    public static final AnimationDefinition CHARGE = AnimationDefinition.Builder.withLength(1.0F).looping()
            .addAnimation("hull", pos(p(0, 0, -2.2F, -1), p(0.25F, 0, 0.2F, -1), p(0.5F, 0, -2.2F, -1), p(0.75F, 0, 0.2F, -1), p(1, 0, -2.2F, -1)))
            .addAnimation("body", rot(r(0, 16, 9, -4), r(0.25F, 18, 0, 0), r(0.5F, 16, -9, 4), r(0.75F, 18, 0, 0), r(1, 16, 9, -4)))
            .addAnimation("head", rot(r(0, -16, -6, 3), r(0.25F, -18, 0, 0), r(0.5F, -16, 6, -3), r(0.75F, -18, 0, 0), r(1, -16, -6, 3)))
            .addAnimation("jaw", rot(r(0, 22, 0, 0), r(0.5F, 14, 0, 0), r(1, 22, 0, 0)))
            .addAnimation("arm_l", rot(r(0, 40, 0, -6), r(0.5F, -52, 0, -6), r(1, 40, 0, -6)))
            .addAnimation("forearm_l", rot(r(0, -22, 0, 0), r(0.5F, -44, 0, 0), r(1, -22, 0, 0)))
            .addAnimation("arm_r", rot(r(0, -138, 0, 12), r(0.5F, -122, 0, 12), r(1, -138, 0, 12)))
            .addAnimation("forearm_r", rot(r(0, -48, 0, 0), r(0.5F, -40, 0, 0), r(1, -48, 0, 0)))
            .addAnimation("hand_r", rot(r(0, 26, 0, 0), r(1, 26, 0, 0)))
            .addAnimation("leg_r", rot(r(0, -38, 0, 0), r(0.25F, -8, 0, 0), r(0.5F, 36, 0, 0), r(0.75F, 8, 0, 0), r(1, -38, 0, 0)))
            .addAnimation("shin_r", rot(r(0, 12, 0, 0), r(0.25F, 4, 0, 0), r(0.5F, 8, 0, 0), r(0.75F, 54, 0, 0), r(1, 12, 0, 0)))
            .addAnimation("foot_r", rot(r(0, 22, 0, 0), r(0.25F, 4, 0, 0), r(0.5F, -20, 0, 0), r(0.75F, -28, 0, 0), r(1, 22, 0, 0)))
            .addAnimation("leg_l", rot(r(0, 36, 0, 0), r(0.25F, 8, 0, 0), r(0.5F, -38, 0, 0), r(0.75F, -8, 0, 0), r(1, 36, 0, 0)))
            .addAnimation("shin_l", rot(r(0, 8, 0, 0), r(0.25F, 54, 0, 0), r(0.5F, 12, 0, 0), r(0.75F, 4, 0, 0), r(1, 8, 0, 0)))
            .addAnimation("foot_l", rot(r(0, -20, 0, 0), r(0.25F, -28, 0, 0), r(0.5F, 22, 0, 0), r(0.75F, 4, 0, 0), r(1, -20, 0, 0)))
            .build();

    public static final AnimationDefinition ROAR = AnimationDefinition.Builder.withLength(1.8F)
            .addAnimation("hull", pos(p(0, 0, 0, 0), p(0.3F, 0, -2, 0), p(0.55F, 0, 0.6F, 0), p(1.4F, 0, 0, 0), p(1.8F, 0, 0, 0)))
            .addAnimation("body", rot(r(0, 0, 0, 0), r(0.3F, 16, 0, 0), r(0.55F, -22, 0, 0), r(1.0F, -20, 3, 1), r(1.4F, -18, -3, -1), r(1.8F, 0, 0, 0)))
            .addAnimation("belly", pos(p(0, 0, 0, 0), p(0.55F, 0, 1.2F, -1), p(1.4F, 0, 0.8F, -1), p(1.8F, 0, 0, 0)))
            .addAnimation("head", rot(r(0, 0, 0, 0), r(0.3F, 14, 0, 0), r(0.55F, -24, 0, 0), r(0.8F, -26, 12, 0), r(1.1F, -26, -12, 0), r(1.4F, -24, 0, 0), r(1.8F, 0, 0, 0)))
            .addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.3F, 8, 0, 0), r(0.55F, 40, 0, 0), r(1.4F, 36, 0, 0), r(1.8F, 0, 0, 0)))
            .addAnimation("ear_r", rot(r(0, 0, 0, 0), r(0.55F, 0, 0, 24), r(1.4F, 0, 0, 20), r(1.8F, 0, 0, 0)))
            .addAnimation("ear_l", rot(r(0, 0, 0, 0), r(0.55F, 0, 0, -24), r(1.4F, 0, 0, -20), r(1.8F, 0, 0, 0)))
            .addAnimation("arm_r", rot(r(0, 0, 0, 0), r(0.3F, 12, 0, 10), r(0.55F, -44, 0, 70), r(1.4F, -40, 0, 64), r(1.8F, 0, 0, 0)))
            .addAnimation("forearm_r", rot(r(0, 0, 0, 0), r(0.55F, -40, 0, 0), r(1.4F, -34, 0, 0), r(1.8F, 0, 0, 0)))
            .addAnimation("hand_r", rot(r(0, 0, 0, 0), r(0.55F, 18, 0, 0), r(1.4F, 18, 0, 0), r(1.8F, 0, 0, 0)))
            .addAnimation("arm_l", rot(r(0, 0, 0, 0), r(0.3F, 12, 0, -10), r(0.55F, -44, 0, -70), r(1.4F, -40, 0, -64), r(1.8F, 0, 0, 0)))
            .addAnimation("forearm_l", rot(r(0, 0, 0, 0), r(0.55F, -40, 0, 0), r(1.4F, -34, 0, 0), r(1.8F, 0, 0, 0)))
            .addAnimation("leg_r", rot(r(0, 0, 0, 0), r(0.3F, -10, 0, 0), r(0.55F, 4, 0, 0), r(1.8F, 0, 0, 0)))
            .addAnimation("shin_r", rot(r(0, 0, 0, 0), r(0.3F, 16, 0, 0), r(0.55F, 0, 0, 0), r(1.8F, 0, 0, 0)))
            .addAnimation("foot_r", rot(r(0, 0, 0, 0), r(0.3F, -6, 0, 0), r(0.55F, -4, 0, 0), r(1.8F, 0, 0, 0)))
            .addAnimation("leg_l", rot(r(0, 0, 0, 0), r(0.3F, -10, 0, 0), r(0.55F, 4, 0, 0), r(1.8F, 0, 0, 0)))
            .addAnimation("shin_l", rot(r(0, 0, 0, 0), r(0.3F, 16, 0, 0), r(0.55F, 0, 0, 0), r(1.8F, 0, 0, 0)))
            .addAnimation("foot_l", rot(r(0, 0, 0, 0), r(0.3F, -6, 0, 0), r(0.55F, -4, 0, 0), r(1.8F, 0, 0, 0)))
            .build();

    /** Ends with the club hanging behind the head; {@link #SLAM_STRIKE} opens on this exact pose. */
    public static final AnimationDefinition SLAM_WINDUP = AnimationDefinition.Builder.withLength(0.9F)
            .addAnimation("hull", pos(p(0, 0, 0, 0), p(0.5F, 0, -1, 0), p(0.9F, 0, -1.5F, 0)))
            .addAnimation("body", rot(r(0, 0, 0, 0), r(0.4F, -8, 6, 0), r(0.75F, -18, 10, 0), r(0.9F, -18, 10, 0)))
            .addAnimation("head", rot(r(0, 0, 0, 0), r(0.4F, -6, -3, 0), r(0.75F, -14, -6, 0), r(0.9F, -14, -6, 0)))
            .addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.75F, 16, 0, 0), r(0.9F, 18, 0, 0)))
            .addAnimation("arm_r", rot(r(0, 0, 0, 0), r(0.4F, -90, 0, 8), r(0.75F, -170, 0, 12), r(0.9F, -174, 0, 12)))
            .addAnimation("forearm_r", rot(r(0, 0, 0, 0), r(0.4F, -24, 0, 0), r(0.75F, -36, 0, 0), r(0.9F, -38, 0, 0)))
            .addAnimation("hand_r", rot(r(0, 0, 0, 0), r(0.75F, 20, 0, 0), r(0.9F, 22, 0, 0)))
            .addAnimation("arm_l", rot(r(0, 0, 0, 0), r(0.4F, -30, 0, -14), r(0.75F, -56, 0, -26), r(0.9F, -56, 0, -26)))
            .addAnimation("forearm_l", rot(r(0, 0, 0, 0), r(0.75F, -34, 0, 0), r(0.9F, -36, 0, 0)))
            .addAnimation("leg_r", rot(r(0, 0, 0, 0), r(0.9F, -8, 0, 0)))
            .addAnimation("shin_r", rot(r(0, 0, 0, 0), r(0.9F, 12, 0, 0)))
            .addAnimation("foot_r", rot(r(0, 0, 0, 0), r(0.9F, -4, 0, 0)))
            .addAnimation("leg_l", rot(r(0, 0, 0, 0), r(0.9F, -8, 0, 0)))
            .addAnimation("shin_l", rot(r(0, 0, 0, 0), r(0.9F, 12, 0, 0)))
            .addAnimation("foot_l", rot(r(0, 0, 0, 0), r(0.9F, -4, 0, 0)))
            .build();

    public static final AnimationDefinition SLAM_STRIKE = AnimationDefinition.Builder.withLength(0.8F)
            .addAnimation("hull", pos(pl(0, 0, -1.5F, 0), pl(0.1F, 0, -3.5F, -2), pl(0.28F, 0, -3, -2), p(0.8F, 0, 0, 0)))
            .addAnimation("body", rot(rl(0, -18, 10, 0), rl(0.1F, 30, -6, 0), rl(0.28F, 28, -6, 0), r(0.8F, 0, 0, 0)))
            .addAnimation("belly", pos(pl(0, 0, 0, 0), pl(0.1F, 0, -1.5F, 0.5F), pl(0.2F, 0, 0.6F, 0), p(0.8F, 0, 0, 0)))
            .addAnimation("head", rot(rl(0, -14, -6, 0), rl(0.1F, -24, 3, 0), rl(0.28F, -22, 3, 0), r(0.8F, 0, 0, 0)))
            .addAnimation("jaw", rot(rl(0, 18, 0, 0), rl(0.1F, 32, 0, 0), rl(0.28F, 28, 0, 0), r(0.8F, 0, 0, 0)))
            .addAnimation("arm_r", rot(rl(0, -174, 0, 12), rl(0.1F, -87, -18, 4), rl(0.28F, -85, -18, 4), r(0.8F, 0, 0, 0)))
            .addAnimation("forearm_r", rot(rl(0, -38, 0, 0), rl(0.1F, 24, 0, 0), rl(0.28F, 22, 0, 0), r(0.8F, 0, 0, 0)))
            .addAnimation("hand_r", rot(rl(0, 22, 0, 0), rl(0.1F, 34, 0, 0), rl(0.28F, 32, 0, 0), r(0.8F, 0, 0, 0)))
            .addAnimation("arm_l", rot(rl(0, -56, 0, -26), rl(0.1F, 22, 0, -32), rl(0.28F, 18, 0, -28), r(0.8F, 0, 0, 0)))
            .addAnimation("forearm_l", rot(rl(0, -36, 0, 0), rl(0.1F, -12, 0, 0), rl(0.28F, -14, 0, 0), r(0.8F, 0, 0, 0)))
            .addAnimation("leg_r", rot(rl(0, -8, 0, 0), rl(0.1F, -26, 0, 0), rl(0.28F, -26, 0, 0), r(0.8F, 0, 0, 0)))
            .addAnimation("shin_r", rot(rl(0, 12, 0, 0), rl(0.1F, 26, 0, 0), rl(0.28F, 26, 0, 0), r(0.8F, 0, 0, 0)))
            .addAnimation("foot_r", rot(rl(0, -4, 0, 0), rl(0.1F, 0, 0, 0), rl(0.28F, 0, 0, 0), r(0.8F, 0, 0, 0)))
            .addAnimation("leg_l", rot(rl(0, -8, 0, 0), rl(0.1F, 14, 0, 0), rl(0.28F, 14, 0, 0), r(0.8F, 0, 0, 0)))
            .addAnimation("shin_l", rot(rl(0, 12, 0, 0), rl(0.1F, 24, 0, 0), rl(0.28F, 24, 0, 0), r(0.8F, 0, 0, 0)))
            .addAnimation("foot_l", rot(rl(0, -4, 0, 0), rl(0.1F, -32, 0, 0), rl(0.28F, -32, 0, 0), r(0.8F, 0, 0, 0)))
            .build();

    /** A flat swing from the troll's right across to its left, peaking at 0.6 s (tick 12). */
    public static final AnimationDefinition SWEEP = AnimationDefinition.Builder.withLength(1.1F)
            .addAnimation("hull", pos(p(0, 0, 0, 0), p(0.35F, 0, -1, 0), pl(0.6F, 0, -2.2F, -1), p(1.1F, 0, 0, 0)))
            .addAnimation("body", rot(r(0, 0, 0, 0), r(0.35F, -4, 30, -2), rl(0.6F, 14, -6, 2), r(0.8F, 10, -32, 3), r(1.1F, 0, 0, 0)))
            .addAnimation("head", rot(r(0, 0, 0, 0), r(0.35F, 0, -14, 0), rl(0.6F, -10, 4, 0), r(0.8F, -6, 14, 0), r(1.1F, 0, 0, 0)))
            .addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.35F, 8, 0, 0), rl(0.6F, 26, 0, 0), r(1.1F, 0, 0, 0)))
            .addAnimation("arm_r", rot(r(0, 0, 0, 0), r(0.35F, -62, 66, 10), rl(0.6F, -92, 0, 0), r(0.8F, -72, -74, 0), r(1.1F, 0, 0, 0)))
            .addAnimation("forearm_r", rot(r(0, 0, 0, 0), r(0.35F, 8, 0, 0), rl(0.6F, 24, 0, 0), r(0.8F, 8, 0, 0), r(1.1F, 0, 0, 0)))
            .addAnimation("hand_r", rot(r(0, 0, 0, 0), r(0.35F, 30, 0, 0), rl(0.6F, 50, 0, 0), r(0.8F, 24, 0, 0), r(1.1F, 0, 0, 0)))
            .addAnimation("arm_l", rot(r(0, 0, 0, 0), r(0.35F, 12, 0, -22), rl(0.6F, -12, 0, -26), r(0.8F, -26, 0, -10), r(1.1F, 0, 0, 0)))
            .addAnimation("leg_r", rot(r(0, 0, 0, 0), r(0.35F, 8, 0, 0), rl(0.6F, -18, 0, 0), r(1.1F, 0, 0, 0)))
            .addAnimation("shin_r", rot(r(0, 0, 0, 0), r(0.35F, 8, 0, 0), rl(0.6F, 20, 0, 0), r(1.1F, 0, 0, 0)))
            .addAnimation("foot_r", rot(r(0, 0, 0, 0), r(0.35F, -12, 0, 0), rl(0.6F, 0, 0, 0), r(1.1F, 0, 0, 0)))
            .addAnimation("leg_l", rot(r(0, 0, 0, 0), r(0.35F, -8, 0, 0), rl(0.6F, 12, 0, 0), r(1.1F, 0, 0, 0)))
            .addAnimation("shin_l", rot(r(0, 0, 0, 0), r(0.35F, 8, 0, 0), rl(0.6F, 16, 0, 0), r(1.1F, 0, 0, 0)))
            .addAnimation("foot_l", rot(r(0, 0, 0, 0), r(0.35F, 2, 0, 0), rl(0.6F, -26, 0, 0), r(1.1F, 0, 0, 0)))
            .build();

    /** A short back-of-the-club swat for whoever gets too close, peaking at 0.35 s (tick 7). */
    public static final AnimationDefinition BACKHAND = AnimationDefinition.Builder.withLength(0.8F)
            .addAnimation("hull", pos(p(0, 0, 0, 0), p(0.2F, 0, -0.8F, 0), pl(0.35F, 0, -1.4F, -1), p(0.8F, 0, 0, 0)))
            .addAnimation("body", rot(r(0, 0, 0, 0), r(0.2F, 2, -24, 0), rl(0.35F, 10, 20, 0), r(0.5F, 8, 14, 0), r(0.8F, 0, 0, 0)))
            .addAnimation("head", rot(r(0, 0, 0, 0), r(0.2F, 0, 12, 0), rl(0.35F, -6, -8, 0), r(0.8F, 0, 0, 0)))
            .addAnimation("jaw", rot(r(0, 0, 0, 0), rl(0.35F, 20, 0, 0), r(0.8F, 0, 0, 0)))
            .addAnimation("arm_r", rot(r(0, 0, 0, 0), r(0.2F, -48, -52, 14), rl(0.35F, -74, 46, 6), r(0.5F, -56, 32, 6), r(0.8F, 0, 0, 0)))
            .addAnimation("forearm_r", rot(r(0, 0, 0, 0), r(0.2F, -18, 0, 0), rl(0.35F, 20, 0, 0), r(0.8F, 0, 0, 0)))
            .addAnimation("hand_r", rot(r(0, 0, 0, 0), r(0.2F, 18, 0, 0), rl(0.35F, 44, 0, 0), r(0.8F, 0, 0, 0)))
            .addAnimation("arm_l", rot(r(0, 0, 0, 0), r(0.2F, -14, 0, -14), rl(0.35F, 8, 0, -20), r(0.8F, 0, 0, 0)))
            .build();

    public static final AnimationDefinition STAGGER = AnimationDefinition.Builder.withLength(0.7F)
            .addAnimation("hull", pos(p(0, 0, 0, 0), p(0.12F, 0, -1, 1.5F), p(0.7F, 0, 0, 0)))
            .addAnimation("body", rot(r(0, 0, 0, 0), r(0.12F, -13, 6, 5), r(0.35F, 7, -3, -2), r(0.7F, 0, 0, 0)))
            .addAnimation("belly", pos(p(0, 0, 0, 0), p(0.12F, 0, 1, 0.6F), p(0.35F, 0, -0.8F, 0), p(0.7F, 0, 0, 0)))
            .addAnimation("head", rot(r(0, 0, 0, 0), r(0.12F, -20, 16, 0), r(0.35F, 8, -8, 0), r(0.7F, 0, 0, 0)))
            .addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.12F, 26, 0, 0), r(0.7F, 0, 0, 0)))
            .addAnimation("arm_r", rot(r(0, 0, 0, 0), r(0.12F, -24, 0, 22), r(0.7F, 0, 0, 0)))
            .addAnimation("arm_l", rot(r(0, 0, 0, 0), r(0.12F, -34, 0, -30), r(0.7F, 0, 0, 0)))
            .addAnimation("forearm_l", rot(r(0, 0, 0, 0), r(0.12F, -30, 0, 0), r(0.7F, 0, 0, 0)))
            .addAnimation("leg_r", rot(r(0, 0, 0, 0), r(0.12F, 6, 0, 0), r(0.7F, 0, 0, 0)))
            .addAnimation("leg_l", rot(r(0, 0, 0, 0), r(0.12F, -10, 0, 0), r(0.7F, 0, 0, 0)))
            .addAnimation("shin_l", rot(r(0, 0, 0, 0), r(0.12F, 14, 0, 0), r(0.7F, 0, 0, 0)))
            .build();

    /**
     * Wingardium Leviosa: the club slides up out of the fist, hangs over the troll's head while it stares
     * at its empty hand and then up, drops on its skull at 2.1 s (tick 42) and bounces to the ground, and
     * the troll goes over backwards. 2.6 s long; {@link #KNOCKED_OUT} opens on its last pose.
     */
    public static final AnimationDefinition DISARMED = AnimationDefinition.Builder.withLength(2.6F)
            .addAnimation("club_loose", pos(p(0, 0, 0, 0), p(0.6F, 18, 46, -10), p(1.2F, 18, 50, -11), p(2.0F, 18, 50, -11),
                    pl(2.1F, 18, 36, -11), p(2.25F, 24, 46, -18), pl(2.6F, -12, -10.5F, -12)))
            .addAnimation("club_loose", rot(r(0, 0, 0, 0), r(0.6F, -34, 50, -16), r(1.4F, -34, 170, -16), r(2.0F, -34, 300, -16),
                    rl(2.1F, -34, 300, -16), r(2.25F, -10, 340, 24), rl(2.6F, -30, 360, 74)))
            .addAnimation("hull", rot(r(0, 0, 0, 0), r(2.1F, 0, 0, 0), r(2.25F, 3, 0, 4), r(2.4F, -22, 0, 6), rl(2.6F, -90, 0, 0)))
            .addAnimation("hull", pos(p(0, 0, 0, 0), p(2.25F, 0, -1, 0), pl(2.6F, 0, 12, 0)))
            .addAnimation("body", rot(r(0, 0, 0, 0), r(0.5F, 6, 0, 0), r(1.1F, -6, -8, 0), r(2.05F, -12, 0, 0), r(2.14F, 16, 0, 0), r(2.6F, -2, 0, 0)))
            .addAnimation("head", rot(r(0, 0, 0, 0), r(0.35F, 10, -24, 6), r(0.75F, 18, -32, 8), r(1.2F, -32, -6, 0), r(2.05F, -36, 14, 0),
                    rl(2.12F, 26, 14, 8), r(2.3F, 10, -20, -10), r(2.6F, 0, 28, 0)))
            .addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.75F, 18, 0, 0), r(1.2F, 26, 0, 0), r(2.05F, 22, 0, 0), rl(2.12F, 34, 0, 0), r(2.6F, 30, 0, 0)))
            .addAnimation("ear_r", rot(r(0, 0, 0, 0), r(2.05F, 0, 0, 6), rl(2.12F, 0, 0, 26), r(2.6F, 0, 0, -8)))
            .addAnimation("ear_l", rot(r(0, 0, 0, 0), r(2.05F, 0, 0, -6), rl(2.12F, 0, 0, -26), r(2.6F, 0, 0, 8)))
            .addAnimation("arm_r", rot(r(0, 0, 0, 0), r(0.35F, -42, -22, 2), r(0.75F, -52, -30, 2), r(1.2F, -44, -20, 4), r(2.05F, -36, -12, 8), r(2.3F, -60, 0, 24), r(2.6F, -30, 0, 46)))
            .addAnimation("forearm_r", rot(r(0, 0, 0, 0), r(0.75F, -40, 0, 0), r(2.05F, -22, 0, 0), r(2.6F, 18, 0, 0)))
            .addAnimation("hand_r", rot(r(0, 0, 0, 0), r(0.75F, 28, 0, 0), r(2.6F, 10, 0, 0)))
            .addAnimation("arm_l", rot(r(0, 0, 0, 0), r(1.2F, -12, 0, -10), r(2.05F, -20, 0, -14), r(2.3F, -62, 0, -28), r(2.6F, -24, 0, -44)))
            .addAnimation("forearm_l", rot(r(0, 0, 0, 0), r(2.3F, -30, 0, 0), r(2.6F, 8, 0, 0)))
            .addAnimation("leg_r", rot(r(0, 0, 0, 0), r(2.25F, -4, 0, 0), r(2.6F, 4, 0, 10)))
            .addAnimation("leg_l", rot(r(0, 0, 0, 0), r(2.25F, 6, 0, 0), r(2.6F, 4, 0, -10)))
            .addAnimation("foot_r", rot(r(0, 0, 0, 0), r(2.6F, -10, 0, 6)))
            .addAnimation("foot_l", rot(r(0, 0, 0, 0), r(2.6F, -10, 0, -6)))
            .build();

    /** Flat on its back, belly heaving, mouth open, the club lying where it bounced. */
    public static final AnimationDefinition KNOCKED_OUT = AnimationDefinition.Builder.withLength(3.0F).looping()
            .addAnimation("club_loose", pos(pl(0, -12, -10.5F, -12), pl(3, -12, -10.5F, -12)))
            .addAnimation("club_loose", rot(rl(0, -30, 360, 74), rl(3, -30, 360, 74)))
            .addAnimation("hull", rot(rl(0, -90, 0, 0), rl(3, -90, 0, 0)))
            .addAnimation("hull", pos(pl(0, 0, 12, 0), pl(3, 0, 12, 0)))
            .addAnimation("body", rot(r(0, -2, 0, 0), r(1.5F, -3.5F, 0, 0), r(3, -2, 0, 0)))
            .addAnimation("belly", pos(p(0, 0, 0, 0), p(1.5F, 0, 0, -1.4F), p(3, 0, 0, 0)))
            .addAnimation("head", rot(r(0, 0, 28, 0), r(1.5F, 2, 28, 0), r(3, 0, 28, 0)))
            .addAnimation("jaw", rot(r(0, 30, 0, 0), r(1.5F, 36, 0, 0), r(3, 30, 0, 0)))
            .addAnimation("ear_r", rot(rl(0, 0, 0, -8), rl(3, 0, 0, -8)))
            .addAnimation("ear_l", rot(rl(0, 0, 0, 8), rl(3, 0, 0, 8)))
            .addAnimation("arm_r", rot(rl(0, -30, 0, 46), rl(3, -30, 0, 46)))
            .addAnimation("forearm_r", rot(rl(0, 18, 0, 0), rl(3, 18, 0, 0)))
            .addAnimation("hand_r", rot(rl(0, 10, 0, 0), rl(3, 10, 0, 0)))
            .addAnimation("arm_l", rot(rl(0, -24, 0, -44), rl(3, -24, 0, -44)))
            .addAnimation("forearm_l", rot(rl(0, 8, 0, 0), rl(3, 8, 0, 0)))
            .addAnimation("leg_r", rot(rl(0, 4, 0, 10), rl(3, 4, 0, 10)))
            .addAnimation("leg_l", rot(rl(0, 4, 0, -10), rl(3, 4, 0, -10)))
            .addAnimation("foot_r", rot(rl(0, -10, 0, 6), rl(3, -10, 0, 6)))
            .addAnimation("foot_l", rot(rl(0, -10, 0, -6), rl(3, -10, 0, -6)))
            .build();

    /** Heaves itself up, feels for the club at 0.7 s (tick 14) and comes up shaking its head. 1.5 s. */
    public static final AnimationDefinition GET_UP = AnimationDefinition.Builder.withLength(1.5F)
            .addAnimation("club_loose", pos(pl(0, -12, -10.5F, -12), pl(1.5F, -12, -10.5F, -12)))
            .addAnimation("club_loose", rot(rl(0, -30, 360, 74), rl(1.5F, -30, 360, 74)))
            .addAnimation("hull", rot(rl(0, -90, 0, 0), r(0.45F, -58, 0, -8), r(0.8F, -18, 0, 4), r(1.15F, 3, 0, 0), r(1.5F, 0, 0, 0)))
            .addAnimation("hull", pos(pl(0, 0, 12, 0), p(0.45F, 0, 8, 0), p(0.8F, 0, 1, 0), p(1.15F, 0, -1, 0), p(1.5F, 0, 0, 0)))
            .addAnimation("body", rot(r(0, -2, 0, 0), r(0.45F, 22, 0, 0), r(0.8F, 24, 0, 0), r(1.15F, -4, 0, 0), r(1.5F, 0, 0, 0)))
            .addAnimation("head", rot(r(0, 0, 28, 0), r(0.45F, -6, 10, 0), r(0.8F, -6, 0, 0), r(0.95F, -4, -22, 8), r(1.1F, -4, 22, -8), r(1.3F, -2, -10, 3), r(1.5F, 0, 0, 0)))
            .addAnimation("jaw", rot(r(0, 30, 0, 0), r(0.8F, 18, 0, 0), r(1.5F, 0, 0, 0)))
            .addAnimation("arm_r", rot(r(0, -30, 0, 46), r(0.4F, -52, 0, 26), r(0.7F, -44, 0, 10), r(1.15F, -8, 0, 2), r(1.5F, 0, 0, 0)))
            .addAnimation("forearm_r", rot(r(0, 18, 0, 0), r(0.7F, 8, 0, 0), r(1.5F, 0, 0, 0)))
            .addAnimation("hand_r", rot(r(0, 10, 0, 0), r(0.7F, 14, 0, 0), r(1.5F, 0, 0, 0)))
            .addAnimation("arm_l", rot(r(0, -24, 0, -44), r(0.4F, 34, 0, -18), r(0.8F, 18, 0, -10), r(1.5F, 0, 0, 0)))
            .addAnimation("forearm_l", rot(r(0, 8, 0, 0), r(1.5F, 0, 0, 0)))
            .addAnimation("leg_r", rot(r(0, 4, 0, 10), r(0.45F, -62, 0, 4), r(0.8F, -42, 0, 0), r(1.15F, -6, 0, 0), r(1.5F, 0, 0, 0)))
            .addAnimation("shin_r", rot(r(0, 0, 0, 0), r(0.45F, 58, 0, 0), r(0.8F, 44, 0, 0), r(1.15F, 8, 0, 0), r(1.5F, 0, 0, 0)))
            .addAnimation("foot_r", rot(r(0, -10, 0, 6), r(0.45F, 0, 0, 0), r(1.5F, 0, 0, 0)))
            .addAnimation("leg_l", rot(r(0, 4, 0, -10), r(0.45F, -62, 0, -4), r(0.8F, -42, 0, 0), r(1.15F, -6, 0, 0), r(1.5F, 0, 0, 0)))
            .addAnimation("shin_l", rot(r(0, 0, 0, 0), r(0.45F, 58, 0, 0), r(0.8F, 44, 0, 0), r(1.15F, 8, 0, 0), r(1.5F, 0, 0, 0)))
            .addAnimation("foot_l", rot(r(0, -10, 0, -6), r(0.45F, 0, 0, 0), r(1.5F, 0, 0, 0)))
            .build();
}
