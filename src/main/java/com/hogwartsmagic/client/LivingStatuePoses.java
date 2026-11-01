package com.hogwartsmagic.client;

/**
 * The Living Statue's animation, as pure arithmetic: state in, joint values out.
 *
 * <p>Deliberately free of every Minecraft class, so it can be compiled and run on its own and
 * checked number for number against build_scripts/generate_living_statue_model.py, which holds a
 * line-for-line mirror of {@link #evaluate} and renders it. That is how the previews the
 * generator draws are known to be what the game draws. {@link LivingStatueModel} only copies the
 * result onto its parts.
 *
 * <p>The tables between the {@code generated} markers are written by the generator: key poses
 * stated as intent (where the right fist is, which way the blade points, where the feet stand)
 * and solved there into joint angles. Edit the generator, never the tables.
 */
public final class LivingStatuePoses {

    // <generated:poses>
    // Timing shared with LivingStatueEntity (read from its source on every run).
    static final int WAKE_TICKS = 50;
    static final float STRIKE_AT = 0.45F;

    // Pose vector slots. Angles in radians, offsets in model units.
    private static final int DROP = 0;
    private static final int ROOT_Z = 1;
    private static final int HIPS_X = 2;
    private static final int HIPS_Y = 3;
    private static final int HIPS_Z = 4;
    private static final int TORSO_X = 5;
    private static final int TORSO_Y = 6;
    private static final int TORSO_Z = 7;
    private static final int HEAD_X = 8;
    private static final int HEAD_Y = 9;
    private static final int HEAD_Z = 10;
    private static final int HEAD_DX = 11;
    private static final int HEAD_DY = 12;
    private static final int HEAD_DZ = 13;
    private static final int RARM_X = 14;
    private static final int RARM_Y = 15;
    private static final int RARM_Z = 16;
    private static final int RFORE_X = 17;
    private static final int HAL_X = 18;
    private static final int HAL_Y = 19;
    private static final int HAL_Z = 20;
    private static final int HAL_DX = 21;
    private static final int HAL_DY = 22;
    private static final int HAL_DZ = 23;
    private static final int LARM_X = 24;
    private static final int LARM_Y = 25;
    private static final int LARM_Z = 26;
    private static final int LFORE_X = 27;
    private static final int TWO_HAND = 28;
    private static final int GRIP_K = 29;
    private static final int RFOOT_Z = 30;
    private static final int LFOOT_Z = 31;
    private static final int RLIFT = 32;
    private static final int LLIFT = 33;
    private static final int FLAT = 34;
    private static final int RFOOT_P = 35;
    private static final int LFOOT_P = 36;
    private static final int SLOTS = 37;

    // Rig dimensions the IK runs on, read off the generated mesh.
    private static final float UPPER_ARM = 9.5F;
    private static final float FOREARM = 8.0F;
    private static final float THIGH = 9.0F;
    private static final float SHIN = 7.5F;
    private static final float ANKLE_Y = 21.5F;
    private static final float HIP_Y = 5.0F;
    private static final float[] R_SHOULDER = {-9.0F, -15.0F, 0.0F};
    private static final float[] L_SHOULDER = {9.0F, -15.0F, 0.0F};
    private static final float[] L_POLE = {0.4F, 0.3F, 1.0F};

