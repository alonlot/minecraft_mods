package com.hogwartsmagic.assignment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssignmentModelTest {
    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void parsesTargetsCountAndParams() {
        AssignmentDef def = AssignmentParser.parse("lumos", json("""
                {"teacher": "flitwick", "objectives": [
                  {"type": "place", "target": ["minecraft:torch", "minecraft:wall_torch"], "count": 12, "no_sky": true},
                  {"type": "stand", "count": 60, "max_y": 0}
                ]}"""));
        assertEquals("lumos", def.spellId());
        assertEquals("flitwick", def.teacher());
        assertFalse(def.dark());
        assertEquals(2, def.objectives().size());
        ObjectiveDef place = def.objectives().get(0);
        assertEquals(ObjectiveType.PLACE, place.type());
        assertEquals(List.of("minecraft:torch", "minecraft:wall_torch"), place.targets());
        assertEquals(12, place.count());
        assertTrue(place.flag("no_sky"));
        assertEquals(0, def.objectives().get(1).intParam("max_y", 99));
        assertEquals("assignment.hogwartsmagic.lumos.1", def.objectiveKey(1));
        assertEquals("assignment.hogwartsmagic.teacher.flitwick", def.teacherKey());
    }

    @Test
    void singleStringTargetAndDefaultCount() {
        AssignmentDef def = AssignmentParser.parse("x", json("""
                {"teacher": "prince", "dark": true, "objectives": [{"type": "know_spell", "target": "levicorpus"}]}"""));
        assertTrue(def.dark());
        assertEquals(List.of("levicorpus"), def.objectives().get(0).targets());
        assertEquals(1, def.objectives().get(0).count());
    }

    @Test
    void rejectsBadFiles() {
        assertThrows(IllegalArgumentException.class, () -> AssignmentParser.parse("x", json("{\"objectives\": [{\"type\": \"fish\"}]}")));
        assertThrows(IllegalArgumentException.class, () -> AssignmentParser.parse("x", json("{\"teacher\": \"a\", \"objectives\": []}")));
        assertThrows(IllegalArgumentException.class, () -> AssignmentParser.parse("x", json("{\"teacher\": \"a\", \"objectives\": [{\"type\": \"juggle\"}]}")));
        assertThrows(IllegalArgumentException.class, () -> AssignmentParser.parse("x", json("{\"teacher\": \"a\", \"objectives\": [{\"type\": \"fish\", \"count\": 0}]}")));
        assertThrows(IllegalArgumentException.class, () -> AssignmentParser.parse("x", json("{\"teacher\": \"a\", \"objectives\": [{\"type\": \"cast\"}]}")));
        assertThrows(IllegalArgumentException.class, () -> AssignmentParser.parse("x", json("{\"teacher\": \"a\", \"objectives\": [{\"type\": \"fish\", \"x\": [1]}]}")));
    }

    @Test
    void typeIdsAreCaseInsensitiveAndUnknownIsNull() {
        assertEquals(ObjectiveType.MOB_KILLS_MOB, ObjectiveType.byId("MOB_kills_mob"));
        assertNull(ObjectiveType.byId("nope"));
        assertNull(ObjectiveType.byId(null));
        assertTrue(ObjectiveType.DELIVER.isLive());
        assertFalse(ObjectiveType.KILL.isLive());
    }

    @Test
    void matchingIdsTagsAndAny() {
        ObjectiveDef any = new ObjectiveDef(ObjectiveType.FISH, List.of(), 1, Map.of());
        assertTrue(any.matches("minecraft:cod"));
        ObjectiveDef mixed = new ObjectiveDef(ObjectiveType.BREAK, List.of("minecraft:ice", "#minecraft:logs"), 1, Map.of());
        assertTrue(mixed.matches("minecraft:ice"));
        assertFalse(mixed.matches("minecraft:oak_log"));
        assertTrue(mixed.matches("minecraft:oak_log", tag -> tag.equals("minecraft:logs")));
        assertFalse(mixed.matches("minecraft:stone", tag -> false));
    }

    @Test
    void addClampsAndCompletionNeedsEveryLine() {
        assertEquals(5, AssignmentRules.add(3, 9, 5));
        assertEquals(3, AssignmentRules.add(3, -2, 5));
        assertEquals(5, AssignmentRules.add(Integer.MAX_VALUE, 1, 5));
        List<ObjectiveDef> objectives = List.of(
                new ObjectiveDef(ObjectiveType.FISH, List.of(), 2, Map.of()),
                new ObjectiveDef(ObjectiveType.KNOW_SPELL, List.of("lumos"), 1, Map.of()));
        assertFalse(AssignmentRules.isComplete(objectives, new int[]{2, 0}, new boolean[]{false, false}));
        assertFalse(AssignmentRules.isComplete(objectives, new int[]{1, 0}, new boolean[]{false, true}));
        assertTrue(AssignmentRules.isComplete(objectives, new int[]{2, 0}, new boolean[]{false, true}));
        assertFalse(AssignmentRules.isComplete(objectives, new int[]{2}, null));
    }

    @Test
    void fitResizesAndClamps() {
        List<ObjectiveDef> objectives = List.of(
                new ObjectiveDef(ObjectiveType.FISH, List.of(), 2, Map.of()),
                new ObjectiveDef(ObjectiveType.BREED, List.of(), 4, Map.of()));
        assertArrayEquals(new int[]{2, 0}, AssignmentRules.fit(new int[]{7}, objectives));
        assertArrayEquals(new int[]{0, 4}, AssignmentRules.fit(new int[]{-1, 9, 3}, objectives));
        assertArrayEquals(new int[]{0, 0}, AssignmentRules.fit(null, objectives));
    }

    @Test
    void distinctSets() {
        String s = AssignmentRules.addDistinct("", "minecraft:healing");
        s = AssignmentRules.addDistinct(s, "minecraft:swiftness");
        assertEquals(s, AssignmentRules.addDistinct(s, "minecraft:healing"));
        assertEquals(2, AssignmentRules.distinctCount(s));
        assertEquals(0, AssignmentRules.distinctCount(""));
    }

    @Test
    void weapons() {
        assertTrue(AssignmentRules.isWeaponPath("iron_sword"));
        assertTrue(AssignmentRules.isWeaponPath("bow"));
        assertTrue(AssignmentRules.isWeaponPath("stone_axe"));
        assertFalse(AssignmentRules.isWeaponPath("pickaxe_of_x"));
        assertFalse(AssignmentRules.isWeaponPath("stick"));
    }
}
