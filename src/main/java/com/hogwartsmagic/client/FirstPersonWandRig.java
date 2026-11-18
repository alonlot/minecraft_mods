package com.hogwartsmagic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * The first-person wand arm, posed in view space. A pose is three things: where the hand is on screen, which
 * way the forearm runs (elbow to hand), and which way the wand points (the wrist). Each gesture is a few key
 * poses at set ticks of the cast, joined by a smooth spline that starts and ends at the rest pose, so the
 * hand snaps through the strike and then drifts back. At rest the forearm stands up at the edge of the screen
 * with the wand angled in towards the middle; it breathes, lags behind fast turns of the view, and settles a
 * little differently after every cast. Visual only.
 *
 * Coordinates are those of the hand render: x right, y up, -z forward, about 0.7 deep. Keys are written for
 * the right hand and mirrored for the left.
 */
final class FirstPersonWandRig {

    /** One key pose at tick {@code at} of the cast. */
    record Key(float at, Vector3f hand, Vector3f arm, Vector3f wand) {
        Key copy(float newAt) {
            return new Key(newAt, new Vector3f(this.hand), new Vector3f(this.arm), new Vector3f(this.wand));
        }
    }

    /** A gesture as the first-person arm plays it: its keys, the tick the spell leaves, and its length. */
    record Motion(Key[] keys, float release, float length) {
        /** The key the arm holds while a channelled spell is kept up: the one at (or nearest after) release. */
        Key holdKey() {
            for (Key key : this.keys) {
                if (key.at() >= this.release - 0.01F) {
                    return key;
                }
            }
            return this.keys[this.keys.length - 1];
        }
    }

    // ---------------------------------------------------------------- the rest pose and the keys

    static final Vector3f REST_HAND = new Vector3f(0.50F, -0.16F, -0.74F);
    static final Vector3f REST_ARM = new Vector3f(-0.10F, 1.0F, -0.30F);
    /**
     * At rest the wand points at this spot, a little way past the crosshair, so its tip sits beside the
     * crosshair and the wand runs from the hand in towards it: aimed where the player looks.
     */
    static final Vector3f REST_AIM = new Vector3f(0.06F, 0.26F, -1.8F);

    /** Mirrored strokes are reflected about this x (the middle of the screen): they wind up on the far side and still strike the middle. */
    private static final float MIRROR_X = 0.0F;
    /** The way the front of the arm (palm side) faces, kept as near as the forearm allows. */
    private static final Vector3f FRONT = new Vector3f(0.0F, -0.5F, -1.0F).normalize();

    private static final Map<FirstPersonCast.Gesture, Motion> MOTIONS = new HashMap<>();

    private static Key k(float at, float hx, float hy, float hz, float ax, float ay, float az,
                         float wx, float wy, float wz) {
        return new Key(at, new Vector3f(hx, hy, hz), new Vector3f(ax, ay, az), new Vector3f(wx, wy, wz));
    }

    /** The tick the gesture lets the spell go, the same as the orb hold and third person use. */
    private static float releaseOf(FirstPersonCast.Gesture g) {
        return g.release * g.ticks;
    }

    private static void motion(FirstPersonCast.Gesture g, float recovery, Key... keys) {
        MOTIONS.put(g, new Motion(keys, releaseOf(g), releaseOf(g) + recovery));
    }

