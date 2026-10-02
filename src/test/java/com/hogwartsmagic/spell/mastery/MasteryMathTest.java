package com.hogwartsmagic.spell.mastery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MasteryMathTest {
    private static final int[] T = {0, 30, 120, 350};

    @Test
    void rankForXpWalksTheThresholds() {
        assertEquals(1, MasteryMath.rankForXp(0f, T));
        assertEquals(1, MasteryMath.rankForXp(29.9f, T));
        assertEquals(2, MasteryMath.rankForXp(30f, T));
        assertEquals(3, MasteryMath.rankForXp(120f, T));
        assertEquals(4, MasteryMath.rankForXp(350f, T));
        assertEquals(4, MasteryMath.rankForXp(99999f, T));
    }

    @Test
    void xpToNextRankIsMinusOneAtMaster() {
        assertEquals(30f, MasteryMath.xpToNextRank(0f, T), 1e-4);
        assertEquals(10f, MasteryMath.xpToNextRank(110f, T), 1e-4);
        assertEquals(-1f, MasteryMath.xpToNextRank(350f, T), 1e-4);
    }

    @Test
    void rankProgressIsFractionOfCurrentBand() {
        assertEquals(0f, MasteryMath.rankProgress(0f, T), 1e-4);
        assertEquals(0.5f, MasteryMath.rankProgress(15f, T), 1e-4);
        assertEquals(0.5f, MasteryMath.rankProgress(75f, T), 1e-4);
        assertEquals(1f, MasteryMath.rankProgress(500f, T), 1e-4);
    }

    @Test
    void xpPerCastScalesWithCooldownAndIsClamped() {
        assertEquals(1, MasteryMath.xpPerCast(40));     // Expelliarmus, 2s
        assertEquals(2, MasteryMath.xpPerCast(100));    // 5s
        assertEquals(5, MasteryMath.xpPerCast(300));    // Fiendfyre, 15s
        assertEquals(20, MasteryMath.xpPerCast(12000)); // Avada, 600s
        assertEquals(1, MasteryMath.xpPerCast(0));
    }

    @Test
    void scaledXpKeepsFractionsAndZeroesNonPositive() {
        assertEquals(0.25f, MasteryMath.scaledXp(1, 0.25), 1e-4);
        assertEquals(10f, MasteryMath.scaledXp(20, 0.5), 1e-4);
        assertEquals(0f, MasteryMath.scaledXp(5, 0.0), 1e-4);
        assertEquals(0f, MasteryMath.scaledXp(5, -1.0), 1e-4);
    }

    @Test
    void costMultiplierIsAdditivePerRankAboveNovice() {
        assertEquals(1.00f, MasteryMath.costMultiplier(1, 0.08), 1e-4);
        assertEquals(0.92f, MasteryMath.costMultiplier(2, 0.08), 1e-4);
        assertEquals(0.84f, MasteryMath.costMultiplier(3, 0.08), 1e-4);
        assertEquals(0.76f, MasteryMath.costMultiplier(4, 0.08), 1e-4);
        assertEquals(0.76f, MasteryMath.costMultiplier(9, 0.08), 1e-4);
        assertEquals(1.00f, MasteryMath.costMultiplier(0, 0.08), 1e-4);
    }

    @Test
    void maxManaBonusIsCapped() {
        assertEquals(0f, MasteryMath.maxManaBonus(0, 1.0, 50.0), 1e-4);
        assertEquals(12f, MasteryMath.maxManaBonus(12, 1.0, 50.0), 1e-4);
        assertEquals(50f, MasteryMath.maxManaBonus(114, 1.0, 50.0), 1e-4);
        assertEquals(0f, MasteryMath.maxManaBonus(-3, 1.0, 50.0), 1e-4);
    }

    @Test
    void romanNumerals() {
        assertEquals("I", MasteryMath.roman(1));
        assertEquals("IV", MasteryMath.roman(4));
        assertEquals("I", MasteryMath.roman(0));
    }
}
