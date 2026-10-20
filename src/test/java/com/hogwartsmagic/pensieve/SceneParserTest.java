package com.hogwartsmagic.pensieve;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneException;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneParserTest {
    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void parsesMinimalScene() throws Exception {
        SceneDef s = SceneParser.parse("t", json("{\"length\":100,\"set\":{},\"actors\":[]}"));
        assertEquals(100, s.length());
        assertEquals(40, s.radius());
        assertEquals(60, s.set().buildTicks());
        assertEquals(18000L, s.set().timeOfDay());
        assertEquals("t", s.title());
    }

    @Test
    void secondsStringsBecomeTicks() throws Exception {
        SceneDef s = SceneParser.parse("t", json("{\"length\":\"2.5s\",\"set\":{},\"actors\":[]}"));
        assertEquals(50, s.length());
    }

    @Test
    void actorTracksAndCues() throws Exception {
        SceneDef s = SceneParser.parse("t", json("""
                {"length":100,"set":{},"actors":[
                  {"id":"a","type":"player","skin":{"username":"Notch","slim":true},"name":"Al",
                   "items":{"mainhand":"hogwartsmagic:student_wand"},
                   "path":[{"t":0,"pos":[0,64,0]},{"t":20,"pos":[10,64,0],"ease":"linear"}],
                   "look":[{"t":0,"yaw":90,"pitch":10}],
                   "limbs":{"right_arm":{"rot":[{"t":0,"v":[0,0,0]},{"t":10,"v":[-90,0,0]}]}},
                   "poses":[{"t":5,"name":"wand_raised"}],
                   "actions":[{"t":10,"do":"cast","spell":"stupefy","target":"actor:b"}]}]}"""));
        var a = s.actors().get(0);
        assertEquals("player", a.type());
        assertEquals("Notch", a.skin().username());
        assertEquals(true, a.skin().slim());
        assertEquals("hogwartsmagic:student_wand", a.items().get("mainhand"));
        assertArrayEquals(new float[]{10, 64, 0}, a.path().sample(20), 1e-5f);
        assertArrayEquals(new float[]{90, 10}, a.look().sample(0), 1e-5f);
        assertArrayEquals(new float[]{-90, 0, 0}, a.limbs().get("right_arm").rot().sample(10), 1e-5f);
        assertEquals("wand_raised", a.poses().get(0).name());
        assertEquals(5f, a.poses().get(0).t());
        assertEquals("cast", a.actions().get(0).action());
        assertEquals("stupefy", a.actions().get(0).spell());
        assertEquals("b", a.actions().get(0).target().actor());
    }

    @Test
    void musicAndVoiceCuesParse() throws Exception {
        SceneDef s = SceneParser.parse("t", json("""
                {"length":100,"events":[
                  {"t":"1s","kind":"music","id":"a:b","volume":0.5},
                  {"t":2,"kind":"voice","id":"a:c"}]}"""));
        assertEquals("music", s.events().get(0).kind());
        assertEquals(0.5f, s.events().get(0).volume());
        assertEquals(20f, s.events().get(0).t());
        assertEquals("voice", s.events().get(1).kind());
        assertEquals(1f, s.events().get(1).volume());
        assertEquals("a:c", s.events().get(1).id());
    }

    @Test
    void parsesEquip() throws Exception {
        SceneDef s = SceneParser.parse("t", json("""
                {"length":100,"actors":[{"id":"a","type":"player","actions":[
                  {"t":10,"do":"equip","slot":"head","item":"hogwartsmagic:sorting_hat"}]}]}"""));
        SceneDef.ActionCue c = s.actors().get(0).actions().get(0);
        assertEquals("equip", c.action());
        assertEquals("head", c.slot());
        assertEquals("hogwartsmagic:sorting_hat", c.item());
    }

    @Test
    void parsesWordSize() throws Exception {
        SceneDef s = SceneParser.parse("t", json("""
                {"length":100,"words":[
                  {"t":1,"text":"big","narration":true,"size":3},
                  {"t":2,"text":"plain","narration":true}]}"""));
        assertEquals(3f, s.words().get(0).size());
        assertEquals(1f, s.words().get(1).size());
    }

    @Test
    void parsesSfx() throws Exception {
        SceneDef s = SceneParser.parse("t", json("""
                {"length":100,"events":[{"t":"2s","kind":"sfx","id":"a:b","volume":2}]}"""));
        assertEquals("sfx", s.events().get(0).kind());
        assertEquals(2f, s.events().get(0).volume());
        assertEquals(40f, s.events().get(0).t());
        SceneException at = assertThrows(SceneException.class, () -> SceneParser.parse("t", json(
                "{\"length\":10,\"events\":[{\"t\":0,\"kind\":\"sfx\",\"id\":\"a:b\",\"at\":[0,0,0]}]}")));
        assertTrue(at.getMessage().contains("events[0].at"), at.getMessage());
    }

    @Test
    void musicAndVoiceCuesRejectBadKeysAndVolume() {
        for (String kind : new String[]{"music", "voice"}) {
            SceneException unknown = assertThrows(SceneException.class, () -> SceneParser.parse("t", json(
                    "{\"length\":10,\"events\":[{\"t\":0,\"kind\":\"" + kind + "\",\"id\":\"a:b\",\"pitch\":0.5}]}")));
            assertTrue(unknown.getMessage().contains("events[0].pitch"), unknown.getMessage());
            SceneException at = assertThrows(SceneException.class, () -> SceneParser.parse("t", json(
                    "{\"length\":10,\"events\":[{\"t\":0,\"kind\":\"" + kind + "\",\"id\":\"a:b\",\"at\":[0,0,0]}]}")));
            assertTrue(at.getMessage().contains("events[0].at"), at.getMessage());
            SceneException vol = assertThrows(SceneException.class, () -> SceneParser.parse("t", json(
                    "{\"length\":10,\"events\":[{\"t\":0,\"kind\":\"" + kind + "\",\"id\":\"a:b\",\"volume\":\"loud\"}]}")));
            assertTrue(vol.getMessage().contains("events[0].volume"), vol.getMessage());
        }
    }

    @Test
    void schematicsAndFogFarParse() throws Exception {
        SceneDef s = SceneParser.parse("t", json("""
                {"length":100,"fog_far":180,"set":{"schematics":[
                  {"id":"mini_hogwarts","at":[-50,3,-150],"rotation":270},
                  {"id":"mini_hogwarts","at":[0,0,0]}]}}"""));
        assertEquals(180, s.fogFar());
        assertEquals(2, s.set().schematics().size());
        assertEquals("mini_hogwarts", s.set().schematics().get(0).id());
        assertArrayEquals(new int[]{-50, 3, -150}, s.set().schematics().get(0).at());
        assertEquals(270, s.set().schematics().get(0).rotation());
        assertEquals(0, s.set().schematics().get(1).rotation());
        assertEquals(0, SceneParser.parse("t", json("{\"length\":1}")).fogFar());
        SceneException e = assertThrows(SceneException.class, () -> SceneParser.parse("t", json(
                "{\"length\":1,\"set\":{\"schematics\":[{\"id\":\"mini_hogwarts\",\"at\":[0,0,0],\"rot\":90}]}}")));
        assertEquals("set.schematics[0].rot", e.path());
    }

    @Test
    void unknownKeyIsNamedError() {
        SceneException e = assertThrows(SceneException.class,
                () -> SceneParser.parse("t", json("{\"length\":1,\"lenght\":2}")));
        assertEquals("lenght", e.path());
    }

    @Test
    void badKeyframeNamesItsPath() {
        SceneException e = assertThrows(SceneException.class, () -> SceneParser.parse("t", json("""
                {"length":1,"set":{},"actors":[{"id":"a","type":"player",
                 "path":[{"t":0,"pos":[0,0,0]},{"t":5}]}]}""")));
        assertEquals("actors[0].path[1].pos", e.path());
    }

    @Test
    void limbModeDefaultsToAddAndAcceptsSet() throws Exception {
        SceneDef s = SceneParser.parse("t", json("""
                {"length":100,"set":{},"actors":[{"id":"a","type":"player","limbs":{
                  "head":{"rot":[{"t":0,"v":[0,0,0]}]},
                  "right_arm":{"mode":"set","rot":[{"t":0,"v":[-90,0,0]}]}}}]}"""));
        var limbs = s.actors().get(0).limbs();
        assertEquals(false, limbs.get("head").set());
        assertEquals(true, limbs.get("right_arm").set());
    }

    @Test
    void limbModeMustBeAddOrSet() {
        assertThrows(SceneException.class, () -> SceneParser.parse("t", json("""
                {"length":100,"set":{},"actors":[{"id":"a","type":"player","limbs":{
                  "head":{"mode":"replace","rot":[{"t":0,"v":[0,0,0]}]}}}]}""")));
    }

    private static SceneDef.ActorDef actor(String actorJson) throws Exception {
        return SceneParser.parse("t", json("{\"length\":20,\"actors\":[" + actorJson + "]}")).actors().get(0);
    }

    @Test
    void parsesNextScene() throws Exception {
        assertEquals("sorting_hut", SceneParser.parse("t", json("{\"length\":20,\"next\":\"sorting_hut\"}")).next());
    }

    @Test
    void nextDefaultsToNull() throws Exception {
        assertNull(SceneParser.parse("t", json("{\"length\":20}")).next());
    }

    @Test
    void parsesActorScale() throws Exception {
        assertEquals(0.75f, actor("{\"id\":\"a\",\"type\":\"player\",\"scale\":0.75}").scale(), 1e-6);
    }

    @Test
    void scaleDefaultsToOne() throws Exception {
        assertEquals(1f, actor("{\"id\":\"a\",\"type\":\"player\"}").scale(), 1e-6);
    }

    @Test
    void parsesViewerSkin() throws Exception {
        assertTrue(actor("{\"id\":\"a\",\"type\":\"player\",\"skin\":{\"viewer\":true}}").skin().viewer());
    }

    @Test
    void parsesEffectYaw() throws Exception {
        SceneDef s = SceneParser.parse("t", json("""
                {"length":100,"set":{},"actors":[],"events":[
                  {"t":5,"kind":"effect","id":"letter_flood","at":[1,2,3],"yaw":-90,"count":220,"spread":[3,2.5,3],"duration":110},
                  {"t":6,"kind":"effect","id":"lightning","at":[0,0,0]}]}"""));
        SceneDef.EventDef e = s.events().get(0);
        assertEquals(-90f, e.yaw());
        assertEquals(220, e.count());
        assertArrayEquals(new float[] {3, 2.5f, 3}, e.spread());
        assertTrue(Float.isNaN(s.events().get(1).yaw()));
    }

    @Test
    void intensityParsesOnEffectsAndDefaultsToNaN() throws Exception {
        SceneDef s = SceneParser.parse("t", json("""
                {"length":100,"events":[
                  {"t":1,"kind":"effect","id":"light_rays","at":[0,0,0],"yaw":0,"spread":[2,5,12],"intensity":0.45},
                  {"t":2,"kind":"effect","id":"light_rays","at":[0,0,0],"yaw":0,"spread":[2,5,12]}]}"""));
        assertEquals(0.45f, s.events().get(0).intensity());
        assertTrue(Float.isNaN(s.events().get(1).intensity()));
    }
}
