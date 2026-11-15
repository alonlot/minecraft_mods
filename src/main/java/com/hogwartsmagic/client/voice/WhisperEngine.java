package com.hogwartsmagic.client.voice;

import com.hogwartsmagic.spell.voice.IncantationMatcher;
import com.mojang.logging.LogUtils;
import io.github.givimad.whisperjni.WhisperContext;
import io.github.givimad.whisperjni.WhisperFullParams;
import io.github.givimad.whisperjni.WhisperJNI;
import io.github.givimad.whisperjni.WhisperSamplingStrategy;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Offline speech to text with whisper.cpp (through whisper-jni) and the tiny English model, quantised to 32 MB.
 * Everything runs on one low-priority worker thread, never on the render thread. The model ships inside the mod
 * jar and is copied to {@code <game dir>/hogwartsmagic/voice/} on first use, because whisper.cpp reads a file.
 *
 * <p>Any failure (no native library for this machine, a broken model file) turns voice casting off for the
 * session with one message; it never crashes the game.</p>
 */
final class WhisperEngine {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String MODEL_FILE = "ggml-tiny.en-q5_1.bin";
    private static final String MODEL_RESOURCE = "/voice/" + MODEL_FILE;
    /**
     * whisper.cpp returns nothing for less than one second of audio, so takes are padded with silence: 0.3 s before
     * and after, 2 s in all at least. This is the padding the 96% spike result was measured with; a shorter pad
     * (0.2 s, 1.5 s) made the model loop ("Stupefyre, Stupefyre, ...") on a take it otherwise heard right.
     */
    private static final int PAD_SAMPLES = MicRecorder.TARGET_RATE * 3 / 10;
    private static final int MIN_SAMPLES = MicRecorder.TARGET_RATE * 2;
    /** Below this loudness the take is silence: skip the model (it invents "Thank you." from nothing). */
    private static final double SILENCE_RMS = 0.004;

    enum State { UNLOADED, LOADING, READY, FAILED }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HogwartsMagic voice recogniser");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private volatile State state = State.UNLOADED;
    private volatile String failure = "";
    private WhisperJNI jni;
    private WhisperContext context;

    State state() {
        return state;
    }

    String failure() {
        return failure;
    }

    /** Starts loading in the background (the first press pays the load, not the first cast). */
    void warmUp() {
        if (state == State.UNLOADED) {
            state = State.LOADING;
            worker.execute(this::loadOnWorker);
        }
    }

    /** Transcribes one take. Completes with "" for silence; completes exceptionally when the engine failed. */
    CompletableFuture<String> transcribe(float[] audio, int threads) {
        warmUp();
        return CompletableFuture.supplyAsync(() -> {
            if (state != State.READY) {
                throw new IllegalStateException(failure.isEmpty() ? "voice engine not loaded" : failure);
            }
            if (rms(audio) < SILENCE_RMS) {
                return "";
            }
            float[] padded = pad(audio);
            WhisperFullParams p = new WhisperFullParams(WhisperSamplingStrategy.GREEDY);
            p.nThreads = threads;
            p.language = "en";
            p.noContext = true;
            p.singleSegment = true;
            p.suppressNonSpeechTokens = true;
            p.printProgress = false;
            p.printRealtime = false;
            p.printTimestamps = false;
            p.printSpecial = false;
            p.initialPrompt = IncantationMatcher.recogniserPrompt();
            int code = jni.full(context, p, padded, padded.length);
            if (code != 0) {
                throw new IllegalStateException("whisper returned " + code);
            }
            StringBuilder text = new StringBuilder();
            int segments = jni.fullNSegments(context);
            for (int i = 0; i < segments; i++) {
                text.append(jni.fullGetSegmentText(context, i));
            }
            return text.toString().trim();
        }, worker);
    }

    private void loadOnWorker() {
        try {
            Path model = extractModel();
            WhisperJNI.loadLibrary();
            WhisperJNI.setLibraryLogger(null); // whisper.cpp logs every call to stderr otherwise
            jni = new WhisperJNI();
            context = jni.init(model);
            state = State.READY;
            LOGGER.info("Voice casting: speech model loaded ({})", jni.getSystemInfo());
        } catch (Throwable t) { // UnsatisfiedLinkError on a machine with no native build is an Error
            failure = t.getClass().getSimpleName() + ": " + t.getMessage();
            state = State.FAILED;
            LOGGER.error("Voice casting: speech engine failed to start, voice casting is off", t);
        }
    }

    private static Path extractModel() throws IOException {
        Path dir = FMLPaths.GAMEDIR.get().resolve("hogwartsmagic").resolve("voice");
        Path target = dir.resolve(MODEL_FILE);
        try (InputStream in = WhisperEngine.class.getResourceAsStream(MODEL_RESOURCE)) {
            if (in == null) {
                throw new IOException("speech model missing from the mod jar: " + MODEL_RESOURCE);
            }
            Files.createDirectories(dir);
            Path tmp = dir.resolve(MODEL_FILE + ".part");
            // Copied to a .part file each session (32 MB, well under a second) and moved into place only when it
            // differs, so a half-written file from a crash is never loaded.
            long size = Files.exists(target) ? Files.size(target) : -1;
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            if (Files.size(tmp) == size) {
                Files.deleteIfExists(tmp);
            } else {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
        }
        return target;
    }

    static double rms(float[] audio) {
        if (audio.length == 0) {
            return 0.0;
        }
        double sum = 0;
        for (float f : audio) {
            sum += f * f;
        }
        return Math.sqrt(sum / audio.length);
    }

    /** Silence before and after the take, and up to {@link #MIN_SAMPLES} in all. */
    static float[] pad(float[] audio) {
        float[] out = new float[Math.max(audio.length + PAD_SAMPLES * 2, MIN_SAMPLES)];
        System.arraycopy(audio, 0, out, PAD_SAMPLES, audio.length);
        return out;
    }
}
