package com.hogwartsmagic.spell.motion;

/** Tenebris Flight from Expert: projectiles and beams may pass through; a Master's smoke ram hurts. */
public final class TenebrisRules {
    public static final float RAM_DAMAGE = 5.0F;
    public static final int RAM_BLIND_TICKS = 40;
    public static final int RAM_COOLDOWN_TICKS = 20;
    public static final double RAM_REACH = 0.4;
    private static final double[] PASS_CHANCE = {0.0, 0.0, 0.3, 0.5};

    private TenebrisRules() {
    }

    public static double passChance(int rank) {
        return PASS_CHANCE[RankIndex.of(rank)];
    }

    public static boolean passes(int rank, double roll) {
        return roll < passChance(rank);
    }

    public static boolean rams(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /** The flight effect's amplifier carries the caster's rank (rank − 1), so the effect alone tells the rank. */
    public static int amplifier(int rank) {
        return RankIndex.of(rank);
    }

    public static boolean ramReady(boolean rammedBefore, long lastRam, long now) {
        return !rammedBefore || now - lastRam >= RAM_COOLDOWN_TICKS;
    }
}
