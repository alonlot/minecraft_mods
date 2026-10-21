package com.hogwartsmagic.pensieve.scene;

/**
 * The memory brooms ({@code hogwartsmagic:memory_broom}, the school broom, and {@code hogwartsmagic:memory_broom_racing},
 * the Nimbus) as pure maths: how a broom flies its path, how it carries its riders and how they sit. No Minecraft or
 * client types, so the client, the tests and the Python mirror ({@code scene_engine.py}: {@code broom_frame},
 * {@code rider_root}, {@code rider_pose}, kept in step by {@code broom_cases.json}) use the same numbers.
 *
 * <p><b>Frame.</b> Broom-local x is the broom's left, y up, z toward the handle's tip. The origin is the seat point on
 * the handle's axis, so the {@code path} is where a rider's hips sit (a rider's thighs rest {@link #SEAT_TOP} above
 * it). World = root + M * local, M = Yaw * Pitch * Roll ({@link #frame}): yaw is the Minecraft yaw (0 faces +Z, -90
 * faces +X), pitch is nose down positive (as for heads), roll is the bank, right side down positive.
 *
 * <p><b>Flying its path.</b> With a {@code look}, the broom takes its yaw and pitch from it, and its roll too when any
 * look key gives one; otherwise ({@link #place}) the yaw follows the heading ({@link #heading}, keeping the last one
 * while it holds still or moves straight up), the pitch the climb angle ({@link #climb}, clamped to
 * {@link #MAX_CLIMB}, faded out below {@link #CLIMB_FADE_SPEED} so a hover is level) and the roll is the auto-bank
 * ({@link #bank}). While it carries a rider it bobs ({@link #hoverBob}), only when slow, eased in and out with the
 * rider layer's weight.
 *
 * <p><b>Riders.</b> An actor riding a broom ({@link Vehicle}) takes the broom's whole yaw x pitch x roll about the seat;
 * its rest offset is {@link #seat}. A player rider gets {@link #riderPose} as its base layer (below poses, clips and
 * limbs), eased in over {@link #RIDER_BLEND} ticks from the start of a ride window and out before its end
 * ({@link #riderWeight}), and never walks. A ridden broom is flying above {@link Airborne#FLY_SPEED} and landed
 * under {@link Airborne#LAND_SPEED} ({@link #flying}, the owls' rule): a flying rider gets the seated life layer, a
 * landed one the standing one with no gait.
 */
public final class BroomFlight {
    private BroomFlight() {
    }

    /** Riders a broom carries at once (the validator checks it). */
    public static final int MAX_RIDERS = 2;
    /** The seat top above the handle axis, and how far behind the seat point the rider's hips sit (blocks, scale 1). */
    public static final double SEAT_TOP = 0.06, SEAT_BACK = 0.05;
    /** Auto-bank: degrees per block/tick^2 of sideways acceleration, the most it banks, the sampling window (ticks). */
    public static final double BANK_GAIN = 1000, MAX_BANK = 45, BANK_WINDOW = 3;
    /** Below this speed (blocks per tick) the bank fades out, so a broom turning on the spot does not flip. */
    public static final double BANK_FADE_SPEED = 0.05;
    /** The climb angle's clamp (degrees) and the speed (blocks per tick) below which it fades to level. */
    public static final double MAX_CLIMB = 70, CLIMB_FADE_SPEED = 0.05;
    /** The ridden hover bob: amplitude (blocks), frequency (Hz) and the speed (blocks per tick) by which it is gone. */
    public static final double BOB_AMPLITUDE = 0.04, BOB_HZ = 0.9, BOB_FADE_SPEED = 0.1;
    /** The rider's forward lean: at rest, the extra at full speed, the speed (blocks per tick) of full speed. */
    public static final double LEAN_REST = 8, LEAN_SPEED = 22, LEAN_FULL_SPEED = 0.6;
    /** The dive tuck: the body lean it reaches, smoothstepped in between these broom pitches (degrees). */
    public static final double TUCK_LEAN = 40, TUCK_FROM = 20, TUCK_TO = 40;
    /**
     * Legs astride the handle (splayed out, so the handle shows between the knees) and arms down and in to grip it
     * (degrees: x, and the y of the right side; the left mirrors it).
     */
    public static final float LEG_X = -70, LEG_Y = 10, ARM_X = -50, ARM_Y = -25;
    /** The hunched grip: the shoulders (arm pivots) lowered and brought forward in the torso's frame, model px. */
    public static final float SHOULDER_DROP = 3.5f, SHOULDER_FORWARD = 2f;
    /** The most a rider's look turns his head off the broom (degrees); his body always faces along it. */
    public static final float HEAD_TURN = 75;
    /** How far back (ticks) the flight rule is replayed to decide, with no history, whether a broom flies now. */
    public static final int FLIGHT_REPLAY = 50;
    /** Ticks over which a rider's life layer goes from standing (landed) to seated (flying). */
    public static final int SEAT_EASE = 10;
    /** Ticks over which the rider layer eases in after a ride window opens and out before it closes. */
    public static final double RIDER_BLEND = 10;

