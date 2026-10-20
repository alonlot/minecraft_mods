package com.hogwartsmagic.pensieve;

import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.ItemText;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemTextTest {
    private static final String SCENE = """
            {"length":100,"words":[{"t":1,"text":"{viewer_wand}"}],
             "actors":[{"id":"boy","type":"player","items":{"mainhand":"{viewer_wand}","offhand":"minecraft:stick"},
               "actions":[{"t":2,"do":"hold","item":"{viewer_wand}"},
                          {"t":3,"do":"equip","slot":"offhand","item":"{viewer_wand}"}]}]}""";

    @Test
    void viewerWandSubstitution() throws Exception {
        String filled = ItemText.fill(SCENE, Map.of(ItemText.CONTEXT_WAND, "hogwartsmagic:elder_wand"));
        SceneDef def = SceneParser.parse("t", JsonParser.parseString(filled).getAsJsonObject());
        SceneDef.ActorDef boy = def.actors().get(0);
        assertEquals("hogwartsmagic:elder_wand", boy.items().get("mainhand"));
        assertEquals("minecraft:stick", boy.items().get("offhand"));
        assertEquals("hogwartsmagic:elder_wand", boy.actions().get(0).item());
        assertEquals("hogwartsmagic:elder_wand", boy.actions().get(1).item());
        // Only whole item values are items: a word that happens to be the placeholder is left as written.
        assertEquals("{viewer_wand}", def.words().get(0).text());
        // Nothing to fill: the same string back.
        assertEquals("{\"length\":1}", ItemText.fill("{\"length\":1}", Map.of(ItemText.CONTEXT_WAND, "a:b_wand")));
        // An empty context fills the default wand, so the placeholder never reaches a client.
        assertTrue(ItemText.fill(SCENE, Map.of()).contains("\"mainhand\":\"" + ItemText.DEFAULT_WAND + "\""));
        // A context value that is not an item id (here an attempt to break out of the JSON string) is never written.
        String hostile = ItemText.fill(SCENE, Map.of(ItemText.CONTEXT_WAND, "a:b\",\"x\":\"y"));
        assertTrue(hostile.contains("\"mainhand\":\"" + ItemText.DEFAULT_WAND + "\""), hostile);
    }

    @Test
    void viewerWandFallbacks() {
        // The purchase wins.
        assertEquals("hogwartsmagic:holly_wand", ItemText.viewerWand("hogwartsmagic:holly_wand",
                List.of("hogwartsmagic:elder_wand")));
        // No purchase: the first held wand (main hand, then off hand), skipping anything that is not a wand.
        assertEquals("hogwartsmagic:elder_wand", ItemText.viewerWand(null,
                List.of("minecraft:bread", "hogwartsmagic:elder_wand")));
        assertEquals("hogwartsmagic:elder_wand", ItemText.viewerWand("", Arrays.asList(null, "hogwartsmagic:elder_wand")));
        // Nothing held that is a wand: Harry's.
        assertEquals("hogwartsmagic:harry_potter_wand", ItemText.viewerWand(null, List.of("minecraft:air", "minecraft:stick")));
        assertEquals(ItemText.DEFAULT_WAND, ItemText.viewerWand(null, List.of()));
        // A purchase that is an item but not a wand (a sword held for /ollivanders) falls through too.
        assertEquals(ItemText.DEFAULT_WAND, ItemText.viewerWand("minecraft:iron_sword", List.of()));
        assertEquals("hogwartsmagic:elder_wand", ItemText.viewerWand("minecraft:dirt", List.of("hogwartsmagic:elder_wand")));
        // A purchase that is not an item id falls through.
        assertEquals(ItemText.DEFAULT_WAND, ItemText.viewerWand("not an id", List.of()));
        // "_wand" must end the path, not just appear in it.
        assertFalse(ItemText.isWand("hogwartsmagic:wand_box"));
        assertTrue(ItemText.isWand("hogwartsmagic:harry_potter_wand"));
        assertFalse(ItemText.isWand(null));
        // The context the server builds: the resolved wand under its key.
        assertEquals("hogwartsmagic:elder_wand",
                ItemText.resolve(Map.of(), List.of("hogwartsmagic:elder_wand")).get(ItemText.CONTEXT_WAND));
        assertEquals("hogwartsmagic:holly_wand", ItemText.resolve(Map.of(ItemText.CONTEXT_WAND, "hogwartsmagic:holly_wand"),
                List.of("hogwartsmagic:elder_wand")).get(ItemText.CONTEXT_WAND));
    }

    @Test
    void rejectsDifferFromTheViewerWand() {
        List<String> every = List.of("hogwartsmagic:unicorn_core_wand", "hogwartsmagic:dragon_core_wand",
                "hogwartsmagic:phoenix_core_wand", "hogwartsmagic:elder_wand", "hogwartsmagic:harry_potter_wand",
                "hogwartsmagic:holly_wand");
        for (String wand : every) {
            List<String> r = ItemText.rejects(wand);
            assertEquals(3, r.size());
            assertEquals(3, new java.util.HashSet<>(r).size(), "distinct: " + r);
            assertFalse(r.contains(wand), wand + " must not be rejected: " + r);
            for (String id : r) assertTrue(ItemText.isWand(id), id);
            assertEquals(r, ItemText.rejects(wand), "stable");
            Map<String, String> ctx = ItemText.resolve(Map.of(ItemText.CONTEXT_WAND, wand), List.of());
            assertEquals(wand, ctx.get(ItemText.CONTEXT_WAND));
            assertEquals(r, List.of(ctx.get("reject_wand_1"), ctx.get("reject_wand_2"), ctx.get("reject_wand_3")));
        }
        // The wandmaker's sold wands come first.
        assertEquals(List.of("hogwartsmagic:unicorn_core_wand", "hogwartsmagic:phoenix_core_wand", "hogwartsmagic:elder_wand"),
                ItemText.rejects("hogwartsmagic:dragon_core_wand"));
    }

    @Test
    void rejectPlaceholdersFill() {
        String scene = "{\"actors\":[{\"id\":\"a\",\"type\":\"player\",\"items\":{\"mainhand\":\"{reject_wand_2}\"},"
                + "\"actions\":[{\"t\":1,\"do\":\"hold\",\"item\":\"{reject_wand_1}\"},"
                + "{\"t\":2,\"do\":\"hold\",\"item\":\"{reject_wand_3}\"},{\"t\":3,\"do\":\"hold\",\"item\":\"{viewer_wand}\"}]}]}";
        String wand = "hogwartsmagic:dragon_core_wand";
        String out = ItemText.fill(scene, ItemText.resolve(Map.of(ItemText.CONTEXT_WAND, wand), List.of()));
        assertFalse(out.contains("{"+"reject"), out);
        assertTrue(out.contains("\"mainhand\":\"hogwartsmagic:phoenix_core_wand\""), out);
        assertTrue(out.contains("\"item\":\"hogwartsmagic:unicorn_core_wand\""), out);
        assertTrue(out.contains("\"item\":\"hogwartsmagic:elder_wand\""), out);
        // An empty context still never leaves a placeholder, and never rejects the (default) viewer wand.
        String bare = ItemText.fill(scene, Map.of());
        assertFalse(bare.contains("{reject") || bare.contains("{viewer"), bare);
    }
}
