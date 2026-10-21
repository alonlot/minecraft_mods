package com.hogwartsmagic.pensieve;

import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.CueKinds;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneException;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.hogwartsmagic.pensieve.scene.SceneValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@code loop} flag of a scene {@code sfx} cue: parsing, validation and the fade curve. */
class SfxLoopTest {
    private static final String SFX = "hogwartsmagic:pensieve.sfx.x";

    private static SceneDef parse(String events) throws Exception {
        return SceneParser.parse("t", JsonParser.parseString("{\"length\":200,\"events\":[" + events + "]}").getAsJsonObject());
    }

    private static List<String> validate(String events) throws Exception {
        SceneNames names = new SceneNames(Set.of(), Set.of(), Set.of(SFX), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        return SceneValidator.validate(parse(events), names);
    }

    @Test
    void absentLoopLeavesCuesAsTheyWere() throws Exception {
        SceneDef.EventDef e = parse("{\"t\":1,\"kind\":\"sfx\",\"id\":\"" + SFX + "\"}").events().get(0);
        assertFalse(e.loop());
        assertEquals(0, e.duration());
        assertThrows(SceneException.class, () -> parse("{\"t\":1,\"kind\":\"voice\",\"id\":\"a:b\",\"loop\":true}"));
        assertThrows(SceneException.class, () -> parse("{\"t\":1,\"kind\":\"sfx\",\"id\":\"a:b\",\"loop\":\"yes\"}"));
    }

    @Test
    void parsesLoopAndDuration() throws Exception {
        SceneDef.EventDef e = parse("{\"t\":1,\"kind\":\"sfx\",\"id\":\"" + SFX + "\",\"loop\":true,\"duration\":\"5s\"}").events().get(0);
        assertTrue(e.loop());
        assertEquals(100, e.duration());
    }

    @Test
    void validatesLoopDuration() throws Exception {
        assertEquals(List.of(), validate("{\"t\":1,\"kind\":\"sfx\",\"id\":\"" + SFX + "\",\"loop\":true}"));
        assertEquals(List.of(), validate("{\"t\":1,\"kind\":\"sfx\",\"id\":\"" + SFX + "\",\"loop\":true,\"duration\":100}"));
        List<String> noLoop = validate("{\"t\":1,\"kind\":\"sfx\",\"id\":\"" + SFX + "\",\"duration\":100}");
        assertEquals(1, noLoop.size(), noLoop.toString());
        assertTrue(noLoop.get(0).startsWith("events[0].duration"), noLoop.get(0));
        List<String> past = validate("{\"t\":150,\"kind\":\"sfx\",\"id\":\"" + SFX + "\",\"loop\":true,\"duration\":100}");
        assertEquals(1, past.size(), past.toString());
    }

    @Test
    void fadeCurve() {
        assertEquals(1f, CueKinds.loopFade(0, 20));
        assertEquals(0.5f, CueKinds.loopFade(10, 20), 1e-6f);
        assertEquals(0f, CueKinds.loopFade(20, 20));
        assertEquals(0f, CueKinds.loopFade(99, 20));
    }
}
