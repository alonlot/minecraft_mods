package com.hogwartsmagic.pensieve.scene;

import java.util.ArrayList;
import java.util.List;

/**
 * The memory Horntail ({@code hogwartsmagic:memory_horntail}) in flight, as pure maths: how she flies her path and how
 * her wings beat. No Minecraft or client types, so the client, the tests and the Python mirror
 * ({@code build_scripts/pensieve_scenes/dragon_engine.py}: {@code horntail_frame}, {@code beat_rate},
 * {@code wing_amp}, {@code wing_phase}, {@code downstroke_ticks}, kept in step by {@code horntail_cases.json}) use the
 * same numbers.
 *
 * <p><b>Frame.</b> The {@code path} is the root: the ground point under her body at rest (her feet stand on it). Her
 * body's pivot is {@link #PIVOT_Y} x scale above it, and her pitch and roll turn the whole dragon about that pivot:
 * world = root + scale x (M x (local - pivot) + pivot), M = {@link BroomFlight#frame}(yaw, pitch, roll), local x her
 * left, y up, z toward her snout. Yaw is the Minecraft yaw, pitch nose down positive, roll right side down positive.
 *
 * <p><b>Flying her path</b> ({@link #place}). With a {@code look} she takes its yaw and pitch, and its roll when any
 * look key gives one (else the auto-bank). Without one, through {@link BroomFlight}'s path helpers: the yaw follows
 * the heading, the pitch the climb angle (clamped to {@link #MAX_CLIMB}, smoothstepped to level below
 * {@link #CLIMB_FADE_SPEED}) and the roll is {@link #bank}.
 *
 * <p><b>Wingbeats</b> are a pure function of the path and the clock, so a late joiner sees the same stroke: the rate
 * ({@link #beatRate}) and the amplitude ({@link #wingAmp}) follow her speed and climb, the phase ({@link #wingPhase}) is
 * the rate summed tick by tick from scene time 0, {@link #strokeAngle} shapes each beat (a quicker downstroke),
 * {@link #boneBeat} lags the outer bones, and {@link #downstrokes} lists the ticks the humerus is mid-downstroke
 * (where the whump of a beat peaks), for the wingbeat sound cues.
 */
public final class DragonFlight {
    private DragonFlight() {
    }

    /** The body pivot above the root at scale 1 (blocks): the model's body pivot, (24 - 9) px, times the 1.15 render scale. */
    public static final double PIVOT_Y = (24 - 9) / 16.0 * 1.15;
    /** Auto-bank: degrees per block/tick^2 of sideways acceleration, the most it banks, the speed it fades out below. */
    public static final double BANK_GAIN = 1000, MAX_BANK = 50, BANK_FADE_SPEED = 0.1;
    /** The climb pitch's clamp (degrees) and the speed (blocks per tick) below which it fades to level. */
    public static final double MAX_CLIMB = 45, CLIMB_FADE_SPEED = 0.05;

    /**
     * Wing rate (rad/tick) at a hover and at a cruise, and the speeds (blocks per tick) they hold at. A hover stroke
     * lasts about 1.3 s and a cruise stroke 2.2 s: a heavy flier of her ~7 block span.
     */
    public static final double HOVER_RATE = 0.24, CRUISE_RATE = 0.14, HOVER_SPEED = 0.1, CRUISE_SPEED = 0.8;
    /** Climbing beats faster: x (1 + CLIMB_BOOST) at a climb of CLIMB_FULL blocks/tick and steeper. */
    public static final double CLIMB_BOOST = 0.35, CLIMB_FULL = 0.3;
    /**
     * A glide (no beat, wings held half up): full from {@link #GLIDE_SPEED} and a sink of {@link #GLIDE_SINK}
     * blocks/tick, eased in over the {@link #GLIDE_RAMP} below each.
     */
    public static final double GLIDE_SPEED = 0.9, GLIDE_SINK = 0.15, GLIDE_RAMP = 0.1;
    /**
     * Wing amplitude (the model's beat gain) at a hover or climb, at a cruise and in a glide. At 0.70 her fingertips
     * stay about a block off her midplane at the bottom of a stroke (at the wild dragon's 0.85 they met under her
     * belly); dragon_engine's self-check holds them at least {@link #MIDPLANE_CLEARANCE} from it.
     */
    public static final double HOVER_AMP = 0.70, CRUISE_AMP = 0.60, GLIDE_AMP = 0.15;
    /** The scale of the memory takeoff's own beat (the wild dragon's takeoff gains would carry her fingertips across). */
    public static final double TAKEOFF_GAIN = 0.75;
    /** How close (blocks, scale 1) a fingertip may come to her body's midplane at any point of a stroke. */
    public static final double MIDPLANE_CLEARANCE = 0.4;

