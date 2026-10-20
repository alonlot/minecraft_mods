package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.Ease;
import com.hogwartsmagic.pensieve.scene.Keyframe;
import com.hogwartsmagic.pensieve.scene.TapeTrail;
import com.hogwartsmagic.pensieve.scene.Track;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TapeTrailTest {
    private static Track line() {
        return new Track(List.of(new Keyframe(0, new float[]{0, 0, 0}, Ease.LINEAR),
                new Keyframe(40, new float[]{4, 0, 0}, Ease.LINEAR)));
    }

    /** A rising spiral round the origin, one turn per 40 ticks, as the tape measures the boy. */
    private static Track spiral() {
        List<Keyframe> keys = new ArrayList<>();
        for (int t = 0; t <= 120; t += 2) {
            double a = t / 40.0 * Math.PI * 2;
            keys.add(new Keyframe(t, new float[]{(float) Math.cos(a) * 0.6f, 0.2f + t * 0.012f, (float) Math.sin(a) * 0.6f},
                    Ease.LINEAR));
        }
        return new Track(keys);
    }

    private static float length(float[][] pts) {
        float sum = 0;
        for (int i = 1; i < pts.length; i++) sum += dist(pts[i - 1], pts[i]);
        return sum;
    }

    private static float dist(float[] a, float[] b) {
        float dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Test
    void startsAtTheHeadAndKeepsItsLength() {
        for (Track path : new Track[]{line(), spiral()}) {
            for (float t : new float[]{0f, 3f, 20f, 39f, 80f, 200f}) {
                float[][] pts = TapeTrail.trail(path, t, 1.5f, 24);
                assertEquals(24, pts.length);
                assertArrayEquals(new float[]{0, 0, 0}, pts[0], 1e-6f);
                assertEquals(1.5f, length(pts), 0.03f, "t=" + t);
                // Evenly spaced along the tape.
                float seg = 1.5f / 23f;
                for (int i = 1; i < pts.length; i++) {
                    assertTrue(dist(pts[i - 1], pts[i]) <= seg + 0.02f, "segment " + i + " at t=" + t);
                }
            }
        }
    }

    @Test
    void followsItsOwnPathBehindTheHead() {
        // Halfway along the line at 0.1 blocks a tick: the tape lies back along -x.
        float[][] pts = TapeTrail.trail(line(), 20, 1.0f, 11);
        assertArrayEquals(new float[]{-1f, 0f, 0f}, pts[10], 0.02f);
        assertArrayEquals(new float[]{-0.5f, 0f, 0f}, pts[5], 0.02f);
    }

    @Test
    void whatThePathDoesNotCoverHangsDown() {
        // At the very start there is no path behind the head: the whole tape hangs straight down.
        float[][] pts = TapeTrail.trail(line(), 0, 1.0f, 11);
        assertArrayEquals(new float[]{0f, -1f, 0f}, pts[10], 0.02f);
        // Holding still long after the path ends: the same.
        pts = TapeTrail.trail(line(), 400, 1.0f, 11);
        assertArrayEquals(new float[]{0f, -1f, 0f}, pts[10], 0.02f);
        // Partly covered: back along the path, then down.
        pts = TapeTrail.trail(line(), 5, 1.0f, 11);
        assertArrayEquals(new float[]{-0.5f, -0.5f, 0f}, pts[10], 0.03f);
        // An empty path is a hanging tape too.
        pts = TapeTrail.trail(Track.EMPTY, 5, 1.0f, 3);
        assertArrayEquals(new float[]{0f, -1f, 0f}, pts[2], 1e-4f);
    }

    @Test
    void deterministic() {
        float[][] a = TapeTrail.trail(spiral(), 57.25f, 1.5f, 24);
        float[][] b = TapeTrail.trail(spiral(), 57.25f, 1.5f, 24);
        for (int i = 0; i < a.length; i++) assertArrayEquals(a[i], b[i]);
    }
}
