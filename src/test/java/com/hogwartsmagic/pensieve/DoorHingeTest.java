package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.DoorHinge;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoorHingeTest {
    private static final float[] YAWS = {-270f, -180f, -90f, -37f, 0f, 12f, 90f, 135f, 180f, 400f};
    private static final float[] SCALES = {0.25f, 1f, 1.5f, 4f};

    @Test
    void hingeStaysPut() {
        for (float s : SCALES) {
            for (float yaw : YAWS) {
                for (int c = 0; c <= 1; c++) {
                    double[] p = DoorHinge.corner(yaw, s, c);
                    assertEquals(0.0, p[0], 1e-9, "x yaw " + yaw + " scale " + s);
                    assertEquals(0.0, p[2], 1e-9, "z yaw " + yaw + " scale " + s);
                    assertEquals(c == 0 ? 0.0 : 3.0 * s, p[1], 1e-9, "y");
                }
            }
        }
    }

    @Test
    void swingsAboutHinge() {
        double[] free0 = DoorHinge.corner(0f, 1f, 2);
        assertArrayEquals(new double[] {1, 0, 0}, free0, 1e-9);
        double[] free90 = DoorHinge.corner(-90f, 1f, 2);
        assertArrayEquals(new double[] {0, 0, 1}, free90, 1e-9);
        double[] free180 = DoorHinge.corner(180f, 1f, 2);
        assertArrayEquals(new double[] {-1, 0, 0}, free180, 1e-9);
        // The free edge stays one leaf-width from the hinge at every yaw.
        for (float yaw : YAWS) {
            double[] p = DoorHinge.corner(yaw, 1f, 2);
            assertEquals(1.0, Math.hypot(p[0], p[2]), 1e-9);
        }
    }

    @Test
    void scaleMultiplies() {
        assertEquals(4.0, DoorHinge.corner(0f, 4f, 2)[0], 1e-9);
        assertEquals(12.0, DoorHinge.corner(0f, 4f, 1)[1], 1e-9);
        assertEquals(0.8, DoorHinge.corner(0f, 4f, 4)[2], 1e-9);
    }

    @Test
    void mirroredHingeStaysPut() {
        for (float s : SCALES) {
            for (float yaw : YAWS) {
                for (int c = 0; c <= 1; c++) {
                    double[] p = DoorHinge.corner(yaw, s, c, true);
                    assertEquals(0.0, p[0], 1e-9);
                    assertEquals(0.0, p[2], 1e-9);
                    assertEquals(c == 0 ? 0.0 : 3.0 * s, p[1], 1e-9);
                }
            }
        }
    }

    @Test
    void mirroredSwingsTheOtherWay() {
        assertArrayEquals(new double[] {-1, 0, 0}, DoorHinge.corner(0f, 1f, 2, true), 1e-9);
        assertArrayEquals(new double[] {0, 0, -1}, DoorHinge.corner(-90f, 1f, 2, true), 1e-9);
        assertArrayEquals(new double[] {0, 0, 1}, DoorHinge.corner(90f, 1f, 2, true), 1e-9);
        assertArrayEquals(new double[] {1, 0, 0}, DoorHinge.corner(180f, 1f, 2, true), 1e-9);
        // The thickness is not mirrored: at yaw 0 it is still on +Z.
        assertArrayEquals(new double[] {0, 0, 0.2}, DoorHinge.corner(0f, 1f, 4, true), 1e-9);
    }

    @Test
    void mirroredFacesWindOutward() {
        for (float yaw : YAWS) {
            double[][] c = new double[8][];
            double[] mid = new double[3];
            for (int i = 0; i < 8; i++) {
                c[i] = DoorHinge.corner(yaw, 1f, i, true);
                for (int k = 0; k < 3; k++) mid[k] += c[i][k] / 8.0;
            }
            for (int f = 0; f < DoorHinge.FACES.length; f++) {
                int[] q = DoorHinge.winding(f, true);
                double[] n = DoorHinge.faceNormal(yaw, 1f, f, true);
                double[] centre = new double[3];
                for (int i : q) for (int k = 0; k < 3; k++) centre[k] += c[i][k] / 4.0;
                double out = 0;
                for (int k = 0; k < 3; k++) out += n[k] * (centre[k] - mid[k]);
                assertTrue(out > 0.05, "mirrored face " + f + " yaw " + yaw + " points inward");
            }
        }
    }

    /** The documented pair: doorway along Z, hinges at z -3.5 and +4.5, scale 4, both opening toward -X. */
    @Test
    void pairMeetsAndOpensTheSameWay() {
        float s = 4f;
        double lowHinge = -3.5, highHinge = 4.5;
        // Closed (both yaw -90): the free edges meet at z 0.5, both thicknesses on -X.
        double[] aFree = DoorHinge.corner(-90f, s, 2, false);
        double[] bFree = DoorHinge.corner(-90f, s, 2, true);
        assertEquals(0.5, lowHinge + aFree[2], 1e-9);
        assertEquals(0.5, highHinge + bFree[2], 1e-9);
        assertEquals(-0.8, DoorHinge.corner(-90f, s, 4, false)[0], 1e-9);
        assertEquals(-0.8, DoorHinge.corner(-90f, s, 4, true)[0], 1e-9);
        // Open: the low leaf at -180 and the high leaf at 0 both have their free edge on -X, 4 from the hinge.
        assertArrayEquals(new double[] {-4, 0, 0}, DoorHinge.corner(-180f, s, 2, false), 1e-9);
        assertArrayEquals(new double[] {-4, 0, 0}, DoorHinge.corner(0f, s, 2, true), 1e-9);
        // Halfway the leaves are mirror images about the doorway centre line z 0.5.
        double[] a45 = DoorHinge.corner(-135f, s, 2, false);
        double[] b45 = DoorHinge.corner(-45f, s, 2, true);
        assertEquals(a45[0], b45[0], 1e-9);
        assertEquals(0.5 - (lowHinge + a45[2]), (highHinge + b45[2]) - 0.5, 1e-9);
    }

    /** Pitch 0 must be exactly the yaw-only leaf, so scenes that key only yaw render as before. */
    @Test
    void pitchZeroIsTheYawOnlyLeaf() {
        for (boolean mirrored : new boolean[] {false, true}) {
            for (float yaw : YAWS) {
                for (int c = 0; c < 8; c++) {
                    assertArrayEquals(DoorHinge.corner(yaw, 1.5f, c, mirrored),
                            DoorHinge.corner(yaw, 0f, 1.5f, c, mirrored), 0.0);
                }
            }
        }
    }

    @Test
    void fallsAboutBottomEdge() {
        for (boolean mirrored : new boolean[] {false, true}) {
            for (float s : SCALES) {
                // The bottom edge on the front face (corners 4 and 6) stays where it was.
                for (int c : new int[] {4, 6}) {
                    assertArrayEquals(DoorHinge.corner(0f, s, c, mirrored),
                            DoorHinge.corner(0f, -90f, s, c, mirrored), 1e-9, "bottom edge " + c);
                }
                // The top edge of the front face lands at floor level, a leaf-height from the bottom edge.
                for (int c : new int[] {5, 7}) {
                    double[] top = DoorHinge.corner(0f, -90f, s, c, mirrored);
                    double[] bottom = DoorHinge.corner(0f, s, c - 1, mirrored);
                    assertEquals(0.0, top[1], 1e-9, "top edge at floor level");
                    assertEquals(3.0 * s, Math.hypot(top[0] - bottom[0], top[2] - bottom[2]), 1e-9);
                    assertEquals(bottom[0], top[0], 1e-9);
                    // It falls toward the front (local +Z): further from the hinge line than the bottom edge.
                    assertEquals(bottom[2] + 3.0 * s, top[2], 1e-9);
                }
                // Lying flat, the whole leaf is a slab of its own thickness resting on the floor.
                for (int c = 0; c < 8; c++) {
                    double y = DoorHinge.corner(0f, -90f, s, c, mirrored)[1];
                    assertTrue(y >= -1e-9 && y <= 0.2 * s + 1e-9, "corner " + c + " y " + y);
                }
            }
        }
    }

    @Test
    void pitchAndYawCompose() {
        // Yaw turns the fallen leaf about the vertical axis through the hinge: the same shape, spun.
        for (boolean mirrored : new boolean[] {false, true}) {
            for (float yaw : YAWS) {
                for (float pitch : new float[] {-90f, -45f, -10f}) {
                    for (int c = 0; c < 8; c++) {
                        double[] flat = DoorHinge.corner(0f, pitch, 1f, c, mirrored);
                        double r = Math.toRadians(yaw);
                        double ex = flat[0] * Math.cos(r) + flat[2] * Math.sin(r);
                        double ez = -flat[0] * Math.sin(r) + flat[2] * Math.cos(r);
                        double[] p = DoorHinge.corner(yaw, pitch, 1f, c, mirrored);
                        assertEquals(ex, p[0], 1e-9);
                        assertEquals(flat[1], p[1], 1e-9);
                        assertEquals(ez, p[2], 1e-9);
                    }
                }
            }
        }
        // Yaw -90 turns the front toward -X: the leaf falls toward -X, its bottom edge still on the hinge line.
        double[] top = DoorHinge.corner(-90f, -90f, 1f, 5, false);
        assertArrayEquals(new double[] {-3.2, 0.0, 0.0}, top, 1e-9);
    }

    @Test
    void tippedFacesStillWindOutward() {
        for (float pitch : new float[] {-90f, -37f}) {
            for (float yaw : new float[] {-90f, 30f}) {
                for (boolean mirrored : new boolean[] {false, true}) {
                    double[][] c = new double[8][];
                    double[] mid = new double[3];
                    for (int i = 0; i < 8; i++) {
                        c[i] = DoorHinge.corner(yaw, pitch, 1f, i, mirrored);
                        for (int k = 0; k < 3; k++) mid[k] += c[i][k] / 8.0;
                    }
                    for (int f = 0; f < DoorHinge.FACES.length; f++) {
                        int[] q = DoorHinge.winding(f, mirrored);
                        double[] n = DoorHinge.faceNormal(yaw, pitch, 1f, f, mirrored);
                        double[] centre = new double[3];
                        for (int i : q) for (int k = 0; k < 3; k++) centre[k] += c[i][k] / 4.0;
                        double out = 0;
                        for (int k = 0; k < 3; k++) out += n[k] * (centre[k] - mid[k]);
                        assertTrue(out > 0.05, "face " + f + " pitch " + pitch + " yaw " + yaw);
                    }
                }
            }
        }
    }

    @Test
    void facesWindOutward() {
        for (float yaw : YAWS) {
            double[][] c = new double[8][];
            double[] mid = new double[3];
            for (int i = 0; i < 8; i++) {
                c[i] = DoorHinge.corner(yaw, 1f, i);
                for (int k = 0; k < 3; k++) mid[k] += c[i][k] / 8.0;
            }
            for (int f = 0; f < DoorHinge.FACES.length; f++) {
                int[] q = DoorHinge.FACES[f];
                double[] n = DoorHinge.faceNormal(yaw, 1f, f);
                double[] centre = new double[3];
                for (int i : q) for (int k = 0; k < 3; k++) centre[k] += c[i][k] / 4.0;
                double out = 0;
                for (int k = 0; k < 3; k++) out += n[k] * (centre[k] - mid[k]);
                assertTrue(out > 0.05, "face " + f + " yaw " + yaw + " points inward");
                assertEquals(1.0, Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]), 1e-9);
            }
        }
    }
}
