package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockSolverProgressTest {
    private static final long BIG = 10_000_000L;

    /** Every input reaches the door and a trap alike, so every attempt is made and rejected. */
    private static LockCircuit allTrapped(int levers, int buttons, int forced) {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int door = b.node(LockCircuit.MAX, 0);
        int trap = b.node(LockCircuit.MAX, 0);
        for (int i = 0; i < levers; i++) {
            int in = b.input(LockCircuit.LEVER, false);
            b.link(in, door, 0);
            b.link(in, trap, 0);
        }
        for (int i = 0; i < buttons; i++) {
            int in = b.input(LockCircuit.PULSE, false);
            b.link(in, door, 0);
            b.link(in, trap, 0);
        }
        for (int i = 0; i < forced; i++) {
            int in = b.input(LockCircuit.FORCE, false);
            b.link(in, door, 0);
            b.link(in, trap, 0);
        }
        b.goal(door);
        b.danger(trap);
        return b.build();
    }

    @Test
    void theFullSearchMakesExactlyThePlannedAttempts() {
        LockCircuit c = allTrapped(3, 1, 0);
        LockSolver.Progress progress = new LockSolver.Progress();
        LockSolver.Solution s = LockSolver.solve(c, 4, 2, BIG, progress);
        assertEquals(LockSolver.Status.NO_SOLUTION, s.status());
        assertEquals(23, LockSolver.plannedAttempts(c, 4, 2));
        assertEquals(23, progress.attempts());
        assertTrue(progress.started());
        assertEquals(s.work(), progress.work());
    }

    @Test
    void theForcedPassesAreCountedToo() {
        LockCircuit c = allTrapped(0, 0, 2);
        LockSolver.Progress progress = new LockSolver.Progress();
        LockSolver.Solution s = LockSolver.solve(c, 4, 2, BIG, progress);
        assertEquals(LockSolver.Status.NO_SOLUTION, s.status());
        assertEquals(7, LockSolver.plannedAttempts(c, 4, 2));
        assertEquals(7, progress.attempts());
    }

    @Test
    void aSolvedSearchStopsWithinThePlan() {
        // Opens with lever A on: piston = A.
        LockCircuit.Builder b = new LockCircuit.Builder();
        int a = b.input(LockCircuit.LEVER, false);
        b.input(LockCircuit.LEVER, false);
        b.input(LockCircuit.LEVER, false);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(a, door, 0);
        b.goal(door);
        LockCircuit c = b.build();
        LockSolver.Progress progress = new LockSolver.Progress();
        LockSolver.Solution s = LockSolver.solve(c, 4, 1, BIG, progress);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertEquals(7, LockSolver.plannedAttempts(c, 4, 1));
        assertTrue(progress.attempts() >= 1 && progress.attempts() <= 7, "attempts " + progress.attempts());
    }

    @Test
    void aLockTooBigToWorkPlansNothing() {
        LockCircuit c = allTrapped(5, 0, 0);
        assertEquals(0, LockSolver.plannedAttempts(c, 4, 1));
        LockSolver.Progress progress = new LockSolver.Progress();
        assertEquals(LockSolver.Status.TOO_MANY_INPUTS, LockSolver.solve(c, 4, 1, BIG, progress).status());
        assertEquals(0, progress.attempts());
    }

    @Test
    void aFreshProgressHasNotStarted() {
        LockSolver.Progress progress = new LockSolver.Progress();
        assertFalse(progress.started());
        assertEquals(0, progress.attempts());
        assertEquals(0, progress.work());
    }
}
