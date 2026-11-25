package com.hogwartsmagic.cosy.client.card;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Frames of a legendary card's exit clip. Follows {@code PortraitVideo}: a frame texture loads the first time it is
 * needed (and a few ahead, so the frame about to show is already uploaded), and every loaded frame of a clip is
 * released again 30 seconds after the clip was last drawn. The texture side is behind {@link Loader} so a test can
 * drive it with a fake clock and count loads and releases.
 */
public final class CardVideo {
    public static final long RELEASE_AFTER_NANOS = 30_000_000_000L;
    public static final int PREFETCH = 3;

    /** Uploads and frees one frame texture. */
    public interface Loader {
        void load(ResourceLocation frame);

        void release(ResourceLocation frame);
    }

    private static final class Loaded {
        final String key;
        final boolean[] frames;
        long lastUsedNanos;

        Loaded(String key, int count) {
            this.key = key;
            this.frames = new boolean[count];
        }
    }

    private static final Map<String, Loaded> CLIPS = new HashMap<>();
    private static Loader loader = new MinecraftLoader();

    private CardVideo() {
    }

    /** Test hook: swap the texture side. */
    static void setLoader(Loader replacement) {
        loader = replacement;
    }

    static void resetLoader() {
        loader = new MinecraftLoader();
    }

    /** The texture to draw for frame {@code index} of the card's clip; makes sure it and the next few are loaded. */
    public static ResourceLocation use(String key, int frameCount, int index, long nowNanos) {
        int count = Math.max(1, frameCount);
        Loaded loaded = CLIPS.computeIfAbsent(key, k -> new Loaded(k, count));
        loaded.lastUsedNanos = nowNanos;
        int clamped = Math.max(0, Math.min(index, loaded.frames.length - 1));
        int last = Math.min(loaded.frames.length - 1, clamped + PREFETCH);
        for (int i = clamped; i <= last; i++) {
            if (!loaded.frames[i]) {
                loaded.frames[i] = true;
                ResourceLocation frame = CardAssets.clipFrame(key, i);
                if (CardAssets.has(frame)) { // a missing frame must not make the manager log it every frame
                    loader.load(frame);
                }
            }
        }
        return CardAssets.clipFrame(key, clamped);
    }

    /** Called about once a second: lets go of clips nobody has drawn for 30 seconds. */
    public static void releaseIdle(long nowNanos) {
        if (CLIPS.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<String, Loaded>> it = CLIPS.entrySet().iterator(); it.hasNext(); ) {
            Loaded loaded = it.next().getValue();
            if (nowNanos - loaded.lastUsedNanos > RELEASE_AFTER_NANOS) {
                release(loaded);
                it.remove();
            }
        }
    }

    public static void releaseAll() {
        for (Loaded loaded : CLIPS.values()) {
            release(loaded);
        }
        CLIPS.clear();
    }

    /** How many clips currently hold uploaded frames (for tests). */
    static int loadedClips() {
        return CLIPS.size();
    }

    private static void release(Loaded loaded) {
        for (int i = 0; i < loaded.frames.length; i++) {
            if (loaded.frames[i]) {
                ResourceLocation frame = CardAssets.clipFrame(loaded.key, i);
                if (CardAssets.has(frame)) {
                    loader.release(frame);
                }
            }
        }
    }

    /** The real thing: the client texture manager. Only touched when a clip actually plays. */
    private static final class MinecraftLoader implements Loader {
        @Override
        public void load(ResourceLocation frame) {
            net.minecraft.client.Minecraft.getInstance().getTextureManager().getTexture(frame);
        }

        @Override
        public void release(ResourceLocation frame) {
            net.minecraft.client.Minecraft.getInstance().getTextureManager().release(frame);
        }
    }
}
