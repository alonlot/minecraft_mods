package com.hogwartsmagic.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BroomPilotRulesTest {
    private static final double SPELL_SPEED = 1.5D;

    /** Replays BroomEntity's flight: steer, move, damp. Target at the origin; returns {range, speed, x, z}. */
    private static double[] fight(int type, double startX, boolean frightened, int ticks, double[] rangeWindow) {
        double x = startX;
        double z = 0.0D;
        double vx = 0.0D;
        double vz = 0.0D;
        double hold = BroomPilotRules.holdRange(frightened);
        double moved = 0.0D;
        for (int t = 0; t < ticks; t++) {
            double range = Math.sqrt(x * x + z * z);
            double tx = -x / range;
            double tz = -z / range;
            double pull = BroomPilotRules.radialPull(range, hold);
            double share = BroomPilotRules.orbitShare(pull);
            double dx = tx * pull - tz * share;
            double dz = tz * pull + tx * share;
            double length = Math.sqrt(dx * dx + dz * dz);
            double command = BroomPilotRules.command(BroomPilotRules.combatSpeed(type, range, hold, false));
            vx = BroomPilotRules.blend(vx, dx / length * command);
            vz = BroomPilotRules.blend(vz, dz / length * command);
            x += vx;
            z += vz;
            moved = Math.sqrt(vx * vx + vz * vz);
            if (rangeWindow != null && t > ticks / 2) {
                double now = Math.sqrt(x * x + z * z);
                rangeWindow[0] = Math.min(rangeWindow[0], now);
                rangeWindow[1] = Math.max(rangeWindow[1], now);
            }
            vx *= BroomPilotRules.DAMPING;
            vz *= BroomPilotRules.DAMPING;
        }
        return new double[]{Math.sqrt(x * x + z * z), moved, x, z};
    }

    @Test
    void theCommandMakesTheBroomActuallyTravelAtTheSpeedAskedFor() {
        double v = 0.0D;
        double travelled = 0.0D;
        for (int t = 0; t < 200; t++) {
            travelled = BroomPilotRules.blend(v, BroomPilotRules.command(0.8D));
            v = travelled * BroomPilotRules.DAMPING;
        }
        assertEquals(0.8D, travelled, 1.0E-6D);
    }

    @Test
    void aFightSettlesAtRangeFromFarOrNearOnEveryBroom() {
        for (int type = 0; type <= 2; type++) {
            for (double start : new double[]{40.0D, 2.0D}) {
                double[] window = {Double.MAX_VALUE, 0.0D};
                fight(type, start, false, 600, window);
                assertTrue(window[0] > 8.5D, "type " + type + " from " + start + " came in to " + window[0]);
                assertTrue(window[1] < 13.5D, "type " + type + " from " + start + " drifted out to " + window[1]);
            }
        }
    }

    @Test
    void aSettledFightIsSlowerThanItsOwnSpells() {
        for (int type = 0; type <= 2; type++) {
            double[] end = fight(type, 40.0D, false, 600, null);
            assertTrue(end[1] < SPELL_SPEED * 0.5D, "type " + type + " strafes at " + end[1]);
        }
    }

    @Test
    void aFrightenedRiderBacksOffButStaysInTheFight() {
        double[] window = {Double.MAX_VALUE, 0.0D};
        fight(0, 6.0D, true, 800, window);
        assertTrue(window[0] > 21.0D, "came in to " + window[0]);
        assertTrue(window[1] < 28.0D, "fled out to " + window[1] + ", past where it can see");
    }

    @Test
    void combatSpeedHasNoBandEdges() {
        for (double range = 0.0D; range < 60.0D; range += 0.25D) {
            double step = Math.abs(BroomPilotRules.combatSpeed(0, range + 0.25D, 11.0D, false)
                    - BroomPilotRules.combatSpeed(0, range, 11.0D, false));
            assertTrue(step < 0.06D, "speed jumps by " + step + " at range " + range);
        }
    }

    @Test
    void castingSlowsTheBroomDown() {
        assertTrue(BroomPilotRules.combatSpeed(0, 11.0D, 11.0D, true) < BroomPilotRules.combatSpeed(0, 11.0D, 11.0D, false) * 0.6D);
    }

    @Test
    void anUnknownBroomTypeFliesLikeTheNearestKnownOne() {
        assertEquals(BroomPilotRules.cruiseSpeed(0), BroomPilotRules.cruiseSpeed(-3));
        assertEquals(BroomPilotRules.cruiseSpeed(2), BroomPilotRules.cruiseSpeed(9));
    }

    @Test
    void altitudeStaysOffTheFloorAndUnderTheCeiling() {
        double open = Double.POSITIVE_INFINITY;
        assertEquals(53.0D, BroomPilotRules.safeAltitude(40.0D, 50.0D, open, BroomPilotRules.MIN_CLEARANCE));
        assertEquals(80.0D, BroomPilotRules.safeAltitude(80.0D, 50.0D, open, BroomPilotRules.MIN_CLEARANCE));
        assertEquals(57.4D, BroomPilotRules.safeAltitude(80.0D, 50.0D, 60.0D, BroomPilotRules.MIN_CLEARANCE), 1.0E-9D);
    }

    @Test
    void aTightPassageLetsTheCeilingDecide() {
        double y = BroomPilotRules.safeAltitude(80.0D, 50.0D, 54.0D, BroomPilotRules.MIN_CLEARANCE);
        assertEquals(51.4D, y, 1.0E-9D);
        assertTrue(BroomPilotRules.safeAltitude(80.0D, 50.0D, 51.0D, BroomPilotRules.MIN_CLEARANCE) >= 50.0D);
    }

    @Test
    void heightSettlesWithoutOvershootingFar() {
        double y = 60.0D;
        double vy = 0.0D;
        double peak = y;
        for (int t = 0; t < 400; t++) {
            vy = BroomPilotRules.blend(vy, BroomPilotRules.command(BroomPilotRules.climbRate(80.0D - y)));
            y += vy;
            vy *= BroomPilotRules.DAMPING;
            peak = Math.max(peak, y);
        }
        assertEquals(80.0D, y, 0.05D);
        assertTrue(peak < 81.5D, "overshot to " + peak);
    }

    @Test
    void anErrandEasesInToAStop() {
        assertEquals(0.0D, BroomPilotRules.arrivalSpeed(0, 0.0D));
        assertEquals(1.0D, BroomPilotRules.arrivalSpeed(0, 40.0D));
        assertEquals(0.0D, BroomPilotRules.glideHeight(0.0D));
        assertEquals(6.0D, BroomPilotRules.glideHeight(40.0D));
        assertFalse(BroomPilotRules.arrivalSpeed(0, 1.0D) > 0.2D);
    }

    @Test
    void cruiseIsAPlayersUnboostedBroom() {
        assertEquals(0.40D, BroomPilotRules.cruiseSpeed(0));
        assertTrue(BroomPilotRules.cruiseAltitude(60.0D, 7) > 60.0D + BroomPilotRules.MIN_CLEARANCE);
    }
}
