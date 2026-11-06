package com.hogwartsmagic.client.model;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Procedural werewolf animation over the Whulvk rig.
 *
 * <p>Two stances are blended per bone with quaternion slerp (never Euler lerp: the all-fours clips
 * carry body Eulers like (-90, 65, -90) that roll through garbage when lerped):
 * <ul>
 *   <li>upright: the bind pose, hunched, plus a procedural stalking walk;</li>
 *   <li>all fours: the old {@code idle_wolf} / {@code walk_wolf} clips, the gait phase driven by
 *       distance travelled so the paws plant instead of skating.</li>
 * </ul>
 * Everything else (look, breath, tail, ears, jaw, the claw swipe, the howl, the flinch and both
 * transformations) is layered on as rotations about model-space axes, so it reads the same in
 * either stance. Model space: +Y down, -Z forward, X lateral.
 *
 * <p>Pure client math with no entity reference: {@link State} is filled by the model from the
 * entity, or by tools/werewolf_harness for previews.
 */
public final class WerewolfAnimator {
    private WerewolfAnimator() {
    }

    /** Blocks walked per all-fours gait cycle (walk_wolf is 1.6667 s per cycle). Its planted paws travel
     *  0.6-1.1 blocks a cycle (tools/werewolf_harness gait check); 2.0 trades a little skating for a cadence
     *  that does not flicker at chase speed. */
    public static final float QUAD_STRIDE = 2.0F;
    /** Blocks walked per upright stalking cycle (two steps). */
    public static final float BIPED_STRIDE = 2.4F;
    public static final float DEG = Mth.DEG_TO_RAD;

    private static final Vector3f AX_X = new Vector3f(1, 0, 0);
    private static final Vector3f AX_Y = new Vector3f(0, 1, 0);
    private static final Vector3f AX_Z = new Vector3f(0, 0, 1);

    /** Everything the animator needs from the entity for one frame. */
    public static final class State {
        /** Entity age in ticks, partial tick included. */
        public float age;
        /** Horizontal distance walked, in blocks (partial-tick interpolated). */
        public float stride;
        /** 0..1 how much it is moving. */
        public float move;
        /** 0..1 all-fours weight (chasing). */
        public float quad;
        /** Look, degrees, as vanilla's netHeadYaw / headPitch. */
        public float headYaw;
        public float headPitch;
        /** 0..1 aggression: snarl, ears back. */
        public float snarl;
        /** 0..1 progress of a claw swipe, or negative when none. */
        public float attack = -1;
        public boolean attackLeft;
        /** 0..1 progress of a howl, or negative. */
        public float howl = -1;
        /** 0..1 progress of villager to werewolf, or negative. */
        public float toWolf = -1;
        /** 0..1 progress of werewolf to villager, or negative. */
        public float toHuman = -1;
        /** 0..1 hurt flash (hurtTime / 10). */
        public float hurt;
    }

    // ---------------------------------------------------------------------------------- poses

    /** Snapshot of every part's local pose, in {@code getAllParts()} order. */
    private static final class Snap {
        final float[] v;

        Snap(int n) {
            v = new float[n * 6];
        }

        void read(List<ModelPart> parts) {
            for (int i = 0; i < parts.size(); i++) {
                ModelPart p = parts.get(i);
                int o = i * 6;
                v[o] = p.x; v[o + 1] = p.y; v[o + 2] = p.z;
                v[o + 3] = p.xRot; v[o + 4] = p.yRot; v[o + 5] = p.zRot;
            }
        }
    }

    private static final Quaternionf QA = new Quaternionf();
    private static final Quaternionf QB = new Quaternionf();
    private static final Vector3f EUL = new Vector3f();

