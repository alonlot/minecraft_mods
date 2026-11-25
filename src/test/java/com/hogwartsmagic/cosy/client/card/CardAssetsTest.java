package com.hogwartsmagic.cosy.client.card;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A card with no art folder must draw a placeholder, never the purple missing texture. */
class CardAssetsTest {
    /** A pretend resource pack: some paths, some json text. */
    private static final class FakeSource implements CardAssets.Source {
        final Set<String> paths;
        final Map<String, String> text = new HashMap<>();

        FakeSource(String... paths) {
            this.paths = Set.of(paths);
        }

        @Override
        public boolean exists(ResourceLocation l) {
            return this.paths.contains(l.getPath());
        }

        @Override
        public String readText(ResourceLocation l) {
            return this.text.get(l.getPath());
        }
    }

    @AfterEach
    void restore() {
        CardAssets.resetSource();
    }

    @Test
    void nonexistentCardGetsAPlaceholder() {
        CardAssets.setSource(new FakeSource());
        CardAssets.Assets a = CardAssets.forKey("nonexistent");
        assertTrue(a.placeholder());
        assertEquals("nonexistent", a.key());
        assertTrue(a.base().getPath().contains("_placeholder"), a.base().getPath());
        assertTrue(a.empty().getPath().contains("_placeholder"));
        assertTrue(a.figure().getPath().contains("_placeholder"));
        assertTrue(a.meta().eyes().valid());
        assertEquals(0, a.clipFrames());
    }

    @Test
    void aCardMissingItsFigureIsStillAPlaceholder() {
        CardAssets.setSource(new FakeSource("textures/card/merlin/base.png", "textures/card/merlin/empty.png"));
        assertTrue(CardAssets.forKey("merlin").placeholder());
    }

    @Test
    void aCompleteCardUsesItsOwnFilesAndJson() {
        FakeSource s = new FakeSource("textures/card/merlin/base.png", "textures/card/merlin/empty.png",
                "textures/card/merlin/figure.png", "cards/merlin.json", "textures/card/merlin/clips/exit/0000.png");
        s.text.put("cards/merlin.json",
                "{\"eyes\":[100,120,20,8],\"face\":[101,130,40,50],\"exit_clip\":{\"frames\":96,\"fps\":12}}");
        CardAssets.setSource(s);
        CardAssets.Assets a = CardAssets.forKey("merlin");
        assertFalse(a.placeholder());
        assertEquals("textures/card/merlin/base.png", a.base().getPath());
        assertEquals(100.0F, a.meta().eyes().cx());
        assertEquals(96, a.clipFrames());
    }

    @Test
    void aClipWithNoFirstFrameIsDropped() {
        FakeSource s = new FakeSource("textures/card/merlin/base.png", "textures/card/merlin/empty.png",
                "textures/card/merlin/figure.png", "cards/merlin.json");
        s.text.put("cards/merlin.json", "{\"exit_clip\":{\"frames\":96,\"fps\":12}}");
        CardAssets.setSource(s);
        assertEquals(0, CardAssets.forKey("merlin").clipFrames());
    }

    @Test
    void metaParsingNeverThrows() {
        assertEquals(CardAssets.DEFAULT_META, CardAssets.parseMeta("not json"));
        assertEquals(CardAssets.DEFAULT_META, CardAssets.parseMeta("[1,2]"));
        assertEquals(CardAssets.DEFAULT_META.eyes(), CardAssets.parseMeta("{\"eyes\":[1,2]}").eyes());
        assertNull(CardAssets.parseMeta("{\"exit_clip\":null}").exit());
        assertNotNull(CardAssets.parseMeta("{\"exit_clip\":{\"frames\":3}}").exit());
        assertNull(CardAssets.parseMeta("{\"exit_clip\":{\"frames\":0}}").exit());
    }

    @Test
    void lookupsAreCachedSoNothingIsAskedTwice() {
        int[] asks = {0};
        CardAssets.setSource(new CardAssets.Source() {
            @Override
            public boolean exists(ResourceLocation l) {
                asks[0]++;
                return false;
            }

            @Override
            public String readText(ResourceLocation l) {
                return null;
            }
        });
        CardAssets.forKey("x");
        int first = asks[0];
        CardAssets.forKey("x");
        assertEquals(first, asks[0]);
    }
}
