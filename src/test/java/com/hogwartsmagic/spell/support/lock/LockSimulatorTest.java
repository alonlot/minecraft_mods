package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockSimulatorTest {
    @Test
    void dustLosesOnePerBlock() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int lever = b.input(LockCircuit.LEVER, true);
        int[] dust = new int[16];
        int prev = lever;
        for (int i = 0; i < 16; i++) {
            dust[i] = b.node(LockCircuit.MAX, 0);
            b.link(prev, dust[i], i == 0 ? 0 : 1);
            prev = dust[i];
        }
        LockSimulator sim = new LockSimulator(b.build());
        assertTrue(sim.reset());
        assertEquals(15, sim.value(dust[0]));
        assertEquals(1, sim.value(dust[14]));
        assertEquals(0, sim.value(dust[15]));
    }

    @Test
    void torchInverts() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        b.input(LockCircuit.LEVER, false);
        int torch = b.node(LockCircuit.NOT, 15);
        b.link(0, torch, 0);
        LockSimulator sim = new LockSimulator(b.build());
        assertTrue(sim.reset());
        assertEquals(15, sim.value(torch));
        sim.setInput(0, true);
        assertTrue(sim.settle());
        assertEquals(0, sim.value(torch));
    }

    @Test
    void lockedRepeaterHolds() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int a = b.input(LockCircuit.LEVER, true);
        int lockLever = b.input(LockCircuit.LEVER, false);
        int rep = b.node(LockCircuit.REPEATER, 0);
        b.link(a, rep, 0);
        b.side(lockLever, rep, 0);
        LockSimulator sim = new LockSimulator(b.build());
        assertTrue(sim.reset());
        assertEquals(15, sim.value(rep));
        sim.setInput(1, true);
        sim.setInput(0, false);
        assertTrue(sim.settle());
        assertEquals(15, sim.value(rep));
        sim.setInput(1, false);
        assertTrue(sim.settle());
        assertEquals(0, sim.value(rep));
    }

    @Test
    void comparatorModes() {
        assertEquals(10, comparator(LockCircuit.COMPARE, 10, 4));
        assertEquals(6, comparator(LockCircuit.SUBTRACT, 10, 4));
        assertEquals(0, comparator(LockCircuit.COMPARE, 10, 12));
        assertEquals(10, comparator(LockCircuit.COMPARE, 10, 10));
    }

    private static int comparator(int kind, int rear, int sideStrength) {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int r = b.constant(rear);
        int s = b.constant(sideStrength);
        int c = b.node(kind, 0);
        b.link(r, c, 0);
        b.side(s, c, 0);
        LockSimulator sim = new LockSimulator(b.build());
        assertTrue(sim.reset());
        return sim.value(c);
    }

    @Test
    void analogInputTakesALevel() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int key = b.analog(0, new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8});
        int c = b.node(LockCircuit.COMPARE, 0);
        b.link(key, c, 0);
        LockCircuit circuit = b.build();
        assertEquals(LockCircuit.ANALOG, circuit.inputKind(0));
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8}, circuit.inputDomain(0));
        LockSimulator sim = new LockSimulator(circuit);
        assertTrue(sim.reset());
        sim.setLevel(0, 6);
        assertTrue(sim.settle());
        assertEquals(6, sim.value(c));
    }

    @Test
    void observerPulseSetsALatch() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int watched = b.input(LockCircuit.LEVER, false);
        int obs = b.node(LockCircuit.OBSERVER, 0);
        b.link(watched, obs, 0);
        int[] latch = norLatch(b, obs);
        LockSimulator sim = new LockSimulator(b.build());
        assertTrue(sim.reset());
        assertEquals(0, sim.value(latch[0]));
        sim.setInput(0, true);
        assertTrue(sim.settle());
        assertEquals(15, sim.value(latch[0]));
        assertEquals(0, sim.value(obs));
    }

    /** RS NOR latch made of two torches; returns {q, qBar}. Starts reset (q = 0). */
    static int[] norLatch(LockCircuit.Builder b, int set) {
        int q = b.node(LockCircuit.NOT, 0);
        int qBar = b.node(LockCircuit.NOT, 15);
        b.link(set, qBar, 0);
        b.link(q, qBar, 0);
        b.link(qBar, q, 0);
        return new int[] {q, qBar};
    }

    @Test
    void torchOnItselfIsUnstable() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int torch = b.node(LockCircuit.NOT, 0);
        b.link(torch, torch, 0);
        assertFalse(new LockSimulator(b.build()).reset());
    }

    @Test
    void restoreReturnsToTheSnapshot() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        b.input(LockCircuit.LEVER, false);
        int dust = b.node(LockCircuit.MAX, 0);
        b.link(0, dust, 0);
        LockSimulator sim = new LockSimulator(b.build());
        assertTrue(sim.reset());
        int[] base = sim.snapshot();
        sim.setInput(0, true);
        assertTrue(sim.settle());
        assertEquals(15, sim.value(dust));
        sim.restore(base);
        assertEquals(0, sim.value(dust));
        assertTrue(sim.work() > 0);
    }
}
