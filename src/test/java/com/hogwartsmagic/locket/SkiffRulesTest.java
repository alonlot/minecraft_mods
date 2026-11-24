package com.hogwartsmagic.locket;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkiffRulesTest {
    private static final List<Vec3> BENT = List.of(new Vec3(0, 0, 0), new Vec3(10, 0, 0), new Vec3(10, 0, 10));

    @Test void steerFollowsCurveNotChord() {
        // The curve between the first two points of a bent path leaves the straight chord (z = 0) ...
        Vec3 mid = SkiffRules.curvePoint(BENT, 1, 0.5);
        assertTrue(Math.abs(mid.z) > 0.1, "midpoint should be off the chord, was " + mid);
        // ... and steering from the chord midpoint heads for the curve ahead, not straight at the next waypoint.
        Vec3 v = SkiffRules.steer(new Vec3(5, 0, 0), BENT, 1, SkiffRules.SPEED);
        assertEquals(SkiffRules.SPEED, Math.sqrt(v.x * v.x + v.z * v.z), 1e-6);
        assertEquals(0.0, v.y, 1e-9);
        assertTrue(Math.abs(v.z) > 1e-4, "steering should bend off the chord, was " + v);
        assertTrue(v.x > 0, "still makes progress toward the next waypoint");
    }

    @Test void curveHitsItsWaypoints() {
        assertEquals(0, SkiffRules.curvePoint(BENT, 1, 0.0).distanceTo(BENT.get(0)), 1e-9);
        assertEquals(0, SkiffRules.curvePoint(BENT, 1, 1.0).distanceTo(BENT.get(1)), 1e-9);
    }

    @Test void speedWithLatchedSlowsAndFloors() {
        assertEquals(SkiffRules.SPEED, SkiffRules.speedWith(0), 1e-9);
        assertEquals(SkiffRules.SPEED * 0.4, SkiffRules.speedWith(2), 1e-9);
        assertEquals(SkiffRules.SPEED * 0.4, SkiffRules.speedWith(9), 1e-9);
    }

    @Test void riderGoneReturnsToShore() {
        assertFalse(SkiffRules.riderGoneReturnsToShore(false, true), "empty at the island stays docked");
        assertTrue(SkiffRules.riderGoneReturnsToShore(false, false), "empty mid-lake goes back to shore");
        assertFalse(SkiffRules.riderGoneReturnsToShore(true, false), "a rider keeps the trip");
    }

    @Test void bobStaysWithinAmplitude() {
        double max = 0;
        for (int t = 0; t < 400; t++) {
            max = Math.max(max, Math.abs(SkiffRules.bob(t)));
        }
        assertEquals(0.06, max, 0.002);
        assertEquals(0.0, SkiffRules.bob(0), 1e-9);
    }

    @Test void routeRunsShoreToIslandThroughPath() {
        List<net.minecraft.core.BlockPos> route = SkiffRules.route(new net.minecraft.core.BlockPos(0, 5, 0),
                List.of(new net.minecraft.core.BlockPos(3, 5, 0)), new net.minecraft.core.BlockPos(6, 5, 0));
        assertEquals(3, route.size());
        assertEquals(new net.minecraft.core.BlockPos(0, 4, 0), route.get(0));
        assertEquals(new net.minecraft.core.BlockPos(6, 4, 0), route.get(2));
    }
}
