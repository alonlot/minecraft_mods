package com.hogwartsmagic.pensieve.scene;

import java.util.List;
import java.util.Map;

/**
 * The Quidditch balls and the Remembrall as memory actors ({@link SceneNames#BALLS}): their sizes, how they roll,
 * and the pure shapes the renderer draws. Pure: no Minecraft imports; {@code scene_engine.ball_box} mirrors the
 * sizes (checked by MemoryBallTest against {@code ball_cases.json}).
 *
 * <p>A ball's {@code path} is its centre, so a ball lying on the grass has a path y of its radius. It faces nowhere
 * and rolls on its own: {@link Spin} turns it by the distance it travels, about the horizontal axis across its
 * travel, so a thrown Quaffle shows its seams turning and one rolling on the lawn rolls true. The Bludger shakes a
 * little when it is fast ({@link #vibration}) and is dented ({@link #dent}); the Remembrall's smoke swirls on a clock
 * from the actor id ({@link #smoke}).
 */
public final class MemoryBall {
    private MemoryBall() {
    }

    /** Diameters at {@code scale} 1, in blocks. */
    private static final Map<String, Double> DIAMETER = Map.of(
            SceneNames.QUAFFLE_ACTOR, 0.55,
            SceneNames.BLUDGER_ACTOR, 0.45,
            SceneNames.REMEMBRALL_ACTOR, 0.22);

    public static boolean isBall(String type) {
        return type != null && SceneNames.BALLS.contains(type);
    }

    /** The ball's diameter at scale 1 (0 for a type that is no ball). */
    public static double diameter(String type) {
        return DIAMETER.getOrDefault(type, 0.0);
    }

    public static double radius(String type, float scale) {
        return diameter(type) / 2 * scale;
    }

    /** The ball's bounds about its centre, {min x, min y, min z, max x, max y, max z}, at {@code scale}. */
    public static double[] box(String type, float scale) {
        double r = radius(type, scale);
        return new double[]{-r, -r, -r, r, r, r};
    }

    // ---- Rolling ----------------------------------------------------------------------------------------------------

    /**
     * The most a ball may move in one tick (blocks). MemoryActors treats a bigger step as a cut (a teleport: no
     * interpolation and no roll), so a scene keeps its balls and the Snitch under it (SceneValidator checks), unless
     * two path keys under half a tick apart cut on purpose.
     */
    public static final double MAX_STEP = 2.0;

    /**
     * A ball's orientation, a unit quaternion. {@link #roll} turns it by the ground covered: through horizontal
     * distance / radius radians about the horizontal axis across the travel (up x direction, so the top of a ball
     * heading +x goes forward, +x). A vertical move does not turn it.
     */
    public static final class Spin {
        private double w = 1, x, y, z;

        /** Rolls the ball through a move of (dx, dy, dz) blocks: only the horizontal part turns it. */
        public void roll(double dx, double dy, double dz, double radius) {
            double[] step = step(dx, dz, radius);
            if (step != null) rollAbout(step[0], step[1], step[2]);
        }

        /** Turns the ball by {@code angle} radians about the horizontal world axis (ax, 0, az), a unit vector there. */
        public void rollAbout(double ax, double az, double angle) {
            if (angle == 0) return;
            double half = angle / 2, s = Math.sin(half);
            double qw = Math.cos(half), qx = ax * s, qz = az * s;
            // q = dq * q: the turn is about a world axis (dq has no y part).
            double nw = qw * w - qx * x - qz * z;
            double nx = qw * x + qx * w - qz * y;
            double ny = qw * y - qx * z + qz * x;
            double nz = qw * z + qx * y + qz * w;
            double n = Math.sqrt(nw * nw + nx * nx + ny * ny + nz * nz);
            w = nw / n;
            x = nx / n;
            y = ny / n;
            z = nz / n;
        }

        public void set(Spin o) {
            w = o.w;
            x = o.x;
            y = o.y;
            z = o.z;
        }

        /** The turn from rest, in radians (0 to 2 pi). */
        public double angle() {
            return 2 * Math.atan2(Math.sqrt(x * x + y * y + z * z), w);
        }

        /** The quaternion as {w, x, y, z}. */
        public double[] quaternion() {
            return new double[]{w, x, y, z};
        }

