package com.hogwartsmagic.spell.voice;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuts an open microphone stream (16 kHz mono floats) into utterances for the open mic mode. Pure Java, unit tested.
 *
 * <p>Energy based, in 20 ms frames. A frame is loud when its RMS is over three times the noise floor (and over a
 * fixed minimum). Three loud frames in a row start an utterance, with 300 ms of audio from before the start kept so
 * the first consonant is not cut. 400 ms of quiet ends it. The noise floor follows the quiet frames, and slowly
 * follows loud ones too, so a fan or steady music stops counting as speech.</p>
 *
 * <p>A spell takes under 3 s to say. An utterance that runs past {@link #MAX_FRAMES} (4 s) is talk or noise: it is
 * dropped, and nothing new starts until the room is quiet again, so the end of a long sentence ("... his Stupefy")
 * never becomes an utterance of its own.</p>
 */
public final class SpeechSegmenter {
    public static final int RATE = 16000;
    public static final int FRAME = RATE / 50; // 20 ms
    static final int START_FRAMES = 3;
    static final int END_FRAMES = 20;
    static final int PREROLL_FRAMES = 15;
    static final int MAX_FRAMES = 200;
    static final int MIN_VOICED_FRAMES = 8;
    static final double MIN_THRESHOLD = 0.006;
    static final double FLOOR_FACTOR = 3.0;
    private static final double FLOOR_QUIET_RATE = 0.05;
    private static final double FLOOR_LOUD_RATE = 0.002;

    private final ArrayDeque<float[]> preroll = new ArrayDeque<>();
    private final List<float[]> utterance = new ArrayList<>();
    private final float[] pending = new float[FRAME];
    private int pendingSize;
    private double floor = -1;
    private boolean inSpeech;
    private boolean waitForQuiet;
    private int loudRun;
    private int quietRun;
    private int voiced;

    /** Feeds samples in any chunk size; returns the utterances that ended inside them (often none). */
    public List<float[]> feed(float[] samples) {
        List<float[]> done = new ArrayList<>();
        int i = 0;
        while (i < samples.length) {
            int n = Math.min(FRAME - pendingSize, samples.length - i);
            System.arraycopy(samples, i, pending, pendingSize, n);
            pendingSize += n;
            i += n;
            if (pendingSize == FRAME) {
                float[] frame = pending.clone();
                pendingSize = 0;
                float[] out = frame(frame);
                if (out != null) {
                    done.add(out);
                }
            }
        }
        return done;
    }

    /** Forgets any utterance in progress (the wand was put away). The noise floor is kept. */
    public void reset() {
        preroll.clear();
        utterance.clear();
        pendingSize = 0;
        inSpeech = false;
        waitForQuiet = false;
        loudRun = 0;
        quietRun = 0;
        voiced = 0;
    }

    double noiseFloor() {
        return floor;
    }

    private float[] frame(float[] frame) {
        double rms = rms(frame);
        if (floor < 0) {
            floor = rms;
        }
        boolean loud = rms > Math.max(MIN_THRESHOLD, floor * FLOOR_FACTOR);
        if (!loud) {
            floor += (rms - floor) * FLOOR_QUIET_RATE;
        } else {
            floor += (rms - floor) * FLOOR_LOUD_RATE;
        }

        if (waitForQuiet) {
            quietRun = loud ? 0 : quietRun + 1;
            if (quietRun >= END_FRAMES) {
                waitForQuiet = false;
                quietRun = 0;
            }
            return null;
        }

        if (!inSpeech) {
            preroll.addLast(frame);
            if (preroll.size() > PREROLL_FRAMES) {
                preroll.removeFirst();
            }
            loudRun = loud ? loudRun + 1 : 0;
            if (loudRun >= START_FRAMES) {
                inSpeech = true;
                utterance.clear();
                utterance.addAll(preroll);
                preroll.clear();
                voiced = loudRun;
                quietRun = 0;
            }
            return null;
        }

        utterance.add(frame);
        if (loud) {
            voiced++;
            quietRun = 0;
        } else {
            quietRun++;
        }
        if (utterance.size() >= MAX_FRAMES) {
            endUtterance();
            waitForQuiet = true; // too long for a spell: talk or noise
            return null;
        }
        if (quietRun >= END_FRAMES) {
            float[] out = voiced >= MIN_VOICED_FRAMES ? join(utterance) : null;
            endUtterance();
            return out;
        }
        return null;
    }

    private void endUtterance() {
        inSpeech = false;
        utterance.clear();
        loudRun = 0;
        quietRun = 0;
        voiced = 0;
    }

    private static float[] join(List<float[]> frames) {
        float[] out = new float[frames.size() * FRAME];
        int at = 0;
        for (float[] f : frames) {
            System.arraycopy(f, 0, out, at, FRAME);
            at += FRAME;
        }
        return out;
    }

    static double rms(float[] frame) {
        double sum = 0;
        for (float f : frame) {
            sum += f * f;
        }
        return Math.sqrt(sum / frame.length);
    }
}
