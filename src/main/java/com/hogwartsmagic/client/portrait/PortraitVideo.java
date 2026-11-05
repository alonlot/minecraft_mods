package com.hogwartsmagic.client.portrait;

import com.hogwartsmagic.portrait.PortraitKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Video frames of the Flow clips (of every portrait kind). A frame texture is loaded the first time it is needed (and a few ahead, so the
 * frame about to show is already uploaded), and every frame of a clip is released again 30 seconds after the clip
 * was last shown.
 */
final class PortraitVideo {
    private static final long RELEASE_AFTER_NANOS = 30_000_000_000L;
    private static final int PREFETCH = 3;

    private static final class Loaded {
        final PortraitKind kind;
        final String clip;
        final boolean[] frames;
        long lastUsedNanos;

        Loaded(PortraitKind kind, String clip, int count) {
            this.kind = kind;
            this.clip = clip;
            this.frames = new boolean[count];
        }
    }

    private static final Map<String, Loaded> CLIPS = new HashMap<>();

    private PortraitVideo() {
    }

    /** The texture to draw for frame {@code index} of the clip; makes sure it and the next few are loaded. */
    static ResourceLocation use(PortraitKind kind, String clip, PortraitAssets.Clip data, int index, long nowNanos) {
        Loaded loaded = CLIPS.computeIfAbsent(kind.id() + "/" + clip, c -> new Loaded(kind, clip, data.frames()));
        loaded.lastUsedNanos = nowNanos;
        int last = Math.min(data.frames() - 1, index + PREFETCH);
        for (int i = Math.max(0, index); i <= last && i < loaded.frames.length; i++) {
            load(loaded, i);
        }
        return PortraitAssets.frame(kind, clip, Math.min(index, data.frames() - 1));
    }

    /** Warm-up when a video line starts, so frame 0 does not hitch the first render. */
    static void prime(PortraitKind kind, String clip, PortraitAssets.Clip data, long nowNanos) {
        use(kind, clip, data, 0, nowNanos);
    }

    private static void load(Loaded loaded, int index) {
        if (loaded.frames[index]) {
            return;
        }
        loaded.frames[index] = true;
        ResourceLocation location = PortraitAssets.frame(loaded.kind, loaded.clip, index);
        if (!PortraitAssets.exists(location)) {
            return; // a missing frame draws as the missing texture; do not make the manager log it again and again
        }
        // upload now; PortraitRenderTypes.SMOOTH_SOLID draws it with linear filtering
        Minecraft.getInstance().getTextureManager().getTexture(location);
    }

    /** Called about once a second: lets go of clips nobody has shown for 30 seconds. */
    static void releaseIdle(long nowNanos) {
        if (CLIPS.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<String, Loaded>> it = CLIPS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Loaded> entry = it.next();
            if (nowNanos - entry.getValue().lastUsedNanos > RELEASE_AFTER_NANOS) {
                release(entry.getValue());
                it.remove();
            }
        }
    }

    static void releaseAll() {
        for (Map.Entry<String, Loaded> entry : CLIPS.entrySet()) {
            release(entry.getValue());
        }
        CLIPS.clear();
    }

    private static void release(Loaded loaded) {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        for (int i = 0; i < loaded.frames.length; i++) {
            if (loaded.frames[i]) {
                textures.release(PortraitAssets.frame(loaded.kind, loaded.clip, i));
            }
        }
    }
}
