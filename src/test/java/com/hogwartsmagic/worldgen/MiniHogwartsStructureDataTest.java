package com.hogwartsmagic.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The castle's structure definition. Monsters must never spawn naturally inside a castle: its roof spaces, stair
 * undersides and deep corners are too many and too dark to light block by block, so the structure overrides the
 * monster spawn list with an empty one over its piece box, the way vanilla's outposts and huts override theirs.
 */
class MiniHogwartsStructureDataTest {

    @Test
    void noMonstersSpawnInsideTheCastle() throws Exception {
        try (Reader in = new InputStreamReader(
                MiniHogwartsStructureDataTest.class.getResourceAsStream("/data/hogwartsmagic/worldgen/structure/mini_hogwarts.json"),
                StandardCharsets.UTF_8)) {
            JsonObject structure = JsonParser.parseReader(in).getAsJsonObject();
            JsonObject monster = structure.getAsJsonObject("spawn_overrides").getAsJsonObject("monster");
            assertNotNull(monster, "the castle overrides the monster spawn list");
            assertEquals("piece", monster.get("bounding_box").getAsString());
            assertTrue(monster.getAsJsonArray("spawns").isEmpty(), "with nothing to spawn");
        }
    }
}
