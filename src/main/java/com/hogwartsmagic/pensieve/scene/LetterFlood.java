package com.hogwartsmagic.pensieve.scene;

import java.util.SplittableRandom;

/**
 * The {@code letter_flood} swarm: envelopes bursting from a fireplace mouth, whirling inside a room box and
 * settling on the floor. Plain doubles and arrays only, no Minecraft types, so it is unit-testable and can run on
 * either side. Every random number is drawn up front in the constructor, so {@link #step} is a pure function of the
 * state: stepping {@code n} times and {@link #fastForward(int) fast-forwarding} {@code n} are identical, and a late
 * joiner sees the swarm exactly as if watched from the start.
 *
 * <p>The room box is axis-aligned. It starts at the emitter plane (so nothing can fly behind the fireplace wall),
 * extends {@code 2 * hz} forward, is {@code hx} either side, and rises {@code 2 * hy} from the floor ({@code ay}).
 * The yaw is snapped to the nearest 90 degrees for the box, and positions are clamped after integration, so a
 * letter can never be outside it whatever the velocity.
 */
public final class LetterFlood {
    /** Letters leave the emitter evenly over this fraction of the duration. */
    static final double EMIT_FRACTION = 0.6;
    static final double GRAVITY = 0.015;
    static final double DRAG = 0.97;
    /**
     * Swirl acceleration (blocks per tick squared) round the box's vertical centre axis. With the drag this settles
     * a tangential speed near 0.4 blocks per tick; applied as a plain 0.04 it would reach 1.3.
     */
    static final double SWIRL = 0.012;
    /** The swirl calms to nothing over this many ticks after the emission ends. */
    static final int SWIRL_FADE_TICKS = 100;
    static final double WALL_DAMPING = 0.4;
    /** Horizontal speed kept on each floor contact. */
    static final double FLOOR_FRICTION = 0.6;
    static final double REST_SPEED = 0.03;
    /** A letter that has hit the floor this many times lies still whatever its speed. */
    static final int MAX_BOUNCES = 6;
    /** Letters stay this far inside the box walls and ceiling, so the quad never pokes into masonry. */
    static final double MARGIN = 0.15;
    /** Degrees a letter's yaw/pitch may turn in one tick, so a landing eases flat rather than snapping. */
    static final float TURN_PER_TICK = 20f;
    /** Height above the floor a letter leaves the mouth at. */
    static final double MOUTH_HEIGHT = 0.3;

    private final double ax, ay, az;
    private final float yawDeg;
    private final int count, duration;
    // Box (after the margin): min/max per axis, and the swirl axis.
    private final double minX, maxX, minY, maxY, minZ, maxZ, cx, cz;

    // Per-letter constants, drawn up front.
    private final int[] emitTick;
    private final float[] launchOffset, launchLift, launchSpeed, restYaw;
    private final double[] jitterX, jitterZ;

    // Per-letter state.
    private final double[] x, y, z, vx, vy, vz;
    private final float[] yaw, pitch;
    private final boolean[] emitted, resting;
    private final int[] bounces;
    private int age;

    public LetterFlood(double ax, double ay, double az, float yawDeg, float hx, float hy, float hz, int count,
                       int duration, long seed) {
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        this.yawDeg = yawDeg;
        this.count = count;
        this.duration = duration;

        int k = Math.floorMod(Math.round(yawDeg / 90f), 4);   // 0 = +Z, 1 = -X, 2 = -Z, 3 = +X
        double fx = k == 1 ? -1 : k == 3 ? 1 : 0;
        double fz = k == 0 ? 1 : k == 2 ? -1 : 0;
        double halfX = fx != 0 ? hz : hx;
        double halfZ = fz != 0 ? hz : hx;
        double centreX = ax + fx * hz, centreZ = az + fz * hz;
        this.cx = centreX;
        this.cz = centreZ;
        // The emitter-side wall is the fireplace wall: keep the margin there too.
        this.minX = centreX - halfX + MARGIN;
        this.maxX = centreX + halfX - MARGIN;
        this.minZ = centreZ - halfZ + MARGIN;
        this.maxZ = centreZ + halfZ - MARGIN;
        this.minY = ay;
        this.maxY = ay + 2.0 * hy - MARGIN;

        emitTick = new int[count];
        launchOffset = new float[count];
        launchLift = new float[count];
        launchSpeed = new float[count];
        restYaw = new float[count];
        jitterX = new double[count];
        jitterZ = new double[count];
        SplittableRandom rng = new SplittableRandom(seed);
        double emitTicks = Math.max(1.0, EMIT_FRACTION * duration);
        for (int i = 0; i < count; i++) {
            emitTick[i] = (int) Math.floor(i * emitTicks / count);
            launchOffset[i] = (float) (rng.nextDouble() * 120.0 - 60.0);
            launchLift[i] = (float) (10.0 + rng.nextDouble() * 35.0);
            launchSpeed[i] = (float) (0.35 + rng.nextDouble() * 0.25);
            restYaw[i] = (float) (rng.nextDouble() * 360.0 - 180.0);
            jitterX[i] = rng.nextDouble() * 2 - 1;
            jitterZ[i] = rng.nextDouble() * 2 - 1;
        }
        x = new double[count];
        y = new double[count];
        z = new double[count];
        vx = new double[count];
        vy = new double[count];
        vz = new double[count];
        yaw = new float[count];
        pitch = new float[count];
        emitted = new boolean[count];
        resting = new boolean[count];
        bounces = new int[count];
    }

