package com.hogwartsmagic.brewing;

/**
 * The numbers of the Phase 2 potions and of the ingredient drops, free of Minecraft so they are unit-tested. The
 * items, the thrown entity, the loot modifier and the petrification code read them from here.
 */
public final class PotionRules {
    /** Essence of Dittany heals this much at once, after curing a Cursed Wound. */
    public static final float DITTANY_HEAL = 4.0f;
    /** Felix Felicis: 3 minutes of Luck III. */
    public static final int FELIX_TICKS = 20 * 60 * 3;
    public static final int FELIX_LUCK_AMPLIFIER = 2;
    /** Mastery XP while lucky (+25%). */
    public static final double FELIX_XP_MULTIPLIER = 1.25;
    /** A drink of Mandrake Draught: the basilisk's gaze cannot build on the drinker for 8 seconds. */
    public static final int MANDRAKE_GAZE_WARD_TICKS = 20 * 8;
    /** A cured stone player gets Slowness I for 5 seconds. */
    public static final int CURE_SLOWNESS_TICKS = 20 * 5;
    /** Draught of Living Death: base stun, before CrowdControl's rules. */
    public static final int LIVING_DEATH_STUN_TICKS = 20 * 20;
    /** Both splash potions reach this far (a sphere). */
    public static final double SPLASH_RADIUS = 4.0;

    public static final double VALERIAN_RUIN_CAMP_CHANCE = 0.25;
    public static final double ASHWINDER_DEATH_EATER_CAMP_CHANCE = 0.10;
    public static final double ASHWINDER_CHAMBER_CHANCE = 0.15;
    public static final double ASHWINDER_BLAZE_CHANCE = 0.05;

    private PotionRules() {
    }

    /** The mastery XP multiplier for a caster whose Luck amplifier is this (-1 without Luck). Luck III or more is Felix. */
    public static double felixXpMultiplier(int luckAmplifier) {
        return luckAmplifier >= FELIX_LUCK_AMPLIFIER ? FELIX_XP_MULTIPLIER : 1.0;
    }

    /** An entity this far (squared) from the impact is splashed. */
    public static boolean inSplash(double distanceSqr) {
        return distanceSqr <= SPLASH_RADIUS * SPLASH_RADIUS;
    }

    /** A roll in [0, 1) lands when it is below the chance. */
    public static boolean rolls(double roll, double chance) {
        return roll < chance;
    }
}
