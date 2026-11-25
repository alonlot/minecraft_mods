package com.hogwartsmagic.cosy;

/**
 * The cooking pot's and stove's rules, apart from the blocks. The pot cooks only while it is hot (a heat source
 * below it) and stirred (manually for a while, or by an enchanted ladle until the dish is done). Without either it
 * simply waits: progress is kept, nothing is lost.
 */
public final class PotRules {
    /** Three seconds of stirring from one empty-hand click. */
    public static final int MANUAL_STIR_TICKS = 60;
    public static final int MAX_INGREDIENTS = 6;
    /** A coal burns 1600 ticks; the stove holds eight coals' worth. */
    public static final int STOVE_MAX_BURN = 8 * 1600;

    private PotRules() {
    }

    /** One tick of cooking: progress goes up by one only when the pot is hot and stirred; otherwise it is unchanged. */
    public static int nextProgress(int progress, boolean heated, boolean stirring) {
        return heated && stirring ? progress + 1 : progress;
    }

    /** One tick of stirring: manual stirring runs down; an enchanted ladle keeps the count where it is. */
    public static int nextStir(int stirTicks, boolean enchanted) {
        return enchanted ? stirTicks : Math.max(0, stirTicks - 1);
    }

    public static boolean isStirring(int stirTicks, boolean enchanted) {
        return enchanted || stirTicks > 0;
    }

    /**
     * Whether the stove has room for an item worth {@code itemBurnTicks} on top of {@code burn} already stored.
     * An empty stove takes anything that burns (a lava bucket is worth more than it holds; the total is clamped).
     */
    public static boolean canTakeFuel(int burn, int itemBurnTicks) {
        return itemBurnTicks > 0 && (burn == 0 || burn + itemBurnTicks <= STOVE_MAX_BURN);
    }
}
