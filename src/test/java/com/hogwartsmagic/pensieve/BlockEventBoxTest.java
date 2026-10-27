package com.hogwartsmagic.pensieve;

import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.SceneDef;
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

/** A block event's optional {@code to}: parsed, and validated (whole blocks, inside the radius, at most 4096 cells). */
class BlockEventBoxTest {
    private static final SceneNames NAMES = new SceneNames(Set.of(), Set.of(), Set.of("minecraft:entity.generic.explode"),
            Set.of("minecraft:air"), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

    private static List<String> validate(String event) throws Exception {
        SceneDef def = SceneParser.parse("t", JsonParser.parseString("{\"length\":100,\"radius\":40,\"events\":[" + event + "]}")
                .getAsJsonObject());
        return SceneValidator.validate(def, NAMES);
    }

    private static String block(String keys) {
        return "{\"t\":10,\"kind\":\"block\",\"id\":\"minecraft:air\"," + keys + "}";
    }

    @Test
    void toIsParsedAndAbsentByDefault() throws Exception {
        SceneDef def = SceneParser.parse("t", JsonParser.parseString("{\"length\":100,\"events\":["
                + block("\"at\":[1,2,3]") + "," + block("\"at\":[1,2,3],\"to\":[4,5,6]") + "]}").getAsJsonObject());
        assertNull(def.events().get(0).to());
        assertArrayEquals(new float[]{4, 5, 6}, def.events().get(1).to());
    }

    @Test
    void validBoxesPass() throws Exception {
        assertEquals(List.of(), validate(block("\"at\":[-5,3,-1],\"to\":[10,3,1]")));
        assertEquals(List.of(), validate(block("\"at\":[10,3,1],\"to\":[-5,3,-1]")), "any corner order");
        assertEquals(List.of(), validate(block("\"at\":[0,0,0],\"to\":[15,15,15]")), "exactly 4096 cells");
    }

    @Test
    void badBoxesAreNamed() throws Exception {
        assertTrue(validate(block("\"at\":[0,0,0],\"to\":[16,15,15]")).get(0).contains("4352 cells; at most 4096"));
        assertTrue(validate(block("\"at\":[0,0,0],\"to\":[1.5,0,0]")).get(0).contains("whole-block"));
        assertTrue(validate(block("\"at\":[0,0,0],\"to\":[41,0,0]")).get(0).contains("outside the scene radius 40 in X"));
        assertTrue(validate(block("\"at\":[0,0,0],\"to\":[0,0,-41]")).get(0).contains("in Z"));
        List<String> m = validate("{\"t\":10,\"kind\":\"sound\",\"id\":\"minecraft:entity.generic.explode\",\"at\":[0,0,0],\"to\":[1,1,1]}");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains(".to: to is only for block events"), m.get(0));
    }
}
