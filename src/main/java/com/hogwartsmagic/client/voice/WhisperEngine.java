package com.hogwartsmagic.client.voice;

import com.hogwartsmagic.spell.voice.IncantationMatcher;
import com.hogwartsmagic.spell.voice.VoiceAudio;
import com.hogwartsmagic.spell.voice.VoiceCastConfig;
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
 * Offline speech to text with whisper.cpp (through whisper-jni) and an English model ({@link VoiceCastConfig#MODEL}:
 * base.en by default, tiny.en for slow computers, both quantised). Everything runs on one low-priority worker thread,
 * never on the render thread. The models ship inside the mod jar and are copied to
 * {@code <game dir>/hogwartsmagic/voice/} on first use, because whisper.cpp reads a file. Changing the model in the
 * config swaps it before the next take.
 *
 * <p>Any failure (no native library for this machine, a broken model file) turns voice casting off for the
 * session with one message; it never crashes the game.</p>
 */
final class WhisperEngine {
    private static final Logger LOGGER = LogUtils.getLogger();
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

    /**
     * One transcribed take: the text, how long the model took, which model heard it, and the take as recorded
     * (before its level was evened out), so it can be saved for tuning.
     */
    record Heard(String text, long millis, VoiceCastConfig.Model model, float[] audio) {
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HogwartsMagic voice recogniser");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private volatile State state = State.UNLOADED;
    private volatile String failure = "";
    /** The spells the recogniser is primed with (set from the render thread: the spells the player knows). */
    private volatile String prompt = IncantationMatcher.recogniserPrompt();
    private WhisperJNI jni;
    private WhisperContext context;
    private volatile VoiceCastConfig.Model loaded;

    State state() {
        return state;
    }

    String failure() {
        return failure;
    }

    /** The model now loaded, or the configured one before the first load. */
    VoiceCastConfig.Model model() {
        VoiceCastConfig.Model m = loaded;
        return m != null ? m : VoiceCastConfig.MODEL.get();
    }

    void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    /** Starts loading in the background (the first press pays the load, not the first cast). */
    void warmUp() {
        if (state == State.UNLOADED) {
            state = State.LOADING;
            worker.execute(this::ensureLoaded);
        }
    }

    /** Transcribes one take. Completes with "" for silence; completes exceptionally when the engine failed. */
    CompletableFuture<Heard> transcribe(float[] audio, int threads) {
        warmUp();
        return CompletableFuture.supplyAsync(() -> {
            ensureLoaded();
            if (state != State.READY) {
                throw new IllegalStateException(failure.isEmpty() ? "voice engine not loaded" : failure);
            }
            VoiceCastConfig.Model model = loaded;
            if (VoiceAudio.rms(audio) < SILENCE_RMS) {
                return new Heard("", 0, model, audio);
            }
            // A quiet or far microphone is raised to speech level (and a hot one lowered) before the model hears it.
            float[] padded = pad(VoiceAudio.normalise(audio));
            int beam = VoiceCastConfig.BEAM.get();
            WhisperFullParams p = new WhisperFullParams(
                    beam > 1 ? WhisperSamplingStrategy.BEAN_SEARCH : WhisperSamplingStrategy.GREEDY);
            if (beam > 1) {
                p.beamSearchBeamSize = beam;
            }
            p.nThreads = threads;
            p.language = "en";
            p.noContext = true;
            p.singleSegment = true;
            p.suppressNonSpeechTokens = true;
            p.printProgress = false;
            p.printRealtime = false;
            p.printTimestamps = false;
            p.printSpecial = false;
            p.initialPrompt = prompt;
            long start = System.nanoTime();
            int code = jni.full(context, p, padded, padded.length);
            long millis = (System.nanoTime() - start) / 1_000_000L;
            if (code != 0) {
                throw new IllegalStateException("whisper returned " + code);
            }
            StringBuilder text = new StringBuilder();
            int segments = jni.fullNSegments(context);
            for (int i = 0; i < segments; i++) {
                text.append(jni.fullGetSegmentText(context, i));
            }
            String heard = text.toString().trim();
            // One line per take, so players can see the real speed on their own machine (logs/latest.log).
            LOGGER.info("Voice casting: heard \"{}\" in {} ms ({} s of speech, {} threads, {} model)", heard, millis,
                    String.format(java.util.Locale.ROOT, "%.1f", audio.length / (double) MicRecorder.TARGET_RATE),
                    threads, model.name().toLowerCase(java.util.Locale.ROOT));
            return new Heard(heard, millis, model, audio);
        }, worker);
    }

    /** On the worker: loads the configured model, or swaps to it when the config changed. A failure is final. */
    private void ensureLoaded() {
        if (state == State.FAILED) {
            return;
        }
        VoiceCastConfig.Model wanted = VoiceCastConfig.MODEL.get();
        if (state == State.READY && wanted == loaded) {
            return;
        }
        state = State.LOADING;
        try {
            Path model = extractModel(wanted.file);
            if (jni == null) {
                WhisperJNI.loadLibrary();
                WhisperJNI.setLibraryLogger(null); // whisper.cpp logs every call to stderr otherwise
                jni = new WhisperJNI();
            }
            if (context != null) {
                jni.free(context);
                context = null;
            }
            context = jni.init(model);
            loaded = wanted;
            state = State.READY;
            LOGGER.info("Voice casting: speech model {} loaded ({})", wanted.file, jni.getSystemInfo());
        } catch (Throwable t) { // UnsatisfiedLinkError on a machine with no native build is an Error
            failure = t.getClass().getSimpleName() + ": " + t.getMessage();
            state = State.FAILED;
            LOGGER.error("Voice casting: speech engine failed to start, voice casting is off", t);
        }
    }

    private static Path extractModel(String file) throws IOException {
        Path dir = FMLPaths.GAMEDIR.get().resolve("hogwartsmagic").resolve("voice");
        Path target = dir.resolve(file);
        try (InputStream in = WhisperEngine.class.getResourceAsStream("/voice/" + file)) {
            if (in == null) {
                throw new IOException("speech model missing from the mod jar: /voice/" + file);
            }
            Files.createDirectories(dir);
            Path tmp = dir.resolve(file + ".part");
            // Copied to a .part file each session (under a second) and moved into place only when it differs, so
            // a half-written file from a crash is never loaded.
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

    /** Silence before and after the take, and up to {@link #MIN_SAMPLES} in all. */
    static float[] pad(float[] audio) {
        float[] out = new float[Math.max(audio.length + PAD_SAMPLES * 2, MIN_SAMPLES)];
        System.arraycopy(audio, 0, out, PAD_SAMPLES, audio.length);
        return out;
    }
}
