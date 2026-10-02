package com.hogwartsmagic.spell.combat;

/**
 * How long a hard crowd-control effect lasts once diminishing returns, the boss rule and the PvP
 * rule are applied. Minecraft-free so it is unit-tested; {@link CrowdControl} feeds it real entities.
 *
 * <p>Call order: {@code factor = DiminishingReturns.peek(...)}, {@code ticks = scaledTicks(base, factor, ...)},
 * then {@code DiminishingReturns.commit(...)} only if {@code ticks > 0}.
 */
public final class CrowdControlMath {
    /** Bosses ({@code #hogwartsmagic:bosses}) shrug off half of every hard crowd control. */
    public static final double BOSS_MULTIPLIER = 0.5;

    private CrowdControlMath() {
    }

    /**
     * @param diminishingFactor 1.0, 0.5 or 0.0 from {@link DiminishingReturns}
     * @param pvpMultiplier     applied only when {@code pvp}; clamped to 0..1, NaN counts as 1
     * @return ticks to apply, rounded to the nearest tick; 0 means nothing lands
     */
    public static int scaledTicks(int baseTicks, double diminishingFactor, boolean boss, boolean pvp, double pvpMultiplier) {
        if (baseTicks <= 0 || !(diminishingFactor > 0.0)) {
            return 0;
        }
        double ticks = baseTicks * Math.min(1.0, diminishingFactor);
        if (boss) {
            ticks *= BOSS_MULTIPLIER;
        }
        if (pvp) {
            ticks *= Double.isNaN(pvpMultiplier) ? 1.0 : Math.max(0.0, Math.min(1.0, pvpMultiplier));
        }
        return (int) Math.round(ticks);
    }
}
