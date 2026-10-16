package com.hogwartsmagic.spell.summon;

/**
 * Expecto Patronum by rank. Every cast rolls once: a hit summons a corporeal Patronus, a miss holds up
 * the cone of silver light. The chance of a corporeal answer grows with rank, and from Expert the
 * corporeal Patronus takes the caster's own animal form. Pure Java: safe on a dedicated server.
 */
public final class PatronusRules {
    /** Indexed by rank - 1: Novice, Adept, Expert, Master. */
    private static final float[] CORPOREAL_CHANCE = {0.15f, 0.30f, 0.50f, 0.75f};
    private static final int FORM_RANK = 3;

    private PatronusRules() {
    }

    public static float corporealChance(int rank) {
        return CORPOREAL_CHANCE[index(rank)];
    }

    /** True when a roll in [0, 1) answers with a corporeal Patronus. */
    public static boolean isCorporeal(int rank, float roll) {
        return roll < corporealChance(rank);
    }

    /** Expert and Master: the corporeal Patronus takes the caster's own form. */
    public static boolean personalForm(int rank) {
        return index(rank) + 1 >= FORM_RANK;
    }

    private static int index(int rank) {
        return Math.max(0, Math.min(CORPOREAL_CHANCE.length - 1, rank - 1));
    }
}