    /**
     * The stroke's shape: the share of a beat spent in the downstroke (a quicker, harder downstroke and a slower
     * recovery), and how far (radians of phase) the forearm and the finger trail the humerus, so the wing flexes
     * as a wave from the shoulder out instead of hinging rigidly.
     */
    public static final double DOWNSTROKE_SHARE = 0.42, FOREARM_LAG = 0.35, FINGER_LAG = 0.70;
    /** The wing bones, for {@link #boneBeat}. */
    public static final int HUMERUS = 0, FOREARM = 1, FINGER = 2;
    /** The phase (mod 2 pi) at which the humerus is mid-downstroke: stroke angle 0, the beat rising through 0. */
    public static final double MID_DOWNSTROKE = Math.PI * DOWNSTROKE_SHARE - Math.PI / 2;

    /** Her head look (the {@code limbs.head_look} channel): the most it turns from her body, degrees. */
    public static final double HEAD_YAW_LIMIT = 60, HEAD_PITCH_LIMIT = 40;
    /** The name of the head-look channel under an actor's {@code limbs}: {@code rot} keys {@code v: [pitch, yaw, 0]}. */
    public static final String HEAD_LOOK = "head_look";

    /** The dragon's pose: its root (scene-relative), yaw, pitch and roll in degrees, M, path speed and climb rate. */
    public record Pose(double x, double y, double z, float yaw, float pitch, float roll, double[] m, double speed,
                       double climb) {
    }

    // ---- The frame ------------------------------------------------------------------------------------------------

    /** The auto-bank in degrees, right side down positive: BroomFlight's rule with the dragon's gain, limit and fade. */
    public static double bank(Track path, double t) {
        return BroomFlight.bank(path, t, BANK_GAIN, MAX_BANK, BANK_FADE_SPEED);
    }

    /** The climb angle as a pitch (nose down positive), clamped to {@link #MAX_CLIMB}, faded to level when slow. */
    public static double climbPitch(Track path, double t) {
        return BroomFlight.climb(path, t, MAX_CLIMB, CLIMB_FADE_SPEED);
    }

    /** The path speed at t, 3D, blocks per tick over [t - 1, t + 1]. */
    public static double speed(Track path, double t) {
        return BroomFlight.speed(path, t);
    }

    /** The vertical speed at t, up positive, blocks per tick over [t - 1, t + 1]. */
    public static double climbRate(Track path, double t) {
        if (path == null || path.keys().size() < 2) return 0;
        float[] a = path.sample((float) (t - 1)), b = path.sample((float) (t + 1));
        return ((double) b[1] - a[1]) / 2;
    }

    /** The Horntail actor's pose at scene time t (see the class comment). */
    public static Pose place(SceneDef.ActorDef dragon, double t) {
        Track path = dragon.path();
        float[] p = path.isEmpty() ? new float[]{0, 0, 0} : path.sample((float) t);
        float yaw, pitch, roll;
        if (!dragon.look().isEmpty()) {
            float[] lk = dragon.look().sample((float) t);
            yaw = lk.length > 0 ? lk[0] : 0;
            pitch = lk.length > 1 ? lk[1] : 0;
            roll = lk.length > 2 ? lk[2] : (float) bank(path, t);
        } else {
            yaw = BroomFlight.heading(path, t);
            pitch = (float) climbPitch(path, t);
            roll = (float) bank(path, t);
        }
        return new Pose(p[0], p.length > 1 ? p[1] : 0, p.length > 2 ? p[2] : 0, yaw, pitch, roll,
                BroomFlight.frame(yaw, pitch, roll), speed(path, t), climbRate(path, t));
    }

    /** A point in the dragon's local frame (blocks, scale 1, from the root) to scene space at this pose. */
    public static double[] toWorld(Pose pose, double scale, double[] local) {
        double[] rel = {local[0], local[1] - PIVOT_Y, local[2]};
        double[] w = BroomFlight.toWorld(0, 0, 0, pose.m(), rel);
        return new double[]{pose.x() + scale * w[0], pose.y() + scale * (w[1] + PIVOT_Y), pose.z() + scale * w[2]};
    }

