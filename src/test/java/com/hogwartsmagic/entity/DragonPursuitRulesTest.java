package com.hogwartsmagic.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonPursuitRulesTest {

    @Test
    void fallingBehindMakesItFlyHarder() {
        double near = DragonPursuitRules.chaseSpeed(1.5D, 12.0D, false);
        double far = DragonPursuitRules.chaseSpeed(1.5D, 50.0D, false);
        assertTrue(far > near, "far " + far + " should beat near " + near);
        assertTrue(near > 1.5D, "in range it still gains on its quarry");
    }

    @Test
    void aBoostingBroomOutrunsEvenARagingDragon() {
        // The slowest broom's boost is 6.5 blocks/tick.
        assertTrue(DragonPursuitRules.chaseSpeed(6.5D, 80.0D, true) < 6.5D);
        assertEquals(DragonPursuitRules.CAP, DragonPursuitRules.chaseSpeed(6.5D, 80.0D, false), 1e-9);
    }

    @Test
    void aBasicBroomAtCruiseCannotShakeIt() {
        // Basic broom cruise is 2.0: under the cap, so the dragon keeps gaining.
        assertTrue(DragonPursuitRules.chaseSpeed(2.0D, 20.0D, false) > 2.0D);
    }

    @Test
    void itNeverHangsStill() {
        assertEquals(DragonPursuitRules.FLOOR, DragonPursuitRules.chaseSpeed(0.0D, 3.0D, false), 1e-9);
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
    void theTurningCircleIsWiderThanABroomsAtTopSpeed() {
        // Circumference over 2 pi: speed / turn rate.
        double radius = DragonPursuitRules.CAP / DragonPursuitRules.TURN;
        assertTrue(radius > 25.0D, "radius " + radius);
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
