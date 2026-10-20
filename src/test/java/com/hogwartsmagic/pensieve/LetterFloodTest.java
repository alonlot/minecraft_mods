package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.LetterFlood;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LetterFloodTest {
    private static final float[] YAWS = {0f, 90f, 180f, -90f};
    private static final double AX = 10.5, AY = 4, AZ = -7.5;
    private static final float HX = 3, HY = 2.5f, HZ = 3;
    private static final double TOL = 0.05;

    private static LetterFlood flood(float yaw, long seed) {
        return new LetterFlood(AX, AY, AZ, yaw, HX, HY, HZ, 200, 120, seed);
    }

    /** The unit forward vector (Minecraft convention: yaw 0 faces +Z, yaw -90 faces +X). */
    private static double[] forward(float yaw) {
        double r = Math.toRadians(yaw);
        return new double[] {Math.round(-Math.sin(r)), Math.round(Math.cos(r))};
    }

    @Test
    void staysInsideRoomBox() {
        for (float yaw : YAWS) {
            LetterFlood f = flood(yaw, 42);
            double[] fw = forward(yaw);
            // Forward half-size is hz, sideways hx; world axes follow the facing.
            double wx = fw[0] != 0 ? HZ : HX, wz = fw[1] != 0 ? HZ : HX;
            double cx = AX + fw[0] * HZ, cz = AZ + fw[1] * HZ;
            for (int tick = 0; tick < 400; tick++) {
                f.step();
                for (int i = 0; i < f.count(); i++) {
                    if (!f.emitted(i)) continue;
                    String msg = "yaw " + yaw + " tick " + tick + " letter " + i;
                    assertTrue(Math.abs(f.x(i) - cx) <= wx + TOL, msg + " x " + f.x(i));
                    assertTrue(Math.abs(f.z(i) - cz) <= wz + TOL, msg + " z " + f.z(i));
                    assertTrue(f.y(i) >= AY - TOL && f.y(i) <= AY + 2 * HY + TOL, msg + " y " + f.y(i));
                }
            }
        }
    }

    @Test
    void neverBehindEmitter() {
        for (float yaw : YAWS) {
            LetterFlood f = flood(yaw, 7);
            double[] fw = forward(yaw);
            for (int tick = 0; tick < 400; tick++) {
                f.step();
                for (int i = 0; i < f.count(); i++) {
                    if (!f.emitted(i)) continue;
                    double dot = (f.x(i) - AX) * fw[0] + (f.z(i) - AZ) * fw[1];
                    assertTrue(dot >= -TOL, "yaw " + yaw + " tick " + tick + " letter " + i + " dot " + dot);
                }
            }
        }
    }

    @Test
    void allEmittedBy60Percent() {
        for (float yaw : YAWS) {
            LetterFlood f = flood(yaw, 1);
            assertFalse(f.emitted(f.count() - 1));
            for (int i = 0; i < 72; i++) f.step();
            for (int i = 0; i < f.count(); i++) assertTrue(f.emitted(i), "letter " + i);
        }
    }

    @Test
    void allRestOnTheFloor() {
        for (float yaw : YAWS) {
            LetterFlood f = flood(yaw, 99);
            for (int i = 0; i < 400; i++) f.step();
            for (int i = 0; i < f.count(); i++) {
                assertTrue(f.resting(i), "letter " + i);
                assertTrue(Math.abs(f.y(i) - AY) < 0.06, "letter " + i + " y " + f.y(i));
            }
        }
    }

    @Test
    void fastForwardMatchesStepping() {
        for (float yaw : YAWS) {
            LetterFlood a = flood(yaw, 5), b = flood(yaw, 5);
            for (int i = 0; i < 150; i++) a.step();
            b.fastForward(150);
            assertEquals(a.age(), b.age());
            for (int i = 0; i < a.count(); i++) {
                assertEquals(a.x(i), b.x(i), 0.0, "x " + i);
                assertEquals(a.y(i), b.y(i), 0.0, "y " + i);
                assertEquals(a.z(i), b.z(i), 0.0, "z " + i);
                assertEquals(a.emitted(i), b.emitted(i));
                assertEquals(a.resting(i), b.resting(i));
            }
        }
    }

    @Test
    void deterministic() {
        LetterFlood a = flood(0f, 11), b = flood(0f, 11), c = flood(0f, 12);
        a.fastForward(60);
        b.fastForward(60);
        c.fastForward(60);
        boolean differs = false;
        for (int i = 0; i < a.count(); i++) {
            assertEquals(a.x(i), b.x(i), 0.0);
            assertEquals(a.y(i), b.y(i), 0.0);
            assertEquals(a.z(i), b.z(i), 0.0);
            if (a.x(i) != c.x(i) || a.y(i) != c.y(i) || a.z(i) != c.z(i)) differs = true;
        }
        assertTrue(differs, "a different seed must give a different swarm");
    }

    @Test
    void restingLettersLieFlatAndStay() {
        LetterFlood f = flood(90f, 3);
        f.fastForward(400);
        double[] xs = new double[f.count()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = f.x(i);
            assertEquals(90f, f.pitch(i), 0.5f, "letter " + i);
        }
        f.fastForward(50);
        for (int i = 0; i < xs.length; i++) assertEquals(xs[i], f.x(i), 0.0);
    }
}
