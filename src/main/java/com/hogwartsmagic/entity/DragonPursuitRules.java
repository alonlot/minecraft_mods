package com.hogwartsmagic.entity;

/**
 * The numbers behind the Horntail's aerial pursuit of a broom rider, kept free of game classes so they can be
 * unit-tested.
 *
 * <p>The chase is a rubber band. Far behind, it flies harder than its quarry and closes the gap. In range, it
 * flies a little faster than its quarry, so it gains slowly enough to dodge. Its top speed depends on what it is
 * chasing: it can run down a Firebolt or a basic broom even at full boost, so straight-line speed never saves
 * you. A Nimbus is the exception: on some chases ({@link #NIMBUS_ESCAPE_CHANCE}) the dragon's top speed sits
 * under a boosting Nimbus, and a full stamina tank is enough to leave it behind. What always works is turning:
 * its turning circle stays well outside a broom's, so jinking hard makes it overshoot.
 */
public final class DragonPursuitRules {
    private DragonPursuitRules() {
    }

    /** Broom types, as BroomEntity numbers them. */
    public static final int BASIC = 0;
    public static final int NIMBUS = 1;
    public static final int FIREBOLT = 2;
    /** Top chase speed against an elytra (or anything not on a broom), blocks/tick. */
    public static final double CAP = 3.5D;
    /** Above a Masterwork Firebolt's boost (10 x 1.1). */
    public static final double CAP_FIREBOLT = 11.6D;
    /** Above a Masterwork basic broom's boost (6.5 x 1.1). */
    public static final double CAP_BASIC = 7.6D;
    /** Above a Masterwork Nimbus's boost (8 x 1.1): the chases it does not get away from. */
    public static final double CAP_NIMBUS = 9.3D;
    /** Under a plain Nimbus's boost (8): the chases it does get away from. */
    public static final double CAP_NIMBUS_OUTRUN = 7.0D;
    /** The share of chases a Nimbus can outrun. */
    public static final double NIMBUS_ESCAPE_CHANCE = 0.4D;
    /** Enraged it flies this much harder, except on a chase a Nimbus is going to win. */
    public static final double RAGE_BONUS = 1.05D;
    /** Slowest it flies while chasing: a dragon in the air never hangs still. */
    public static final double FLOOR = 0.55D;
    /** Max change of heading per tick (radians) at low speed: a turning circle of about 30 blocks at 2.4. */
    public static final double TURN = 0.075D;
    public static final double TURN_RAGING = 0.095D;
    /** Fast, the turn rate grows so the turning circle stops at this radius instead of growing without end. */
    public static final double MAX_TURN_RADIUS = 45.0D;
    /** Beyond this the dragon has fallen behind and flies harder. */
    public static final double CATCH_UP_RANGE = 28.0D;

    /**
     * The top speed for this chase. {@code broomType} is -1 when the quarry is not on a broom;
     * {@code nimbusEscapes} is this chase's roll against {@link #NIMBUS_ESCAPE_CHANCE}.
     */
    public static double cap(int broomType, boolean nimbusEscapes, boolean raging) {
        double cap;
        if (broomType == FIREBOLT) {
            cap = CAP_FIREBOLT;
        } else if (broomType == NIMBUS) {
            if (nimbusEscapes) return CAP_NIMBUS_OUTRUN;
            cap = CAP_NIMBUS;
        } else if (broomType == BASIC) {
            cap = CAP_BASIC;
        } else {
            cap = CAP;
        }
        return raging ? cap * RAGE_BONUS : cap;
    }

    /** Max heading change this tick at {@code speed}: the base rate, or enough to hold {@link #MAX_TURN_RADIUS}. */
    public static double turnRate(double speed, boolean raging) {
        return Math.max(raging ? TURN_RAGING : TURN, speed / MAX_TURN_RADIUS);
    }

    /** How fast the dragon wants to fly, given how fast its quarry flies, how far ahead it is, and the cap. */
    public static double chaseSpeed(double quarrySpeed, double distance, double cap) {
        double want;
        if (distance > CATCH_UP_RANGE) {
            want = quarrySpeed * 1.06D + 0.35D + (distance - CATCH_UP_RANGE) * 0.01D;
        } else {
            // A fixed margin is nothing at boost speed, so the edge grows with the quarry's speed.
            want = quarrySpeed * 1.04D + 0.12D;
        }
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
