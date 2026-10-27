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

    /** Master: the Patronus follows its caster, lights the dark around them and drives back the undead. */
    public static final int MASTER_RANK = 4;
    public static final double UNDEAD_RADIUS = 6.0;
    public static final int UNDEAD_INTERVAL = 5;
    public static final double UNDEAD_PUSH = 0.6;
    public static final int NIGHT_VISION_TICKS = 260;
    public static final int NIGHT_VISION_INTERVAL = 40;
    public static final double LIGHT_RADIUS = 8.0;
    public static final double FOLLOW_START = 6.0;
    public static final double FOLLOW_STOP = 3.0;
    public static final double TELEPORT_DISTANCE = 24.0;

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

    public static boolean masterAura(int rank) {
        return rank >= MASTER_RANK;
    }

    /** Undead and Inferi inside the radius are driven back; bosses are never moved. */
    public static boolean repels(boolean undeadOrInferius, boolean boss, double distanceSqr) {
        return undeadOrInferius && !boss && distanceSqr <= UNDEAD_RADIUS * UNDEAD_RADIUS;
    }

    private static int index(int rank) {
        return Math.max(0, Math.min(CORPOREAL_CHANCE.length - 1, rank - 1));
    }
}
