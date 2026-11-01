package com.hogwartsmagic.ambience;

/**
 * The swing of a hall door leaf, as pure numbers (the drawing is {@code client/ambience/WorldDoors}). A leaf opens over
 * {@link #OPEN_TICKS} ticks while a player is near, stays open until {@link #HOLD_TICKS} ticks after the last one was
 * near, then closes over the same time. Leaves are only ever a picture over an opening that stays empty, so nobody is
 * ever blocked, whatever this says.
 *
 * <p>Yaw follows {@code DoorHinge}: the plain leaf opens toward lower yaw and the mirrored one toward higher yaw, which
 * is how a pair swings apart (the Sorting memory's doors do the same: -90 to -180 and -90 to 0).
 */
public final class DoorSwing {
    /** Ticks to swing fully open, and fully shut. */
    public static final int OPEN_TICKS = 30;
    /** Ticks a leaf stays open after the last player was near. */
    public static final int HOLD_TICKS = 100;
    /** How far a leaf swings from shut, in degrees. */
    public static final float OPEN_DEGREES = 100f;
    /** A player this close (blocks, level) to the doorway opens the leaves. */
    public static final double TRIGGER_RANGE = 6.0;
    /** The leaf is this wide at the hall's scale (a scale 1 leaf is 1 wide, the hall's are scale 4). */
    public static final double LEAF_WIDTH = 4.0;

    private DoorSwing() {
    }

    /** One leaf's swing: how far open it is (0 shut, 1 open) and when a player was last near. */
    public static final class State {
        private float progress;
        private long lastNear = Long.MIN_VALUE / 2;

        /** 0 shut to 1 fully open. */
        public float progress() {
            return progress;
        }
    }

    /** Advances the leaf one tick; {@code gameTime} is the level's game time, {@code near} whether a player is near. */
    public static void tick(State s, boolean near, long gameTime) {
        if (near) {
            s.lastNear = gameTime;
        }
        boolean wantOpen = gameTime - s.lastNear <= HOLD_TICKS;
        s.progress += (wantOpen ? 1f : -1f) / OPEN_TICKS;
        s.progress = Math.max(0f, Math.min(1f, s.progress));
    }

    /** The swing eased: slow, quick, slow (a heavy door), flat at both ends. */
    public static float eased(float progress) {
        float p = Math.max(0f, Math.min(1f, progress));
        return p * p * (3f - 2f * p);
    }

    /** The leaf's yaw at this progress, from its closed yaw. */
    public static float yaw(float closedYaw, boolean mirrored, float progress) {
        return closedYaw + (mirrored ? OPEN_DEGREES : -OPEN_DEGREES) * eased(progress);
    }

    /**
     * The middle of the shut leaf in x and z, from its hinge: the leaf runs {@link #LEAF_WIDTH} from the hinge along
     * local x (toward the opposite side for the mirrored leaf), which {@code DoorHinge} puts at
     * {@code (cos yaw, -sin yaw)} in the world.
     */
    public static double[] leafMid(double hingeX, double hingeZ, float closedYaw, boolean mirrored) {
        double r = Math.toRadians(closedYaw);
        double half = LEAF_WIDTH * 0.5 * (mirrored ? -1.0 : 1.0);
        return new double[]{hingeX + Math.cos(r) * half, hingeZ - Math.sin(r) * half};
    }
}
