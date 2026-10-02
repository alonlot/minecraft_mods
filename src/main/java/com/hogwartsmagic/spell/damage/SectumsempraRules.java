package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.spell.combat.BleedRules;

/** Sectumsempra: a cut plus bleeding stacks capped by rank; at Master a hit tears every open wound. */
public final class SectumsempraRules {
    public static final int BLEED_TICKS = 120;
    public static final float DETONATE_DAMAGE_PER_STACK = 2.0f;
    private static final float[] DAMAGE = {5f, 6f, 7f, 8f};
    private static final int[] MAX_STACKS = {1, 2, 3, 3};

    private SectumsempraRules() {
    }

    public static float damage(int rank) {
        return DAMAGE[RankIndex.of(rank)];
    }

    public static int maxStacks(int rank) {
        return Math.min(BleedRules.MAX_STACKS, MAX_STACKS[RankIndex.of(rank)]);
    }

    public static boolean detonates(int rank) {
        return RankIndex.of(rank) == 3;
    }

    /** One more stack up to the rank cap; stacks someone else already put on the target are never lowered. */
    public static int nextStacks(int rank, int currentStacks) {
        int current = Math.max(0, currentStacks);
        int capped = Math.min(maxStacks(rank), current + 1);
        return Math.min(BleedRules.MAX_STACKS, Math.max(current, capped));
    }

    /** Extra damage at Master for the stacks already on the target when the hit lands. */
    public static float burstDamage(int rank, int currentStacks) {
        if (!detonates(rank) || currentStacks <= 0) {
            return 0f;
        }
        return DETONATE_DAMAGE_PER_STACK * Math.min(BleedRules.MAX_STACKS, currentStacks);
    }
}
