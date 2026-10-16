package com.hogwartsmagic.pensieve;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneException;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
