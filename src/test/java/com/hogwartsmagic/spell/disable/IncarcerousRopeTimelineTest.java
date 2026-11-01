package com.hogwartsmagic.spell.disable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncarcerousRopeTimelineTest {
    private static final double FLIGHT_LEN = 8.0;
    private static final double COIL_LEN = 6.0;

    @Test
    void headCrossesTheFlightThenTheCoil() {
        assertEquals(0.0, IncarcerousRopeTimeline.head(0.0, FLIGHT_LEN, COIL_LEN), 1e-9);
        assertEquals(FLIGHT_LEN / 2, IncarcerousRopeTimeline.head(IncarcerousRopeTimeline.FLIGHT / 2, FLIGHT_LEN, COIL_LEN), 1e-9);
        assertEquals(FLIGHT_LEN, IncarcerousRopeTimeline.head(IncarcerousRopeTimeline.FLIGHT, FLIGHT_LEN, COIL_LEN), 1e-9);
        double end = IncarcerousRopeTimeline.FLIGHT + IncarcerousRopeTimeline.WRAP;
        assertEquals(FLIGHT_LEN + COIL_LEN, IncarcerousRopeTimeline.head(end, FLIGHT_LEN, COIL_LEN), 1e-9);
        assertEquals(FLIGHT_LEN + COIL_LEN, IncarcerousRopeTimeline.head(end + 50, FLIGHT_LEN, COIL_LEN), 1e-9);
    }

    @Test
    void wrappedRopeEndsExactlyOnTheCoil() {
        double head = IncarcerousRopeTimeline.head(100.0, FLIGHT_LEN, COIL_LEN);
        assertEquals(FLIGHT_LEN, IncarcerousRopeTimeline.tail(head, COIL_LEN), 1e-9);
    }

    @Test
    void laidAtInvertsTheWrap() {
        for (double along = 0.0; along <= COIL_LEN; along += 0.5) {
            double t = IncarcerousRopeTimeline.laidAt(along, COIL_LEN);
            assertEquals(FLIGHT_LEN + along, IncarcerousRopeTimeline.head(t, FLIGHT_LEN, COIL_LEN), 1e-6);
        }
    }

    @Test
    void coilsLandLooseAndSettle() {
        assertEquals(0.55, IncarcerousRopeTimeline.looseness(0.0), 1e-9);
        assertTrue(Math.abs(IncarcerousRopeTimeline.looseness(20.0)) < 0.01);
    }

    @Test
    void strainStaysInRangeAndWaitsForTheCinch() {
        assertEquals(0.0, IncarcerousRopeTimeline.strain(7L, IncarcerousRopeTimeline.CINCHED), 1e-9);
        boolean strained = false;
        for (double t = 0.0; t < 400.0; t += 0.5) {
            double s = IncarcerousRopeTimeline.strain(-123456789L, t);
            assertTrue(s >= 0.0 && s <= 1.0);
            strained |= s > 0.5;
        }
        assertTrue(strained);
    }

    @Test
    void holdEasesInAndOut() {
        assertEquals(0.0, IncarcerousRopeTimeline.hold(0.0, 0.0), 1e-9);
        assertEquals(1.0, IncarcerousRopeTimeline.hold(40.0, 0.0), 1e-9);
        assertEquals(0.0, IncarcerousRopeTimeline.hold(40.0, 6.0), 1e-9);
        assertEquals(1.0, IncarcerousRopeTimeline.release(IncarcerousRopeTimeline.RELEASE), 1e-9);
    }
}
