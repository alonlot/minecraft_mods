package com.hogwartsmagic.entity;

/**
 * The numbers and steering of a Death Eater flying a broom. Pure, so a whole flight can be simulated in a unit test.
 * Speeds are blocks actually travelled per tick: {@link #command} converts them for the broom's momentum and damping.
 *
 * <p>For scale, a player's broom really travels 0.40 / 0.48 / 0.57 a tick unboosted (training, Nimbus, Firebolt) and
 * 1.2 to 1.9 at full boost, and a Death Eater's spells fly at 1.5.
 */
public final class BroomPilotRules {
    /** How much of its velocity the broom keeps from one tick's steering to the next. */
    public static final double RETAIN = 0.86D;
    /** BroomEntity.tick scales every broom's velocity by this after moving. */
    public static final double DAMPING = 0.94D;

    /** The distance a healthy rider fights from. */
    public static final double HOLD_RANGE = 11.0D;
    /** The distance a hurt or wandless rider keeps. Still inside its follow range, so it stays in the fight. */
    public static final double FLEE_RANGE = 24.0D;
    /** How high above its target a rider fights. */
    public static final double COMBAT_HEIGHT = 5.5D;
    /** The lowest a rider flies over whatever is beneath it, unless it is landing. */
    public static final double MIN_CLEARANCE = 3.0D;
    /** How low a rider flies when landing on an errand, so it can reach the ground from its seat. */
    public static final double LANDING_CLEARANCE = 0.1D;
    /** Room a seated rider needs between the broom and a ceiling. */
    public static final double HEADROOM = 2.6D;
    /** Patrol height over the ground. */
    public static final double CRUISE_CLEARANCE = 9.0D;
    /** The fastest a broom climbs or dives. */
    public static final double MAX_CLIMB = 0.5D;
    /** The tallest obstacle a rider goes over rather than around. */
    public static final double MAX_HOP = 24.0D;
    /** How far from its anchor a patrol point can be. */
    public static final double PATROL_MIN_RADIUS = 12.0D;
    public static final double PATROL_MAX_RADIUS = 32.0D;

    private static final double CAST_SLOWDOWN = 0.45D;
    private static final double[] CRUISE = {0.40D, 0.48D, 0.56D};
    private static final double[] STRAFE = {0.50D, 0.58D, 0.66D};
    private static final double[] CHASE = {1.00D, 1.25D, 1.50D};
    private static final double[] ESCAPE = {1.20D, 1.50D, 1.85D};

    private BroomPilotRules() {
    }

    private static int tier(int broomType) {
        return Math.max(0, Math.min(CRUISE.length - 1, broomType));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double cruiseSpeed(int broomType) {
        return CRUISE[tier(broomType)];
    }

    public static double strafeSpeed(int broomType) {
        return STRAFE[tier(broomType)];
    }

    /** The range to keep: the fighting range, or well back when hurt or without a wand. */
    public static double holdRange(boolean frightened) {
        return frightened ? FLEE_RANGE : HOLD_RANGE;
    }

    /** How strongly to close (positive) or open (negative) the range, from -1 to 1. Continuous: no bands to flip across. */
    public static double radialPull(double range, double hold) {
        return clamp((range - hold) / 6.0D, -1.0D, 1.0D);
    }

    /** How much of the heading is circling the target. Circling fades as the range needs correcting. */
    public static double orbitShare(double radialPull) {
        return 1.0D - 0.85D * Math.abs(radialPull);
    }

    /** Strafing speed in the fight, rising smoothly to a chase when far and an escape when crowded. */
    public static double combatSpeed(int broomType, double range, double hold, boolean casting) {
        int tier = tier(broomType);
        double far = clamp((range - (hold + 4.0D)) / 10.0D, 0.0D, 1.0D);
        double near = clamp(((hold - 3.0D) - range) / 5.0D, 0.0D, 1.0D);
        double speed = STRAFE[tier] + (CHASE[tier] - STRAFE[tier]) * far + (ESCAPE[tier] - STRAFE[tier]) * near;
        return casting ? speed * CAST_SLOWDOWN : speed;
    }

    /** Speed on the way to an errand: a chase, easing off to nothing on arrival. */
    public static double arrivalSpeed(int broomType, double distance) {
        return Math.min(CHASE[tier(broomType)], distance * 0.14D);
    }

    /** Height above an errand's spot, gliding down over the last stretch. */
    public static double glideHeight(double distance) {
        return Math.min(6.0D, distance * 0.5D);
    }

    /** Patrol height: a level cruise over the floor with a slow breathing bob. */
    public static double cruiseAltitude(double floor, int tick) {
        return floor + CRUISE_CLEARANCE + Math.sin(tick * 0.05D) * 1.2D;
    }

    /** The height to fly at: what is wanted, kept off the floor and under the ceiling with room for the rider. */
    public static double safeAltitude(double want, double floor, double ceiling, double clearance) {
        double low = floor + clearance;
        double high = ceiling - HEADROOM;
        if (high < low) {
            return Math.max(floor + LANDING_CLEARANCE, high); // a tight passage: the ceiling decides
        }
        return clamp(want, low, high);
    }

    /** The climb speed for a height error. */
    public static double climbRate(double heightError) {
        return clamp(heightError * 0.1D, -MAX_CLIMB, MAX_CLIMB);
    }

    /** What to steer with for the broom to settle at this speed, given its momentum and damping. */
    public static double command(double speed) {
        return speed * (1.0D - RETAIN * DAMPING) / (1.0D - RETAIN);
    }

    /** One tick of steering on one axis. */
    public static double blend(double current, double command) {
        return current * RETAIN + command * (1.0D - RETAIN);
    }
}
