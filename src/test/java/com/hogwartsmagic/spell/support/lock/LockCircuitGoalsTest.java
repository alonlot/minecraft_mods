package com.hogwartsmagic.spell.support.lock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockCircuitGoalsTest {
    @Test
    void pulseAndForceInputsAreOnOff() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int button = b.input(LockCircuit.PULSE, false);
        int forced = b.input(LockCircuit.FORCE, false);
        int door = b.node(LockCircuit.MAX, 0);
        b.link(button, door, 0);
        b.link(forced, door, 0);
        LockCircuit c = b.build();
        assertEquals(LockCircuit.PULSE, c.inputKind(0));
        assertEquals(LockCircuit.FORCE, c.inputKind(1));
        assertArrayEquals(new int[] {0, 15}, c.inputDomain(0));
        LockSimulator sim = new LockSimulator(c);
        assertTrue(sim.reset());
        sim.setInput(1, true);
        assertTrue(sim.settle());
        assertEquals(15, sim.value(door));
    }

    @Test
    void setLevelWorksOnLeversWithinTheirDomain() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        b.input(LockCircuit.LEVER, false);
        LockSimulator sim = new LockSimulator(b.build());
        sim.setLevel(0, 15);
        assertTrue(sim.settle());
        assertEquals(15, sim.value(0));
        assertThrows(IllegalArgumentException.class, () -> sim.setLevel(0, 7));
    }

    @Test
    void setInputRefusesAnalog() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        b.analog(0, new int[] {0, 5});
        LockSimulator sim = new LockSimulator(b.build());
        assertThrows(IllegalArgumentException.class, () -> sim.setInput(0, true));
    }

    @Test
    void goalsDangersAndFlags() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        int lever = b.input(LockCircuit.LEVER, false);
        int reached = b.node(LockCircuit.MAX, 0);
        b.link(lever, reached, 0);
        int stranded = b.node(LockCircuit.MAX, 0);
        int tnt = b.node(LockCircuit.MAX, 0);
        b.link(lever, tnt, 0);
        b.goal(reached);
        b.goal(reached);
        b.goal(stranded);
        b.danger(tnt);
        LockCircuit c = b.build();
        assertEquals(2, c.goalCount());
        assertEquals(1, c.dangerCount());
        assertEquals(1, c.controllableGoals());
        assertFalse(c.keyed());
        assertFalse(c.truncated());
        LockSimulator sim = new LockSimulator(c);
        assertTrue(sim.reset());
        assertFalse(sim.goalPowered(0));
        sim.setInput(0, true);
        assertTrue(sim.settle());
        assertTrue(sim.goalPowered(0));
        assertTrue(sim.dangerPowered(0));
    }

    @Test
    void flagsStick() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        b.markKeyed();
        b.markTruncated();
        LockCircuit c = b.build();
        assertTrue(c.keyed());
        assertTrue(c.truncated());
    }

    @Test
    void badGoalIndexThrows() {
        LockCircuit.Builder b = new LockCircuit.Builder();
        assertThrows(IllegalArgumentException.class, () -> b.goal(3));
        assertThrows(IllegalArgumentException.class, () -> b.danger(-1));
    }
}
