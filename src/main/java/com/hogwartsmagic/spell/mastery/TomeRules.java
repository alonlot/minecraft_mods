package com.hogwartsmagic.spell.mastery;

import com.hogwartsmagic.spell.SpellLootRarity;

/**
 * The Tome of Mastery rules, kept free of Minecraft types so they are unit-tested directly.
 * A tome raises spells of its own tier or below, by exactly one rank, and a chest gives at most
 * one tome.
 */
public final class TomeRules {
    private TomeRules() {
    }

    /** Tiers compare by ordinal: COMMON < UNCOMMON < RARE < EPIC. */
    public static boolean canRaise(SpellLootRarity tomeTier, SpellLootRarity spellTier) {
        return spellTier.ordinal() <= tomeTier.ordinal();
    }

    public static boolean isEligible(SpellLootRarity tomeTier, SpellLootRarity spellTier, boolean learned, int rank) {
        return learned && rank < MasteryMath.MAX_RANK && canRaise(tomeTier, spellTier);
    }

    /**
     * The XP a tome sets the spell to: the start of the next rank, so leftover progress inside
     * the current rank isn't carried over and doubled. -1 once the spell is Master.
     */
    public static float xpForNextRank(float xp, int[] thresholds) {
        int rank = MasteryMath.rankForXp(xp, thresholds);
        if (rank >= MasteryMath.MAX_RANK) {
            return -1f;
        }
        return thresholds[rank];
    }

    /**
     * One roll per chest, so each tier's chance is exactly its config value and a chest never
     * gets two tomes. Rarest first: [0, epic) is Epic, the next band Rare, then Uncommon, then
     * Common. Disallowed tiers take no band. Returns null when the roll lands past every band.
     */
    public static SpellLootRarity pickTome(double roll, double commonChance, double uncommonChance,
                                           double rareChance, double epicChance,
                                           boolean rareAllowed, boolean epicAllowed) {
        double edge = 0.0;
        if (epicAllowed) {
            edge += Math.max(0.0, epicChance);
            if (roll < edge) {
                return SpellLootRarity.EPIC;
            }
        }
        if (rareAllowed) {
            edge += Math.max(0.0, rareChance);
            if (roll < edge) {
                return SpellLootRarity.RARE;
            }
        }
        edge += Math.max(0.0, uncommonChance);
        if (roll < edge) {
            return SpellLootRarity.UNCOMMON;
        }
        edge += Math.max(0.0, commonChance);
        if (roll < edge) {
            return SpellLootRarity.COMMON;
        }
        return null;
    }
}
