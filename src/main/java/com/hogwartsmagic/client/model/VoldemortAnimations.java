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
                    r(0.55F, -88, 0, 0), r(0.9F, -37, 0, 4)))
            .addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(0.3F, -20, 0, -25), r(0.9F, -9, 0, 0)))
            .addAnimation("body", rot(r(0F, 0, 0, 0), r(0.15F, -3, 10, 0), r(0.3F, 6, -12, 0), r(0.9F, 3, -6, 0)))
            .addAnimation("cape", rot(r(0F, 0, 0, 0), r(0.3F, 14, 0, 0), r(0.9F, 4, 0, 0)))
            .build();

    /** Hold the wand levelled at the target (hold this after a {@link #CAST}); 0.5 s to settle. */
    public static final AnimationDefinition POINT = AnimationDefinition.Builder.withLength(0.5F)
            .addAnimation("right_arm", rot(r(0F, -37, 0, 4), r(0.5F, -88, 0, 0)))
            .addAnimation("left_arm", rot(r(0F, -9, 0, 0), r(0.5F, -6, 0, -18)))
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

    // ---- the boss fight's own clips, started from the entity's synced action (VoldemortMoves) ---------------

    private static Keyframe p(float t, float x, float y, float z) {
        return new Keyframe(t, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
    }

    private static AnimationChannel pos(Keyframe... keys) {
        return new AnimationChannel(AnimationChannel.Targets.POSITION, keys);
    }

    /** Re-forming out of the summoning smoke: hunched and drawn in, he rises and throws his arms wide; 3 s. */
    public static final AnimationDefinition ENTRANCE = AnimationDefinition.Builder.withLength(3.0F)
            .addAnimation("body", rot(r(0F, 30, 0, 0), r(1.2F, 0, 0, 0), r(1.6F, -10, 0, 0), r(2.4F, -8, 0, 0), r(3.0F, 0, 0, 0)))
            .addAnimation("body", pos(p(0F, 0, -3, 0), p(1.2F, 0, 0, 0), p(3.0F, 0, 0, 0)))
            .addAnimation("head", rot(r(0F, 35, 0, 0), r(1.2F, 0, 0, 0), r(1.6F, -25, 0, 0), r(2.4F, -22, 0, 0), r(3.0F, 0, 0, 0)))
            .addAnimation("right_arm", rot(r(0F, -20, 0, -25), r(1.2F, -10, 0, 10), r(1.7F, -35, 0, 80), r(2.5F, -33, 0, 82), r(3.0F, 0, 0, 0)))
            .addAnimation("left_arm", rot(r(0F, -20, 0, 25), r(1.2F, -10, 0, -10), r(1.7F, -35, 0, -80), r(2.5F, -33, 0, -82), r(3.0F, 0, 0, 0)))
            .addAnimation("cape", rot(r(0F, 0, 0, 0), r(1.6F, 35, 0, 0), r(2.5F, 20, 0, 0), r(3.0F, 0, 0, 0)))
            .addAnimation("right_skirt", rot(r(0F, 0, 0, 0), r(1.6F, 0, 0, 10), r(3.0F, 0, 0, 0)))
            .addAnimation("left_skirt", rot(r(0F, 0, 0, 0), r(1.6F, 0, 0, -10), r(3.0F, 0, 0, 0)))
            .build();

    /** Crucio: the wand thrust out and twisted in his grip as the curse holds, the off hand clawing; 1.4 s. */
    public static final AnimationDefinition CRUCIO = AnimationDefinition.Builder.withLength(1.4F)
            .addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(0.2F, -100, 0, 5), r(0.35F, -92, 0, 0), r(0.45F, -94, 6, 8),
                    r(0.55F, -90, -6, -6), r(0.65F, -95, 6, 8), r(0.75F, -90, -6, -7), r(0.85F, -94, 5, 8), r(0.95F, -90, -5, -6),
                    r(1.05F, -93, 5, 6), r(1.2F, -90, 0, 0), r(1.4F, -37, 0, 4)))
            .addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(0.3F, -60, -20, -20), r(1.2F, -55, -15, -22), r(1.4F, -9, 0, 0)))
            .addAnimation("body", rot(r(0F, 0, 0, 0), r(0.3F, 5, -10, 0), r(1.2F, 4, -8, 0), r(1.4F, 2, -4, 0)))
            .addAnimation("head", rot(r(0F, 0, 0, 0), r(0.3F, 6, 0, 0), r(1.4F, 0, 0, 0)))
            .build();

    /** Sectumsempra: three slashes, down-left, down-right and overhead, at 0.25, 0.55 and 0.85 s; 1.2 s. */
    public static final AnimationDefinition SLASH = AnimationDefinition.Builder.withLength(1.2F)
            .addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(0.15F, -150, 0, 60), r(0.27F, -60, 0, -40), r(0.42F, -150, 0, -50),
                    r(0.57F, -60, 0, 40), r(0.72F, -172, 0, 0), r(0.87F, -45, 0, 0), r(1.2F, -37, 0, 4)))
            .addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(0.27F, -20, 0, -35), r(0.57F, -25, 0, -20), r(0.87F, -30, 0, -40), r(1.2F, -9, 0, 0)))
            .addAnimation("body", rot(r(0F, 0, 0, 0), r(0.27F, 4, -15, 0), r(0.57F, 4, 15, 0), r(0.87F, 12, 0, 0), r(1.2F, 0, 0, 0)))
            .addAnimation("cape", rot(r(0F, 0, 0, 0), r(0.3F, 12, 0, 6), r(0.6F, 12, 0, -6), r(0.9F, 22, 0, 0), r(1.2F, 0, 0, 0)))
            .build();

    /** Fiendfyre: the hands rise from low behind him gathering the fire overhead, then hurl it at 1.25 s; 1.6 s. */
    public static final AnimationDefinition FIENDFYRE = AnimationDefinition.Builder.withLength(1.6F)
            .addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(0.3F, 20, 0, 20), r(1.15F, -150, -10, 25), r(1.25F, -85, 0, 10), r(1.6F, -37, 0, 4)))
            .addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(0.3F, 20, 0, -20), r(1.15F, -150, 10, -25), r(1.25F, -60, 0, -30), r(1.6F, -9, 0, 0)))
            .addAnimation("body", rot(r(0F, 0, 0, 0), r(0.3F, 8, 0, 0), r(1.1F, -10, 0, 0), r(1.25F, 12, 0, 0), r(1.6F, 4, 0, 0)))
            .addAnimation("head", rot(r(0F, 0, 0, 0), r(1.1F, -20, 0, 0), r(1.25F, 5, 0, 0), r(1.6F, 0, 0, 0)))
            .addAnimation("cape", rot(r(0F, 0, 0, 0), r(1.1F, 10, 0, 0), r(1.25F, 30, 0, 0), r(1.6F, 8, 0, 0)))
            .build();

    /** The dark shockwave: arms up and back as he lifts, then down and the ground slams at 1.2 s; 1.8 s. */
    public static final AnimationDefinition SHOCKWAVE = AnimationDefinition.Builder.withLength(1.8F)
            .addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(1.1F, -170, 0, 15), r(1.22F, -20, 0, 35), r(1.8F, -10, 0, 12)))
            .addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(1.1F, -170, 0, -15), r(1.22F, -20, 0, -35), r(1.8F, -10, 0, -12)))
            .addAnimation("body", rot(r(0F, 0, 0, 0), r(1.1F, -14, 0, 0), r(1.22F, 28, 0, 0), r(1.5F, 22, 0, 0), r(1.8F, 6, 0, 0)))
            .addAnimation("body", pos(p(0F, 0, 0, 0), p(1.1F, 0, 1.5F, 0), p(1.22F, 0, -2, 0), p(1.8F, 0, 0, 0)))
            .addAnimation("head", rot(r(0F, 0, 0, 0), r(1.1F, -20, 0, 0), r(1.22F, 20, 0, 0), r(1.8F, 0, 0, 0)))
            .addAnimation("cape", rot(r(0F, 0, 0, 0), r(1.1F, 8, 0, 0), r(1.25F, 55, 0, 0), r(1.8F, 10, 0, 0)))
            .addAnimation("right_skirt", rot(r(0F, 0, 0, 0), r(1.25F, -10, 0, 14), r(1.8F, 0, 0, 0)))
            .addAnimation("left_skirt", rot(r(0F, 0, 0, 0), r(1.25F, -10, 0, -14), r(1.8F, 0, 0, 0)))
            .build();

    /** Half his life gone: he rises, arms flung up and wide, screaming, then lashes out at 2.5 s; 3.5 s. */
    public static final AnimationDefinition PHASE = AnimationDefinition.Builder.withLength(3.5F)
            .addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(0.6F, -20, 0, 110), r(1.0F, -24, 4, 104), r(1.4F, -18, -4, 114),
                    r(1.8F, -24, 4, 104), r(2.2F, -18, -4, 114), r(2.4F, -22, 0, 110), r(2.55F, -40, 0, 40), r(3.5F, 0, 0, 0)))
            .addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(0.6F, -20, 0, -110), r(1.0F, -24, -4, -104), r(1.4F, -18, 4, -114),
                    r(1.8F, -24, -4, -104), r(2.2F, -18, 4, -114), r(2.4F, -22, 0, -110), r(2.55F, -40, 0, -40), r(3.5F, 0, 0, 0)))
            .addAnimation("head", rot(r(0F, 0, 0, 0), r(0.6F, -35, 0, 0), r(2.4F, -38, 0, 0), r(2.55F, 15, 0, 0), r(3.5F, 0, 0, 0)))
            .addAnimation("body", rot(r(0F, 0, 0, 0), r(0.6F, -15, 0, 0), r(2.4F, -16, 0, 0), r(2.55F, 20, 0, 0), r(3.5F, 0, 0, 0)))
            .addAnimation("cape", rot(r(0F, 0, 0, 0), r(0.6F, 50, 0, 0), r(1.2F, 40, 0, 5), r(1.8F, 55, 0, -5), r(2.4F, 45, 0, 0),
                    r(2.6F, 65, 0, 0), r(3.5F, 0, 0, 0)))
            .addAnimation("right_skirt", rot(r(0F, 0, 0, 0), r(0.6F, 0, 0, 16), r(2.6F, 0, 0, 22), r(3.5F, 0, 0, 0)))
            .addAnimation("left_skirt", rot(r(0F, 0, 0, 0), r(0.6F, 0, 0, -16), r(2.6F, 0, 0, -22), r(3.5F, 0, 0, 0)))
            .build();

    /** Calling the storm down: the wand goes up to the sky, trembling, then points at 1.25 s; 1.5 s. */
    public static final AnimationDefinition STRIKE = AnimationDefinition.Builder.withLength(1.5F)
            .addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(0.5F, -178, 0, 8), r(0.7F, -174, 3, 10), r(0.9F, -179, -3, 7),
                    r(1.1F, -175, 3, 9), r(1.25F, -90, 0, 0), r(1.5F, -37, 0, 4)))
            .addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(0.5F, -30, 0, -50), r(1.25F, -20, 0, -30), r(1.5F, -9, 0, 0)))
            .addAnimation("head", rot(r(0F, 0, 0, 0), r(0.5F, -30, 0, 0), r(1.1F, -32, 0, 0), r(1.25F, 0, 0, 0)))
            .addAnimation("body", rot(r(0F, 0, 0, 0), r(0.5F, -8, 0, 0), r(1.25F, 6, 0, 0), r(1.5F, 0, 0, 0)))
            .build();

    /** Dying: he staggers back, reaches up and out as he comes apart into ash, and sags; 3.5 s, held. */
    public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(3.5F)
            .addAnimation("body", rot(r(0F, 0, 0, 0), r(0.3F, -14, 0, 0), r(2.0F, -8, 0, 4), r(3.5F, 5, 0, 0)))
            .addAnimation("head", rot(r(0F, 0, 0, 0), r(0.3F, -30, 0, 0), r(2.0F, -40, 0, 0), r(3.5F, -10, 0, 0)))
            .addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(0.3F, -25, 0, 40), r(1.0F, -40, 0, 60), r(1.2F, -44, 4, 56),
                    r(1.4F, -40, -4, 62), r(2.0F, -50, 0, 75), r(3.5F, -30, 0, 50)))
            .addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(0.3F, -25, 0, -40), r(1.0F, -40, 0, -60), r(1.2F, -44, -4, -56),
                    r(1.4F, -40, 4, -62), r(2.0F, -50, 0, -75), r(3.5F, -30, 0, -50)))
            .addAnimation("cape", rot(r(0F, 0, 0, 0), r(0.3F, 20, 0, 0), r(2.0F, 30, 0, 4), r(3.5F, 12, 0, 0)))
            .build();

    /** The clip for a {@code VoldemortMoves} action id, or null for none (an int switch: no enum switch-map here). */
    public static AnimationDefinition forAction(int action) {
        switch (action) {
            case 1: return ENTRANCE;
            case 2: return CAST;
            case 3: return CRUCIO;
            case 4: return SLASH;
            case 5: return FIENDFYRE;
            case 6: return SHOCKWAVE;
            case 7: return PHASE;
            case 8: return STRIKE;
            default: return null;
        }
    }
}