    // ---- Wingbeats ------------------------------------------------------------------------------------------------

    /** How much of a glide she is in at this speed and climb, 0 to 1 (smoothstepped on both). */
    public static double glide(double speed, double climb) {
        return smooth((speed - (GLIDE_SPEED - GLIDE_RAMP)) / GLIDE_RAMP)
                * smooth((-climb - (GLIDE_SINK - GLIDE_RAMP)) / GLIDE_RAMP);
    }

    private static double cruise(double speed) {
        return smooth((speed - HOVER_SPEED) / (CRUISE_SPEED - HOVER_SPEED));
    }

    private static double climbing(double climb) {
        return smooth(climb / CLIMB_FULL);
    }

    /**
     * The wingbeat rate in rad/tick: {@link #HOVER_RATE} at a hover, easing to {@link #CRUISE_RATE} at a cruise, up
     * to (1 + {@link #CLIMB_BOOST}) times that when climbing, easing to 0 in a glide. Every blend is a smoothstep, so
     * the rate never steps.
     */
    public static double beatRate(double speed, double climb) {
        double rate = HOVER_RATE + (CRUISE_RATE - HOVER_RATE) * cruise(speed);
        rate *= 1 + CLIMB_BOOST * climbing(climb);
        return rate * (1 - glide(speed, climb));
    }

    /** The wing amplitude: {@link #HOVER_AMP} at a hover or climb, {@link #CRUISE_AMP} at a cruise, {@link #GLIDE_AMP} gliding. */
    public static double wingAmp(double speed, double climb) {
        double amp = HOVER_AMP + (CRUISE_AMP - HOVER_AMP) * cruise(speed);
        amp += (HOVER_AMP - amp) * climbing(climb);
        return amp + (GLIDE_AMP - amp) * glide(speed, climb);
    }

    /** The rate at whole tick k, from the path's speed and climb there. */
    public static double rateAt(Track path, int k) {
        return beatRate(speed(path, k), climbRate(path, k));
    }

    /**
     * The wing phase at t (radians): the sum of {@link #rateAt} over the whole ticks from 0 to floor(t), plus the
     * fraction of the current tick at its rate (before 0, tick 0's rate). Uncached: the client keeps a
     * {@link WingClock} per actor.
     */
    public static double wingPhase(Track path, double t) {
        return new WingClock(path).phase(t);
    }

    /** {@link #wingPhase} with the per-tick sums kept, for one actor. Not thread safe (the render thread owns it). */
    public static final class WingClock {
        private final Track path;
        private double[] sums = new double[64];
        private int known = 1; // sums[0] = 0

        public WingClock(Track path) {
            this.path = path;
        }

        /** The phase at whole tick n (n at least 0). */
        private double at(int n) {
            if (n >= known) {
                if (n >= sums.length) {
                    double[] grown = new double[Math.max(sums.length * 2, n + 1)];
                    System.arraycopy(sums, 0, grown, 0, known);
                    sums = grown;
                }
                for (int k = known; k <= n; k++) sums[k] = sums[k - 1] + rateAt(path, k - 1);
                known = n + 1;
            }
            return sums[n];
        }

        public double phase(double t) {
            if (t <= 0) return t * rateAt(path, 0);
            int n = (int) Math.floor(t);
            return at(n) + (t - n) * rateAt(path, n);
        }
    }

    // ---- The stroke ------------------------------------------------------------------------------------------------

    /**
     * The stroke angle of a wing phase: the phase warped so a beat spends {@link #DOWNSTROKE_SHARE} of its turn going
     * down (sin from -1 to +1) and the rest coming back up. The top (sin -1) stays at phase -pi/2 (mod 2 pi); the
     * angle is piecewise linear in the phase with its breaks at the top and the bottom, where the sine is flat, so the
     * beat {@code sin(strokeAngle)} is smooth (C1).
     */
    public static double strokeAngle(double phase) {
        double tau = 2 * Math.PI, d = DOWNSTROKE_SHARE;
        double shifted = phase + Math.PI / 2;
        double u = shifted - tau * Math.floor(shifted / tau);
        double psi = u < tau * d ? u / (2 * d) : Math.PI + (u - tau * d) / (2 * (1 - d));
        return psi - Math.PI / 2 + (shifted - u);
    }

