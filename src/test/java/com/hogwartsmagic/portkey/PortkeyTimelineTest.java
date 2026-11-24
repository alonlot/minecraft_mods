package com.hogwartsmagic.portkey;

import com.hogwartsmagic.portkey.PortkeyTimeline.Frame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The traveller's own screen over a whole journey: charge, hook, tunnel, exit, landing. */
class PortkeyTimelineTest {

    private static final float EXIT_AT = 24.0f;

    @Test
    void chargeEndpoints() {
        assertEquals(1.0f, PortkeyTimeline.self(0, -1, -1, 0).fov(), 1e-6);
        assertEquals(0.88f, PortkeyTimeline.self(60, -1, -1, 0).fov(), 1e-4);
        for (float t = 0; t <= 60; t += 0.5f) {
            assertEquals(0.0f, PortkeyTimeline.self(t, -1, -1, 0).mix(), 1e-6, "no tunnel while charging, t=" + t);
        }
    }

    @Test
    void hookPunchThenCollapse() {
        assertTrue(PortkeyTimeline.self(60, 1, -1, 0).fov() > 1.25f, "the yank flings the view wide");
        assertTrue(PortkeyTimeline.self(60, 4, -1, 0).fov() < 0.75f, "then it collapses into the tunnel");
        assertEquals(1.0f, PortkeyTimeline.self(60, 3.5f, -1, 0).mix(), 1e-6);
    }

    @Test
    void exitUnwinds() {
        assertEquals(0.0f, PortkeyTimeline.self(60, EXIT_AT + 5, 5, 0).mix(), 1e-6);
        assertEquals(0.0f, PortkeyTimeline.self(60, EXIT_AT + 10, 10, 0).twist(), 1e-6);
        assertEquals(1.0f, PortkeyTimeline.self(60, EXIT_AT + 10, 10, 0).fov(), 1e-6);
    }

    @Test
    void landingRollDecays() {
        assertTrue(Math.abs(PortkeyTimeline.self(60, EXIT_AT + 22, 22, 25).roll()) < 0.5f);
        assertTrue(Math.abs(PortkeyTimeline.self(60, EXIT_AT + 22, 22, -25).roll()) < 0.5f);
        // The roll at the start of the exit is the one the camera had: no snap.
        assertEquals(25.0f, PortkeyTimeline.self(60, EXIT_AT, 0, 25).roll(), 1e-4);
    }

    @Test
    void continuous() {
        List<Frame> frames = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Boolean> punch = new ArrayList<>();
        for (float t = 0; t <= 60.0001f; t += 0.25f) {
            frames.add(PortkeyTimeline.self(t, -1, -1, 0));
            labels.add("charge t=" + t);
            punch.add(false);
        }
        for (float h = 0; h <= EXIT_AT + 0.0001f; h += 0.25f) {
            frames.add(PortkeyTimeline.self(60, h, -1, 0));
            labels.add("hook h=" + h);
            punch.add(h > 0 && h <= 1.0f); // the yank itself is allowed to snap the FOV
        }
        float rollAtExit = PortkeyTimeline.self(60, EXIT_AT, -1, 0).roll();
        for (float x = 0; x <= 20.0001f; x += 0.25f) {
            frames.add(PortkeyTimeline.self(60, EXIT_AT + x, x, rollAtExit));
            labels.add("exit x=" + x);
            punch.add(false);
        }
        for (int i = 1; i < frames.size(); i++) {
            Frame a = frames.get(i - 1);
            Frame b = frames.get(i);
            String where = labels.get(i - 1) + " -> " + labels.get(i);
            assertTrue(Math.abs(b.mix() - a.mix()) <= 0.2f, "mix jumps at " + where);
            assertTrue(Math.abs(b.twist() - a.twist()) <= 0.35f, "twist jumps at " + where);
            if (!punch.get(i)) {
                assertTrue(Math.abs(b.fov() - a.fov()) <= 0.2f, "fov jumps at " + where);
            }
            assertTrue(Math.abs(b.roll() - a.roll()) <= 6.0f, "roll snaps at " + where);
        }
    }

    @Test
    void exitForcedAtTimeout() {
        assertTrue(PortkeyTimeline.exitReady(240, false, false, 0));
        assertFalse(PortkeyTimeline.exitReady(30, true, false, 10));
        assertTrue(PortkeyTimeline.exitReady(30, true, false, 40));
        assertFalse(PortkeyTimeline.exitReady(20, true, true, 5));
    }

    @Test
    void chargeSoundRises() {
        assertEquals(0.35f, PortkeyTimeline.chargeVolume(0), 1e-6);
        assertEquals(1.0f, PortkeyTimeline.chargeVolume(1), 1e-6);
        assertEquals(0.9f, PortkeyTimeline.chargePitch(0), 1e-6);
        assertEquals(1.1f, PortkeyTimeline.chargePitch(1), 1e-6);
        assertEquals(1.0f, PortkeyTimeline.chargeEase(60), 1e-6);
        assertEquals(0.0f, PortkeyTimeline.chargeEase(0), 1e-6);
    }
}
