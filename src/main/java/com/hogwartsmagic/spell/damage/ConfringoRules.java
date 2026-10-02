package com.hogwartsmagic.spell.damage;

/** Confringo: the combat blast. Bigger and hotter with rank; at Master it hits brittle targets harder. */
public final class ConfringoRules {
    /** Today's rain blast is 1.15 against 1.5 dry. */
    public static final float RAIN_POWER_FACTOR = 0.77f;
    public static final float MASTER_BRITTLE_BONUS = 1.5f;
    private static final float[] POWER = {1.5f, 1.8f, 2.1f, 2.4f};
    private static final int[] IGNITE_SECONDS = {4, 5, 6, 7};
    private static final double[] SPLASH_IGNITE_RADIUS = {0.0, 1.5, 2.0, 2.5};
    private static final int[] SPLASH_IGNITE_SECONDS = {0, 2, 3, 3};

    private ConfringoRules() {
    }

    public static float power(int rank, boolean raining) {
        float power = POWER[RankIndex.of(rank)];
        return raining ? power * RAIN_POWER_FACTOR : power;
    }

    public static int igniteSeconds(int rank) {
        return IGNITE_SECONDS[RankIndex.of(rank)];
    }

    public static double splashIgniteRadius(int rank) {
        return SPLASH_IGNITE_RADIUS[RankIndex.of(rank)];
    }

    public static int splashIgniteSeconds(int rank) {
        return SPLASH_IGNITE_SECONDS[RankIndex.of(rank)];
    }

    public static float brittleBonus(int rank) {
        return RankIndex.of(rank) == 3 ? MASTER_BRITTLE_BONUS : 1.0f;
    }
}