        /** A vector turned by the orientation. */
        public double[] turn(double[] v) {
            // v' = v + 2 w (q x v) + 2 q x (q x v)
            double cx = y * v[2] - z * v[1], cy = z * v[0] - x * v[2], cz = x * v[1] - y * v[0];
            return new double[]{
                    v[0] + 2 * (w * cx + y * cz - z * cy),
                    v[1] + 2 * (w * cy + z * cx - x * cz),
                    v[2] + 2 * (w * cz + x * cy - y * cx)};
        }
    }

    /** One tick's roll for a horizontal move (dx, dz): {axis x, axis z, angle}, or null when it does not turn. */
    static double[] step(double dx, double dz, double radius) {
        double h = Math.sqrt(dx * dx + dz * dz);
        if (h <= 0 || radius <= 0) return null;
        // up x direction = (dz, 0, -dx)
        return new double[]{dz / h, -dx / h, h / radius};
    }

    /**
     * A ball's roll as a pure function of its path and the clock, so a late joiner and a replay see the same turn:
     * the orientation after every whole tick is built once from the path (each tick's horizontal move rolls it,
     * except a step over {@link #MAX_STEP}, a cut), and a time between ticks applies that tick's own axis and
     * angle in proportion on top of the tick before, so the ball turns forward at any speed (a shortest-arc blend
     * between two orientations would run backwards past half a turn a tick).
     */
    public static final class Roll {
        private static final int MAX_TICKS = 72_000;
        private final Track path;
        private final int t0;
        private final int n;
        private final double[] q;       // w, x, y, z after each whole tick
        private final double[] axis;    // ax, az, angle of the step that ends at each tick

        public Roll(Track path, double radius) {
            this.path = path;
            List<Keyframe> k = path.keys();
            if (k.isEmpty()) {
                t0 = 0;
                n = 1;
            } else {
                t0 = (int) Math.floor(k.get(0).t());
                n = Math.min(MAX_TICKS, Math.max(1, (int) Math.ceil(k.get(k.size() - 1).t()) - t0 + 2));
            }
            q = new double[n * 4];
            axis = new double[n * 3];
            Spin spin = new Spin();
            double[] prev = pos(t0);
            put(0, spin);
            for (int i = 1; i < n; i++) {
                double[] cur = pos(t0 + i);
                double dx = cur[0] - prev[0], dy = cur[1] - prev[1], dz = cur[2] - prev[2];
                boolean cut = Math.sqrt(dx * dx + dy * dy + dz * dz) > MAX_STEP;
                double[] st = cut ? null : step(dx, dz, radius);
                if (st != null) {
                    spin.rollAbout(st[0], st[1], st[2]);
                    System.arraycopy(st, 0, axis, i * 3, 3);
                }
                put(i, spin);
                prev = cur;
            }
        }

        private double[] pos(double t) {
            if (path.isEmpty()) return new double[]{0, 0, 0};
            float[] p = path.sample((float) t);
            return new double[]{p.length > 0 ? p[0] : 0, p.length > 1 ? p[1] : 0, p.length > 2 ? p[2] : 0};
        }

        private void put(int i, Spin s) {
            double[] v = s.quaternion();
            System.arraycopy(v, 0, q, i * 4, 4);
        }

        /** The orientation at scene time t (ticks, any fraction). */
        public Spin at(double t) {
            Spin s = new Spin();
            int k = (int) Math.floor(t) - t0;
            if (k < 0) return s;
            if (k >= n - 1) {
                load(s, n - 1);
                return s;
            }
            load(s, k);
            double frac = t - Math.floor(t);
            s.rollAbout(axis[(k + 1) * 3], axis[(k + 1) * 3 + 1], axis[(k + 1) * 3 + 2] * frac);
            return s;
        }

        private void load(Spin s, int i) {
            s.w = q[i * 4];
            s.x = q[i * 4 + 1];
            s.y = q[i * 4 + 2];
            s.z = q[i * 4 + 3];
        }

