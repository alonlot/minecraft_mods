package com.hogwartsmagic.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonPursuitRulesTest {
    /** Boost speeds from BroomEntity.handlePlayerFlight, and the Masterwork multiplier. */
    private static final double BASIC_BOOST = 6.5D;
    private static final double NIMBUS_BOOST = 8.0D;
    private static final double FIREBOLT_BOOST = 10.0D;
    private static final double MASTERWORK = 1.1D;

    private static double chase(int broom, boolean nimbusEscapes, double quarrySpeed, double distance) {
        double cap = DragonPursuitRules.cap(broom, nimbusEscapes, false);
        return DragonPursuitRules.chaseSpeed(quarrySpeed, distance, cap);
    }

    @Test
    void fallingBehindMakesItFlyHarder() {
        double near = chase(DragonPursuitRules.FIREBOLT, false, 1.5D, 12.0D);
        double far = chase(DragonPursuitRules.FIREBOLT, false, 1.5D, 50.0D);
        assertTrue(far > near, "far " + far + " should beat near " + near);
        assertTrue(near > 1.5D, "in range it still gains on its quarry");
    }

    @Test
    void itRunsDownAFireboltAtFullBoost() {
        double boost = FIREBOLT_BOOST * MASTERWORK;
        assertTrue(chase(DragonPursuitRules.FIREBOLT, false, boost, 15.0D) > boost);
        assertTrue(chase(DragonPursuitRules.FIREBOLT, true, boost, 15.0D) > boost, "the Nimbus roll is not a Firebolt's");
    }

    @Test
    void itRunsDownABasicBroomAtFullBoost() {
        double boost = BASIC_BOOST * MASTERWORK;
        assertTrue(chase(DragonPursuitRules.BASIC, false, boost, 15.0D) > boost);
    }

    @Test
    void aNimbusGetsAwayOnlyOnTheLuckyChases() {
        // Unlucky: even a Masterwork Nimbus at full boost is caught.
        double best = NIMBUS_BOOST * MASTERWORK;
        assertTrue(chase(DragonPursuitRules.NIMBUS, false, best, 15.0D) > best);
        // Lucky: a plain Nimbus at full boost pulls away, and fast enough to matter.
        double plain = NIMBUS_BOOST;
        double dragon = chase(DragonPursuitRules.NIMBUS, true, plain, 60.0D);
        assertTrue(dragon < plain, "dragon " + dragon);
        // It has to open 140 blocks and hold the gap 100 ticks on one ~288-tick tank.
        double ticksToEscape = 140.0D / (plain - dragon) + 100.0D;
        assertTrue(ticksToEscape < 288.0D, "escape takes " + ticksToEscape + " ticks");
        // A lucky chase stays lucky even when it is enraged.
        assertEquals(DragonPursuitRules.CAP_NIMBUS_OUTRUN,
                DragonPursuitRules.cap(DragonPursuitRules.NIMBUS, true, true), 1e-9);
        assertTrue(DragonPursuitRules.NIMBUS_ESCAPE_CHANCE > 0.0D && DragonPursuitRules.NIMBUS_ESCAPE_CHANCE < 1.0D);
    }

    @Test
    void itNeverHangsStill() {
        assertEquals(DragonPursuitRules.FLOOR, chase(-1, false, 0.0D, 3.0D), 1e-9);
    }

    @Test
    void turnIsLimitedPerTick() {
        double[] from = {0, 0, 1};
        double[] want = {1, 0, 0};
        double[] got = DragonPursuitRules.turnToward(from, want, 0.1D);
        double angle = Math.acos(got[2]);
        assertEquals(0.1D, angle, 1e-9);
        assertTrue(got[0] > 0, "it turns toward the wanted side");
        assertEquals(1.0D, Math.sqrt(got[0] * got[0] + got[1] * got[1] + got[2] * got[2]), 1e-9);
    }

    @Test
    void smallTurnsSnapStraightOn() {
        double[] want = {0.05, 0, 1};
        double[] got = DragonPursuitRules.turnToward(new double[]{0, 0, 1}, want, 0.1D);
        double[] n = DragonPursuitRules.normalize(want);
        assertEquals(n[0], got[0], 1e-9);
        assertEquals(n[2], got[2], 1e-9);
    }

    @Test
    void straightBehindStillTurnsLevel() {
        double[] got = DragonPursuitRules.turnToward(new double[]{0, 0, 1}, new double[]{0, 0, -1}, 0.2D);
        assertEquals(0.0D, got[1], 1e-9);
        assertEquals(0.2D, Math.acos(got[2]), 1e-9);
    }

    @Test
    void theTurningCircleIsWideSlowAndBoundedFast() {
        double slow = 2.4D / DragonPursuitRules.turnRate(2.4D, false);
        assertTrue(slow > 25.0D, "slow radius " + slow);
        double fast = 11.0D / DragonPursuitRules.turnRate(11.0D, false);
        assertEquals(DragonPursuitRules.MAX_TURN_RADIUS, fast, 1e-9);
    }

    @Test
    void leadIsBounded() {
        assertEquals(0.0D, DragonPursuitRules.leadTicks(0.0D, 2.0D), 1e-9);
        assertEquals(14.0D * 0.6D, DragonPursuitRules.leadTicks(500.0D, 1.0D), 1e-9);
    }

    @Test
    void speedEasesRatherThanSnaps() {
        double s = DragonPursuitRules.easeSpeed(1.0D, 2.0D);
        assertTrue(s > 1.0D && s < 1.5D, "eased " + s);
    }
}