    // Key poses, SOLVED by the generator from intent (grip, blade direction, feet).
    static final float[] REST = {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0524F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.1275F, -0.2882F, 0.2926F, -1.6432F, 1.5154F, 0.3078F, 0.2718F, 0.0F, 0.0F, 0.0F, 0.5868F, 0.2731F, 0.1693F, -2.4925F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F};
    static final float[] WAKE_LOOK = {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.1571F, -0.384F, 0.0F, 0.0F, 0.0F, 0.0F, 0.1275F, -0.2882F, 0.2926F, -1.6432F, 1.5154F, 0.3078F, 0.2718F, 0.0F, 0.0F, 0.0F, 0.5868F, 0.2731F, 0.1693F, -2.4925F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F};
    static final float[] WAKE_FREE = {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.0524F, 0.0F, 0.0F, -0.1047F, -0.1745F, 0.0F, 0.0F, 0.0F, 0.0F, 0.097F, -0.2889F, 0.2907F, -1.5747F, 1.5292F, 0.316F, 0.2608F, 0.0F, 0.0F, 0.0F, -0.1396F, 0.0F, -0.1745F, -0.3142F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F};
    static final float[] WAKE_ARMS = {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.1047F, 0.0F, 0.0F, -0.0873F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0322F, -0.3674F, 0.049F, -1.2625F, 1.8318F, -0.703F, -0.0881F, 0.0F, 0.0F, 0.0F, -0.1578F, 0.4586F, 0.4849F, -1.4067F, 1.0F, -10.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F};
    static final float[] WAKE_STEP = {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.1047F, 0.0F, 0.0F, -0.0873F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0322F, -0.3674F, 0.049F, -1.2625F, 1.8318F, -0.703F, -0.0881F, 0.0F, 0.0F, 0.0F, -0.1578F, 0.4586F, 0.4849F, -1.4067F, 1.0F, -10.0F, 0.8F, -4.5F, 0.0F, 5.0F, 1.0F, 0.0F, 0.22F};
    static final float[] READY = {0.8F, 0.0F, 0.0F, 0.0F, 0.0F, 0.1047F, 0.0F, 0.0F, -0.0873F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0661F, -0.3672F, 0.0495F, -1.3982F, 1.9631F, -0.6894F, -0.1726F, 0.0F, 0.0F, 0.0F, -0.1505F, 0.461F, 0.5288F, -1.4793F, 1.0F, -10.0F, 1.5F, -2.5F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F};
    static final float[] WIND0 = {0.6F, 0.0F, 0.0F, 0.2443F, 0.0F, -0.1047F, 0.384F, 0.0F, -0.0349F, -0.4189F, 0.0F, 0.0F, 0.0F, 0.0F, -0.1612F, 0.4022F, 1.9448F, -2.187F, -1.7651F, 0.7676F, -2.5909F, 0.0F, 0.0F, 0.0F, -0.2309F, 0.4097F, 1.162F, -0.5802F, 1.0F, 9.0F, 1.5F, -2.5F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F};
    static final float[] STRIKE0 = {2.6F, -1.0F, 0.0F, -0.2443F, 0.0F, 0.2793F, -0.4189F, 0.0F, -0.2094F, 0.4538F, 0.0F, 0.0F, 0.0F, 0.0F, -0.0647F, -0.4056F, -0.1055F, -1.451F, -3.1271F, 0.2906F, 0.4343F, 0.0F, 0.0F, 0.0F, 0.4358F, 0.4359F, 1.0155F, -0.7968F, 1.0F, 9.0F, 1.5F, -2.5F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F};
    static final float[] WIND1 = {0.6F, 0.0F, 0.0F, -0.2443F, 0.0F, -0.1047F, -0.4189F, 0.0F, -0.0349F, 0.4538F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3401F, -0.1756F, -1.8515F, -1.2159F, -0.6665F, 0.3817F, 2.1206F, 0.0F, 0.0F, 0.0F, 0.4946F, 0.3969F, 1.2202F, -1.7789F, 1.0F, 9.0F, 1.5F, -2.5F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F};
    static final float[] STRIKE1 = {2.6F, -1.0F, 0.0F, 0.2443F, 0.0F, 0.2793F, 0.384F, 0.0F, -0.2094F, -0.4189F, 0.0F, 0.0F, 0.0F, 0.0F, -0.5438F, -0.4094F, -0.1236F, -0.9062F, 3.0912F, -0.4365F, 0.4527F, 0.0F, 0.0F, 0.0F, 0.3345F, 0.452F, 0.8843F, -1.2308F, 1.0F, 9.0F, 1.5F, -2.5F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F};
    static final float[] WIND2 = {0.2F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2094F, 0.0F, 0.0F, -0.1047F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.0153F, 0.1372F, -2.494F, -1.009F, -1.8913F, -0.4766F, 2.6662F, 0.0F, 0.0F, 0.0F, -0.4635F, -0.0015F, 2.2172F, -0.7545F, 1.0F, 6.0F, 1.5F, -2.5F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F};
    static final float[] STRIKE2 = {4.0F, -1.5F, 0.0F, 0.0F, 0.0F, 0.5236F, 0.0F, 0.0F, -0.4189F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0197F, -0.4636F, -0.6524F, -1.9774F, -2.6788F, -0.7703F, 0.1398F, 0.0F, 0.0F, 0.0F, 0.9332F, 0.3603F, 1.3608F, -1.5958F, 1.0F, 9.0F, 2.5F, -4.5F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F};
    static final float[] CRUMBLE_A = {10.0F, 0.0F, 0.1396F, 0.0F, 0.0F, 0.4887F, 0.0F, 0.1047F, 0.4538F, 0.1396F, 0.0F, 0.0F, 0.0F, 0.0F, -0.4887F, 0.0F, 0.2443F, -0.2094F, 1.0766F, -0.6707F, 0.2595F, 0.0F, 0.0F, 0.0F, -0.4189F, 0.0F, -0.2793F, -0.2443F, 0.0F, 0.0F, -0.1348F, -0.1348F, 0.0F, 0.0F, 1.0F, -3.1416F, -3.1416F};
    static final float[] CRUMBLE_B = {10.0F, 0.0F, 0.2094F, 0.0F, 0.0F, 0.8029F, 0.0F, 0.1571F, -1.508F, 1.2813F, -0.3061F, 5.2057F, 4.5341F, -11.5913F, -0.6729F, -0.3432F, 0.1313F, -0.8851F, 0.6146F, -0.5074F, 1.657F, 0.0F, 0.0F, 0.0F, -0.1047F, 0.0F, -0.4538F, -0.1745F, 0.0F, 0.0F, -0.1348F, -0.1348F, 0.0F, 0.0F, 1.0F, -3.1416F, -3.1416F};
    // </generated:poses>

