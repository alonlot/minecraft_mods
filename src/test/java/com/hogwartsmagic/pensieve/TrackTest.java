package com.hogwartsmagic.pensieve;

import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.CameraPose;
import com.hogwartsmagic.pensieve.scene.CameraTrack;
import com.hogwartsmagic.pensieve.scene.Ease;
import com.hogwartsmagic.pensieve.scene.Keyframe;
import com.hogwartsmagic.pensieve.scene.Track;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackTest {
    @Test
    void holdsEndsAndInterpolates() {
        Track t = new Track(List.of(
                new Keyframe(0, new float[]{0}, Ease.LINEAR),
                new Keyframe(20, new float[]{10}, Ease.LINEAR)));
        assertArrayEquals(new float[]{0}, t.sample(-5), 1e-5f);
        assertArrayEquals(new float[]{5}, t.sample(10), 1e-5f);
        assertArrayEquals(new float[]{10}, t.sample(40), 1e-5f);
    }

    @Test
    void easeShapesTheSegmentEndingAtTheKey() {
        Track t = new Track(List.of(
                new Keyframe(0, new float[]{0}, Ease.LINEAR),
                new Keyframe(20, new float[]{10}, Ease.IN)));
        assertTrue(t.sample(10)[0] < 5);
    }

    /** Uneven keys, spline with an in_out key in the middle and a linear one at the end (every kind of run end). */
    private static final float[] TS = {0, 7, 15, 30, 36, 50, 61, 75};
    private static final float[][] VS = {{0, 2, 0}, {3, 2.5f, 1}, {4, 4, 3}, {9, 3, 2}, {9.5f, 3.2f, 4}, {6, 1, 7},
            {2, 1.5f, 8}, {0, 2, 6}};
    private static final String[] EASES = {null, "spline", "spline", "in_out", "spline", "spline", "spline", "linear"};

    @Test
    void splineMatchesCameraTrack() throws Exception {
        List<Keyframe> keys = new ArrayList<>();
        StringBuilder cam = new StringBuilder("[");
        for (int i = 0; i < TS.length; i++) {
            Ease e = EASES[i] == null ? Ease.IN_OUT : Ease.parse(EASES[i]);
            keys.add(new Keyframe(TS[i], VS[i], e));
            if (i > 0) cam.append(',');
            cam.append("{\"t\":").append(TS[i]).append(",\"pos\":[").append(VS[i][0]).append(',').append(VS[i][1])
                    .append(',').append(VS[i][2]).append("],\"look_at\":[0,0,100]");
            if (EASES[i] != null) cam.append(",\"ease\":\"").append(EASES[i]).append('"');
            cam.append('}');
        }
        Track track = new Track(keys);
        CameraTrack camera = CameraTrack.parse(JsonParser.parseString(cam.append(']').toString()).getAsJsonArray());
        for (float t = -2; t <= 78; t += 0.25f) {
            float[] v = track.sample(t);
            CameraPose c = camera.sample(t, id -> null);
            assertEquals(c.x(), v[0], 1e-6, "x at " + t);
            assertEquals(c.y(), v[1], 1e-6, "y at " + t);
            assertEquals(c.z(), v[2], 1e-6, "z at " + t);
        }
    }

    @Test
    void splineIsC1() {
        List<Keyframe> keys = new ArrayList<>();
        for (int i = 0; i < TS.length; i++) keys.add(new Keyframe(TS[i], VS[i], Ease.SPLINE));
        Track track = new Track(keys);
        for (int i = 1; i < TS.length - 1; i++) {
            float k = TS[i];
            for (int c = 0; c < 3; c++) {
                double before = (track.sample(k)[c] - track.sample(k - 0.01f)[c]) / 0.01;
                double after = (track.sample(k + 0.01f)[c] - track.sample(k)[c]) / 0.01;
                assertEquals(before, after, 0.02, "velocity through key " + i + " axis " + c);
            }
            assertArrayEquals(VS[i], track.sample(k), 1e-6f, "passes through key " + i);
        }
        // The same keys linear step their velocity at the keys: what the spline is for.
        List<Keyframe> lin = new ArrayList<>();
        for (int i = 0; i < TS.length; i++) lin.add(new Keyframe(TS[i], VS[i], Ease.LINEAR));
        Track l = new Track(lin);
        double before = (l.sample(15)[0] - l.sample(14.99f)[0]) / 0.01, after = (l.sample(15.01f)[0] - l.sample(15)[0]) / 0.01;
        assertTrue(Math.abs(before - after) > 0.1);
    }

    @Test
    void sampleWithinUsesOnlyTheWindowKeys() {
        Track t = new Track(List.of(
                new Keyframe(0, new float[]{0, 0, 0}, Ease.LINEAR),
                new Keyframe(10, new float[]{10, 0, 0}, Ease.LINEAR),
                new Keyframe(20, new float[]{100, 0, 0}, Ease.LINEAR),
                new Keyframe(30, new float[]{110, 0, 0}, Ease.LINEAR)));
        assertArrayEquals(new float[]{10, 0, 0}, t.sampleWithin(15, Float.NEGATIVE_INFINITY, 20), 1e-6f);
        assertArrayEquals(new float[]{100, 0, 0}, t.sampleWithin(15, 20, Float.POSITIVE_INFINITY), 1e-6f);
        assertArrayEquals(new float[]{105, 0, 0}, t.sampleWithin(25, 20, Float.POSITIVE_INFINITY), 1e-6f);
        assertEquals(null, t.sampleWithin(5, 11, 19));
    }
}
