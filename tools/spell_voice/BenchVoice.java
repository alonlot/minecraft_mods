import com.hogwartsmagic.spell.voice.IncantationMatcher;
import com.hogwartsmagic.spell.voice.VoiceAudio;
import io.github.givimad.whisperjni.WhisperContext;
import io.github.givimad.whisperjni.WhisperFullParams;
import io.github.givimad.whisperjni.WhisperJNI;
import io.github.givimad.whisperjni.WhisperSamplingStrategy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Compares speech settings on labelled clips, through the game's own matcher. Usage (run_bench.sh):
 *
 * <pre>BenchVoice ROOT MODEL_DIR [takes=GAME_DIR/hogwartsmagic/voice/takes] VARIANT...</pre>
 *
 * A VARIANT is {@code model=base,beam=5,gain=1,prompt=all,cond=quiet}: model tiny|base, beam 1 (greedy) or more,
 * gain 0|1 (VoiceAudio.normalise), prompt all|known (the target and 9 other spells, as a player who knows 10),
 * cond clean|quiet (the clip at -26 dB with a little hiss, a weak microphone). Clips: the incantation voice lines in
 * the mod's sounds (label from the file name), and, with takes=, every practice take in takes.tsv (label = the
 * spell practised; cond is ignored for those, they are real microphone audio).
 */
public final class BenchVoice {
    static final int PAD = VoiceAudio.RATE * 3 / 10;
    static final int MIN = VoiceAudio.RATE * 2;

    record Clip(String label, String source, float[] pcm) {
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Path modelDir = Path.of(args[1]);
        List<String> variants = new ArrayList<>();
        Path takes = null;
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("takes=")) {
                takes = Path.of(args[i].substring(6));
            } else {
                variants.add(args[i]);
            }
        }
        List<Clip> lines = new ArrayList<>();
        Path voiceDir = root.resolve("src/main/resources/assets/hogwartsmagic/sounds/spells/voice");
        for (String id : IncantationMatcher.incantations().keySet()) {
            Path ogg = voiceDir.resolve(id + ".ogg");
            if (Files.exists(ogg)) {
                lines.add(new Clip(id, "line", CheckSpellVoice.decode(ogg)));
            }
        }
        List<Clip> real = new ArrayList<>();
        if (takes != null && Files.exists(takes.resolve("takes.tsv"))) {
            for (String row : Files.readAllLines(takes.resolve("takes.tsv"), StandardCharsets.UTF_8)) {
                String[] f = row.split("\t", -1);
                if (f.length < 3 || f[0].equals("file") || f[2].isEmpty()) {
                    continue; // header, or a take that was not practice (no label)
                }
                Path wav = takes.resolve(f[0]);
                if (Files.exists(wav)) {
                    real.add(new Clip(f[2], f[0], VoiceAudio.fromWav(Files.readAllBytes(wav))));
                }
            }
        }
        System.out.printf("%d voice lines, %d practice takes%n", lines.size(), real.size());
        WhisperJNI.loadLibrary();
        WhisperJNI.setLibraryLogger(null);
        WhisperJNI jni = new WhisperJNI();
        Map<String, WhisperContext> contexts = new HashMap<>();
        List<String> summary = new ArrayList<>();
        for (String v : variants) {
            Map<String, String> o = parse(v);
            String model = o.getOrDefault("model", "tiny");
            WhisperContext ctx = contexts.computeIfAbsent(model, m -> {
                try {
                    return jni.init(modelDir.resolve("ggml-" + m + ".en-q5_1.bin"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            int beam = Integer.parseInt(o.getOrDefault("beam", "1"));
            boolean gain = o.getOrDefault("gain", "1").equals("1");
            boolean known = o.getOrDefault("prompt", "all").equals("known");
            boolean quiet = o.getOrDefault("cond", "clean").equals("quiet");
            List<Clip> set = new ArrayList<>();
            for (Clip c : lines) {
                set.add(quiet ? new Clip(c.label(), c.source(), weaken(c.pcm(), c.label().hashCode())) : c);
            }
            set.addAll(real);
            int ptt = 0;
            int open = 0;
            int wrong = 0;
            long ms = 0;
            List<String> misses = new ArrayList<>();
            for (Clip c : set) {
                float[] audio = gain ? VoiceAudio.normalise(c.pcm()) : c.pcm();
                float[] padded = new float[Math.max(audio.length + PAD * 2, MIN)];
                System.arraycopy(audio, 0, padded, PAD, audio.length);
                WhisperFullParams p = new WhisperFullParams(
                        beam > 1 ? WhisperSamplingStrategy.BEAN_SEARCH : WhisperSamplingStrategy.GREEDY);
                if (beam > 1) {
                    p.beamSearchBeamSize = beam;
                }
                p.nThreads = 4;
                p.language = "en";
                p.noContext = true;
                p.singleSegment = true;
                p.suppressNonSpeechTokens = true;
                p.printProgress = false;
                p.printRealtime = false;
                p.printTimestamps = false;
                p.printSpecial = false;
                p.initialPrompt = known ? IncantationMatcher.recogniserPrompt(knownSet(c.label()))
                        : IncantationMatcher.recogniserPrompt();
                long t0 = System.nanoTime();
                if (jni.full(ctx, p, padded, padded.length) != 0) {
                    throw new IllegalStateException("whisper failed on " + c.source());
                }
                ms += (System.nanoTime() - t0) / 1_000_000L;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < jni.fullNSegments(ctx); i++) {
                    sb.append(jni.fullGetSegmentText(ctx, i));
                }
                String heard = sb.toString().trim();
                String a = IncantationMatcher.match(heard, false).spellId();
                String b = IncantationMatcher.match(heard, true).spellId();
                if (c.label().equals(a)) {
                    ptt++;
                } else {
                    misses.add(c.label() + "=\"" + heard + "\"");
                }
                if (c.label().equals(b)) {
                    open++;
                }
                if ((a != null && !a.equals(c.label())) || (b != null && !b.equals(c.label()))) {
                    wrong++;
                }
            }
            String line = String.format("%-48s ptt %3d/%d  open %3d/%d  wrong %d  %4d ms/take", v, ptt, set.size(),
                    open, set.size(), wrong, set.isEmpty() ? 0 : ms / set.size());
            System.out.println(line);
            System.out.println("    misses: " + String.join(", ", misses));
            summary.add(line);
        }
        System.out.println("==== summary");
        summary.forEach(System.out::println);
    }

    static Map<String, String> parse(String v) {
        Map<String, String> o = new LinkedHashMap<>();
        for (String kv : v.split(",")) {
            String[] p = kv.split("=", 2);
            o.put(p[0], p.length > 1 ? p[1] : "");
        }
        return o;
    }

    /** The target spell and 9 others, fixed per target: a player who knows ten spells. */
    static List<String> knownSet(String target) {
        List<String> ids = new ArrayList<>(IncantationMatcher.incantations().keySet());
        ids.remove(target);
        java.util.Collections.shuffle(ids, new Random(target.hashCode()));
        List<String> out = new ArrayList<>(ids.subList(0, 9));
        out.add(target);
        return out;
    }

    /** -26 dB and a little hiss: a quiet or far microphone. */
    static float[] weaken(float[] pcm, long seed) {
        Random r = new Random(seed);
        float[] out = new float[pcm.length];
        for (int i = 0; i < pcm.length; i++) {
            out[i] = pcm[i] * 0.05f + (float) (r.nextGaussian() * 0.0015);
        }
        return out;
    }
}