    /** Bones {@link #evaluate} writes, in this order, six floats each: dx, dy, dz, xRot, yRot, zRot. */
    public static final String[] BONES = {"root", "hips", "torso", "head", "right_arm",
            "right_forearm", "halberd", "left_arm", "left_forearm", "right_leg", "right_shin",
            "right_foot", "left_leg", "left_shin", "left_foot"};
    public static final int B_ROOT = 0, B_HIPS = 1, B_TORSO = 2, B_HEAD = 3, B_RARM = 4,
            B_RFORE = 5, B_HAL = 6, B_LARM = 7, B_LFORE = 8, B_RLEG = 9, B_RSHIN = 10,
            B_RFOOT = 11, B_LLEG = 12, B_LSHIN = 13, B_LFOOT = 14;
    public static final int OUT_SIZE = BONES.length * 6;

    /** Wake beats, in ticks out of WAKE_TICKS: head, hand off the sword, halberd up, step, land. */
    private static final float[] WAKE_BEATS = {5.0F, 13.0F, 21.0F, 33.0F, 41.0F, 46.0F};
    private static final float WALK_FREQ = 0.55F;
    private static final int[] TRIPLES = {HIPS_X, TORSO_X, HEAD_X, RARM_X, HAL_X, LARM_X};

    private LivingStatuePoses() {
    }