    /** parts := slerp(a, b, t) bone by bone. */
    private static void blend(List<ModelPart> parts, Snap a, Snap b, float t) {
        for (int i = 0; i < parts.size(); i++) {
            ModelPart p = parts.get(i);
            int o = i * 6;
            p.x = Mth.lerp(t, a.v[o], b.v[o]);
            p.y = Mth.lerp(t, a.v[o + 1], b.v[o + 1]);
            p.z = Mth.lerp(t, a.v[o + 2], b.v[o + 2]);
            if (a.v[o + 3] == b.v[o + 3] && a.v[o + 4] == b.v[o + 4] && a.v[o + 5] == b.v[o + 5]) {
                p.xRot = a.v[o + 3]; p.yRot = a.v[o + 4]; p.zRot = a.v[o + 5];
                continue;
            }
            QA.rotationZYX(a.v[o + 5], a.v[o + 4], a.v[o + 3]);
            QB.rotationZYX(b.v[o + 5], b.v[o + 4], b.v[o + 3]);
            QA.slerp(QB, t);
            setLocal(p, QA);
        }
    }

    private static final org.joml.Matrix3f MAT = new org.joml.Matrix3f();

    /**
     * Exact inverse of ModelPart's {@code rotationZYX(z, y, x)} (R = Rz Ry Rx). JOML's own
     * getEulerAnglesZYX is lossy and breaks at gimbal lock, where the all-fours body pose sits.
     */
    private static void setLocal(ModelPart p, Quaternionf q) {
        MAT.set(q);
        // JOML: get(column, row); r_ij = row i, column j
        float r20 = MAT.get(0, 2), r21 = MAT.get(1, 2), r22 = MAT.get(2, 2);
        float r10 = MAT.get(0, 1), r00 = MAT.get(0, 0), r01 = MAT.get(1, 0), r11 = MAT.get(1, 1);
        float sy = Mth.clamp(-r20, -1, 1);
        p.yRot = (float) Math.asin(sy);
        if (Math.abs(sy) < 0.99995F) {
            p.xRot = (float) Math.atan2(r21, r22);
            p.zRot = (float) Math.atan2(r10, r00);
        } else {
            p.zRot = 0;
            p.xRot = (float) Math.atan2(r01 * sy, r11);
        }
    }

    private static final Quaternionf QP = new Quaternionf();
    private static final Quaternionf QL = new Quaternionf();
    private static final Quaternionf QR = new Quaternionf();
    private static final Vector3f AX = new Vector3f();

    /**
     * Rotates {@code part} about a model-space axis through its own pivot. {@code chain} is its
     * ancestors from the root down (excluding the part).
     */
    static void rotW(ModelPart part, ModelPart[] chain, Vector3f axis, float angle) {
        if (angle == 0) return;
        QP.identity();
        for (ModelPart c : chain) {
            QP.mul(QL.rotationZYX(c.zRot, c.yRot, c.xRot));
        }
        // axis into the parent's local frame
        QP.conjugate(QR);
        QR.transform(axis, AX);
        QL.rotationAxis(angle, AX.x, AX.y, AX.z);
        QL.mul(QR.rotationZYX(part.zRot, part.yRot, part.xRot));
        setLocal(part, QL);
    }

    // ---------------------------------------------------------------------------------- rig

    /** Per-model cache: bone chains + scratch snapshots. */
    static final class Rig {
        final List<ModelPart> parts;
        final Snap rest, upright, fours, tmp;
        final ModelPart[] cBody, cLower, cMid, cUpper, cNeckL, cNeckU, cHead, cJaw;
        final ModelPart[] cArmL, cUArmL, cLArmL, cHandL, cArmR, cUArmR, cLArmR, cHandR;
        final ModelPart[] cTighL, cLegL, cShankL, cFootL, cTighR, cLegR, cShankR, cFootR;
        final ModelPart[] cTail, cTail1, cTail2, cTail3, cTail4;

