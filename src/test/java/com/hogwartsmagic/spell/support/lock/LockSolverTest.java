package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LockSolverTest {
    private static final long BIG = 10_000_000L;

    private static int piston(LockCircuit.Builder b, int from) {
        int p = b.node(LockCircuit.MAX, 0);
        b.link(from, p, 0);
        b.goal(p);
        return p;
    }

    private static int not(LockCircuit.Builder b, int from, int initial) {
        int t = b.node(LockCircuit.NOT, initial);
        b.link(from, t, 0);
        return t;
    }

    /** door += NOT(MAX(NOT x, NOT y)), i.e. x AND y. */
    private static void and(LockCircuit.Builder b, int x, int y, int door) {
        int or = b.node(LockCircuit.MAX, 15);
        b.link(not(b, x, 15), or, 0);
        b.link(not(b, y, 15), or, 0);
        b.link(not(b, or, 0), door, 0);
    }

    @Test
    void buttonDoor() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int button = b.input(LockCircuit.PULSE, false);
        int dust = b.node(LockCircuit.MAX, 0);
        b.link(button, dust, 0);
        piston(b, dust);
        piston(b, dust);
        LockSolver.Solution s = LockSolver.solve(b.build(), 4, 1, BIG);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertArrayEquals(new int[0], s.set());
        assertArrayEquals(new int[] {0}, s.presses());
        assertEquals(2, s.moved());
    }

    /** Opens only with A on, B off, C on: piston = NOT(MAX(NOT A, B, NOT C)). */
    private static LockCircuit threeLeverCombo() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int a = b.input(LockCircuit.LEVER, false);
        int bb = b.input(LockCircuit.LEVER, false);
        int c = b.input(LockCircuit.LEVER, false);
        int or = b.node(LockCircuit.MAX, 15);
        b.link(not(b, a, 15), or, 0);
        b.link(bb, or, 0);
        b.link(not(b, c, 15), or, 0);
        piston(b, not(b, or, 0));
        return b.build();
    }

    @Test
    void leverCombination() {
        LockSolver.Solution s = LockSolver.solve(threeLeverCombo(), 4, 1, BIG);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertArrayEquals(new int[] {0, 2}, s.set());
        assertArrayEquals(new int[] {15, 15}, s.setTo());
        assertArrayEquals(new int[0], s.presses());
    }

    @Test
    void neverFiresATrap() {
        // Lever 0 alone opens the door but also primes the TNT; levers 1 and 2 together open it safely.
        LockCircuit.Builder b = new LockCircuit.Builder();
        int trapLever = b.input(LockCircuit.LEVER, false);
        int x = b.input(LockCircuit.LEVER, false);
        int y = b.input(LockCircuit.LEVER, false);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(trapLever, door, 0);
        b.goal(door);
        int tnt = b.node(LockCircuit.MAX, 0);
        b.link(trapLever, tnt, 0);
        b.danger(tnt);
        and(b, x, y, door);
        LockSolver.Solution s = LockSolver.solve(b.build(), 4, 1, BIG);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertArrayEquals(new int[] {1, 2}, s.set());
    }

    @Test
    void prefersFewestActions() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int single = b.input(LockCircuit.LEVER, false);
        int x = b.input(LockCircuit.LEVER, false);
        int y = b.input(LockCircuit.LEVER, false);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(single, door, 0);
        b.goal(door);
        and(b, x, y, door);
        assertArrayEquals(new int[] {0}, LockSolver.solve(b.build(), 4, 1, BIG).set());
    }

    /** Press A, then B: A sets a latch, B opens only while it is set. */
    private static LockCircuit buttonOrder() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int a = b.input(LockCircuit.PULSE, false);
        int bb = b.input(LockCircuit.PULSE, false);
        int[] latch = LockSimulatorTest.norLatch(b, a);
        int door = b.node(LockCircuit.MAX, 0);
        b.goal(door);
        and(b, latch[0], bb, door);
        return b.build();
    }

    @Test
    void buttonOrderAtMaster() {
        LockSolver.Solution s = LockSolver.solve(buttonOrder(), 20, 4, BIG);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertArrayEquals(new int[] {0, 1}, s.presses());
    }

    @Test
    void buttonOrderBeyondExpert() {
        assertEquals(LockSolver.Status.NO_SOLUTION, LockSolver.solve(buttonOrder(), 4, 1, BIG).status());
    }

    /** The door opens only when a frame reads exactly 7: (a >= 7) AND NOT (a >= 8). */
    @Test
    void itemKeyLevel() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int key = b.analog(0, new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8});
        int ge7 = b.node(LockCircuit.COMPARE, 0);
        b.link(key, ge7, 0);
        b.side(b.constant(7), ge7, 0);
        int ge8 = b.node(LockCircuit.COMPARE, 0);
        b.link(key, ge8, 0);
        b.side(b.constant(8), ge8, 0);
        int or = b.node(LockCircuit.MAX, 15);
        b.link(not(b, ge7, 15), or, 0);
        b.link(ge8, or, 0);
        piston(b, not(b, or, 0));
        LockSolver.Solution s = LockSolver.solve(b.build(), 20, 4, BIG);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertArrayEquals(new int[] {0}, s.set());
        assertArrayEquals(new int[] {7}, s.setTo());
    }

    @Test
    void forcesOnlyWhenNoSwitchWorks() {
        // A forced source opens the door; so does the lever. The lever must win.
        LockCircuit.Builder b = new LockCircuit.Builder();
        int lever = b.input(LockCircuit.LEVER, false);
        int force = b.input(LockCircuit.FORCE, false);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(lever, door, 0);
        b.link(force, door, 0);
        b.goal(door);
        LockSolver.Solution s = LockSolver.solve(b.build(), 20, 4, BIG);
        assertArrayEquals(new int[] {0}, s.set());
        assertArrayEquals(new int[0], s.presses());
    }

    @Test
    void forcesAKeyedComparator() {
        // A comparator reading an empty chest (0) feeds the door; forcing its output opens it.
        LockCircuit.Builder b = new LockCircuit.Builder();
        int force = b.input(LockCircuit.FORCE, false);
        int comparator = b.node(LockCircuit.COMPARE, 0);
        b.link(b.constant(0), comparator, 0);
        int out = b.node(LockCircuit.MAX, 0);
        b.link(comparator, out, 0);
        b.link(force, out, 0);
        piston(b, out);
        LockSolver.Solution s = LockSolver.solve(b.build(), 20, 4, BIG);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertArrayEquals(new int[] {0}, s.presses());
    }

    @Test
    void forcedSourcesDoNotCountTowardTheCap() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int door = b.node(LockCircuit.MAX, 0);
        for (int i = 0; i < 5; i++) {
            b.link(b.input(LockCircuit.FORCE, false), door, 0);
        }
        b.goal(door);
        assertEquals(LockSolver.Status.SOLVED, LockSolver.solve(b.build(), 4, 1, BIG).status());
    }

    @Test
    void tooManyInputs() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int door = b.node(LockCircuit.MAX, 0);
        for (int i = 0; i < 5; i++) {
            b.link(b.input(LockCircuit.LEVER, false), door, 0);
        }
        b.goal(door);
        assertEquals(LockSolver.Status.TOO_MANY_INPUTS, LockSolver.solve(b.build(), 4, 1, BIG).status());
    }

    @Test
    void clockIsUnstable() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int lever = b.input(LockCircuit.LEVER, false);
        int torch = b.node(LockCircuit.NOT, 0);
        b.link(torch, torch, 0);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(lever, door, 0);
        b.link(torch, door, 0);
        b.goal(door);
        assertEquals(LockSolver.Status.UNSTABLE, LockSolver.solve(b.build(), 4, 1, BIG).status());
    }

    @Test
    void noDoor() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        b.input(LockCircuit.LEVER, false);
        assertEquals(LockSolver.Status.NO_DOOR, LockSolver.solve(b.build(), 4, 1, BIG).status());
    }

    @Test
    void tinyBudgetRunsOut() {
        assertEquals(LockSolver.Status.OUT_OF_TIME, LockSolver.solve(threeLeverCombo(), 4, 1, 1).status());
    }
}
