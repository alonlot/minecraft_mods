package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.EffectSeed;
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

    @Test
    void restingLettersLieFlat() {
        LetterFlood f = flood(0f, 8);
        f.fastForward(400);
        for (int i = 0; i < f.count(); i++) assertEquals(90f, f.pitch(i), 0.5f, "letter " + i);
    }

    @Test
    void marginCoversTheEnvelopeCorners() {
        double halfDiagonal = Math.hypot(LetterFlood.ENVELOPE_HALF_W, LetterFlood.ENVELOPE_HALF_H);
        assertTrue(LetterFlood.MARGIN >= halfDiagonal, "margin " + LetterFlood.MARGIN + " < " + halfDiagonal);
    }

    /** Every corner of a flat letter (centre, yaw) must lie in the box {minX, minY, minZ, maxX, maxY, maxZ}. */
    private static void assertCornersInBox(LetterFlood f, int i, double[] box, String msg) {
        double yaw = Math.toRadians(f.yaw(i));
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                double lx = sx * LetterFlood.ENVELOPE_HALF_W, lz = sz * LetterFlood.ENVELOPE_HALF_H;
                double wx = f.x(i) + lx * cos - lz * sin, wz = f.z(i) + lx * sin + lz * cos;
                assertTrue(wx >= box[0] - 1e-6 && wx <= box[3] + 1e-6, msg + " corner x " + wx);
                assertTrue(wz >= box[2] - 1e-6 && wz <= box[5] + 1e-6, msg + " corner z " + wz);
            }
        }
    }

    @Test
    void offCentreBoxClampsAndKeepsCornersInside() {
        // An explicit box that is not centred on the emitter (the emitter is on its -Z face, off to the side).
        double[] box = {4.0, 4.0, -14.0, 9.5, 7.0, -6.0};
        for (float yaw : YAWS) {
            LetterFlood f = new LetterFlood(5.0, 4.0, -6.0, yaw, box, 300, 120, 21);
            for (int tick = 0; tick < 400; tick++) {
                f.step();
                for (int i = 0; i < f.count(); i++) {
                    if (!f.emitted(i)) continue;
                    String msg = "yaw " + yaw + " tick " + tick + " letter " + i;
                    assertTrue(f.x(i) >= box[0] + LetterFlood.MARGIN - 1e-9 && f.x(i) <= box[3] - LetterFlood.MARGIN + 1e-9, msg + " x " + f.x(i));
                    assertTrue(f.z(i) >= box[2] + LetterFlood.MARGIN - 1e-9 && f.z(i) <= box[5] - LetterFlood.MARGIN + 1e-9, msg + " z " + f.z(i));
                    assertTrue(f.y(i) >= box[1] - 1e-9 && f.y(i) <= box[4] - LetterFlood.MARGIN + 1e-9, msg + " y " + f.y(i));
                }
            }
            for (int i = 0; i < f.count(); i++) {
                assertTrue(f.resting(i), "letter " + i + " rests");
                assertCornersInBox(f, i, box, "yaw " + yaw + " letter " + i);
            }
        }
    }

    @Test
    void legacyBoxRestingCornersStayInside() {
        for (float yaw : YAWS) {
            LetterFlood f = flood(yaw, 17);
            f.fastForward(400);
            double[] fw = forward(yaw);
            double cx = AX + fw[0] * HZ, cz = AZ + fw[1] * HZ;
            double wx = fw[0] != 0 ? HZ : HX, wz = fw[1] != 0 ? HZ : HX;
            double[] box = {cx - wx, AY, cz - wz, cx + wx, AY + 2 * HY, cz + wz};
            for (int i = 0; i < f.count(); i++) assertCornersInBox(f, i, box, "yaw " + yaw + " letter " + i);
        }
    }

    @Test
    void manyLettersStillSettleInsideTheBox() {
        for (int n : new int[] {300, 400}) {
            LetterFlood f = new LetterFlood(AX, AY, AZ, 90f, HX, HY, HZ, n, 120, 77);
            f.fastForward(500);
            double[] fw = forward(90f);
            double cx = AX + fw[0] * HZ, cz = AZ + fw[1] * HZ;
            double wx = fw[0] != 0 ? HZ : HX, wz = fw[1] != 0 ? HZ : HX;
            for (int i = 0; i < n; i++) {
                assertTrue(f.emitted(i) && f.resting(i), n + " letters, letter " + i);
                assertTrue(Math.abs(f.x(i) - cx) <= wx - LetterFlood.MARGIN + 1e-9, n + " x " + f.x(i));
                assertTrue(Math.abs(f.z(i) - cz) <= wz - LetterFlood.MARGIN + 1e-9, n + " z " + f.z(i));
            }
        }
    }

    @Test
    void sameSeedWhereverTheSceneIsPlaced() {
        long a = EffectSeed.of(110.5 + 4096, 24, 12.5 - 8192, 4096, 0, -8192, 3.5f);
        long b = EffectSeed.of(110.5, 24, 12.5, 0, 0, 0, 3.5f);
        assertEquals(b, a);
        assertTrue(EffectSeed.of(110.5, 24, 12.5, 0, 0, 0, 4f) != b, "event time is mixed in");
        assertTrue(EffectSeed.of(111.5, 24, 12.5, 0, 0, 0, 3.5f) != b, "position is mixed in");
    }

    @Test
    void stepsDueCatchesUpToTheSceneClock() {
        // Just fired: one step due. Caught up: none. Behind: the gap, capped. Before the event: never negative.
        assertEquals(1, LetterFlood.stepsDue(0, 10.2f, 10f, 20));
        assertEquals(0, LetterFlood.stepsDue(1, 10.9f, 10f, 20));
        assertEquals(1, LetterFlood.stepsDue(1, 11.0f, 10f, 20));
        assertEquals(5, LetterFlood.stepsDue(2, 16.5f, 10f, 20));
        assertEquals(20, LetterFlood.stepsDue(0, 100f, 10f, 20));
        assertEquals(0, LetterFlood.stepsDue(0, 5f, 10f, 20));
        assertEquals(0, LetterFlood.stepsDue(50, 12f, 10f, 20));
    }

    @Test
    void flightAgeCountsFromTheEmitTick() {
        LetterFlood f = flood(0f, 2);
        f.fastForward(80);
        for (int i = 0; i < f.count(); i++) {
            assertEquals(f.age() - (int) Math.floor(i * (0.6 * 120) / f.count()), f.flightAge(i));
        }
    }

    @Test
    void flyingLettersTiltAroundFlat() {
        for (float yaw : YAWS) {
            LetterFlood f = flood(yaw, 4);
            for (int tick = 0; tick < 300; tick++) {
                f.step();
                for (int i = 0; i < f.count(); i++) {
                    if (!f.emitted(i)) continue;
                    assertTrue(f.pitch(i) >= 45f - 1e-3f && f.pitch(i) <= 135f + 1e-3f,
                            "yaw " + yaw + " tick " + tick + " letter " + i + " pitch " + f.pitch(i));
                }
            }
        }
    }
}
