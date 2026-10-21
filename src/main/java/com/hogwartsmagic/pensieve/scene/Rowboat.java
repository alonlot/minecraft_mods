package com.hogwartsmagic.pensieve.scene;

import java.util.List;

/**
 * The {@code hogwartsmagic:memory_rowboat} actor as pure maths: its hull shape (shared with the client renderer), how it
 * rides a {@link RoughSea}, where its riders sit, and how its oars stroke. Pure: no Minecraft or client types, so the
 * client and the tests use exactly the same numbers.
 *
 * <p><b>Frame.</b> Boat-local x is the boat's left (port) side at yaw 0, y is up, z is forward (the bow). The origin is
 * the waterline at the middle of the hull, so the {@code path} position is a point on the still water. At scale 1 the
 * hull is 4 blocks long (z -2 stern to +2 bow) and 2 wide at the gunwale; {@code scale} multiplies everything.
 * World = root + M * local, with M = Yaw * Pitch * Roll ({@link #matrix}): yaw is the Minecraft yaw (0 faces +Z,
 * -90 faces +X), pitch lifts the bow (positive = bow up) about local x, roll lifts the left side (positive) about local z.
 *
 * <p><b>Riding the sea.</b> At scene time t the boat takes the {@code rough_sea} event running then whose area contains
 * the boat's path position, the one with the nearest centre if several do ({@link RoughSea#bind}); with none it sits
 * level on its path. It samples the sea under a 3 x 5 grid over its footprint and fits a plane: the mean is the heave,
 * the slopes give pitch and roll. The waterline then stays within 0.15 blocks of the drawn waves at intensity 1
 * ({@code RowboatTest.boatRidesTheSurface}); above 1 the shorter swells grow faster than a 4-block hull can follow.
 *
 * <p><b>Riders.</b> An actor with {@code "ride": "<boat id>"} has its {@code path} read as an offset in the boat frame
 * (blocks, at the boat's own scale: the seat offsets below are for scale 1) and its look yaw relative to the boat's
 * yaw, so it heaves, pitches and rolls with the boat ({@link #rider}).
 */
public final class Rowboat {
    private Rowboat() {
    }

    // ---- Hull geometry (scale 1) ----------------------------------------------------------------------------------

    /** Half the hull length (stern at -HALF_LENGTH, bow at +HALF_LENGTH) and the largest half-beam at the gunwale. */
    public static final double HALF_LENGTH = 2.0, HALF_BEAM = 1.0;
    /** The top of the thwarts (seats) and of the bottom boards, boat-local y. */
    public static final double SEAT_TOP = 0.12, FLOOR = -0.20;
    /** The cross-section from keel (0, 0) to gunwale (1, 1): fractions of the half-beam and of keel-to-gunwale height. */
    public static final double[][] SECTION = {{0, 0}, {0.40, 0.03}, {0.74, 0.18}, {0.93, 0.50}, {1, 1}};
    /** The hull skin thickness. */
    public static final double PLANK = 0.04;

    /** Half-beam at the gunwale at u = z / HALF_LENGTH (-1 stern to 1 bow): fine at the bow, a wide transom aft. */
    public static double halfBeam(double u) {
        if (u >= 0) return Math.max(0.03, HALF_BEAM * Math.pow(Math.max(0, 1 - u * u), 0.55));
        return HALF_BEAM * (1 - 0.32 * u * u);
    }

    /** The gunwale height (the sheer), rising toward both ends, most at the bow. */
    public static double sheer(double u) {
        double b = Math.max(0, u);
        return 0.48 + 0.08 * u * u + 0.10 * b * b * b;
    }

    /** The keel's underside, rising into the bow's forefoot and a little at the stern. */
    public static double keel(double u) {
        double b = Math.max(0, u);
        return -0.30 + 0.16 * b * b * b + (u < 0 ? 0.05 * u * u : 0);
    }

    /** The outer skin point of section fraction i at u, as {x, y} (x >= 0, the left side). */
    public static double[] sectionPoint(double u, int i) {
        double hb = halfBeam(u), k = keel(u), g = sheer(u);
        return new double[]{SECTION[i][0] * hb, k + SECTION[i][1] * (g - k)};
    }

