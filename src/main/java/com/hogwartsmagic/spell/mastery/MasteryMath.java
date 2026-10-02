package com.hogwartsmagic.spell.mastery;

/**
 * Rank and XP arithmetic for spell mastery. Deliberately free of Minecraft types so it is
 * unit-tested directly; everything that touches a player goes through {@link SpellMasteryData}.
 */
public final class MasteryMath {
    public static final int NOVICE = 1;
    public static final int MAX_RANK = 4;
    private static final String[] ROMAN = {"I", "II", "III", "IV"};

    private MasteryMath() {
    }

    /** {@code thresholds[i]} is the cumulative XP for rank {@code i + 1}; {@code thresholds[0]} is 0. */
    public static int rankForXp(float xp, int[] thresholds) {
        int rank = NOVICE;
        for (int i = 1; i < thresholds.length && i < MAX_RANK; i++) {
            if (xp >= thresholds[i]) {
                rank = i + 1;
            }
        }
        return rank;
    }

    /** XP still missing for the next rank, or -1 once Master. */
    public static float xpToNextRank(float xp, int[] thresholds) {
        int rank = rankForXp(xp, thresholds);
        if (rank >= MAX_RANK) {
            return -1f;
        }
        return thresholds[rank] - xp;
    }

    /** How far through the current rank's band, 0..1; 1 at Master. Drives the Grimoire bar. */
    public static float rankProgress(float xp, int[] thresholds) {
        int rank = rankForXp(xp, thresholds);
        if (rank >= MAX_RANK) {
            return 1f;
        }
        float low = thresholds[rank - 1];
        float high = thresholds[rank];
        return Math.max(0f, Math.min(1f, (xp - low) / (high - low)));
    }

    /** Long-cooldown spells pay more per cast so every spell takes a similar real time to master. */
    public static int xpPerCast(int baseCooldownTicks) {
        int perCast = Math.round(baseCooldownTicks / 20.0f / 3.0f);
        return Math.max(1, Math.min(20, perCast));
    }

    public static float scaledXp(int baseXp, double multiplier) {
        if (multiplier <= 0.0) {
            return 0f;
        }
        return (float) (baseXp * multiplier);
    }

    /** Additive: -perRankDiscount for each rank above Novice. */
    public static float costMultiplier(int rank, double perRankDiscount) {
        int ranksAbove = Math.max(0, Math.min(MAX_RANK, rank) - NOVICE);
        return (float) Math.max(0.0, 1.0 - perRankDiscount * ranksAbove);
    }

    public static float maxManaBonus(int ranksGained, double perRank, double cap) {
        return (float) Math.min(cap, Math.max(0, ranksGained) * perRank);
    }

    public static String roman(int rank) {
        int index = Math.max(NOVICE, Math.min(MAX_RANK, rank)) - 1;
        return ROMAN[index];
    }
}
