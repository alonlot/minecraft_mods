package com.hogwartsmagic.spell.combat;

/**
 * Which hits shatter a brittle target, and by how much. An explosion always does. A projectile does
 * unless it is fire or magic (a plain arrow counts, a fire arrow or a spell bolt does not). A melee blow
 * from a living attacker does unless it is magic or fire. Magic is damage that bypasses armour.
 */
public final class BrittleRules {
    public static final double BONUS_PER_LEVEL = 0.3;
    public static final int MAX_LEVEL = 5;

    private BrittleRules() {
    }

    public static boolean countsAsBrittleHit(boolean explosion, boolean projectile, boolean directLivingAttacker,
                                             boolean bypassesArmor, boolean fire) {
        if (explosion) {
            return true;
        }
        if (projectile) {
            return !fire && !bypassesArmor;
        }
        return directLivingAttacker && !bypassesArmor && !fire;
    }

    /** Damage multiplier for the consuming hit: +30% per level, level = amplifier + 1, capped at 5. */
    public static float bonusMultiplier(int amplifier) {
        int level = Math.max(1, Math.min(MAX_LEVEL, amplifier + 1));
        return (float) (1.0 + BONUS_PER_LEVEL * level);
    }
}