    static {
        float r;
        // Flick: the wand cocked back up at the shoulder, then snapped forward to the middle of the screen.
        r = releaseOf(FirstPersonCast.FLICK);
        motion(FirstPersonCast.FLICK, 13.0F,
                k(r * 0.5F, 0.58F, -0.05F, -0.66F, 0.05F, 1.0F, -0.10F, 0.25F, 0.90F, 0.15F),
                k(r, -0.04F, -0.12F, -0.64F, -0.60F, 0.35F, -0.75F, -0.05F, 0.02F, -1.0F),
                k(r + 1.2F, -0.10F, -0.20F, -0.62F, -0.65F, 0.25F, -0.70F, -0.12F, -0.10F, -1.0F));
        // Stab: drawn back low, then a straight jab deep at the target, held a moment.
        r = releaseOf(FirstPersonCast.STAB);
        motion(FirstPersonCast.STAB, 12.0F,
                k(r * 0.48F, 0.52F, -0.38F, -0.50F, 0.0F, 1.0F, 0.15F, -0.10F, 0.50F, -0.80F),
                k(r, 0.08F, -0.14F, -0.86F, -0.35F, 0.30F, -1.0F, 0.0F, 0.03F, -1.0F),
                k(r + 1.8F, 0.07F, -0.15F, -0.84F, -0.35F, 0.30F, -1.0F, 0.0F, 0.0F, -1.0F));
        // Slash: high at the right, cut down through the middle to low left.
        r = releaseOf(FirstPersonCast.SLASH);
        motion(FirstPersonCast.SLASH, 13.0F,
                k(r * 0.48F, 0.60F, 0.05F, -0.70F, 0.15F, 1.0F, -0.05F, 0.35F, 0.85F, 0.20F),
                k(r, 0.0F, -0.14F, -0.62F, -0.55F, 0.40F, -0.70F, -0.45F, -0.25F, -0.85F),
                k(r + 1.2F, -0.32F, -0.32F, -0.60F, -0.80F, 0.10F, -0.50F, -0.75F, -0.55F, -0.40F));
        // Double slash: a Z, high right to low left, back across the middle and down to the right.
        r = releaseOf(FirstPersonCast.DOUBLE_SLASH);
        motion(FirstPersonCast.DOUBLE_SLASH, 12.0F,
                k(r * 0.35F, 0.60F, 0.05F, -0.70F, 0.15F, 1.0F, -0.05F, 0.35F, 0.85F, 0.20F),
                k(r * 0.65F, -0.30F, -0.30F, -0.60F, -0.80F, 0.10F, -0.50F, -0.70F, -0.50F, -0.50F),
                k(r, 0.05F, -0.12F, -0.64F, -0.50F, 0.40F, -0.75F, 0.05F, 0.10F, -1.0F),
                k(r + 1.3F, 0.35F, -0.32F, -0.62F, -0.20F, 0.60F, -0.70F, 0.50F, -0.50F, -0.70F));
        // Push: pulled in to the chest, then a heavy shove out, held.
        r = releaseOf(FirstPersonCast.PUSH);
        motion(FirstPersonCast.PUSH, 12.0F,
                k(r * 0.5F, 0.32F, -0.42F, -0.44F, -0.10F, 1.0F, 0.20F, -0.20F, 0.70F, -0.60F),
                k(r, 0.04F, -0.10F, -0.95F, -0.35F, 0.25F, -1.0F, 0.0F, 0.03F, -1.0F),
                k(r + 2.0F, 0.05F, -0.12F, -0.92F, -0.35F, 0.25F, -1.0F, 0.0F, 0.0F, -1.0F));
        // Raise: the wand lifted straight up and held there.
        r = releaseOf(FirstPersonCast.RAISE);
        motion(FirstPersonCast.RAISE, 12.0F,
                k(r * 0.5F, 0.44F, -0.02F, -0.72F, 0.0F, 1.0F, -0.15F, -0.10F, 1.0F, -0.20F),
                k(r, 0.38F, 0.07F, -0.74F, 0.0F, 1.0F, -0.20F, -0.12F, 1.0F, -0.30F),
                k(r + 3.0F, 0.38F, 0.06F, -0.74F, 0.0F, 1.0F, -0.20F, -0.12F, 1.0F, -0.32F));
        // Swish and flick: a loop of the tip, a swish down, and a sharp flick up.
        r = releaseOf(FirstPersonCast.SWISH_FLICK);
        motion(FirstPersonCast.SWISH_FLICK, 12.0F,
                k(r * 0.22F, 0.30F, -0.18F, -0.66F, -0.30F, 0.80F, -0.60F, -0.30F, 0.30F, -0.90F),
                k(r * 0.44F, 0.12F, -0.28F, -0.64F, -0.50F, 0.50F, -0.70F, -0.50F, -0.10F, -0.85F),
                k(r * 0.66F, 0.06F, -0.14F, -0.66F, -0.50F, 0.60F, -0.70F, -0.40F, 0.30F, -0.85F),
                k(r * 0.86F, 0.18F, -0.24F, -0.66F, -0.40F, 0.50F, -0.70F, -0.10F, -0.25F, -0.95F),
                k(r, 0.14F, -0.08F, -0.70F, -0.30F, 0.80F, -0.50F, -0.15F, 0.90F, -0.40F),
                k(r + 2.0F, 0.14F, -0.09F, -0.70F, -0.30F, 0.80F, -0.50F, -0.15F, 0.85F, -0.45F));
        // Spiral: two small circles of the hand with the wand on the target, then a point.
        r = releaseOf(FirstPersonCast.SPIRAL);
        Key[] spiral = new Key[9];
        for (int i = 0; i < 8; i++) {
            float a = i / 4.0F * Mth.TWO_PI;
            float cx = Mth.cos(a) * 0.08F;
            float cy = Mth.sin(a) * 0.07F;
            spiral[i] = k(r * (0.12F + 0.1F * i), 0.14F + cx, -0.16F + cy, -0.70F, -0.45F, 0.45F, -0.80F,
                    cx * 2.2F, 0.05F + cy * 2.2F, -1.0F);
        }
        spiral[8] = k(r, 0.08F, -0.12F, -0.82F, -0.40F, 0.30F, -1.0F, 0.0F, 0.03F, -1.0F);
        motion(FirstPersonCast.SPIRAL, 11.0F, spiral);
        // Pull: reach out at the target, then yank back to the chest with the wand up.
        r = releaseOf(FirstPersonCast.PULL);
        motion(FirstPersonCast.PULL, 12.0F,
                k(r * 0.55F, 0.06F, -0.12F, -0.90F, -0.35F, 0.30F, -1.0F, 0.0F, 0.03F, -1.0F),
                k(r, 0.06F, -0.12F, -0.88F, -0.35F, 0.30F, -1.0F, 0.0F, 0.05F, -1.0F),
                k(r + 2.4F, 0.46F, -0.28F, -0.60F, 0.0F, 1.0F, 0.10F, -0.30F, 0.90F, -0.25F));
        // Whip: back over the shoulder, then lashed forward and down.
        r = releaseOf(FirstPersonCast.WHIP);
        motion(FirstPersonCast.WHIP, 12.0F,
                k(r * 0.48F, 0.62F, 0.08F, -0.52F, 0.25F, 1.0F, 0.25F, 0.30F, 0.60F, 0.75F),
                k(r, 0.0F, -0.18F, -0.66F, -0.55F, 0.30F, -0.75F, -0.10F, -0.25F, -1.0F),
                k(r + 1.3F, -0.05F, -0.26F, -0.66F, -0.55F, 0.20F, -0.75F, -0.15F, -0.45F, -0.90F));
        // Curse: the wand raised overhead, then driven down at the target and held.
        r = releaseOf(FirstPersonCast.CURSE);
        motion(FirstPersonCast.CURSE, 12.0F,
                k(r * 0.48F, 0.30F, 0.18F, -0.62F, 0.0F, 1.0F, 0.05F, 0.0F, 1.0F, 0.25F),
                k(r, 0.02F, -0.14F, -0.72F, -0.50F, 0.30F, -0.85F, 0.0F, 0.0F, -1.0F),
                k(r + 2.5F, 0.02F, -0.15F, -0.72F, -0.50F, 0.30F, -0.85F, 0.0F, -0.02F, -1.0F));
        // Twist: point at the lock, turn the wrist one way and back like a key.
        r = releaseOf(FirstPersonCast.TWIST);
        motion(FirstPersonCast.TWIST, 11.0F,
                k(r * 0.45F, 0.10F, -0.14F, -0.80F, -0.35F, 0.30F, -1.0F, 0.0F, 0.03F, -1.0F),
                k(r, 0.12F, -0.14F, -0.80F, -0.35F, 0.30F, -1.0F, 0.20F, 0.0F, -1.0F),
                k(r + 1.4F, 0.10F, -0.14F, -0.80F, -0.35F, 0.30F, -1.0F, -0.14F, 0.02F, -1.0F));
    }

