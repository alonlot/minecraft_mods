package com.hogwartsmagic.spell.damage;

/** Dolohov's Curse: a lasting max-health wound that grows with rank and, at Master, spreads on death. */
public final class DolohovRules {
    public static final float DAMAGE = 6.0f;
    public static final double SPREAD_RADIUS = 3.0;
    private static final int[] HEARTS = {2, 3, 4, 5};
    private static final int[] TICKS = {600, 900, 1200, 1800};

    private DolohovRules() {
    }

    public static int hearts(int rank) {
        return HEARTS[RankIndex.of(rank)];
    }

    public static int ticks(int rank) {
        return TICKS[RankIndex.of(rank)];
    }

    public static boolean spreads(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /** Half the hearts, rounded up, at least one. */
    public static int spreadHearts(int hearts) {
        return Math.max(1, (hearts + 1) / 2);
    }

    /** Half the duration, at least one second. */
    public static int spreadTicks(int ticks) {
        return Math.max(20, ticks / 2);
    }
}