    /** The hull's outer half-width at height y at u (0 below the keel, the gunwale half-beam above it). */
    public static double halfWidthAt(double u, double y) {
        double[] prev = sectionPoint(u, 0);
        if (y <= prev[1]) return 0;
        for (int i = 1; i < SECTION.length; i++) {
            double[] p = sectionPoint(u, i);
            if (y <= p[1]) return prev[0] + (p[0] - prev[0]) * (y - prev[1]) / (p[1] - prev[1]);
            prev = p;
        }
        return prev[0];
    }

    /** Points on the hull's waterline (y = 0) along both sides and at the stern corners and bow, scale 1. */
    public static final double[][] WATERLINE = waterline();

    private static double[][] waterline() {
        double[] zs = {-1.95, -1.3, -0.6, 0, 0.6, 1.3, 1.9};
        double[][] out = new double[zs.length * 2][];
        for (int i = 0; i < zs.length; i++) {
            double w = halfWidthAt(zs[i] / HALF_LENGTH, 0);
            out[2 * i] = new double[]{w, 0, zs[i]};
            out[2 * i + 1] = new double[]{-w, 0, zs[i]};
        }
        return out;
    }

    /** Where the boat samples the sea, scale 1 (boat-local x, z). */
    private static final double[] FIT_X = {-0.8, 0, 0.8}, FIT_Z = {-1.8, -0.9, 0, 0.9, 1.8};

    // ---- Seats ----------------------------------------------------------------------------------------------------

    /** A seat: boat-local x and z of the seated hip at scale 1, and the yaw a rider there faces, relative to the boat. */
    public record SeatSpot(String name, double x, double z, float yaw) {
        /** The rider's path offset for a rider of this scale on a boat of scale 1: the seated root, see HumanMotion. */
        public double[] offset(double riderScale) {
            return new double[]{x, HumanMotion.seatedRootY(SEAT_TOP, riderScale), z};
        }
    }

    /**
     * The four seats: {@code oars} (the rower on the middle thwart, facing the stern), {@code stern_left} and
     * {@code stern_right} (side by side on the stern thwart, facing forward) and {@code bow} (facing aft, toward the
     * others; turn the rider's look yaw to 0 to face ahead).
     */
    public static final List<SeatSpot> SEATS = List.of(
            new SeatSpot("oars", 0, 0.15, 180f),
            new SeatSpot("stern_left", 0.42, -1.38, 0f),
            new SeatSpot("stern_right", -0.38, -1.38, 0f),
            new SeatSpot("bow", 0, 1.3, 180f));
    /** The thwarts' middle z and depth along the boat. */
    public static final double[] THWART_Z = {-1.35, 0.15, 1.3};
    public static final double THWART_DEPTH = 0.30, THWART_THICK = 0.05;

    // ---- Pose -----------------------------------------------------------------------------------------------------

    /**
     * Where the boat is: its root (the waterline at the hull's middle, scene-relative), yaw, pitch and roll in degrees,
     * and M (row-major, world from boat-local, scale not included).
     */
    public record Pose(double x, double y, double z, float yaw, float pitch, float roll, double[] m) {
    }