        Rig(WhulvkWerewolfLycanModel m) {
            parts = m.root().getAllParts().toList();
            rest = new Snap(parts.size()); upright = new Snap(parts.size());
            fours = new Snap(parts.size()); tmp = new Snap(parts.size());
            m.root().getAllParts().forEach(ModelPart::resetPose);
            rest.read(parts);
            cBody = new ModelPart[]{m.root};
            ModelPart[] b = {m.root, m.body};
            cLower = b;
            cMid = add(b, m.lowerBody);
            cUpper = add(cMid, m.middleBody);
            ModelPart[] up = add(cUpper, m.upperBody);
            cNeckL = up;
            cNeckU = add(up, m.lowerNeck);
            cHead = add(cNeckU, m.upperNeck);
            cJaw = add(cHead, m.head);
            cArmL = up; cUArmL = add(up, m.armLeft); cLArmL = add(cUArmL, m.upperArmLeft); cHandL = add(cLArmL, m.lowerArmLeft);
            cArmR = up; cUArmR = add(up, m.armRight); cLArmR = add(cUArmR, m.upperArmRight); cHandR = add(cLArmR, m.lowerArmRight);
            cTighL = b; cLegL = add(b, m.tighLeft); cShankL = add(cLegL, m.legLeft); cFootL = add(cShankL, m.shankLeft);
            cTighR = b; cLegR = add(b, m.tighRight); cShankR = add(cLegR, m.legRight); cFootR = add(cShankR, m.shankRight);
            cTail = b; cTail1 = add(b, m.tail); cTail2 = add(cTail1, m.tail_1); cTail3 = add(cTail2, m.tail_2); cTail4 = add(cTail3, m.tail_3);
        }

        private static ModelPart[] add(ModelPart[] a, ModelPart p) {
            ModelPart[] r = java.util.Arrays.copyOf(a, a.length + 1);
            r[a.length] = p;
            return r;
        }
    }

    private static final java.util.WeakHashMap<WhulvkWerewolfLycanModel, Rig> RIGS = new java.util.WeakHashMap<>();

    static Rig rig(WhulvkWerewolfLycanModel m) {
        return RIGS.computeIfAbsent(m, Rig::new);
    }

    // ---------------------------------------------------------------------------------- main

    public static void apply(WhulvkWerewolfLycanModel m, State s) {
        Rig r = rig(m);
        List<ModelPart> parts = r.parts;
        float t = s.age;
        scale(m, 1.0F);                                    // resetPose leaves scale alone

        // Stance weight: swipes, howls and transformations all happen upright.
        float quad = s.quad;
        if (s.attack >= 0) quad *= 1 - bump(s.attack, 0.0F, 0.18F, 0.8F, 1.0F);
        if (s.howl >= 0) quad *= 1 - bump(s.howl, 0.0F, 0.15F, 0.85F, 1.0F);
        if (s.toWolf >= 0 || s.toHuman >= 0) quad = 0;

        // ---- all fours: idle_wolf blended into walk_wolf, gait phase from distance
        if (quad > 0.001F) {
            pose(m, parts, WhulvkLycanWolfIdleAnimations.idle_wolf, (long) ((t * 50.0F) % 2250.0F));
            r.tmp.read(parts);
            float ph = frac(s.stride / QUAD_STRIDE);
            pose(m, parts, WhulvkLycanWolfWalkAnimations.walk_wolf, (long) (ph * 1666.7F));
            r.fours.read(parts);
            blend(parts, r.tmp, r.fours, smooth(s.move));
            r.fours.read(parts);
        }

        // ---- upright
        parts.forEach(ModelPart::resetPose);
        upright(m, r, s);
        if (quad > 0.001F) {
            r.upright.read(parts);
            blend(parts, r.upright, r.fours, smooth(quad));
        }

        // ---- layers that read the same in either stance
        tail(m, r, s, quad);
        face(m, r, s, quad);
        if (s.attack >= 0) swipe(m, r, s);
        if (s.howl >= 0) howl(m, r, s.howl, s.age);
        if (s.toWolf >= 0) toWolf(m, r, s.toWolf, s.age);
        if (s.toHuman >= 0) toHuman(m, r, s.toHuman, s.age);
        look(m, r, s, quad);
        if (s.hurt > 0) flinch(m, r, s.hurt);
    }

