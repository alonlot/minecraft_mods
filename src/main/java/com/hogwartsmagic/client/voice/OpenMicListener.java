package com.hogwartsmagic.client.voice;

import com.hogwartsmagic.spell.voice.SpeechSegmenter;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.sound.sampled.TargetDataLine;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Open mic mode: keeps the microphone open while the player holds a wand, cuts the stream into utterances
 * ({@link SpeechSegmenter}) and sends each one to the speech engine. One utterance is worked on at a time; one that
 * ends while the engine is still busy is dropped, so talking never builds a queue and a late cast never fires
 * seconds after the words.
 */
final class OpenMicListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final WhisperEngine engine;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile boolean running;
    private Thread reader;
    private TargetDataLine line;

    OpenMicListener(WhisperEngine engine) {
        this.engine = engine;
    }

    boolean isRunning() {
        return running;
    }

    /**
     * Opens the microphone and starts listening. {@code onHeard} gets each transcript (or the error) on the engine
     * thread. Returns false when no microphone opens.
     */
    boolean start(int threads, BiConsumer<WhisperEngine.Heard, Throwable> onHeard) {
        stop();
        MicRecorder.OpenLine open = MicRecorder.openLine();
        if (open == null) {
            return false;
        }
        line = open.line();
        running = true;
        float rate = open.rate();
        TargetDataLine l = line;
        reader = new Thread(() -> readLoop(l, rate, threads, onHeard), "HogwartsMagic voice open mic");
        reader.setDaemon(true);
        reader.start();
        return true;
    }

    private void readLoop(TargetDataLine l, float rate, int threads, BiConsumer<WhisperEngine.Heard, Throwable> onHeard) {
        SpeechSegmenter segmenter = new SpeechSegmenter();
        byte[] buf = new byte[(int) (rate * 2 / 20)]; // 50 ms
        try {
            while (running) {
                int n = l.read(buf, 0, buf.length);
                if (n <= 0) {
                    continue;
                }
                byte[] chunk = n == buf.length ? buf : java.util.Arrays.copyOf(buf, n);
                for (float[] utterance : segmenter.feed(MicRecorder.toTargetRate(chunk, rate))) {
                    if (!busy.compareAndSet(false, true)) {
                        continue; // still working on the last one
                    }
                    engine.transcribe(utterance, threads).whenComplete((heard, error) -> {
                        busy.set(false);
                        onHeard.accept(heard, error);
                    });
                }
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Voice casting: open mic stopped: {}", e.toString());
            running = false;
        }
    }

    /** Closes the microphone. Safe to call when not running. */
    void stop() {
        running = false;
        TargetDataLine l = line;
        line = null;
        if (l != null) {
            try {
                l.stop();
                l.close(); // unblocks a read in progress
            } catch (RuntimeException ignored) {
                // a device pulled out: nothing to close
            }
        }
        Thread r = reader;
        reader = null;
        if (r != null) {
            try {
                r.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
