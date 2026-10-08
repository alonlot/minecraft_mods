package com.hogwartsmagic.sneakoscope;

import com.hogwartsmagic.sneakoscope.SneakoscopeRules.Hit;
import com.hogwartsmagic.sneakoscope.SneakoscopeRules.Reach;
import com.hogwartsmagic.sneakoscope.SneakoscopeRules.Source;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SneakoscopeRulesTest {
    private static final int NEAR = SneakoscopeRules.DEFAULT_RANGE;
    private static final int SKY = SneakoscopeRules.DEFAULT_DARK_MARK_RANGE;

    private static int strengthOf(Hit... hits) {
        return SneakoscopeRules.strength(List.of(hits), NEAR, SKY);
    }

    @Test
    void defaultsMatchTheSpec() {
        assertEquals(16, NEAR);
        assertEquals(48, SKY);
        assertEquals(15, SneakoscopeRules.MAX_STRENGTH);
        assertEquals(20, SneakoscopeRules.CHECK_INTERVAL_TICKS);
        assertEquals(40, SneakoscopeRules.SLOWEST_WHISTLE_TICKS);
        assertEquals(10, SneakoscopeRules.FASTEST_WHISTLE_TICKS);
    }

    @Test
    void atTheSourceTheStrengthIsFifteen() {
        assertEquals(15, SneakoscopeRules.strength(0.0, 16));
        assertEquals(15, SneakoscopeRules.strength(0.5, 16));
        assertEquals(15, SneakoscopeRules.strength(0.0, 48));
        assertEquals(15, SneakoscopeRules.strength(-2.0, 16));
    }

    @Test
    void atOrBeyondTheEdgeTheStrengthIsZero() {
        assertEquals(0, SneakoscopeRules.strength(16.0, 16));
        assertEquals(0, SneakoscopeRules.strength(17.0, 16));
        assertEquals(0, SneakoscopeRules.strength(48.0, 48));
        assertEquals(0, SneakoscopeRules.strength(100.0, 48));
    }

    @Test
    void justInsideTheEdgeTheStrengthIsOne() {
        assertEquals(1, SneakoscopeRules.strength(15.9, 16));
        assertEquals(1, SneakoscopeRules.strength(47.9, 48));
    }

    @Test
    void strengthRoundsUp() {
        assertEquals(8, SneakoscopeRules.strength(8.0, 16));
        assertEquals(14, SneakoscopeRules.strength(2.0, 16));
        assertEquals(4, SneakoscopeRules.strength(12.0, 16));
        // 15 * (1 - (16/15) / 16) is exactly 14: an exact value is not pushed up by rounding noise.
        assertEquals(14, SneakoscopeRules.strength(16.0 / 15.0, 16));
    }

    @Test
    void strengthRisesAsTheSourceComesCloser() {
        int previous = 0;
        for (int i = 64; i >= 0; i--) {
            int s = SneakoscopeRules.strength(i * 0.25, 16);
            assertTrue(s >= previous, "strength fell at distance " + (i * 0.25));
            assertTrue(s >= 0 && s <= 15);
            previous = s;
        }
        assertEquals(15, previous);
    }

    @Test
    void aDarkMarkFortyBlocksUpIsWeak() {
        assertEquals(3, strengthOf(new Hit(Source.DARK_MARK, 40.0)));
    }

    @Test
    void onlyTheDarkMarkUsesTheSkyRange() {
        assertEquals(0, strengthOf(new Hit(Source.DEATH_EATER, 40.0)));
        assertEquals(0, strengthOf(new Hit(Source.DEMENTOR, 40.0)));
        assertEquals(Reach.NEAR, Source.DEATH_EATER.reach());
        assertEquals(Reach.NEAR, Source.DEMENTOR.reach());
        assertEquals(Reach.SKY, Source.DARK_MARK.reach());
        assertEquals(16, SneakoscopeRules.range(Source.DEATH_EATER, 16, 48));
        assertEquals(16, SneakoscopeRules.range(Source.DEMENTOR, 16, 48));
        assertEquals(48, SneakoscopeRules.range(Source.DARK_MARK, 16, 48));
    }

    @Test
    void theStrongestSourceWins() {
        // Dark Mark at 14 of 48 gives 11; a Death Eater at 12 of 16 gives 4. The weaker, nearer one does not lower it.
        assertEquals(11, strengthOf(new Hit(Source.DARK_MARK, 14.0), new Hit(Source.DEATH_EATER, 12.0)));
        // Dementor at 2 gives 14; Dark Mark at 30 gives 6.
        assertEquals(14, strengthOf(new Hit(Source.DEMENTOR, 2.0), new Hit(Source.DARK_MARK, 30.0)));
        // The order of the hits does not matter.
        assertEquals(11, strengthOf(new Hit(Source.DEATH_EATER, 12.0), new Hit(Source.DARK_MARK, 14.0)));
    }

    @Test
    void nothingNearIsZero() {
        assertEquals(0, strengthOf());
    }

    @Test
    void aNaNDistanceDetectsNothing() {
        assertEquals(0, SneakoscopeRules.strength(Double.NaN, 16));
        assertEquals(0, strengthOf(new Hit(Source.DEATH_EATER, Double.NaN)));
    }

    @Test
    void aBadRangeDetectsNothing() {
        assertEquals(0, SneakoscopeRules.strength(1.0, 0));
        assertEquals(0, SneakoscopeRules.strength(1.0, -5));
    }

    @Test
    void configuredRangesAreUsed() {
        assertEquals(6, SneakoscopeRules.strength(List.of(new Hit(Source.DEATH_EATER, 20.0)), 32, 48));
        assertEquals(1, SneakoscopeRules.strength(List.of(new Hit(Source.DARK_MARK, 60.0)), 16, 64));
    }

    @Test
    void whistleIntervalRunsFromFortyToTen() {
        assertEquals(40, SneakoscopeRules.whistleInterval(1));
        assertEquals(25, SneakoscopeRules.whistleInterval(8));
        assertEquals(10, SneakoscopeRules.whistleInterval(15));
        assertEquals(10, SneakoscopeRules.whistleInterval(20));
        assertEquals(0, SneakoscopeRules.whistleInterval(0));
        assertEquals(0, SneakoscopeRules.whistleInterval(-1));
    }

    @Test
    void whistleIntervalShrinksAsTheStrengthRises() {
        for (int s = 1; s < 15; s++) {
            int here = SneakoscopeRules.whistleInterval(s);
            int next = SneakoscopeRules.whistleInterval(s + 1);
            assertTrue(next < here, "interval did not shrink at strength " + s);
            assertTrue(here >= 10 && here <= 40);
        }
    }

    @Test
    void eachKeyIsCheckedOnceEveryTwentyTicks() {
        long[] keys = {0L, 7L, 19L, 123456789L, -3L};
        for (long key : keys) {
            int due = 0;
            for (long t = 1000L; t < 1020L; t++) {
                if (SneakoscopeRules.dueToCheck(t, key)) {
                    due++;
                    assertTrue(SneakoscopeRules.dueToCheck(t + 20L, key));
                }
            }
            assertEquals(1, due, "key " + key);
        }
    }

    @Test
    void blockPositionsSpreadOverMostOfTheTwentyPhases() {
        // The key is BlockPos.hashCode() in 1.21 (Vec3i): (y + z * 31) * 31 + x. BlockPos.asLong() only reached 5 phases.
        java.util.Set<Integer> phases = new java.util.HashSet<>();
        for (int x = 0; x < 20; x++) {
            for (int z = 0; z < 20; z++) {
                long key = (64 + z * 31) * 31 + x;
                phases.add((int) Math.floorMod(key, (long) SneakoscopeRules.CHECK_INTERVAL_TICKS));
            }
        }
        assertTrue(phases.size() >= 15, "only " + phases.size() + " phases");
    }

    @Test
    void aPlacedSneakoscopeWhistlesOnlyWhenItStartsDetecting() {
        assertTrue(SneakoscopeRules.whistlesOnRise(0, 1));
        assertTrue(SneakoscopeRules.whistlesOnRise(0, 15));
        assertFalse(SneakoscopeRules.whistlesOnRise(3, 5));
        assertFalse(SneakoscopeRules.whistlesOnRise(5, 0));
        assertFalse(SneakoscopeRules.whistlesOnRise(0, 0));
    }

    @Test
    void spinningMeansAnySignal() {
        assertFalse(SneakoscopeRules.spinning(0));
        assertTrue(SneakoscopeRules.spinning(1));
        assertTrue(SneakoscopeRules.spinning(15));
    }

    @Test
    void nextWhistleIsImmediateWhenDetectionStarts() {
        assertEquals(100L, SneakoscopeRules.nextWhistle(100L, Long.MAX_VALUE, 0, 5));
    }

    @Test
    void nextWhistleComesSoonerWhenTheSourceComesCloser() {
        assertEquals(110L, SneakoscopeRules.nextWhistle(100L, 130L, 1, 15));
        assertEquals(105L, SneakoscopeRules.nextWhistle(100L, 105L, 1, 15));
    }

    @Test
    void nextWhistleKeepsItsTimeWhenTheSourceMovesAway() {
        assertEquals(110L, SneakoscopeRules.nextWhistle(100L, 110L, 15, 1));
    }

    @Test
    void nextWhistleStopsWhenNothingIsNear() {
        assertEquals(Long.MAX_VALUE, SneakoscopeRules.nextWhistle(100L, 110L, 5, 0));
    }

    @Test
    void aDarkPlayerUsesTheNearRange() {
        assertEquals(Reach.NEAR, Source.DARK_PLAYER.reach());
        assertEquals(16, SneakoscopeRules.range(Source.DARK_PLAYER, 16, 48));
        assertEquals(8, strengthOf(new Hit(Source.DARK_PLAYER, 8.0)));
        assertEquals(0, strengthOf(new Hit(Source.DARK_PLAYER, 40.0)));
    }

    @Test
    void onlyCorruptedOrLostPlayersInSurvivalAreDetected() {
        assertEquals(com.hogwartsmagic.spell.dark.DarkRules.CORRUPTED, SneakoscopeRules.DARK_PLAYER_MIN_TIER);
        assertFalse(SneakoscopeRules.detectsPlayer(true, true, false, 0));
        assertFalse(SneakoscopeRules.detectsPlayer(true, true, false, 1));
        assertFalse(SneakoscopeRules.detectsPlayer(true, true, false, 2));
        assertTrue(SneakoscopeRules.detectsPlayer(true, true, false, 3));
        assertTrue(SneakoscopeRules.detectsPlayer(true, true, false, 4));
        assertFalse(SneakoscopeRules.detectsPlayer(true, true, true, 4));
        assertFalse(SneakoscopeRules.detectsPlayer(false, true, false, 4));
        assertFalse(SneakoscopeRules.detectsPlayer(true, false, false, 4));
        assertTrue(SneakoscopeRules.playersDetectable(true, true));
        assertFalse(SneakoscopeRules.playersDetectable(false, true));
        assertFalse(SneakoscopeRules.playersDetectable(true, false));
    }
}
