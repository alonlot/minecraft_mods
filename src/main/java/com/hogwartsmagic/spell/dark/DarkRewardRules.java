package com.hogwartsmagic.spell.dark;

/**
 * Dark Magic rewards and deeds (Phase 3), free of Minecraft so they are unit-tested. Every reward reads the tier
 * (DarkRules.CLEAN..LOST, through DarkMagicData.getTier, so enableDarkMagic off reads Clean), never the raw number;
 * a tier outside 0..4 is clamped into it. The deed numbers (innocent +3, Horcrux +30 and floor 60, redemption -1)
 * are DarkRules constants from Phase 1; this class decides when they apply.
 */
public final class DarkRewardRules {

    /** One reward and the lowest tier it applies at. A tier keeps every reward below it. */
    public enum Reward {
        DARK_MANA_DISCOUNT(DarkRules.CORRUPTED),
        DARK_MARK_BUFF(DarkRules.CORRUPTED),
        DEATH_EATERS_NEUTRAL(DarkRules.LOST),
        MORSMORDRE_ESCORT(DarkRules.LOST);

        private final int fromTier;

        Reward(int fromTier) {
            this.fromTier = fromTier;
        }

        public int fromTier() {
            return this.fromTier;
        }

        public boolean appliesAt(int tier) {
            return clampTier(tier) >= this.fromTier;
        }
    }

    /** Corrupted and Lost: a dark spell costs 15% less mana, after the wand and mastery multipliers. */
    public static final float DARK_MANA_MULTIPLIER = 0.85f;

    /** The Morsmordre escort: 5 minutes, 4 to 6 Death Eaters. */
    public static final long ESCORT_DURATION_TICKS = 6_000L;
    public static final int ESCORT_MIN_SIZE = 4;
    public static final int ESCORT_MAX_SIZE = 6;
    /** They appear 3 to 8 blocks from the caster. */
    public static final double ESCORT_SPAWN_MIN_DISTANCE = 3.0D;
    public static final double ESCORT_SPAWN_MAX_DISTANCE = 8.0D;
    /** With no target an escort walks back when 8 blocks away and stops at 4; past 20 it drops any target. */
    public static final double ESCORT_FOLLOW_START = 8.0D;
    public static final double ESCORT_FOLLOW_STOP = 4.0D;
    public static final double ESCORT_LEASH = 20.0D;
    /** Past 32 blocks an escort Apparates to its owner. */
    public static final double ESCORT_CATCH_UP = 32.0D;
    /** The owner, expiry and catch-up checks run once a second per escort member. */
    public static final int ESCORT_CHECK_INTERVAL_TICKS = 20;
    /** A new escort dismisses the caster's old one within this many blocks. */
    public static final double ESCORT_REPLACE_RADIUS = 96.0D;

    private DarkRewardRules() {
    }

    private static int clampTier(int tier) {
        return Math.max(DarkRules.CLEAN, Math.min(DarkRules.LOST, tier));
    }

    /** The mana multiplier for a spell at a tier: 0.85 for a dark spell from Corrupted, else 1. */
    public static float manaMultiplier(String spellId, int tier) {
        return DarkRules.isDark(spellId) && Reward.DARK_MANA_DISCOUNT.appliesAt(tier) ? DARK_MANA_MULTIPLIER : 1.0f;
    }

    /**
     * SpellCosts.manaCost: the base times the wand and mastery multipliers (computed by the caller in that order),
     * times the dark multiplier, rounded, never below 1.
     */
    public static int manaCost(float baseWandMastery, float darkMultiplier) {
        return Math.max(1, Math.round(baseWandMastery * darkMultiplier));
    }

    public static boolean darkMarkBuffs(int tier) {
        return Reward.DARK_MARK_BUFF.appliesAt(tier);
    }

    public static boolean deathEatersNeutral(int tier) {
        return Reward.DEATH_EATERS_NEUTRAL.appliesAt(tier);
    }

    public static boolean morsmordreEscort(int tier) {
        return Reward.MORSMORDRE_ESCORT.appliesAt(tier);
    }

    /** The escort size for a roll in 0..(MAX - MIN); any other roll wraps into it. */
    public static int escortSize(int roll) {
        return ESCORT_MIN_SIZE + Math.floorMod(roll, ESCORT_MAX_SIZE - ESCORT_MIN_SIZE + 1);
    }

    public static long escortUntil(long now) {
        return now + ESCORT_DURATION_TICKS;
    }

