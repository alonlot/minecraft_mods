package com.hogwartsmagic.spell.support;

/** Heal (Episkey to Vulnera Sanentur): the only cleanse for the mod's curses. Pure numbers. */
public final class HealRules {
    private static final float[] HEAL = {6.0f, 8.0f, 10.0f, 12.0f};
    private static final int[] REGEN_TICKS = {0, 0, 80, 80};

    private HealRules() {
    }

    public static float healAmount(int rank) {
        return HEAL[RankIndex.of(rank)];
    }

    public static boolean curesBleeding(int rank) {
        return rank >= 2;
    }

    public static int regenTicks(int rank) {
        return REGEN_TICKS[RankIndex.of(rank)];
    }

    public static boolean curesCursedWound(int rank) {
        return rank >= 4;
    }
}
