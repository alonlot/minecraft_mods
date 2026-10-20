package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.Eyelids;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EyelidsTest {
    @Test void openingStartsShut() { assertEquals(1f, Eyelids.closed(true, 0, 100), 1e-4); }
    @Test void openingBlinksHalfOpen() { assertEquals(0.5f, Eyelids.closed(true, 45, 100), 0.02); }
    @Test void openingShutAgainMidBlink() { assertEquals(1f, Eyelids.closed(true, 55, 100), 0.02); }
    @Test void openingEndsOpen() { assertEquals(0f, Eyelids.closed(true, 100, 100), 1e-4); assertEquals(0f, Eyelids.closed(true, 500, 100), 1e-4); }
    @Test void closingEndsShutAndStays() { assertEquals(0f, Eyelids.closed(false, 0, 40), 1e-4); assertEquals(1f, Eyelids.closed(false, 40, 40), 1e-4); assertEquals(1f, Eyelids.closed(false, 400, 40), 1e-4); }

    @Test void shutLidsLeaveNoSeam() {
        for (int h : new int[] {1, 2, 99, 100, 240, 253, 1080}) {
            int solid = Eyelids.band(1f, h, 24) - 24;
            assertTrue(2 * solid >= h, "h=" + h);
        }
    }
    @Test void openLidCoversNothing() { assertEquals(0, Eyelids.band(0f, 240, 24)); }

    private static com.hogwartsmagic.pensieve.scene.SceneDef.EventDef effect(float t, String id) {
        return new com.hogwartsmagic.pensieve.scene.SceneDef.EventDef(t, "effect", id, null, 1f, 1f, 0, null, 0, Float.NaN, null, Float.NaN);
    }

    @Test void opensShutOnlyForAnEyesOpenAtTheStart() {
        assertTrue(Eyelids.opensShut(java.util.List.of(effect(0, "eyes_open"))));
        assertTrue(Eyelids.opensShut(java.util.List.of(effect(40, "lightning"), effect(0, "eyes_open"))));
        assertTrue(!Eyelids.opensShut(java.util.List.of(effect(20, "eyes_open"))));
        assertTrue(!Eyelids.opensShut(java.util.List.of(effect(0, "eyes_close"))));
        assertTrue(!Eyelids.opensShut(java.util.List.of()));
        assertTrue(!Eyelids.opensShut(null));
        assertTrue(!Eyelids.opensShut(java.util.List.of(new com.hogwartsmagic.pensieve.scene.SceneDef.EventDef(0,
                "sound", "eyes_open", null, 1f, 1f, 0, null, 0, Float.NaN, null, Float.NaN))));
    }

    @Test void peekStartsShutOpensToTheSlitAndShutsForGood() {
        assertEquals(1f, Eyelids.peek(0, 60, 0.35f), 1e-4);
        // Held at the slit through the middle of the effect: 35% open is 65% shut.
        assertEquals(0.65f, Eyelids.peek(30, 60, 0.35f), 1e-4);
        assertEquals(0.65f, Eyelids.peek(20, 60, 0.35f), 1e-4);
        assertEquals(0.65f, Eyelids.peek(40, 60, 0.35f), 1e-4);
        assertEquals(1f, Eyelids.peek(60, 60, 0.35f), 1e-4);
        assertEquals(1f, Eyelids.peek(600, 60, 0.35f), 1e-4);
    }

    @Test void peekNeverOpensPastItsSlit() {
        for (int age = 0; age <= 70; age++) {
            float c = Eyelids.peek(age, 60, 0.35f);
            assertTrue(c >= 0.65f - 1e-4 && c <= 1f + 1e-4, "age " + age + " closed " + c);
        }
    }

    @Test void peekOpenDefaultsAndClamps() {
        assertEquals(0.35f, Eyelids.peekOpen(Float.NaN), 1e-6);
        assertEquals(0.5f, Eyelids.peekOpen(0.5f), 1e-6);
        assertEquals(Eyelids.MIN_PEEK_OPEN, Eyelids.peekOpen(0f), 1e-6);
        assertEquals(1f, Eyelids.peekOpen(3f), 1e-6);
    }
}
