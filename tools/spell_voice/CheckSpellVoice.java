import com.hogwartsmagic.spell.voice.IncantationMatcher;
import io.github.givimad.whisperjni.WhisperContext;
import io.github.givimad.whisperjni.WhisperFullParams;
import io.github.givimad.whisperjni.WhisperJNI;
import io.github.givimad.whisperjni.WhisperSamplingStrategy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plays every spell voice line through the game's own recogniser: the shipped tiny.en model, the same whisper
 * params, prompt and silence padding as WhisperEngine, then IncantationMatcher both ways (push-to-talk and the
 * strict open mic). Each line must come back as its own spell. Run with run_check.sh. Exit 1 on any miss.
 */
public final class CheckSpellVoice {
    static final int RATE = 16000;
    static final int PAD = RATE * 3 / 10;
    static final int MIN = RATE * 2;

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Path voiceDir = root.resolve("src/main/resources/assets/hogwartsmagic/sounds/spells/voice");
        Path model = root.resolve("src/main/resources/voice/ggml-tiny.en-q5_1.bin");
        WhisperJNI.loadLibrary();
        WhisperJNI.setLibraryLogger(null);
        WhisperJNI jni = new WhisperJNI();
        WhisperContext ctx = jni.init(model);
        List<String> only = List.of(args).subList(1, args.length);
        int fails = 0;
        for (Map.Entry<String, String> e : IncantationMatcher.incantations().entrySet()) {
            String id = e.getKey();
            if (!only.isEmpty() && !only.contains(id)) {
                continue;
            }
            Path ogg = voiceDir.resolve(id + ".ogg");
            if (!Files.exists(ogg)) {
                System.out.printf("MISSING %-20s%n", id);
                fails++;
                continue;
            }
            float[] pcm = decode(ogg);
            float[] padded = new float[Math.max(pcm.length + PAD * 2, MIN)];
            System.arraycopy(pcm, 0, padded, PAD, pcm.length);
            WhisperFullParams p = new WhisperFullParams(WhisperSamplingStrategy.GREEDY);
            p.nThreads = 4;
            p.language = "en";
            p.noContext = true;
            p.singleSegment = true;
            p.suppressNonSpeechTokens = true;
            p.printProgress = false;
            p.printRealtime = false;
            p.printTimestamps = false;
            p.printSpecial = false;
            p.initialPrompt = IncantationMatcher.recogniserPrompt();
            if (jni.full(ctx, p, padded, padded.length) != 0) {
                throw new IllegalStateException("whisper failed on " + id);
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < jni.fullNSegments(ctx); i++) {
                sb.append(jni.fullGetSegmentText(ctx, i));
            }
            String heard = sb.toString().trim();
            IncantationMatcher.Result ptt = IncantationMatcher.match(heard, false);
            IncantationMatcher.Result open = IncantationMatcher.match(heard, true);
            boolean ok = id.equals(ptt.spellId()) && id.equals(open.spellId());
            if (!ok) {
                fails++;
            }
            System.out.printf("%s %-20s %5.2fs heard=%-28s ptt=%s open=%s d=%.2f m=%.2f%n", ok ? "OK  " : "FAIL", id,
                    pcm.length / (double) RATE, "\"" + heard + "\"", ptt.spellId(), open.spellId(), open.distance(),
                    open.margin());
        }
        System.out.println(fails == 0 ? "ALL RECOGNISED" : fails + " NOT RECOGNISED");
        System.exit(fails == 0 ? 0 : 1);
    }

    /** ffmpeg to 16 kHz mono float, the rate MicRecorder hands the recogniser. */
    static float[] decode(Path ogg) throws Exception {
        Process proc = new ProcessBuilder("ffmpeg", "-loglevel", "error", "-i", ogg.toString(), "-ac", "1", "-ar",
                String.valueOf(RATE), "-f", "f32le", "-").redirectError(ProcessBuilder.Redirect.INHERIT).start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = proc.getInputStream()) {
            in.transferTo(out);
        }
        proc.waitFor();
        ByteBuffer buf = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        float[] pcm = new float[buf.remaining() / 4];
        buf.asFloatBuffer().get(pcm);
        return pcm;
    }
}
