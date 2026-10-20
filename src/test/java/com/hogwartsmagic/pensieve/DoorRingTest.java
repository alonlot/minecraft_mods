package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.DoorRing;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorRingTest {
    @Test
    void settlesToZeroAtRest() {
        DoorRing.State s = new DoorRing.State();
        s.angle = 30f;
        s.vel = 4f;
        for (int i = 0; i < 400; i++) DoorRing.step(s, 0f);
        assertEquals(0f, s.angle, 0.01f);
        assertEquals(0f, s.vel, 0.01f);
    }

    @Test
    void swingsOppositeTheAcceleration() {
        DoorRing.State up = new DoorRing.State();
        DoorRing.State down = new DoorRing.State();
        for (int i = 0; i < 4; i++) {
            DoorRing.step(up, 1.5f);
            DoorRing.step(down, -1.5f);
        }
        assertTrue(up.angle < -1f, "positive acceleration swings the ring negative: " + up.angle);
        assertTrue(down.angle > 1f, "negative acceleration swings the ring positive: " + down.angle);
    }

    @Test
    void staysBounded() {
        DoorRing.State s = new DoorRing.State();
        Random r = new Random(7);
        for (int i = 0; i < 5000; i++) {
            DoorRing.step(s, (r.nextFloat() - 0.5f) * 400f);
            assertTrue(Math.abs(s.angle) <= DoorRing.MAX_DEG + 1e-4f, "angle " + s.angle);
        }
        for (int i = 0; i < 20; i++) DoorRing.step(s, 1000f);
        assertEquals(-DoorRing.MAX_DEG, s.angle, 1e-4f);
    }

    @Test
    void shudderOnlyOnSuddenStop() {
        assertTrue(DoorRing.shudders(10f, 0.5f));
        assertTrue(DoorRing.shudders(-12f, -1f));
        assertFalse(DoorRing.shudders(10f, 5f));
        assertFalse(DoorRing.shudders(3f, 0f));
        assertFalse(DoorRing.shudders(0f, 10f));
        float peak = 0f;
        for (int t = DoorRing.SHUDDER_TICKS; t >= 1; t--) peak = Math.max(peak, Math.abs(DoorRing.shudder(t)));
        assertTrue(peak <= DoorRing.SHUDDER_DEG + 1e-6f);
        assertEquals(0f, DoorRing.shudder(0), 0f);
    }
}
