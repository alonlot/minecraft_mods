package com.hogwartsmagic.client.voice;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;

/**
 * Records the microphone while the voice key is held. The line is opened on press and closed on release, so the
 * microphone is never open while the player is not casting (the system "microphone in use" light shows that).
 *
 * <p>The recogniser wants 16 kHz mono. Some devices only give 44.1 or 48 kHz, so those are tried next and the
 * samples are averaged down to 16 kHz.</p>
 */
final class MicRecorder {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final int TARGET_RATE = 16000;
    private static final float[] RATES = {16000f, 48000f, 44100f, 32000f};

    private TargetDataLine line;
    private Thread reader;
    private volatile boolean recording;
    private ByteArrayOutputStream bytes;
    private float rate;
    private int maxBytes;

    /** An open, started microphone line and its sample rate. */
    record OpenLine(TargetDataLine line, float rate) {
    }

    /**
     * Opens the default microphone as 16-bit mono, trying 16 kHz first and then the common device rates.
     * Returns null when none opens (no device, or the system refused microphone access).
     */
    static OpenLine openLine() {
        for (float r : RATES) {
            AudioFormat format = new AudioFormat(r, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                continue;
            }
            try {
                TargetDataLine l = (TargetDataLine) AudioSystem.getLine(info);
                l.open(format, (int) (r * 2 / 10)); // a 100 ms buffer keeps the release tail short
                l.start();
                return new OpenLine(l, r);
            } catch (LineUnavailableException | SecurityException | IllegalArgumentException e) {
                LOGGER.debug("Voice casting: microphone at {} Hz refused: {}", r, e.toString());
            }
        }
        LOGGER.warn("Voice casting: no microphone could be opened");
        return null;
    }

    /** Opens the microphone and starts recording. Returns false (and records nothing) when no microphone opens. */
    boolean start(int maxSeconds) {
        stopQuietly();
        OpenLine open = openLine();
        if (open == null) {
            return false;
        }
        line = open.line();
        rate = open.rate();
        maxBytes = (int) (rate * 2 * maxSeconds);
        bytes = new ByteArrayOutputStream((int) (rate * 2 * 2));
        recording = true;
        reader = new Thread(this::readLoop, "HogwartsMagic voice mic");
        reader.setDaemon(true);
        reader.start();
        return true;
    }

    boolean isRecording() {
        return recording;
    }

    private void readLoop() {
        byte[] buf = new byte[(int) (rate * 2 / 20)];
        TargetDataLine l = line;
        while (recording) {
            int n = l.read(buf, 0, buf.length);
            if (n <= 0) {
                continue;
            }
            synchronized (this) {
                if (bytes.size() + n > maxBytes) {
                    recording = false; // a stuck key never records for ever
                    break;
                }
                bytes.write(buf, 0, n);
            }
        }
    }

    /** Stops, closes the microphone and returns the take as 16 kHz mono floats in [-1, 1]. */
    float[] stop() {
        recording = false;
        Thread r = reader;
        if (r != null) {
            try {
                r.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        closeLine();
        byte[] raw;
        synchronized (this) {
            raw = bytes == null ? new byte[0] : bytes.toByteArray();
            bytes = null;
        }
        return toTargetRate(raw, rate);
    }

    void stopQuietly() {
        recording = false;
        closeLine();
        synchronized (this) {
            bytes = null;
        }
    }

    private void closeLine() {
        TargetDataLine l = line;
        line = null;
        if (l != null) {
            try {
                l.stop();
                l.close();
            } catch (RuntimeException ignored) {
                // a device pulled out mid-take: nothing to save
            }
        }
    }

    /** Little-endian 16-bit mono to floats, averaged down to 16 kHz when the device ran faster. */
    static float[] toTargetRate(byte[] raw, float rate) {
        int samples = raw.length / 2;
        float[] in = new float[samples];
        for (int i = 0; i < samples; i++) {
            short s = (short) ((raw[2 * i] & 0xFF) | (raw[2 * i + 1] << 8));
            in[i] = s / 32768f;
        }
        if (rate <= TARGET_RATE + 1) {
            return in;
        }
        double step = rate / TARGET_RATE;
        int outLen = (int) (samples / step);
        float[] out = new float[outLen];
        for (int o = 0; o < outLen; o++) {
            int from = (int) (o * step);
            int to = Math.min(samples, (int) ((o + 1) * step));
            float sum = 0;
            for (int i = from; i < to; i++) {
                sum += in[i];
            }
            out[o] = to > from ? sum / (to - from) : 0f;
        }
        return out;
    }
}