        /** Blocks moved in the tick that ends at t (the path's own step), for the Bludger's tremor. */
        public double speed(double t) {
            double[] a = pos(t - 1), b = pos(t);
            return Math.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]));
        }
    }

    // ---- The Bludger ------------------------------------------------------------------------------------------------

    /** The speed (blocks per tick) a Bludger starts to shake at, the speed it shakes fully at, and the peak offset. */
    public static final double VIBRATE_FROM = 0.5, VIBRATE_FULL = 1.5, VIBRATE_MAX = 0.012;

    /** How much a Bludger shakes at a speed: 0 up to VIBRATE_FROM, smoothly to 1 at VIBRATE_FULL. */
    public static double vibrationGain(double speed) {
        double x = (speed - VIBRATE_FROM) / (VIBRATE_FULL - VIBRATE_FROM);
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        return x * x * (3 - 2 * x);
    }

    /** The shake's offset in blocks (at scale 1) at a speed and an age in ticks: a fast, small, three-axis tremor. */
    public static double[] vibration(double speed, double age) {
        double g = vibrationGain(speed) * VIBRATE_MAX;
        if (g == 0) return new double[]{0, 0, 0};
        return new double[]{g * Math.sin(age * 2.9 + 0.3), g * Math.sin(age * 3.7 + 2.1), g * Math.sin(age * 3.1 + 4.4)};
    }

    private static final double[][] DENTS = {
            // direction (unit), depth (fraction of the radius), tightness
            {0.62, 0.55, 0.56, 0.085, 0.020}, {-0.71, 0.10, 0.69, 0.07, 0.016}, {0.20, -0.85, -0.48, 0.09, 0.022},
            {-0.30, 0.50, -0.81, 0.06, 0.014}, {0.90, -0.30, -0.31, 0.055, 0.012}, {-0.55, -0.35, 0.76, 0.075, 0.018}};

    /**
     * The radius factor of the surface in unit direction (nx, ny, nz): 1 for a round ball, and for the Bludger 1 less
     * a few shallow dents (never below 0.88).
     */
    public static double dent(String type, double nx, double ny, double nz) {
        if (!SceneNames.BLUDGER_ACTOR.equals(type)) return 1.0;
        double f = 1.0;
        for (double[] d : DENTS) {
            double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            double dot = (nx * d[0] + ny * d[1] + nz * d[2]) / len;
            f -= d[3] * Math.exp(-(1 - dot) / d[4]);
        }
        return Math.max(0.88, f);
    }

    // ---- The sphere -------------------------------------------------------------------------------------------------

    /**
     * A unit sphere's vertex at ring i (0 at the top pole, {@code lat} at the bottom) and column j (0 to {@code lon},
     * the last closing on the first): u = j / lon, v = i / lat. A quad (i, j), (i, j+1), (i+1, j+1), (i+1, j) is
     * wound counter-clockwise seen from outside.
     */
    public static double[] unit(int i, int j, int lat, int lon) {
        double phi = Math.PI * i / lat, theta = 2 * Math.PI * (j % lon) / lon;
        double s = Math.sin(phi);
        return new double[]{s * Math.cos(theta), Math.cos(phi), s * Math.sin(theta)};
    }

    // ---- The Remembrall's smoke -------------------------------------------------------------------------------------

    /** How many puffs swirl in the glass, and how far (as a fraction of the radius) a puff's far side may reach. */
    public static final int PUFFS = 6;
    public static final double SMOKE_REACH = 0.9;

    /** A stable clock offset and smoke pattern for an actor id (the same every replay). */
    public static int seedFor(String actorId) {
        return (int) HumanMotion.seed(actorId);
    }

    /**
     * Puff i of the smoke at scene time t (ticks): {x, y, z, size} in units of the ball's radius. Each puff orbits the
     * ball's axis at its own rate and direction, drifting up and down, so the smoke churns without ever leaving the
     * glass.
     */
    public static double[] smoke(int seed, int i, double t) {
        int salt = i * 16;
        double period = 45 + 40 * unit(seed, salt);
        double dir = (i & 1) == 0 ? 1 : -1;
        double phi = dir * t * 2 * Math.PI / period + 2 * Math.PI * unit(seed, salt + 1);
        double rho = 0.20 + 0.22 * unit(seed, salt + 2) + 0.08 * Math.sin(t * 0.045 + 2 * Math.PI * unit(seed, salt + 3));
        double y = 0.30 * Math.sin(t * 0.031 * (1 + unit(seed, salt + 4)) + 2 * Math.PI * unit(seed, salt + 5));
        double size = 0.17 + 0.07 * unit(seed, salt + 6) + 0.02 * Math.sin(t * 0.07 + i);
        return new double[]{rho * Math.cos(phi), y, rho * Math.sin(phi), size};
    }

    /** A stable pseudo-random value in [0, 1) from a seed and a salt. */
    private static double unit(int seed, int salt) {
        int h = seed * 0x9E3779B1 + salt * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) / (double) (1 << 24);
    }
}
