package com.hogwartsmagic.spell.voice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Audio helpers for voice casting, pure Java (unit tested, and shared with the offline benchmark in
 * {@code tools/spell_voice}). Samples are 16 kHz mono floats in [-1, 1], the form the microphone code hands over.
 */
public final class VoiceAudio {
    public static final int RATE = 16000;
    /** Frame length for the speech level, 20 ms. */
    static final int FRAME = RATE / 50;
    /** The level speech is brought to: the loud part of a take at about -22 dBFS, a close headset voice. */
    static final double TARGET_LEVEL = 0.08;
    /** A quiet microphone is raised at most this much, so a far voice is not turned into hiss. */
    static final double MAX_GAIN = 10.0;
    /** Gain never pushes the highest sample past this. */
    static final double PEAK_CEILING = 0.95;
    /** The loudest share of frames that counts as the speech (the rest is the pause around the word). */
    static final double LOUD_SHARE = 0.3;

    private VoiceAudio() {
    }

    /**
     * The level of the speech in a take: the RMS of its loudest {@link #LOUD_SHARE} of 20 ms frames, so the pause
     * before and after the word does not drag it down.
     */
    static double speechLevel(float[] audio) {
        int frames = audio.length / FRAME;
        if (frames == 0) {
            return rms(audio);
        }
        double[] levels = new double[frames];
        for (int f = 0; f < frames; f++) {
            double sum = 0;
            for (int i = f * FRAME; i < (f + 1) * FRAME; i++) {
                sum += audio[i] * audio[i];
            }
            levels[f] = sum / FRAME;
        }
        java.util.Arrays.sort(levels);
        int take = Math.max(1, (int) Math.round(frames * LOUD_SHARE));
        double sum = 0;
        for (int f = frames - take; f < frames; f++) {
            sum += levels[f];
        }
        return Math.sqrt(sum / take);
    }

    public static double rms(float[] audio) {
        if (audio.length == 0) {
            return 0.0;
        }
        double sum = 0;
        for (float f : audio) {
            sum += f * f;
        }
        return Math.sqrt(sum / audio.length);
    }

    /**
     * Brings the speech to {@link #TARGET_LEVEL}: a quiet or far microphone is raised (at most {@link #MAX_GAIN}),
     * a hot one lowered, and the peak never clips. Returns the input itself when it is already close.
     */
    public static float[] normalise(float[] audio) {
        double level = speechLevel(audio);
        if (level <= 1e-6) {
            return audio;
        }
        double peak = 0;
        for (float f : audio) {
            peak = Math.max(peak, Math.abs(f));
        }
        double gain = Math.min(TARGET_LEVEL / level, MAX_GAIN);
        if (peak > 0) {
            gain = Math.min(gain, PEAK_CEILING / peak);
        }
        if (gain > 0.8 && gain < 1.25) {
            return audio;
        }
        float[] out = new float[audio.length];
        for (int i = 0; i < audio.length; i++) {
            out[i] = (float) (audio[i] * gain);
        }
        return out;
    }

    /** 16-bit PCM mono WAV at {@link #RATE}. */
    public static byte[] toWav(float[] audio) {
        int dataBytes = audio.length * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + dataBytes).put(new byte[]{'W', 'A', 'V', 'E'});
        b.put(new byte[]{'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16);
        b.put(new byte[]{'d', 'a', 't', 'a'}).putInt(dataBytes);
        for (float f : audio) {
            float c = Math.max(-1f, Math.min(1f, f));
            b.putShort((short) Math.round(c * 32767f));
        }
        return b.array();
    }

    /** Reads a 16-bit PCM mono WAV (as {@link #toWav} writes). Other formats throw. */
    public static float[] fromWav(byte[] wav) {
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if (wav.length < 12 || b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157) {
            throw new IllegalArgumentException("not a WAV file");
        }
        int pos = 12;
        int channels = 0;
        int bits = 0;
        while (pos + 8 <= wav.length) {
            int id = b.getInt(pos);
            int size = b.getInt(pos + 4);
            int body = pos + 8;
            if (id == 0x20746d66) { // "fmt "
                channels = b.getShort(body + 2);
                bits = b.getShort(body + 14);
            } else if (id == 0x61746164) { // "data"
                if (channels != 1 || bits != 16) {
                    throw new IllegalArgumentException("want 16-bit mono, got " + bits + "-bit x" + channels);
                }
                int n = Math.min(size, wav.length - body) / 2;
                float[] out = new float[n];
                for (int i = 0; i < n; i++) {
                    out[i] = b.getShort(body + 2 * i) / 32768f;
                }
                return out;
            }
            pos = body + size + (size & 1);
        }
        throw new IllegalArgumentException("WAV file has no data");
    }
}
