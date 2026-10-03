package com.hogwartsmagic.spell.mastery;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every spell must say what it gains at ranks II, III and IV (the Tome tooltip and the rank-up chat
 * read these lines). Fails when a spell is added to {@code SpellType} without them. SpellType cannot be
 * loaded in a plain unit test (its particle constants need the Minecraft bootstrap), so the spell ids
 * are read from its source: every enum constant starts a line with {@code NAME("id", ...}.
 */
class RankChangeTextTest {
    private static final Path LANG = Path.of("src/main/resources/assets/hogwartsmagic/lang/en_us.json");

    private static final Path SPELL_TYPE = Path.of("src/main/java/com/hogwartsmagic/spell/SpellType.java");
    private static final Pattern CONSTANT = Pattern.compile("^    [A-Z][A-Z_]*\\(\"([a-z_]+)\"", Pattern.MULTILINE);

    private static List<String> spellIds() throws IOException {
        Matcher matcher = CONSTANT.matcher(Files.readString(SPELL_TYPE, StandardCharsets.UTF_8));
        List<String> ids = new ArrayList<>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    private static JsonObject lang() throws IOException {
        try (Reader reader = Files.newBufferedReader(LANG, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static boolean hasText(JsonObject lang, String key) {
        return lang.has(key) && lang.get(key).isJsonPrimitive() && !lang.get(key).getAsString().isBlank();
    }

    @Test
    void everySpellHasALineForRanksTwoThreeAndFour() throws IOException {
        JsonObject lang = lang();
        List<String> missing = new ArrayList<>();
        List<String> ids = spellIds();
        assertFalse(ids.isEmpty(), "found no spell ids in SpellType.java");
        for (String id : ids) {
            for (int rank = 2; rank <= MasteryMath.MAX_RANK; rank++) {
                String key = "spell.hogwartsmagic." + id + ".rank." + rank;
                if (!hasText(lang, key)) {
                    missing.add(key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "Missing rank-change lines: " + missing);
    }

    @Test
    void genericKeysExist() throws IOException {
        JsonObject lang = lang();
        for (String key : new String[] {
                "gui.hogwartsmagic.mastery.upgrade_preview", "gui.hogwartsmagic.mastery.upgrade_generic",
                "gui.hogwartsmagic.mastery.changed", "gui.hogwartsmagic.mastery.at_master"}) {
            assertTrue(hasText(lang, key), key);
        }
    }
}
