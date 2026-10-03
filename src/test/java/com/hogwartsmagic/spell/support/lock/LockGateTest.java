package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockGateTest {
    private static final int[] ALL = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};

    /** Input 0 is the reading, input 1 the conductor's signal. */
    private static LockCircuit gate(int reading, int conductor) {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int r = b.analog(reading, ALL);
        int c = b.analog(conductor, ALL);
        int g = b.node(LockCircuit.GATE15, 0);
        b.link(r, g, 0);
        b.side(c, g, 0);
        int danger = b.node(LockCircuit.MAX, 0);
        b.link(g, danger, 0);
        b.danger(danger);
        return b.build();
    }

    @Test
    void aFullConductorWinsWhateverTheReading() {
        LockCircuit c = gate(3, 15);
        LockSimulator sim = new LockSimulator(c);
        assertTrue(sim.reset());
        assertEquals(15, sim.value(2));
        sim.setLevel(0, 0);
        assertTrue(sim.settle(1));
        assertEquals(15, sim.value(2));
    }

    @Test
    void aWeakerConductorLetsTheReadingThrough() {
        LockCircuit c = gate(6, 14);
        LockSimulator sim = new LockSimulator(c);
        assertTrue(sim.reset());
        assertEquals(6, sim.value(2));
        sim.setLevel(1, 0);
        assertTrue(sim.settle(1));
        assertEquals(6, sim.value(2));
        sim.setLevel(0, 9);
        assertTrue(sim.settle(1));
        assertEquals(9, sim.value(2));
    }

    @Test
    void dropsFromFifteenWithoutADip() {
        LockCircuit c = gate(5, 15);
        LockSimulator sim = new LockSimulator(c);
        assertTrue(sim.reset());
        assertTrue(sim.dangerPowered(0));
        sim.setLevel(1, 0);
        assertTrue(sim.settle());
        assertEquals(5, sim.value(2));
        assertTrue(sim.dangerPowered(0));
        assertFalse(sim.dangerRoseInLastSettle());
    }

    @Test
    void aSideInputIsAcceptedOnAGate15ButRefusedOnADust() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int x = b.input(LockCircuit.LEVER, false);
        int gate = b.node(LockCircuit.GATE15, 0);
        int dust = b.node(LockCircuit.MAX, 0);
        assertDoesNotThrow(() -> b.side(x, gate, 0));
        assertThrows(IllegalArgumentException.class, () -> b.side(x, dust, 0));
    }
}