    /** Still escorting: before the end, and the end is no more than 5 minutes away (a bad saved value ends it). */
    public static boolean escortActive(long now, long until) {
        return now < until && until - now <= ESCORT_DURATION_TICKS;
    }

    /** One tick in ESCORT_CHECK_INTERVAL_TICKS, spread over the escort by a per-entity phase key (the entity id). */
    public static boolean dueToEscortCheck(long tickCount, long phaseKey) {
        return Math.floorMod(tickCount + phaseKey, (long) ESCORT_CHECK_INTERVAL_TICKS) == 0L;
    }

    public static boolean beyondLeash(double distanceSqr) {
        return distanceSqr > ESCORT_LEASH * ESCORT_LEASH;
    }

    /** Walk back to the owner: always past the leash; otherwise only with no target and more than 8 blocks away. */
    public static boolean escortStartsFollowing(double distanceSqr, boolean hasTarget) {
        return beyondLeash(distanceSqr) || (!hasTarget && distanceSqr > ESCORT_FOLLOW_START * ESCORT_FOLLOW_START);
    }

    /** Keep walking: always past the leash; otherwise until a target appears or within 4 blocks. */
    public static boolean escortKeepsFollowing(double distanceSqr, boolean hasTarget) {
        return beyondLeash(distanceSqr) || (!hasTarget && distanceSqr > ESCORT_FOLLOW_STOP * ESCORT_FOLLOW_STOP);
    }

    public static boolean needsCatchUp(double distanceSqr) {
        return distanceSqr > ESCORT_CATCH_UP * ESCORT_CATCH_UP;
    }

    /** An innocent: a villager, a Student, a House Elf, or any mob MasteryAwards.isPassive calls passive. */
    public static boolean isInnocent(boolean villager, boolean student, boolean houseElf, boolean passive) {
        return villager || student || houseElf || passive;
    }

    /** The Darkness an Avada hit adds on top of the cast: +3 for killing an innocent, else nothing. */
    public static float innocentBonus(boolean killed, boolean innocent) {
        return killed && innocent ? DarkRules.AVADA_INNOCENT_BONUS : 0.0f;
    }

    /**
     * Who answers for a beam's deed: nobody for a reflected beam (the reflector did not choose it), the puppeteer for
     * a puppet beam, nobody for another forced body, else the caster (the Phase 1 cast rule).
     */
    public static DarkRules.Payer deedPayer(boolean reflected, boolean puppetBeam, boolean ownerForced) {
        if (reflected) {
            return DarkRules.Payer.NOBODY;
        }
        return DarkRules.payer(puppetBeam, ownerForced);
    }

    /** Darkness after forging a Horcrux: +30, and never less than the floor of 60 (capped at 100). */
    public static float afterHorcrux(float darkness) {
        return DarkRules.sanitize(Math.max(DarkRules.afterGain(darkness, DarkRules.HORCRUX_BONUS), DarkRules.HORCRUX_FLOOR));
    }

    /** The floor: 60 while the player's Horcrux exists (bound, ritual finished), else none. */
    public static float floor(boolean horcruxBound) {
        return horcruxBound ? DarkRules.HORCRUX_FLOOR : DarkRules.NO_FLOOR;
    }

    /** Hurt before the heal: alive and below full health. */
    public static boolean wasHurt(float health, float maxHealth) {
        return health > 0.0f && health < maxHealth;
    }

    /**
     * A Heal redeems only when a player cast it of their own will (not a puppet, not a forced body), not in creative
     * or spectator, on another living thing (not themselves, not an armour stand) that was hurt before the heal.
     */
    public static boolean healRedeems(boolean selfHeal, boolean armorStand, boolean forced, boolean earns,
                                      boolean targetWasHurt) {
        return !selfHeal && !armorStand && !forced && earns && targetWasHurt;
    }

    /** Darkness after one redemption: -1 through the floor (Phase 1 loss rule). */
    public static float afterRedemption(float darkness, float floor) {
        return DarkRules.afterLoss(darkness, DarkRules.REDEMPTION_AMOUNT, floor);
    }

    /**
     * The once-per-window cap, in ticks of play (the vanilla play-time stat, which counts only online ticks). Open with
     * no earlier redemption, once a full window has passed, or when the stat went backwards (it was reset).
     */
    public static boolean redemptionWindowOpen(boolean hasRecord, int playTicks, int lastRedemptionPlayTicks,
                                               int windowTicks) {
        if (!hasRecord || playTicks < lastRedemptionPlayTicks) {
            return true;
        }
        return (long) playTicks - lastRedemptionPlayTicks >= Math.max(1, windowTicks);
    }
}