    /**
     * The broom models' bounds in the broom frame at scale 1, {min x, min y, min z, max x, max y, max z} (blocks),
     * from BroomModelV2's training and Nimbus layers: the brush hangs below the handle at the back (-z).
     */
    public static final double[] BOX_SCHOOL = {-0.156, -0.381, -1.969, 0.156, 0.166, 1.678};
    public static final double[] BOX_RACING = {-0.344, -0.508, -1.744, 0.344, 0.368, 1.217};

    /** A broom's pose: its root (scene-relative, the bob included), yaw, pitch and roll in degrees, M, its path speed. */
    public record Pose(double x, double y, double z, float yaw, float pitch, float roll, double[] m, double speed) {
    }

    public static boolean isBroom(String type) {
        return SceneNames.BROOM_ACTOR.equals(type) || SceneNames.RACING_BROOM_ACTOR.equals(type);
    }

    public static double[] box(String type) {
        return SceneNames.RACING_BROOM_ACTOR.equals(type) ? BOX_RACING : BOX_SCHOOL;
    }

    /** The rider's path offset at rest on a broom of scale 1: hips over the seat point, thighs on the seat top. */
    public static double[] seat(double riderScale) {
        return new double[]{0, HumanMotion.seatedRootY(SEAT_TOP, riderScale), -SEAT_BACK};
    }

    // ---- The frame ------------------------------------------------------------------------------------------------

    /** M = Yaw * Pitch * Roll, row-major (see the class comment for the conventions). */
    public static double[] frame(double yawDeg, double pitchDeg, double rollDeg) {
        double y = Math.toRadians(yawDeg), p = Math.toRadians(-pitchDeg), r = Math.toRadians(rollDeg);
        double cy = Math.cos(y), sy = Math.sin(y), cp = Math.cos(p), sp = Math.sin(p), cr = Math.cos(r), sr = Math.sin(r);
        double[] yaw = {cy, 0, -sy, 0, 1, 0, sy, 0, cy};
        double[] pitch = {1, 0, 0, 0, cp, sp, 0, -sp, cp};
        double[] roll = {cr, -sr, 0, sr, cr, 0, 0, 0, 1};
        return mul(mul(yaw, pitch), roll);
    }

