package com.hogwartsmagic.spell.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceAudioTest {
    /** Half a second of quiet, a second of "speech" (a tone at {@code amp}), half a second of quiet. */
    private static float[] take(float amp) {
        float[] a = new float[VoiceAudio.RATE * 2];
        for (int i = VoiceAudio.RATE / 2; i < VoiceAudio.RATE * 3 / 2; i++) {
            a[i] = (float) (amp * Math.sin(i * 2 * Math.PI * 220 / VoiceAudio.RATE));
        }
        return a;
    }

    @Test
    void aQuietTakeIsRaisedToSpeechLevel() {
        float[] quiet = take(0.01f);
        float[] out = VoiceAudio.normalise(quiet);
        assertEquals(VoiceAudio.TARGET_LEVEL, VoiceAudio.speechLevel(out), 0.01);
    }

    @Test
    void gainIsCappedSoHissIsNotBlownUp() {
        float[] faint = take(0.001f);
        float[] out = VoiceAudio.normalise(faint);
        assertEquals(VoiceAudio.speechLevel(faint) * VoiceAudio.MAX_GAIN, VoiceAudio.speechLevel(out), 1e-4);
    }

    @Test
    void aHotTakeIsLoweredAndNeverClips() {
        float[] hot = take(0.99f);
        float[] out = VoiceAudio.normalise(hot);
        for (float f : out) {
            assertTrue(Math.abs(f) <= VoiceAudio.PEAK_CEILING + 1e-6);
        }
    }

    @Test
    void aTakeAlreadyAtLevelIsLeftAlone() {
        // A sine of amplitude A has RMS A / sqrt 2.
        float[] ok = take((float) (VoiceAudio.TARGET_LEVEL * Math.sqrt(2)));
        assertSame(ok, VoiceAudio.normalise(ok));
    }

    @Test
    void silenceStaysSilence() {
        float[] s = new float[VoiceAudio.RATE];
        assertSame(s, VoiceAudio.normalise(s));
    }

    @Test
    void wavRoundTrips() {
        float[] a = take(0.3f);
        float[] back = VoiceAudio.fromWav(VoiceAudio.toWav(a));
        assertEquals(a.length, back.length);
        for (int i = 0; i < a.length; i += 97) {
            assertEquals(a[i], back[i], 1.0 / 16000);
        }
        assertArrayEquals(new float[0], VoiceAudio.fromWav(VoiceAudio.toWav(new float[0])));
    }
}
