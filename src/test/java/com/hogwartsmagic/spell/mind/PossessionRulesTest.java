package com.hogwartsmagic.spell.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PossessionRulesTest {
    @Test
    void twentySecondsOrFiveOnABoss() {
        // maxTicks(false) is the value for both players and mobs; only bosses get the short leash.
        assertEquals(400, PossessionRules.maxTicks(false));
        assertEquals(100, PossessionRules.maxTicks(true));
        assertFalse(PossessionRules.expired(399, 400));
        assertTrue(PossessionRules.expired(400, 400));
    }

    @Test
    void releasingMeansTenTicksWithoutInput() {
        assertFalse(PossessionRules.heartbeatLapsed(110, 100));
        assertTrue(PossessionRules.heartbeatLapsed(111, 100));
    }

    @Test
    void resistingOpensAfterEightSecondsInHalfSecondWindows() {
        assertFalse(PossessionRules.resistOpen(159));
        assertTrue(PossessionRules.resistOpen(160));
        assertEquals(-1, PossessionRules.resistWindow(159));
        assertEquals(0, PossessionRules.resistWindow(160));
        assertEquals(0, PossessionRules.resistWindow(169));
        assertEquals(1, PossessionRules.resistWindow(170));
    }

    @Test
    void aBossPossessionIsTooShortToResist() {
        assertFalse(PossessionRules.resistOpen(PossessionRules.BOSS_MAX_TICKS - 1));
    }

    @Test
    void sixPressesGiveATwentyPercentRoll() {
        assertFalse(PossessionRules.breaksFree(5, 0.0));
        assertTrue(PossessionRules.breaksFree(6, 0.19));
        assertFalse(PossessionRules.breaksFree(6, 0.2));
        assertTrue(PossessionRules.breaksFree(12, 0.0));
    }

    @Test
    void pressesAreCappedSoAMacroCantEscapeAtOnce() {
        assertEquals(12, PossessionRules.clampPresses(500));
        assertEquals(0, PossessionRules.clampPresses(-3));
        assertTrue(PossessionRules.reportAllowed(108, 100));
        assertFalse(PossessionRules.reportAllowed(107, 100));
    }

    @Test
    void fourDamageThrowsTheCasterOut() {
        assertFalse(PossessionRules.ejects(3.99f));
        assertTrue(PossessionRules.ejects(4.0f));
    }

    @Test
    void fortyEightBlocksIsTooFar() {
        assertFalse(PossessionRules.tooFar(48.0 * 48.0));
        assertTrue(PossessionRules.tooFar(48.0 * 48.0 + 1.0));
    }

    @Test
    void inputIsSanitised() {
        assertEquals(1.0f, PossessionRules.axis(7.0f), 1e-6);
        assertEquals(-1.0f, PossessionRules.axis(-7.0f), 1e-6);
        assertEquals(0.0f, PossessionRules.axis(Float.NaN), 1e-6);
        assertEquals(90.0f, PossessionRules.pitch(300.0f), 1e-6);
        assertEquals(0.0f, PossessionRules.pitch(Float.NaN), 1e-6);
        assertEquals(-170.0f, PossessionRules.yaw(190.0f), 1e-4);
        assertEquals(170.0f, PossessionRules.yaw(-190.0f), 1e-4);
        assertEquals(0.0f, PossessionRules.yaw(Float.POSITIVE_INFINITY), 1e-6);
        assertEquals(0.0f, PossessionRules.yaw(Float.NEGATIVE_INFINITY), 1e-6);
        assertEquals(0.0f, PossessionRules.yaw(Float.NaN), 1e-6);
        assertEquals(0.0f, PossessionRules.axis(Float.POSITIVE_INFINITY), 1e-6);
        assertEquals(0.0f, PossessionRules.axis(Float.NEGATIVE_INFINITY), 1e-6);
        assertEquals(-90.0f, PossessionRules.pitch(-300.0f), 1e-6);
        assertEquals(0.0f, PossessionRules.pitch(Float.POSITIVE_INFINITY), 1e-6);
        assertEquals(0.0f, PossessionRules.pitch(Float.NEGATIVE_INFINITY), 1e-6);
        assertEquals(-180.0f, PossessionRules.yaw(180.0f), 1e-4);
        assertEquals(-180.0f, PossessionRules.yaw(540.0f), 1e-4);
    }

    @Test
    void hotbarSlotsAreZeroToEight() {
        assertTrue(PossessionRules.validHotbarSlot(0));
        assertTrue(PossessionRules.validHotbarSlot(8));
        assertFalse(PossessionRules.validHotbarSlot(9));
        assertFalse(PossessionRules.validHotbarSlot(-1));
    }

    @Test
    void blockedSpellsCantBeCastThroughAPuppet() {
        for (String id : new String[] {"imperio", "apparition", "crucio", "protego_maxima", "fiendfyre",
                "wingardium_leviosa", "expecto_patronum"}) {
            assertTrue(PossessionRules.isPuppetBlocked(id), id);
        }
        assertFalse(PossessionRules.isPuppetBlocked("stupefy"));
        assertFalse(PossessionRules.isPuppetBlocked("avada_kedavra"));
        assertFalse(PossessionRules.isPuppetBlocked(null));
    }
}