    static Motion motionOf(FirstPersonCast.Gesture gesture) {
        return MOTIONS.getOrDefault(gesture, MOTIONS.get(FirstPersonCast.FLICK));
    }

    private FirstPersonWandRig() {
    }

    // ---------------------------------------------------------------- the live rest pose

    /** This cast's rest offset (hand x, hand y, wand x, wand y), eased towards a new target after each cast. */
    private static final float[] REST_OFFSET = new float[4];
    private static final float[] REST_TARGET = new float[4];
    private static int lastCastStart = Integer.MIN_VALUE;
    private static float lastFrameAge = Float.NaN;
    private static float lastYaw;
    private static float lastPitch;
    private static float yawSpeed;
    private static float pitchSpeed;
    private static float swayYaw;
    private static float swayPitch;
    /** The spell selected last frame, and when the selection last changed (player age, ticks). */
    @javax.annotation.Nullable
    private static com.hogwartsmagic.spell.SpellType lastSpell;
    private static float switchAt = -100.0F;
    /** Length of the twirl that marks a change of spell, in ticks. */
    private static final float TWIRL_TICKS = 9.0F;

    /** 0..1: how bright the tip sparkles from a change of spell (1 the moment it changes). */
    static float switchGlow(float age) {
        float t = (age - switchAt) / TWIRL_TICKS;
        return t < 0.0F || t > 1.0F ? 0.0F : 1.0F - t;
    }

