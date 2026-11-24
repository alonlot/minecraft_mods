package com.hogwartsmagic.entity;

import static com.hogwartsmagic.entity.GnomeRules.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GnomeRulesTest {
    private static final double EPS = 1e-6;

    @Test
    void throwSpeedRisesWithSpinAndIsCapped() {
        assertEquals(MIN_THROW_SPEED, throwSpeed(0), EPS);
        assertEquals(MAX_THROW_SPEED, throwSpeed(FULL_SPIN_TICKS), EPS);
        assertEquals(MAX_THROW_SPEED, throwSpeed(10_000), EPS);
        for (int t = 0; t < FULL_SPIN_TICKS; t++) {
            assertTrue(throwSpeed(t + 1) > throwSpeed(t));
        }
    }

    @Test
    void aFullSpinFlingsAboutTwentyFiveBlocksAndNoFurther() {
        double max = 0;
        for (int e = 10; e <= 50; e++) {
            double d = flightDistance(throwSpeed(FULL_SPIN_TICKS), Math.toRadians(e), THROW_HEIGHT);
            assertTrue(d <= 26.0, "elevation " + e + " flew " + d);
            max = Math.max(max, d);
        }
        assertTrue(max >= 23.0, "best distance " + max);
    }

    @Test
    void aBriefSpinIsNotEnoughToBeRidOfIt() {
        double rad45 = Math.toRadians(45);
        assertTrue(flightDistance(throwSpeed(20), rad45, THROW_HEIGHT) < GONE_DISTANCE);
        assertTrue(flightDistance(throwSpeed(45), rad45, THROW_HEIGHT) >= GONE_DISTANCE);
        assertTrue(flightDistance(throwSpeed(0), launchElevationRadians(0), THROW_HEIGHT) < 4.0);
    }

    @Test
    void launchElevationAddsLiftAndClamps() {
        assertEquals(Math.toRadians(15), launchElevationRadians(0), EPS);
        assertEquals(Math.toRadians(35), launchElevationRadians(-20), EPS);
        assertEquals(Math.toRadians(50), launchElevationRadians(-90), EPS);
        assertEquals(Math.toRadians(10), launchElevationRadians(90), EPS);
    }

    @Test
    void goneForGoodFromTwelveBlocks() {
        assertFalse(goneForGood(11.99));
        assertTrue(goneForGood(12.0));
    }

    @Test
    void onlyAnUnspunGnomeBitesAndOnlyAfterTheDelay() {
        assertFalse(biteDue(59, false));
        assertTrue(biteDue(60, false));
        assertFalse(biteDue(500, true));
    }

    @Test
    void theSpinSpeedsUpThenHolds() {
        assertEquals(0, spinAngleDegrees(0), EPS);
        for (int t = 0; t < 200; t++) {
            assertTrue(spinAngleDegrees(t + 1) > spinAngleDegrees(t));
        }
        assertEquals(MAX_SPIN_DEG_PER_TICK,
                spinAngleDegrees(FULL_SPIN_TICKS + 1) - spinAngleDegrees(FULL_SPIN_TICKS), EPS);
        assertTrue(spinAngleDegrees(1) - spinAngleDegrees(0) < MIN_SPIN_DEG_PER_TICK + 1);
    }

    @Test
    void theOrbitKeepsItsRadiusAndStartsInFront() {
        for (float yaw : new float[] {0, -90, 37}) {
            for (double t : new double[] {0, 13.5, 90}) {
                double[] o = spinOffset(yaw, t);
                assertEquals(SPIN_RADIUS, Math.hypot(o[0], o[2]), EPS);
                assertEquals(SPIN_HEIGHT, o[1], EPS);
            }
        }
        double[] o = spinOffset(0, 0);
        assertArrayEquals(new double[] {0, SPIN_HEIGHT, SPIN_RADIUS}, o, EPS);
    }

    @Test
    void theHandIsForwardAndToTheRight() {
        assertArrayEquals(new double[] {-0.38, 0.75, 0.6}, handOffset(0), EPS);
        assertArrayEquals(new double[] {0.6, 0.75, 0.38}, handOffset(-90), EPS);
    }

    @Test
    void digOutcomes() {
        assertEquals(DIG_COARSE, digOutcome(true, false, true, 0.9f));
        assertEquals(DIG_COARSE, digOutcome(true, false, false, 0f));
        assertEquals(DIG_PIT, digOutcome(false, true, true, 0f));
        assertEquals(DIG_NONE, digOutcome(false, true, true, PIT_CHANCE));
        assertEquals(DIG_NONE, digOutcome(false, true, false, 0f));
        assertEquals(DIG_NONE, digOutcome(false, false, true, 0f));
    }

    @Test
    void inViewIsASixtyDegreeCone() {
        assertTrue(inView(0, 0, 1, 0, 0, 5));
        assertFalse(inView(0, 0, 1, 0, 0, -5));
        assertTrue(inView(0, 0, 1, 5, 0, 5));
        assertFalse(inView(0, 0, 1, 5, 0, 1));
    }
}
