package com.hogwartsmagic.spell.mind;

/** Obliviate: the escape spell. Mobs forget the caster, players are silenced, Master wipes a raider's raid and home. */
public final class ObliviateRules {
    private static final int[] IGNORE_TICKS = {100, 200, 400, 600};
    private static final int[] SILENCE_TICKS = {40, 60, 60, 80};

    private ObliviateRules() {
    }

    /** How long a mob ignores the caster, unless the caster hurts it first. */
    public static int ignoreTicks(int rank) {
        return IGNORE_TICKS[RankIndex.of(rank)];
    }

    /** Silence on a player target. */
    public static int silenceTicks(int rank) {
        return SILENCE_TICKS[RankIndex.of(rank)];
    }

    /** Master capstone: the mob also forgets its raid and its home. */
    public static boolean memoryCharm(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /** Still forgetting at {@code now}; {@code until} is the game time the forgetting ends. */
    public static boolean stillForgotten(long now, long until) {
        return now < until;
    }
}