    /**
     * Everything the model needs for one frame.
     *
     * @param wake      ticks into the rise: 0 dormant .. WAKE_TICKS risen
     * @param combo     blow in flight (0, 1, 2) or -1
     * @param swing     0..1 through that blow
     * @param crumble   0..1 through the fall, or 0
     * @param reform    0..1 through reassembling, or 0
     * @param out       {@link #OUT_SIZE} floats, see {@link #BONES}
     * @return true when the statue is animate (the hurt tint may show)
     */
    public static boolean evaluate(float wake, int combo, float swing, float crumble, float reform,
                                   float netHeadYaw, float headPitch, float limbSwing,
                                   float limbSwingAmount, float[] out) {
        float[] p = new float[SLOTS];
        float look = 0.0F;
        float walk = 0.0F;
        if (wake <= 0.0F) {
            System.arraycopy(REST, 0, p, 0, SLOTS);
        } else if (wake < WAKE_TICKS) {
            wakePose(p, wake);
        } else {
            System.arraycopy(READY, 0, p, 0, SLOTS);
            look = 1.0F;
            walk = Math.min(1.0F, limbSwingAmount * 2.2F);
            if (combo >= 0) {
                float committed = swingPose(p, combo, swing);
                look *= 1.0F - 0.8F * committed;
                walk *= 1.0F - committed;
            }
        }

        if (reform > 0.0F) {
            // Reassembling at its post: the fall run backwards, finishing at attention.
            if (reform < 0.5F) {
                blendInto(p, CRUMBLE_B, CRUMBLE_A, smooth(reform / 0.5F));
            } else {
                blendInto(p, CRUMBLE_A, REST, smooth((reform - 0.5F) / 0.5F));
            }
            look = walk = 0.0F;
        } else if (crumble > 0.0F) {
            // Gravity: each stage of the fall accelerates. The knees go first and fast, the
            // head and the halberd follow it down.
            float[] from = p.clone();
            if (crumble < 0.35F) {
                float t = crumble / 0.35F;
                blendInto(p, from, CRUMBLE_A, t * t);
            } else {
                float t = (crumble - 0.35F) / 0.65F;
                blendInto(p, CRUMBLE_A, CRUMBLE_B, t * t);
            }
            look = walk = 0.0F;
        }

        if (look > 0.0F) {
            // A statue turns from the waist as well as the neck; all head and no torso reads as
            // a man in a suit.
            float yaw = clamp(netHeadYaw, -55.0F, 55.0F) * ((float) Math.PI / 180F) * look;
            float pitch = clamp(headPitch, -30.0F, 30.0F) * ((float) Math.PI / 180F) * look;
            p[HEAD_Y] += yaw * 0.65F;
            p[TORSO_Y] += yaw * 0.35F;
            p[HEAD_X] += pitch * 0.8F;
        }
        resolve(p, walk, limbSwing, out);
        return wake >= WAKE_TICKS && crumble <= 0.0F && reform <= 0.0F;
    }

    /** The rise: one joint at a time, each grinding loose. */
    private static void wakePose(float[] p, float w) {
        float[] b = WAKE_BEATS;
        if (w < b[0]) {
            System.arraycopy(REST, 0, p, 0, SLOTS);
        } else if (w < b[1]) {
            blendInto(p, REST, WAKE_LOOK, grind((w - b[0]) / (b[1] - b[0])));
        } else if (w < b[2]) {
            blendInto(p, WAKE_LOOK, WAKE_FREE, grind((w - b[1]) / (b[2] - b[1])));
        } else if (w < b[3]) {
            blendInto(p, WAKE_FREE, WAKE_ARMS, grind((w - b[2]) / (b[3] - b[2])));
        } else if (w < b[4]) {
            blendInto(p, WAKE_ARMS, WAKE_STEP, smooth((w - b[3]) / (b[4] - b[3])));
        } else if (w < b[5]) {
            // The foot comes down hard: accelerate into the floor rather than easing onto it.
            float t = (w - b[4]) / (b[5] - b[4]);
            blendInto(p, WAKE_STEP, READY, t * t);
        } else {
            System.arraycopy(READY, 0, p, 0, SLOTS);
            // The landing jolts through it once and is gone.
            float t = (w - b[5]) / (WAKE_TICKS - b[5]);
            p[DROP] += 0.9F * (1.0F - t) * (1.0F - t);
        }
    }

