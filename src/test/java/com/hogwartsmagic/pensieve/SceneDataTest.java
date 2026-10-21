package com.hogwartsmagic.pensieve;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.hogwartsmagic.pensieve.scene.SceneValidator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parses and validates every shipped memory scene against names harvested from the source tree (the game
 * registries need a running game). Passes trivially while no scenes exist.
 */
class SceneDataTest {
    private static final Path MEMORIES = Path.of("src/main/resources/data/hogwartsmagic/memories");
    private static final Path JAVA = Path.of("src/main/java/com/hogwartsmagic");
    private static final Path STRUCTURES = Path.of("src/main/resources/data/hogwartsmagic/structure");
    private static final Path SOUNDS = Path.of("src/main/resources/assets/hogwartsmagic/sounds.json");
    private static final List<String> VANILLA_MOBS = List.of("zombie", "skeleton", "villager", "armor_stand", "bat",
            "spider", "cave_spider", "wither_skeleton", "evoker", "vindicator", "witch", "horse", "wolf", "cat", "fox",
            "sheep", "cow", "pig", "chicken", "squid", "glow_squid", "salmon", "cod", "lightning_bolt");

    /** A set that also contains every minecraft: id. */
    private static final class WithVanilla extends AbstractSet<String> {
        private final Set<String> own;

        WithVanilla(Set<String> own) {
            this.own = own;
        }

        @Override
        public boolean contains(Object o) {
            return o instanceof String s && (s.startsWith("minecraft:") || own.contains(s));
        }

        @Override
        public Iterator<String> iterator() {
            return own.iterator();
        }

        @Override
        public int size() {
            return own.size();
        }
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    private static Set<String> matches(Path file, String regex) throws IOException {
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile(regex, Pattern.MULTILINE).matcher(read(file));
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** Every register("name" call in the mod sources, as hogwartsmagic ids. */
    private static Set<String> modRegistered() throws IOException {
        Set<String> out = new HashSet<>();
        try (Stream<Path> files = Files.walk(JAVA)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String n : matches(f, "register\\(\\s*\"([a-z0-9_./]+)\"")) out.add("hogwartsmagic:" + n);
            }
        }
        return out;
    }

