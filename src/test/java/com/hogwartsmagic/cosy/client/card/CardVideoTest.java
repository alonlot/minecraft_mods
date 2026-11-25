package com.hogwartsmagic.cosy.client.card;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exit-clip frames load lazily and are released 30 seconds after their last use (a fake clock drives it). */
class CardVideoTest {
    private static final long SECOND = 1_000_000_000L;

    private final List<String> loaded = new ArrayList<>();
    private final List<String> released = new ArrayList<>();

    private static CardAssets.Source everything(boolean exists) {
        return new CardAssets.Source() {
            @Override
            public boolean exists(ResourceLocation l) {
                return exists;
            }

            @Override
            public String readText(ResourceLocation l) {
                return null;
            }
        };
    }

    @BeforeEach
    void fakeEverything() {
        CardAssets.setSource(everything(true));
        CardVideo.setLoader(new CardVideo.Loader() {
            @Override
            public void load(ResourceLocation frame) {
                loaded.add(frame.getPath());
            }

            @Override
            public void release(ResourceLocation frame) {
                released.add(frame.getPath());
            }
        });
    }

    @AfterEach
    void restore() {
        CardVideo.releaseAll();
        CardVideo.resetLoader();
        CardAssets.resetSource();
    }

    @Test
    void loadsLazilyWithAPrefetch() {
        assertTrue(loaded.isEmpty(), "nothing loads before a clip plays");
        CardVideo.use("merlin", 96, 0, 0);
        assertEquals(1 + CardVideo.PREFETCH, loaded.size(), "frame 0 and three ahead");
        CardVideo.use("merlin", 96, 1, SECOND);
        assertEquals(2 + CardVideo.PREFETCH, loaded.size(), "only the new frame at the edge loads");
        CardVideo.use("merlin", 96, 1, 2 * SECOND);
        assertEquals(2 + CardVideo.PREFETCH, loaded.size(), "nothing loads twice");
    }

    @Test
    void noPrefetchPastTheLastFrame() {
        CardVideo.use("merlin", 5, 4, 0);
        assertEquals(1, loaded.size());
    }

    @Test
    void releasedThirtySecondsAfterLastUse() {
        CardVideo.use("merlin", 96, 0, 0);
        int n = loaded.size();
        CardVideo.releaseIdle(29 * SECOND);
        assertTrue(released.isEmpty(), "still warm at 29 s");
        CardVideo.releaseIdle(31 * SECOND);
        assertEquals(n, released.size(), "every loaded frame is released after 30 s");
        assertEquals(0, CardVideo.loadedClips());
    }

    @Test
    void useRefreshesTheClock() {
        CardVideo.use("merlin", 96, 0, 0);
        CardVideo.use("merlin", 96, 2, 25 * SECOND);
        CardVideo.releaseIdle(40 * SECOND);
        assertTrue(released.isEmpty(), "used at 25 s, so not idle at 40 s");
        assertEquals(1, CardVideo.loadedClips());
        CardVideo.releaseIdle(56 * SECOND);
        assertTrue(!released.isEmpty(), "idle for 31 s, released");
        assertEquals(0, CardVideo.loadedClips());
    }

    @Test
    void aMissingFrameIsNeverLoaded() {
        CardAssets.setSource(everything(false));
        CardVideo.use("ghost", 10, 0, 0);
        assertTrue(loaded.isEmpty(), "a missing frame must not reach the texture manager");
    }
}