    /**
     * The whole pose turned a few degrees behind a fast turn of the view, about the eye: arm and wand trail
     * as one rigid piece and settle back, so the wand never swings about the hand. Right-hand coordinates.
     */
    static Key sway(Key pose) {
        if (swayYaw == 0.0F && swayPitch == 0.0F) {
            return pose;
        }
        Matrix3f turn = new Matrix3f().rotateY(swayYaw * Mth.DEG_TO_RAD).rotateX(swayPitch * Mth.DEG_TO_RAD);
        return new Key(pose.at(), turn.transform(new Vector3f(pose.hand())), turn.transform(new Vector3f(pose.arm())),
                turn.transform(new Vector3f(pose.wand())));
    }

    /**
     * The rest pose this frame: the base pose, the cast's own settle, breathing, and a lag behind fast turns.
     * Call once per frame.
     */
    static Key rest(LocalPlayer player, float partialTick, FirstPersonCast.Cast cast) {
        float age = player.tickCount + partialTick;
        float dt = Float.isNaN(lastFrameAge) ? 0.0F : Mth.clamp(age - lastFrameAge, 0.0F, 2.0F);
        lastFrameAge = age;

        com.hogwartsmagic.spell.SpellType spell = com.hogwartsmagic.item.MagicWandItem.getSelectedSpell(player.getMainHandItem());
        if (spell != lastSpell) {
            if (lastSpell != null && spell != null) {
                switchAt = age;
                player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                        com.hogwartsmagic.ModSounds.WAND_SWITCH.get(), net.minecraft.sounds.SoundSource.PLAYERS,
                        0.45F, 0.95F + player.getRandom().nextFloat() * 0.15F, false);
            }
            lastSpell = spell;
        }

        if (cast != null && cast.start() != lastCastStart) {
            lastCastStart = cast.start();
            java.util.Random random = new java.util.Random(cast.start() * 31L + player.getId());
            REST_TARGET[0] = (random.nextFloat() - 0.5F) * 0.07F;
            REST_TARGET[1] = (random.nextFloat() - 0.5F) * 0.06F;
            REST_TARGET[2] = (random.nextFloat() - 0.5F) * 0.26F; // the aim spot, at REST_AIM's depth
            REST_TARGET[3] = (random.nextFloat() - 0.5F) * 0.20F;
        }
        float ease = 1.0F - (float) Math.exp(-dt * 0.12F);
        for (int i = 0; i < 4; i++) {
            REST_OFFSET[i] += (REST_TARGET[i] - REST_OFFSET[i]) * ease;
        }

        float yaw = player.getViewYRot(partialTick);
        float pitch = player.getViewXRot(partialTick);
        if (dt > 0.0F) {
            float dy = Mth.wrapDegrees(yaw - lastYaw) / dt;
            float dp = (pitch - lastPitch) / dt;
            float k = 1.0F - (float) Math.exp(-dt * 0.6F);
            yawSpeed += (dy - yawSpeed) * k;
            pitchSpeed += (dp - pitchSpeed) * k;
        }
        lastYaw = yaw;
        lastPitch = pitch;
        // A fast turn leaves the arm a few degrees behind the view (see sway).
        swayYaw = Mth.clamp(yawSpeed * 0.3F, -6.0F, 6.0F);
        swayPitch = Mth.clamp(pitchSpeed * 0.3F, -5.0F, 5.0F);