    private static void pose(WhulvkWerewolfLycanModel m, List<ModelPart> parts, AnimationDefinition def, long ms) {
        parts.forEach(ModelPart::resetPose);
        KeyframeAnimations.animate(m, def, ms, 1.0F, SCRATCH);
    }

    private static final Vector3f SCRATCH = new Vector3f();

    // ---------------------------------------------------------------------------------- upright

    private static void upright(WhulvkWerewolfLycanModel m, Rig r, State s) {
        float t = s.age;
        float breath = Mth.sin(t * 0.105F);                 // ~3 s
        float mv = smooth(s.move);
        float ph = s.stride / BIPED_STRIDE * Mth.TWO_PI;
        float sw = Mth.sin(ph);
        float cw = Mth.cos(ph);

        // hunch a little deeper than the bind pose, breathe through the ribs
        rotW(m.lowerBody, r.cLower, AX_X, (4 + 6 * mv) * DEG);
        rotW(m.upperBody, r.cUpper, AX_X, (breath * 2.2F) * DEG);
        rotW(m.upperBody, r.cUpper, AX_Y, sw * 6 * mv * DEG);
        m.root.y += mv * 0.9F * Mth.abs(cw) - 0.4F * mv;

        // legs: thigh swings, knee lifts the paw through the swing, the foot stays flat-ish
        leg(m.tighLeft, m.legLeft, m.footLeft, r.cTighL, r.cLegL, r.cFootL, sw, cw, mv);
        leg(m.tighRight, m.legRight, m.footRight, r.cTighR, r.cLegR, r.cFootR, -sw, -cw, mv);

        // arms hang heavy and swing against the legs; idle sway when still
        float sway = Mth.sin(t * 0.06F) * 3;
        rotW(m.armLeft, r.cArmL, AX_X, (-sw * 18 * mv + sway + breath * 1.5F) * DEG);
        rotW(m.armRight, r.cArmR, AX_X, (sw * 18 * mv + sway + breath * 1.5F) * DEG);
        rotW(m.lowerArmLeft, r.cLArmL, AX_X, (-8 - 10 * mv * Math.max(0, -sw)) * DEG);
        rotW(m.lowerArmRight, r.cLArmR, AX_X, (-8 - 10 * mv * Math.max(0, sw)) * DEG);
        fingers(m, Mth.sin(t * 0.043F) * 0.5F + 0.5F);
    }

    private static ModelPart[] addTo(ModelPart[] a, ModelPart p) {
        ModelPart[] out = java.util.Arrays.copyOf(a, a.length + 1);
        out[a.length] = p;
        return out;
    }

    private static void leg(ModelPart tigh, ModelPart leg, ModelPart foot, ModelPart[] cT, ModelPart[] cL, ModelPart[] cF,
                            float sw, float cw, float mv) {
        float swing = -sw * 32 * mv;                       // + = thigh back
        float lift = Math.max(0, cw) * 46 * mv;            // knee folds while the leg travels forward
        rotW(tigh, cT, AX_X, (swing - lift * 0.35F) * DEG);
        rotW(leg, cL, AX_X, lift * DEG);
        rotW(foot, cF, AX_X, (-swing * 0.6F - lift * 0.55F) * DEG);
    }

    private static void fingers(WhulvkWerewolfLycanModel m, float curl) {
        float c = (6 + curl * 14) * DEG;
        ModelPart[] fs = {m.upperFingerLeft_2, m.upperFingerLeft_3, m.upperFingerLeft_4, m.upperFingerLeft_5,
                m.upperFinger_7, m.upperFinger_8, m.upperFinger_9, m.upperFinger_10};
        for (ModelPart f : fs) f.zRot += c;
        ModelPart[] ls = {m.lowerFingerLeft_2, m.lowerFingerLeft_3, m.lowerFingerLeft_4, m.lowerFingerLeft_5,
                m.lowerFinger_6, m.lowerFinger_7, m.lowerFinger_8, m.lowerFinger_9};
        for (ModelPart f : ls) f.zRot += c * 0.8F;
    }

