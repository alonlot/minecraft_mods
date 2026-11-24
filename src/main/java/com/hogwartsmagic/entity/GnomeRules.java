package com.hogwartsmagic.entity;

/**
 * The sums behind de-gnoming a garden gnome: how hard a whirled gnome is flung, how far it flies, when a gnome
 * held without whirling bites, where it hangs at the hand or on the spin orbit, and what a digging gnome does
 * to the ground. Plain static methods with no Minecraft imports, so they can be unit tested.
 */
public final class GnomeRules {
    private GnomeRules() {}

    /** Ticks of spinning after which the throw is at full strength (3 seconds). */
    public static final int FULL_SPIN_TICKS = 60;
    /** Launch speed (blocks per tick) of a gnome let go without any spin. */
    public static final double MIN_THROW_SPEED = 0.35;
    /** Launch speed of a fully whirled gnome; tuned so the best angle lands it about 25 blocks away. */
    public static final double MAX_THROW_SPEED = 1.30;
    /** Downward pull per tick on a flying gnome. */
    public static final double GRAVITY = 0.06;
    /** Air drag per tick on a flying gnome. */
    public static final double DRAG = 0.99;
    /** Height above the thrower's feet at which the gnome is let go. */
    public static final double THROW_HEIGHT = 1.2;
    /** A gnome that lands this far from where it was let go leaves for good. */
    public static final double GONE_DISTANCE = 12.0;
    /** Ticks a gnome can be held, unspun, before it bites. */
    public static final int BITE_DELAY_TICKS = 60;
    /** The throw aims this much higher than the thrower's gaze. */
    public static final float LIFT_DEGREES = 15f;
    public static final float MIN_ELEVATION_DEGREES = 10f;
    public static final float MAX_ELEVATION_DEGREES = 50f;
    /** Radius and height (above the thrower's feet) of the circle a whirled gnome swings on. */
    public static final double SPIN_RADIUS = 1.2;
    public static final double SPIN_HEIGHT = 0.9;
    /** The whirl starts slow and ends fast, in degrees per tick. */
    public static final double MIN_SPIN_DEG_PER_TICK = 6;
    public static final double MAX_SPIN_DEG_PER_TICK = 24;
    /** Where the held gnome hangs, relative to the carrier's feet: forward, to the right and up. */
    public static final double HAND_FORWARD = 0.6;
    public static final double HAND_RIGHT = 0.38;
    public static final double HAND_UP = 0.75;
    /** Chance that a dig on coarse dirt opens a one-block pit. */
    public static final float PIT_CHANCE = 0.3f;
    public static final int DIG_NONE = 0;
    public static final int DIG_COARSE = 1;
    public static final int DIG_PIT = 2;
    /** Cosine of the half-angle (60 degrees) of the cone in which a player counts as watching a gnome. */
    public static final double VIEW_COS = 0.5;

    private static final int MAX_FLIGHT_TICKS = 400;

    /** Launch speed for a gnome whirled for {@code spinTicks}: linear up to {@link #FULL_SPIN_TICKS}, then capped. */
    public static double throwSpeed(int spinTicks) {
        double f = Math.max(0, Math.min(1.0, spinTicks / (double) FULL_SPIN_TICKS));
        return MIN_THROW_SPEED + (MAX_THROW_SPEED - MIN_THROW_SPEED) * f;
    }

    /** Launch angle above the horizontal, in radians, from a Minecraft pitch (negative looks up). */
    public static double launchElevationRadians(float xRotDegrees) {
        double deg = -xRotDegrees + LIFT_DEGREES;
        deg = Math.max(MIN_ELEVATION_DEGREES, Math.min(MAX_ELEVATION_DEGREES, deg));
        return Math.toRadians(deg);
    }

    /**
     * Horizontal distance flown before landing {@code dropHeight} below the launch point. Steps exactly as
     * {@code GnomeEntity.travel} does: move, then apply drag and gravity.
     */
    public static double flightDistance(double speed, double elevationRad, double dropHeight) {
        double vx = speed * Math.cos(elevationRad);
        double vy = speed * Math.sin(elevationRad);
        double x = 0, y = 0;
        for (int i = 0; i < MAX_FLIGHT_TICKS; i++) {
            double nx = x + vx;
            double ny = y + vy;
            if (ny <= -dropHeight) {
                double t = (y + dropHeight) / (y - ny);
                return x + (nx - x) * t;
            }
            x = nx;
            y = ny;
            vx *= DRAG;
            vy = (vy - GRAVITY) * DRAG;
        }
        return x;
    }

    public static boolean goneForGood(double distance) {
        return distance >= GONE_DISTANCE;
    }

    /** True when a carried gnome has been held long enough, never whirled, to bite. */
    public static boolean biteDue(int carriedTicks, boolean everSpun) {
        return !everSpun && carriedTicks >= BITE_DELAY_TICKS;
    }

    /** Total angle swept after {@code spinTicks} of whirling, in degrees; the speed ramps up then holds. */
    public static double spinAngleDegrees(double spinTicks) {
        double f = FULL_SPIN_TICKS;
        double t = Math.max(0, spinTicks);
        if (t <= f) {
            return MIN_SPIN_DEG_PER_TICK * t + (MAX_SPIN_DEG_PER_TICK - MIN_SPIN_DEG_PER_TICK) * t * t / (2 * f);
        }
        return spinAngleDegrees(f) + MAX_SPIN_DEG_PER_TICK * (t - f);
    }

    /** Offset of the carried gnome's origin from the carrier's feet. Forward is (-sin yaw, cos yaw). */
    public static double[] handOffset(float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        return new double[] {
                fx * HAND_FORWARD + rx * HAND_RIGHT,
                HAND_UP,
                fz * HAND_FORWARD + rz * HAND_RIGHT};
    }

    /** Offset on the whirl orbit; it starts in front of the carrier and sweeps round. */
    public static double[] spinOffset(float yawDegrees, double spinTicks) {
        double a = Math.toRadians(yawDegrees + spinAngleDegrees(spinTicks));
        return new double[] {-Math.sin(a) * SPIN_RADIUS, SPIN_HEIGHT, Math.cos(a) * SPIN_RADIUS};
    }

    /**
     * What a digging gnome does: grass or dirt becomes coarse dirt; coarse dirt sometimes becomes a one-block
     * pit, but only over solid ground (never over a cave).
     */
    public static int digOutcome(boolean grassOrDirt, boolean coarse, boolean sturdyBelow, float roll) {
        if (grassOrDirt) {
            return DIG_COARSE;
        }
        if (coarse && sturdyBelow && roll < PIT_CHANCE) {
            return DIG_PIT;
        }
        return DIG_NONE;
    }

    /** True when the direction (to) lies within the viewing cone around (look). */
    public static boolean inView(double lookX, double lookY, double lookZ, double toX, double toY, double toZ) {
        double ll = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
        double tl = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
        if (ll < 1e-9 || tl < 1e-9) {
            return true;
        }
        return (lookX * toX + lookY * toY + lookZ * toZ) / (ll * tl) > VIEW_COS;
    }
}
