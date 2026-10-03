package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockSolverSafetyTest {
    private static final long BIG = 10_000_000L;

    private static int not(LockCircuit.Builder b, int from, int initial) {
        int t = b.node(LockCircuit.NOT, initial);
        b.link(from, t, 0);
        return t;
    }

    /** door += x AND y. */
    private static void and(LockCircuit.Builder b, int x, int y, int door) {
        int or = b.node(LockCircuit.MAX, 15);
        b.link(not(b, x, 15), or, 0);
        b.link(not(b, y, 15), or, 0);
        b.link(not(b, or, 0), door, 0);
    }

    @Test
    void doesNotPickTheLeverThatPulsesATrap() {
        // Lever 0 opens the door, but through dust and an observer it pulses the TNT for two ticks.
        // The TNT is off again once everything settles, yet it fired. Levers 1 and 2 are the safe route.
        LockCircuit.Builder b = new LockCircuit.Builder();
        int trapLever = b.input(LockCircuit.LEVER, false);
        int x = b.input(LockCircuit.LEVER, false);
        int y = b.input(LockCircuit.LEVER, false);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(trapLever, door, 0);
        b.goal(door);
        int dust = b.node(LockCircuit.MAX, 0);
        b.link(trapLever, dust, 0);
        int observer = b.node(LockCircuit.OBSERVER, 0);
        b.link(dust, observer, 0);
        int tnt = b.node(LockCircuit.MAX, 0);
        b.link(observer, tnt, 0);
        b.danger(tnt);
        and(b, x, y, door);
        LockSolver.Solution s = LockSolver.solve(b.build(), 4, 1, BIG);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertArrayEquals(new int[] {1, 2}, s.set());
    }

    @Test
    void simulatorReportsADangerThatPulsedDuringASettle() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int lever = b.input(LockCircuit.LEVER, false);
        int observer = b.node(LockCircuit.OBSERVER, 0);
        b.link(lever, observer, 0);
        int tnt = b.node(LockCircuit.MAX, 0);
        b.link(observer, tnt, 0);
        b.danger(tnt);
        LockSimulator sim = new LockSimulator(b.build());
        assertTrue(sim.reset());
        assertFalse(sim.dangerRoseInLastSettle());
        sim.setInput(0, true);
        assertTrue(sim.settle());
        assertFalse(sim.dangerPowered(0));
        assertTrue(sim.dangerRoseInLastSettle());
        assertTrue(sim.settle());
        assertFalse(sim.dangerRoseInLastSettle());
    }

    @Test
    void aDangerOnAtBaselineThatGoesOffAndOnIsRejected() {
        // The TNT is powered at rest (NOT of the button). Pressing the button cuts it, releasing restores it:
        // the button also opens the door, but the TNT would come back on, so there is no safe solution.
        LockCircuit.Builder b = new LockCircuit.Builder();
        int button = b.input(LockCircuit.PULSE, false);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(button, door, 0);
        b.goal(door);
        int tnt = b.node(LockCircuit.MAX, 15);
        b.link(not(b, button, 15), tnt, 0);
        b.danger(tnt);
        assertEquals(LockSolver.Status.NO_SOLUTION, LockSolver.solve(b.build(), 4, 1, BIG).status());
    }

    @Test
    void closesADoorThatIsAlreadyOpen() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int lever = b.input(LockCircuit.LEVER, false);
        int piston = b.node(LockCircuit.MAX, 15);
        b.link(not(b, lever, 15), piston, 0);
        b.goal(piston);
        LockSolver.Solution s = LockSolver.solve(b.build(), 4, 1, BIG);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertEquals(1, s.moved());
        assertArrayEquals(new int[] {0}, s.set());
        assertArrayEquals(new int[] {15}, s.setTo());
    }

    @Test
    void anInterruptedThreadGivesOutOfTime() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int lever = b.input(LockCircuit.LEVER, false);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(lever, door, 0);
        b.goal(door);
        LockCircuit c = b.build();
        Thread.currentThread().interrupt();
        try {
            assertEquals(LockSolver.Status.OUT_OF_TIME, LockSolver.solve(c, 4, 1, BIG).status());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void aLeverThatStartsAClockIsBounded() {
        // t = NOT(lever AND t): steady while the lever is off, a clock once it is on.
        LockCircuit.Builder b = new LockCircuit.Builder();
        int lever = b.input(LockCircuit.LEVER, false);
        int t = b.node(LockCircuit.NOT, 15);
        int or = b.node(LockCircuit.MAX, 15);
        b.link(not(b, t, 0), or, 0);
        b.link(not(b, lever, 15), or, 0);
        int and = not(b, or, 0);
        b.link(and, t, 0);
        int door = b.node(LockCircuit.MAX, 15);
        b.link(t, door, 0);
        b.goal(door);
        LockCircuit c = b.build();
        LockSolver.Solution s = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> LockSolver.solve(c, 4, 1, 2000));
        assertTrue(s.status() == LockSolver.Status.OUT_OF_TIME || s.status() == LockSolver.Status.NO_SOLUTION,
                "was " + s.status());
    }
}
