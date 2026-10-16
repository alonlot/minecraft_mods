package com.hogwartsmagic.assignment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the shipped assignment files against the rules the design promises: one per spell, the task
 * size set by the spell's rarity, no task that needs the spell it teaches, prerequisites never rarer
 * than the spell they unlock and never circular, and every line translated. Reads the source files
 * directly (SpellType itself needs a running game to load).
 */
class AssignmentDataTest {
    private static final Path DATA = Path.of("src/main/resources/data/hogwartsmagic/assignments");
    private static final Path LANG = Path.of("src/main/resources/assets/hogwartsmagic/lang/en_us.json");
    private static final Path SPELLS = Path.of("src/main/java/com/hogwartsmagic/spell/SpellType.java");
    private static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC");
    private static final Set<String> DARK_TEACHERS = Set.of("prince", "death_eater");

    private static Map<String, Integer> spellRarities() throws IOException {
        Map<String, Integer> out = new HashMap<>();
        Matcher m = Pattern.compile("^    [A-Z_]+\\(\"([a-z_]+)\".*?SpellLootRarity\\.([A-Z]+)", Pattern.MULTILINE)
                .matcher(Files.readString(SPELLS, StandardCharsets.UTF_8));
        while (m.find()) {
            out.put(m.group(1), RARITIES.indexOf(m.group(2)));
        }
        return out;
    }

    private static Map<String, AssignmentDef> load() throws IOException {
        Map<String, AssignmentDef> out = new HashMap<>();
        try (Stream<Path> files = Files.list(DATA)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                assertTrue(name.endsWith(".json"), name);
                String spell = name.substring(0, name.length() - 5);
                JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                out.put(spell, AssignmentParser.parse(spell, json));
            }
        }
        return out;
    }

    @Test
    void everySpellHasExactlyOneAssignment() throws IOException {
        Map<String, Integer> spells = spellRarities();
        assertEquals(41, spells.size(), "SpellType parse");
        assertEquals(spells.keySet(), load().keySet());
    }

    @Test
    void taskSizeFollowsSpellRarity() throws IOException {
        Map<String, Integer> rarity = spellRarities();
        int[] min = {1, 2, 3, 3};
        int[] max = {2, 2, 3, 4};
        for (AssignmentDef def : load().values()) {
            int r = rarity.get(def.spellId());
            int lines = def.objectives().size();
            assertTrue(lines >= min[r] && lines <= max[r],
                    def.spellId() + " is " + RARITIES.get(r) + " but has " + lines + " lines");
        }
    }

    @Test
    void noTaskNeedsTheSpellItTeaches() throws IOException {
        for (AssignmentDef def : load().values()) {
            for (ObjectiveDef objective : def.objectives()) {
                if (objective.type() == ObjectiveType.CAST || objective.type() == ObjectiveType.KNOW_SPELL) {
                    assertFalse(objective.targets().contains(def.spellId()), def.spellId());
                }
            }
        }
    }

    @Test
    void prerequisitesAreRealNotRarerAndNotCircular() throws IOException {
        Map<String, Integer> rarity = spellRarities();
        Map<String, AssignmentDef> defs = load();
        for (AssignmentDef def : defs.values()) {
            for (ObjectiveDef objective : def.objectives()) {
                if (objective.type() != ObjectiveType.KNOW_SPELL && objective.type() != ObjectiveType.CAST) {
                    continue;
                }
                for (String needed : objective.targets()) {
                    assertTrue(rarity.containsKey(needed), def.spellId() + " needs unknown spell " + needed);
                    assertTrue(rarity.get(needed) <= rarity.get(def.spellId()),
                            def.spellId() + " needs the rarer " + needed);
                }
            }
            Set<String> seen = new HashSet<>();
            String current = def.spellId();
            while (current != null) {
                assertTrue(seen.add(current), "prerequisite loop through " + current);
                String next = null;
                for (ObjectiveDef objective : defs.get(current).objectives()) {
                    if (objective.type() == ObjectiveType.KNOW_SPELL) {
                        next = objective.targets().get(0);
                    }
                }
                current = next;
            }
        }
    }

    @Test
    void darkOrdersComeOnlyFromDarkTeachers() throws IOException {
        for (AssignmentDef def : load().values()) {
            assertEquals(DARK_TEACHERS.contains(def.teacher()), def.dark(), def.spellId());
        }
    }

    @Test
    void everyLineIsTranslated() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(LANG, StandardCharsets.UTF_8)).getAsJsonObject();
        for (AssignmentDef def : load().values()) {
            assertTrue(lang.has(def.quoteKey()), def.quoteKey());
            assertTrue(lang.has(def.teacherKey()), def.teacherKey());
            for (int i = 0; i < def.objectives().size(); i++) {
                assertTrue(lang.has(def.objectiveKey(i)), def.objectiveKey(i));
                String text = lang.get(def.objectiveKey(i)).getAsString();
                if (def.objectives().get(i).count() > 1) {
                    assertTrue(text.contains("%s"), def.objectiveKey(i) + " must show its count");
                }
                assertNotEquals("", text.trim());
            }
        }
    }
}
