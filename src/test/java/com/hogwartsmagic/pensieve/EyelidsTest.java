package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.Eyelids;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EyelidsTest {
    @Test void openingStartsShut() { assertEquals(1f, Eyelids.closed(true, 0, 100), 1e-4); }
    @Test void openingBlinksHalfOpen() { assertEquals(0.5f, Eyelids.closed(true, 45, 100), 0.02); }
    @Test void openingShutAgainMidBlink() { assertEquals(1f, Eyelids.closed(true, 55, 100), 0.02); }
    @Test void openingEndsOpen() { assertEquals(0f, Eyelids.closed(true, 100, 100), 1e-4); assertEquals(0f, Eyelids.closed(true, 500, 100), 1e-4); }
    @Test void closingEndsShutAndStays() { assertEquals(0f, Eyelids.closed(false, 0, 40), 1e-4); assertEquals(1f, Eyelids.closed(false, 40, 40), 1e-4); assertEquals(1f, Eyelids.closed(false, 400, 40), 1e-4); }
}
