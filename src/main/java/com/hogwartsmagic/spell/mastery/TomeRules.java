package com.hogwartsmagic.spell.mastery;

import com.hogwartsmagic.spell.SpellLootRarity;

import java.util.Set;

/**
 * The Tome of Mastery rules, kept free of Minecraft types so they are unit-tested directly.
 * A tome raises spells of its own tier or below, by exactly one rank, and a chest gives at most
 * one tome.
 */
public final class TomeRules {
    private TomeRules() {
    }

    /** Loot tables whose every chest is endgame: Rare and Epic tomes can drop. */
    public static final Set<String> ENDGAME_TABLES = Set.of(
            "hogwartsmagic:chests/gringotts_vault",
            "hogwartsmagic:chests/chamber_of_secrets",
            "minecraft:chests/trial_chambers/reward_ominous",
            "minecraft:chests/trial_chambers/reward_ominous_common",
            "minecraft:chests/trial_chambers/reward_ominous_rare",
            "minecraft:chests/trial_chambers/reward_ominous_unique"
    );

    /** Dungeon-tier vanilla tables: Rare tomes can drop, Epic cannot. */
    public static final Set<String> DUNGEON_TIER_TABLES = Set.of(
            "minecraft:chests/simple_dungeon",
            "minecraft:chests/abandoned_mineshaft",
            "minecraft:chests/desert_pyramid",
            "minecraft:chests/jungle_temple",
            "minecraft:chests/stronghold_library",
            "minecraft:chests/stronghold_corridor",
            "minecraft:chests/stronghold_crossing",
            "minecraft:chests/ancient_city",
            "minecraft:chests/ancient_city_ice_box",
            "minecraft:chests/woodland_mansion",
            "minecraft:chests/end_city_treasure",
            "minecraft:chests/bastion_treasure",
            "minecraft:chests/bastion_other",
            "minecraft:chests/bastion_bridge",
            "minecraft:chests/bastion_hoglin_stable",
            "minecraft:chests/nether_bridge",
            "minecraft:chests/buried_treasure",
            "minecraft:chests/shipwreck_treasure",
            "minecraft:chests/trial_chambers/reward",
            "minecraft:chests/trial_chambers/reward_common",
            "minecraft:chests/trial_chambers/reward_rare",
            "minecraft:chests/trial_chambers/reward_unique"
    );

    public static boolean isEndgameTable(String id) {
        return ENDGAME_TABLES.contains(id);
    }

    public static boolean isDungeonTierTable(String id) {
        return DUNGEON_TIER_TABLES.contains(id);
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
