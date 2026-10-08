package com.hogwartsmagic.spell.dark;

import com.hogwartsmagic.spell.dark.DarkAdvancementRules.Outcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkAdvancementRulesTest {

    @Test
    void reachingLostGrantsATornSoulAndRemembersIt() {
        Outcome outcome = DarkAdvancementRules.afterWrite(true, DarkRules.LOST, false);
        assertTrue(outcome.grantTornSoul());
        assertFalse(outcome.grantRedemption());
        assertTrue(outcome.wasLost());
        assertTrue(outcome.flagChanged(false));
    }

    @Test
    void everyWriteAtLostStillGrantsATornSoul() {
        Outcome outcome = DarkAdvancementRules.afterWrite(true, DarkRules.LOST, true);
        assertTrue(outcome.grantTornSoul());
        assertFalse(outcome.grantRedemption());
        assertTrue(outcome.wasLost());
        assertFalse(outcome.flagChanged(true));
    }

    @Test
    void cleanAfterLostGrantsRedemptionAndForgets() {
        Outcome outcome = DarkAdvancementRules.afterWrite(true, DarkRules.CLEAN, true);
        assertFalse(outcome.grantTornSoul());
        assertTrue(outcome.grantRedemption());
        assertFalse(outcome.wasLost());
        assertTrue(outcome.flagChanged(true));
    }

    @Test
    void cleanWithoutHavingBeenLostGrantsNothing() {
        Outcome outcome = DarkAdvancementRules.afterWrite(true, DarkRules.CLEAN, false);
        assertEquals(new Outcome(false, false, false), outcome);
        assertFalse(outcome.flagChanged(false));
    }

    @Test
    void theTiersBetweenKeepTheFlag() {
        for (int tier = DarkRules.SHADOWED; tier <= DarkRules.CORRUPTED; tier++) {
            assertEquals(new Outcome(false, false, true), DarkAdvancementRules.afterWrite(true, tier, true), "tier " + tier);
            assertEquals(new Outcome(false, false, false), DarkAdvancementRules.afterWrite(true, tier, false), "tier " + tier);
        }
    }

    @Test
    void theToggleOffGrantsNothingAndKeepsTheFlag() {
        for (int tier = DarkRules.CLEAN; tier <= DarkRules.LOST; tier++) {
            assertEquals(new Outcome(false, false, true), DarkAdvancementRules.afterWrite(false, tier, true), "tier " + tier);
            assertEquals(new Outcome(false, false, false), DarkAdvancementRules.afterWrite(false, tier, false), "tier " + tier);
        }
    }

    @Test
    void tiersOutsideTheRangeAreClamped() {
        assertEquals(new Outcome(true, false, true), DarkAdvancementRules.afterWrite(true, 7, false));
        assertEquals(new Outcome(false, true, false), DarkAdvancementRules.afterWrite(true, -3, true));
    }

    @Test
    void aFullJourneyGrantsEachOnceAndASecondNeedsLostAgain() {
        float[] path = {0.0f, 20.0f, 40.0f, 60.0f, 79.99f, 80.0f, 100.0f, 80.0f, 79.99f, 40.0f, 20.0f, 19.99f, 0.0f,
                25.0f, 19.0f};
        boolean wasLost = false;
        int tornSoul = 0;
        int redemption = 0;
        int redeemedAt = -1;
        for (int i = 0; i < path.length; i++) {
            Outcome outcome = DarkAdvancementRules.afterWrite(true, DarkRules.effectiveTier(true, path[i]), wasLost);
            if (outcome.grantTornSoul()) {
                tornSoul++;
            }
            if (outcome.grantRedemption()) {
                redemption++;
                redeemedAt = i;
            }
            wasLost = outcome.wasLost();
        }
        assertEquals(3, tornSoul, "80, 100 and 80 each leave the player Lost");
        assertEquals(1, redemption);
        assertEquals(11, redeemedAt, "19.99 is the first Clean value after Lost");
        assertFalse(wasLost);
    }

    @Test
    void idsAndKeyArePinned() {
        assertEquals("hp/torn_soul", DarkAdvancementRules.TORN_SOUL);
        assertEquals("hp/redemption", DarkAdvancementRules.REDEMPTION);
        assertEquals("DarkMagicWasLost", DarkAdvancementRules.KEY_WAS_LOST);
    }
}
