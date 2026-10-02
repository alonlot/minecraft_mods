package com.hogwartsmagic.spell.damage;

/** Bombarda: the terrain breaker. Radius and hardness tier grow with rank; it is gentler on creatures. */
public final class BombardaRules {
    /** Positions examined per level per tick; a Master blast (179 positions) finishes in 3 ticks. */
    public static final int BLOCKS_PER_TICK = 64;
    public static final float MIN_SPLASH_FACTOR = 0.25f;
    private static final double[] RADIUS = {1.5, 2.0, 2.5, 3.5};
    private static final float[] MAX_HARDNESS = {3.0f, 3.0f, 6.0f, 6.0f};
    private static final double[] OBSIDIAN_CHANCE = {0.0, 0.0, 0.25, 1.0};
    private static final double[] DROP_CHANCE = {0.5, 1.0, 1.0, 1.0};
    private static final float[] ENTITY_DAMAGE = {5f, 6f, 7f, 8f};

    private BombardaRules() {
    }

    public static double radius(int rank) {
        return RADIUS[RankIndex.of(rank)];
    }

    public static float entityDamage(int rank) {
        return ENTITY_DAMAGE[RankIndex.of(rank)];
    }

    public static double dropChance(int rank) {
        return DROP_CHANCE[RankIndex.of(rank)];
    }

    /**
     * @param hardness     the block's destroy speed (negative means unbreakable)
     * @param obsidianTier the block is in #hogwartsmagic:bombarda_obsidian_tier
     * @param roll         a random number in [0, 1), used only for the obsidian chance
     */
    public static boolean canBreak(int rank, float hardness, boolean obsidianTier, double roll) {
        if (Float.isNaN(hardness) || hardness < 0f) {
            return false;
        }
        int i = RankIndex.of(rank);
        if (obsidianTier) {
            return roll < OBSIDIAN_CHANCE[i];
        }
        return hardness <= MAX_HARDNESS[i];
    }

    /** Full damage at the centre, falling linearly to a quarter, none from radius + 1 out. */
    public static float splashDamage(float base, double distance, double radius) {
        double reach = radius + 1.0;
        if (!(distance < reach)) {
            return 0f;
        }
        double factor = Math.max(MIN_SPLASH_FACTOR, 1.0 - Math.max(0.0, distance) / reach);
        return (float) (base * factor);
    }
}
