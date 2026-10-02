package com.hogwartsmagic.spell.support;

/**
 * Protego as a parry. The shield lasts {@link #SHIELD_TICKS}; damage from the front is cut by
 * {@link #blockFraction}; a beam from the front inside the first {@link #parryWindowTicks} is parried
 * (dissolved, reflected at random, or sent back at its caster, by rank). Pure numbers and geometry.
 */
public final class ProtegoRules {
    public static final int SHIELD_TICKS = 60;
    public static final int PARRY_DISSOLVE = 0;
    public static final int PARRY_RANDOM = 1;
    public static final int PARRY_AT_CASTER = 2;
    public static final double RANDOM_REFLECT_YAW_DEGREES = 60.0;
    public static final double RANDOM_REFLECT_PITCH_DEGREES = 15.0;
    public static final double MASTER_MELEE_KNOCKBACK = 1.0;

    private static final float[] BLOCK = {0.40f, 0.50f, 0.60f, 0.75f};
    private static final int[] WINDOW = {8, 8, 12, 12};
    private static final int[] MODE = {PARRY_DISSOLVE, PARRY_RANDOM, PARRY_AT_CASTER, PARRY_AT_CASTER};

    private ProtegoRules() {
    }

    public static float blockFraction(int rank) {
        return BLOCK[RankIndex.of(rank)];
    }

    public static int parryWindowTicks(int rank) {
        return WINDOW[RankIndex.of(rank)];
    }

    public static int parryMode(int rank) {
        return MODE[RankIndex.of(rank)];
    }

    public static float refundFraction(int rank) {
        return rank >= 4 ? 0.5f : 0.0f;
    }

    public static boolean knocksBackMelee(int rank) {
        return rank >= 4;
    }

    public static boolean inParryWindow(long raisedAt, long now, int rank) {
        long age = now - raisedAt;
        return age >= 0 && age < parryWindowTicks(rank);
    }

    /** True when the source lies in the half-space the holder faces (horizontal only); degenerate input counts as front. */
    public static boolean isFront(double facingX, double facingZ, double toSourceX, double toSourceZ) {
        if (Math.hypot(facingX, facingZ) < 1.0e-6 || Math.hypot(toSourceX, toSourceZ) < 1.0e-6) {
            return true;
        }
        return facingX * toSourceX + facingZ * toSourceZ >= 0.0;
    }

    public static float reducedDamage(float amount, int rank) {
        return amount * (1.0f - blockFraction(rank));
    }

    /** {@code roll} in 0..1 maps to facing yaw -60..+60 degrees. */
    public static float randomReflectYaw(float facingYaw, double roll) {
        return facingYaw + (float) ((roll * 2.0 - 1.0) * RANDOM_REFLECT_YAW_DEGREES);
    }

    public static boolean canReflect(boolean unforgivable, boolean alreadyReflected) {
        return !unforgivable && !alreadyReflected;
    }
}
