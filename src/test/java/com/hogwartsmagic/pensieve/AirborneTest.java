package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.Airborne;
import com.hogwartsmagic.pensieve.scene.Ease;
import com.hogwartsmagic.pensieve.scene.Keyframe;
import com.hogwartsmagic.pensieve.scene.Track;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirborneTest {
    private static Keyframe k(float t, float x, float y, float z, Ease e) {
        return new Keyframe(t, new float[]{x, y, z}, e);
    }

    @Test
    void emptyAndOneKeyArePerched() {
        assertFalse(Airborne.airborne(Track.EMPTY, 5));
        Track one = new Track(List.of(k(0, 3, 4, 5, Ease.LINEAR)));
        assertFalse(Airborne.airborne(one, -10));
        assertFalse(Airborne.airborne(one, 0));
        assertFalse(Airborne.airborne(one, 100, true));
    }

    @Test
    void fliesBetweenKeysAndPerchesWhenHeld() {
        Track t = new Track(List.of(k(10, 0, 0, 0, Ease.LINEAR), k(30, 10, 0, 0, Ease.LINEAR)));
        assertTrue(Airborne.airborne(t, 20));
        assertFalse(Airborne.airborne(t, 5));
        assertFalse(Airborne.airborne(t, 40));
    }

    @Test
    void aVerticalClimbFlies() {
        Track t = new Track(List.of(k(0, 0, 0, 0, Ease.LINEAR), k(20, 0, 4, 0, Ease.LINEAR)));
        assertTrue(Airborne.airborne(t, 10));
    }

    @Test
    void slowerThanHalfABlockASecondIsPerched() {
        // 0.4 blocks a second = 0.02 per tick
        Track t = new Track(List.of(k(0, 0, 0, 0, Ease.LINEAR), k(100, 2, 0, 0, Ease.LINEAR)));
        assertFalse(Airborne.airborne(t, 50));
    }

    @Test
    void anEaseOutLandingFliesUntilTheLastTicks() {
        Track t = new Track(List.of(k(0, 0, 0, 0, Ease.LINEAR), k(40, 12, 0, 0, Ease.OUT)));
        assertTrue(Airborne.airborne(t, 20));
        assertTrue(Airborne.airborne(t, 30));
        assertFalse(Airborne.airborne(t, 41));
    }

    @Test
    void hysteresisHoldsThroughASlowPatch() {
        // 0.36 blocks a second (0.018 per tick): between LAND_SPEED and FLY_SPEED
        Track t = new Track(List.of(k(0, 0, 0, 0, Ease.LINEAR), k(100, 1.8f, 0, 0, Ease.LINEAR)));
        assertTrue(Airborne.airborne(t, 50, true));
        assertFalse(Airborne.airborne(t, 50, false));
    }
}