    /**
     * One blow. {@code s} runs 0..1 over the whole beat; the blade arrives at STRIKE_AT, the same
     * instant the server lands the damage. A wind-up that hangs at the top, a strike that
     * accelerates, then it stays stuck in the follow-through before it grinds back to guard.
     *
     * @return how committed the body is (0 at guard, 1 mid-blow), to damp the walk and the look
     */
    private static float swingPose(float[] p, int combo, float s) {
        float[] wind = combo == 0 ? WIND0 : combo == 1 ? WIND1 : WIND2;
        float[] strike = combo == 0 ? STRIKE0 : combo == 1 ? STRIKE1 : STRIKE2;
        boolean heavy = combo == 2;
        float windEnd = heavy ? 0.33F : 0.27F;
        float hangEnd = windEnd + (heavy ? 0.05F : 0.04F);
        float holdEnd = heavy ? 0.68F : 0.60F;
        if (s < windEnd) {
            float t = s / windEnd;
            blendInto(p, READY, wind, 1.0F - (1.0F - t) * (1.0F - t));
        } else if (s < hangEnd) {
            System.arraycopy(wind, 0, p, 0, SLOTS);
        } else if (s < STRIKE_AT) {
            float t = (s - hangEnd) / (STRIKE_AT - hangEnd);
            blendInto(p, wind, strike, t * t);
        } else if (s < holdEnd) {
            // Stuck in the follow-through; the heavy blow's impact rings up through it once.
            System.arraycopy(strike, 0, p, 0, SLOTS);
            float t = (s - STRIKE_AT) / (holdEnd - STRIKE_AT);
            p[DROP] += (heavy ? 1.0F : 0.4F) * (1.0F - t) * (1.0F - t);
        } else {
            float t = smooth((s - holdEnd) / (1.0F - holdEnd));
            blendInto(p, strike, READY, t);
            return 1.0F - t;
        }
        return 1.0F;
    }

    /** Pose vector -> joints: the left fist onto the haft, the feet onto the floor, the march. */
    private static void resolve(float[] p, float walk, float limbSwing, float[] out) {
        java.util.Arrays.fill(out, 0.0F);
        float[] o = new float[4];
        if (p[TWO_HAND] > 0.0F) {
            float[] t = haftPoint(p);
            solveArm(L_SHOULDER, t[0], t[1], t[2], L_POLE, o);
            float w = Math.min(1.0F, p[TWO_HAND]);
            float[] ik = {o[0], o[1], o[2]};
            slerpTriple(p, LARM_X, ik, 0, w, p, LARM_X);
            p[LFORE_X] += (o[3] - p[LFORE_X]) * w;
        }

        // The march. Thighs swing on a flattened sine, so each leg holds at the end of its stride
        // instead of pendulumming through it; the forward leg lifts its knee; the body drops by
        // exactly what the spread legs lose in height, so the planted sole stays on the floor.
        float swingR = 0.0F;
        float liftR = 0.0F;
        float liftL = 0.0F;
        float bob = 0.0F;
        if (walk > 0.0F) {
            float ph = limbSwing * WALK_FREQ;
            float sn = (float) Math.sin(ph);
            float cs = (float) Math.cos(ph);
            float shaped = Math.signum(sn) * (float) Math.pow(Math.abs(sn), 0.7F);
            swingR = -0.40F * shaped * walk;
            liftR = Math.max(0.0F, cs) * 0.80F * walk;
            liftL = Math.max(0.0F, -cs) * 0.80F * walk;
            bob = (THIGH + SHIN) * (1.0F - (float) Math.cos(swingR));
            p[TORSO_Z] += 0.05F * cs * walk;
            p[HIPS_Z] -= 0.03F * cs * walk;
            p[TORSO_Y] += 0.07F * shaped * walk;
        }

        out[B_ROOT * 6 + 1] = p[DROP] + bob;
        out[B_ROOT * 6 + 2] = p[ROOT_Z];
        rot(out, B_HIPS, p[HIPS_X], p[HIPS_Y], p[HIPS_Z]);
        rot(out, B_TORSO, p[TORSO_X], p[TORSO_Y], p[TORSO_Z]);
        rot(out, B_HEAD, p[HEAD_X], p[HEAD_Y], p[HEAD_Z]);
        out[B_HEAD * 6] = p[HEAD_DX];
        out[B_HEAD * 6 + 1] = p[HEAD_DY];
        out[B_HEAD * 6 + 2] = p[HEAD_DZ];
        rot(out, B_RARM, p[RARM_X], p[RARM_Y], p[RARM_Z]);
        rot(out, B_RFORE, p[RFORE_X], 0.0F, 0.0F);
        rot(out, B_HAL, p[HAL_X], p[HAL_Y], p[HAL_Z]);
        out[B_HAL * 6] = p[HAL_DX];
        out[B_HAL * 6 + 1] = p[HAL_DY];
        out[B_HAL * 6 + 2] = p[HAL_DZ];
        rot(out, B_LARM, p[LARM_X], p[LARM_Y], p[LARM_Z]);
        rot(out, B_LFORE, p[LFORE_X], 0.0F, 0.0F);
        leg(p, B_RLEG, p[RFOOT_Z], p[RLIFT], p[RFOOT_P], swingR, liftR, out);
        leg(p, B_LLEG, p[LFOOT_Z], p[LLIFT], p[LFOOT_P], -swingR, liftL, out);
    }

