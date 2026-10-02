package com.hogwartsmagic.spell.disable;

/** Stupefy: the fastest true stun, the interrupt button. Master chains to one more enemy. */
public final class StupefyRules {
    private static final int[] STUN_TICKS = {30, 40, 50, 50};
    private static final double[] KNOCKBACK = {0.0, 0.4, 0.4, 0.4};
    public static final double CHAIN_RADIUS = 5.0;
    public static final float MASTER_BEAM_SPEED = 1.25f;

    private StupefyRules() {
    }

    public static int stunTicks(int rank) {
        return STUN_TICKS[RankIndex.of(rank)];
    }

    public static double knockback(int rank) {
        return KNOCKBACK[RankIndex.of(rank)];
    }

    public static boolean chains(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /** The chained enemy gets half the stun. */
    public static int chainStunTicks(int rank) {
        return stunTicks(rank) / 2;
    }

    public static float beamSpeedMultiplier(int rank) {
        return chains(rank) ? MASTER_BEAM_SPEED : 1.0f;
    }
}
