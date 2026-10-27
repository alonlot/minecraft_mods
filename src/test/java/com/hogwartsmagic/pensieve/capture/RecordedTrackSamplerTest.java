package com.hogwartsmagic.pensieve.capture;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordedTrackSamplerTest {
    private static Recording.Track track(int first, int removal, float[] x, float[] yaw) {
        float[] z = new float[x.length];
        return new Recording.Track(0, "minecraft:zombie", null, "Z", false, null, null, first,
                first + x.length - 1, removal, x, z, z, yaw, yaw, yaw, z, List.of(), List.of(), List.of());
    }

    @Test
    void lerpsBetweenTicks() {
        Recording.Track t = track(10, Recording.REMOVAL_NONE, new float[] {0, 2}, new float[] {0, 0});
        assertEquals(1f, RecordedTrackSampler.pose(t, 10.5f)[0], 1e-6);
        assertEquals(0f, RecordedTrackSampler.pose(t, 5f)[0], 1e-6);
        assertEquals(2f, RecordedTrackSampler.pose(t, 30f)[0], 1e-6);
    }

    @Test
    void emptyArraysGiveZeros() {
        Recording.Track t = track(0, Recording.REMOVAL_NONE, new float[0], new float[0]);
        assertEquals(7, RecordedTrackSampler.pose(t, 3f).length);
    }

    @Test
    void yawWrapsShortWay() {
        Recording.Track t = track(0, Recording.REMOVAL_NONE, new float[] {0, 0}, new float[] {179, -179});
        assertEquals(180f, Math.abs(RecordedTrackSampler.pose(t, 0.5f)[3]), 1e-4);
    }

    @Test
    void absentOutsideLife() {
        Recording.Track t = track(10, Recording.REMOVAL_GONE, new float[] {0, 1, 2}, new float[3]);
        assertFalse(RecordedTrackSampler.present(t, 9.9f));
        assertTrue(RecordedTrackSampler.present(t, 10f));
        assertTrue(RecordedTrackSampler.present(t, 12f));
        assertFalse(RecordedTrackSampler.present(t, 12.1f));
    }

    @Test
    void killedLingersForDeathAnimation() {
        Recording.Track t = track(10, Recording.REMOVAL_KILLED, new float[] {0, 1, 2}, new float[3]);
        assertTrue(RecordedTrackSampler.present(t, 32f));
        assertFalse(RecordedTrackSampler.present(t, 32.1f));
    }

    @Test
    void patchesUpToStopsAfterTick() {
        List<Recording.Act> acts = List.of(new Recording.Act(1, 0), new Recording.Act(5, 0),
                new Recording.Act(5, 2), new Recording.Act(9, 3));
        assertEquals(3, RecordedTrackSampler.patchesUpTo(acts, 0, 5));
        assertEquals(4, RecordedTrackSampler.patchesUpTo(acts, 3, 9));
        assertEquals(0, RecordedTrackSampler.patchesUpTo(acts, 0, 0));
    }
}
