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
     * Every shipped letter_flood with a {@code box} flies in clear air: each cell of the box is air (or absent) in
     * every template the scene places over it, and no fill or disk of the set reaches into it. The Letter memory's
     * flood uses Privet Drive's living_room_box (build_scripts/pensieve_scenes/privet_drive.py); a letter clamped to
     * a box with furniture in it would sink into the sofa. Also checks that some scene does ship such a flood, so the
     * check cannot pass by finding nothing.
     */
    @Test
    void letterFloodBoxesAreAllAir() throws Exception {
        if (!Files.isDirectory(MEMORIES)) return;
        List<String> problems = new ArrayList<>();
        int floods = 0;
        try (Stream<Path> files = Files.list(MEMORIES)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = f.getFileName().toString();
                if (name.startsWith("_")) continue;
                String id = name.substring(0, name.length() - 5);
                SceneDef def = SceneParser.parse(id, JsonParser.parseString(read(f)).getAsJsonObject());
                for (SceneDef.EventDef e : def.events()) {
                    if (!"letter_flood".equals(e.id()) || e.box() == null) continue;
                    floods++;
                    int[] lo = new int[3], hi = new int[3];
                    for (int i = 0; i < 3; i++) {
                        lo[i] = (int) Math.floor(e.box()[0][i]);
                        hi[i] = (int) Math.ceil(e.box()[1][i]) - 1;
                    }
                    for (SceneDef.TemplateRef tr : def.set().templates()) {
                        String tn = tr.id().substring(tr.id().indexOf(':') + 1);
                        java.util.Map<Long, String> cells = templateBlocks(STRUCTURES.resolve(tn + ".nbt"));
                        for (int x = lo[0]; x <= hi[0]; x++) {
                            for (int y = lo[1]; y <= hi[1]; y++) {
                                for (int z = lo[2]; z <= hi[2]; z++) {
                                    String b = cells.get(key(x - tr.at()[0], y - tr.at()[1], z - tr.at()[2]));
                                    if (b != null && !b.equals("minecraft:air") && !b.equals("minecraft:structure_void")) {
                                        problems.add(id + ": flood box cell " + x + "," + y + "," + z + " is " + b
                                                + " in " + tr.id());
                                    }
                                }
                            }
                        }
                    }
                    for (SceneDef.Fill fl : def.set().fills()) {
                        boolean overlaps = true;
                        for (int i = 0; i < 3; i++) {
                            int a = Math.min(fl.from()[i], fl.to()[i]), b = Math.max(fl.from()[i], fl.to()[i]);
                            overlaps &= a <= hi[i] && b >= lo[i];
                        }
                        if (overlaps) problems.add(id + ": a " + fl.block() + " fill reaches into the flood box");
                    }
                }
            }
        }
        assertEquals(List.of(), problems);
        assertTrue(floods > 0, "no shipped scene has a letter_flood with a box");
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0xFFFFF) << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }

    /** A structure template's blocks as packed local position -> block id (properties dropped). */
    private static java.util.Map<Long, String> templateBlocks(Path nbt) throws IOException {
        net.minecraft.nbt.CompoundTag root = net.minecraft.nbt.NbtIo.readCompressed(nbt,
                net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        net.minecraft.nbt.ListTag pal = root.contains("palette")
                ? root.getList("palette", 10) : root.getList("palettes", 9).getList(0);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < pal.size(); i++) names.add(pal.getCompound(i).getString("Name"));
        java.util.Map<Long, String> out = new java.util.HashMap<>();
        net.minecraft.nbt.ListTag blocks = root.getList("blocks", 10);
        for (int i = 0; i < blocks.size(); i++) {
            net.minecraft.nbt.CompoundTag b = blocks.getCompound(i);
            net.minecraft.nbt.ListTag pos = b.getList("pos", 3);
            out.put(key(pos.getInt(0), pos.getInt(1), pos.getInt(2)), names.get(b.getInt("state")));
        }
        return out;
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

    /**
     * The storm sky dev scene (build_scripts/pensieve_scenes/dev_storm_sky.py into the ignored run/pensieve_dev)
     * validates like a shipped one. Skipped where it has not been generated.
     */
    @Test
    void devStormSkySceneValidates() throws Exception {
        Path f = Path.of("run/pensieve_dev/dev_storm_sky.json");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(f),
                "run/pensieve_dev/dev_storm_sky.json not generated (python build_scripts/pensieve_scenes/dev_storm_sky.py)");
        SceneDef def = SceneParser.parse("dev_storm_sky", JsonParser.parseString(read(f)).getAsJsonObject());
        assertEquals(List.of(), SceneValidator.validate(def, harvest()));
        assertTrue(def.events().stream().anyMatch(e -> "sky".equals(e.id()) && "storm".equals(e.style())));
        assertTrue(def.events().stream().anyMatch(e -> "rain".equals(e.id()) && e.spread()[0] == 200f));
        assertTrue(def.events().stream().filter(e -> "lightning".equals(e.id())).count() >= 4);
    }

    /**
     * The crowd dev scene (build_scripts/pensieve_scenes/dev_crowd.py into the ignored run/pensieve_dev) validates like a
     * shipped one: 3,000 spectators over four crowd events, and a goal, a gasp and an erupt. Skipped where it has not
     * been generated.
     */
    @Test
    void devCrowdSceneValidates() throws Exception {
        Path f = Path.of("run/pensieve_dev/dev_crowd.json");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(f),
                "run/pensieve_dev/dev_crowd.json not generated (python build_scripts/pensieve_scenes/dev_crowd.py)");
        SceneDef def = SceneParser.parse("dev_crowd", JsonParser.parseString(read(f)).getAsJsonObject());
        assertEquals(List.of(), SceneValidator.validate(def, harvest()));
        int seats = 0;
        for (SceneDef.EventDef e : def.events()) {
            if ("crowd".equals(e.id())) seats += com.hogwartsmagic.pensieve.scene.Crowd.seatCount(e);
        }
        assertEquals(3000, seats);
        for (String style : new String[] {"goal", "gasp", "erupt"}) {
            assertTrue(def.events().stream().anyMatch(e -> "crowd_react".equals(e.id()) && style.equals(e.style())), style);
        }
    }

    /**
     * The broom dev scene (written by build_scripts/pensieve_scenes/dev_brooms.py into the ignored run/pensieve_dev)
     * validates like a shipped one: brooms, riders with ride windows, spline actor tracks. Skipped where it has not
     * been generated.
     */
    @Test
    void devBroomsSceneValidates() throws Exception {
        Path f = Path.of("run/pensieve_dev/dev_brooms.json");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(f),
                "run/pensieve_dev/dev_brooms.json not generated (python build_scripts/pensieve_scenes/dev_brooms.py)");
        SceneDef def = SceneParser.parse("dev_brooms", JsonParser.parseString(read(f)).getAsJsonObject());
        assertEquals(List.of(), SceneValidator.validate(def, harvest()));
        assertTrue(def.actors().stream().anyMatch(a -> "hogwartsmagic:memory_broom".equals(a.type())));
        assertTrue(def.actors().stream().anyMatch(a -> "hogwartsmagic:memory_broom_racing".equals(a.type())));
        assertTrue(def.actors().stream().anyMatch(a -> a.ride() != null && !Float.isInfinite(a.rideFrom())),
                "a rider mounts inside the scene");
    }
    /** The ball types, the Snitch and the display-only props are all names a scene may use. */
    @Test
    void harvestKnowsTheBallsAndTheProps() throws Exception {
        SceneNames names = harvest();
        for (String type : SceneNames.BALLS) assertTrue(names.entityTypes().contains(type), type);
        assertTrue(names.entityTypes().contains("hogwartsmagic:golden_snitch"));
        assertTrue(names.items().contains("hogwartsmagic:memory_remembrall"));
        assertTrue(names.items().contains("hogwartsmagic:whistle"));
    }

    /** The Firebolt, the golden egg, the champion's robes and the egg prop are all names a scene may use. */
    @Test
    void harvestKnowsTheFirstTaskProps() throws Exception {
        SceneNames names = harvest();
        assertTrue(names.entityTypes().contains("hogwartsmagic:memory_broom_firebolt"));
        assertTrue(names.entityTypes().contains("hogwartsmagic:memory_golden_egg"));
        assertTrue(names.items().contains("hogwartsmagic:golden_egg_prop"));
        assertTrue(names.items().contains("hogwartsmagic:triwizard_robes"));
    }

    /** The four house Quidditch tunics and the shared shin guards are names a scene may use. */
    @Test
    void harvestKnowsTheQuidditchKits() throws Exception {
        SceneNames names = harvest();
        for (String house : new String[] {"gryffindor", "hufflepuff", "ravenclaw", "slytherin"})
            assertTrue(names.items().contains("hogwartsmagic:" + house + "_quidditch_robes"), house);
        assertTrue(names.items().contains("hogwartsmagic:quidditch_shin_guards"));
    }

    /**
     * The First Task props dev scene (build_scripts/pensieve_scenes/dev_first_task_props.py into the ignored
     * run/pensieve_dev) validates like a shipped one: a Firebolt with the champion in her robes, the golden egg, the
     * held egg prop and a triwizard crowd. Skipped where it has not been generated.
     */
    @Test
    void devFirstTaskPropsSceneValidates() throws Exception {
        Path f = Path.of("run/pensieve_dev/dev_first_task_props.json");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(f),
                "run/pensieve_dev/dev_first_task_props.json not generated (python build_scripts/pensieve_scenes/dev_first_task_props.py)");
        SceneDef def = SceneParser.parse("dev_first_task_props", JsonParser.parseString(read(f)).getAsJsonObject());
        assertEquals(List.of(), SceneValidator.validate(def, harvest()));
        assertTrue(def.actors().stream().anyMatch(a -> "hogwartsmagic:memory_broom_firebolt".equals(a.type())));
        assertTrue(def.actors().stream().anyMatch(a -> "hogwartsmagic:memory_golden_egg".equals(a.type())));
        assertTrue(def.events().stream().anyMatch(e -> "crowd".equals(e.id()) && "triwizard".equals(e.style())));
    }

    /**
     * The balls dev scene (build_scripts/pensieve_scenes/dev_balls.py into the ignored run/pensieve_dev) validates like
     * a shipped one. Skipped where it has not been generated.
     */
    @Test
    void devBallsSceneValidates() throws Exception {
        Path f = Path.of("run/pensieve_dev/dev_balls.json");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(f),
                "run/pensieve_dev/dev_balls.json not generated (python build_scripts/pensieve_scenes/dev_balls.py)");
        SceneDef def = SceneParser.parse("dev_balls", JsonParser.parseString(read(f)).getAsJsonObject());
        assertEquals(List.of(), SceneValidator.validate(def, harvest()));
        for (String type : SceneNames.BALLS) {
            assertTrue(def.actors().stream().anyMatch(a -> type.equals(a.type())), type);
        }
        assertTrue(def.actors().stream().anyMatch(a -> "hogwartsmagic:golden_snitch".equals(a.type())));
    }

    private static final List<String> HOUSES = List.of("gryffindor", "hufflepuff", "ravenclaw", "slytherin");

    private static com.google.gson.JsonObject scene(String id) throws IOException {
        Path f = MEMORIES.resolve(id + ".json");
        assertTrue(Files.exists(f), id + ".json is missing");
        return JsonParser.parseString(read(f)).getAsJsonObject();
    }

    /**
     * The Flying memory (build_scripts/pensieve_scenes/flying_scene.py) ships a whole chain for every house:
     * flying_h, then _chase, _match and _snitch, wired by next in that order, opening on eyes_open at t 0 and
     * ending in a wake (FlyingMemory.sceneFor and SNITCH_SUFFIX name the first and the last).
     */
    @Test
    void everyHouseHasAFlyingChain() throws Exception {
        for (String h : HOUSES) {
            String[] chain = {"flying_" + h, "flying_" + h + "_chase", "flying_" + h + "_match", "flying_" + h + "_snitch"};
            for (int i = 0; i < chain.length; i++) {
                com.google.gson.JsonObject s = scene(chain[i]);
                SceneDef def = SceneParser.parse(chain[i], s);
                if (i < chain.length - 1) {
                    assertEquals(chain[i + 1], def.next(), chain[i] + " hands over to the next scene of the chain");
                    assertFalse(s.has("end_action"), chain[i] + " does not end the memory");
                } else {
                    assertEquals(null, def.next(), chain[i] + " is the end of the chain");
                    assertEquals("wake", s.get("end_action").getAsString(), chain[i] + " ends in a wake");
                }
            }
            boolean opens = false;
            for (var e : scene(chain[0]).getAsJsonArray("events")) {
                var o = e.getAsJsonObject();
                if (o.get("id").getAsString().equals("eyes_open") && o.get("t").getAsDouble() == 0) opens = true;
            }
            assertTrue(opens, chain[0] + " opens on eyes_open at t 0");
            assertEquals(com.hogwartsmagic.pensieve.FlyingMemory.sceneFor(
                    com.hogwartsmagic.sorting.HogwartsHouse.valueOf(h.toUpperCase(java.util.Locale.ROOT))), chain[0]);
        }
    }

    /**
     * The First Task memory (build_scripts/pensieve_scenes/first_task_scene.py): the champions' tent, the arena, the
     * chase and the golden egg, wired by next in that order; the tent hands over through white, the first opens on
     * eyes_open at t 0 and the egg scene ends in a wake.
     */
    @Test
    void firstTaskChainIsWired() throws Exception {
        String[] chain = {"first_task", "first_task_arena", "first_task_chase", "first_task_egg"};
        for (int i = 0; i < chain.length; i++) {
            com.google.gson.JsonObject s = scene(chain[i]);
            SceneDef def = SceneParser.parse(chain[i], s);
            if (i < chain.length - 1) {
                assertEquals(chain[i + 1], def.next(), chain[i] + " hands over to the next scene of the chain");
                assertFalse(s.has("end_action"), chain[i] + " does not end the memory");
            } else {
                assertEquals(null, def.next(), chain[i] + " is the end of the chain");
                assertEquals("wake", s.get("end_action").getAsString(), chain[i] + " ends in a wake");
            }
            assertEquals(i == 0, s.has("handover_colour") && s.get("handover_colour").getAsString().equals("white"),
                    chain[i] + (i == 0 ? " hands over through white" : " hands over without a colour"));
        }
        boolean opens = false;
        for (var e : scene(chain[0]).getAsJsonArray("events")) {
            var o = e.getAsJsonObject();
            if (o.get("id").getAsString().equals("eyes_open") && o.get("t").getAsDouble() == 0) opens = true;
        }
        assertTrue(opens, chain[0] + " opens on eyes_open at t 0");
    }

    /**
     * The opponent: a Slytherin's chain plays Gryffindor, every other house's plays Slytherin. In the match and the
     * Snitch scenes every rival player (ids r_...) wears the rival house's Quidditch kit and a student skin of that house,
     * and every one of the boy's team (o_...) his own.
     */
    @Test
    void slytherinFacesGryffindor() throws Exception {
        for (String h : HOUSES) {
            String rival = h.equals("slytherin") ? "gryffindor" : "slytherin";
            for (String part : List.of("_match", "_snitch")) {
                int rivals = 0;
                for (var e : scene("flying_" + h + part).getAsJsonArray("actors")) {
                    var a = e.getAsJsonObject();
                    String id = a.get("id").getAsString();
                    if (!a.get("type").getAsString().equals("player")) continue;
                    String team = id.startsWith("r_") ? rival : id.startsWith("o_") || id.equals("viewer") ? h : null;
                    if (team == null) continue;
                    assertEquals("hogwartsmagic:" + team + "_quidditch_robes",
                            a.getAsJsonObject("items").get("chest").getAsString(), h + part + " " + id);
                    assertEquals("hogwartsmagic:quidditch_shin_guards",
                            a.getAsJsonObject("items").get("legs").getAsString(), h + part + " " + id);
                    if (!id.equals("viewer")) {
                        assertTrue(a.getAsJsonObject("skin").get("texture").getAsString()
                                .startsWith("student_" + team + "_"), h + part + " " + id);
                    }
                    if (team.equals(rival)) rivals++;
                }
                assertEquals(7, rivals, h + part + ": seven rival players");
            }
        }
    }
}
