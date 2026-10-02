package com.hogwartsmagic.spell.support;

/** Reparo: how much it mends per rank, and the price of mending an item. Pure numbers. */
public final class ReparoRules {
    /** NPC casters (Students rebuilding) keep the sweep they had before mastery. */
    public static final int NPC_MAX_BLOCKS = 32;
    public static final double NPC_RADIUS = 6.0;
    public static final int ITEM_EXTRA_MANA_CAP = 20;

    private static final int[] MAX = {4, 10, 24, 48};
    private static final double[] RADIUS = {6.0, 10.0, 14.0, 20.0};

    private ReparoRules() {
    }

    public static int maxBlocks(int rank) {
        return MAX[RankIndex.of(rank)];
    }

    public static double radius(int rank) {
        return RADIUS[RankIndex.of(rank)];
    }

    public static boolean repairsItems(int rank) {
        return rank >= 3;
    }

    /** A tenth of max durability, rounded up (integer math, no float drift); 0 for an undamageable item. */
    public static int itemRepairAmount(int maxDamage) {
        if (maxDamage <= 0) {
            return 0;
        }
        return Math.max(1, (maxDamage + 9) / 10);
    }

    /** Extra mana for an item repair: one per 100 durability, rounded, 0..20 (wood 1, iron tools 3, diamond 16, netherite 20). */
    public static int itemExtraMana(int maxDamage) {
        return Math.max(0, Math.min(ITEM_EXTRA_MANA_CAP, Math.round(maxDamage / 100.0f)));
    }
}
