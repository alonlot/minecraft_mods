package com.hogwartsmagic.spell.combat;

/**
 * Which hits shatter a brittle target, and by how much. "Physical" means a hit armour could stop:
 * an explosion, a projectile, or a melee blow from a living attacker that isn't magic or fire.
 */
public final class BrittleRules {
    public static final double BONUS_PER_LEVEL = 0.3;
    public static final int MAX_LEVEL = 5;

    private BrittleRules() {
    }

    public static boolean countsAsBrittleHit(boolean explosion, boolean projectile, boolean directLivingAttacker,
                                             boolean bypassesArmor, boolean fire) {
        if (explosion || projectile) {
            return true;
        }
        return directLivingAttacker && !bypassesArmor && !fire;
    }

    /** Damage multiplier for the consuming hit: +30% per level, level = amplifier + 1, capped at 5. */
    public static float bonusMultiplier(int amplifier) {
        int level = Math.max(1, Math.min(MAX_LEVEL, amplifier + 1));
        return (float) (1.0 + BONUS_PER_LEVEL * level);
    }
}