    /** A wing bone's beat at this phase, -1 (up) to 1 (down): the humerus leads, the forearm and finger trail it. */
    public static double boneBeat(double phase, int bone) {
        double lag = bone == FOREARM ? FOREARM_LAG : bone == FINGER ? FINGER_LAG : 0;
        return Math.sin(strokeAngle(phase - lag));
    }

    /**
     * The ticks in [t0, t1] where the humerus is mid-downstroke ({@link #MID_DOWNSTROKE}: the stroke angle 0, the
     * beat rising fastest through 0 toward the bottom; a positive beat lowers the wings, the model's wing zRot being
     * down for positive values): where the whump of a downstroke peaks. Each is the whole tick nearest the moment.
     */
    public static int[] downstrokes(Track path, int t0, int t1) {
        WingClock clock = new WingClock(path);
        List<Integer> out = new ArrayList<>();
        for (int n = Math.max(0, t0 - 1); n <= t1; n++) {
            double a = clock.phase(n), rate = rateAt(path, n);
            if (rate <= 0) continue;
            double b = a + rate;
            // The first mid-downstroke at or after a: MID_DOWNSTROKE + 2 pi m.
            double m = Math.ceil((a - MID_DOWNSTROKE) / (2 * Math.PI));
            for (double target = MID_DOWNSTROKE + 2 * Math.PI * m; target < b; target += 2 * Math.PI) {
                if (target < a) continue;
                int tick = (int) Math.round(n + (target - a) / rate);
                if (tick >= t0 && tick <= t1 && (out.isEmpty() || out.get(out.size() - 1) != tick)) out.add(tick);
            }
        }
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    /**
     * {@link #downstrokes} where her wings really beat: she is flying and her state lets the beat show
     * ({@link HorntailCues#BEATING}); a dive, a landing flare or a roar holds the wings in its own pose.
     */
    public static int[] downstrokes(Track path, List<SceneDef.Cue> anims, int t0, int t1) {
        int[] all = downstrokes(path, t0, t1);
        int n = 0;
        int[] keep = new int[all.length];
        for (int tick : all) {
            HorntailCues.State s = HorntailCues.at(anims, tick);
            if (s.flying() && HorntailCues.BEATING.contains(s.name())) keep[n++] = tick;
        }
        int[] r = new int[n];
        System.arraycopy(keep, 0, r, 0, n);
        return r;
    }

    // ---- Head and neck ---------------------------------------------------------------------------------------------

    /**
     * Her head look at t from the {@code limbs.head_look} channel's {@code rot} track ({@code v: [pitch, yaw, 0]}, degrees
     * in Minecraft's sense: pitch nose down positive, yaw to her right positive, relative to her body), clamped to
     * {@link #HEAD_PITCH_LIMIT} and {@link #HEAD_YAW_LIMIT}: {@code {yaw, pitch}}, the model's netHeadYaw and headPitch
     * (split along her neck). {0, 0} without one.
     */
    public static float[] headLook(Track rot, double t) {
        if (rot == null || rot.isEmpty()) return new float[]{0f, 0f};
        float[] v = rot.sample((float) t);
        double pitch = v.length > 0 ? v[0] : 0, yaw = v.length > 1 ? v[1] : 0;
        return new float[]{(float) clamp(yaw, HEAD_YAW_LIMIT), (float) clamp(pitch, HEAD_PITCH_LIMIT)};
    }

    /**
     * The neck's counter-roll input (radians, as the model's body zRot): her whole-body roll in degrees (right side
     * down positive), limited to {@link #MAX_BANK}, with the sign flipped because the model's y and z axes point the
     * other way. In level flight the model turns her neck back by 0.55 and 0.35 of it, as the wild dragon does for its
     * own bank, so her head stays near level through a banked turn (dragon_engine.neck_counter_check).
     */
    public static float neckRoll(double rollDegrees) {
        return (float) -Math.toRadians(clamp(rollDegrees, MAX_BANK));
    }

    private static double clamp(double v, double limit) {
        return Math.max(-limit, Math.min(limit, v));
    }

    static double smooth(double x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        return x * x * (3 - 2 * x);
    }
}