    /** Advance one tick. */
    public void step() {
        double swirl = swirlStrength();
        for (int i = 0; i < count; i++) {
            if (!emitted[i] && age >= emitTick[i]) spawn(i);
            if (emitted[i] && !resting[i]) move(i, swirl);
            turn(i);
        }
        age++;
    }

    public void fastForward(int ticks) {
        for (int i = 0; i < ticks; i++) step();
    }

    private double swirlStrength() {
        int over = age - duration;
        if (over <= 0) return SWIRL;
        return SWIRL * Math.max(0.0, 1.0 - over / (double) SWIRL_FADE_TICKS);
    }

    private void spawn(int i) {
        emitted[i] = true;
        double a = Math.toRadians(yawDeg + launchOffset[i]);
        double lift = Math.toRadians(launchLift[i]);
        double horizontal = Math.cos(lift) * launchSpeed[i];
        vx[i] = -Math.sin(a) * horizontal;
        vz[i] = Math.cos(a) * horizontal;
        vy[i] = Math.sin(lift) * launchSpeed[i];
        x[i] = clamp(ax + jitterX[i] * 0.3, minX, maxX);
        z[i] = clamp(az + jitterZ[i] * 0.3, minZ, maxZ);
        // The mouth sits on the box edge; nudge the letter into the room so it is never behind the wall.
        double[] f = forwardUnit();
        x[i] = clamp(x[i] + f[0] * MARGIN, minX, maxX);
        z[i] = clamp(z[i] + f[1] * MARGIN, minZ, maxZ);
        y[i] = clamp(ay + MOUTH_HEIGHT, minY, maxY);
        yaw[i] = (float) Math.toDegrees(Math.atan2(-vx[i], vz[i]));
    }

    private double[] forwardUnit() {
        int k = Math.floorMod(Math.round(yawDeg / 90f), 4);
        return new double[] {k == 1 ? -1 : k == 3 ? 1 : 0, k == 0 ? 1 : k == 2 ? -1 : 0};
    }

    private void move(int i, double swirl) {
        boolean airborne = y[i] > minY + 0.05;
        vy[i] -= GRAVITY;
        if (airborne) {
            double rx = x[i] - cx, rz = z[i] - cz;
            double r = Math.sqrt(rx * rx + rz * rz);
            if (r > 1e-6) {
                vx[i] += -rz / r * swirl;
                vz[i] += rx / r * swirl;
            }
        }
        vx[i] *= DRAG;
        vy[i] *= DRAG;
        vz[i] *= DRAG;
        x[i] += vx[i];
        y[i] += vy[i];
        z[i] += vz[i];

        // Clamp after integrating, so no velocity can carry a letter out of the room.
        if (x[i] < minX) { x[i] = minX; vx[i] = Math.abs(vx[i]) * WALL_DAMPING; }
        if (x[i] > maxX) { x[i] = maxX; vx[i] = -Math.abs(vx[i]) * WALL_DAMPING; }
        if (z[i] < minZ) { z[i] = minZ; vz[i] = Math.abs(vz[i]) * WALL_DAMPING; }
        if (z[i] > maxZ) { z[i] = maxZ; vz[i] = -Math.abs(vz[i]) * WALL_DAMPING; }
        if (y[i] > maxY) { y[i] = maxY; vy[i] = -Math.abs(vy[i]) * WALL_DAMPING; }
        if (y[i] <= minY) {
            y[i] = minY;
            double speed = Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]);
            bounces[i]++;
            if (speed < REST_SPEED || bounces[i] >= MAX_BOUNCES) {
                resting[i] = true;
                vx[i] = vy[i] = vz[i] = 0;
            } else {
                vy[i] = Math.abs(vy[i]) * WALL_DAMPING;
                vx[i] *= FLOOR_FRICTION;
                vz[i] *= FLOOR_FRICTION;
            }
        }
    }

    /** Ease the facing: along the flight while flying (tilted with the climb), flat at the rest yaw once landed. */
    private void turn(int i) {
        if (!emitted[i]) return;
        float targetYaw = yaw[i];
        float targetPitch;
        if (resting[i]) {
            targetYaw = restYaw[i];
            targetPitch = 90f;
        } else {
            if (vx[i] * vx[i] + vz[i] * vz[i] > 1e-4) targetYaw = (float) Math.toDegrees(Math.atan2(-vx[i], vz[i]));
            targetPitch = (float) Math.max(-45.0, Math.min(45.0, -vy[i] * 100.0));
        }
        float dy = ((targetYaw - yaw[i]) % 360f + 540f) % 360f - 180f;
        yaw[i] += Math.max(-TURN_PER_TICK, Math.min(TURN_PER_TICK, dy));
        pitch[i] += Math.max(-TURN_PER_TICK, Math.min(TURN_PER_TICK, targetPitch - pitch[i]));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public int age() {
        return age;
    }

    public int count() {
        return count;
    }

    public double x(int i) {
        return emitted[i] ? x[i] : ax;
    }

    public double y(int i) {
        return emitted[i] ? y[i] : ay;
    }

    public double z(int i) {
        return emitted[i] ? z[i] : az;
    }

    public float yaw(int i) {
        return yaw[i];
    }

    public float pitch(int i) {
        return pitch[i];
    }

    public boolean emitted(int i) {
        return emitted[i];
    }

    public boolean resting(int i) {
        return resting[i];
    }

    /** Whether any letter is still in the air (for the page-turn sound). */
    public boolean anyFlying() {
        for (int i = 0; i < count; i++) {
            if (emitted[i] && !resting[i]) return true;
        }
        return false;
    }
}
