package com.hogwartsmagic.spell.mastery;

import com.hogwartsmagic.spell.SpellLootRarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TomeRulesTest {
    private static final int[] T = {0, 30, 120, 350};
    private static final double C = 0.03;
    private static final double U = 0.015;
    private static final double R = 0.005;
    private static final double E = 0.0015;

    @Test
    void tomeRaisesItsTierAndBelow() {
        assertTrue(TomeRules.canRaise(SpellLootRarity.COMMON, SpellLootRarity.COMMON));
        assertFalse(TomeRules.canRaise(SpellLootRarity.COMMON, SpellLootRarity.UNCOMMON));
        assertTrue(TomeRules.canRaise(SpellLootRarity.UNCOMMON, SpellLootRarity.COMMON));
        assertFalse(TomeRules.canRaise(SpellLootRarity.RARE, SpellLootRarity.EPIC));
        for (SpellLootRarity spell : SpellLootRarity.values()) {
            assertTrue(TomeRules.canRaise(SpellLootRarity.EPIC, spell));
        }
    }

    @Test
    void eligibilityNeedsLearnedTierAndRoomToGrow() {
        assertTrue(TomeRules.isEligible(SpellLootRarity.RARE, SpellLootRarity.RARE, true, 3));
        assertFalse(TomeRules.isEligible(SpellLootRarity.RARE, SpellLootRarity.RARE, false, 1));
        assertFalse(TomeRules.isEligible(SpellLootRarity.EPIC, SpellLootRarity.COMMON, true, 4));
        assertFalse(TomeRules.isEligible(SpellLootRarity.COMMON, SpellLootRarity.RARE, true, 1));
    }

    @Test
    void xpJumpsToTheStartOfTheNextRank() {
        assertEquals(30f, TomeRules.xpForNextRank(0f, T), 1e-4);
        assertEquals(30f, TomeRules.xpForNextRank(29.9f, T), 1e-4);
        assertEquals(120f, TomeRules.xpForNextRank(30f, T), 1e-4);
        assertEquals(350f, TomeRules.xpForNextRank(200f, T), 1e-4);
        assertEquals(-1f, TomeRules.xpForNextRank(350f, T), 1e-4);
        assertEquals(-1f, TomeRules.xpForNextRank(9999f, T), 1e-4);
    }

    @Test
    void xpForNextRankAlwaysLandsExactlyOneRankHigher() {
        for (float xp : new float[]{0f, 15f, 30f, 100f, 120f, 349.9f}) {
            int before = MasteryMath.rankForXp(xp, T);
            int after = MasteryMath.rankForXp(TomeRules.xpForNextRank(xp, T), T);
            assertEquals(before + 1, after);
        }
    }

    @Test
    void endgameChestWalksEpicRareUncommonCommon() {
        assertEquals(SpellLootRarity.EPIC, TomeRules.pickTome(0.0, C, U, R, E, true, true));
        assertEquals(SpellLootRarity.EPIC, TomeRules.pickTome(0.0014, C, U, R, E, true, true));
        assertEquals(SpellLootRarity.RARE, TomeRules.pickTome(0.0016, C, U, R, E, true, true));
        assertEquals(SpellLootRarity.UNCOMMON, TomeRules.pickTome(0.007, C, U, R, E, true, true));
        assertEquals(SpellLootRarity.COMMON, TomeRules.pickTome(0.03, C, U, R, E, true, true));
        assertNull(TomeRules.pickTome(0.06, C, U, R, E, true, true));
    }

    @Test
    void ordinaryChestNeverRollsRareOrEpic() {
        assertEquals(SpellLootRarity.UNCOMMON, TomeRules.pickTome(0.0, C, U, R, E, false, false));
        assertEquals(SpellLootRarity.UNCOMMON, TomeRules.pickTome(0.0149, C, U, R, E, false, false));
        assertEquals(SpellLootRarity.COMMON, TomeRules.pickTome(0.015, C, U, R, E, false, false));
        assertEquals(SpellLootRarity.COMMON, TomeRules.pickTome(0.0449, C, U, R, E, false, false));
        assertNull(TomeRules.pickTome(0.045, C, U, R, E, false, false));
    }

    @Test
    void dungeonChestCanRollRareButNotEpic() {
        assertEquals(SpellLootRarity.RARE, TomeRules.pickTome(0.0, C, U, R, E, true, false));
        assertEquals(SpellLootRarity.UNCOMMON, TomeRules.pickTome(0.005, C, U, R, E, true, false));
    }

    @Test
    void zeroChancesGiveNothing() {
        assertNull(TomeRules.pickTome(0.0, 0, 0, 0, 0, true, true));
        assertNull(TomeRules.pickTome(0.0, -1, -1, -1, -1, true, true));
    }

    @Test
    void trialVaultsAndExistingTablesAreClassified() {
        assertTrue(TomeRules.isDungeonTierTable("minecraft:chests/trial_chambers/reward"));
        assertTrue(TomeRules.isDungeonTierTable("minecraft:chests/trial_chambers/reward_unique"));
        assertTrue(TomeRules.isEndgameTable("minecraft:chests/trial_chambers/reward_ominous"));
        assertTrue(TomeRules.isEndgameTable("minecraft:chests/trial_chambers/reward_ominous_rare"));
        assertFalse(TomeRules.isEndgameTable("minecraft:chests/trial_chambers/reward"));
        assertTrue(TomeRules.isDungeonTierTable("minecraft:chests/simple_dungeon"));
        assertTrue(TomeRules.isEndgameTable("hogwartsmagic:chests/gringotts_vault"));
    }
}