    private static SceneNames harvest() throws IOException {
        Set<String> spells = matches(JAVA.resolve("spell/SpellType.java"), "^    [A-Z_]+\\(\"([a-z_]+)\"");
        Set<String> registered = modRegistered();
        Set<String> entityIds = new HashSet<>();
        for (String e : matches(JAVA.resolve("entity/ModEntities.java"), "register\\(\\s*\"([a-z0-9_]+)\"")) {
            entityIds.add("hogwartsmagic:" + e);
        }
        for (String v : VANILLA_MOBS) entityIds.add("minecraft:" + v);
        entityIds.add("player");
        Set<String> sounds = new HashSet<>(registered);
        if (Files.exists(SOUNDS)) {
            for (String k : JsonParser.parseString(read(SOUNDS)).getAsJsonObject().keySet()) sounds.add("hogwartsmagic:" + k);
        }
        Set<String> templates = new HashSet<>();
        try (Stream<Path> files = Files.list(STRUCTURES)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".nbt")).toList()) {
                String n = f.getFileName().toString();
                templates.add("hogwartsmagic:" + n.substring(0, n.length() - 4));
            }
        }
        Set<String> skins = new HashSet<>();
        Path skinList = MEMORIES.resolve("_skins.json");
        if (Files.exists(skinList)) {
            JsonArray a = JsonParser.parseString(read(skinList)).getAsJsonArray();
            a.forEach(e -> skins.add(e.getAsString()));
        }
        return new SceneNames(entityIds, spells, new WithVanilla(sounds), new WithVanilla(registered),
                new WithVanilla(registered), templates, new WithVanilla(Set.of()), new WithVanilla(registered), skins);
    }

    @Test
    void spellVisualsAreRealSpells() throws IOException {
        Set<String> spells = matches(JAVA.resolve("spell/SpellType.java"), "^    [A-Z_]+\\(\"([a-z_]+)\"");
        assertEquals(Set.of(), SceneNames.SPELL_VISUALS.stream().filter(s -> !spells.contains(s) && !SceneNames.MEMORY_ONLY_SPELLS.contains(s)).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void spellVisualsAreExactlyWhatSpellVisualsSupports() throws IOException {
        // SpellVisuals is client-only and cannot load in a pure test, so both read the pure sets in SceneNames:
        // the beams, and the hand-written adapters, which SpellVisuals must name in its source.
        Set<String> union = new HashSet<>(SceneNames.SPELL_BEAMS);
        union.addAll(SceneNames.SPELL_ADAPTERS);
        assertEquals(union, SceneNames.SPELL_VISUALS);
        assertEquals(Set.of(), SceneNames.SPELL_BEAMS.stream().filter(SceneNames.SPELL_ADAPTERS::contains).collect(java.util.stream.Collectors.toSet()));
        String src = read(JAVA.resolve("client/pensieve/SpellVisuals.java"));
        assertTrue(src.contains("SceneNames.SPELL_VISUALS"), "supported() must return SceneNames.SPELL_VISUALS");
        assertTrue(src.contains("SceneNames.SPELL_BEAMS"), "beams must be chosen by SceneNames.SPELL_BEAMS");
        for (String adapter : SceneNames.SPELL_ADAPTERS) {
            assertTrue(src.contains("\"" + adapter + "\""), "SpellVisuals has no adapter for " + adapter);
        }
        assertFalse(SceneNames.SPELL_VISUALS.contains("serpensortia"));
    }

    @Test
    void everyShippedSceneParsesAndValidates() throws Exception {
        if (!Files.isDirectory(MEMORIES)) return;
        SceneNames names = harvest();
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.list(MEMORIES)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = f.getFileName().toString();
                if (name.startsWith("_")) continue;
                String id = name.substring(0, name.length() - 5);
                SceneDef def = SceneParser.parse(id, JsonParser.parseString(read(f)).getAsJsonObject());
                for (String msg : SceneValidator.validate(def, names)) problems.add(id + ": " + msg);
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void everyListedSkinHasATexture() throws IOException {
        Path skinList = MEMORIES.resolve("_skins.json");
        if (!Files.exists(skinList)) return;
        Path dir = Path.of("src/main/resources/assets/hogwartsmagic/textures/memory_skin");
        List<String> missing = new ArrayList<>();
        JsonParser.parseString(read(skinList)).getAsJsonArray().forEach(e -> {
            if (!Files.exists(dir.resolve(e.getAsString() + ".png"))) missing.add(e.getAsString());
        });
        assertEquals(List.of(), missing);
    }

    /**
     * Walks every scene's {@code next} chain in {@code nextById} (id to next id, null for none) and returns one
     * problem per chain that names a missing scene or loops back on itself.
     */
    static List<String> chainProblems(java.util.Map<String, String> nextById) {
        List<String> problems = new ArrayList<>();
        for (String start : new java.util.TreeSet<>(nextById.keySet())) {
            List<String> seen = new ArrayList<>();
            String id = start;
            while (id != null) {
                if (seen.contains(id)) {
                    problems.add(start + ": next chain loops: " + String.join(" -> ", seen) + " -> " + id);
                    break;
                }
                if (!nextById.containsKey(id)) {
                    problems.add(start + ": next scene '" + id + "' does not exist");
                    break;
                }
                seen.add(id);
                id = nextById.get(id);
            }
        }
        return problems;
    }

    @Test
    void chainWalkCatchesCyclesAndMissingScenes() {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("a", "b");
        m.put("b", "a");
        m.put("c", "gone");
        m.put("d", null);
        m.put("e", "d");
        List<String> problems = chainProblems(m);
        assertEquals(3, problems.size(), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("a: next chain loops")));
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("b: next chain loops")));
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("c: next scene 'gone'")));
    }

    @Test
    void everyShippedChainEndsAndNamesRealScenes() throws Exception {
        if (!Files.isDirectory(MEMORIES)) return;
        java.util.Map<String, String> nextById = new java.util.HashMap<>();
        try (Stream<Path> files = Files.list(MEMORIES)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = f.getFileName().toString();
                if (name.startsWith("_")) continue;
                String id = name.substring(0, name.length() - 5);
                nextById.put(id, SceneParser.parse(id, JsonParser.parseString(read(f)).getAsJsonObject()).next());
            }
        }
        assertEquals(List.of(), chainProblems(nextById));
    }

    /**
     * The storm dev scene (written by build_scripts/pensieve_scenes/dev_storm.py into the ignored run/pensieve_dev)
     * validates like a shipped one: a rough_sea under rain with a rowboat and four riders. Skipped where it has not
     * been generated.
     */
    @Test
    void devStormSceneValidates() throws Exception {
        Path f = Path.of("run/pensieve_dev/dev_storm.json");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(f),
                "run/pensieve_dev/dev_storm.json not generated (python build_scripts/pensieve_scenes/dev_storm.py)");
        SceneDef def = SceneParser.parse("dev_storm", JsonParser.parseString(read(f)).getAsJsonObject());
        assertEquals(List.of(), SceneValidator.validate(def, harvest()));
        assertTrue(def.events().stream().anyMatch(e -> "rough_sea".equals(e.id())));
        assertTrue(def.events().stream().anyMatch(e -> "rain".equals(e.id())));
        assertEquals(4, def.actors().stream().filter(a -> a.ride() != null).count());
    }
}
