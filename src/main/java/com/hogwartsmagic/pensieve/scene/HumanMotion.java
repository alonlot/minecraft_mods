package com.hogwartsmagic.pensieve.scene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Procedural human motion for {@code player} actors, as pure maths: joint angles as a function of the scene clock,
 * a per-actor seed, the path speed and the clip cues. Nothing here touches Minecraft, so it runs the same on every
 * client and is unit tested; {@code client/pensieve/ActorMotion} copies the six humanoid parts in and out.
 *
 * <p>Two layers:
 * <ul>
 *   <li><b>Life</b> ({@link #life}), always on unless the actor says {@code "life": false}: breathing, weight
 *   shifts and a drifting gaze while standing; a speed-matched gait (stride and arm swing from the real path speed,
 *   arms against the legs, a torso counter-twist, a bob and a forward lean) while walking; a slouch and hands in
 *   the lap while seated. All additive and small.</li>
 *   <li><b>Clips</b> ({@link Clips}), the player {@code anims} cues ({@link #CLIPS}): looping or one-shot
 *   gestures. A clip owns the parts it drives (their rotation is replaced, blended by the cue weight) and adds
 *   secondary motion (bounces, nods, leans) everywhere else. A new cue cross-fades from the previous clip.</li>
 * </ul>
 *
 * <p>Arrays are the six parts in {@link #PART_NAMES} order, three components each: rotations in degrees, positions
 * in model pixels (the part pivots, +y down, -z forward). Times are scene ticks. Every random choice comes from
 * the actor seed (its id), so a late joiner sees exactly what everybody else sees, and two actors never move in
 * lockstep: each has its own tempo, phase, energy and handedness.
 */
public final class HumanMotion {
    public static final int HEAD = 0, BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 3, RIGHT_LEG = 4, LEFT_LEG = 5, PARTS = 6;
    public static final List<String> PART_NAMES = List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg");

    /** The player clip names, as the scene writes them (lookups ignore case). {@code IDLE} is "no clip". */
    public static final List<String> CLIPS = List.of("IDLE", "CLAP", "CHEER", "CHEER_STAND", "TALK", "WHISPER",
            "NERVOUS", "WAVE", "HANDS_CLASPED", "READ_SCROLL", "REACH_PLACE", "SIT_DOWN", "STAND_UP");
    private static final int IDLE = 0, CLAP = 1, CHEER = 2, CHEER_STAND = 3, TALK = 4, WHISPER = 5, NERVOUS = 6,
            WAVE = 7, HANDS_CLASPED = 8, READ_SCROLL = 9, REACH_PLACE = 10, SIT_DOWN = 11, STAND_UP = 12;

    /** Ticks a clip cue cross-fades in over when the scene gives no (or a 0) {@code blend}. */
    public static final float DEFAULT_CLIP_BLEND = 4f;
    /** REACH_PLACE: the arms are highest at this tick of the clip, and the item touches down at this one. */
    public static final int REACH_PEAK_TICKS = 11, REACH_PLACE_TICKS = 19;
    /** One-shot lengths in ticks at the actor's own tempo (CHEER_STAND varies 54 to 66 per actor). */
    public static final int SIT_DOWN_TICKS = 20, STAND_UP_TICKS = 20, REACH_PLACE_LENGTH_TICKS = 34;

    /** Player model leg length in px: the hips are this far above the feet. */
    private static final float LEG = 12f;
    private static final float DEG = (float) (180 / Math.PI);

    private HumanMotion() {
    }

    // ==== names and seeds =========================================================================================

    /** The canonical clip name for {@code name} (any case), or null when it is not a player clip. */
    public static String clipName(String name) {
        if (name == null) return null;
        String up = name.toUpperCase(Locale.ROOT);
        return CLIPS.contains(up) ? up : null;
    }

    private static int clipId(String name) {
        String c = clipName(name);
        return c == null ? -1 : CLIPS.indexOf(c);
    }

    /** The actor's motion seed: a well-mixed hash of its id (stable across runs and machines). */
    public static long seed(String actorId) {
        long h = 0xcbf29ce484222325L;
        String s = actorId == null ? "" : actorId;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return mix(h);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static long sub(long seed, int salt) {
        return mix(seed ^ (salt * 0x9E3779B97F4A7C15L));
    }

    /** A fixed random number in [0, 1) for (seed, k). */
    private static double rnd(long seed, long k) {
        return (mix(seed + k * 0xD1B54A32D192ED03L) >>> 11) * 0x1.0p-53;
    }

    private static float range(long seed, int k, double lo, double hi) {
        return (float) (lo + (hi - lo) * rnd(seed, k));
    }

    // ==== smooth randomness =======================================================================================

    /** Smooth value noise in [-1, 1], C2 continuous, one new value per unit of {@code x}. */
    private static float noise(long seed, double x) {
        double fl = Math.floor(x);
        long i = (long) fl;
        double f = x - fl;
        double a = rnd(seed, i) * 2 - 1;
        double b = rnd(seed, i + 1) * 2 - 1;
        double s = f * f * f * (f * (f * 6 - 15) + 10);
        return (float) (a + (b - a) * s);
    }

    /** Two octaves of {@link #noise}, still in [-1, 1]. */
    private static float fbm(long seed, double x) {
        return (noise(seed, x) + 0.5f * noise(seed ^ 0x5bd1e995L, x * 2.07 + 13.1)) / 1.5f;
    }

    /**
     * A held-and-switched value in [-1, 1]: it holds a random value, then moves to the next over {@code tau}
     * seconds at a random moment in each {@code cell}. Glances, weight shifts and fidgets: hold, move, hold.
     */
    private static float steps(long seed, double s, double cell, double tau) {
        tau = Math.min(tau, cell * 0.38);
        long k = (long) Math.floor(s / cell);
        double at = k * cell + rnd(seed ^ 0x2545F4914F6CDD1DL, k) * 0.6 * cell;
        double prev = rnd(seed, k - 1) * 2 - 1;
        if (s < at) return (float) prev;
        double next = rnd(seed, k) * 2 - 1;
        return (float) (prev + (next - prev) * smooth((s - at) / tau));
    }

    private static double frac(double x) {
        return x - Math.floor(x);
    }

    /** Cubic smoothstep of x clamped to [0, 1]. */
    private static float smooth(double x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        return (float) (x * x * (3 - 2 * x));
    }

    /** 0 before a, 1 after b, smooth between. */
    private static float ramp(double s, double a, double b) {
        return smooth((s - a) / (b - a));
    }

    private static float lerp(float a, float b, float w) {
        return a + (b - a) * w;
    }

    /**
     * A keyed curve through (time, value) pairs, seconds: a cubic Hermite spline whose tangents come from the
     * neighbouring keys (Catmull-Rom on uneven times), flat at the two ends, held before the first and after the
     * last. Poses flow through the keys in arcs rather than stopping at each one.
     */
    private static float keys(double s, float... tv) {
        int n = tv.length / 2;
        if (s <= tv[0]) return tv[1];
        if (s >= tv[(n - 1) * 2]) return tv[(n - 1) * 2 + 1];
        int i = 0;
        while (i < n - 2 && s > tv[(i + 1) * 2]) i++;
        double t0 = tv[i * 2], v0 = tv[i * 2 + 1], t1 = tv[i * 2 + 2], v1 = tv[i * 2 + 3];
        double m0 = i == 0 ? 0 : (v1 - tv[i * 2 - 1]) / (t1 - tv[i * 2 - 2]);
        double m1 = i + 2 >= n ? 0 : (tv[i * 2 + 5] - v0) / (tv[i * 2 + 4] - t0);
        double h = t1 - t0;
        double u = (s - t0) / h;
        double u2 = u * u, u3 = u2 * u;
        return (float) ((2 * u3 - 3 * u2 + 1) * v0 + (u3 - 2 * u2 + u) * h * m0 + (-2 * u3 + 3 * u2) * v1
                + (u3 - u2) * h * m1);
    }

    // ==== body mechanics ==========================================================================================

    /**
     * Turns the torso about the hips (pitch forward, yaw twist, roll sideways, degrees): the body part turns, and
     * the neck (head and body pivots) and the shoulders (arm pivots) move to where the turned torso puts them. A
     * model part pivots at its top, so turning only the body part would swing the hips instead of the shoulders.
     */
    private static void hipTurn(float[] rot, float[] pos, float pitch, float yaw, float roll, float k) {
        if (pitch == 0 && yaw == 0 && roll == 0) return;
        double a = pitch / DEG, b = yaw / DEG, c = roll / DEG;
        rot[BODY * 3] += pitch * k;
        rot[BODY * 3 + 1] += yaw * k;
        rot[BODY * 3 + 2] += roll * k;
        float[] neck = turned(0, -LEG, 0, a, b, c);
        pos[HEAD * 3] += neck[0] * k;
        pos[HEAD * 3 + 1] += (neck[1] + LEG) * k;
        pos[HEAD * 3 + 2] += neck[2] * k;
        pos[BODY * 3] += neck[0] * k;
        pos[BODY * 3 + 1] += (neck[1] + LEG) * k;
        pos[BODY * 3 + 2] += neck[2] * k;
        for (int side = 0; side < 2; side++) {
            float x = side == 0 ? -5 : 5;
            int p = side == 0 ? RIGHT_ARM : LEFT_ARM;
            float[] sh = turned(x, -10, 0, a, b, c);
            pos[p * 3] += (sh[0] - x) * k;
            pos[p * 3 + 1] += (sh[1] + 10) * k;
            pos[p * 3 + 2] += sh[2] * k;
        }
    }

    /** {@code (x, y, z)} turned as a model part turns it: X, then Y, then Z (JOML rotationZYX). */
    private static float[] turned(double x, double y, double z, double a, double b, double c) {
        double y1 = y * Math.cos(a) - z * Math.sin(a);
        double z1 = y * Math.sin(a) + z * Math.cos(a);
        double x2 = x * Math.cos(b) + z1 * Math.sin(b);
        double z2 = -x * Math.sin(b) + z1 * Math.cos(b);
        double x3 = x2 * Math.cos(c) - y1 * Math.sin(c);
        double y3 = x2 * Math.sin(c) + y1 * Math.cos(c);
        return new float[]{(float) x3, (float) y3, (float) z2};
    }

    /** Moves every part (the whole figure) by {@code (x, y, z)} px. */
    private static void root(float[] pos, float x, float y, float z) {
        for (int p = 0; p < PARTS; p++) {
            pos[p * 3] += x;
            pos[p * 3 + 1] += y;
            pos[p * 3 + 2] += z;
        }
    }

    /**
     * How far (px) the hips rise when a seated actor stands, for a seat half a block high (a bench or a slab, the
     * Great Hall's benches): standing hips are a leg above the floor, seated ones on the seat.
     */
    public static float standLift(float scale) {
        return Math.max(0f, LEG - 8f / Math.max(0.25f, scale));
    }

    // ==== the life layer ==========================================================================================

    /**
     * The always-on layer, added to {@code rot}/{@code pos}. {@code speed} is the path speed in blocks per second
     * and {@code phase} the gait phase in cycles ({@link Odometer}); {@code seat} is how seated the actor is, 0 to 1.
     * {@code gain} (per part, 0 to 1) damps the life on parts the scene animates itself.
     */
    public static void life(float[] rot, float[] pos, long seed, float t, float scale, float speed, float phase,
                            float seat, float[] gain) {
        double s = t / 20.0;
        float energy = range(seed, 1, 0.75, 1.25);
        float stand = 1 - seat;
        float vn = speed / Math.max(0.25f, scale);
        float gw = ramp(vn, 0.08, 0.7) * stand;
        float still = stand * (1 - gw);
        float pitch = 0, yaw = 0, roll = 0;

        // Breathing: in over 40% of the breath, out, then a pause; about 4 s, drifting a little breath to breath.
        float period = range(seed, 2, 3.4, 4.6);
        double u = frac(s / period + rnd(seed, 3) + 0.15 * noise(sub(seed, 4), s / 9));
        float breath = u < 0.4 ? smooth(u / 0.4) : 1 - smooth((u - 0.4) / 0.45);
        pos[RIGHT_ARM * 3 + 1] -= 0.35f * breath;
        pos[LEFT_ARM * 3 + 1] -= 0.35f * breath;
        pos[HEAD * 3 + 1] -= 0.15f * breath;
        rot[RIGHT_ARM * 3 + 2] += 0.8f * breath * gain[RIGHT_ARM];
        rot[LEFT_ARM * 3 + 2] -= 0.8f * breath * gain[LEFT_ARM];
        pitch -= 0.7f * breath;
        rot[HEAD * 3] -= 0.5f * breath * gain[HEAD];

        // The gaze drifts: tiny slow head turns on top of wherever the scene points it.
        rot[HEAD * 3 + 1] += 2.5f * energy * fbm(sub(seed, 5), s / 2.7) * gain[HEAD];
        rot[HEAD * 3] += 1.5f * energy * fbm(sub(seed, 6), s / 3.3) * gain[HEAD];

        // Standing: the weight sits on one leg, then shifts to the other every few seconds; the hips slide over the
        // standing leg (both feet stay put), the other knee eases, the shoulders lean back over the middle.
        if (still > 0) {
            float ws = steps(sub(seed, 7), s + rnd(seed, 8) * 10, range(seed, 9, 4.5, 8), 1.1)
                    * (0.7f + 0.3f * noise(sub(seed, 10), s / 6)) * still;
            float dx = 0.5f * energy * ws;
            root(pos, dx, 0, 0);
            float legRoll = (float) Math.asin(dx / LEG) * DEG;
            rot[RIGHT_LEG * 3 + 2] += legRoll;
            rot[LEFT_LEG * 3 + 2] += legRoll;
            // dx > 0 is toward the left leg: the right one relaxes, and the other way round.
            rot[RIGHT_LEG * 3] -= 2.5f * Math.max(0, ws) * gain[RIGHT_LEG];
            rot[LEFT_LEG * 3] -= 2.5f * Math.max(0, -ws) * gain[LEFT_LEG];
            roll -= 0.9f * ws;
            roll += 0.4f * noise(sub(seed, 11), s / 5) * still;
        }

        // Seated: a slouch, the head lifted back to the eye line, hands resting in the lap, a slow sway.
        if (seat > 0) {
            float slouch = range(seed, 12, 4, 9) * seat;
            pitch += slouch;
            rot[HEAD * 3] -= 0.5f * slouch * gain[HEAD];
            float rest = range(seed, 13, -42, -30) * seat;
            float in = range(seed, 14, 6, 14) * seat;
            rot[RIGHT_ARM * 3] += rest * gain[RIGHT_ARM];
            rot[LEFT_ARM * 3] += (rest + range(seed, 15, -4, 4) * seat) * gain[LEFT_ARM];
            rot[RIGHT_ARM * 3 + 1] -= in * gain[RIGHT_ARM];
            rot[LEFT_ARM * 3 + 1] += in * gain[LEFT_ARM];
            roll += 0.6f * noise(sub(seed, 16), s / 4) * seat;
        }

        // Walking.
        if (gw > 0) {
            float stride = strideLength(vn);
            float theta = (float) Math.asin(Math.min(0.85, stride / (4 * 0.75))) * DEG * gw;
            // The swing forward is quicker than the push back: warp the phase (40% swing, 60% stance).
            double w = phase + 0.2 / (2 * Math.PI) * Math.sin(2 * Math.PI * phase);
            float leg = (float) Math.sin(2 * Math.PI * w);
            rot[RIGHT_LEG * 3] += -theta * leg * gain[RIGHT_LEG];
            rot[LEFT_LEG * 3] += theta * leg * gain[LEFT_LEG];
            // Arms swing against the legs, a beat late (follow-through), a little forward and clear of the body.
            float swing = (0.55f * theta + 4 * gw) * range(seed, 17, 0.8, 1.15);
            float arm = (float) Math.sin(2 * Math.PI * (w - 0.04));
            rot[RIGHT_ARM * 3] += (swing * arm - 2 * gw) * gain[RIGHT_ARM];
            rot[LEFT_ARM * 3] += (-swing * arm - 2 * gw) * gain[LEFT_ARM];
            rot[RIGHT_ARM * 3 + 2] += 2.5f * gw * gain[RIGHT_ARM];
            rot[LEFT_ARM * 3 + 2] -= 2.5f * gw * gain[LEFT_ARM];
            // The shoulders turn with the arms and the head turns back against them to keep the gaze steady.
            float twist = (3 + 0.08f * theta) * gw * arm;
            yaw += twist;
            rot[HEAD * 3 + 1] -= 0.8f * twist * gain[HEAD];
            // The hips sink as the legs spread and rise over the standing leg, and slide over it.
            float drop = 0.6f * LEG * (1 - (float) Math.cos(theta / DEG * leg));
            float side = 0.4f * gw * (float) Math.cos(2 * Math.PI * w);
            root(pos, side, drop, 0);
            roll -= 1.2f * gw * (float) Math.cos(2 * Math.PI * w);
            // Leaning into the walk, more so running; a small nod with each step.
            float lean = (2 + 1.5f * Math.min(3f, Math.max(0f, vn - 1.4f))) * gw;
            pitch += lean;
            rot[HEAD * 3] += (-0.5f * lean + 0.8f * gw * (float) Math.sin(4 * Math.PI * w)) * gain[HEAD];
        }
        hipTurn(rot, pos, pitch, yaw, roll, gain[BODY]);
    }

    /**
     * One full gait cycle (two steps) in blocks, at scale 1, for a speed in blocks per second: longer strides as
     * the walk gets faster (about 1.1 slow, 1.4 walking, 2 jogging, 2.5 running).
     */
    public static float strideLength(float speed) {
        return 0.95f + 0.45f * Math.min(3.5f, Math.max(0f, speed / 1.4f));
    }

    /**
     * The body yaw while the scene steers the look: the head goes where {@code look} says at once, the body follows
     * a few ticks behind and settles (a smooth lag, mean about 5 ticks, no overshoot). Keep look yaw continuous.
     */
    public static float followYaw(Track look, float t) {
        double sum = 0, weights = 0;
        for (int k = 1; k <= 14; k++) {
            double w = k * Math.exp(-k / 2.2);
            float[] v = look.sample(t - k);
            sum += w * (v.length > 0 ? v[0] : 0);
            weights += w;
        }
        return (float) (sum / weights);
    }

    // ==== distance travelled ======================================================================================

    /**
     * How far an actor's path has carried it (horizontally) and where its gait is, as functions of time, so the
     * feet are locked to the ground: the stride phase advances with distance, and stops when the path holds.
     */
    public static final class Odometer {
        private static final int MAX_TICKS = 72_000;
        private final int t0;
        private final float[] dist;
        private final float[] phase;

        public Odometer(Track path, float scale) {
            List<Keyframe> k = path.keys();
            float sc = Math.max(0.25f, scale);
            if (k.size() < 2) {
                t0 = 0;
                dist = null;
                phase = null;
                return;
            }
            int from = (int) Math.floor(k.get(0).t());
            int to = (int) Math.ceil(k.get(k.size() - 1).t()) + 1;
            int n = Math.min(MAX_TICKS, Math.max(2, to - from + 1));
            float[] d = new float[n];
            float[] ph = new float[n];
            float[] prev = path.sample(from);
            for (int i = 1; i < n; i++) {
                float[] p = path.sample(from + i);
                double dx = (p.length > 0 ? p[0] : 0) - (prev.length > 0 ? prev[0] : 0);
                double dz = (p.length > 2 ? p[2] : 0) - (prev.length > 2 ? prev[2] : 0);
                float step = (float) Math.sqrt(dx * dx + dz * dz);
                d[i] = d[i - 1] + step;
                float speed = step * 20 / sc;
                ph[i] = ph[i - 1] + step / (strideLength(speed) * sc);
                prev = p;
            }
            if (d[n - 1] < 1.0e-4f) {
                t0 = 0;
                dist = null;
                phase = null;
            } else {
                t0 = from;
                dist = d;
                phase = ph;
            }
        }

        private static float at(float[] table, int t0, float t) {
            float x = t - t0;
            if (x <= 0) return table[0];
            int last = table.length - 1;
            if (x >= last) return table[last];
            int i = (int) x;
            return table[i] + (table[i + 1] - table[i]) * (x - i);
        }

        /** Blocks walked by tick {@code t}. */
        public float distance(float t) {
            return dist == null ? 0 : at(dist, t0, t);
        }

        /** The gait phase in cycles at tick {@code t}. */
        public float phase(float t) {
            return phase == null ? 0 : at(phase, t0, t);
        }

        /** Path speed in blocks per second, averaged over 6 ticks so the gait eases in and out. */
        public float speed(float t) {
            if (dist == null) return 0;
            return (distance(t + 3) - distance(t - 3)) / 6f * 20f;
        }
    }

    // ==== clips ===================================================================================================

    /** One clip's pose at one moment: absolute targets for the parts it owns, additions, and offsets. */
    public static final class Frame {
        /** Absolute rotations (degrees) for owned parts. */
        public final float[] abs = new float[PARTS * 3];
        /** How much each part's rotation is replaced by {@link #abs}, 0 to 1. */
        public final float[] own = new float[PARTS];
        /** Rotations (degrees) added on top. */
        public final float[] add = new float[PARTS * 3];
        /** Offsets (px) added to the part pivots. */
        public final float[] off = new float[PARTS * 3];
        /** How seated the clip says the actor is (NaN: the clip has no opinion; the pose decides). */
        public float seat = Float.NaN;
        /** 0 to 1: hand the legs back to the life layer whatever the pose says (after STAND_UP). */
        public float lifeLegs;

        void clear() {
            Arrays.fill(abs, 0);
            Arrays.fill(own, 0);
            Arrays.fill(add, 0);
            Arrays.fill(off, 0);
            seat = Float.NaN;
            lifeLegs = 0;
        }

        /**
         * An arm, mirrored: {@code in} turns the hand toward the middle, {@code out} swings it away from the body
         * sideways. A part rolls (z) last, about the model's forward axis, so the same roll that lifts a hanging
         * arm outward swings a raised one in toward the head: the roll is scaled by cos(x) so "out" stays out at
         * any height (it fades to nothing with the arm straight forward, where a roll only spins it).
         */
        void arm(int part, float x, float in, float out, float own) {
            boolean right = part == RIGHT_ARM;
            float roll = out * (float) Math.cos(x / DEG);
            abs[part * 3] = x;
            abs[part * 3 + 1] = right ? -in : in;
            abs[part * 3 + 2] = right ? roll : -roll;
            this.own[part] = own;
        }

        void legs(float x, float splay, float own) {
            abs[RIGHT_LEG * 3] = x;
            abs[RIGHT_LEG * 3 + 1] = splay;
            abs[LEFT_LEG * 3] = x;
            abs[LEFT_LEG * 3 + 1] = -splay;
            this.own[RIGHT_LEG] = own;
            this.own[LEFT_LEG] = own;
        }

        /** Turns the torso about the hips (see {@link #hipTurn}), added on top. */
        void lean(float pitch, float yaw, float roll) {
            hipTurn(add, off, pitch, yaw, roll, 1f);
        }

        void lift(float x, float y, float z) {
            root(off, x, y, z);
        }

        /** Applies this frame to {@code rot}/{@code pos} at weight {@code w}. */
        void applyTo(float[] rot, float[] pos, float w) {
            if (w <= 0) return;
            for (int p = 0; p < PARTS; p++) {
                float o = own[p] * w;
                for (int c = 0; c < 3; c++) {
                    int i = p * 3 + c;
                    rot[i] = lerp(rot[i], abs[i], o) + add[i] * w;
                    pos[i] += off[i] * w;
                }
            }
        }
    }

    /** One-shot length in ticks for this actor, or +infinity for a loop. */
    private static float length(int clip, long seed) {
        if (clip == CHEER_STAND) return cheerStandSeconds(seed) * 20;
        if (clip == SIT_DOWN) return SIT_DOWN_TICKS;
        if (clip == STAND_UP) return STAND_UP_TICKS;
        if (clip == REACH_PLACE) return REACH_PLACE_LENGTH_TICKS;
        return Float.POSITIVE_INFINITY;
    }

    private static float cheerStandSeconds(long seed) {
        return range(sub(seed, 300), 0, 2.7, 3.3);
    }

    /** Fills {@code f} with clip {@code name} at {@code local} ticks after its cue. For tests and tools. */
    public static void clip(Frame f, String name, long seed, float local, float scale, float headYaw) {
        f.clear();
        int id = clipId(name);
        if (id > 0) evaluate(f, id, seed, local, scale, headYaw);
    }

    private static void evaluate(Frame f, int clip, long seed, float local, float scale, float headYaw) {
        long cs = sub(seed, 100 + clip);
        double s = Math.max(0, local) / 20.0;
        if (clip == CLAP) clap(f, cs, s);
        else if (clip == CHEER) cheer(f, cs, s, 0);
        else if (clip == CHEER_STAND) cheerStand(f, cs, seed, s, scale);
        else if (clip == TALK) talk(f, cs, s);
        else if (clip == WHISPER) whisper(f, cs, s, headYaw);
        else if (clip == NERVOUS) nervous(f, cs, s);
        else if (clip == WAVE) wave(f, cs, s);
        else if (clip == HANDS_CLASPED) handsClasped(f, cs, s);
        else if (clip == READ_SCROLL) readScroll(f, cs, s);
        else if (clip == REACH_PLACE) reachPlace(f, cs, s);
        else if (clip == SIT_DOWN) sitDown(f, cs, s);
        else if (clip == STAND_UP) standUp(f, cs, s);
    }

    /**
     * CLAP: hands meeting in front of the chest about 2.5 times a second. The rhythm wanders (each actor its own
     * tempo, phase and drift), one hand does most of the travel, the hands dip on each contact and the body and head
     * take a small bounce from it.
     */
    private static void clap(Frame f, long cs, double s) {
        float rate = range(cs, 1, 2.2, 2.8);
        float energy = range(cs, 2, 0.7, 1.15);
        double c = rate * s + rnd(cs, 3) + 0.3 * noise(sub(cs, 4), s * 0.9);
        double u = frac(c);
        float gap = (float) Math.pow(Math.sin(Math.PI * u), 0.75);
        float hit = (1 - gap) * (1 - gap) * (1 - gap);
        float lateHit = (float) Math.pow(1 - Math.pow(Math.sin(Math.PI * frac(c - 0.06)), 0.75), 3);
        float height = -58 - 6 * energy + 2.5f * noise(sub(cs, 5), s * 0.5) + 3 * (1 - gap);
        boolean rightLeads = rnd(cs, 6) < 0.6;
        // Arms 4 px wide meet when the hand centres are 4 px apart: about 54 degrees of inward turn between them.
        float lead = lerp(34, -2 - 4 * energy, gap);
        float follow = lerp(20, 6, gap);
        float spread = 8 * gap;
        f.arm(RIGHT_ARM, height, rightLeads ? lead : follow, spread, 1);
        f.arm(LEFT_ARM, height + range(cs, 7, -4, 4), rightLeads ? follow : lead, spread, 1);
        f.lean(1.5f * energy * hit, 0, 0);
        f.lift(0, 0.25f * hit, 0);
        f.add[HEAD * 3] += 1.2f * lateHit + 2 * noise(sub(cs, 8), s * 0.4);
        f.add[HEAD * 3 + 1] += 4 * fbm(sub(cs, 9), s * 0.35);
    }

    /** The pumping part of a cheer: both fists punching up, the right or left leading, or alternating. */
    private static float pump(long cs, double s, int arm) {
        float rate = range(cs, 11, 1.6, 2.3);
        double c = rate * s + rnd(cs, 12) + 0.25 * noise(sub(cs, 13), s * 0.8);
        double offset = rnd(cs, 14) < 0.3 ? 0.5 : 0.07;
        double u = frac(c - (arm == LEFT_ARM ? offset : 0));
        // A quick punch up then a slower return: skewed so the thrust takes a third of the beat.
        double w = u + 0.25 / (2 * Math.PI) * Math.sin(2 * Math.PI * u);
        return (float) (0.5 + 0.5 * Math.cos(2 * Math.PI * w));
    }

    /**
     * CHEER: arms thrown up (a dip to load, a throw past the top, a settle), then fists pumping, the body bouncing
     * on the seat with every punch and the head thrown back, shouting. {@code delay} seconds of wait first.
     */
    private static void cheer(Frame f, long cs, double s, double delay) {
        double t = s - delay;
        float energy = range(cs, 15, 0.8, 1.2);
        float intro = keys(t, 0, -18, 0.1f, -26, 0.3f, -168, 0.42f, -166, 0.55f, -160);
        float into = ramp(t, 0.35, 0.65);
        for (int arm = RIGHT_ARM; arm <= LEFT_ARM; arm++) {
            float pumped = -150 - 22 * energy * pump(cs, t, arm);
            float x = lerp(intro, pumped, into);
            float out = 16 + 6 * noise(sub(cs, 16 + arm), t * 0.6) + (arm == RIGHT_ARM ? 0 : range(cs, 18, -4, 4));
            f.arm(arm, x, -4, out, 1);
        }
        float beat = pump(cs, t, RIGHT_ARM) * into;
        f.lift(0, -0.5f * energy * beat, 0);
        f.lean(lerp(keys(t, 0, 3, 0.15f, -5, 0.5f, -3), -2 + 2 * beat, into), 0, 2 * noise(sub(cs, 19), t * 0.5));
        f.add[HEAD * 3] += lerp(keys(t, 0, 4, 0.2f, -14, 0.5f, -10), -9 + 3 * pump(cs, t - 0.05, RIGHT_ARM), into);
        f.add[HEAD * 3 + 1] += 6 * fbm(sub(cs, 20), t * 0.45);
    }

    /**
     * CHEER_STAND (one-shot, 2.7-3.3 s): from the bench, weight forward and hands pushing off, up (the hips rise
     * by {@link #standLift}), arms thrown up and pumping while bouncing, then down again: arms come down, weight
     * forward, back onto the bench, a settle. The legs stay seated once it is over; the arms go back to the pose.
     */
    private static void cheerStand(Frame f, long cs, long seed, double s, float scale) {
        float len = cheerStandSeconds(seed);
        double q = s * 3.0 / len;                 // the choreography is written for 3 s
        float up = Math.max(0f, Math.min(1f, keys(q, 0, 0, 0.22f, 0, 0.72f, 1, 2.15f, 1, 2.75f, 0)));
        f.legs(lerp(-90, -6, up), 8 * (1 - up), 1);
        f.seat = 1 - up;
        float lift = standLift(scale);
        float bounce = q > 0.8 && q < 2.1 ? pump(cs, q, RIGHT_ARM) * ramp(q, 0.8, 1.0) * (1 - ramp(q, 1.9, 2.1)) : 0;
        f.lift(0, -lift * up - 0.6f * bounce, -3 * up);
        f.lean(keys(q, 0, 0, 0.22f, 24, 0.6f, 7, 0.82f, -4, 2.0f, -3, 2.35f, 16, 2.75f, 2, 2.92f, -1, 3.0f, 0), 0,
                2 * noise(sub(cs, 21), q * 0.5) * up);
        float own = 1 - ramp(q, 2.78, 3.0);
        float pumping = ramp(q, 0.85, 1.05) * (1 - ramp(q, 2.0, 2.2));
        for (int arm = RIGHT_ARM; arm <= LEFT_ARM; arm++) {
            float delay = arm == RIGHT_ARM ? 0 : range(cs, 22, 0.03, 0.09);
            double qa = q - delay;
            float keyed = keys(qa, 0, -32, 0.22f, -48, 0.5f, -62, 0.78f, -170, 0.95f, -160, 2.05f, -158,
                    2.3f, -92, 2.62f, -40, 2.95f, -32);
            float x = lerp(keyed, -148 - 22 * pump(cs, qa, arm), pumping);
            float out = keys(qa, 0, 2, 0.5f, 4, 0.8f, 18, 2.1f, 16, 2.6f, 4, 3f, 2);
            f.arm(arm, x, keys(qa, 0, 8, 0.4f, 4, 0.8f, -4, 2.2f, -4, 2.8f, 8), out, own);
        }
        f.add[HEAD * 3] += keys(q, 0, 0, 0.22f, 8, 0.7f, -12, 2.1f, -9, 2.4f, 6, 3f, 0) + 3 * bounce;
        f.add[HEAD * 3 + 1] += 7 * fbm(sub(cs, 23), q * 0.5) * up;
    }

    /**
     * TALK: speaking to someone. The leading hand (mostly the right) gestures in phrases, beating small accents on
     * the stressed syllables; the other hand joins now and then; the head nods on the beats and tilts.
     */
    private static void talk(Frame f, long cs, double s) {
        int lead = rnd(cs, 30) < 0.7 ? RIGHT_ARM : LEFT_ARM;
        int other = lead == RIGHT_ARM ? LEFT_ARM : RIGHT_ARM;
        float energy = range(cs, 31, 0.7, 1.2);
        float phrase = Math.max(0, 0.35f + 0.65f * fbm(sub(cs, 32), s / 1.7));
        double beatPhase = range(cs, 33, 1.9, 2.6) * s + 0.4 * noise(sub(cs, 34), s * 0.7);
        float beat = (float) Math.sin(2 * Math.PI * beatPhase);
        float x = -24 - 32 * phrase * energy + 5 * phrase * beat;
        f.arm(lead, x, 8 + 10 * phrase, 4 + 5 * noise(sub(cs, 35), s * 0.6), 1);
        float join = Math.max(0, noise(sub(cs, 36), s / 2.3));
        f.arm(other, -8 - 22 * join * energy + 2 * join * beat, 6 + 6 * join, 3, 1);
        f.add[HEAD * 3] += 3 * phrase * (float) Math.sin(Math.PI * beatPhase) + 1.5f * noise(sub(cs, 37), s * 0.5);
        f.add[HEAD * 3 + 1] += 5 * noise(sub(cs, 38), s / 2.1);
        f.add[HEAD * 3 + 2] += 3 * noise(sub(cs, 39), s / 2.9);
        f.lean(2 + 1.5f * noise(sub(cs, 40), s / 3), 0, 1.2f * noise(sub(cs, 41), s / 3.7));
    }

    /**
     * WHISPER: leaning toward whoever the head is turned to ({@code look}), the hand on that side raised by the
     * mouth, head bowed and tilted in, shoulders up.
     */
    private static void whisper(Frame f, long cs, double s, float headYaw) {
        double h = headYaw / DEG;
        float lean = 12 + 3 * noise(sub(cs, 50), s / 2);
        float in = ramp(s, 0, 0.5);
        f.lean((float) (lean * Math.cos(h)) * in, 0, (float) (-lean * Math.sin(h)) * in);
        boolean right = Math.sin(h + (rnd(cs, 51) < 0.7 ? 0.09 : -0.09)) > 0;
        int arm = right ? RIGHT_ARM : LEFT_ARM;
        float x = keys(s, 0, -20, 0.15f, -60, 0.45f, -128, 0.6f, -123) + 3 * noise(sub(cs, 52), s * 0.8);
        f.arm(arm, x, 30 + 3 * noise(sub(cs, 53), s * 0.5), 0, 1);
        f.add[HEAD * 3] += 6 * in + 2 * noise(sub(cs, 54), s * 0.9);
        f.add[HEAD * 3 + 2] += (right ? -8 : 8) * in;
        f.off[RIGHT_ARM * 3 + 1] -= 0.4f * in;
        f.off[LEFT_ARM * 3 + 1] -= 0.4f * in;
    }

    /**
     * NERVOUS: wringing hands in front, then dropping them and fiddling; darting glances (quick turns, then a hold),
     * frequent weight shifts and a tense, slightly rocking body.
     */
    private static void nervous(Frame f, long cs, double s) {
        float wring = smooth(0.5 + 0.9 * steps(sub(cs, 60), s, 2.6, 0.5));
        double fid = range(cs, 61, 2.6, 3.6) * s + 0.5 * noise(sub(cs, 62), s);
        float a = (float) Math.sin(2 * Math.PI * fid);
        float b = (float) Math.sin(2 * Math.PI * (fid + 0.3));
        float tremble = 1.5f * noise(sub(cs, 63), s * 5);
        f.arm(RIGHT_ARM, lerp(-6 + tremble, -30 + 3 * a, wring), lerp(2, 36 + 5 * a, wring), lerp(3, 0, wring), 1);
        f.arm(LEFT_ARM, lerp(-5 - tremble, -28 - 3 * b, wring), lerp(2, 36 + 5 * b, wring), lerp(3, 0, wring), 1);
        f.add[HEAD * 3 + 1] += 26 * steps(sub(cs, 64), s, range(cs, 65, 1.1, 1.6), 0.14);
        f.add[HEAD * 3] += 7 * steps(sub(cs, 66), s, range(cs, 67, 1.5, 2.1), 0.18) + 3;
        float shift = 0.7f * steps(sub(cs, 68), s, 2.2, 0.6);
        f.lift(shift, 0, 0);
        float legRoll = (float) Math.asin(shift / LEG) * DEG;
        f.add[RIGHT_LEG * 3 + 2] += legRoll;
        f.add[LEFT_LEG * 3 + 2] += legRoll;
        f.lean(1.5f * noise(sub(cs, 69), s * 0.4) + 1, 0, -1.2f * shift);
        f.off[RIGHT_ARM * 3 + 1] -= 0.5f;
        f.off[LEFT_ARM * 3 + 1] -= 0.5f;
    }

    /**
     * WAVE: a big, open wave (Hagrid's): the arm rises with a little wind-up, then swings side to side like a
     * pendulum (the hand dips at each end), the body rocking against it and the free arm swinging a touch.
     */
    private static void wave(Frame f, long cs, double s) {
        float rate = range(cs, 70, 1.2, 1.6);
        double c = rate * (s - 0.45) + 0.15 * noise(sub(cs, 71), s * 0.6);
        float sw = (float) Math.sin(2 * Math.PI * c);
        float into = ramp(s, 0.35, 0.7);
        float raise = keys(s, 0, -8, 0.12f, 6, 0.45f, -172, 0.6f, -164);
        float x = lerp(raise, -166 + 9 * sw * sw, into);
        float out = lerp(keys(s, 0, 4, 0.45f, 24, 0.6f, 30), 30 + 24 * sw, into);
        f.arm(RIGHT_ARM, x, -6 * sw * into, out, 1);
        float lag = (float) Math.sin(2 * Math.PI * (c - 0.15)) * into;
        f.lean(0, 2 * lag, -2.5f * lag);
        f.add[LEFT_ARM * 3] += 3 * lag;
        f.add[HEAD * 3 + 2] += 3 * lag;
        f.add[HEAD * 3] -= 4 * into;
    }

    /** HANDS_CLASPED: the formal stance; hands together at the waist, chest up, chin up, a slow nod and a fidget. */
    private static void handsClasped(Frame f, long cs, double s) {
        float fidget = 1.5f * steps(sub(cs, 80), s, 3.5, 0.4);
        f.arm(RIGHT_ARM, -34 + fidget, 40, 0, 1);
        f.arm(LEFT_ARM, -33 - 0.5f * fidget, 39, 0, 1);
        f.lean(-1.5f, 0, 0);
        f.add[HEAD * 3] += -2 + 2 * noise(sub(cs, 81), s / 3);
    }

    /**
     * READ_SCROLL: the scroll held open in both hands at reading height, head bowed to it; the eyes run along each
     * line (the head turns slowly across and flicks back) and drift down the page.
     */
    private static void readScroll(Frame f, long cs, double s) {
        float tremor = 0.6f * noise(sub(cs, 90), s * 3);
        f.arm(RIGHT_ARM, -62 + tremor, 14, 0, 1);
        f.arm(LEFT_ARM, -58 - tremor, 16, 0, 1);
        double lines = s / range(cs, 91, 1.4, 1.9) + rnd(cs, 92);
        double u = frac(lines);
        float across = u < 0.85 ? lerp(-5, 5, (float) (u / 0.85)) : lerp(5, -5, smooth((u - 0.85) / 0.15));
        f.add[HEAD * 3 + 1] += across * ramp(s, 0, 0.4);
        double page = frac(lines / 6);
        float down = page < 0.85 ? (float) (page / 0.85) : 1 - smooth((page - 0.85) / 0.15);
        f.add[HEAD * 3] += (24 + 3 * down + 1.5f * noise(sub(cs, 93), s * 0.5)) * ramp(s, 0, 0.4);
        f.lean(3, 0, 0);
    }

    /**
     * REACH_PLACE (one-shot 1.7 s): lowering something onto a seated head in front, with both hands. A small
     * wind-up, up and over in an arc (highest at {@link #REACH_PEAK_TICKS}), down onto the head at
     * {@link #REACH_PLACE_TICKS} as the body leans in and the head looks down, a lift as the hands let go, then the
     * arms come home and hand back to the pose. The left hand trails the right.
     */
    private static void reachPlace(Frame f, long cs, double s) {
        float own = ramp(s, 0, 0.12) * (1 - ramp(s, 1.45, 1.7));
        for (int arm = RIGHT_ARM; arm <= LEFT_ARM; arm++) {
            double t = arm == RIGHT_ARM ? s : s - 0.07;
            float amt = arm == RIGHT_ARM ? 1 : 0.92f;
            float x = keys(t, 0, -10, 0.12f, 4, 0.55f, -158, 0.95f, -100, 1.2f, -96, 1.5f, -30, 1.7f, -10) * amt;
            float out = keys(t, 0, 0, 0.12f, 4, 0.55f, 16, 0.95f, 4, 1.7f, 2);
            float in = keys(t, 0, 0, 0.55f, 0, 0.95f, 12, 1.2f, 12, 1.7f, 0);
            f.arm(arm, x, in, out, own);
        }
        f.lean(keys(s, 0, 0, 0.12f, -2, 0.55f, 4, 0.95f, 14, 1.2f, 12, 1.7f, 0), 0, 0);
        f.add[HEAD * 3] += keys(s, 0, 0, 0.55f, -10, 0.95f, 22, 1.3f, 10, 1.7f, 0);
    }

    /**
     * SIT_DOWN (one-shot 1 s; the path should carry the root into the seat meanwhile): a glance down at the seat,
     * arms out a little for balance, weight forward over the feet while the legs fold, hands bracing on the bench,
     * a settle with a small bounce and the weight going back. Ends seated (legs held, life seated) for as long as the
     * cue is in force; the arms go back to the pose.
     */
    private static void sitDown(Frame f, long cs, double s) {
        f.legs(keys(s, 0, 0, 0.2f, -6, 0.75f, -90, 0.86f, -93, 1.0f, -90), 8 * ramp(s, 0.4, 0.9), 1);
        f.seat = ramp(s, 0.5, 0.9);
        float lean = keys(s, 0, 0, 0.25f, 18, 0.6f, 24, 0.8f, 8, 0.92f, -2, 1.0f, 0);
        f.lean(lean, 0, 0);
        f.add[HEAD * 3] += keys(s, 0, 0, 0.15f, 12, 0.5f, 4, 0.9f, 0) - 0.4f * lean;
        f.lift(0, 0.5f * ramp(s, 0.72, 0.82) * (1 - ramp(s, 0.84, 0.98)), 0);
        float own = ramp(s, 0, 0.15) * (1 - ramp(s, 0.9, 1.25));
        float x = keys(s, 0, -4, 0.25f, -22, 0.6f, -42, 0.85f, -36, 1.0f, -32);
        float out = keys(s, 0, 2, 0.25f, 10, 0.6f, 6, 1.0f, 2);
        f.arm(RIGHT_ARM, x, keys(s, 0, 0, 0.6f, 4, 1f, 8), out, own);
        f.arm(LEFT_ARM, x + range(cs, 100, -3, 3), keys(s, 0, 0, 0.6f, 4, 1f, 8), out, own);
    }

    /**
     * STAND_UP (one-shot 1 s; the path should carry the root out of the seat meanwhile): weight well forward, hands
     * pushing on the knees, the legs straighten as the body comes up, the arms swing back with the rise and settle.
     * Ends standing: the legs go back to the life layer (and walk) whatever the pose says, for as long as the cue
     * is in force.
     */
    private static void standUp(Frame f, long cs, double s) {
        float up = keys(s, 0, 0, 0.3f, 0.04f, 0.75f, 0.96f, 0.88f, 1.03f, 1.0f, 1);
        f.legs(lerp(-90, 0, up), 8 * (1 - up), 1);
        f.lifeLegs = ramp(s, 0.85, 1.05);
        f.seat = 1 - ramp(s, 0.35, 0.8);
        float lean = keys(s, 0, 0, 0.3f, 30, 0.55f, 22, 0.8f, 2, 0.9f, -3, 1.05f, 0);
        f.lean(lean, 0, 0);
        f.add[HEAD * 3] += -0.45f * lean;
        float own = ramp(s, 0, 0.12) * (1 - ramp(s, 0.95, 1.3));
        float x = keys(s, 0, -32, 0.3f, -50, 0.55f, -30, 0.8f, 4, 0.95f, 2, 1.2f, 0);
        float out = keys(s, 0, 2, 0.3f, 6, 0.8f, 3, 1.2f, 1);
        f.arm(RIGHT_ARM, x, keys(s, 0, 8, 0.3f, 6, 0.8f, 0), out, own);
        f.arm(LEFT_ARM, x + range(cs, 110, -3, 3), keys(s, 0, 8, 0.3f, 6, 0.8f, 0), out, own);
    }

    // ==== cue playback ============================================================================================

    /**
     * An actor's clip cues, in time order. The cue in force plays from its {@code t}; it cross-fades in from the
     * clip before it over its {@code blend} ticks (smoothly; {@link #DEFAULT_CLIP_BLEND} when 0 or absent), so a
     * change of clip never pops. Cues with unknown names are ignored (the validator reports them).
     */
    public static final class Clips {
        private final float[] times;
        private final float[] blends;
        private final int[] ids;
        private final long seed;
        private final Frame a = new Frame();
        private final Frame b = new Frame();

        public Clips(List<SceneDef.Cue> cues, long seed) {
            List<SceneDef.Cue> known = new ArrayList<>();
            for (SceneDef.Cue c : cues) {
                if (clipId(c.name()) >= 0) known.add(c);
            }
            known.sort((x, y) -> Float.compare(x.t(), y.t()));
            times = new float[known.size()];
            blends = new float[known.size()];
            ids = new int[known.size()];
            for (int i = 0; i < ids.length; i++) {
                times[i] = known.get(i).t();
                blends[i] = known.get(i).blend() > 0 ? known.get(i).blend() : DEFAULT_CLIP_BLEND;
                ids[i] = clipId(known.get(i).name());
            }
            this.seed = seed;
        }

        public boolean isEmpty() {
            return ids.length == 0;
        }

        private int active(float t) {
            int found = -1;
            for (int i = 0; i < times.length && times[i] <= t; i++) found = i;
            return found;
        }

        private float weight(int i, float t) {
            return smooth((t - times[i]) / blends[i]);
        }

        /** The time of the cue in force, or -infinity before the first. */
        public float cueTime(float t) {
            int i = active(t);
            return i < 0 ? Float.NEGATIVE_INFINITY : times[i];
        }

        private void frame(Frame f, int i, float t, float scale, float headYaw) {
            f.clear();
            if (ids[i] != IDLE) evaluate(f, ids[i], seed, t - times[i], scale, headYaw);
        }

        /** How seated the actor is: the clips' say over {@code poseSeat} (what the pose in force says). */
        public float seat(float t, float poseSeat) {
            int i = active(t);
            if (i < 0) return poseSeat;
            float w = weight(i, t);
            float before = i > 0 ? clipSeat(i - 1, t, poseSeat) : poseSeat;
            return lerp(before, clipSeat(i, t, poseSeat), w);
        }

        private float clipSeat(int i, float t, float poseSeat) {
            int id = ids[i];
            if (id != SIT_DOWN && id != STAND_UP && id != CHEER_STAND) return poseSeat;
            frame(a, i, t, 1f, 0f);
            return Float.isNaN(a.seat) ? poseSeat : a.seat;
        }

        /**
         * Applies the clip in force (cross-fading from the one before) to {@code rot}/{@code pos}. Returns how much
         * the legs should be handed back to the life layer afterwards (0 to 1).
         */
        public float apply(float[] rot, float[] pos, float t, float scale, float headYaw) {
            int i = active(t);
            if (i < 0) return 0;
            float w = weight(i, t);
            float legs = 0;
            if (w < 1 && i > 0) {
                frame(b, i - 1, t, scale, headYaw);
                b.applyTo(rot, pos, 1 - w);
                legs += b.lifeLegs * (1 - w);
            }
            frame(a, i, t, scale, headYaw);
            a.applyTo(rot, pos, w);
            legs += a.lifeLegs * w;
            return Math.min(1f, legs);
        }

        /** True when the clip in force is a one-shot that has run its course (for tools). */
        public boolean finished(float t) {
            int i = active(t);
            return i >= 0 && t - times[i] >= length(ids[i], seed);
        }
    }
}
