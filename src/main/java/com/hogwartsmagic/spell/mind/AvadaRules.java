package com.hogwartsmagic.spell.mind;

/** Avada Kedavra: a windup anyone can break, a backlash that fades with rank, a fifth of a boss's health. */
public final class AvadaRules {
    private static final int[] WINDUP_TICKS = {30, 24, 20, 16};
    private static final float[] BACKLASH = {4.0f, 3.0f, 2.0f, 0.0f};
    public static final float BOSS_FRACTION = 0.20f;
    public static final float INTERRUPT_DAMAGE = 4.0f;
    public static final int INTERRUPT_LOCKOUT_TICKS = 100;
    /** NPC casters keep today's backlash (before the house multiplier). */
    public static final float NPC_BACKLASH = 2.0f;

    private AvadaRules() {
    }

    public static int windupTicks(int rank) {
        return WINDUP_TICKS[RankIndex.of(rank)];
    }

    public static float backlash(int rank) {
        return BACKLASH[RankIndex.of(rank)];
    }

    public static float bossDamage(float maxHealth) {
        return maxHealth * BOSS_FRACTION;
    }

    public static boolean interrupts(float damage) {
        return damage >= INTERRUPT_DAMAGE;
    }

    public static boolean windupDone(long elapsed, int rank) {
        return elapsed >= windupTicks(rank);
    }
}