    // ---------------------------------------------------------------------------------- layers

    private static void tail(WhulvkWerewolfLycanModel m, Rig r, State s, float quad) {
        float t = s.age;
        float q = smooth(quad);
        // the bind tail kinks up; let it hang, and stream out behind when running on all fours
        m.tail.zRot += (-0.45F - 0.75F * q) ;
        m.tail_1.zRot += -0.25F + 0.2F * q;
        m.tail_2.zRot += -0.55F + 0.25F * q;
        m.tail_3.zRot += 0.25F;
        m.tail_4.zRot += 0.2F;
        float speed = 0.09F + 0.12F * s.move;
        float amp = (9 + 6 * s.snarl) * DEG * (1 - 0.5F * s.move);
        ModelPart[] seg = {m.tail, m.tail_1, m.tail_2, m.tail_3, m.tail_4};
        for (int i = 0; i < seg.length; i++) {
            seg[i].yRot += Mth.sin(t * speed - i * 0.7F) * amp * (0.6F + 0.15F * i);
            seg[i].zRot += Mth.sin(t * speed * 2 - i * 0.9F) * 2.5F * DEG * s.move;
        }
    }

    private static void face(WhulvkWerewolfLycanModel m, Rig r, State s, float quad) {
        float t = s.age;
        // jaw: breathing gap, wider pant when moving, bared when aggressive
        float jaw = 3 + 2 * Mth.sin(t * 0.21F) + 6 * s.move * (0.5F + 0.5F * Mth.sin(t * 0.5F)) + 7 * s.snarl;
        m.jaw.zRot += jaw * DEG;
        m.tongue.zRot -= jaw * 0.4F * DEG;
        // ears: flattened when aggressive, idle twitches otherwise
        float tw = twitch(t, 11.3F) * 22 * (1 - s.snarl);
        float tw2 = twitch(t + 37, 13.7F) * 22 * (1 - s.snarl);
        m.earLeft.zRot += (-28 * s.snarl - tw) * DEG;
        m.earRight.zRot += (-28 * s.snarl - tw2) * DEG;
        // brows knit in a snarl
        m.eyebrowLeft.zRot += -10 * s.snarl * DEG;
        m.eyebrowRight.zRot += -10 * s.snarl * DEG;
    }

    /** 0..1..0 blip once every {@code period} seconds, ~0.3 s long. */
    private static float twitch(float age, float period) {
        float x = frac(age / 20.0F / period) * period;   // seconds into the period
        return x < 0.3F ? Mth.sin(x / 0.3F * Mth.PI) : 0;
    }

    private static void look(WhulvkWerewolfLycanModel m, Rig r, State s, float quad) {
        float yaw = Mth.clamp(s.headYaw, -70, 70) * DEG;
        float pitch = Mth.clamp(s.headPitch, -40, 40) * DEG;
        if (s.toWolf >= 0 || s.toHuman >= 0) {
            float k = s.toWolf >= 0 ? bump(s.toWolf, 0.85F, 1.0F, 2, 3) : 0;
            yaw *= k; pitch *= k;
        }
        rotW(m.upperNeck, r.cNeckU, AX_X, pitch * 0.35F);
        rotW(m.upperNeck, r.cNeckU, AX_Y, -yaw * 0.35F);
        rotW(m.head, r.cHead, AX_X, pitch * 0.65F);
        rotW(m.head, r.cHead, AX_Y, -yaw * 0.65F);
    }

    // ---------------------------------------------------------------------------------- one-shots

