package com.hogwartsmagic.client.voice;

import com.hogwartsmagic.spell.voice.VoiceAudio;
import com.hogwartsmagic.spell.voice.VoiceCastConfig;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Keeps each voice take as a WAV file with a row in {@code takes.tsv} (what was heard, what it matched, the model)
 * in {@code <game dir>/hogwartsmagic/voice/takes/}, only while {@link VoiceCastConfig#SAVE_TAKES} is on. Practice
 * takes carry the spell that was being said, so they are a labelled test set of the player's own voice for
 * {@code tools/spell_voice/run_bench.sh}. Written on its own thread; a failure only logs.
 */
final class TakeLog {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The newest takes kept; older WAV files are deleted (about 64 KB a take). */
    static final int KEEP = 1000;
    private static final String HEADER = "file\tmode\texpected\theard\tmatched\tdistance\tmargin\tmodel\tms\tbeam";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT);
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HogwartsMagic voice takes");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static int sinceTrim;

    private TakeLog() {
    }

    static Path dir() {
        return FMLPaths.GAMEDIR.get().resolve("hogwartsmagic").resolve("voice").resolve("takes");
    }

    /**
     * Saves one take when saving is on. {@code mode} is ptt, open or practice; {@code expected} the spell id being
     * practised ("" otherwise); {@code matched} the spell it cast or would cast ("" for none).
     */
    static void save(WhisperEngine.Heard heard, String mode, String expected, String matched, double distance,
                     double margin) {
        if (!VoiceCastConfig.SAVE_TAKES.get() || heard == null || heard.audio().length == 0) {
            return;
        }
        int beam = VoiceCastConfig.BEAM.get();
        String name = LocalDateTime.now().format(STAMP) + "-" + mode + ".wav";
        String row = String.join("\t", name, mode, expected, clean(heard.text()), matched,
                String.format(Locale.ROOT, "%.3f", distance), String.format(Locale.ROOT, "%.3f", margin),
                heard.model().name().toLowerCase(Locale.ROOT), Long.toString(heard.millis()), Integer.toString(beam));
        float[] audio = heard.audio();
        IO.execute(() -> write(name, audio, row));
    }

    private static void write(String name, float[] audio, String row) {
        try {
            Path dir = dir();
            Files.createDirectories(dir);
            Files.write(dir.resolve(name), VoiceAudio.toWav(audio));
            Path tsv = dir.resolve("takes.tsv");
            List<String> lines = new ArrayList<>();
            if (!Files.exists(tsv)) {
                lines.add(HEADER);
            }
            lines.add(row);
            Files.write(tsv, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (++sinceTrim >= 50) {
                sinceTrim = 0;
                trim(dir);
            }
        } catch (IOException e) {
            LOGGER.warn("Voice casting: could not save a take: {}", e.toString());
        }
    }

    /** Deletes the oldest WAV files past {@link #KEEP} (names start with the time, so name order is age order). */
    private static void trim(Path dir) throws IOException {
        List<Path> wavs;
        try (Stream<Path> s = Files.list(dir)) {
            wavs = s.filter(p -> p.getFileName().toString().endsWith(".wav")).sorted().toList();
        }
        for (int i = 0; i < wavs.size() - KEEP; i++) {
            Files.deleteIfExists(wavs.get(i));
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
