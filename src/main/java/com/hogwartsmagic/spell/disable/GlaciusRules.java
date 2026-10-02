package com.hogwartsmagic.spell.disable;

/** Glacius: chill first, freeze solid on the next hit, brittle for the payoff. Master frozen deaths shatter. */
public final class GlaciusRules {
    private static final int[] SLOW_TICKS = {80, 100, 120, 140};
    private static final int[] CHILL_WINDOW_TICKS = {80, 90, 100, 120};
    private static final int[] FREEZE_TICKS = {40, 50, 60, 70};
    private static final int[] BRITTLE_AMPLIFIER = {0, 0, 1, 1};
    /** Slowness II. */
    public static final int SLOW_AMPLIFIER = 1;
    /** Vanilla frost overlay for a chilled target; under the 140 that counts as fully frozen. */
    public static final int CHILL_FROST_TICKS = 100;
    public static final int BRITTLE_LINGER_TICKS = 60;
    public static final double SHATTER_RADIUS = 4.0;
    public static final int SHATTER_SLOW_TICKS = 60;
    /** Slowness III. */
    public static final int SHATTER_SLOW_AMPLIFIER = 2;

    private GlaciusRules() {
    }

    public static int slowTicks(int rank) {
        return SLOW_TICKS[RankIndex.of(rank)];
    }

    public static int chillWindowTicks(int rank) {
        return CHILL_WINDOW_TICKS[RankIndex.of(rank)];
    }

    public static int freezeTicks(int rank) {
        return FREEZE_TICKS[RankIndex.of(rank)];
    }

    /** Brittle amplifier: 0 is +30%, 1 is +60% (see BrittleRules). */
    public static int brittleAmplifier(int rank) {
        return BRITTLE_AMPLIFIER[RankIndex.of(rank)];
    }

    /** Brittle stays a little after the thaw, so the follow-up hit still lands on it. */
    public static int brittleTicks(int appliedFreezeTicks) {
        return Math.max(0, appliedFreezeTicks) + BRITTLE_LINGER_TICKS;
    }

    public static boolean shatters(int rank) {
        return RankIndex.of(rank) == 3;
    }
}
