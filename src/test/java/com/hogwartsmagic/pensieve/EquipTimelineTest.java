package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.EquipTimeline;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EquipTimelineTest {
    private static SceneDef.ActionCue equip(float t, String slot, String item) {
        return new SceneDef.ActionCue(t, "equip", null, null, item, slot);
    }

    private static SceneDef.ActionCue hold(float t, String item) {
        return new SceneDef.ActionCue(t, "hold", null, null, item, null);
    }

    @Test
    void baseBeforeAnyEquip() {
        List<SceneDef.ActionCue> a = List.of(equip(10, "head", "minecraft:iron_helmet"));
        assertEquals("minecraft:stick", EquipTimeline.itemAt(a, Map.of("head", "minecraft:stick"), "head", 9.9f));
        assertNull(EquipTimeline.itemAt(a, Map.of(), "head", 0));
    }

    @Test
    void lastEquipWins() {
        List<SceneDef.ActionCue> a = List.of(equip(20, "head", "b"), equip(10, "head", "a"), equip(15, "feet", "boots"));
        assertEquals("a", EquipTimeline.itemAt(a, Map.of(), "head", 12));
        assertEquals("b", EquipTimeline.itemAt(a, Map.of(), "head", 20));
        assertEquals("boots", EquipTimeline.itemAt(a, Map.of(), "feet", 99));
    }

    @Test
    void airClears() {
        List<SceneDef.ActionCue> a = List.of(equip(5, "head", "a"), equip(8, "head", "minecraft:air"));
        assertEquals("a", EquipTimeline.itemAt(a, Map.of(), "head", 6));
        assertEquals("minecraft:air", EquipTimeline.itemAt(a, Map.of("head", "x"), "head", 9));
    }

    @Test
    void holdCountsAsMainhandEquip() {
        List<SceneDef.ActionCue> a = List.of(hold(3, "wand"), equip(6, "mainhand", "stick"));
        assertEquals("base", EquipTimeline.itemAt(a, Map.of("mainhand", "base"), "mainhand", 2));
        assertEquals("wand", EquipTimeline.itemAt(a, Map.of(), "mainhand", 4));
        assertEquals("stick", EquipTimeline.itemAt(a, Map.of(), "mainhand", 7));
        assertNull(EquipTimeline.itemAt(a, Map.of(), "offhand", 7));
    }

    @Test
    void useItemWithItemCountsAsMainhand() {
        List<SceneDef.ActionCue> a = List.of(
                new SceneDef.ActionCue(4, "use_item", null, null, "wand", null),
                new SceneDef.ActionCue(6, "use_item", null, null, null, null));
        assertEquals("wand", EquipTimeline.itemAt(a, Map.of("mainhand", "base"), "mainhand", 9));
        assertEquals("base", EquipTimeline.itemAt(a, Map.of("mainhand", "base"), "mainhand", 3));
    }

    @Test
    void equalTimeLaterListedWins() {
        List<SceneDef.ActionCue> a = List.of(equip(5, "head", "first"), equip(5, "head", "second"));
        assertEquals("second", EquipTimeline.itemAt(a, Map.of(), "head", 5));
        assertEquals("second", EquipTimeline.itemAt(a, Map.of(), "head", 8));
    }

    @Test
    void equipStateIsAFunctionOfTime() {
        List<SceneDef.ActionCue> a = List.of(equip(10, "head", "hogwartsmagic:sorting_hat"));
        String once = EquipTimeline.itemAt(a, Map.of(), "head", 12);
        String stepped = null;
        for (float t = 0; t <= 12; t += 1) stepped = EquipTimeline.itemAt(a, Map.of(), "head", t);
        assertEquals("hogwartsmagic:sorting_hat", once);
        assertEquals(once, stepped);
    }
}
