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
}
