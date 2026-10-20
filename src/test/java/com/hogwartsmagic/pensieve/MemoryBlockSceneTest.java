package com.hogwartsmagic.pensieve;

import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneException;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.hogwartsmagic.pensieve.scene.SceneValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code memory_block} actor's {@code block} key, the {@code look} roll, and the {@code {viewer_wand}} item. */
class MemoryBlockSceneTest {
    private static final SceneNames NAMES = new SceneNames(
            Set.of("hogwartsmagic:memory_block", "hogwartsmagic:memory_tape", "minecraft:zombie", "player"),
            Set.of(), Set.of(), Set.of("minecraft:bricks", "minecraft:oak_stairs"), Set.of("minecraft:stick"),
            Set.of(), Set.of(), Set.of(), Set.of());

    private static SceneDef parse(String json) throws SceneException {
        return SceneParser.parse("t", JsonParser.parseString(json).getAsJsonObject());
    }

    private static List<String> validate(String json) throws Exception {
        return SceneValidator.validate(parse(json), NAMES);
    }

    private static String actor(String body) {
        return "{\"length\":100,\"actors\":[{\"id\":\"a\"," + body + "}]}";
    }

    @Test
    void blockKeyIsParsed() throws Exception {
        SceneDef s = parse(actor("\"type\":\"hogwartsmagic:memory_block\",\"block\":\"minecraft:oak_stairs[facing=east,half=top]\""));
        assertEquals("minecraft:oak_stairs[facing=east,half=top]", s.actors().get(0).block());
        assertNull(parse(actor("\"type\":\"player\"")).actors().get(0).block());
    }

    @Test
    void rollIsAThirdLookComponentOnlyWhenGiven() throws Exception {
        SceneDef s = parse(actor("\"type\":\"hogwartsmagic:memory_block\",\"block\":\"minecraft:bricks\","
                + "\"look\":[{\"t\":0,\"yaw\":0,\"pitch\":10,\"roll\":30},{\"t\":10,\"yaw\":90,\"ease\":\"linear\"}]"));
        assertArrayEquals(new float[]{45, 5, 15}, s.actors().get(0).look().sample(5), 1e-4f);
        // No roll anywhere: the look stays [yaw, pitch].
        SceneDef plain = parse(actor("\"type\":\"player\",\"look\":[{\"t\":0,\"yaw\":90,\"pitch\":10}]"));
        assertArrayEquals(new float[]{90, 10}, plain.actors().get(0).look().sample(0), 1e-5f);
    }

    @Test
    void goodBlockActorsValidate() throws Exception {
        assertEquals(List.of(), validate(actor("\"type\":\"hogwartsmagic:memory_block\",\"block\":\"minecraft:bricks\","
                + "\"scale\":0.5,\"look\":[{\"t\":0,\"yaw\":0,\"pitch\":0,\"roll\":45}]")));
        assertEquals(List.of(), validate(actor("\"type\":\"hogwartsmagic:memory_block\","
                + "\"block\":\"minecraft:oak_stairs[facing=east,half=top]\"")));
    }

    @Test
    void unknownBlockIsRejected() throws Exception {
        List<String> m = validate(actor("\"type\":\"hogwartsmagic:memory_block\",\"block\":\"minecraft:brick\""));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).startsWith("actors[0].block") && m.get(0).contains("unknown block 'minecraft:brick'"), m.get(0));
        // Properties do not hide the id.
        m = validate(actor("\"type\":\"hogwartsmagic:memory_block\",\"block\":\"minecraft:oak_stair[facing=east]\""));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("unknown block 'minecraft:oak_stair'"), m.get(0));
    }

    @Test
    void malformedBlockStateIsRejected() throws Exception {
        for (String bad : new String[]{"minecraft:oak_stairs[facing=east", "minecraft:oak_stairs[]",
                "minecraft:oak_stairs[facing]", "bricks", "minecraft:oak_stairs[facing=east]x"}) {
            List<String> m = validate(actor("\"type\":\"hogwartsmagic:memory_block\",\"block\":\"" + bad + "\""));
            assertEquals(1, m.size(), bad + " -> " + m);
            assertTrue(m.get(0).startsWith("actors[0].block"), m.get(0));
        }
    }

    @Test
    void blockIsOnlyForTheBlockActorAndTheBlockActorNeedsOne() throws Exception {
        List<String> m = validate(actor("\"type\":\"minecraft:zombie\",\"block\":\"minecraft:bricks\""));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("block is only for hogwartsmagic:memory_block"), m.get(0));
        m = validate(actor("\"type\":\"hogwartsmagic:memory_block\""));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("needs a block"), m.get(0));
    }

    @Test
    void rollIsOnlyForTheBlockActor() throws Exception {
        List<String> m = validate(actor("\"type\":\"player\",\"look\":[{\"t\":0,\"yaw\":0,\"roll\":20}]"));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("roll is only for hogwartsmagic:memory_block"), m.get(0));
    }

    @Test
    void viewerWandIsAnItemEverywhereAnItemIs() throws Exception {
        assertEquals(List.of(), validate(actor("\"type\":\"player\",\"items\":{\"mainhand\":\"{viewer_wand}\"},"
                + "\"actions\":[{\"t\":1,\"do\":\"hold\",\"item\":\"{viewer_wand}\"},"
                + "{\"t\":2,\"do\":\"equip\",\"slot\":\"offhand\",\"item\":\"{viewer_wand}\"},"
                + "{\"t\":3,\"do\":\"use_item\",\"item\":\"{viewer_wand}\"}]")));
        // Any other brace word is still an unknown item.
        List<String> m = validate(actor("\"type\":\"player\",\"items\":{\"mainhand\":\"{viewer_hat}\"}"));
        assertEquals(1, m.size(), m.toString());
    }
}
