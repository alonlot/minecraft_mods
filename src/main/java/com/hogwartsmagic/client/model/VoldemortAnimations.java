package com.hogwartsmagic.client.model;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Keyframe clips for {@link VoldemortModel}, cued by name in Pensieve memories ({@code "anims"} on a
 * {@code hogwartsmagic:voldemort} actor; registered in {@code ActorAnimationLibrary}). They are added on top of
 * the model's own procedural pose (breathing, cape, stride), so each starts and ends near zero except where it
 * means to hold: a one-shot holds its last frame until the next cue, so {@link #POINT} and the end of
 * {@link #AVADA} leave the wand levelled at the target. {@link #IDLE} is empty: it hands him back to his own pose.
 *
 * <p>Part names are the model's ({@code body head right_arm left_arm cape right_skirt left_skirt}); a wrong
 * name fails silently, so keep them in step.
 */
public final class VoldemortAnimations {
    private VoldemortAnimations() {
    }

    private static Keyframe r(float t, float x, float y, float z) {
        return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
    }

    private static AnimationChannel rot(Keyframe... keys) {
        return new AnimationChannel(AnimationChannel.Targets.ROTATION, keys);
    }

    /** Nothing: his own breathing, sway and stride. */
    public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(0.05F).build();

    /** A curse thrown: the wand arm whips up and lunges at the target, the body turning into it; 0.9 s. */
    public static final AnimationDefinition CAST = AnimationDefinition.Builder.withLength(0.9F)
            .addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(0.15F, -125, 10, 0), r(0.3F, -92, 0, 0),
                    r(0.55F, -88, 0, 0), r(0.9F, -80, 0, 0)))
            .addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(0.3F, -20, 0, -25), r(0.9F, -10, 0, -12)))
            .addAnimation("body", rot(r(0F, 0, 0, 0), r(0.15F, -3, 10, 0), r(0.3F, 6, -12, 0), r(0.9F, 3, -6, 0)))
            .addAnimation("cape", rot(r(0F, 0, 0, 0), r(0.3F, 14, 0, 0), r(0.9F, 4, 0, 0)))
            .build();

    /** Hold the wand levelled at the target (hold this after a {@link #CAST}); 0.5 s to settle. */
    public static final AnimationDefinition POINT = AnimationDefinition.Builder.withLength(0.5F)
            .addAnimation("right_arm", rot(r(0F, -80, 0, 0), r(0.5F, -88, 0, 0)))
            .addAnimation("left_arm", rot(r(0F, -10, 0, -12), r(0.5F, -6, 0, -18)))
            .addAnimation("body", rot(r(0F, 3, -6, 0), r(0.5F, 2, -8, 0)))
            .build();

    /**
     * The Killing Curse, 3.4 s: the wand goes up over his head and back while the off hand opens wide and the
     * head tips back; it trembles as it gathers; at 2.6 s it slashes down and forward and holds on the target.
     */
    public static final AnimationDefinition AVADA = AnimationDefinition.Builder.withLength(3.4F)
            .addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(0.6F, -165, 0, 10), r(1.2F, -172, 4, 12),
                    r(1.6F, -168, -3, 9), r(2.0F, -175, 4, 13), r(2.3F, -170, -4, 10), r(2.5F, -178, 3, 12),
                    r(2.7F, -95, 0, 0), r(2.85F, -82, 0, 0), r(3.4F, -88, 0, 0)))
            .addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(0.8F, -35, 0, -65), r(2.5F, -40, 0, -75),
                    r(2.75F, -15, 0, -30), r(3.4F, -8, 0, -15)))
            .addAnimation("head", rot(r(0F, 0, 0, 0), r(0.8F, -22, 0, 0), r(2.5F, -26, 0, 0),
                    r(2.75F, 6, 0, 0), r(3.4F, 2, 0, 0)))
            .addAnimation("body", rot(r(0F, 0, 0, 0), r(0.8F, -8, 0, 0), r(2.5F, -11, 4, 0),
                    r(2.75F, 9, -10, 0), r(3.4F, 4, -6, 0)))
            .addAnimation("cape", rot(r(0F, 0, 0, 0), r(2.5F, 6, 0, 0), r(2.8F, 30, 0, 0), r(3.4F, 10, 0, 0)))
            .build();

    /** Both hands rise forward and open, palms up, as if lifting something out of the air (fire, the Mark); 2 s, held. */
    public static final AnimationDefinition CONJURE = AnimationDefinition.Builder.withLength(2.0F)
            .addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(1.2F, -105, -15, 25), r(2.0F, -128, -15, 30)))
            .addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(1.2F, -100, 15, -25), r(2.0F, -124, 15, -30)))
            .addAnimation("head", rot(r(0F, 0, 0, 0), r(2.0F, -18, 0, 0)))
            .addAnimation("body", rot(r(0F, 0, 0, 0), r(2.0F, -6, 0, 0)))
            .addAnimation("cape", rot(r(0F, 0, 0, 0), r(2.0F, 18, 0, 0)))
            .build();

    /** The arms thrown open, the Dark Lord displaying himself; 1.6 s, held. */
    public static final AnimationDefinition ARMS_SPREAD = AnimationDefinition.Builder.withLength(1.6F)
            .addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(1.0F, -30, 0, 70), r(1.6F, -28, 0, 74)))
            .addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(1.0F, -30, 0, -70), r(1.6F, -28, 0, -74)))
            .addAnimation("head", rot(r(0F, 0, 0, 0), r(1.0F, -12, 0, 0), r(1.6F, -14, 0, 0)))
            .addAnimation("right_skirt", rot(r(0F, 0, 0, 0), r(1.0F, 0, 0, 6)))
            .addAnimation("left_skirt", rot(r(0F, 0, 0, 0), r(1.0F, 0, 0, -6)))
            .addAnimation("cape", rot(r(0F, 0, 0, 0), r(1.0F, 16, 0, 0), r(1.6F, 12, 0, 0)))
            .build();

    /** A high cold laugh: the head goes back, the shoulders shake, the hands open; 2.2 s. */
    public static final AnimationDefinition LAUGH = AnimationDefinition.Builder.withLength(2.2F)
            .addAnimation("head", rot(r(0F, 0, 0, 0), r(0.3F, -24, 0, 0), r(0.45F, -18, 0, 2), r(0.6F, -26, 0, 0),
                    r(0.75F, -19, 0, -2), r(0.9F, -27, 0, 0), r(1.05F, -20, 0, 2), r(1.2F, -26, 0, 0),
                    r(1.6F, -20, 0, 0), r(2.2F, 0, 0, 0)))
            .addAnimation("body", rot(r(0F, 0, 0, 0), r(0.3F, -6, 0, 0), r(0.45F, -3, 0, 0), r(0.6F, -7, 0, 0),
                    r(0.75F, -3, 0, 0), r(0.9F, -7, 0, 0), r(1.05F, -3, 0, 0), r(1.2F, -6, 0, 0), r(2.2F, 0, 0, 0)))
            .addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(0.4F, -30, 0, -30), r(1.6F, -28, 0, -34), r(2.2F, 0, 0, 0)))
            .addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(0.4F, -18, 0, 18), r(1.6F, -20, 0, 20), r(2.2F, 0, 0, 0)))
            .build();
}
