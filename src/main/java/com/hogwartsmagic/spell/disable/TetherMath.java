package com.hogwartsmagic.spell.disable;

/**
 * Leash arithmetic, Minecraft-free. {@code d} is always the target position minus the anchor. Inside
 * the leash nothing changes; outside it, motion away from the anchor is cancelled and a pull back
 * proportional to the overshoot is added (capped). Far outside, the caller teleports the target to
 * {@link #snapPoint}, so lag or a big knockback can never carry it away.
 */
public final class TetherMath {
    public static final double PULL_GAIN = 0.4;
    public static final double MAX_PULL = 0.6;
    public static final double SNAP_SLACK = 2.0;

    private TetherMath() {
    }

    private static double length(double dx, double dy, double dz) {
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static boolean isOutside(double dx, double dy, double dz, double leash) {
        return length(dx, dy, dz) > leash;
    }

    public static boolean shouldSnap(double dx, double dy, double dz, double leash) {
        return length(dx, dy, dz) > leash + SNAP_SLACK;
    }

    public static double[] correctedVelocity(double dx, double dy, double dz, double leash,
                                             double vx, double vy, double vz) {
        double len = length(dx, dy, dz);
        if (!(len > leash) || len < 1.0E-6) {
            return new double[]{vx, vy, vz};
        }
        double nx = dx / len;
        double ny = dy / len;
        double nz = dz / len;
        double outward = vx * nx + vy * ny + vz * nz;
        if (outward > 0.0) {
            vx -= nx * outward;
            vy -= ny * outward;
            vz -= nz * outward;
        }
        double pull = Math.min(MAX_PULL, (len - leash) * PULL_GAIN);
        return new double[]{vx - nx * pull, vy - ny * pull, vz - nz * pull};
    }

    /** The point on the leash in the direction of {@code d}; the anchor itself when {@code d} is zero. */
    public static double[] snapPoint(double ax, double ay, double az, double dx, double dy, double dz, double leash) {
        double len = length(dx, dy, dz);
        if (len < 1.0E-6) {
            return new double[]{ax, ay, az};
        }
        double scale = leash / len;
        return new double[]{ax + dx * scale, ay + dy * scale, az + dz * scale};
    }
}