        float breath = age * Mth.TWO_PI / 80.0F;
        Vector3f hand = new Vector3f(REST_HAND).add(
                REST_OFFSET[0] + 0.004F * Mth.sin(breath * 0.6F + 1.0F),
                REST_OFFSET[1] + 0.009F * Mth.sin(breath),
                0.0F);
        Vector3f aim = new Vector3f(REST_AIM).add(REST_OFFSET[2], REST_OFFSET[3] + 0.04F * Mth.sin(breath + 0.6F), 0.0F);
        Vector3f wand = aim.sub(hand).normalize();
        float twirl = (age - switchAt) / TWIRL_TICKS;
        if (twirl >= 0.0F && twirl < 1.0F) {
            // A change of spell: the wrist rolls the wand once around, the tip drawing a small circle, with
            // a little lift of the hand.
            float e = twirl * twirl * (3.0F - 2.0F * twirl);
            Vector3f tilted = new Vector3f(wand).rotateAxis(0.45F * Mth.sin(Mth.PI * twirl), 1.0F, 0.0F, 0.0F);
            wand = tilted.rotateAxis(e * Mth.TWO_PI, wand.x, wand.y, wand.z).normalize();
            hand.y += 0.03F * Mth.sin(Mth.PI * twirl);
        }
        Vector3f arm = new Vector3f(REST_ARM).normalize();
        return new Key(0.0F, hand, arm, wand);
    }

    // ---------------------------------------------------------------- the curve

    /** The key with a cast's variant: mirrored across the body, scaled about rest, shifted on screen. */
    private static Key vary(Key key, Key rest, FirstPersonCast.Variant v) {
        Vector3f hand = new Vector3f(key.hand());
        Vector3f arm = new Vector3f(key.arm()).normalize();
        Vector3f wand = new Vector3f(key.wand()).normalize();
        if (v.mirror()) {
            hand.x = 2.0F * MIRROR_X - hand.x;
            arm.x = -arm.x;
            wand.x = -wand.x;
        }
        hand.sub(rest.hand()).mul(v.scale()).add(rest.hand());
        hand.add(v.shiftX() * 0.35F, v.shiftY() * 0.35F, 0.0F);
        wand.y += v.pitchOffset() / 70.0F;
        return new Key(key.at(), hand, arm, wand);
    }

    /**
     * The pose {@code since} ticks into a cast: a spline from rest through the keys and back to rest, with the
     * speed carried smoothly through every key (no stop at each one) and easing out into rest.
     */
    static Key pose(Motion motion, FirstPersonCast.Variant variant, Key rest, float since) {
        int n = motion.keys().length;
        Key[] points = new Key[n + 2];
        points[0] = rest.copy(0.0F);
        for (int i = 0; i < n; i++) {
            points[i + 1] = vary(motion.keys()[i], rest, variant);
        }
        points[n + 1] = rest.copy(motion.length());
        if (since <= 0.0F) {
            return points[0];
        }
        if (since >= motion.length()) {
            return points[n + 1];
        }
        int seg = 0;
        while (seg < n && since > points[seg + 1].at()) {
            seg++;
        }
        Key a = points[seg];
        Key b = points[seg + 1];
        float span = Math.max(1.0E-3F, b.at() - a.at());
        float u = (since - a.at()) / span;
        return new Key(since,
                hermite(points, seg, u, span, Key::hand),
                hermite(points, seg, u, span, Key::arm),
                hermite(points, seg, u, span, Key::wand));
    }

    /** Catmull-Rom style tangents over uneven key times; zero speed at the rest ends. */
    private static Vector3f hermite(Key[] p, int seg, float u, float span, java.util.function.Function<Key, Vector3f> f) {
        Vector3f p0 = f.apply(p[seg]);
        Vector3f p1 = f.apply(p[seg + 1]);
        Vector3f m0 = tangent(p, seg, f).mul(span);
        Vector3f m1 = tangent(p, seg + 1, f).mul(span);
        float u2 = u * u;
        float u3 = u2 * u;
        float h00 = 2 * u3 - 3 * u2 + 1;
        float h10 = u3 - 2 * u2 + u;
        float h01 = -2 * u3 + 3 * u2;
        float h11 = u3 - u2;
        return new Vector3f(p0).mul(h00).add(m0.mul(h10)).add(new Vector3f(p1).mul(h01)).add(m1.mul(h11));
    }

    private static Vector3f tangent(Key[] p, int i, java.util.function.Function<Key, Vector3f> f) {
        if (i <= 0 || i >= p.length - 1) {
            return new Vector3f();
        }
        float dt = p[i + 1].at() - p[i - 1].at();
        if (dt < 1.0E-3F) {
            return new Vector3f();
        }
        return new Vector3f(f.apply(p[i + 1])).sub(f.apply(p[i - 1])).div(dt);
    }

    /** Mixes two poses: 0 gives a, 1 gives b. */
    static Key mix(Key a, Key b, float k) {
        return new Key(a.at(), new Vector3f(a.hand()).lerp(b.hand(), k), new Vector3f(a.arm()).lerp(b.arm(), k),
                new Vector3f(a.wand()).lerp(b.wand(), k));
    }

    // ---------------------------------------------------------------- placing the arm

    /**
     * A point on the arm as renderHand draws it, in the space the arm is drawn in: the box hangs from the pivot
     * (-5, 2, 0) px, turned 0.1 rad about z by the idle bob, from 2 px above the pivot to 10 px below it.
     * {@code down} is px down the arm from the pivot.
     */
    private static Vector3f armPoint(float side, float down) {
        Vector3f pivot = new Vector3f(-5.0F * side / 16.0F, 2.0F / 16.0F, 0.0F);
        return new Matrix3f().rotateZ(0.1F * side).transform(new Vector3f(-side / 16.0F, down / 16.0F, 0.0F)).add(pivot);
    }

    /**
     * Turns and moves the pose stack so the arm drawn next (PlayerRenderer.renderRightHand/LeftHand) has its
     * hand at {@code pose.hand}, its forearm along {@code pose.arm}. {@code side} is 1 for the right arm.
     * Returns the middle of the fist in the same space as the pose (the stack's space before this call), where
     * the wand is held.
     */
    static Vector3f placeArm(PoseStack stack, Key pose, float side) {
        Vector3f hand = new Vector3f(pose.hand()).mul(side, 1.0F, 1.0F);
        Vector3f arm = new Vector3f(pose.arm()).mul(side, 1.0F, 1.0F).normalize();
        Matrix3f bob = new Matrix3f().rotateZ(0.1F * side);
        Vector3f shoulder = armPoint(side, -2.0F);
        Vector3f handEnd = armPoint(side, 10.0F);
        Vector3f axisP = new Vector3f(handEnd).sub(shoulder).normalize();
        Vector3f frontP = orthoTo(bob.transform(new Vector3f(0.0F, 0.0F, -1.0F)), axisP);

        Vector3f front = orthoTo(new Vector3f(FRONT).mul(side, 1.0F, 1.0F), arm);
        if (front.lengthSquared() < 1.0E-4F) {
            front = orthoTo(new Vector3f(0.0F, -1.0F, 0.0F), arm);
        }
        Matrix3f from = basis(axisP, frontP);
        Matrix3f to = basis(arm, front);
        Matrix3f rotation = to.mul(from.transpose());
        Vector3f offset = new Vector3f(hand).sub(rotation.transform(new Vector3f(handEnd)));
        stack.translate(offset.x, offset.y, offset.z);
        stack.mulPose(new Quaternionf().setFromNormalized(rotation));
        return rotation.transform(armPoint(side, 8.6F)).add(offset);
    }

    /** The wand's own axis in its centred model space: the sprite diagonal, handle low left, tip high right. */
    private static final Vector3f WAND_AXIS = new Vector3f(1.0F, 1.0F, 0.0F).normalize();
    /** The wand model's size in the hand, as its third-person display scale. */
    private static final float WAND_SCALE = 0.85F;

    /**
     * Moves the stack so the wand model drawn next (with no display transform) lies with its grip on
     * {@code fist} and its tip along {@code direction}, both in the pose's space (as placeArm takes them):
     * held through the fist, not hung on the item transform of the hand. Anything the stack already carries
     * (vanilla's hand sway and bobbing) moves arm and wand alike.
     */
    static void placeWand(PoseStack stack, Vector3f fist, Vector3f direction, Vector3f grip) {
        stack.translate(fist.x, fist.y, fist.z);
        stack.mulPose(new Quaternionf().rotationTo(WAND_AXIS, new Vector3f(direction).normalize()));
        stack.scale(WAND_SCALE, WAND_SCALE, WAND_SCALE);
        stack.translate(-grip.x, -grip.y, -grip.z);
    }

    private static Vector3f orthoTo(Vector3f v, Vector3f axis) {
        Vector3f out = new Vector3f(v).sub(new Vector3f(axis).mul(v.dot(axis)));
        float len = out.length();
        return len < 1.0E-4F ? out : out.div(len);
    }

    /** An orthonormal basis with columns {axis, front, axis x front}. */
    private static Matrix3f basis(Vector3f axis, Vector3f front) {
        Vector3f third = new Vector3f(axis).cross(front).normalize();
        return new Matrix3f(axis, front, third);
    }
}
