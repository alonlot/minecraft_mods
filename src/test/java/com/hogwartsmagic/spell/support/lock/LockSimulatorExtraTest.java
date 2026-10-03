package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockSimulatorExtraTest {
    @Test
    void freshSimulatorFiresNoObserverPulse() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int lever = b.input(LockCircuit.LEVER, true);
        int obs = b.node(LockCircuit.OBSERVER, 0);
        b.link(lever, obs, 0);
        LockSimulator sim = new LockSimulator(b.build());
        assertTrue(sim.settle());
        assertEquals(0, sim.value(obs));
    }

    @Test
    void settleWithTooFewTicksReportsNotStable() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int lever = b.input(LockCircuit.LEVER, false);
        int t1 = b.node(LockCircuit.NOT, 0);
        int t2 = b.node(LockCircuit.NOT, 0);
        b.link(lever, t1, 0);
        b.link(t1, t2, 0);
        LockSimulator sim = new LockSimulator(b.build());
        assertFalse(sim.settle(1));
        assertTrue(sim.settle());
        assertEquals(15, sim.value(t1));
        assertEquals(0, sim.value(t2));
        assertThrows(IllegalArgumentException.class, () -> sim.settle(0));
    }
}