    /** Rear up, wind one arm back over the shoulder, rake it down across, snap the jaws. */
    private static void swipe(WhulvkWerewolfLycanModel m, Rig r, State s) {
        float p = s.attack;
        float wind = bump(p, 0.0F, 0.28F, 0.32F, 0.45F);
        float rake = bump(p, 0.3F, 0.48F, 0.6F, 1.0F);
        float lunge = bump(p, 0.15F, 0.45F, 0.55F, 1.0F);
        boolean left = s.attackLeft;
        ModelPart arm = left ? m.armLeft : m.armRight;
        ModelPart low = left ? m.lowerArmLeft : m.lowerArmRight;
        ModelPart[] ca = left ? r.cArmL : r.cArmR;
        ModelPart[] cl = left ? r.cLArmL : r.cLArmR;
        float side = left ? 1 : -1;

        rotW(m.lowerBody, r.cLower, AX_X, (-10 * wind + 14 * lunge) * DEG);
        rotW(m.upperBody, r.cUpper, AX_Y, (side * 22 * wind - side * 30 * rake) * DEG);
        // arm: up and back, then down and across the body
        rotW(arm, ca, AX_X, (-150 * wind + 40 * rake - 60 * lunge * (1 - rake)) * DEG);
        rotW(arm, ca, AX_Z, (side * -25 * wind + side * 35 * rake) * DEG);
        rotW(low, cl, AX_X, (-50 * wind + 10 * rake) * DEG);
        // the other arm braces
        ModelPart other = left ? m.armRight : m.armLeft;
        rotW(other, left ? r.cArmR : r.cArmL, AX_X, (-35 * lunge) * DEG);
        // head drives forward, jaw snaps on the rake
        rotW(m.head, r.cHead, AX_X, (12 * lunge) * DEG);
        m.jaw.zRot += (28 * bump(p, 0.1F, 0.35F, 0.45F, 0.6F)) * DEG;
        m.earLeft.zRot -= 25 * wind * DEG;
        m.earRight.zRot -= 25 * wind * DEG;
    }

    /** Sit back on the haunches, arch, muzzle to the sky, jaw open, chest out. */
    static void howl(WhulvkWerewolfLycanModel m, Rig r, float p, float age) {
        float k = bump(p, 0.0F, 0.15F, 0.85F, 1.0F);
        float sing = k * (0.85F + 0.15F * Mth.sin(age * 0.6F));
        m.root.y += 2.5F * k;
        rotW(m.lowerBody, r.cLower, AX_X, -18 * k * DEG);
        rotW(m.upperBody, r.cUpper, AX_X, -14 * k * DEG);
        rotW(m.lowerNeck, r.cNeckL, AX_X, -20 * k * DEG);
        rotW(m.upperNeck, r.cNeckU, AX_X, -22 * k * DEG);
        rotW(m.head, r.cHead, AX_X, -38 * k * DEG);
        m.jaw.zRot += 34 * sing * DEG;
        m.earLeft.zRot -= 30 * k * DEG;
        m.earRight.zRot -= 30 * k * DEG;
        rotW(m.armLeft, r.cArmL, AX_Z, 18 * k * DEG);
        rotW(m.armRight, r.cArmR, AX_Z, -18 * k * DEG);
        rotW(m.armLeft, r.cArmL, AX_X, 12 * k * DEG);
        rotW(m.armRight, r.cArmR, AX_X, 12 * k * DEG);
        legsBend(m, r, 22 * k);
    }

    private static void legsBend(WhulvkWerewolfLycanModel m, Rig r, float deg) {
        rotW(m.tighLeft, r.cTighL, AX_X, -deg * DEG);
        rotW(m.tighRight, r.cTighR, AX_X, -deg * DEG);
        rotW(m.legLeft, r.cLegL, AX_X, deg * 1.6F * DEG);
        rotW(m.legRight, r.cLegR, AX_X, deg * 1.6F * DEG);
        rotW(m.footLeft, r.cFootL, AX_X, -deg * 0.6F * DEG);
        rotW(m.footRight, r.cFootR, AX_X, -deg * 0.6F * DEG);
    }

