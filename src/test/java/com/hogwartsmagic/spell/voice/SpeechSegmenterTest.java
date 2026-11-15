package com.hogwartsmagic.spell.voice;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeechSegmenterTest {
    private static final int RATE = SpeechSegmenter.RATE;
    private final Random rnd = new Random(3);

    private float[] noise(double seconds, double level) {
        float[] out = new float[(int) (seconds * RATE)];
        for (int i = 0; i < out.length; i++) {
            out[i] = (float) (rnd.nextGaussian() * level);
        }
        return out;
    }

    /** A voice stand-in: a 220 Hz tone with some noise, loud enough to be speech. */
    private float[] voice(double seconds) {
        float[] out = noise(seconds, 0.002);
        for (int i = 0; i < out.length; i++) {
            out[i] += (float) (0.2 * Math.sin(2 * Math.PI * 220 * i / RATE));
        }
        return out;
    }

    private static float[] join(float[]... parts) {
        int n = 0;
        for (float[] p : parts) {
            n += p.length;
        }
        float[] out = new float[n];
        int at = 0;
        for (float[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }

    /** Feeds in 50 ms chunks, as the microphone thread does. */
    private static List<float[]> feed(SpeechSegmenter s, float[] audio) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < audio.length; i += 800) {
            float[] chunk = java.util.Arrays.copyOfRange(audio, i, Math.min(audio.length, i + 800));
            out.addAll(s.feed(chunk));
        }
        return out;
    }

    @Test
    void oneWordBetweenSilencesIsOneUtterance() {
        List<float[]> u = feed(new SpeechSegmenter(), join(noise(1.0, 0.002), voice(0.8), noise(1.0, 0.002)));
        assertEquals(1, u.size());
        // 0.8 s of speech plus up to 0.3 s before and 0.4 s of quiet after.
        double seconds = u.get(0).length / (double) RATE;
        assertTrue(seconds > 0.9 && seconds < 1.6, "length " + seconds);
    }

    @Test
    void theStartOfTheWordIsKept() {
        float[] audio = join(noise(1.0, 0.002), voice(0.6), noise(1.0, 0.002));
        float[] u = feed(new SpeechSegmenter(), audio).get(0);
        // The first frames of the utterance are quiet lead-in from before the start was detected.
        assertTrue(SpeechSegmenter.rms(java.util.Arrays.copyOf(u, SpeechSegmenter.FRAME)) < 0.01);
    }

    @Test
    void twoWordsWithAShortPauseStayTogether() {
        List<float[]> u = feed(new SpeechSegmenter(),
                join(noise(1.0, 0.002), voice(0.5), noise(0.2, 0.002), voice(0.5), noise(1.0, 0.002)));
        assertEquals(1, u.size()); // "Avada ... Kedavra"
    }

    @Test
    void twoWordsWithALongPauseAreTwoUtterances() {
        List<float[]> u = feed(new SpeechSegmenter(),
                join(noise(1.0, 0.002), voice(0.5), noise(0.8, 0.002), voice(0.5), noise(1.0, 0.002)));
        assertEquals(2, u.size());
    }

    @Test
    void aClickIsNotSpeech() {
        List<float[]> u = feed(new SpeechSegmenter(), join(noise(1.0, 0.002), voice(0.08), noise(1.0, 0.002)));
        assertEquals(0, u.size());
    }

    @Test
    void silenceAndSteadyNoiseGiveNothing() {
        assertEquals(0, feed(new SpeechSegmenter(), noise(5.0, 0.002)).size());
        assertEquals(0, feed(new SpeechSegmenter(), noise(5.0, 0.05)).size()); // a loud fan from the start
    }

    @Test
    void longTalkIsDroppedAndItsTailNeverBecomesAnUtterance() {
        // 6 s of talk with only short breaths: too long for a spell. The last words must not cast on their own.
        float[] talk = join(voice(1.5), noise(0.15, 0.002), voice(1.5), noise(0.15, 0.002), voice(1.5),
                noise(0.15, 0.002), voice(1.5));
        List<float[]> u = feed(new SpeechSegmenter(), join(noise(1.0, 0.002), talk, noise(1.0, 0.002)));
        assertEquals(0, u.size());
    }

    @Test
    void aSpellAfterLongTalkStillWorksOnceTheRoomIsQuiet() {
        float[] talk = join(voice(2.5), noise(0.15, 0.002), voice(2.5));
        List<float[]> u = feed(new SpeechSegmenter(),
                join(noise(1.0, 0.002), talk, noise(1.0, 0.002), voice(0.7), noise(1.0, 0.002)));
        assertEquals(1, u.size());
    }

    @Test
    void theNoiseFloorFollowsTheRoomDown() {
        SpeechSegmenter s = new SpeechSegmenter();
        feed(s, noise(2.0, 0.05));
        double loud = s.noiseFloor();
        feed(s, noise(2.0, 0.002));
        assertTrue(s.noiseFloor() < loud / 5, "floor " + s.noiseFloor() + " after " + loud);
        assertEquals(1, feed(s, join(voice(0.6), noise(1.0, 0.002))).size());
    }

    @Test
    void resetDropsAnUtteranceInProgress() {
        SpeechSegmenter s = new SpeechSegmenter();
        feed(s, join(noise(1.0, 0.002), voice(0.5)));
        s.reset();
        assertEquals(0, feed(s, noise(1.0, 0.002)).size());
    }
}
