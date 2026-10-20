package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.BannerCloth;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannerClothTest {
    private static float[] params(int seed) {
        return BannerCloth.params(seed);
    }

    @Test
    void topEdgeNeverMoves() {
        for (int seed = 0; seed < 40; seed++) {
            float[] p = params(seed);
            for (float t = 0; t < 120; t += 0.37f) {
                for (int i = 0; i <= BannerCloth.COLS; i++) {
                    float[] o = BannerCloth.offset(p, i / (float) BannerCloth.COLS, 0f, t);
                    assertArrayEquals(new float[]{0f, 0f, 0f}, o, 0f, "seed " + seed + " t " + t);
                }
            }
        }
    }

    @Test
    void isBounded() {
        float maxDz = 0f;
        for (int seed = 0; seed < 40; seed++) {
            float[] p = params(seed);
            for (float t = 0; t < 300; t += 0.25f) {
                for (int j = 0; j <= BannerCloth.ROWS; j++) {
                    for (int i = 0; i <= BannerCloth.COLS; i++) {
                        float[] o = BannerCloth.offset(p, i / (float) BannerCloth.COLS, j / (float) BannerCloth.ROWS, t);
                        assertTrue(Math.abs(o[2]) <= BannerCloth.SWING + BannerCloth.RIPPLE + 1e-5f, "dz " + o[2]);
                        assertTrue(Math.abs(o[0]) <= BannerCloth.DRIFT + 1e-5f, "dx " + o[0]);
                        assertTrue(o[1] >= 0f && o[1] < 0.02f, "dy " + o[1]);
                        maxDz = Math.max(maxDz, Math.abs(o[2]));
                    }
                }
            }
        }
        // it does sway visibly, not just a few millimetres
        assertTrue(maxDz > 0.15f, "max swing " + maxDz);
    }

    @Test
    void swayGrowsTowardTheFoot() {
        float[] p = params(7);
        float near = 0f, far = 0f;
        for (float t = 0; t < 60; t += 0.1f) {
            near = Math.max(near, Math.abs(BannerCloth.offset(p, 0.5f, 0.1f, t)[2]));
            far = Math.max(far, Math.abs(BannerCloth.offset(p, 0.5f, 1f, t)[2]));
        }
        assertTrue(near < 0.03f, "near the rod " + near);
        assertTrue(far > 4 * near, "foot " + far + " rod " + near);
    }

    @Test
    void isSmoothInTimeAndAlongTheCloth() {
        for (int seed = 0; seed < 20; seed++) {
            float[] p = params(seed);
            for (float t = 0; t < 120; t += 0.05f) {
                for (int j = 0; j <= BannerCloth.ROWS; j++) {
                    float s = j / (float) BannerCloth.ROWS;
                    float[] a = BannerCloth.offset(p, 0.3f, s, t);
                    float[] b = BannerCloth.offset(p, 0.3f, s, t + 0.05f);       // one tick later
                    for (int k = 0; k < 3; k++) assertTrue(Math.abs(a[k] - b[k]) < 0.03f, "tick step " + k);
                    if (j < BannerCloth.ROWS) {
                        float[] c = BannerCloth.offset(p, 0.3f, (j + 1) / (float) BannerCloth.ROWS, t);
                        for (int k = 0; k < 3; k++) assertTrue(Math.abs(a[k] - c[k]) < 0.06f, "row step " + k);
                    }
                }
            }
        }
    }

    @Test
    void deterministicPerActorAndDifferentBetweenActors() {
        UUID a = UUID.nameUUIDFromBytes("pensieve:banner_gryffindor_0".getBytes());
        UUID b = UUID.nameUUIDFromBytes("pensieve:banner_gryffindor_1".getBytes());
        long sa = BannerCloth.seed(a.getMostSignificantBits(), a.getLeastSignificantBits());
        long sa2 = BannerCloth.seed(a.getMostSignificantBits(), a.getLeastSignificantBits());
        long sb = BannerCloth.seed(b.getMostSignificantBits(), b.getLeastSignificantBits());
        assertEquals(sa, sa2);
        assertArrayEquals(BannerCloth.offset(BannerCloth.params(sa), 0.5f, 1f, 12.3f),
                BannerCloth.offset(BannerCloth.params(sa2), 0.5f, 1f, 12.3f), 0f);
        assertNotEquals(BannerCloth.offset(BannerCloth.params(sa), 0.5f, 1f, 12.3f)[2],
                BannerCloth.offset(BannerCloth.params(sb), 0.5f, 1f, 12.3f)[2]);
    }
}