    /**
     * Planted against the pose's own sink, not the march's bob: the bob exists to put the swung
     * legs' soles back on the floor, and planting against it would bend the knees to cancel it.
     */
    private static void leg(float[] p, int thighBone, float footZ, float lift, float pitch,
                            float swing, float kneeLift, float[] out) {
        float[] o = new float[2];
        legIk(HIP_Y, 0.0F, ANKLE_Y - lift - p[DROP], footZ - p[ROOT_Z], o);
        float t = o[0] + swing;
        float s = o[1] + kneeLift;
        out[thighBone * 6 + 3] = t;
        out[(thighBone + 1) * 6 + 3] = s;
        out[(thighBone + 2) * 6 + 3] = -(t + s) * p[FLAT] + pitch;
    }

    private static void rot(float[] out, int bone, float x, float y, float z) {
        out[bone * 6 + 3] = x;
        out[bone * 6 + 4] = y;
        out[bone * 6 + 5] = z;
    }

    // --- curves -------------------------------------------------------------------------------

    static float smooth(float t) {
        t = clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * Stone working loose: most of the way, a catch where it creeps, then it breaks free for the
     * rest. The stall is what separates a statue grinding round from a man turning his head.
     */
    static float grind(float t) {
        t = clamp(t, 0.0F, 1.0F);
        if (t < 0.45F) {
            return 0.55F * smooth(t / 0.45F);
        }
        if (t < 0.6F) {
            return 0.55F + 0.05F * (t - 0.45F) / 0.15F;
        }
        return 0.6F + 0.4F * smooth((t - 0.6F) / 0.4F);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // --- blending: scalars lerp, joint rotations slerp ----------------------------------------

    /**
     * out = a -> b at t. Lerping Euler angles is what the old statue did, and between two
     * far-apart orientations it takes a path no joint would: the halberd tumbled end over end
     * through its wielder. Joint rotations go the short way round instead.
     */
    private static void blendInto(float[] out, float[] a, float[] b, float t) {
        for (int i = 0; i < SLOTS; i++) {
            out[i] = a[i] + (b[i] - a[i]) * t;
        }
        for (int i : TRIPLES) {
            slerpTriple(a, i, b, i, t, out, i);
        }
    }

    private static void slerpTriple(float[] a, int ai, float[] b, int bi, float t, float[] out,
                                    int oi) {
        double[] qa = quat(a[ai], a[ai + 1], a[ai + 2]);
        double[] qb = quat(b[bi], b[bi + 1], b[bi + 2]);
        double d = qa[0] * qb[0] + qa[1] * qb[1] + qa[2] * qb[2] + qa[3] * qb[3];
        if (d < 0.0) {
            for (int k = 0; k < 4; k++) {
                qb[k] = -qb[k];
            }
            d = -d;
        }
        double[] q = new double[4];
        if (d > 0.9995) {
            for (int k = 0; k < 4; k++) {
                q[k] = qa[k] + (qb[k] - qa[k]) * t;
            }
        } else {
            double th = Math.acos(d);
            double sa = Math.sin((1.0 - t) * th) / Math.sin(th);
            double sb = Math.sin(t * th) / Math.sin(th);
            for (int k = 0; k < 4; k++) {
                q[k] = qa[k] * sa + qb[k] * sb;
            }
        }
        double n = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        double w = q[0] / n, x = q[1] / n, y = q[2] / n, z = q[3] / n;
        double r00 = 1 - 2 * (y * y + z * z);
        double r10 = 2 * (x * y + w * z);
        double r20 = 2 * (x * z - w * y);
        double r21 = 2 * (y * z + w * x);
        double r22 = 1 - 2 * (x * x + y * y);
        out[oi] = (float) Math.atan2(r21, r22);
        out[oi + 1] = (float) Math.asin(Math.max(-1.0, Math.min(1.0, -r20)));
        out[oi + 2] = (float) Math.atan2(r10, r00);
    }

    /** Rz Ry Rx as a quaternion (w, x, y, z) -- ModelPart's composition order. */
    private static double[] quat(float x, float y, float z) {
        double cx = Math.cos(x / 2.0), sx = Math.sin(x / 2.0);
        double cy = Math.cos(y / 2.0), sy = Math.sin(y / 2.0);
        double cz = Math.cos(z / 2.0), sz = Math.sin(z / 2.0);
        return new double[]{cz * cy * cx + sz * sy * sx, cz * cy * sx - sz * sy * cx,
                cz * sy * cx + sz * cy * sx, sz * cy * cx - cz * sy * sx};
    }

    // --- IK -----------------------------------------------------------------------------------

    /** Row-major Rz Ry Rx. */
    private static double[] matrix(double x, double y, double z) {
        double cx = Math.cos(x), sx = Math.sin(x), cy = Math.cos(y), sy = Math.sin(y);
        double cz = Math.cos(z), sz = Math.sin(z);
        return new double[]{
                cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx,
                sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx,
                -sy, cy * sx, cy * cx};
    }

    private static double[] mul(double[] a, double[] b) {
        double[] r = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
        return r;
    }

    /** The point GRIP_K along the haft from the right fist, in torso space. */
    private static float[] haftPoint(float[] p) {
        double[] ra = matrix(p[RARM_X], p[RARM_Y], p[RARM_Z]);
        double ex = R_SHOULDER[0] + ra[1] * UPPER_ARM;
        double ey = R_SHOULDER[1] + ra[4] * UPPER_ARM;
        double ez = R_SHOULDER[2] + ra[7] * UPPER_ARM;
        double[] rf = mul(ra, matrix(p[RFORE_X], 0.0, 0.0));
        double hx = p[HAL_DX], hy = FOREARM + p[HAL_DY], hz = p[HAL_DZ];
        double gx = ex + rf[0] * hx + rf[1] * hy + rf[2] * hz;
        double gy = ey + rf[3] * hx + rf[4] * hy + rf[5] * hz;
        double gz = ez + rf[6] * hx + rf[7] * hy + rf[8] * hz;
        double[] rh = mul(rf, matrix(p[HAL_X], p[HAL_Y], p[HAL_Z]));
        double k = p[GRIP_K];
        return new float[]{(float) (gx + rh[1] * k), (float) (gy + rh[4] * k),
                (float) (gz + rh[7] * k)};
    }

    /**
     * Two-bone arm IK in torso space -> out[0..3] = arm x, y, z, forearm x. The elbow only folds
     * the fist forward; the chain is swung so the fist lands on the target with the elbow as near
     * the pole as the target allows.
     */
    private static void solveArm(float[] shoulder, float tx, float ty, float tz, float[] pole,
                                 float[] out) {
        double vx = tx - shoulder[0], vy = ty - shoulder[1], vz = tz - shoulder[2];
        double d = Math.sqrt(vx * vx + vy * vy + vz * vz);
        d = Math.max(Math.abs(UPPER_ARM - FOREARM) + 0.05,
                Math.min(UPPER_ARM + FOREARM - 0.05, d));
        double c = (UPPER_ARM * UPPER_ARM + FOREARM * FOREARM - d * d)
                / (2.0 * UPPER_ARM * FOREARM);
        double bend = -(Math.PI - Math.acos(Math.max(-1.0, Math.min(1.0, c))));
        // local frame: a towards the fist, n the elbow's offset from that line, c = a x n
        double hy = UPPER_ARM + FOREARM * Math.cos(bend), hz = FOREARM * Math.sin(bend);
        double hl = Math.sqrt(hy * hy + hz * hz);
        double ay = hy / hl, az = hz / hl;
        double ed = UPPER_ARM * ay;
        double ny = UPPER_ARM - ay * ed, nz = -az * ed;
        double nl = Math.sqrt(ny * ny + nz * nz);
        ny /= nl;
        nz /= nl;
        double cx = ay * nz - az * ny;
        // world frame: A at the target, N the pole made perpendicular to A, C = A x N
        double tl = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double ax = vx / tl, aY = vy / tl, aZ = vz / tl;
        double pd = pole[0] * ax + pole[1] * aY + pole[2] * aZ;
        double nX = pole[0] - ax * pd, nY = pole[1] - aY * pd, nZ = pole[2] - aZ * pd;
        double nL = Math.sqrt(nX * nX + nY * nY + nZ * nZ);
        nX /= nL;
        nY /= nL;
        nZ /= nL;
        double cX = aY * nZ - aZ * nY, cY = aZ * nX - ax * nZ, cZ = ax * nY - aY * nX;
        // R = W L^T, where L's columns are (0, ay, az), (0, ny, nz), (cx, 0, 0)
        double r00 = cX * cx;
        double r10 = cY * cx;
        double r20 = cZ * cx;
        double r21 = aZ * ay + nZ * ny;
        double r22 = aZ * az + nZ * nz;
        out[0] = (float) Math.atan2(r21, r22);
        out[1] = (float) Math.asin(Math.max(-1.0, Math.min(1.0, -r20)));
        out[2] = (float) Math.atan2(r10, r00);
        out[3] = (float) bend;
    }

    /** Planar two-bone leg IK -> out[0] thigh x, out[1] shin x. Knees bend forward only. */
    private static void legIk(float hipY, float hipZ, float ankleY, float ankleZ, float[] out) {
        double dy = ankleY - hipY, dz = ankleZ - hipZ;
        double d = Math.sqrt(dy * dy + dz * dz);
        d = Math.max(Math.abs(THIGH - SHIN) + 0.05, Math.min(THIGH + SHIN - 0.001, d));
        double base = Math.atan2(dz, dy);
        double c = (THIGH * THIGH + d * d - SHIN * SHIN) / (2.0 * THIGH * d);
        double thigh = base - Math.acos(Math.max(-1.0, Math.min(1.0, c)));
        double ky = hipY + THIGH * Math.cos(thigh), kz = hipZ + THIGH * Math.sin(thigh);
        double shinWorld = Math.atan2(ankleZ - kz, ankleY - ky);
        out[0] = (float) thigh;
        out[1] = (float) (shinWorld - thigh);
    }
}
