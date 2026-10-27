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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneValidatorTest {
    private static final SceneNames NAMES = new SceneNames(
            Set.of("hogwartsmagic:basilisk", "minecraft:zombie", "player", "hogwartsmagic:memory_rowboat",
                    "hogwartsmagic:memory_broom", "hogwartsmagic:memory_broom_racing", "hogwartsmagic:memory_block",
                    "hogwartsmagic:memory_quaffle", "hogwartsmagic:memory_bludger", "hogwartsmagic:memory_remembrall",
                    "hogwartsmagic:golden_snitch", "hogwartsmagic:memory_horntail", "hogwartsmagic:memory_broom_firebolt",
                    "hogwartsmagic:memory_golden_egg"), Set.of("stupefy"),
            Set.of("minecraft:entity.generic.explode", "hogwartsmagic:pensieve.music.m", "hogwartsmagic:pensieve.voice.v", "hogwartsmagic:pensieve.sfx.x"), Set.of("minecraft:stone"), Set.of("minecraft:stick", "minecraft:air"),
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
    void schematicsAreCheckedByIdRotationAndAnchor() throws Exception {
        assertEquals(0, validate("""
                {"length":100,"radius":200,"fog_far":180,
                 "set":{"schematics":[{"id":"mini_hogwarts","at":[-52,3,-155],"rotation":270}]}}""").size());
        List<String> m = validate("""
                {"length":100,"radius":100,
                 "set":{"schematics":[
                   {"id":"mini_hogwart","at":[0,0,0]},
                   {"id":"mini_hogwarts","at":[0,0,0],"rotation":45},
                   {"id":"mini_hogwarts","at":[0,0,-101]}]}}""");
        assertEquals(3, m.size(), m.toString());
        assertTrue(m.get(0).startsWith("set.schematics[0].id") && m.get(0).contains("mini_hogwart'"), m.get(0));
        assertTrue(m.get(1).startsWith("set.schematics[1].rotation"), m.get(1));
        assertTrue(m.get(2).startsWith("set.schematics[2]") && m.get(2).contains("beyond radius 100"), m.get(2));
    }

    @Test
    void fogFarOutOfRangeIsRejected() throws Exception {
        List<String> m = validate("{\"length\":100,\"fog_far\":600}");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).startsWith("fog_far"), m.get(0));
        assertEquals(1, validate("{\"length\":100,\"fog_far\":-5}").size());
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
    void musicAndVoiceCues() throws Exception {
        assertEquals(List.of(), validate("""
                {"length":100,"events":[
                  {"t":0,"kind":"music","id":"hogwartsmagic:pensieve.music.m"},
                  {"t":"2s","kind":"music","id":"hogwartsmagic:pensieve.music.m","volume":0.5},
                  {"t":3,"kind":"voice","id":"hogwartsmagic:pensieve.voice.v","volume":1.0}]}"""));
        SceneDef def = SceneParser.parse("t", JsonParser.parseString(
                "{\"length\":100,\"events\":[{\"t\":0,\"kind\":\"voice\",\"id\":\"hogwartsmagic:pensieve.voice.v\"}]}").getAsJsonObject());
        assertEquals(1f, def.events().get(0).volume());
    }

    @Test
    void musicAndVoiceCueProblems() throws Exception {
        List<String> m = validate("""
                {"length":100,"events":[
                  {"t":0,"kind":"music"},
                  {"t":1,"kind":"voice","id":"hogwartsmagic:pensieve.voice.nope"},
                  {"t":2,"kind":"music","id":"hogwartsmagic:pensieve.music.m","volume":-1},
                  {"t":3,"kind":"voice","id":"hogwartsmagic:pensieve.voice.v","volume":9},
                  {"t":200,"kind":"music","id":"hogwartsmagic:pensieve.music.m"}]}""");
        assertEquals(5, m.size(), m.toString());
        assertTrue(m.stream().anyMatch(s -> s.startsWith("events[0].id") && s.contains("missing")), m.toString());
        assertTrue(m.stream().anyMatch(s -> s.startsWith("events[1].id")), m.toString());
        assertTrue(m.stream().anyMatch(s -> s.startsWith("events[2].volume")), m.toString());
        assertTrue(m.stream().anyMatch(s -> s.startsWith("events[3].volume")), m.toString());
        assertTrue(m.stream().anyMatch(s -> s.startsWith("events[4].t")), m.toString());
    }

    @Test
    void equipNeedsSlotAndItem() throws Exception {
        assertEquals(List.of(), validate("""
                {"length":100,"actors":[{"id":"a","type":"minecraft:zombie","actions":[
                  {"t":1,"do":"equip","slot":"head","item":"minecraft:stick"},
                  {"t":2,"do":"equip","slot":"head","item":"minecraft:air"}]}]}"""));
        List<String> m = validate("""
                {"length":100,"actors":[{"id":"a","type":"minecraft:zombie","actions":[
                  {"t":1,"do":"equip","item":"minecraft:stick"},
                  {"t":2,"do":"equip","slot":"head"}]}]}""");
        assertEquals(2, m.size(), m.toString());
        assertTrue(m.get(0).startsWith("actors[0].actions[0].slot"), m.get(0));
        assertTrue(m.get(1).startsWith("actors[0].actions[1].item"), m.get(1));
    }

    @Test
    void slotOnlyForEquip() throws Exception {
        List<String> m = validate("""
                {"length":100,"actors":[{"id":"a","type":"minecraft:zombie","actions":[
                  {"t":1,"do":"hold","slot":"head","item":"minecraft:stick"}]}]}""");
        assertEquals(1, m.size(), m.toString());
        assertEquals("actors[0].actions[0].slot: slot is only for equip", m.get(0));
    }

    @Test
    void equipBadSlot() throws Exception {
        List<String> m = validate("""
                {"length":100,"actors":[{"id":"a","type":"minecraft:zombie","actions":[
                  {"t":1,"do":"equip","slot":"tail","item":"minecraft:stick"}]}]}""");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("equip slot must be one of"), m.get(0));
    }

    @Test
    void sizeOnlyForNarration() throws Exception {
        List<String> m = validate("{\"length\":100,\"words\":[{\"t\":1,\"text\":\"x\",\"size\":2}]}");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("size is only for narration"), m.get(0));
        assertEquals(List.of(), validate("{\"length\":100,\"words\":[{\"t\":1,\"text\":\"x\",\"narration\":true,\"size\":2}]}"));
    }

    @Test
    void subtitleRules() throws Exception {
        assertEquals(List.of(), validate("{\"length\":100,\"words\":[{\"t\":1,\"text\":\"x\",\"subtitle\":true,\"speaker\":\"A\"}]}"));
        List<String> m = validate("{\"length\":100,\"words\":[{\"t\":1,\"text\":\"x\",\"subtitle\":true,\"narration\":true}]}");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("subtitle"), m.get(0));
        m = validate("{\"length\":100,\"actors\":[{\"id\":\"a\",\"type\":\"minecraft:zombie\"}],\"words\":[{\"t\":1,\"by\":\"a\",\"text\":\"x\",\"subtitle\":true}]}");
        assertEquals(1, m.size(), m.toString());
        m = validate("{\"length\":100,\"words\":[{\"t\":1,\"text\":\"x\",\"speaker\":\"A\"}]}");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("speaker"), m.get(0));
    }

    @Test
    void sizeRange() throws Exception {
        for (String size : new String[]{"0.5", "4.5"}) {
            List<String> m = validate("{\"length\":100,\"words\":[{\"t\":1,\"text\":\"x\",\"narration\":true,\"size\":" + size + "}]}");
            assertEquals(1, m.size(), m.toString());
            assertTrue(m.get(0).startsWith("words[0].size"), m.get(0));
        }
        assertEquals(List.of(), validate("{\"length\":100,\"words\":[{\"t\":1,\"text\":\"x\",\"narration\":true,\"size\":4}]}"));
    }

    @Test
    void sfxVolumeRange() throws Exception {
        assertEquals(List.of(), validate("""
                {"length":100,"events":[{"t":1,"kind":"sfx","id":"hogwartsmagic:pensieve.sfx.x","volume":4}]}"""));
        List<String> m = validate("""
                {"length":100,"events":[
                  {"t":1,"kind":"sfx","id":"hogwartsmagic:pensieve.sfx.x","volume":5},
                  {"t":2,"kind":"sfx","id":"hogwartsmagic:pensieve.sfx.nope"}]}""");
        assertEquals(2, m.size(), m.toString());
        assertTrue(m.get(0).startsWith("events[0].volume"), m.get(0));
        assertTrue(m.get(1).startsWith("events[1].id"), m.get(1));
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

    @Test
    void unsupportedSpellIsNamed() throws Exception {
        SceneNames names = new SceneNames(Set.of("minecraft:zombie"), Set.of("stupefy", "serpensortia"), Set.of(), Set.of(),
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        SceneDef def = SceneParser.parse("t", JsonParser.parseString("""
                {"length":100,"actors":[{"id":"a","type":"minecraft:zombie",
                  "actions":[{"t":5,"do":"cast","spell":"serpensortia"}]}]}""").getAsJsonObject());
        List<String> m = SceneValidator.validate(def, names);
        assertEquals(List.of("actors[0].actions[0].spell: no memory visual for 'serpensortia'"), m);
    }

    private static List<String> validateAs(String id, String json) throws Exception {
        SceneDef def = SceneParser.parse(id, JsonParser.parseString(json).getAsJsonObject());
        return SceneValidator.validate(def, NAMES);
    }

    private static String actorScene(String type, String extra) {
        return "{\"length\":100,\"actors\":[{\"id\":\"a\",\"type\":\"" + type + "\"," + extra + "}]}";
    }

    private static void assertMessage(List<String> m, String text) {
        assertTrue(m.stream().anyMatch(x -> x.contains(text)), "expected '" + text + "' in " + m);
    }

    @Test
    void scaleOutOfRangeFails() throws Exception {
        assertMessage(validate(actorScene("player", "\"scale\":5")), "scale: must be 0.25 to 4");
        assertEquals(0, validate(actorScene("player", "\"scale\":0.75")).size());
    }

    @Test
    void viewerSkinWithTextureFails() throws Exception {
        assertMessage(validate(actorScene("player", "\"skin\":{\"viewer\":true,\"texture\":\"harry\"}")),
                "skin: viewer cannot be combined with texture or username");
    }

    @Test
    void viewerSkinOnMobFails() throws Exception {
        assertMessage(validate(actorScene("minecraft:zombie", "\"skin\":{\"viewer\":true}")),
                "skin: only a player actor can have a skin");
        assertEquals(0, validate(actorScene("player", "\"skin\":{\"viewer\":true}")).size());
    }

    @Test
    void nextMustBeValidId() throws Exception {
        assertMessage(validate("{\"length\":100,\"next\":\"Bad Id\"}"), "next: must be a lowercase scene id");
        assertEquals(0, validate("{\"length\":100,\"next\":\"sorting_hut\"}").size());
    }

    @Test
    void handoverColourNames() throws Exception {
        assertEquals(0, validate("{\"length\":100,\"next\":\"b\",\"handover_colour\":\"white\"}").size());
        assertEquals(0, validate("{\"length\":100,\"next\":\"b\",\"handover_colour\":\"black\"}").size());
        assertMessage(validate("{\"length\":100,\"next\":\"b\",\"handover_colour\":\"red\"}"),
                "handover_colour: must be \"black\" or \"white\"");
    }

    @Test
    void handoverColourNeedsNext() throws Exception {
        assertMessage(validate("{\"length\":100,\"handover_colour\":\"white\"}"),
                "handover_colour: \"white\" needs a next scene");
        assertEquals(0, validate("{\"length\":100,\"handover_colour\":\"black\"}").size());
        assertEquals(0, validate("{\"length\":100}").size());
    }

    @Test
    void nextCannotBeSelf() throws Exception {
        assertMessage(validateAs("a", "{\"length\":100,\"next\":\"a\"}"), "next: a scene cannot chain to itself");
    }

    private static String flood(String extra) {
        return "{\"length\":100,\"events\":[{\"t\":1,\"kind\":\"effect\",\"id\":\"letter_flood\"" + extra + "}]}";
    }

    @Test
    void letterFloodNeedsYaw() throws Exception {
        List<String> m = validate(flood(",\"at\":[0,0,0]"));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("letter_flood needs yaw"), m.get(0));
        assertEquals(List.of(), validate(flood(",\"at\":[0,0,0],\"yaw\":90")));
    }

    @Test
    void letterFloodNeedsAt() throws Exception {
        List<String> m = validate(flood(",\"yaw\":90"));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("letter_flood needs at"), m.get(0));
    }

    @Test
    void letterFloodCountRange() throws Exception {
        String base = ",\"at\":[0,0,0],\"yaw\":0";
        assertEquals(List.of(), validate(flood(base + ",\"count\":400")));
        assertEquals(List.of(), validate(flood(base + ",\"count\":1")));
        assertEquals(1, validate(flood(base + ",\"count\":401")).size());
        assertEquals(1, validate(flood(base + ",\"count\":0")).size());
        assertEquals(List.of(), validate(flood(base + ",\"spread\":[0.5,12,3]")));
        assertEquals(1, validate(flood(base + ",\"spread\":[0.4,2,3]")).size());
        assertEquals(1, validate(flood(base + ",\"spread\":[3,2,12.5]")).size());
    }

    @Test
    void letterFloodYawMustBeAQuarterTurn() throws Exception {
        String base = ",\"at\":[0,0,0]";
        for (int yaw : new int[] {0, 90, 180, -90, 270, -180}) {
            assertEquals(List.of(), validate(flood(base + ",\"yaw\":" + yaw)), "yaw " + yaw);
        }
        List<String> m = validate(flood(base + ",\"yaw\":45"));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("multiple of 90"), m.get(0));
        assertEquals(1, validate(flood(base + ",\"yaw\":91.5")).size());
    }

    @Test
    void letterFloodBox() throws Exception {
        String base = ",\"at\":[0,0,0],\"yaw\":0";
        assertEquals(List.of(), validate(flood(base + ",\"box\":[[-3,0,1],[3,5,9]]")));
        List<String> m = validate(flood(base + ",\"box\":[[3,0,1],[-3,5,9]]"));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("box"), m.get(0));
        assertEquals(1, validate(flood(base + ",\"box\":[[-3,0,1],[3,0,9]]")).size());   // flat in y
        assertEquals(1, validate(flood(base + ",\"box\":[[-3,0,1],[3,5,41]]")).size());  // beyond radius 40
        assertEquals(1, validate(flood(base + ",\"box\":[[-41,0,1],[3,5,9]]")).size());  // beyond radius 40
        // A box of the wrong shape is a parse error.
        org.junit.jupiter.api.Assertions.assertThrows(com.hogwartsmagic.pensieve.scene.SceneException.class,
                () -> validate(flood(base + ",\"box\":[[-3,0,1]]")));
        org.junit.jupiter.api.Assertions.assertThrows(com.hogwartsmagic.pensieve.scene.SceneException.class,
                () -> validate(flood(base + ",\"box\":[[-3,0],[3,5,9]]")));
        // box is only for letter_flood
        assertEquals(1, validate("{\"length\":100,\"events\":[{\"t\":1,\"kind\":\"effect\",\"id\":\"lightning\","
                + "\"at\":[0,0,0],\"box\":[[0,0,0],[1,1,1]]}]}").size());
    }

    private static String ceiling(String extra) {
        return "{\"length\":100,\"events\":[{\"t\":1,\"kind\":\"effect\",\"id\":\"enchanted_ceiling\"" + extra + "}]}";
    }

    @Test
    void ceilingNeedsSpread() throws Exception {
        List<String> m = validate(ceiling(",\"at\":[0,20,0]"));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("enchanted_ceiling needs spread"), m.get(0));
        assertEquals(List.of(), validate(ceiling(",\"at\":[0,20,0],\"spread\":[15,0,10]")));
    }

    @Test
    void ceilingNeedsAt() throws Exception {
        List<String> m = validate(ceiling(",\"spread\":[15,0,10]"));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("enchanted_ceiling needs at"), m.get(0));
    }

    @Test
    void ceilingSpreadRange() throws Exception {
        String at = ",\"at\":[0,20,0]";
        assertEquals(List.of(), validate(ceiling(at + ",\"spread\":[1,0,100]")));
        assertEquals(List.of(), validate(ceiling(at + ",\"spread\":[100,0,1],\"duration\":40")));
        assertEquals(1, validate(ceiling(at + ",\"spread\":[0.5,0,10]")).size());
        assertEquals(1, validate(ceiling(at + ",\"spread\":[10,0,100.5]")).size());
        assertEquals(2, validate(ceiling(at + ",\"spread\":[0,0,0]")).size());
    }

    @Test
    void ceilingStyle() throws Exception {
        String base = ",\"at\":[0,20,0],\"spread\":[15,0,10]";
        assertEquals(List.of(), validate(ceiling(base + ",\"style\":\"storm\"")));
        assertEquals(List.of(), validate(ceiling(base + ",\"style\":\"night\"")));
        List<String> m = validate(ceiling(base + ",\"style\":\"sunny\""));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("style"), m.get(0));
        m = validate(rays(",\"at\":[0,0,0],\"yaw\":90,\"spread\":[2,5,12],\"style\":\"storm\""));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("style is only for enchanted_ceiling"), m.get(0));
    }

    private static String sky(String extra) {
        return "{\"length\":100,\"events\":[{\"t\":1,\"kind\":\"effect\",\"id\":\"sky\"" + extra + "}]}";
    }

    @Test
    void skyStormAccepted() throws Exception {
        assertEquals(List.of(), validate(sky(",\"at\":[0,0,0],\"style\":\"storm\"")));
    }

    @Test
    void rainSpread200Accepted() throws Exception {
        assertEquals(List.of(), validate(rain(",\"at\":[0,10,0],\"spread\":[200,20,200]")));
        assertMessage(validate(rain(",\"at\":[0,10,0],\"spread\":[201,20,200]")), "spread[0]");
        assertMessage(validate(rain(",\"at\":[0,10,0],\"spread\":[200,20,201]")), "spread[2]");
    }

    @Test
    void skyStyleAndAt() throws Exception {
        assertEquals(List.of(), validate(sky(",\"at\":[0,0,0]")));
        for (String s : new String[] {"day", "overcast", "dawn", "storm"}) {
            assertEquals(List.of(), validate(sky(",\"at\":[0,0,0],\"style\":\"" + s + "\",\"duration\":200")), s);
        }
        List<String> m = validate(sky(",\"at\":[0,0,0],\"style\":\"blizzard\""));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("style"), m.get(0));
        m = validate(sky(",\"style\":\"day\""));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("sky needs at"), m.get(0));
    }

    @Test
    void fractionalAtForEffectsButNotBlocks() throws Exception {
        String json = ceiling(",\"at\":[46.5,21,22.5],\"spread\":[36.5,0,17.5]");
        SceneDef def = SceneParser.parse("t", JsonParser.parseString(json).getAsJsonObject());
        assertEquals(46.5f, def.events().get(0).at()[0], 1e-6f);
        assertEquals(22.5f, def.events().get(0).at()[2], 1e-6f);
        assertEquals(List.of(), SceneValidator.validate(def, NAMES));
        List<String> m = validate("""
                {"length":100,"events":[{"t":1,"kind":"block","id":"minecraft:stone","at":[0.5,0,0]}]}""");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("whole-block"), m.get(0));
        assertEquals(List.of(), validate("""
                {"length":100,"events":[{"t":1,"kind":"block","id":"minecraft:stone","at":[-3,0,2]}]}"""));
    }

    private static String candles(String extra) {
        return "{\"length\":100,\"events\":[{\"t\":1,\"kind\":\"effect\",\"id\":\"floating_candles\"" + extra + "}]}";
    }

    @Test
    void candlesNeedAtAndSpread() throws Exception {
        assertEquals(List.of(), validate(candles(",\"at\":[0,14,0],\"spread\":[10,2,30],\"count\":600")));
        assertEquals(List.of(), validate(candles(",\"at\":[0,14,0],\"spread\":[10,2,30]")));
        List<String> m = validate(candles(",\"spread\":[10,2,30]"));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("floating_candles needs at"), m.get(0));
        m = validate(candles(",\"at\":[0,14,0]"));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("floating_candles needs spread"), m.get(0));
    }

    @Test
    void candlesRanges() throws Exception {
        String at = ",\"at\":[0,14,0]";
        assertEquals(List.of(), validate(candles(at + ",\"spread\":[0.5,0.5,100],\"count\":1500")));
        assertEquals(1, validate(candles(at + ",\"spread\":[10,2,30],\"count\":1501")).size());
        assertEquals(1, validate(candles(at + ",\"spread\":[10,2,30],\"count\":0")).size());
        assertEquals(1, validate(candles(at + ",\"spread\":[0.4,2,30]")).size());
        assertEquals(1, validate(candles(at + ",\"spread\":[10,2,101]")).size());
    }

    private static String rays(String extra) {
        return "{\"length\":100,\"events\":[{\"t\":1,\"kind\":\"effect\",\"id\":\"light_rays\"" + extra + "}]}";
    }

    @Test
    void raysNeedAtYawAndSpread() throws Exception {
        assertEquals(List.of(), validate(rays(",\"at\":[0,0,0],\"yaw\":90,\"spread\":[2,5,12],\"duration\":48")));
        List<String> m = validate(rays(",\"yaw\":90,\"spread\":[2,5,12]"));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("light_rays needs at"), m.get(0));
        m = validate(rays(",\"at\":[0,0,0],\"spread\":[2,5,12]"));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("light_rays needs yaw"), m.get(0));
        m = validate(rays(",\"at\":[0,0,0],\"yaw\":90"));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("light_rays needs spread"), m.get(0));
    }

    @Test
    void raysSpreadRange() throws Exception {
        String base = ",\"at\":[0,0,0],\"yaw\":0";
        assertEquals(List.of(), validate(rays(base + ",\"spread\":[0.5,1,1]")));
        assertEquals(List.of(), validate(rays(base + ",\"spread\":[12,24,60]")));
        assertEquals(1, validate(rays(base + ",\"spread\":[0.4,5,12]")).size());
        assertEquals(1, validate(rays(base + ",\"spread\":[2,25,12]")).size());
        assertEquals(1, validate(rays(base + ",\"spread\":[2,5,61]")).size());
    }

    @Test
    void yawOnlyOnEffects() throws Exception {
        List<String> m = validate("""
                {"length":100,"events":[{"t":1,"kind":"particles","id":"minecraft:flame","at":[0,0,0],"yaw":90}]}""");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("yaw is only for effects"), m.get(0));
    }

    @Test
    void raysIntensityRange() throws Exception {
        String base = ",\"at\":[0,0,0],\"yaw\":0,\"spread\":[2,5,12]";
        assertEquals(List.of(), validate(rays(base + ",\"intensity\":0.1")));
        assertEquals(List.of(), validate(rays(base + ",\"intensity\":2")));
        assertEquals(1, validate(rays(base + ",\"intensity\":0.05")).size());
        assertEquals(1, validate(rays(base + ",\"intensity\":2.5")).size());
    }

    @Test
    void intensityOnlyForLightRays() throws Exception {
        List<String> m = validate("""
                {"length":100,"events":[{"t":1,"kind":"effect","id":"lightning","at":[0,0,0],"intensity":1}]}""");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("intensity is only for light_rays"), m.get(0));
        m = validate("""
                {"length":100,"events":[{"t":1,"kind":"particles","id":"minecraft:flame","at":[0,0,0],"intensity":1}]}""");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("intensity is only for light_rays"), m.get(0));
    }

    @Test
    void playerClipsMustBeKnown() throws Exception {
        assertEquals(0, validate(actorScene("player",
                "\"anims\":[{\"t\":0,\"name\":\"clap\"},{\"t\":20,\"name\":\"CHEER_STAND\",\"blend\":3}]")).size());
        List<String> m = validate(actorScene("player", "\"anims\":[{\"t\":0,\"name\":\"MOONWALK\"}]"));
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).startsWith("actors[0].anims[0].name: unknown player clip 'MOONWALK'"), m.get(0));
        assertTrue(m.get(0).contains("CLAP"), m.get(0));
        // Creature animation names are checked by the client (it has the models), not here.
        assertEquals(0, validate(actorScene("hogwartsmagic:basilisk", "\"anims\":[{\"t\":0,\"name\":\"REAR\"}]")).size());
    }

    @Test
    void lifeIsOnlyForPlayers() throws Exception {
        assertEquals(0, validate(actorScene("player", "\"life\":false")).size());
        assertMessage(validate(actorScene("minecraft:zombie", "\"life\":false")), "life: only a player actor has life");
    }

    @Test
    void eyesPeekTakesAnOpenFractionAsIntensity() throws Exception {
        assertEquals(List.of(), validate("""
                {"length":100,"events":[{"t":1,"kind":"effect","id":"eyes_peek"}]}"""));
        assertEquals(List.of(), validate("""
                {"length":100,"events":[{"t":1,"kind":"effect","id":"eyes_peek","duration":60,"intensity":0.35}]}"""));
        List<String> m = validate("""
                {"length":100,"events":[{"t":1,"kind":"effect","id":"eyes_peek","intensity":1.5}]}""");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("intensity"), m.get(0));
        assertEquals(1, validate("""
                {"length":100,"events":[{"t":1,"kind":"effect","id":"eyes_peek","intensity":0.01}]}""").size());
    }

    @Test
    void wakeIsAnEndAction() throws Exception {
        assertEquals(List.of(), validate("{\"length\":100,\"end_action\":\"wake\"}"));
    }

    private static String sea(String extra) {
        return "{\"length\":100,\"radius\":40,\"events\":[{\"t\":1,\"kind\":\"effect\",\"id\":\"rough_sea\"" + extra + "}]}";
    }

    @Test
    void roughSeaNeedsAnAreaAndRangedIntensity() throws Exception {
        assertEquals(List.of(), validate(sea(",\"at\":[0,0,0],\"spread\":[30,0,30]")));
        assertEquals(List.of(), validate(sea(",\"at\":[0,0,0],\"spread\":[30,0,30],\"intensity\":2,\"duration\":60")));
        assertMessage(validate(sea(",\"at\":[0,0,0]")), "rough_sea needs spread");
        assertMessage(validate(sea(",\"spread\":[30,0,30]")), "rough_sea needs at");
        assertMessage(validate(sea(",\"at\":[0,0,0],\"spread\":[1,0,30]")), "spread[0]");
        assertMessage(validate(sea(",\"at\":[0,0,0],\"spread\":[30,0,120]")), "spread[2]");
        assertMessage(validate(sea(",\"at\":[0,0,0],\"spread\":[30,0,30],\"intensity\":2.5")), "intensity");
        assertMessage(validate(sea(",\"at\":[0,0,0],\"spread\":[30,0,30],\"intensity\":0.05")), "intensity");
        // The area must lie inside the scene radius (40).
        assertMessage(validate(sea(",\"at\":[20,0,0],\"spread\":[30,0,10]")), "radius");
    }

    private static String rain(String extra) {
        return "{\"length\":100,\"events\":[{\"t\":1,\"kind\":\"effect\",\"id\":\"rain\"" + extra + "}]}";
    }

    @Test
    void rainNeedsABoxAndRangedIntensity() throws Exception {
        assertEquals(List.of(), validate(rain(",\"at\":[0,10,0],\"spread\":[20,10,20]")));
        assertEquals(List.of(), validate(rain(",\"at\":[0,10,0],\"spread\":[20,10,20],\"intensity\":1.6")));
        assertMessage(validate(rain(",\"at\":[0,10,0]")), "rain needs spread");
        assertMessage(validate(rain(",\"spread\":[20,10,20]")), "rain needs at");
        assertMessage(validate(rain(",\"at\":[0,10,0],\"spread\":[0.5,10,20]")), "spread[0]");
        assertMessage(validate(rain(",\"at\":[0,10,0],\"spread\":[20,10,20],\"intensity\":3")), "intensity");
    }

    private static String riders(String riderType, String ride) {
        return "{\"length\":100,\"actors\":["
                + "{\"id\":\"boat\",\"type\":\"hogwartsmagic:memory_rowboat\"},"
                + "{\"id\":\"zed\",\"type\":\"minecraft:zombie\"},"
                + "{\"id\":\"r\",\"type\":\"" + riderType + "\",\"ride\":\"" + ride + "\"}]}";
    }

    private static String boatOnSea(float intensity) {
        return "{\"length\":200,\"radius\":40,\"actors\":[{\"id\":\"boat\",\"type\":\"hogwartsmagic:memory_rowboat\","
                + "\"path\":[{\"t\":0,\"pos\":[-35,0,0]},{\"t\":150,\"pos\":[5,0,0]}]}],"
                + "\"events\":[{\"t\":0,\"kind\":\"effect\",\"id\":\"rough_sea\",\"at\":[0,0,0],\"spread\":[10,0,10],"
                + "\"intensity\":" + intensity + "}]}";
    }

    @Test
    void aBoatMayOnlyRideASeaUpToIntensityOne() throws Exception {
        assertEquals(List.of(), validate(boatOnSea(1.0f)));
        // The boat reaches the sea part-way along its path: still caught.
        assertMessage(validate(boatOnSea(1.5f)), "memory_rowboat 'boat' rides a rough_sea of intensity 1.5");
        // A rough sea of 2 with no boat on it is fine.
        assertEquals(List.of(), validate(sea(",\"at\":[0,0,0],\"spread\":[30,0,30],\"intensity\":2")));
    }

    @Test
    void rideNamesARowboat() throws Exception {
        assertEquals(List.of(), validate(riders("player", "boat")));
        assertEquals(List.of(), validate(riders("minecraft:zombie", "boat")));
        assertMessage(validate(riders("player", "ghost")), "ride: unknown actor 'ghost'");
        assertMessage(validate(riders("player", "zed")), "ride: 'zed' is not a vehicle");
        assertMessage(validate(riders("player", "r")), "ride: an actor cannot ride itself");
        assertMessage(validate(riders("hogwartsmagic:memory_rowboat", "boat")), "ride: a vehicle cannot ride");
        assertMessage(validate(riders("hogwartsmagic:memory_broom", "boat")), "ride: a vehicle cannot ride");
    }

    // ---- brooms, ride windows, spline actor tracks -----------------------------------------------------------------

    private static final String BROOM = "{\"id\":\"b\",\"type\":\"hogwartsmagic:memory_broom\"}";

    private static String onBroom(String... rides) {
        StringBuilder sb = new StringBuilder("{\"length\":200,\"actors\":[" + BROOM);
        for (int i = 0; i < rides.length; i++) {
            sb.append(",{\"id\":\"r").append(i).append("\",\"type\":\"player\",\"ride\":").append(rides[i]).append('}');
        }
        return sb.append("]}").toString();
    }

    @Test
    void rideWindowParsed() throws Exception {
        SceneDef d = SceneParser.parse("t", JsonParser.parseString(
                onBroom("{\"on\":\"b\",\"from\":40,\"until\":\"6s\"}", "\"b\"")).getAsJsonObject());
        SceneDef.ActorDef r0 = d.actors().get(1), r1 = d.actors().get(2);
        assertEquals("b", r0.ride());
        assertEquals(40f, r0.rideFrom());
        assertEquals(120f, r0.rideUntil());
        assertEquals("b", r1.ride());
        assertEquals(Float.NEGATIVE_INFINITY, r1.rideFrom());
        assertEquals(Float.POSITIVE_INFINITY, r1.rideUntil());
        assertEquals(List.of(), validate(onBroom("{\"on\":\"b\",\"from\":40,\"until\":120}")));
        assertEquals(List.of(), validate(onBroom("{\"on\":\"b\",\"from\":40}", "{\"on\":\"b\",\"until\":200}")));
        assertMessage(validate(onBroom("{\"on\":\"b\",\"from\":120,\"until\":40}")), "must be before until");
        assertMessage(validate(onBroom("{\"on\":\"b\",\"from\":40,\"until\":250}")), "ride.until: 250.0 is outside");
        assertMessage(validate(onBroom("{\"on\":\"b\",\"from\":-5}")), "ride.from: -5.0 is outside");
        assertThrows(SceneException.class, () -> validate(onBroom("{\"from\":4}")));
        assertThrows(SceneException.class, () -> validate(onBroom("{\"on\":\"b\",\"at\":4}")));
    }

    @Test
    void rideWindowOverlapRejected() throws Exception {
        // Three riders take turns two at a time: fine. A third joins while two are on: rejected.
        assertEquals(List.of(), validate(onBroom("{\"on\":\"b\",\"from\":0,\"until\":100}",
                "{\"on\":\"b\",\"from\":50,\"until\":150}", "{\"on\":\"b\",\"from\":100,\"until\":200}")));
        List<String> m = validate(onBroom("{\"on\":\"b\",\"from\":0,\"until\":100}",
                "{\"on\":\"b\",\"from\":50,\"until\":150}", "{\"on\":\"b\",\"from\":99,\"until\":200}"));
        assertMessage(m, "memory_broom 'b' carries 3 riders at t 99.0; it carries at most 2");
    }

    @Test
    void rideOnNonVehicleRejected() throws Exception {
        String json = "{\"length\":100,\"actors\":[{\"id\":\"z\",\"type\":\"minecraft:zombie\"},"
                + "{\"id\":\"p\",\"type\":\"player\"},"
                + "{\"id\":\"r\",\"type\":\"player\",\"ride\":{\"on\":\"z\",\"from\":10,\"until\":50}},"
                + "{\"id\":\"q\",\"type\":\"player\",\"ride\":\"p\"}]}";
        List<String> m = validate(json);
        assertEquals(2, m.size(), m.toString());
        assertMessage(m, "actors[2].ride: 'z' is not a vehicle");
        assertMessage(m, "actors[3].ride: 'p' is not a vehicle");
        // A vehicle never rides: a broom on a broom.
        assertMessage(validate("{\"length\":100,\"actors\":[" + BROOM + ",{\"id\":\"c\",\"type\":"
                + "\"hogwartsmagic:memory_broom_racing\",\"ride\":\"b\"}]}"), "actors[1].ride: a vehicle cannot ride");
    }

    @Test
    void brooms3RidersRejected() throws Exception {
        assertEquals(List.of(), validate(onBroom("\"b\"", "\"b\"")));
        assertMessage(validate(onBroom("\"b\"", "\"b\"", "\"b\"")), "carries 3 riders at the start; it carries at most 2");
        String racing = onBroom("\"b\"", "\"b\"", "\"b\"").replace("memory_broom\"", "memory_broom_racing\"");
        assertMessage(validate(racing), "memory_broom_racing 'b' carries 3 riders");
    }

    @Test
    void rowboatKeepsFourRiders() throws Exception {
        StringBuilder sb = new StringBuilder("{\"length\":100,\"actors\":[{\"id\":\"boat\",\"type\":\"hogwartsmagic:memory_rowboat\"}");
        for (int i = 0; i < 4; i++) sb.append(",{\"id\":\"r").append(i).append("\",\"type\":\"player\",\"ride\":\"boat\"}");
        assertEquals(List.of(), validate(sb + "]}"));
        sb.append(",{\"id\":\"r9\",\"type\":\"player\",\"ride\":\"boat\"}");
        assertMessage(validate(sb + "]}"), "memory_rowboat 'boat' carries 5 riders at the start; it carries at most 4");
    }

    @Test
    void rollAllowedOnBroom() throws Exception {
        for (String type : new String[]{"hogwartsmagic:memory_broom", "hogwartsmagic:memory_broom_racing", "hogwartsmagic:memory_block"}) {
            String block = type.endsWith("block") ? ",\"block\":\"minecraft:stone\"" : "";
            assertEquals(List.of(), validate("{\"length\":100,\"actors\":[{\"id\":\"b\",\"type\":\"" + type + "\"" + block
                    + ",\"look\":[{\"t\":0,\"yaw\":10,\"pitch\":20,\"roll\":35}]}]}"), type);
        }
    }

    @Test
    void rollStillRejectedOnPlayer() throws Exception {
        assertMessage(validate("{\"length\":100,\"actors\":[{\"id\":\"p\",\"type\":\"player\","
                + "\"look\":[{\"t\":0,\"yaw\":10,\"roll\":35}]}]}"), "actors[0].look: roll is only for");
        assertMessage(validate("{\"length\":100,\"actors\":[{\"id\":\"p\",\"type\":\"hogwartsmagic:memory_rowboat\","
                + "\"look\":[{\"t\":0,\"yaw\":10,\"roll\":5}]}]}"), "roll is only for");
    }

    @Test
    void splineRejectedOnLimbs() throws Exception {
        // Path and look keys take spline; limb keys do not.
        assertEquals(List.of(), validate("{\"length\":100,\"actors\":[{\"id\":\"p\",\"type\":\"player\","
                + "\"path\":[{\"t\":0,\"pos\":[0,0,0]},{\"t\":20,\"pos\":[3,0,0],\"ease\":\"spline\"}],"
                + "\"look\":[{\"t\":0,\"yaw\":0},{\"t\":20,\"yaw\":40,\"ease\":\"spline\"}]}]}"));
        SceneException e = assertThrows(SceneException.class,
                () -> validate("{\"length\":100,\"actors\":[{\"id\":\"p\",\"type\":\"player\",\"limbs\":{\"right_arm\":"
                        + "{\"rot\":[{\"t\":0,\"v\":[0,0,0]},{\"t\":9,\"v\":[-90,0,0],\"ease\":\"spline\"}]}}}]}"));
        assertTrue(e.getMessage().contains("limbs.right_arm.rot[1].ease"), e.getMessage());
        assertTrue(e.getMessage().contains("not limbs"), e.getMessage());
    }
    @Test
    void ballTypesParse() throws Exception {
        // The balls take a path (the centre) and a plain look; they are not vehicles and cannot ride.
        for (String type : new String[]{"hogwartsmagic:memory_quaffle", "hogwartsmagic:memory_bludger",
                "hogwartsmagic:memory_remembrall", "hogwartsmagic:golden_snitch"}) {
            assertEquals(List.of(), validate("{\"length\":100,\"actors\":[{\"id\":\"ball\",\"type\":\"" + type + "\","
                    + "\"scale\":1.0,\"path\":[{\"t\":0,\"pos\":[0,1,0]},{\"t\":20,\"pos\":[3,2,1],\"ease\":\"spline\"}]}]}"), type);
        }
        // They roll by themselves, so a look roll is no use to them.
        assertMessage(validate("{\"length\":100,\"actors\":[{\"id\":\"q\",\"type\":\"hogwartsmagic:memory_quaffle\","
                + "\"look\":[{\"t\":0,\"yaw\":10,\"roll\":35}]}]}"), "roll is only for");
        assertMessage(validate("{\"length\":100,\"actors\":[{\"id\":\"q\",\"type\":\"hogwartsmagic:memory_quaffle\"},"
                + "{\"id\":\"b\",\"type\":\"hogwartsmagic:memory_bludger\",\"ride\":\"q\"}]}"), "ride");
    }

    @Test
    void fireboltAndGoldenEggParse() throws Exception {
        // The Firebolt is a broom: it rolls, carries two riders and cannot carry three; the egg takes a path and a yaw only.
        String fire = "hogwartsmagic:memory_broom_firebolt";
        assertEquals(List.of(), validate("{\"length\":100,\"actors\":[{\"id\":\"b\",\"type\":\"" + fire + "\","
                + "\"path\":[{\"t\":0,\"pos\":[0,5,0]},{\"t\":40,\"pos\":[0,6,20],\"ease\":\"spline\"}],"
                + "\"look\":[{\"t\":0,\"yaw\":10,\"roll\":20}]},"
                + "{\"id\":\"r\",\"type\":\"player\",\"scale\":0.85,\"ride\":\"b\"}]}"));
        assertMessage(validate("{\"length\":100,\"actors\":[{\"id\":\"b\",\"type\":\"" + fire + "\"},"
                + "{\"id\":\"a\",\"type\":\"player\",\"ride\":\"b\"},{\"id\":\"c\",\"type\":\"player\",\"ride\":\"b\"},"
                + "{\"id\":\"d\",\"type\":\"player\",\"ride\":\"b\"}]}"), "memory_broom_firebolt 'b' carries 3 riders");
        String egg = "hogwartsmagic:memory_golden_egg";
        assertEquals(List.of(), validate("{\"length\":100,\"actors\":[{\"id\":\"e\",\"type\":\"" + egg + "\",\"scale\":0.85,"
                + "\"path\":[{\"t\":0,\"pos\":[0,1,0]},{\"t\":20,\"pos\":[3,2,1],\"ease\":\"spline\"}],"
                + "\"look\":[{\"t\":0,\"yaw\":10}]}]}"));
        assertMessage(validate("{\"length\":100,\"actors\":[{\"id\":\"e\",\"type\":\"" + egg
                + "\",\"look\":[{\"t\":0,\"yaw\":10,\"roll\":35}]}]}"), "roll is only for");
        assertMessage(validate("{\"length\":100,\"actors\":[{\"id\":\"e\",\"type\":\"" + egg + "\"},"
                + "{\"id\":\"b\",\"type\":\"player\",\"ride\":\"e\"}]}"), "ride");
        // Like the balls, the egg must stay under the cut speed.
        assertEquals(List.of(), validate(mover(egg, "{\"t\":0,\"pos\":[0,2,0]},{\"t\":20,\"pos\":[38,2,0],\"ease\":\"linear\"}")));
        assertMessage(validate(mover(egg, "{\"t\":0,\"pos\":[0,2,0]},{\"t\":20,\"pos\":[60,2,0],\"ease\":\"linear\"}")),
                "moves 3.0 blocks in a tick");
    }

    private static String mover(String type, String keys) {
        return "{\"length\":100,\"actors\":[{\"id\":\"m\",\"type\":\"" + type + "\",\"path\":[" + keys + "]}]}";
    }

    @Test
    void ballsAndSnitchMustStayUnderTheCutSpeed() throws Exception {
        for (String type : new String[]{"hogwartsmagic:memory_bludger", "hogwartsmagic:memory_quaffle",
                "hogwartsmagic:memory_remembrall", "hogwartsmagic:golden_snitch"}) {
            // 1.9 blocks a tick is fine; 3 a tick is a cut by accident: no interpolation, no roll.
            assertEquals(List.of(), validate(mover(type, "{\"t\":0,\"pos\":[0,2,0]},{\"t\":20,\"pos\":[38,2,0],\"ease\":\"linear\"}")), type);
            assertMessage(validate(mover(type, "{\"t\":0,\"pos\":[0,2,0]},{\"t\":20,\"pos\":[60,2,0],\"ease\":\"linear\"}")),
                    "moves 3.0 blocks in a tick");
            // A deliberate cut (two keys under half a tick apart) is allowed.
            assertEquals(List.of(), validate(mover(type, "{\"t\":0,\"pos\":[0,2,0]},{\"t\":10,\"pos\":[0,2,0]},"
                    + "{\"t\":10.001,\"pos\":[40,2,0],\"ease\":\"linear\"},{\"t\":20,\"pos\":[40,2,0]}")), type);
        }
        // Players are not held to it.
        assertEquals(List.of(), validate(mover("player", "{\"t\":0,\"pos\":[0,2,0]},{\"t\":20,\"pos\":[60,2,0],\"ease\":\"linear\"}")));
    }

    // ------------------------------------------------------------------ the stadium crowd

    private static String crowdScene(String events) {
        return "{\"length\":400,\"radius\":60,\"events\":[" + events + "]}";
    }

    private static final String ROW = "[[-10,3.5,20,10,3.5,20]]";

    @Test
    void rowsOnlyOnCrowd() throws Exception {
        assertEquals(List.of(), validate(crowdScene(
                "{\"t\":0,\"kind\":\"effect\",\"id\":\"crowd\",\"at\":[0,0,0],\"rows\":" + ROW + "}")));
        assertMessage(validate(crowdScene(
                "{\"t\":0,\"kind\":\"effect\",\"id\":\"rain\",\"at\":[0,10,0],\"spread\":[20,10,20],\"rows\":" + ROW + "}")),
                "rows is only for crowd");
        assertMessage(validate(crowdScene(
                "{\"t\":0,\"kind\":\"particles\",\"id\":\"minecraft:flame\",\"rows\":" + ROW + "}")),
                "rows is only for crowd");
        assertMessage(validate(crowdScene("{\"t\":0,\"kind\":\"effect\",\"id\":\"crowd\",\"at\":[0,0,0]}")),
                "crowd needs rows");
        // A row is six numbers, or seven with the standing flag.
        assertThrows(SceneException.class, () -> validate(crowdScene(
                "{\"t\":0,\"kind\":\"effect\",\"id\":\"crowd\",\"at\":[0,0,0],\"rows\":[[0,0,0,1,0]]}")));
        assertEquals(List.of(), validate(crowdScene(
                "{\"t\":0,\"kind\":\"effect\",\"id\":\"crowd\",\"at\":[0,0,0],\"rows\":[[-10,3,20,10,3,20,1]]}")));
    }

    @Test
    void rowsInsideRadius() throws Exception {
        assertMessage(validate(crowdScene(
                "{\"t\":0,\"kind\":\"effect\",\"id\":\"crowd\",\"at\":[0,0,0],\"rows\":[[-10,3.5,20,10,3.5,20],[50,3.5,0,61,3.5,0]]}")),
                "rows[1]: reaches beyond the scene radius 60");
        assertMessage(validate(crowdScene(
                "{\"t\":0,\"kind\":\"effect\",\"id\":\"crowd\",\"at\":[0,0,0],\"rows\":[[0,3.5,-61,0,3.5,-50]]}")),
                "rows[0]: reaches beyond");
    }

    @Test
    void crowdStyleNames() throws Exception {
        for (String s : new String[] {"gryffindor", "hufflepuff", "ravenclaw", "slytherin", "mixed", "triwizard", "death_eaters"}) {
            assertEquals(List.of(), validate(crowdScene("{\"t\":0,\"kind\":\"effect\",\"id\":\"crowd\",\"at\":[0,0,0],\"style\":\""
                    + s + "\",\"intensity\":0.5,\"count\":10,\"rows\":" + ROW + "}")), s);
        }
        assertMessage(validate(crowdScene("{\"t\":0,\"kind\":\"effect\",\"id\":\"crowd\",\"at\":[0,0,0],\"style\":\"durmstrang\",\"rows\":"
                + ROW + "}")), "style: 'durmstrang' is not one of");
        assertMessage(validate(crowdScene("{\"t\":0,\"kind\":\"effect\",\"id\":\"crowd\",\"at\":[0,0,0],\"intensity\":1.5,\"rows\":"
                + ROW + "}")), "intensity: 1.5 is outside 0.1 to 1");
        for (String s : new String[] {"cheer", "goal", "gasp", "erupt", "chant", "sit"}) {
            assertEquals(List.of(), validate(crowdScene("{\"t\":10,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0],"
                    + "\"spread\":[30,10,30],\"style\":\"" + s + "\",\"duration\":80}")), s);
        }
        assertMessage(validate(crowdScene("{\"t\":10,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0],\"style\":\"boo\"}")),
                "style: 'boo' is not one of");
        assertMessage(validate(crowdScene("{\"t\":10,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0]}")),
                "crowd_react needs a style");
        assertMessage(validate(crowdScene("{\"t\":10,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0],\"style\":\"erupt\",\"duration\":40}")),
                "an erupt lasts at least 60 ticks");
        assertMessage(validate(crowdScene("{\"t\":10,\"kind\":\"effect\",\"id\":\"rain\",\"at\":[0,10,0],\"spread\":[20,10,20],\"style\":\"goal\"}")),
                "style is only for enchanted_ceiling, sky, crowd and crowd_react");
    }

    @Test
    void crowdCap4000() throws Exception {
        // 50 rows of 90 blocks hold about 5,000 seats: one event is capped at 4,000 by itself, two events capped at
        // 2,000 each fit, three of them (6,000) do not: the cap counts the whole scene.
        StringBuilder rows = new StringBuilder("[");
        for (int i = 0; i < 50; i++) {
            if (i > 0) rows.append(',');
            rows.append("[-45,").append(i).append(".5,").append(10 + i).append(",45,").append(i).append(".5,").append(10 + i).append(']');
        }
        rows.append(']');
        String full = "{\"t\":0,\"kind\":\"effect\",\"id\":\"crowd\",\"at\":[0,0,0],\"rows\":" + rows + "}";
        String half = "{\"t\":0,\"kind\":\"effect\",\"id\":\"crowd\",\"at\":[0,0,0],\"count\":2000,\"rows\":" + rows + "}";
        assertEquals(List.of(), validate(crowdScene(half + "," + half.replace("\"t\":0", "\"t\":1"))));
        List<String> m = validate(crowdScene(half + "," + half.replace("\"t\":0", "\"t\":1") + ","
                + half.replace("\"t\":0", "\"t\":2")));
        assertMessage(m, "the crowd effects seat 6000 spectators; a scene holds at most 4000");
        // One event alone is capped at 4,000 by the effect itself, so it fits.
        assertEquals(List.of(), validate(crowdScene(full)));
        assertMessage(validate(crowdScene(full.replace("\"rows\"", "\"count\":5000,\"rows\""))), "count: 5000 is outside 1 to 4000");
    }

    @Test
    void standersAcrossCrowdEventsValidate() throws Exception {
        // Seats and their rail in separate crowd events (ruling C11 allows one event per stand): valid, and the rail's
        // stander in front of the seat is left out scene-wide, so the scene seats 1 + the rest of the rail.
        String seats = "{\"t\":0,\"kind\":\"effect\",\"id\":\"crowd\",\"at\":[0,0,0],\"rows\":[[-0.4,3.5,20,0.4,3.5,20]]}";
        String rail = "{\"t\":0,\"kind\":\"effect\",\"id\":\"crowd\",\"at\":[0,0,0],\"rows\":[[-0.2,3,18.6,0.2,3,18.6,1]]}";
        String json = crowdScene(seats + "," + rail);
        assertEquals(List.of(), validate(json));
        SceneDef def = SceneParser.parse("t", JsonParser.parseString(json).getAsJsonObject());
        int total = 0;
        for (com.hogwartsmagic.pensieve.scene.Crowd c : com.hogwartsmagic.pensieve.scene.Crowd.ofScene(def.events())) total += c.size();
        assertEquals(1, total, "the blocking stander is not seated");
    }

    @Test
    void crowdKeysStayInTheScene() throws Exception {
        assertMessage(validate(crowdScene("{\"t\":10,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,61],\"style\":\"goal\"}")),
                "centre of attention is beyond the scene radius 60");
        assertMessage(validate(crowdScene("{\"t\":300,\"kind\":\"effect\",\"id\":\"crowd\",\"at\":[0,0,0],\"duration\":200,\"rows\":"
                + ROW + "}")), "duration: 200 must be 0");
        assertMessage(validate(crowdScene("{\"t\":0,\"kind\":\"effect\",\"id\":\"crowd\",\"at\":[0,0,0],\"rows\":[[0,400,20,5,400,20]]}")),
                "y must be -64 to 320");
        assertMessage(validate(crowdScene("{\"t\":0,\"kind\":\"effect\",\"id\":\"crowd\",\"at\":[0,0,0],\"rows\":[[0,3,59.97,5,3,59.97]]}")),
                "reaches beyond the scene radius");
    }

    // ---- the memory Horntail -------------------------------------------------------------------------------------

    private static String horntail(String extra) {
        return "{\"length\":200,\"actors\":[{\"id\":\"h\",\"type\":\"hogwartsmagic:memory_horntail\"" + extra + "}]}";
    }

    @Test
    void horntailAnimNamesChecked() throws Exception {
        // Every attack-state cue, in any case, is fine.
        StringBuilder all = new StringBuilder(",\"anims\":[");
        for (int i = 0; i < SceneNames.HORNTAIL_ANIMS.size(); i++) {
            String n = SceneNames.HORNTAIL_ANIMS.get(i);
            all.append(i > 0 ? "," : "").append("{\"t\":").append(i * 10).append(",\"name\":\"")
                    .append(i % 2 == 0 ? n : n.toLowerCase(java.util.Locale.ROOT)).append("\"}");
        }
        assertEquals(List.of(), validate(horntail(all + "]")));
        // Anything else is not, and the message lists the names.
        List<String> m = validate(horntail(",\"anims\":[{\"t\":0,\"name\":\"FLY\"},{\"t\":10,\"name\":\"SOAR\"}]"));
        assertEquals(1, m.size(), m.toString());
        assertMessage(m, "actors[0].anims[1].name: unknown Horntail cue 'SOAR'");
        assertMessage(m, "AIR_BREATH");
        // A player clip name is no Horntail cue.
        assertMessage(validate(horntail(",\"anims\":[{\"t\":0,\"name\":\"CHEER\"}]")), "unknown Horntail cue 'CHEER'");
    }

    @Test
    void rollAllowedOnHorntail() throws Exception {
        assertEquals(List.of(), validate(horntail(",\"look\":[{\"t\":0,\"yaw\":10,\"pitch\":20,\"roll\":35},"
                + "{\"t\":40,\"yaw\":30,\"pitch\":-10,\"roll\":-50,\"ease\":\"spline\"}]")));
        assertTrue(SceneNames.ROLL_TYPES.contains(SceneNames.HORNTAIL_ACTOR));
    }

    @Test
    void splineAcceptedOnHorntailPath() throws Exception {
        assertEquals(List.of(), validate(horntail(",\"path\":[{\"t\":0,\"pos\":[0,0,0]},"
                + "{\"t\":20,\"pos\":[0,4,10],\"ease\":\"spline\"},{\"t\":40,\"pos\":[10,8,20],\"ease\":\"spline\"},"
                + "{\"t\":60,\"pos\":[20,8,20],\"ease\":\"in_out\"}],\"anims\":[{\"t\":0,\"name\":\"TAKEOFF\"}]")));
    }

    @Test
    void horntailFasterThanTwoBlocksATickRejected() throws Exception {
        // MemoryActors reads a step of 2 blocks or more as a cut, as for the balls.
        assertMessage(validate(horntail(",\"path\":[{\"t\":0,\"pos\":[0,10,0]},"
                + "{\"t\":10,\"pos\":[0,10,30],\"ease\":\"linear\"}]")), "memory_horntail 'h' moves 3.0 blocks in a tick");
    }

    // ---- the Horntail's fire -------------------------------------------------------------------------------------

    /** A ground breath: the BREATH cue at 20 opens her jaw at full fire from 26 (its 6-tick fade) to 66. */
    private static final String BREATH_CUES = ",\"anims\":[{\"t\":0,\"name\":\"BREATH_WINDUP\"},{\"t\":20,\"name\":\"BREATH\"}]";

    @Test
    void breatheOnlyOnHorntail() throws Exception {
        assertEquals(List.of(), validate(horntail(BREATH_CUES + ",\"actions\":[{\"t\":26,\"do\":\"breathe\","
                + "\"target\":[0,0,12]}]")));
        assertMessage(validate(actorScene("player", "\"actions\":[{\"t\":26,\"do\":\"breathe\"}]")),
                "actors[0].actions[0].do: breathe is only for hogwartsmagic:memory_horntail");
        assertMessage(validate(actorScene("hogwartsmagic:basilisk", "\"actions\":[{\"t\":26,\"do\":\"breathe\"}]")),
                "breathe is only for hogwartsmagic:memory_horntail");
        // An actor target must exist, as for a cast.
        assertMessage(validate(horntail(BREATH_CUES + ",\"actions\":[{\"t\":26,\"do\":\"breathe\","
                + "\"target\":\"actor:nobody\"}]")), "unknown actor 'nobody'");
        // Outside the fire window (before the BREATH's fade is over, past its end, or with no breath cue) it is refused.
        assertMessage(validate(horntail(BREATH_CUES + ",\"actions\":[{\"t\":22,\"do\":\"breathe\"}]")),
                "lies outside the fire window of a BREATH or AIR_BREATH cue (her jaw is open at full fire from 26.0 to 66.0)");
        assertMessage(validate(horntail(BREATH_CUES + ",\"actions\":[{\"t\":30,\"do\":\"breathe\"}]")),
                "lies outside the fire window");
        assertMessage(validate(horntail(",\"anims\":[{\"t\":0,\"name\":\"ROAR\"}],\"actions\":[{\"t\":10,"
                + "\"do\":\"breathe\"}]")), "(none is in force then)");
        // An air breath opens its jaw at tick 12.
        String air = ",\"anims\":[{\"t\":0,\"name\":\"FLY\"},{\"t\":40,\"name\":\"AIR_BREATH\"}]";
        assertEquals(List.of(), validate(horntail(air + ",\"actions\":[{\"t\":52,\"do\":\"breathe\"}]")));
        assertMessage(validate(horntail(air + ",\"actions\":[{\"t\":46,\"do\":\"breathe\"}]")), "from 52.0 to 92.0");
    }

    @Test
    void breatheActorTargetIsAPlayer() throws Exception {
        String boy = ",{\"id\":\"boy\",\"type\":\"player\",\"path\":[{\"t\":0,\"pos\":[0,0,12]}]}";
        String sheep = ",{\"id\":\"sheep\",\"type\":\"minecraft:sheep\",\"path\":[{\"t\":0,\"pos\":[0,0,12]}]}";
        String at = BREATH_CUES + ",\"actions\":[{\"t\":26,\"do\":\"breathe\",\"target\":\"actor:%s\"}]}";
        String head = "{\"length\":200,\"actors\":[{\"id\":\"h\",\"type\":\"hogwartsmagic:memory_horntail\"";
        assertEquals(List.of(), validate(head + String.format(at, "boy") + boy + "]}"));
        assertMessage(validate(head + String.format(at, "sheep") + sheep + "]}"),
                "actors[0].actions[0].target: a breathe actor target must be a player actor ('sheep' is minecraft:sheep)");
    }

    @Test
    void breatheDurationRange() throws Exception {
        for (String d : new String[]{"10", "25", "\"1s\""}) {
            assertEquals(List.of(), validate(horntail(BREATH_CUES + ",\"actions\":[{\"t\":26,\"do\":\"breathe\","
                    + "\"duration\":" + d + "}]")), d);
        }
        assertMessage(validate(horntail(BREATH_CUES + ",\"actions\":[{\"t\":26,\"do\":\"breathe\",\"duration\":9}]")),
                "actors[0].actions[0].duration: 9.0 is out of range (10 to 80 ticks)");
        assertMessage(validate(horntail(",\"anims\":[{\"t\":0,\"name\":\"FLY\"},{\"t\":10,\"name\":\"AIR_BREATH\"},"
                + "{\"t\":50,\"name\":\"AIR_BREATH\"},{\"t\":90,\"name\":\"AIR_BREATH\"}],"
                + "\"actions\":[{\"t\":22,\"do\":\"breathe\",\"duration\":81}]")), "out of range");
        // A breathe longer than its window is refused even inside the range.
        assertMessage(validate(horntail(BREATH_CUES + ",\"actions\":[{\"t\":26,\"do\":\"breathe\",\"duration\":41}]")),
                "for 41.0 ticks lies outside the fire window");
    }

    @Test
    void durationKeyOnlyOnBreathe() throws Exception {
        assertMessage(validate(actorScene("player", "\"actions\":[{\"t\":5,\"do\":\"swing\",\"duration\":20}]")),
                "actors[0].actions[0].duration: duration is only for breathe");
        assertMessage(validate(horntail(BREATH_CUES + ",\"actions\":[{\"t\":26,\"do\":\"hurt\",\"duration\":20}]")),
                "duration is only for breathe");
        // Any other unknown key is still a parse error.
        assertThrows(SceneException.class, () -> validate(horntail(BREATH_CUES + ",\"actions\":[{\"t\":26,"
                + "\"do\":\"breathe\",\"length\":20}]")));
    }
}
