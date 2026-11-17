package com.hogwartsmagic.spell.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceStampTest {
    @Test
    void oneCastPerSpokenSpell() {
        VoiceStamp s = new VoiceStamp("stupefy", false, 100);
        assertTrue(s.allowsCast("stupefy", false, false, 102));
        assertFalse(s.allowsCast("stupefy", false, false, 110)); // clicking again casts nothing
    }

    @Test
    void onlyTheSpellThatWasSaid() {
        VoiceStamp s = new VoiceStamp("stupefy", false, 100);
        assertFalse(s.allowsCast("bombarda", false, false, 101));
        assertTrue(s.allowsCast("stupefy", false, false, 101));
    }

    @Test
    void theStampGoesStale() {
        VoiceStamp s = new VoiceStamp("lumos", false, 100);
        assertFalse(s.allowsCast("lumos", false, false, 100 + VoiceStamp.WINDOW_TICKS + 1));
        VoiceStamp t = new VoiceStamp("lumos", false, 100);
        assertTrue(t.allowsCast("lumos", false, false, 100 + VoiceStamp.WINDOW_TICKS));
    }

    @Test
    void heartbeatsKeepAVoiceStartedHoldAliveForAsLongAsItIsHeld() {
        VoiceStamp s = new VoiceStamp("fiendfyre", false, 100);
        long t = 125; // right-click went down inside the 1.5 s window
        for (int i = 0; i < 40; i++) {
            assertTrue(s.allowsCast("fiendfyre", true, false, t));
            t += 20; // 40 s of holding, one heartbeat a second is already plenty
        }
    }

    @Test
    void aCastThenItsHold() {
        VoiceStamp s = new VoiceStamp("crucio", false, 100);
        assertTrue(s.allowsCast("crucio", false, false, 101)); // the beam
        assertTrue(s.allowsCast("crucio", true, false, 105)); // the held curse
        assertFalse(s.allowsCast("crucio", false, false, 106)); // no second beam
    }

    @Test
    void aTapAtTheEndOfTheHoldWindowStillCasts() {
        VoiceStamp s = new VoiceStamp("fiendfyre", false, 100);
        assertTrue(s.allowsCast("fiendfyre", false, false, 100 + 30 + 5)); // window end plus network time
    }

    @Test
    void selfCastOnlyWhenSpokenAsOne() {
        VoiceStamp self = new VoiceStamp("protego", true, 100);
        assertFalse(self.allowsCast("protego", false, false, 101)); // stamped as a self-cast
        assertTrue(self.allowsCast("protego", false, true, 101));
        VoiceStamp beam = new VoiceStamp("protego", false, 100);
        assertFalse(beam.allowsCast("protego", false, true, 101)); // a left-click after a spoken beam
        assertFalse(beam.allowsCast("protego", true, true, 101)); // no heartbeat is a self-cast
    }

    @Test
    void imperioHoldStartNeedsAFreshSpokenImperio() {
        VoiceStamp s = new VoiceStamp("imperio", false, 100);
        assertTrue(s.allowsCast("imperio", false, false, 101));
        assertTrue(s.allowsHoldStart("imperio", 120));
        assertFalse(new VoiceStamp("stupefy", false, 100).allowsHoldStart("imperio", 101));
        assertFalse(new VoiceStamp("imperio", false, 100).allowsHoldStart("imperio", 100 + VoiceStamp.WINDOW_TICKS + 1));
    }
}