    /** Curled crouch, arms clutching the head: the shape a transformation is fought in. */
    private static void crouch(WhulvkWerewolfLycanModel m, Rig r, float k) {
        if (k <= 0) return;
        m.root.y += 7 * k;
        legsBend(m, r, 48 * k);
        rotW(m.lowerBody, r.cLower, AX_X, 30 * k * DEG);
        rotW(m.upperBody, r.cUpper, AX_X, 22 * k * DEG);
        rotW(m.head, r.cHead, AX_X, 30 * k * DEG);
        rotW(m.armLeft, r.cArmL, AX_X, -115 * k * DEG);
        rotW(m.armRight, r.cArmR, AX_X, -115 * k * DEG);
        rotW(m.armLeft, r.cArmL, AX_Z, -20 * k * DEG);
        rotW(m.armRight, r.cArmR, AX_Z, 20 * k * DEG);
        rotW(m.lowerArmLeft, r.cLArmL, AX_X, -75 * k * DEG);
        rotW(m.lowerArmRight, r.cLArmR, AX_X, -75 * k * DEG);
        m.tail.zRot -= 0.5F * k;
    }

    /** Spasms: fast jerks of spine, shoulders and head, as a deterministic noise of time. */
    private static void convulse(WhulvkWerewolfLycanModel m, Rig r, float amp, float age) {
        if (amp <= 0) return;
        float a = amp * DEG;
        rotW(m.upperBody, r.cUpper, AX_X, jerk(age, 1) * 12 * a);
        rotW(m.upperBody, r.cUpper, AX_Y, jerk(age, 2) * 10 * a);
        rotW(m.head, r.cHead, AX_Z, jerk(age, 3) * 16 * a);
        rotW(m.head, r.cHead, AX_X, jerk(age, 4) * 14 * a);
        rotW(m.armLeft, r.cArmL, AX_Z, jerk(age, 5) * 14 * a);
        rotW(m.armRight, r.cArmR, AX_Z, jerk(age, 6) * 14 * a);
        m.jaw.zRot += Math.max(0, jerk(age, 7)) * 30 * a;
        m.root.y += jerk(age, 8) * 0.8F * amp;
    }

    /** Smooth-stepped random value in [-1, 1], retargeted every ~2.5 ticks. */
    private static float jerk(float age, int ch) {
        float x = age / 2.5F;
        int i = Mth.floor(x);
        float f = x - i;
        f = f * f * (3 - 2 * f);
        return Mth.lerp(f, hashS(i, ch), hashS(i + 1, ch));
    }

    private static float hashS(int i, int ch) {
        int h = i * 374761393 + ch * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 32767.5F - 1;
    }

    /**
     * Villager to werewolf, 3 s. Starts villager-sized and curled (the swap is hidden in a burst of
     * smoke), spasms while it grows, rears up into a roar, settles.
     */
    static void toWolf(WhulvkWerewolfLycanModel m, Rig r, float p, float age) {
        float crouchK = 1 - smoothstep(0.55F, 0.75F, p);
        float arch = bump(p, 0.55F, 0.7F, 0.82F, 1.0F);
        crouch(m, r, crouchK);
        convulse(m, r, 1.6F * (1 - smoothstep(0.1F, 0.6F, p)) + 0.4F, age);
        if (arch > 0) {
            rotW(m.lowerBody, r.cLower, AX_X, -16 * arch * DEG);
            rotW(m.upperBody, r.cUpper, AX_X, -16 * arch * DEG);
            rotW(m.head, r.cHead, AX_X, -34 * arch * DEG);
            rotW(m.armLeft, r.cArmL, AX_Z, 40 * arch * DEG);
            rotW(m.armRight, r.cArmR, AX_Z, -40 * arch * DEG);
            rotW(m.armLeft, r.cArmL, AX_X, -20 * arch * DEG);
            rotW(m.armRight, r.cArmR, AX_X, -20 * arch * DEG);
            m.jaw.zRot += 36 * arch * DEG;
        }
        scale(m, Mth.lerp(smoothstep(0.05F, 0.7F, p), 0.74F, 1.0F));
    }

