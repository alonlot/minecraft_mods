package com.hogwartsmagic.spell.motion;

/**
 * Alatus: faster broom infusion with rank and a chance of a better broom. A broom's quality is stored as
 * the BROOM_QUALITY item component and copied onto the BroomEntity, which reads the multipliers here.
 */
public final class AlatusRules {
    public static final int QUALITY_NORMAL = 0;
    public static final int QUALITY_FINE = 1;
    public static final int QUALITY_MASTERWORK = 2;
    public static final int REINFUSE_DUST_COST = 4;
    private static final int[] INFUSION_TICKS = {110, 80, 60, 40};
    private static final double[] FINE = {0.0, 0.15, 0.30, 0.40};
    private static final double[] MASTERWORK = {0.0, 0.0, 0.05, 0.15};
    private static final double[] SPEED = {1.0, 1.05, 1.10};

    private AlatusRules() {
    }

    public static int infusionTicks(int rank) {
        return INFUSION_TICKS[RankIndex.of(rank)];
    }

    public static double fineChance(int rank) {
        return FINE[RankIndex.of(rank)];
    }

    public static double masterworkChance(int rank) {
        return MASTERWORK[RankIndex.of(rank)];
    }

    /** {@code roll} in [0, 1): below the Masterwork chance is Masterwork, then the Fine band, else normal. */
    public static int rollQuality(int rank, double roll) {
        int i = RankIndex.of(rank);
        if (roll < MASTERWORK[i]) {
            return QUALITY_MASTERWORK;
        }
        if (roll < MASTERWORK[i] + FINE[i]) {
            return QUALITY_FINE;
        }
        return QUALITY_NORMAL;
    }

    public static boolean reinfuses(int rank) {
        return RankIndex.of(rank) == 3;
    }

    public static int clampQuality(int quality) {
        return Math.max(QUALITY_NORMAL, Math.min(QUALITY_MASTERWORK, quality));
    }

    public static double speedMultiplier(int quality) {
        return SPEED[clampQuality(quality)];
    }

    /** Masterwork boost stamina lasts 15% longer, so it drains at 1/1.15 the rate. */
    public static double staminaDrainMultiplier(int quality) {
        return clampQuality(quality) == QUALITY_MASTERWORK ? 1.0 / 1.15 : 1.0;
    }
}
