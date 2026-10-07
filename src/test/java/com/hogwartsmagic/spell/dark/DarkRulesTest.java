package com.hogwartsmagic.spell.dark;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class DarkRulesTest {
    private static final float EPS = 1.0e-4f;

    private static int castsToCorrupted(String spellId) {
        float darkness = 0.0f;
        int casts = 0;
        while (DarkRules.tier(darkness) < DarkRules.CORRUPTED) {
            darkness = DarkRules.afterGain(darkness, DarkRules.gain(spellId, 1.0));
            casts++;
            if (casts > 1000) {
                fail(spellId + " never reached Corrupted");
            }
        }
        return casts;
    }

    private static long quietTicksToDecay(float from, float to) {
        DarkRules.Decay d = new DarkRules.Decay(from, 0);
        long ticks = 0L;
        while (d.darkness() > to) {
            d = DarkRules.decay(d.darkness(), d.progressTicks(), DarkRules.DECAY_CHECK_INTERVAL_TICKS, true,
                    DarkRules.NO_FLOOR, DarkRules.DEFAULT_DECAY_TICKS);
            ticks += DarkRules.DECAY_CHECK_INTERVAL_TICKS;
            if (ticks > 10_000_000L) {
                fail("decay never reached " + to);
            }
        }
        return ticks;
    }

    @Test
    void darkSetIsTheNineSpellsOfTheTable() {
        assertEquals(Set.of("avada_kedavra", "fiendfyre", "morsmordre", "imperio", "crucio", "tenebris_flight",
                "sectumsempra", "dolohov_curse", "serpensortia"), DarkRules.darkSpellIds());
        assertTrue(DarkRules.isDark("crucio"));
        assertFalse(DarkRules.isDark("obliviate"));
        assertFalse(DarkRules.isDark("confundo"));
        assertFalse(DarkRules.isDark("expecto_patronum"));
        assertFalse(DarkRules.isDark(null));
    }

    @Test
    void gainTableMatchesTheSpec() {
        assertEquals(6.0f, DarkRules.baseGain("avada_kedavra"), EPS);
        assertEquals(2.5f, DarkRules.baseGain("fiendfyre"), EPS);
        assertEquals(2.5f, DarkRules.baseGain("morsmordre"), EPS);
        assertEquals(1.5f, DarkRules.baseGain("imperio"), EPS);
        assertEquals(1.2f, DarkRules.baseGain("crucio"), EPS);
        assertEquals(0.6f, DarkRules.baseGain("tenebris_flight"), EPS);
        assertEquals(0.4f, DarkRules.baseGain("sectumsempra"), EPS);
        assertEquals(0.4f, DarkRules.baseGain("dolohov_curse"), EPS);
        assertEquals(0.3f, DarkRules.baseGain("serpensortia"), EPS);
        assertEquals(0.0f, DarkRules.baseGain("stupefy"), EPS);
        assertEquals(0.0f, DarkRules.baseGain(null), EPS);
    }

    @Test
    void castsFromCleanToCorruptedMatchTheTable() {
        assertEquals(10, castsToCorrupted("avada_kedavra"));
        assertEquals(24, castsToCorrupted("fiendfyre"));
        assertEquals(24, castsToCorrupted("morsmordre"));
        assertEquals(40, castsToCorrupted("imperio"));
        assertEquals(50, castsToCorrupted("crucio"));
        assertEquals(100, castsToCorrupted("tenebris_flight"));
        assertEquals(150, castsToCorrupted("sectumsempra"));
        assertEquals(150, castsToCorrupted("dolohov_curse"));
        assertEquals(200, castsToCorrupted("serpensortia"));
    }

    @Test
    void gainMultiplierScalesTheWholeTable() {
        for (String id : DarkRules.darkSpellIds()) {
            float base = DarkRules.baseGain(id);
            assertTrue(base > 0.0f, id);
            assertEquals(base * 2.0f, DarkRules.gain(id, 2.0), EPS, id);
            assertEquals(base * 0.5f, DarkRules.gain(id, 0.5), EPS, id);
            assertEquals(base, DarkRules.gain(id, 1.0), EPS, id);
        }
        assertEquals(12.0f, DarkRules.gain("avada_kedavra", 2.0), EPS);
        assertEquals(3.0f, DarkRules.gain("avada_kedavra", 0.5), EPS);
        assertEquals(0.15f, DarkRules.gain("serpensortia", 0.5), EPS);
        assertEquals(0.0f, DarkRules.gain("avada_kedavra", 0.0), EPS);
        assertEquals(0.0f, DarkRules.gain("avada_kedavra", -1.0), EPS);
        assertEquals(0.0f, DarkRules.gain("avada_kedavra", Double.NaN), EPS);
        assertEquals(0.0f, DarkRules.gain("avada_kedavra", Double.POSITIVE_INFINITY), EPS);
        assertEquals(0.0f, DarkRules.gain("stupefy", 2.0), EPS);
    }

    @Test
    void tierLowerBoundsAreInclusive() {
        assertEquals(DarkRules.CLEAN, DarkRules.tier(0.0f));
        assertEquals(DarkRules.CLEAN, DarkRules.tier(19.99f));
        assertEquals(DarkRules.SHADOWED, DarkRules.tier(20.0f));
        assertEquals(DarkRules.SHADOWED, DarkRules.tier(39.99f));
        assertEquals(DarkRules.TAINTED, DarkRules.tier(40.0f));
        assertEquals(DarkRules.TAINTED, DarkRules.tier(59.99f));
        assertEquals(DarkRules.CORRUPTED, DarkRules.tier(60.0f));
        assertEquals(DarkRules.CORRUPTED, DarkRules.tier(79.99f));
        assertEquals(DarkRules.LOST, DarkRules.tier(80.0f));
        assertEquals(DarkRules.LOST, DarkRules.tier(100.0f));
        assertEquals(20.0f, DarkRules.tierLowerBound(DarkRules.SHADOWED), EPS);
        assertEquals(60.0f, DarkRules.tierLowerBound(DarkRules.CORRUPTED), EPS);
        assertEquals(80.0f, DarkRules.tierLowerBound(99), EPS);
        assertEquals(0.0f, DarkRules.tierLowerBound(-1), EPS);
    }

    @Test
    void sanitizeClampsRoundsAndRejectsGarbage() {
        assertEquals(0.0f, DarkRules.sanitize(-5.0f), EPS);
        assertEquals(100.0f, DarkRules.sanitize(150.0f), EPS);
        assertEquals(0.0f, DarkRules.sanitize(Float.NaN), EPS);
        assertEquals(0.0f, DarkRules.sanitize(Float.POSITIVE_INFINITY), EPS);
        assertEquals(0.0f, DarkRules.sanitize(Float.NEGATIVE_INFINITY), EPS);
        assertEquals(12.35f, DarkRules.sanitize(12.346f), EPS);
        assertEquals(12.34f, DarkRules.sanitize(12.344f), EPS);
        assertEquals(60.0f, DarkRules.sanitize(59.999998f), 0.0f);
    }

    @Test
    void gainAddsAndStopsAtOneHundred() {
        assertEquals(16.0f, DarkRules.afterGain(10.0f, 6.0f), EPS);
        assertEquals(100.0f, DarkRules.afterGain(99.0f, 6.0f), EPS);
        assertEquals(10.0f, DarkRules.afterGain(10.0f, -3.0f), EPS);
        assertEquals(10.0f, DarkRules.afterGain(10.0f, Float.NaN), EPS);
        assertEquals(10.0f, DarkRules.afterGain(10.0f, Float.POSITIVE_INFINITY), EPS);
    }

    @Test
    void lossStopsAtTheFloorAndNeverRaisesToIt() {
        assertEquals(60.0f, DarkRules.afterLoss(65.0f, 10.0f, DarkRules.HORCRUX_FLOOR), EPS);
        assertEquals(55.0f, DarkRules.afterLoss(55.0f, 1.0f, DarkRules.HORCRUX_FLOOR), EPS);
        assertEquals(0.0f, DarkRules.afterLoss(5.0f, 10.0f, DarkRules.NO_FLOOR), EPS);
        assertEquals(49.0f, DarkRules.afterLoss(50.0f, 1.0f, DarkRules.NO_FLOOR), EPS);
        assertEquals(50.0f, DarkRules.afterLoss(50.0f, -1.0f, DarkRules.NO_FLOOR), EPS);
        assertEquals(50.0f, DarkRules.afterLoss(50.0f, Float.NaN, DarkRules.NO_FLOOR), EPS);
    }

    @Test
    void addPicksGainOrLossBySign() {
        assertEquals(53.0f, DarkRules.add(50.0f, 3.0f, DarkRules.NO_FLOOR), EPS);
        assertEquals(49.0f, DarkRules.add(50.0f, -1.0f, DarkRules.NO_FLOOR), EPS);
        assertEquals(60.0f, DarkRules.add(61.0f, -5.0f, DarkRules.HORCRUX_FLOOR), EPS);
        assertEquals(50.0f, DarkRules.add(50.0f, 0.0f, DarkRules.NO_FLOOR), EPS);
        assertEquals(50.0f, DarkRules.add(50.0f, Float.NaN, DarkRules.NO_FLOOR), EPS);
    }

    @Test
    void quietAfterTenMinutesWithoutADarkCast() {
        assertTrue(DarkRules.quiet(12_000L, 0L));
        assertFalse(DarkRules.quiet(11_999L, 0L));
        assertTrue(DarkRules.quiet(112_000L, 100_000L));
        assertFalse(DarkRules.quiet(100_500L, 100_000L));
        // The clock is behind the last cast (another world's save): never strand the player.
        assertTrue(DarkRules.quiet(100L, 5_000L));
    }

    @Test
    void decayTakesOnePointPerThirtyMinutesOfQuietPlay() {
        DarkRules.Decay d = new DarkRules.Decay(50.0f, 0);
        for (int i = 0; i < 179; i++) {
            d = DarkRules.decay(d.darkness(), d.progressTicks(), 200, true, DarkRules.NO_FLOOR, 36_000);
        }
        assertEquals(50.0f, d.darkness(), EPS);
        assertEquals(35_800, d.progressTicks());
        d = DarkRules.decay(d.darkness(), d.progressTicks(), 200, true, DarkRules.NO_FLOOR, 36_000);
        assertEquals(49.0f, d.darkness(), EPS);
        assertEquals(0, d.progressTicks());
    }

    @Test
    void decayFromOneHundredToZeroTakesFiftyHours() {
        assertEquals(3_600_000L, quietTicksToDecay(100.0f, 0.0f)); // 50 hours
        assertEquals(2_160_000L, quietTicksToDecay(60.0f, 0.0f));  // Corrupted to Clean-at-zero: 30 hours
        assertEquals(DarkRules.CLEAN, DarkRules.tier(DarkRules.decay(20.5f, 35_900, 200, true,
                DarkRules.NO_FLOOR, 36_000).darkness()));
    }

    @Test
    void noDecayWhileADarkCastIsRecent() {
        DarkRules.Decay d = DarkRules.decay(50.0f, 1_000, 200, false, DarkRules.NO_FLOOR, 36_000);
        assertEquals(50.0f, d.darkness(), EPS);
        assertEquals(0, d.progressTicks());
    }

    @Test
    void decayStopsAtTheFloor() {
        DarkRules.Decay d = DarkRules.decay(60.5f, 35_900, 200, true, DarkRules.HORCRUX_FLOOR, 36_000);
        assertEquals(60.0f, d.darkness(), EPS);
        assertEquals(0, d.progressTicks());
        d = DarkRules.decay(60.0f, 5_000, 200, true, DarkRules.HORCRUX_FLOOR, 36_000);
        assertEquals(60.0f, d.darkness(), EPS);
        assertEquals(0, d.progressTicks());
        d = DarkRules.decay(40.0f, 0, 200, true, DarkRules.HORCRUX_FLOOR, 36_000);
        assertEquals(40.0f, d.darkness(), EPS);
        assertEquals(0, d.progressTicks());
        d = DarkRules.decay(0.0f, 0, 200, true, DarkRules.NO_FLOOR, 36_000);
        assertEquals(0.0f, d.darkness(), EPS);
        assertEquals(0, d.progressTicks());
    }

    @Test
    void aLoweredDecayPeriodNeverDropsManyPointsAtOnce() {
        DarkRules.Decay d = DarkRules.decay(50.0f, 30_000, 200, true, DarkRules.NO_FLOOR, 200);
        assertEquals(49.0f, d.darkness(), EPS);
        assertEquals(199, d.progressTicks());
        // A tester period under one check drops one point per period inside the check.
        d = DarkRules.decay(50.0f, 0, 200, true, DarkRules.NO_FLOOR, 20);
        assertEquals(40.0f, d.darkness(), EPS);
        assertEquals(0, d.progressTicks());
    }

    @Test
    void decayCheckRunsOnceEveryTwoHundredTicks() {
        for (long id : new long[]{0L, 7L, 199L, 12_345L, -3L}) {
            int due = 0;
            for (long t = 1_000L; t < 1_200L; t++) {
                if (DarkRules.dueToDecayCheck(t, id)) {
                    due++;
                }
            }
            assertEquals(1, due, "id " + id);
        }
        assertEquals(200, DarkRules.DECAY_CHECK_INTERVAL_TICKS);
    }

    @Test
    void puppetCastsChargeThePuppeteerAndCreativeEarnsNothing() {
        assertEquals(DarkRules.Payer.PUPPETEER, DarkRules.payer(true, true));
        assertEquals(DarkRules.Payer.PUPPETEER, DarkRules.payer(true, false));
        assertEquals(DarkRules.Payer.NOBODY, DarkRules.payer(false, true));
        assertEquals(DarkRules.Payer.BODY, DarkRules.payer(false, false));
        assertTrue(DarkRules.earns(false, false));
        assertFalse(DarkRules.earns(true, false));
        assertFalse(DarkRules.earns(false, true));
    }

    @Test
    void disabledDarkMagicReadsAsClean() {
        assertEquals(0.0f, DarkRules.effective(false, 90.0f), EPS);
        assertEquals(DarkRules.CLEAN, DarkRules.effectiveTier(false, 90.0f));
        assertEquals(90.0f, DarkRules.effective(true, 90.0f), EPS);
        assertEquals(DarkRules.LOST, DarkRules.effectiveTier(true, 90.0f));
    }

    @Test
    void tierChangeDirection() {
        assertEquals(1, DarkRules.tierChange(DarkRules.CLEAN, DarkRules.SHADOWED));
        assertEquals(1, DarkRules.tierChange(DarkRules.CLEAN, DarkRules.LOST));
        assertEquals(-1, DarkRules.tierChange(DarkRules.CORRUPTED, DarkRules.TAINTED));
        assertEquals(0, DarkRules.tierChange(DarkRules.TAINTED, DarkRules.TAINTED));
    }

    @Test
    void tierKeysAndColours() {
        assertEquals("gui.hogwartsmagic.dark.tier.clean", DarkRules.tierKey(DarkRules.CLEAN));
        assertEquals("gui.hogwartsmagic.dark.tier.shadowed", DarkRules.tierKey(DarkRules.SHADOWED));
        assertEquals("gui.hogwartsmagic.dark.tier.tainted", DarkRules.tierKey(DarkRules.TAINTED));
        assertEquals("gui.hogwartsmagic.dark.tier.corrupted", DarkRules.tierKey(DarkRules.CORRUPTED));
        assertEquals("gui.hogwartsmagic.dark.tier.lost", DarkRules.tierKey(DarkRules.LOST));
        assertEquals("gui.hogwartsmagic.dark.tier.clean", DarkRules.tierKey(-1));
        assertEquals("gui.hogwartsmagic.dark.tier.lost", DarkRules.tierKey(9));
        assertEquals(0xFFFFFFFF, DarkRules.tierColor(DarkRules.CLEAN));
        assertEquals(0xFFCDE8CD, DarkRules.tierColor(DarkRules.SHADOWED));
        assertEquals(0xFF98CC98, DarkRules.tierColor(DarkRules.TAINTED));
        assertEquals(0xFF5CAE5C, DarkRules.tierColor(DarkRules.CORRUPTED));
        assertEquals(0xFF2E8B3A, DarkRules.tierColor(DarkRules.LOST));
        assertEquals(DarkRules.MANA_BAR_COLOR, DarkRules.manaBarColor(DarkRules.TAINTED));
        assertEquals(DarkRules.MANA_BAR_DARK_COLOR, DarkRules.manaBarColor(DarkRules.CORRUPTED));
        assertEquals(DarkRules.MANA_BAR_DARK_COLOR, DarkRules.manaBarColor(DarkRules.LOST));
        assertEquals(0xFF00AAFF, DarkRules.MANA_BAR_COLOR);
    }

    @Test
    void deedAndRedemptionNumbersMatchTheSpec() {
        assertEquals(3.0f, DarkRules.AVADA_INNOCENT_BONUS, EPS);
        assertEquals(30.0f, DarkRules.HORCRUX_BONUS, EPS);
        assertEquals(60.0f, DarkRules.HORCRUX_FLOOR, EPS);
        assertEquals(0.0f, DarkRules.NO_FLOOR, EPS);
        assertEquals(1.0f, DarkRules.REDEMPTION_AMOUNT, EPS);
        assertEquals(36_000, DarkRules.DEFAULT_REDEMPTION_TICKS);
        assertEquals(36_000, DarkRules.DEFAULT_DECAY_TICKS);
        assertEquals(12_000L, DarkRules.QUIET_TICKS);
        assertEquals(1.0f, DarkRules.DECAY_STEP, EPS);
        assertEquals(0.0f, DarkRules.MIN_DARKNESS, EPS);
        assertEquals(100.0f, DarkRules.MAX_DARKNESS, EPS);
    }
}
