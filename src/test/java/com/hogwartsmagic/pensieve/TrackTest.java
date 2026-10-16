package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.Ease;
import com.hogwartsmagic.pensieve.scene.Keyframe;
import com.hogwartsmagic.pensieve.scene.Track;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
}