    /** Werewolf to villager, 1.5 s: folds up, shudders and shrinks; the smoke hides the swap back. */
    static void toHuman(WhulvkWerewolfLycanModel m, Rig r, float p, float age) {
        crouch(m, r, smoothstep(0.0F, 0.45F, p));
        convulse(m, r, 1.2F * smoothstep(0.1F, 0.4F, p), age);
        scale(m, Mth.lerp(smoothstep(0.2F, 1.0F, p), 1.0F, 0.76F));
    }

    private static void scale(WhulvkWerewolfLycanModel m, float k) {
        m.root.xScale = k; m.root.yScale = k; m.root.zScale = k;
    }

    private static void flinch(WhulvkWerewolfLycanModel m, Rig r, float h) {
        float k = Mth.sin(h * Mth.PI);
        rotW(m.upperBody, r.cUpper, AX_X, -9 * k * DEG);
        rotW(m.head, r.cHead, AX_X, -14 * k * DEG);
        m.jaw.zRot += 16 * k * DEG;
        m.earLeft.zRot -= 30 * k * DEG;
        m.earRight.zRot -= 30 * k * DEG;
    }

    // ---------------------------------------------------------------------------------- curves

    /** 0 before a, ramps to 1 by b, holds to c, back to 0 by d (smooth). */
    static float bump(float x, float a, float b, float c, float d) {
        if (x <= a || x >= d) return 0;
        if (x < b) return smoothstep(a, b, x);
        if (x <= c) return 1;
        return 1 - smoothstep(c, d, x);
    }

    static float smoothstep(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }

    static float smooth(float x) {
        return smoothstep(0, 1, x);
    }

    static float frac(float x) {
        return x - Mth.floor(x);
    }

    // ---------------------------------------------------------------------------------- harness

    /** Preview entry points for tools/werewolf_harness (pose:WerewolfAnimator.name@t). */
    public static void hStand(WhulvkWerewolfLycanModel m, float t) {
        State s = new State(); s.age = t; apply(m, s);
    }

    public static void hStalk(WhulvkWerewolfLycanModel m, float phase) {
        State s = new State(); s.age = 40; s.move = 1; s.stride = phase * BIPED_STRIDE; apply(m, s);
    }

    public static void hRun(WhulvkWerewolfLycanModel m, float phase) {
        State s = new State(); s.age = 40; s.move = 1; s.quad = 1; s.snarl = 1; s.stride = phase * QUAD_STRIDE; apply(m, s);
    }

    public static void hBlend(WhulvkWerewolfLycanModel m, float q) {
        State s = new State(); s.age = 40; s.move = 1; s.quad = q; s.stride = 0.3F * QUAD_STRIDE; apply(m, s);
    }

    public static void hSwipe(WhulvkWerewolfLycanModel m, float p) {
        State s = new State(); s.age = 40; s.attack = p; s.snarl = 1; s.quad = 1; apply(m, s);
    }

    public static void hHowl(WhulvkWerewolfLycanModel m, float p) {
        State s = new State(); s.age = 40; s.howl = p; apply(m, s);
    }

    public static void hToWolf(WhulvkWerewolfLycanModel m, float p) {
        State s = new State(); s.age = 40 + p * 60; s.toWolf = p; apply(m, s);
    }

    public static void hToHuman(WhulvkWerewolfLycanModel m, float p) {
        State s = new State(); s.age = 40 + p * 30; s.toHuman = p; apply(m, s);
    }

    public static void hLook(WhulvkWerewolfLycanModel m, float yaw) {
        State s = new State(); s.age = 40; s.headYaw = yaw; s.headPitch = 20; apply(m, s);
    }
}
