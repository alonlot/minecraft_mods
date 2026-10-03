package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockSolverBoundsTest {
    private static int not(LockCircuit.Builder b, int from, int initial) {
        int t = b.node(LockCircuit.NOT, initial);
        b.link(from, t, 0);
        return t;
    }

    /** door += x AND y (each read as zero or not). */
    private static void and(LockCircuit.Builder b, int x, int y, int door) {
        int or = b.node(LockCircuit.MAX, 15);
        b.link(not(b, x, 15), or, 0);
        b.link(not(b, y, 15), or, 0);
        b.link(not(b, or, 0), door, 0);
    }

    /** t = NOT(lever AND t): steady while the lever is off, a clock once it is on. Input 0 is the lever. */
    private static LockCircuit clock(int dummyLevers) {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int lever = b.input(LockCircuit.LEVER, false);
        for (int i = 0; i < dummyLevers; i++) {
            b.input(LockCircuit.LEVER, false);
        }
        int t = b.node(LockCircuit.NOT, 15);
        int or = b.node(LockCircuit.MAX, 15);
        b.link(not(b, t, 0), or, 0);
        b.link(not(b, lever, 15), or, 0);
        int and = not(b, or, 0);
        b.link(and, t, 0);
        int door = b.node(LockCircuit.MAX, 15);
        b.link(t, door, 0);
        b.goal(door);
        return b.build();
    }

    @Test
    void aBudgetBelowOneFullSettleStopsTheClockAsOutOfTime() {
        LockCircuit c = clock(0);
        LockSimulator probe = new LockSimulator(c);
        assertTrue(probe.resetBounded(Long.MAX_VALUE));
        long resetCost = probe.work();
        probe.setInput(0, true);
        assertFalse(probe.settle());
        long fullSettle = probe.work() - resetCost;
        long budget = resetCost + 20;
        assertTrue(budget < fullSettle, "a clock settle (" + fullSettle + ") must outrun the budget " + budget);
        assertEquals(LockSolver.Status.OUT_OF_TIME, LockSolver.solve(c, 4, 1, budget).status());
    }

    @Test
    void aBudgetBelowTheResetCostIsOutOfTimeNotUnstable() {
        LockCircuit c = clock(0);
        assertEquals(LockSolver.Status.OUT_OF_TIME, LockSolver.solve(c, 4, 1, 0).status());
    }

    @Test
    void boundedResetRespectsTheBudgetAndKeepsPlainResetAsItWas() {
        LockCircuit c = clock(0);
        LockSimulator sim = new LockSimulator(c);
        assertTrue(sim.reset());
        assertFalse(sim.stoppedForBudget());
        LockSimulator bounded = new LockSimulator(c);
        assertFalse(bounded.resetBounded(0));
        assertTrue(bounded.stoppedForBudget());
        assertTrue(bounded.resetBounded(Long.MAX_VALUE));
        assertFalse(bounded.stoppedForBudget());
    }

    @Test
    void anInterruptDuringASettleIsOutOfTime() throws Exception {
        // Twenty dummy levers keep the search busy far longer than the interrupt takes; half the attempts run
        // the clock, so the interrupt lands inside a settle, not before the solve starts.
        LockCircuit c = clock(20);
        AtomicReference<LockSolver.Solution> out = new AtomicReference<>();
        Thread worker = new Thread(() -> out.set(LockSolver.solve(c, 30, 1, Long.MAX_VALUE / 2)));
        worker.setDaemon(true);
        worker.start();
        Thread.sleep(200);
        assertTrue(worker.isAlive(), "the solve finished before it could be interrupted");
        worker.interrupt();
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> worker.join());
        assertEquals(LockSolver.Status.OUT_OF_TIME, out.get().status());
    }

    @Test
    void anAnalogKeyAndAPressWorkTogether() {
        // The door opens only with the frame turned to any non-zero level AND the button down.
        LockCircuit.Builder b = new LockCircuit.Builder();
        int frame = b.analog(0, new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8});
        int button = b.input(LockCircuit.PULSE, false);
        int door = b.node(LockCircuit.MAX, 0);
        and(b, frame, button, door);
        b.goal(door);
        LockSolver.Solution s = LockSolver.solve(b.build(), 4, 1, 10_000_000L);
        assertEquals(LockSolver.Status.SOLVED, s.status());
        assertEquals(1, s.moved());
        assertArrayEquals(new int[] {0}, s.set());
        assertArrayEquals(new int[] {1}, s.presses());
        assertTrue(s.setTo()[0] > 0);
    }
}
