package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.CueKinds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CueKindsTest {
    @Test
    void sfxStopsOnClear() {
        assertTrue(CueKinds.stopsAtEnd("sfx"));
        assertTrue(CueKinds.stopsAtEnd("voice"));
        assertFalse(CueKinds.stopsAtEnd("music"));
        assertFalse(CueKinds.stopsAtEnd("sound"));
    }
}
