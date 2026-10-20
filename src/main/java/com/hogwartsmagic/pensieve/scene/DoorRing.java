package com.hogwartsmagic.pensieve.scene;

/**
 * The iron ring on a {@code memory_door} leaf as a damped pendulum. The ring hangs from a stud and the leaf swings
 * under it: when the leaf speeds up or slows down about its hinge the ring lags and swings opposite to the angular
 * acceleration, then rings down. Pure maths, stepped once per client tick by the renderer.
 *
 * <p>{@link State#angle} is the ring swing in degrees, signed opposite to the yaw acceleration: positive yaw
 * acceleration drives it negative. The renderer maps the sign to the leaf's local +Z or -Z (it depends on the
 * face and on whether it is the mirrored leaf).
 */
public final class DoorRing {
    /** Spring pulling the ring back to hanging, per tick squared. */
    public static final float K = 0.18f;
    /** Velocity kept each tick. */
    public static final float DAMPING = 0.86f;
    /** The ring never swings more than this many degrees from hanging. */
    public static final float MAX_DEG = 50f;
    /** Degrees of ring velocity per degree-per-tick-squared of leaf acceleration. */
    public static final float DRIVE = 6f;
    /** A leaf losing more than this many degrees per tick of yaw speed in one tick shudders. */
    public static final float SHUDDER_DECEL_DEG = 8f;
    public static final int SHUDDER_TICKS = 4;
    public static final float SHUDDER_DEG = 0.3f;

    private DoorRing() {}

    public static final class State {
        public float angle;
        public float vel;
    }

    /** Advance one tick; {@code yawAccelDeg} is the leaf change in yaw speed this tick, in degrees per tick squared. */
    public static void step(State s, float yawAccelDeg) {
        s.vel = (s.vel - DRIVE * yawAccelDeg - K * s.angle) * DAMPING;
        s.angle += s.vel;
        if (s.angle > MAX_DEG) {
            s.angle = MAX_DEG;
            if (s.vel > 0) s.vel = 0;
        } else if (s.angle < -MAX_DEG) {
            s.angle = -MAX_DEG;
            if (s.vel < 0) s.vel = 0;
        }
    }

    /** True when the leaf was moving and its yaw speed dropped by more than {@link #SHUDDER_DECEL_DEG} in one tick. */
    public static boolean shudders(float prevYawVel, float yawVel) {
        return Math.abs(prevYawVel) - Math.abs(yawVel) > SHUDDER_DECEL_DEG && prevYawVel * yawVel >= 0f;
    }

    /** The yaw jitter in degrees for a shudder with {@code ticksLeft} ticks to go: alternating sign, fading out. */
    public static float shudder(int ticksLeft) {
        if (ticksLeft <= 0) return 0f;
        float fade = ticksLeft / (float) SHUDDER_TICKS;
        return SHUDDER_DEG * fade * ((ticksLeft & 1) == 0 ? 1f : -1f);
    }
}