    /** M = Yaw * Pitch * Roll, row-major (see the class comment for the conventions). */
    public static double[] matrix(float yawDeg, float pitchDeg, float rollDeg) {
        double y = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg), r = Math.toRadians(rollDeg);
        double cy = Math.cos(y), sy = Math.sin(y), cp = Math.cos(p), sp = Math.sin(p), cr = Math.cos(r), sr = Math.sin(r);
        // Yaw columns: x -> (cy, 0, sy), y -> (0, 1, 0), z -> (-sy, 0, cy).
        double[] yaw = {cy, 0, -sy, 0, 1, 0, sy, 0, cy};
        // Pitch about x, bow up: y -> (0, cp, -sp), z -> (0, sp, cp).
        double[] pitch = {1, 0, 0, 0, cp, sp, 0, -sp, cp};
        // Roll about z, left side up: x -> (cr, sr, 0), y -> (-sr, cr, 0).
        double[] roll = {cr, -sr, 0, sr, cr, 0, 0, 0, 1};
        return mul(mul(yaw, pitch), roll);
    }

    private static double[] mul(double[] a, double[] b) {
        double[] o = new double[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
            }
        }
        return o;
    }

    /** root + M * local (local already scaled). */
    public static double[] toWorld(Pose p, double[] local) {
        double[] m = p.m;
        return new double[]{
                p.x + m[0] * local[0] + m[1] * local[1] + m[2] * local[2],
                p.y + m[3] * local[0] + m[4] * local[1] + m[5] * local[2],
                p.z + m[6] * local[0] + m[7] * local[1] + m[8] * local[2]};
    }

    /**
     * The boat on {@code sea} (null: still water) with its path at the scene-relative (x, y, z), facing {@code yaw}, at
     * scene time t: the heave is added to y, pitch and roll fitted to the waves under the hull.
     */
    public static Pose ride(RoughSea.Sea sea, double x, double y, double z, float yaw, float scale, float t) {
        if (sea == null || sea.intensityAt(t) <= 0) return new Pose(x, y, z, yaw, 0f, 0f, matrix(yaw, 0f, 0f));
        double ry = Math.toRadians(yaw), cy = Math.cos(ry), sy = Math.sin(ry);
        double sum = 0, sx = 0, sz = 0, xx = 0, zz = 0;
        int n = 0;
        for (double fx : FIT_X) {
            for (double fz : FIT_Z) {
                double lx = fx * scale, lz = fz * scale;
                double wx = x + cy * lx - sy * lz, wz = z + sy * lx + cy * lz;
                double h = sea.surface(wx, wz, t) - sea.cy();
                sum += h;
                sx += h * lx;
                sz += h * lz;
                xx += lx * lx;
                zz += lz * lz;
                n++;
            }
        }
        double h0 = sum / n, a = sx / xx, b = sz / zz;
        double pitch = Math.atan(b);
        double roll = Math.atan(a / Math.cos(pitch));
        float pd = (float) Math.toDegrees(pitch), rd = (float) Math.toDegrees(roll);
        return new Pose(x, y + h0, z, yaw, pd, rd, matrix(yaw, pd, rd));
    }

    /** The boat actor's pose at scene time t: its path, its yaw (look, else its heading), on the sea that binds it. */
    public static Pose place(SceneDef scene, SceneDef.ActorDef boat, float t) {
        float[] p = boat.path().isEmpty() ? new float[]{0, 0, 0} : boat.path().sample(t);
        float yaw = yaw(boat, t);
        RoughSea.Sea sea = RoughSea.bind(scene.events(), t, p[0], p[2]);
        return ride(sea, p[0], p[1], p[2], yaw, boat.scale(), t);
    }

    /** The boat's yaw: its look yaw when it has a look, else the way its path heads (0 when it never moves). */
    public static float yaw(SceneDef.ActorDef boat, float t) {
        if (!boat.look().isEmpty()) return boat.look().sample(t)[0];
        if (boat.path().isEmpty()) return 0f;
        float[] a = boat.path().sample(t - 2), b = boat.path().sample(t + 2);
        double dx = b[0] - a[0], dz = b[2] - a[2];
        if (dx * dx + dz * dz < 1e-8) {
            // Holding still: keep the heading it last had (or will first have).
            for (float dt = 10; dt <= 400; dt += 10) {
                float[] c = boat.path().sample(t - dt), d = boat.path().sample(t + dt);
                dx = d[0] - c[0];
                dz = d[2] - c[2];
                if (dx * dx + dz * dz > 1e-8) break;
            }
            if (dx * dx + dz * dz < 1e-8) return 0f;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** A seated rider: its root (scene-relative), world yaw, the boat's pitch, roll and yaw, and the boat's M. */
    public record Seat(double x, double y, double z, float yaw, float pitch, float roll, float boatYaw, double[] m) {
    }

    /**
     * Where a rider ({@code ride} set) sits in its boat at scene time t, or null when it does not ride a boat then (out
     * of its window, or its boat is not in the scene): {@link Vehicle#seat}, the path MemoryActors runs.
     */
    public static Seat rider(SceneDef scene, SceneDef.ActorDef rider, float t) {
        Vehicle.Seat v = Vehicle.seat(scene, rider, t);
        if (v == null || v.broom()) return null;
        return new Seat(v.x(), v.y(), v.z(), v.yaw(), v.pitch(), v.roll(), v.vehicleYaw(), v.m());
    }

    // ---- Oars -----------------------------------------------------------------------------------------------------

    /** One stroke, catch to catch, in ticks (2.6 s: a heavy man in a heavy sea). */
    public static final float OAR_PERIOD = 52f;
    /** The oarlocks: boat-local x (each side), height above the gunwale, and z (just aft of the rower). */
    public static final double OARLOCK_Z = -0.2, OARLOCK_LIFT = 0.07;
    /** The oar: inboard length (oarlock to handle end) and outboard length (oarlock to blade tip). */
    public static final double OAR_INBOARD = 0.75, OAR_OUTBOARD = 1.85;
    /** Path speed (blocks per tick) at which the oars pull at full stroke; they rest at 0. */
    public static final float FULL_STROKE_SPEED = 0.04f;

    /**
     * An oar's angles, degrees: {@code sweep} about the vertical through the oarlock (0 straight out, positive swings
     * the blade toward the bow), {@code drop} how far the outboard shaft dips below level (the blade is in the water
     * near 20), {@code feather} the blade's turn about the shaft (0 square to the water, 80 flat).
     */
    public record Oar(float sweep, float drop, float feather) {
    }

    /** How hard the oars pull at scene time t, 0 (resting) to 1, from the boat's path speed over the 10 ticks round t. */
    public static float strokeAmount(SceneDef.ActorDef boat, float t) {
        if (boat.path().isEmpty()) return 0f;
        float[] a = boat.path().sample(t - 5), b = boat.path().sample(t + 5);
        double dx = b[0] - a[0], dz = b[2] - a[2];
        double speed = Math.sqrt(dx * dx + dz * dz) / 10.0;
        return (float) Math.max(0, Math.min(1, speed / FULL_STROKE_SPEED));
    }

    /** The stroke phase at scene time t, 0 (the catch, blades going in) to 1. */
    public static float phase(float t) {
        float s = t / OAR_PERIOD;
        return s - (float) Math.floor(s);
    }

    /**
     * Both oars at stroke phase s (they move together, mirrored) at stroke amount {@code amount}. The stroke: the catch at
     * s = 0 (sweep 40, blade toward the bow), the drive through the water to the finish at s = 0.5 (sweep -22), then
     * the recovery with the blade lifted (drop 20 down to 9) and feathered flat. Resting (amount 0): trailed aft,
     * sweep -62, drop 8, flat.
     */
    public static Oar oar(float s, float amount) {
        double th = 2 * Math.PI * s;
        double sweep = 9 + 31 * Math.cos(th);
        double lift = Math.max(0, -Math.sin(th));
        double drop = 20 - 11 * lift * lift;
        double feather = 80 * Math.min(smooth((s - 0.5) / 0.08), smooth((1.0 - s) / 0.08));
        float k = Math.max(0f, Math.min(1f, amount));
        return new Oar((float) (-62 + (sweep + 62) * k), (float) (8 + (drop - 8) * k), (float) (80 + (feather - 80) * k));
    }

    private static double smooth(double u) {
        if (u <= 0) return 0;
        if (u >= 1) return 1;
        return u * u * (3 - 2 * u);
    }

    /**
     * The left oar's blade centre in boat-local coordinates (scale 1) for an oar pose; the right oar mirrors x. For the
     * catch splash and for a generator keying the rower's hands (the handle is the same line through the oarlock).
     */
    public static double[] blade(Oar o) {
        return alongOar(o, OAR_OUTBOARD - 0.25);
    }

    /** The left oar's handle end, boat-local, scale 1 (the rower's left hand holds it). */
    public static double[] handle(Oar o) {
        return alongOar(o, -OAR_INBOARD);
    }

    /** The point {@code d} blocks out along the left oar from its oarlock (negative: inboard). */
    public static double[] alongOar(Oar o, double d) {
        double u = OARLOCK_Z / HALF_LENGTH;
        double lx = halfBeam(u), ly = sheer(u) + OARLOCK_LIFT;
        double sw = Math.toRadians(o.sweep()), dr = Math.toRadians(o.drop());
        double horiz = Math.cos(dr) * d;
        return new double[]{lx + horiz * Math.cos(sw), ly - Math.sin(dr) * d, OARLOCK_Z + horiz * Math.sin(sw)};
    }
}