    static double[] mul(double[] a, double[] b) {
        double[] o = new double[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
            }
        }
        return o;
    }

    /** root + M * local. */
    public static double[] toWorld(double x, double y, double z, double[] m, double[] local) {
        return new double[]{
                x + m[0] * local[0] + m[1] * local[1] + m[2] * local[2],
                y + m[3] * local[0] + m[4] * local[1] + m[5] * local[2],
                z + m[6] * local[0] + m[7] * local[1] + m[8] * local[2]};
    }

    /** The lowest world y of a broom's model box at this pose (scale multiplies the box). */
    public static double lowest(Pose p, String type, double scale) {
        double[] b = box(type);
        double low = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            double[] c = {(i & 1) == 0 ? b[0] : b[3], (i & 2) == 0 ? b[1] : b[4], (i & 4) == 0 ? b[2] : b[5]};
            double y = p.y() + (p.m()[3] * c[0] + p.m()[4] * c[1] + p.m()[5] * c[2]) * scale;
            low = Math.min(low, y);
        }
        return low;
    }

    // ---- Flight from the path ---------------------------------------------------------------------------------------

    private static float[] at(Track path, double t) {
        return path.sample((float) t);
    }

    /** The path speed in blocks per tick, 3D, over [t - 1, t + 1] (as {@link Airborne#speed}). */
    public static double speed(Track path, double t) {
        return Airborne.speed(path, (float) t);
    }

    /** Whether a ridden broom flies at t, given whether it flew on the previous tick (the owls' rule, hysteresis). */
    public static boolean flying(Track path, float t, boolean wasFlying) {
        return Airborne.airborne(path, t, wasFlying);
    }

    /**
     * The auto-bank in degrees (right side down positive): {@link #BANK_GAIN} times the sideways acceleration, the
     * velocity and acceleration taken from the path at t and {@link #BANK_WINDOW} ticks either side, softly limited to
     * {@link #MAX_BANK} by a tanh. Equivalently -k x (yaw rate x speed) with the yaw rate counter-clockwise seen from
     * above: a broom turning right (Minecraft yaw increasing) banks right side down. Continuous: below
     * {@link #BANK_FADE_SPEED} the sideways acceleration is scaled down with the speed, to 0 at rest.
     */
    public static double bank(Track path, double t) {
        if (path == null || path.keys().size() < 2) return 0;
        float[] a = at(path, t - BANK_WINDOW), b = at(path, t), c = at(path, t + BANK_WINDOW);
        double vx = ((double) c[0] - a[0]) / (2 * BANK_WINDOW), vz = ((double) c[2] - a[2]) / (2 * BANK_WINDOW);
        double ax = ((double) c[0] - 2.0 * b[0] + a[0]) / (BANK_WINDOW * BANK_WINDOW);
        double az = ((double) c[2] - 2.0 * b[2] + a[2]) / (BANK_WINDOW * BANK_WINDOW);
        double v = Math.sqrt(vx * vx + vz * vz);
        double cross = vx * az - vz * ax;
        double side = v >= BANK_FADE_SPEED ? cross / v : cross / BANK_FADE_SPEED;
        return MAX_BANK * Math.tanh(BANK_GAIN * side / MAX_BANK);
    }

    /**
     * The climb angle as a pitch (nose down positive) from the velocity over [t - 1, t + 1], clamped to
     * {@link #MAX_CLIMB} and smoothstepped to level below {@link #CLIMB_FADE_SPEED}.
     */
    public static double climb(Track path, double t) {
        if (path == null || path.keys().size() < 2) return 0;
        float[] a = at(path, t - 1), b = at(path, t + 1);
        double vx = (b[0] - (double) a[0]) / 2, vy = (b[1] - (double) a[1]) / 2, vz = (b[2] - (double) a[2]) / 2;
        double h = Math.sqrt(vx * vx + vz * vz);
        double s = Math.sqrt(h * h + vy * vy);
        if (s == 0) return 0;
        double angle = -Math.toDegrees(Math.atan2(vy, h));
        return Math.max(-MAX_CLIMB, Math.min(MAX_CLIMB, angle)) * smooth(s / CLIMB_FADE_SPEED);
    }

    /** The way the path heads (Minecraft yaw), keeping the last (or first) heading while it holds or moves straight up. */
    public static float heading(Track path, double t) {
        if (path == null || path.isEmpty()) return 0f;
        float[] a = at(path, t - 2), b = at(path, t + 2);
        double dx = (double) b[0] - a[0], dz = (double) b[2] - a[2];
        if (dx * dx + dz * dz < 1e-6) {
            for (double dt = 10; dt <= 400; dt += 10) {
                float[] c = at(path, t - dt), d = at(path, t + dt);
                dx = (double) d[0] - c[0];
                dz = (double) d[2] - c[2];
                if (dx * dx + dz * dz > 1e-6) break;
            }
            if (dx * dx + dz * dz < 1e-6) return 0f;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * The ridden hover bob (blocks, added to the broom's height): {@link #BOB_AMPLITUDE} at {@link #BOB_HZ}, its phase
     * from {@code seed}, fading out smoothly to 0 at {@link #BOB_FADE_SPEED}.
     */
    public static double hoverBob(double speed, double t, long seed) {
        double fade = 1 - smooth(speed / BOB_FADE_SPEED);
        if (fade <= 0) return 0;
        double phase = (seed >>> 11) * 0x1.0p-53;
        return BOB_AMPLITUDE * fade * Math.sin(2 * Math.PI * (BOB_HZ * t / 20.0 + phase));
    }

    /**
     * How much broom {@code id} is ridden at t: the largest {@link #riderWeight} of the actors riding it then (0 with
     * none), so the bob eases in as a rider mounts instead of jumping.
     */
    public static float ridden(SceneDef scene, String id, float t) {
        float w = 0;
        for (SceneDef.ActorDef a : scene.actors()) {
            if (id.equals(a.ride()) && Vehicle.ridesAt(a, t)) w = Math.max(w, riderWeight(a.rideFrom(), a.rideUntil(), t));
        }
        return w;
    }

    /** The broom actor's pose at scene time t (see the class comment). */
    public static Pose place(SceneDef scene, SceneDef.ActorDef broom, float t) {
        Track path = broom.path();
        float[] p = path.isEmpty() ? new float[]{0, 0, 0} : path.sample(t);
        double speed = speed(path, t);
        float yaw, pitch, roll;
        if (!broom.look().isEmpty()) {
            float[] lk = broom.look().sample(t);
            yaw = lk.length > 0 ? lk[0] : 0;
            pitch = lk.length > 1 ? lk[1] : 0;
            roll = lk.length > 2 ? lk[2] : (float) bank(path, t);
        } else {
            yaw = heading(path, t);
            pitch = (float) climb(path, t);
            roll = (float) bank(path, t);
        }
        double y = p[1];
        float w = scene == null ? 0 : ridden(scene, broom.id(), t);
        if (w > 0) y += hoverBob(speed, t, HumanMotion.seed(broom.id())) * w;
        return new Pose(p[0], y, p[2], yaw, pitch, roll, frame(yaw, pitch, roll), speed);
    }

    // ---- The rider ------------------------------------------------------------------------------------------------

    /** The rider's forward body lean (degrees): with speed, and the dive tuck smoothstepped in from pitch 20 to 40. */
    public static double lean(double speed, double pitch) {
        double base = LEAN_REST + LEAN_SPEED * Math.max(0, Math.min(1, speed / LEAN_FULL_SPEED));
        return base + (TUCK_LEAN - base) * smooth((pitch - TUCK_FROM) / (TUCK_TO - TUCK_FROM));
    }

    /**
     * The base rider layer: rotations in degrees per part in {@link HumanMotion} order (head, body, right arm, left
     * arm, right leg, left leg). The legs astride the handle {@code [-70, +-10, 0]} (splayed out), the arms down and in
     * gripping it {@code [-50, -+25, 0]} (with the hunched shoulders of {@link #riderLayer} the fists close on the
     * handle at every rider scale, speed and through the dive tuck: {@code BroomFlightTest.handsOnHandle}), the body
     * leaning forward {@link #lean}, the head lifted back by the same so the gaze holds (the look pitch adds to it).
     */
    public static float[][] riderPose(double speed, double pitch) {
        float l = (float) lean(speed, pitch);
        return new float[][]{
                {-l, 0, 0},
                {l, 0, 0},
                {ARM_X, ARM_Y, 0},
                {ARM_X, -ARM_Y, 0},
                {LEG_X, LEG_Y, 0},
                {LEG_X, -LEG_Y, 0}};
    }

    /** How much of the rider layer is in force at t in a window [from, until): eased in and out over RIDER_BLEND. */
    public static float riderWeight(float from, float until, float t) {
        double in = Float.isInfinite(from) ? 1 : smooth((t - from) / RIDER_BLEND);
        double out = Float.isInfinite(until) ? 1 : smooth((until - t) / RIDER_BLEND);
        return (float) (in * out);
    }

    /**
     * Applies the rider layer at weight {@code w} to a model's six parts ({@code rot} degrees and {@code pos} px, three
     * each per part in HumanMotion order, as the model's own setupAnim left them): the body, arms and legs are blended
     * toward {@link #riderPose} (absolute), the head gets its lift added, and the lean turns the torso about the hips
     * (the neck and shoulders move with it, as {@code HumanMotion.hipTurn}).
     */
    public static void riderLayer(float[] rot, float[] pos, double speed, double pitch, float w) {
        if (w <= 0) return;
        float[][] pose = riderPose(speed, pitch);
        float lean = pose[1][0];
        rot[HumanMotion.HEAD * 3] += pose[0][0] * w;
        for (int p = HumanMotion.BODY; p < HumanMotion.PARTS; p++) {
            float[] target = p == HumanMotion.BODY ? new float[]{0, 0, 0} : pose[p];
            for (int c = 0; c < 3; c++) rot[p * 3 + c] += (target[c] - rot[p * 3 + c]) * w;
        }
        HumanMotion.hipTurn(rot, pos, lean, 0, 0, w);
        // The hunched grip: each shoulder drops and comes forward in the leaned torso's frame.
        double a = Math.toRadians(lean);
        float dy = (float) (SHOULDER_DROP * Math.cos(a) + SHOULDER_FORWARD * Math.sin(a));
        float dz = (float) (SHOULDER_DROP * Math.sin(a) - SHOULDER_FORWARD * Math.cos(a));
        for (int p = HumanMotion.RIGHT_ARM; p <= HumanMotion.LEFT_ARM; p++) {
            pos[p * 3 + 1] += dy * w;
            pos[p * 3 + 2] += dz * w;
        }
    }

    /**
     * A broom rider's head turn (degrees, relative to the broom): his body faces along the broom and his look yaw
     * (relative to it) turns only his head, wrapped to [-180, 180) and clamped to {@link #HEAD_TURN}.
     */
    public static float headTurn(float lookYaw) {
        float d = (float) (((lookYaw + 180.0) % 360.0 + 360.0) % 360.0 - 180.0);
        return Math.max(-HEAD_TURN, Math.min(HEAD_TURN, d));
    }

    /**
     * How seated a rider of a broom flying {@code path} is at t, 0 (landed: standing life) to 1 (flying: seated
     * life), from nothing but the scene: the flight rule ({@link #flying}, with its hysteresis) replayed tick by tick
     * from {@link #FLIGHT_REPLAY} ticks back (or from {@code since}, the ride window's start, not flying before it),
     * and the share of the last {@link #SEAT_EASE} + 1 ticks it flew. So a late joiner sees what a viewer who watched
     * from the start sees, and the life layer eases over SEAT_EASE ticks instead of switching.
     */
    public static float seatFraction(Track path, float t, float since) {
        boolean fly = false;
        int flown = 0;
        for (int k = FLIGHT_REPLAY; k >= 0; k--) {
            float s = t - k;
            fly = s >= since && flying(path, s, fly);
            if (k <= SEAT_EASE && fly) flown++;
        }
        return flown / (float) (SEAT_EASE + 1);
    }

    private static double smooth(double x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        return x * x * (3 - 2 * x);
    }
}
