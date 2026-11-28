package com.hogwartsmagic.entity;

/**
 * The numbers behind the Horntail's aerial pursuit of a broom rider, kept free of game classes so they can be
 * unit-tested.
 *
 * <p>The chase is a rubber band, not a race the dragon always wins. Far behind, it flies harder than its quarry
 * and closes the gap. In range, it flies a little faster than its quarry, so it gains slowly enough to dodge. Its
 * top speed is capped below a boosting broom, and the boost runs on stamina. So the loop is: boost to break away,
 * and get caught again if the broom overheats. It also turns wide: a vector-rotation limit keeps its turning circle
 * well outside a broom's, so jinking hard makes it overshoot.
 */
public final class DragonPursuitRules {
    private DragonPursuitRules() {
    }

    /** Top chase speed, blocks/tick. A Firebolt cruises at 3.0 and boosts to 10, a basic broom 2.0 and 6.5. */
    public static final double CAP = 2.4D;
    public static final double CAP_RAGING = 2.8D;
    /** Slowest it flies while chasing: a dragon in the air never hangs still. */
    public static final double FLOOR = 0.55D;
    /** Max change of heading per tick (radians). At the cap that is a turning circle of roughly 30 blocks. */
    public static final double TURN = 0.075D;
    public static final double TURN_RAGING = 0.095D;
    /** Beyond this the dragon has fallen behind and flies harder. */
    public static final double CATCH_UP_RANGE = 28.0D;

    /** How fast the dragon wants to fly, given how fast its quarry flies and how far ahead it is. */
    public static double chaseSpeed(double quarrySpeed, double distance, boolean raging) {
        double want;
        if (distance > CATCH_UP_RANGE) {
            want = quarrySpeed + 0.35D + (distance - CATCH_UP_RANGE) * 0.01D;
        } else {
            want = quarrySpeed + 0.12D;
        }
        double cap = raging ? CAP_RAGING : CAP;
        return Math.max(FLOOR, Math.min(cap, want));
    }

    /** How many ticks ahead of the quarry to aim: enough to cut its corners, never a wild guess. */
    public static double leadTicks(double distance, double speed) {
        double eta = distance / Math.max(0.5D, speed);
        return Math.max(0.0D, Math.min(14.0D, eta)) * 0.6D;
    }

    /**
     * Turns unit vector {@code from} toward {@code want} by at most {@code maxAngle} radians, on the great circle
     * between them. Both are normalised first; the result is a unit vector.
     */
    public static double[] turnToward(double[] from, double[] want, double maxAngle) {
        double[] a = normalize(from);
        double[] b = normalize(want);
        double dot = clamp(a[0] * b[0] + a[1] * b[1] + a[2] * b[2], -1.0D, 1.0D);
        double angle = Math.acos(dot);
        if (angle <= maxAngle || angle < 1.0E-6D) {
            return b;
        }
        // The axis-free way: the component of b perpendicular to a gives the plane of the turn. Straight
        // behind leaves no plane, so any perpendicular will do, and a level one keeps the dragon upright.
        double[] perp = {b[0] - a[0] * dot, b[1] - a[1] * dot, b[2] - a[2] * dot};
        double pl = Math.sqrt(perp[0] * perp[0] + perp[1] * perp[1] + perp[2] * perp[2]);
        if (pl < 1.0E-6D) {
            perp = new double[]{-a[2], 0.0D, a[0]};
            pl = Math.sqrt(perp[0] * perp[0] + perp[2] * perp[2]);
            if (pl < 1.0E-6D) {
                perp = new double[]{1.0D, 0.0D, 0.0D};
                pl = 1.0D;
            }
        }
        double c = Math.cos(maxAngle);
        double s = Math.sin(maxAngle);
        return normalize(new double[]{
                a[0] * c + perp[0] / pl * s,
                a[1] * c + perp[1] / pl * s,
                a[2] * c + perp[2] / pl * s});
    }

    /** The new speed, eased from the current one: a dragon builds speed over a few wingbeats. */
    public static double easeSpeed(double current, double want) {
        double rate = want > current ? 0.10D : 0.18D;
        return current + (want - current) * rate;
    }

    static double[] normalize(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (l < 1.0E-9D) {
            return new double[]{0.0D, 0.0D, 1.0D};
        }
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
