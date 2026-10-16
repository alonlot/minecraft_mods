package com.hogwartsmagic.pensieve;

import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.hogwartsmagic.pensieve.scene.SceneValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneValidatorTest {
    private static final SceneNames NAMES = new SceneNames(
            Set.of("hogwartsmagic:basilisk", "minecraft:zombie", "player"), Set.of("stupefy"),
            Set.of("minecraft:entity.generic.explode"), Set.of("minecraft:stone"), Set.of("minecraft:stick"),
            Set.of("hogwartsmagic:chamber_hall"), Set.of(), Set.of("minecraft:flame"), Set.of());

    private static List<String> validate(String json) throws Exception {
        SceneDef def = SceneParser.parse("t", JsonParser.parseString(json).getAsJsonObject());
        return SceneValidator.validate(def, NAMES);
    }

    @Test
    void radiusOver200IsRejected() throws Exception {
        List<String> m = validate("{\"length\":100,\"radius\":201}");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).startsWith("radius"), m.get(0));
        assertEquals(0, validate("{\"length\":100,\"radius\":200}").size());
    }

    @Test
    void fillBeyondRadiusIsRejected() throws Exception {
        List<String> m = validate("""
                {"length":100,"radius":10,
                 "set":{"fills":[
                   {"block":"minecraft:stone","from":[0,0,0],"to":[2,0,2]},
                   {"block":"minecraft:stone","from":[0,0,0],"to":[11,0,0]}]}}""");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).startsWith("set.fills[1]") && m.get(0).contains("extends beyond radius 10"), m.get(0));
    }

    @Test
    void oversizedFillIsRejected() throws Exception {
        List<String> m = validate("""
                {"length":100,"radius":200,
                 "set":{"fills":[{"block":"minecraft:stone","from":[-200,0,-200],"to":[200,20,200]}]}}""");
        assertEquals(1, m.size(), m.toString());
        assertEquals("set.fills[0]: fill too large (3376821 blocks, max 2000000)", m.get(0));
    }

    @Test
    void unknownEntityIsNamed() throws Exception {
        List<String> m = validate("{\"length\":100,\"actors\":[{\"id\":\"a\",\"type\":\"hogwartsmagic:basilsk\"}]}");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).startsWith("actors[0].type"), m.get(0));
        assertTrue(m.get(0).contains("hogwartsmagic:basilsk"), m.get(0));
    }

    @Test
    void unknownSpellBlockSoundTemplate() throws Exception {
        List<String> m = validate("""
                {"length":100,
                 "set":{"templates":[{"id":"hogwartsmagic:nope","at":[0,0,0]}]},
                 "actors":[{"id":"a","type":"minecraft:zombie",
                   "actions":[{"t":5,"do":"cast","spell":"stupefyy"}]}],
                 "events":[{"t":1,"kind":"block","id":"minecraft:stoen","at":[0,0,0]},
                           {"t":2,"kind":"sound","id":"minecraft:nothing"}]}""");
        assertEquals(4, m.size(), m.toString());
        assertTrue(m.stream().anyMatch(s -> s.startsWith("set.templates[0].id")), m.toString());
        assertTrue(m.stream().anyMatch(s -> s.startsWith("actors[0].actions[0].spell")), m.toString());
        assertTrue(m.stream().anyMatch(s -> s.startsWith("events[0].id")), m.toString());
        assertTrue(m.stream().anyMatch(s -> s.startsWith("events[1].id")), m.toString());
    }

    @Test
    void unknownActionAndEventKind() throws Exception {
        List<String> m = validate("""
                {"length":100,
                 "actors":[{"id":"a","type":"minecraft:zombie","actions":[{"t":5,"do":"dance"}]}],
                 "events":[{"t":1,"kind":"explosion"}]}""");
        assertEquals(2, m.size(), m.toString());
        assertTrue(m.stream().anyMatch(s -> s.startsWith("actors[0].actions[0].do") && s.contains("dance")), m.toString());
        assertTrue(m.stream().anyMatch(s -> s.startsWith("events[0].kind") && s.contains("explosion")), m.toString());
    }

    @Test
    void sceneLevelProblems() throws Exception {
        List<String> m = validate("""
                {"length":0,"end_action":"explode",
                 "actors":[{"id":"a","type":"player"},{"id":"a","type":"player",
                   "actions":[{"t":0,"do":"cast"}]}],
                 "words":[{"t":0,"by":"ghost","text":"hi"}],
                 "events":[{"t":0,"kind":"effect","id":"rain_of_frogs"}]}""");
        assertEquals(6, m.size(), m.toString());
        assertTrue(m.stream().anyMatch(s -> s.startsWith("length")), m.toString());
        assertTrue(m.stream().anyMatch(s -> s.startsWith("end_action")), m.toString());
        assertTrue(m.stream().anyMatch(s -> s.startsWith("actors[1].id")), m.toString());
        assertTrue(m.stream().anyMatch(s -> s.startsWith("actors[1].actions[0].spell")), m.toString());
        assertTrue(m.stream().anyMatch(s -> s.startsWith("words[0].by")), m.toString());
        assertTrue(m.stream().anyMatch(s -> s.startsWith("events[0].id") && s.contains("rain_of_frogs")), m.toString());
    }

    @Test
    void danglingActorTarget() throws Exception {
        List<String> m = validate("""
                {"length":100,"actors":[{"id":"a","type":"minecraft:zombie",
                  "actions":[{"t":5,"do":"cast","spell":"stupefy","target":"actor:ghost"}]}]}""");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).startsWith("actors[0].actions[0].target"), m.get(0));
    }

    @Test
    void timeOutsideLength() throws Exception {
        List<String> m = validate("{\"length\":100,\"events\":[{\"t\":120,\"kind\":\"sound\",\"id\":\"minecraft:entity.generic.explode\"}]}");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).startsWith("events[0].t"), m.get(0));
    }

    @Test
    void validSceneHasNoMessages() throws Exception {
        List<String> m = validate("""
                {"length":100,"poses":{"salute":{"right_arm":[-90,0,0]}},
                 "set":{"templates":[{"id":"hogwartsmagic:chamber_hall","at":[0,0,0]}],
                        "fills":[{"block":"minecraft:stone","from":[0,0,0],"to":[1,1,1]}]},
                 "actors":[{"id":"a","type":"hogwartsmagic:basilisk","poses":[{"t":3,"name":"salute"}],
                            "items":{"mainhand":"minecraft:stick"},
                            "actions":[{"t":5,"do":"cast","spell":"stupefy","target":"actor:b"}]},
                           {"id":"b","type":"player"}],
                 "words":[{"t":10,"by":"a","text":"hi"}],
                 "events":[{"t":1,"kind":"particles","id":"minecraft:flame","at":[0,0,0]},
                           {"t":2,"kind":"effect","id":"lightning","at":[0,0,0]}]}""");
        assertEquals(List.of(), m);
    }
}
