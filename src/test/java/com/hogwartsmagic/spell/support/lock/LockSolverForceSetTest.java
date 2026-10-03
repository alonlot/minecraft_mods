package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockSolverForceSetTest {
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
    void twoComparatorsAndedAreHeldTogether() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int x = b.input(LockCircuit.FORCE, false);
        int y = b.input(LockCircuit.FORCE, false);
        int door = b.node(LockCircuit.MAX, 0);
        and(b, x, y, door);
        b.goal(door);
        LockSolver.Solution s = LockSolver.solve(b.build(), 4, 2, BIG);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertEquals(1, s.moved());
        assertArrayEquals(new int[] {0, 1}, s.set());
        assertArrayEquals(new int[] {15, 15}, s.setTo());
        assertArrayEquals(new int[0], s.presses());
    }

    @Test
    void theSmallestSufficientSubsetWins() {
        // Door needs x AND y; z is a bystander that also feeds nothing.
        LockCircuit.Builder b = new LockCircuit.Builder();
        int x = b.input(LockCircuit.FORCE, false);
        int z = b.input(LockCircuit.FORCE, false);
        int y = b.input(LockCircuit.FORCE, false);
        int door = b.node(LockCircuit.MAX, 0);
        and(b, x, y, door);
        int idle = b.node(LockCircuit.MAX, 0);
        b.link(z, idle, 0);
        b.goal(door);
        LockSolver.Solution s = LockSolver.solve(b.build(), 4, 2, BIG);
        assertArrayEquals(new int[] {0, 2}, s.set());
    }

    @Test
    void aSubsetThatPowersATrapIsRejected() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int x = b.input(LockCircuit.FORCE, false);
        int y = b.input(LockCircuit.FORCE, false);
        int door = b.node(LockCircuit.MAX, 0);
        and(b, x, y, door);
        int trap = b.node(LockCircuit.MAX, 0);
        and(b, x, y, trap);
        b.goal(door);
        b.danger(trap);
        LockSolver.Solution s = LockSolver.solve(b.build(), 4, 2, BIG);
        assertEquals(LockSolver.Status.NO_SOLUTION, s.status());
    }

    @Test
    void realSwitchesStillWinWhenTheyExist() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int lever = b.input(LockCircuit.LEVER, false);
        int x = b.input(LockCircuit.FORCE, false);
        int y = b.input(LockCircuit.FORCE, false);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(lever, door, 0);
        int forceDoor = b.node(LockCircuit.MAX, 0);
        and(b, x, y, forceDoor);
        b.link(forceDoor, door, 0);
        b.goal(door);
        LockSolver.Solution s = LockSolver.solve(b.build(), 4, 2, BIG);
        assertArrayEquals(new int[] {0}, s.set());
        assertArrayEquals(new int[] {15}, s.setTo());
    }

    @Test
    void theBudgetBoundsTheSubsetPass() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int x = b.input(LockCircuit.FORCE, false);
        int y = b.input(LockCircuit.FORCE, false);
        int door = b.node(LockCircuit.MAX, 0);
        and(b, x, y, door);
        b.goal(door);
        LockCircuit c = b.build();
        LockSimulator probe = new LockSimulator(c);
        assertTrue(probe.resetBounded(Long.MAX_VALUE));
        long budget = probe.work() + 1; // the reset fits, no attempt does
        LockSolver.Solution s = LockSolver.solve(c, 4, 2, budget);
        assertEquals(LockSolver.Status.OUT_OF_TIME, s.status());
        assertTrue(s.work() < 10_000, "work " + s.work());
    }
}
