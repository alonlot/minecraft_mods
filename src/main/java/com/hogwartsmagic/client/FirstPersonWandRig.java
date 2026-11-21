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
    record Motion(Key[] keys, float release, float length, Builder builder, boolean anchorFlash, float trailUntil,
                  float trailLife, Roll roll, boolean holdLast, float tremor) {
        Motion(Key[] keys, float release, float length) {
            this(keys, release, length, null, false, release + 2.0F, 1.8F, null, false, 0.0F);
        }

        /** The same gesture, its flash left where the spell left the wand (the arm moves on across the view). */
        Motion anchored() {
            return new Motion(keys, release, length, builder, true, trailUntil, trailLife, roll, holdLast, tremor);
        }

        /** The same gesture, its last key held while the spell is kept up (not the one at the release). */
        Motion holdingLast() {
            return new Motion(keys, release, length, builder, anchorFlash, trailUntil, trailLife, roll, true, tremor);
        }

        /** The same gesture, the hand shaking from the release on and while the spell is kept up. */
        Motion trembling(float amount) {
            return new Motion(keys, release, length, builder, anchorFlash, trailUntil, trailLife, roll, holdLast,
                    amount);
        }

        /** How far the fist is rolled about the forearm at gesture time {@code time} (radians), 0 for most. */
        float rollAt(float time, FirstPersonCast.Variant variant) {
            return this.roll == null ? 0.0F : this.roll.at(time, variant);
        }

        /**
         * Whether the keys are built for each cast from where the arm is (the big-arm gestures, as in the
         * reference): not mirrored, tilted or clamped, and the wind-up never played faster.
         */
        boolean point() {
            return this.builder != null;
        }

        /** The key the arm holds while a channelled spell is kept up: the one at (or nearest after) release. */
        Key holdKey() {
            if (this.holdLast) {
                return this.keys[this.keys.length - 1];
            }
            for (Key key : this.keys) {
                if (key.at() >= this.release - 0.01F) {
                    return key;
                }
            }
            return this.keys[this.keys.length - 1];
        }
    }

    /** Builds a gesture's keys for one cast: from where the arm was, with the cast's variant. */
    interface Builder {
        Key[] build(Key from, FirstPersonCast.Variant variant);
    }

    /** A gesture's roll of the fist about the forearm through the cast (radians; positive turns it clockwise). */
    interface Roll {
        float at(float time, FirstPersonCast.Variant variant);
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
        // The arm settles back fast after the strike, as in the reference.
        MOTIONS.put(g, new Motion(keys, releaseOf(g), releaseOf(g) + recovery * 0.7F));
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

    /**
     * Which way the wand tip cuts as a cast lets the spell go, on screen (x right, y up; right-hand
     * coordinates): from where it is shortly before the release to where it is at the release, with the cast's
     * mirroring and variation. The spell leaves along this cut before curving onto its target. Null when the
     * tip hardly moves there (a straight jab or a lift).
     */
    static Vector3f strikeDirection(FirstPersonCast.Cast cast) {
        Motion motion = motionOf(cast);
        Key rest = new Key(0.0F, new Vector3f(REST_HAND), new Vector3f(REST_ARM).normalize(),
                new Vector3f(REST_AIM).sub(REST_HAND).normalize());
        float release = motion.release();
        Key from = fromOf(cast, rest);
        Key before = pose(motion, cast.variant(), rest, Math.max(0.0F, release - 0.8F), from);
        Key at = pose(motion, cast.variant(), rest, release, from);
        // The tip, as seen from the eye: the hand plus a stretch of the wand, divided by depth.
        Vector3f a = new Vector3f(before.wand()).mul(0.35F).add(before.hand());
        Vector3f b = new Vector3f(at.wand()).mul(0.35F).add(at.hand());
        float ax = a.x / -a.z;
        float ay = a.y / -a.z;
        float bx = b.x / -b.z;
        float by = b.y / -b.z;
        Vector3f cut = new Vector3f(bx - ax, by - ay, 0.0F);
        return cut.lengthSquared() < 1.0E-4F ? null : cut.normalize();
    }

    /** Where the arm was when the cast that started at {@code castFrom}'s tick began (set by FirstPersonWand). */
    private static Key castFrom;
    private static int castFromStart = Integer.MIN_VALUE;

    static void castFrom(int castStart, Key from) {
        castFromStart = castStart;
        castFrom = from.copy(0.0F);
    }

    /** Where {@code cast} set off from, or {@code fallback} when that was not seen. */
    static Key fromOf(FirstPersonCast.Cast cast, Key fallback) {
        return castFrom != null && castFromStart == cast.start() ? castFrom : fallback;
    }

    /** The motion your arm plays for {@code cast}: the spell's own signature move, or its gesture's. */
    static Motion motionOf(FirstPersonCast.Cast cast) {
        return motionOf(cast.spell(), cast.gesture());
    }

    static Motion motionOf(@javax.annotation.Nullable com.hogwartsmagic.spell.SpellType spell,
                           FirstPersonCast.Gesture gesture) {
        Motion own = spell == null ? null : SIGNATURE.get(spell);
        return own != null ? own : motionOf(gesture);
    }

    static Motion motionOf(FirstPersonCast.Gesture gesture) {
        if (POINTING.contains(gesture)) {
            return POINT;
        }
        Motion built = BUILT.get(gesture);
        if (built != null) {
            return built;
        }
        return MOTIONS.getOrDefault(gesture, MOTIONS.get(FirstPersonCast.FLICK));
    }

    /** The rest pose with no live motion (breath, settle, sway). */
    static Key baseRest() {
        return new Key(0.0F, new Vector3f(REST_HAND), new Vector3f(REST_ARM).normalize(),
                new Vector3f(REST_AIM).sub(REST_HAND).normalize());
    }

    /** The Killing Curse gathering: the wand held high over the shoulder (the old curse wind-up). */
    static Key raisedKey() {
        return MOTIONS.get(FirstPersonCast.CURSE).keys()[0];
    }

    // ---------------------------------------------------------------- the sweep (the reference)

    /**
     * The bolt spells, cast as in the reference video: the whole arm sweeps across the view in a fast U, from
     * the side it is on, low and upright through the middle (where the spell leaves the wand) and up to the
     * other side, where it stops with the sleeve across that side of the screen. It hangs there a moment,
     * sinking slowly outwards, then goes back to rest: from the right it rises into place, from the left it
     * drops under the bottom of the view and comes up on the right, so it never drifts back across the target.
     * A cast can also cut down or up on the side the arm is already on.
     */
    private static final java.util.Set<FirstPersonCast.Gesture> POINTING = java.util.Set.of(FirstPersonCast.FLICK,
            FirstPersonCast.STAB, FirstPersonCast.SLASH, FirstPersonCast.DOUBLE_SLASH, FirstPersonCast.WHIP,
            FirstPersonCast.CURSE, FirstPersonCast.PUSH);
    /** Ticks: the arm passes the middle (the spell leaves), reaches the far side, has sunk, is back at rest. */
    private static final float SWEEP_MID = 1.2F;
    private static final float SWEEP_END = 2.2F;
    private static final float SWEEP_DRIFT = 10.5F;
    static final float SWEEP_LENGTH = 17.0F;

    /** The bottom of the U: the fist low in the middle, the forearm upright, the wand on the target. */
    private static Key sweepMiddle() {
        Vector3f arm = new Vector3f(0.0F, 1.0F, -0.15F).normalize();
        // The wand straight out of the fist, tipped forward at the target: a short stub, as in the reference.
        return new Key(SWEEP_MID, new Vector3f(0.02F, -0.19F, -0.74F), arm,
                new Vector3f(arm).mul(0.35F).add(0.02F, 0.0F, -1.0F).normalize());
    }

    /** The middle of the sweep for the channelled bolt spells' hold pose. */
    private static final Motion POINT = new Motion(new Key[]{sweepMiddle()}, SWEEP_MID, SWEEP_LENGTH,
            (from, variant) -> sweepKeys(from, variant.place()), true, SWEEP_MID + 2.0F, 1.8F, null, false, 0.0F);

    /**
     * Where a sweep ends, by {@code place}: its sign the side of the view (-1 left, 1 right), its size how low
     * (0.5 the arm raised high on that side, 1 lying almost flat along the bottom). Right-hand view coordinates.
     */
    static Key sweepEnd(float place) {
        float side = place < 0.0F ? -1.0F : 1.0F;
        float low = Mth.clamp((Math.abs(place) - 0.5F) / 0.5F, 0.0F, 1.0F);
        Vector3f hand = new Vector3f(0.32F, 0.09F, -0.90F).lerp(new Vector3f(0.36F, -0.29F, -0.80F), low);
        Vector3f arm = new Vector3f(0.47F, 0.88F, -0.35F).lerp(new Vector3f(1.0F, 0.2F, -0.3F), low);
        hand.x *= side;
        arm.x *= side;
        arm.normalize();
        return new Key(SWEEP_END, hand, arm, sideWand(arm));
    }

    /** At the sides the wand runs on straight out of the fist, in line with the forearm (a little forward). */
    private static Vector3f sideWand(Vector3f arm) {
        return new Vector3f(arm).add(0.0F, 0.0F, -0.3F).normalize();
    }

    /** Which side of the view a pose is on: -1 left, 1 right, 0 the middle. */
    private static int sideOf(Key pose) {
        return pose.hand().x > 0.12F ? 1 : pose.hand().x < -0.12F ? -1 : 0;
    }

    /** The keys of a sweep from {@code from} (where the arm was) to the side {@code place} names. */
    static Key[] sweepKeys(Key from, float place) {
        Key end = sweepEnd(place);
        float side = Math.signum(end.hand().x);
        Key middle;
        if (sideOf(from) == (int) side) {
            // A cut on the side the arm is on: straight from one height to the other, dipping a little.
            middle = mix(from, end, 0.5F).copy(SWEEP_MID);
            middle.hand().y -= 0.04F;
        } else {
            middle = sweepMiddle();
        }
        // A moment's overshoot as it stops, then the slow sink outwards and down.
        Key hold = new Key(SWEEP_END + 1.0F, new Vector3f(end.hand()).add(side * 0.025F, -0.012F, 0.0F),
                new Vector3f(end.arm()), new Vector3f(end.wand()));
        Vector3f sunkArm = new Vector3f(end.arm()).add(side * 0.25F, -0.12F, 0.0F).normalize();
        Key sunk = new Key(SWEEP_DRIFT, new Vector3f(end.hand()).add(side * 0.11F, -0.11F, 0.04F), sunkArm,
                sideWand(sunkArm));
        if (side > 0.0F) {
            return new Key[]{middle, end, hold, sunk};
        }
        // From the left the way home is under the bottom of the view (the wand pointing down, out of sight).
        Key under = new Key(SWEEP_DRIFT + 3.5F, new Vector3f(0.12F, -0.82F, -0.62F),
                new Vector3f(0.25F, 1.0F, -0.2F).normalize(), new Vector3f(0.0F, -0.3F, -0.95F).normalize());
        return new Key[]{middle, end, hold, sunk, under};
    }

    // ---------------------------------------------------------------- the other big-arm gestures

    private static Key key(float at, Vector3f hand, Vector3f arm, Vector3f wand) {
        return new Key(at, hand, new Vector3f(arm).normalize(), new Vector3f(wand).normalize());
    }

    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    /** The wand straight out of the fist along the forearm, a little forward. */
    private static Vector3f inLine(Vector3f arm) {
        return new Vector3f(arm).normalize().add(0.0F, 0.0F, -0.3F);
    }

    /** A built gesture with its template keys (built from rest) for the channel hold. */
    private static Motion built(Builder builder, float release, float length, float trailUntil, float trailLife) {
        return built(builder, release, length, trailUntil, trailLife, null);
    }

    private static Motion built(Builder builder, float release, float length, float trailUntil, float trailLife,
                                Roll roll) {
        return new Motion(builder.build(baseRest(), FirstPersonCast.Variant.NONE), release, length, builder, false,
                trailUntil, trailLife, roll, false, 0.0F);
    }

    /**
     * The lift (Lumos, the Patronus, the shields, Ascendio...): the arm scoops down low on one side and thrusts
     * straight up, the wand pointing at the sky, held high with a little bounce, then sinks back.
     */
    static Key[] riseKeys(Key from, FirstPersonCast.Variant variant) {
        float side = variant.tilt() < 0.0F ? -1.0F : 1.0F;
        Vector3f lowArm = v(-side * 0.25F, 1.0F, -0.05F);
        Vector3f topArm = v(-0.05F, 1.0F, -0.25F);
        return new Key[]{
                key(0.7F, v(0.10F + side * 0.12F, -0.50F, -0.62F), lowArm, inLine(lowArm)),
                key(RISE_RELEASE, v(0.14F, 0.10F, -0.80F), topArm, v(-0.02F, 0.75F, -0.66F)),
                key(2.4F, v(0.14F, 0.135F, -0.80F), topArm, v(-0.02F, 0.80F, -0.60F)),
                key(5.5F, v(0.15F, 0.10F, -0.79F), topArm, v(-0.02F, 0.74F, -0.67F)),
        };
    }

    private static final float RISE_RELEASE = 1.4F;

    /**
     * Swish and flick: a big swish down through the middle and up the far side, then the wand flicked sharply
     * back up at the target. Either way across (the cast's mirror).
     */
    static Key[] swishKeys(Key from, FirstPersonCast.Variant variant) {
        Vector3f dipArm = v(0.0F, 1.0F, -0.1F);
        Vector3f farArm = v(-0.5F, 0.85F, -0.3F);
        Vector3f flickArm = v(0.03F, 1.0F, -0.3F);
        Key[] keys = {
                key(1.4F, v(0.05F, -0.30F, -0.70F), dipArm, inLine(dipArm)),
                key(2.8F, v(-0.30F, 0.02F, -0.88F), farArm, inLine(farArm)),
                key(SWISH_RELEASE, v(0.06F, -0.06F, -0.78F), flickArm, v(0.02F, 0.85F, -0.5F)),
                key(4.6F, v(0.07F, -0.08F, -0.78F), flickArm, v(0.02F, 0.72F, -0.70F)),
                key(6.5F, v(0.07F, -0.08F, -0.78F), flickArm, v(0.02F, 0.72F, -0.70F)),
        };
        if (variant.mirror()) {
            for (Key key : keys) {
                key.hand().x = -key.hand().x;
                key.arm().x = -key.arm().x;
                key.wand().x = -key.wand().x;
            }
        }
        return keys;
    }

    private static final float SWISH_RELEASE = 3.8F;

    /**
     * The wrist circle (Confundo, Obliviate, Imperio...): the arm stays where it is and only the hand turns, the
     * wand tip drawing two small quick circles round the line to the target (either way round), leaving a ring
     * of light; then a little point at the target.
     */
    static Key[] circleKeys(Key from, FirstPersonCast.Variant variant) {
        float turn = variant.tilt() < 0.0F ? -1.0F : 1.0F;
        Vector3f hand = v(0.40F, -0.11F, -0.78F);
        Vector3f arm = v(-0.15F, 1.0F, -0.3F);
        Vector3f aim = v(-0.05F, 0.05F, -1.5F).sub(hand).normalize();
        Vector3f across = new Vector3f(aim).cross(0.0F, 1.0F, 0.0F).normalize();
        Vector3f up = new Vector3f(across).cross(aim).normalize();
        int steps = CIRCLE_STEPS * CIRCLES;
        Key[] keys = new Key[steps + 2];
        for (int i = 0; i <= steps; i++) {
            float phi = Mth.HALF_PI + turn * Mth.TWO_PI * i / CIRCLE_STEPS;
            float c = Mth.cos(phi);
            float sn = Mth.sin(phi);
            Vector3f wand = new Vector3f(aim).add(new Vector3f(across).mul(c * CIRCLE_SIZE))
                    .add(new Vector3f(up).mul(sn * CIRCLE_SIZE));
            // The palm turns; the hand itself hardly moves.
            Vector3f palm = new Vector3f(hand).add(c * 0.006F, sn * 0.006F, 0.0F);
            keys[i] = key(CIRCLE_RELEASE + i * CIRCLE_LOOP / CIRCLE_STEPS, palm, arm, wand);
        }
        keys[steps + 1] = key(CIRCLE_RELEASE + CIRCLES * CIRCLE_LOOP + 1.0F, v(0.39F, -0.11F, -0.81F), arm, aim);
        return keys;
    }

    private static final float CIRCLE_RELEASE = 1.2F;
    private static final float CIRCLE_LOOP = 4.0F;
    private static final int CIRCLES = 2;
    private static final int CIRCLE_STEPS = 8;
    /** How wide the tip's circle is, as a share of the wand's length. */
    private static final float CIRCLE_SIZE = 0.26F;

    /**
     * Accio: the arm reaches out at the target, holds a beat, and yanks back hard to the shoulder, the wand
     * whipping upright, as if pulling the thing in on a rope.
     */
    static Key[] yankKeys(Key from, FirstPersonCast.Variant variant) {
        Vector3f reachHand = v(0.06F, -0.10F, -0.92F);
        Vector3f reachArm = new Vector3f(reachHand).sub(0.30F, -0.50F, -0.25F);
        Vector3f reachWand = new Vector3f(reachArm).normalize().add(0.0F, 0.12F, 0.0F);
        Vector3f backArm = v(0.1F, 1.0F, 0.25F);
        return new Key[]{
                key(YANK_RELEASE, reachHand, reachArm, reachWand),
                key(2.4F, v(0.06F, -0.10F, -0.95F), reachArm, reachWand),
                key(3.5F, v(0.42F, -0.30F, -0.52F), backArm, v(-0.12F, 1.0F, -0.2F)),
                key(4.2F, v(0.44F, -0.26F, -0.56F), backArm, v(-0.15F, 1.0F, -0.3F)),
        };
    }

    private static final float YANK_RELEASE = 1.2F;

    /**
     * The key turn (Alohomora, Colloportus): the arm reaches out and pushes the wand in at the lock like a key,
     * in line with the forearm; then the whole fist rolls a quarter turn about the forearm with a snap and a
     * little bounce (clockwise to open, the other way to lock), holds, and draws back. The roll is
     * {@link #keyRoll}; the wand, a few degrees off the forearm, turns with the fist.
     */
    static Key[] keyTurnKeys(Key from, FirstPersonCast.Variant variant) {
        Vector3f hand = v(0.14F, -0.12F, -0.86F);
        Vector3f arm = new Vector3f(hand).sub(0.34F, -0.5F, -0.2F).normalize();
        Vector3f in = v(0.14F, -0.12F, -0.90F);
        float[] at = {KEY_RELEASE, 2.2F, KEY_TURN_END, KEY_TURN_END + 0.4F, KEY_TURN_END + 0.8F, 7.5F, 9.0F};
        Vector3f[] hands = {hand, in, in, in, in, in, v(0.17F, -0.13F, -0.80F)};
        Key[] keys = new Key[at.length];
        for (int i = 0; i < at.length; i++) {
            Vector3f wand = new Vector3f(arm).add(0.0F, 0.0F, -0.25F).normalize();
            wand.rotateAxis(keyRoll(at[i], variant), arm.x, arm.y, arm.z);
            keys[i] = key(at[i], hands[i], arm, wand);
        }
        return keys;
    }

    private static final float KEY_TURN_END = 4.4F;

    /** The key turn's roll of the fist: turned back as it goes in, a quarter round with a bounce, held, let go. */
    static float keyRoll(float time, FirstPersonCast.Variant variant) {
        float turn = variant.mirror() ? -1.0F : 1.0F;
        float back = -60.0F * Mth.DEG_TO_RAD;
        float open = 45.0F * Mth.DEG_TO_RAD;
        float roll;
        if (time < KEY_RELEASE) {
            roll = back * smooth(time / KEY_RELEASE);
        } else if (time < 2.2F) {
            roll = back;
        } else if (time < KEY_TURN_END) {
            roll = Mth.lerp(smooth((time - 2.2F) / (KEY_TURN_END - 2.2F)), back, open);
        } else if (time < KEY_TURN_END + 0.8F) {
            // The click: a little past, and back.
            roll = open + 7.0F * Mth.DEG_TO_RAD * Mth.sin(Mth.PI * (time - KEY_TURN_END) / 0.8F);
        } else if (time < 7.5F) {
            roll = open;
        } else {
            roll = open * (1.0F - smooth(Math.min(1.0F, (time - 7.5F) / 4.0F)));
        }
        return turn * roll;
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static final float KEY_RELEASE = 1.2F;

    /** The gestures built from where the arm is (besides the sweep). */
    private static final Map<FirstPersonCast.Gesture, Motion> BUILT = new HashMap<>();

    static {
        BUILT.put(FirstPersonCast.RAISE, built(FirstPersonWandRig::riseKeys, RISE_RELEASE, 12.0F, 2.2F, 2.5F));
        BUILT.put(FirstPersonCast.SWISH_FLICK, built(FirstPersonWandRig::swishKeys, SWISH_RELEASE, 14.0F, 4.8F, 4.0F));
        BUILT.put(FirstPersonCast.SPIRAL, built(FirstPersonWandRig::circleKeys, CIRCLE_RELEASE,
                CIRCLE_RELEASE + CIRCLES * CIRCLE_LOOP + 5.5F, CIRCLE_RELEASE + CIRCLES * CIRCLE_LOOP + 0.2F,
                CIRCLE_LOOP));
        BUILT.put(FirstPersonCast.PULL, built(FirstPersonWandRig::yankKeys, YANK_RELEASE, 11.0F, 4.2F, 2.0F));
        BUILT.put(FirstPersonCast.TWIST, built(FirstPersonWandRig::keyTurnKeys, KEY_RELEASE, 15.0F, KEY_TURN_END + 0.8F, 2.0F,
                FirstPersonWandRig::keyRoll));
    }

    // ---------------------------------------------------------------- the signature moves

    /** The arm along from an elbow below and to the right of the view to {@code hand}. */
    private static Vector3f from(Vector3f hand, float ex, float ey, float ez) {
        return new Vector3f(hand).sub(ex, ey, ez).normalize();
    }

    /**
     * After a cut ends on a side: a moment's overshoot, the slow sink outwards and down, and (from the left) the
     * way home under the bottom of the view; as the sweep's own, from tick {@code t0}.
     */
    private static Key[] sideTail(Key end, float side, float t0) {
        Key hold = new Key(t0 + 1.0F, new Vector3f(end.hand()).add(side * 0.025F, -0.012F, 0.0F),
                new Vector3f(end.arm()), new Vector3f(end.wand()));
        Vector3f sunkArm = new Vector3f(end.arm()).add(side * 0.25F, -0.12F, 0.0F).normalize();
        Key sunk = new Key(t0 + 8.3F, new Vector3f(end.hand()).add(side * 0.11F, -0.11F, 0.04F), sunkArm,
                sideWand(sunkArm));
        if (side > 0.0F) {
            return new Key[]{hold, sunk};
        }
        Key under = new Key(t0 + 11.8F, new Vector3f(0.12F, -0.82F, -0.62F),
                new Vector3f(0.25F, 1.0F, -0.2F).normalize(), new Vector3f(0.0F, -0.3F, -0.95F).normalize());
        return new Key[]{hold, sunk, under};
    }

    /** How long a gesture ending in {@link #sideTail} from {@code t0} lasts. */
    private static float tailLength(float t0) {
        return t0 + 14.8F;
    }

    private static Key[] join(Key[] a, Key... b) {
        Key[] out = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** Expelliarmus: the sweep, and at its end the wand flicked up and back, tearing the wand from their hand. */
    static Key[] expelliarmusKeys(Key from, FirstPersonCast.Variant variant) {
        Key[] keys = sweepKeys(from, variant.place());
        Key end = keys[1];
        float side = Math.signum(end.hand().x);
        keys[2] = key(SWEEP_END + 0.8F, new Vector3f(end.hand()).add(-side * 0.03F, 0.07F, 0.03F),
                new Vector3f(end.arm()).add(-side * 0.15F, 0.2F, 0.0F),
                new Vector3f(end.wand()).add(-side * 0.3F, 1.2F, 0.2F));
        return keys;
    }

    /**
     * Petrificus Totalus: the wand raised upright, then chopped straight down at the target and frozen there,
     * dead still, for a moment, before it lets go.
     */
    static Key[] petrificusKeys(Key from, FirstPersonCast.Variant variant) {
        Vector3f upArm = v(0.15F, 1.0F, -0.05F);
        Vector3f chop = v(0.12F, -0.14F, -0.82F);
        Vector3f chopArm = from(chop, 0.40F, -0.55F, -0.25F);
        Vector3f aim = v(-0.06F, 0.40F, -1.0F);
        Vector3f frozen = v(0.12F, -0.145F, -0.82F);
        return new Key[]{
                key(0.9F, v(0.24F, 0.12F, -0.78F), upArm, v(0.05F, 1.0F, -0.25F)),
                key(PETRIFICUS_RELEASE, chop, chopArm, aim),
                key(1.8F, frozen, chopArm, aim),
                key(5.8F, frozen, chopArm, aim),
                key(7.8F, v(0.16F, -0.19F, -0.80F), chopArm, v(-0.04F, 0.45F, -1.0F)),
        };
    }

    private static final float PETRIFICUS_RELEASE = 1.6F;

    /** Diffindo: one flat, very fast cut straight across at the target's height, the wand lying along it. */
    static Key[] diffindoKeys(Key from, FirstPersonCast.Variant variant) {
        float s = variant.place() < 0.0F ? -1.0F : 1.0F;
        Vector3f endArm = v(s * 0.8F, 0.55F, -0.25F);
        Key end = key(1.6F, v(s * 0.36F, -0.16F, -0.84F), endArm, v(s * 0.9F, 0.05F, -0.45F));
        Key middle;
        if (sideOf(from) == (int) s) {
            middle = mix(from, end, 0.5F).copy(0.9F);
        } else {
            middle = key(0.9F, v(0.02F, -0.08F, -0.80F), v(s * 0.35F, 0.9F, -0.35F), v(s * 0.9F, 0.15F, -0.45F));
        }
        return join(new Key[]{middle, end}, sideTail(end, s, 1.6F));
    }

    /** Sectumsempra: two cuts in a Z, high across one way, then back low the other way. */
    static Key[] sectumsempraKeys(Key from, FirstPersonCast.Variant variant) {
        float s = variant.place() < 0.0F ? -1.0F : 1.0F;
        Key endA = sweepEnd(s * 0.55F).copy(1.7F);
        Key middleA = sideOf(from) == (int) s ? mix(from, endA, 0.5F).copy(1.0F) : sweepMiddle().copy(1.0F);
        Vector3f lowArm = v(0.0F, 1.0F, -0.1F);
        Key middleB = key(2.5F, v(0.02F, -0.26F, -0.72F), lowArm, inLine(lowArm));
        Key endB = sweepEnd(-s * 0.95F).copy(3.2F);
        return join(new Key[]{middleA, endA, middleB, endB}, sideTail(endB, -s, 3.2F));
    }

    /** Bombarda, Depulso: the arm drawn back to the shoulder, then a heavy punch out at the target. */
    static Key[] punchKeys(Key from, FirstPersonCast.Variant variant) {
        Vector3f backArm = v(0.1F, 1.0F, 0.15F);
        Vector3f punch = v(0.08F, -0.10F, -0.95F);
        Vector3f punchArm = from(punch, 0.28F, -0.5F, -0.3F);
        Vector3f wand = inLine(punchArm).add(0.0F, 0.1F, 0.0F);
        return new Key[]{
                key(0.9F, v(0.40F, -0.24F, -0.56F), backArm, v(-0.05F, 1.0F, -0.25F)),
                key(PUNCH_RELEASE, punch, punchArm, wand),
                key(2.2F, v(0.08F, -0.10F, -0.99F), punchArm, wand),
                key(3.2F, v(0.12F, -0.13F, -0.86F), punchArm, wand),
                key(5.4F, v(0.13F, -0.14F, -0.85F), punchArm, wand),
        };
    }

    private static final float PUNCH_RELEASE = 1.8F;

    /**
     * Avada Kedavra: the wand high over the shoulder (where the charge holds it), then one hard chop down
     * across the body through the target, following through low to the left.
     */
    static Key[] avadaKeys(Key from, FirstPersonCast.Variant variant) {
        Key through = sweepEnd(-0.9F).copy(2.2F);
        return join(new Key[]{
                key(0.6F, v(0.36F, 0.16F, -0.64F), v(0.2F, 1.0F, 0.1F), v(-0.1F, 1.0F, -0.2F)),
                key(1.4F, v(0.02F, -0.17F, -0.74F), v(-0.25F, 1.0F, -0.15F), v(-0.04F, 0.40F, -1.0F)),
                through}, sideTail(through, -1.0F, 2.2F));
    }

    /** Crucio: a thrust at the target, then held there with the hand shaking (the motion's tremor). */
    static Key[] crucioKeys(Key from, FirstPersonCast.Variant variant) {
        Vector3f hand = v(0.06F, -0.10F, -0.92F);
        Vector3f arm = from(hand, 0.30F, -0.5F, -0.25F);
        Vector3f wand = new Vector3f(arm).add(0.0F, 0.12F, 0.0F);
        return new Key[]{
                key(1.2F, hand, arm, wand),
                key(1.6F, v(0.06F, -0.10F, -0.94F), arm, wand),
                key(8.0F, v(0.06F, -0.10F, -0.94F), arm, wand),
                key(9.5F, v(0.10F, -0.12F, -0.88F), arm, wand),
        };
    }

    /**
     * Expecto Patronum: the wand thrust out, then spiralled up in two widening-to-the-sky turns, ending high
     * with the wand to the sky, where it stays while the Patronus is held.
     */
    static Key[] patronusKeys(Key from, FirstPersonCast.Variant variant) {
        float turn = variant.tilt() < 0.0F ? -1.0F : 1.0F;
        Vector3f startHand = v(0.10F, -0.10F, -0.88F);
        Vector3f startArm = v(-0.15F, 0.45F, -0.65F);
        Vector3f startWand = v(-0.02F, 0.4F, -1.0F);
        Vector3f topHand = v(0.14F, 0.10F, -0.80F);
        Vector3f topArm = v(-0.05F, 1.0F, -0.25F);
        Vector3f topWand = v(-0.02F, 0.80F, -0.60F);
        int steps = 16;
        Key[] keys = new Key[steps + 2];
        keys[0] = key(1.3F, startHand, startArm, startWand);
        for (int i = 1; i <= steps; i++) {
            float u = i / (float) steps;
            float a = turn * Mth.TWO_PI * 2.0F * u;
            float c = Mth.cos(a);
            float sn = Mth.sin(a);
            Vector3f hand = new Vector3f(startHand).lerp(topHand, u).add(c * 0.06F * (1.0F - 0.5F * u),
                    sn * 0.05F * (1.0F - 0.5F * u), 0.0F);
            Vector3f arm = new Vector3f(startArm).lerp(topArm, u);
            Vector3f wand = new Vector3f(startWand).lerp(topWand, u).add(c * 0.25F * (1.0F - u),
                    sn * 0.25F * (1.0F - u), 0.0F);
            keys[i] = key(1.3F + 5.0F * u, hand, arm, wand);
        }
        keys[steps + 1] = key(7.0F, topHand, topArm, topWand);
        return keys;
    }

    /**
     * Incarcerous: the rope thrown out at the target, wound twice round with the wrist, and pulled tight with a
     * hard yank back to the shoulder.
     */
    static Key[] incarcerousKeys(Key from, FirstPersonCast.Variant variant) {
        Vector3f hand = v(0.08F, -0.08F, -0.90F);
        Vector3f arm = from(hand, 0.30F, -0.5F, -0.25F);
        Vector3f aim = v(0.0F, 0.38F, -1.0F).normalize();
        Vector3f across = new Vector3f(aim).cross(0.0F, 1.0F, 0.0F).normalize();
        Vector3f up = new Vector3f(across).cross(aim).normalize();
        int steps = 16;
        Key[] keys = new Key[steps + 4];
        keys[0] = key(1.2F, hand, arm, aim);
        for (int i = 0; i <= steps; i++) {
            float phi = Mth.HALF_PI + Mth.TWO_PI * 2.0F * i / steps;
            float c = Mth.cos(phi);
            float sn = Mth.sin(phi);
            Vector3f wand = new Vector3f(aim).add(new Vector3f(across).mul(c * 0.22F))
                    .add(new Vector3f(up).mul(sn * 0.22F));
            keys[i + 1] = key(1.6F + 5.0F * i / steps, new Vector3f(hand).add(c * 0.008F, sn * 0.008F, 0.0F),
                    arm, wand);
        }
        Vector3f backArm = v(0.1F, 1.0F, 0.15F);
        keys[steps + 2] = key(7.4F, v(0.38F, -0.24F, -0.60F), backArm, v(-0.08F, 1.0F, -0.25F));
        keys[steps + 3] = key(8.0F, v(0.40F, -0.22F, -0.62F), backArm, v(-0.1F, 1.0F, -0.3F));
        return keys;
    }

    /**
     * Obliviate: one slow, wide wipe across the whole view from right to left at the target's height, as if
     * wiping it out, fast through the middle and easing off to the left.
     */
    static Key[] obliviateKeys(Key from, FirstPersonCast.Variant variant) {
        Vector3f startArm = v(0.45F, 0.9F, -0.3F);
        Vector3f midArm = v(0.0F, 1.0F, -0.35F);
        Vector3f farArm = v(-0.35F, 0.95F, -0.3F);
        Vector3f endArm = v(-0.5F, 0.85F, -0.3F);
        Key end = key(OBLIVIATE_END, v(-0.38F, -0.14F, -0.88F), endArm,
                sideWand(new Vector3f(endArm).normalize()));
        return join(new Key[]{
                key(0.6F, v(0.40F, -0.06F, -0.82F), startArm, sideWand(new Vector3f(startArm).normalize())),
                key(1.3F, v(0.04F, -0.10F, -0.84F), midArm, inLine(midArm)),
                key(3.2F, v(-0.24F, -0.11F, -0.86F), farArm, sideWand(new Vector3f(farArm).normalize())),
                end}, sideTail(end, -1.0F, OBLIVIATE_END));
    }

    private static final float OBLIVIATE_END = 5.0F;

    /** Serpensortia: the wand drawn down in an S, a snake's wave, its tip swinging out at every bend. */
    static Key[] serpensortiaKeys(Key from, FirstPersonCast.Variant variant) {
        float[][] wave = {{0.24F, 0.08F}, {0.05F, 0.03F}, {0.22F, -0.04F}, {0.05F, -0.11F}, {0.22F, -0.18F},
                {0.12F, -0.24F}};
        float[] at = {0.6F, 1.3F, 2.1F, 2.9F, 3.7F, 4.5F};
        Key[] keys = new Key[wave.length + 1];
        for (int i = 0; i < wave.length; i++) {
            Vector3f hand = v(wave[i][0], wave[i][1], -0.84F);
            Vector3f arm = from(hand, 0.40F, -0.6F, -0.3F);
            float lean = wave[i][0] < 0.13F ? -0.3F : 0.3F;
            keys[i] = key(at[i], hand, arm, new Vector3f(arm).mul(0.4F).add(lean, 0.15F, -1.0F));
        }
        Vector3f lastArm = from(v(0.14F, -0.24F, -0.84F), 0.40F, -0.6F, -0.3F);
        keys[wave.length] = key(6.5F, v(0.14F, -0.24F, -0.84F), lastArm, v(0.0F, 0.40F, -1.0F));
        return keys;
    }

    /** The spells with a move of their own (Stupefy and the rest keep their gesture's). */
    private static final Map<com.hogwartsmagic.spell.SpellType, Motion> SIGNATURE =
            new java.util.EnumMap<>(com.hogwartsmagic.spell.SpellType.class);

    static {
        java.util.function.BiConsumer<Motion, com.hogwartsmagic.spell.SpellType[]> put = (motion, spells) -> {
            for (com.hogwartsmagic.spell.SpellType spell : spells) {
                SIGNATURE.put(spell, motion);
            }
        };
        put.accept(built(FirstPersonWandRig::expelliarmusKeys, SWEEP_MID, SWEEP_LENGTH, SWEEP_MID + 2.2F, 2.0F)
                .anchored(), new com.hogwartsmagic.spell.SpellType[]{com.hogwartsmagic.spell.SpellType.EXPELLIARMUS});
        put.accept(built(FirstPersonWandRig::petrificusKeys, PETRIFICUS_RELEASE, 13.0F, 1.8F, 1.8F),
                new com.hogwartsmagic.spell.SpellType[]{com.hogwartsmagic.spell.SpellType.PETRIFICUS_TOTALUS});
        put.accept(built(FirstPersonWandRig::diffindoKeys, 0.9F, tailLength(1.6F), 2.2F, 1.8F).anchored(),
                new com.hogwartsmagic.spell.SpellType[]{com.hogwartsmagic.spell.SpellType.DIFFINDO});
        put.accept(built(FirstPersonWandRig::sectumsempraKeys, 1.0F, tailLength(3.2F), 3.4F, 2.6F).anchored(),
                new com.hogwartsmagic.spell.SpellType[]{com.hogwartsmagic.spell.SpellType.SECTUMSEMPRA});
        put.accept(built(FirstPersonWandRig::punchKeys, PUNCH_RELEASE, 12.0F, 2.4F, 1.8F),
                new com.hogwartsmagic.spell.SpellType[]{com.hogwartsmagic.spell.SpellType.BOMBARDA,
                        com.hogwartsmagic.spell.SpellType.DEPULSO});
        put.accept(built(FirstPersonWandRig::avadaKeys, 1.4F, tailLength(2.2F), 2.4F, 2.2F).anchored(),
                new com.hogwartsmagic.spell.SpellType[]{com.hogwartsmagic.spell.SpellType.AVADA_KEDAVRA});
        put.accept(built(FirstPersonWandRig::crucioKeys, 1.2F, 14.0F, 1.4F, 1.8F).trembling(1.0F),
                new com.hogwartsmagic.spell.SpellType[]{com.hogwartsmagic.spell.SpellType.CRUCIO});
        put.accept(built(FirstPersonWandRig::patronusKeys, 1.3F, 15.0F, 7.0F, 4.0F).holdingLast(),
                new com.hogwartsmagic.spell.SpellType[]{com.hogwartsmagic.spell.SpellType.EXPECTO_PATRONUM});
        put.accept(built(FirstPersonWandRig::incarcerousKeys, 1.2F, 14.0F, 6.8F, 2.4F),
                new com.hogwartsmagic.spell.SpellType[]{com.hogwartsmagic.spell.SpellType.INCARCEROUS});
        put.accept(built(FirstPersonWandRig::obliviateKeys, 1.3F, tailLength(OBLIVIATE_END), OBLIVIATE_END, 4.0F)
                .anchored(),
                new com.hogwartsmagic.spell.SpellType[]{com.hogwartsmagic.spell.SpellType.OBLIVIATE});
        put.accept(built(FirstPersonWandRig::serpensortiaKeys, 1.3F, 13.0F, 4.8F, 4.0F),
                new com.hogwartsmagic.spell.SpellType[]{com.hogwartsmagic.spell.SpellType.SERPENSORTIA});
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
    private static Key vary(Key key, Key rest, FirstPersonCast.Variant v, float release) {
        Vector3f hand = new Vector3f(key.hand());
        Vector3f arm = new Vector3f(key.arm()).normalize();
        Vector3f wand = new Vector3f(key.wand()).normalize();
        if (v.mirror()) {
            hand.x = 2.0F * MIRROR_X - hand.x;
            arm.x = -arm.x;
            wand.x = -wand.x;
        }
        hand.sub(rest.hand()).mul(v.scale()).add(rest.hand());
        // The whole stroke turned round the strike point on screen: a slash comes in steep, flat or diagonal.
        // Full through the wind-up and the strike; the follow-through only half, so it drifts back to rest
        // instead of swinging the fist at the eye. The hand is kept on screen.
        float tilt = key.at() <= release + 0.01F ? v.tilt() : v.tilt() * 0.5F;
        if (tilt != 0.0F) {
            float cos = Mth.cos(tilt);
            float sin = Mth.sin(tilt);
            float dx = hand.x - STRIKE_POINT.x;
            float dy = hand.y - STRIKE_POINT.y;
            hand.x = STRIKE_POINT.x + dx * cos - dy * sin;
            hand.y = STRIKE_POINT.y + dx * sin + dy * cos;
            wand.set(wand.x * cos - wand.y * sin, wand.x * sin + wand.y * cos, wand.z);
            arm.set(arm.x * cos - arm.y * sin, arm.x * sin + arm.y * cos, arm.z);
        }
        hand.add(v.shiftX() * 0.35F, v.shiftY() * 0.35F, 0.0F);
        wand.y += v.pitchOffset() / 70.0F;
        // As in the reference: the hand stays at the right of the view at arm's length, the forearm stays
        // upright, and the wand does the travelling. A hand crossing to the left or near the eye fills the
        // screen with the fist or the sleeve.
        hand.x = Mth.clamp(hand.x, HAND_MIN_X, 0.62F);
        hand.y = Mth.clamp(hand.y, -0.26F, 0.06F);
        hand.z = Mth.clamp(hand.z, -0.95F, -0.68F);
        if (arm.y < 0.65F) {
            arm.y = 0.65F;
            arm.normalize();
        }
        keepWandInView(wand);
        return new Key(key.at(), hand, arm, wand);
    }

    /**
     * Keeps a wand direction (right-hand view coordinates) where the wand shows: pointing away from the eye,
     * never more than a little below level, and turned across the view enough to show its length (pointing
     * almost straight away from the eye it hides behind the fist). Applied to every key and to every frame's
     * pose, as the curve between keys can swing it out of view.
     */
    static void keepWandInView(Vector3f wand) {
        if (wand.lengthSquared() < 1.0E-8F) {
            wand.set(REST_AIM).sub(REST_HAND);
        }
        wand.normalize();
        if (wand.z > -0.2F) {
            wand.z = -0.2F;
            wand.normalize();
        }
        if (wand.y < -0.3F) {
            wand.y = -0.3F;
            wand.normalize();
        }
        float lateral = Mth.sqrt(wand.x * wand.x + wand.y * wand.y);
        if (lateral < 0.35F) {
            float lx = lateral < 1.0E-3F ? -0.64F : wand.x / lateral;
            float ly = lateral < 1.0E-3F ? 0.77F : wand.y / lateral;
            wand.set(lx * 0.35F, ly * 0.35F, -Mth.sqrt(1.0F - 0.35F * 0.35F));
        }
    }

    /** The hand never comes further left than this (view x at the hand's depth). */
    private static final float HAND_MIN_X = 0.14F;

    /** Where the strikes meet in the middle of the screen: the point a stroke's tilt turns round. */
    private static final Vector3f STRIKE_POINT = new Vector3f(0.05F, -0.13F, -0.70F);

    /**
     * The pose {@code since} ticks into a cast: a spline from rest through the keys and back to rest, with the
     * speed carried smoothly through every key (no stop at each one) and easing out into rest.
     */
    static Key pose(Motion motion, FirstPersonCast.Variant variant, Key rest, float since) {
        return pose(motion, variant, rest, since, rest);
    }

    /**
     * The pose {@code since} ticks into a cast that began with the arm at {@code from} (where it was drawn when
     * the cast started), so a new cast moves on from there instead of jumping back to rest first.
     */
    static Key pose(Motion motion, FirstPersonCast.Variant variant, Key rest, float since, Key from) {
        Key[] keys = motion.point() ? motion.builder().build(from, variant) : motion.keys();
        int n = keys.length;
        Key[] points = new Key[n + 2];
        points[0] = from.copy(0.0F);
        for (int i = 0; i < n; i++) {
            points[i + 1] = motion.point() ? keys[i] : vary(keys[i], rest, variant, motion.release());
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
        return placeArm(stack, pose, side, 0.0F);
    }

    /** As {@link #placeArm(PoseStack, Key, float)}, the fist rolled {@code roll} radians about the forearm. */
    static Vector3f placeArm(PoseStack stack, Key pose, float side, float roll) {
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
        if (roll != 0.0F) {
            // The same way the key's wand turns (clockwise as you look along the forearm); the left arm is the
            // mirror image, so it turns the other way about its own forearm.
            front.rotateAxis(roll * side, arm.x, arm.y, arm.z);
        }
        Matrix3f from = basis(axisP, frontP);
        Matrix3f to = basis(arm, front);
        Matrix3f rotation = to.mul(from.transpose());
        Vector3f offset = new Vector3f(hand).sub(rotation.transform(new Vector3f(handEnd)));
        stack.translate(offset.x, offset.y, offset.z);
        stack.mulPose(new Quaternionf().setFromNormalized(rotation));
        return rotation.transform(armPoint(side, 8.6F)).add(offset);
    }

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
        stack.mulPose(new Quaternionf().rotationTo(UP, new Vector3f(direction).normalize()));
        // Full length, but thinner: this close to the eye the 3D wand otherwise reads as a stake.
        stack.scale(WAND_SCALE * WAND_THIN, WAND_SCALE, WAND_SCALE * WAND_THIN);
        stack.mulPose(new Quaternionf().rotationZ(Mth.PI / 4.0F)); // the model's diagonal onto +y
        stack.translate(-grip.x, -grip.y, -grip.z);
    }

    private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);
    /** How thick the wand is drawn in first person, against its length. */
    private static final float WAND_THIN = 0.55F;

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
