package com.hogwartsmagic.cosy.client.card;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pre-shrunk (one quarter size) copies of a card's layers, for the album: a 256 x 384 painting sampled down to about
 * 38 px with no mip chain sparkles, so small cards are drawn from 64 x 96 copies (the frames from 80 x 112) built once
 * with an alpha-weighted box filter. Building a card's copies decodes a handful of PNGs, so the album spreads it over
 * frames (at most four cards each) and shows silhouettes until a card is ready. Client only.
 */
final class CardMini {
    private static final int FACTOR = 4;

    private static final Set<String> READY = new HashSet<>();
    private static final Map<ResourceLocation, ResourceLocation> COPIES = new HashMap<>();
    private static boolean framesDone;

    private CardMini() {
    }

    static boolean ready(String key) {
        return READY.contains(key);
    }

    /** The copy for a texture, or the texture itself when no copy was made. */
    static ResourceLocation resolve(ResourceLocation full) {
        ResourceLocation copy = COPIES.get(full);
        return copy == null ? full : copy;
    }

    /** Builds the small copies of one card's layers (and, once, of the three frames). */
    static void prepare(String key) {
        if (READY.contains(key)) {
            return;
        }
        CardAssets.Assets a = CardAssets.forKey(key);
        List<ResourceLocation> layers = new ArrayList<>();
        layers.add(a.empty());
        layers.add(a.figure());
        layers.add(a.blink());
        layers.add(a.lookL());
        layers.add(a.lookR());
        if (!framesDone) {
            framesDone = true;
            for (int r = 0; r <= 2; r++) {
                layers.add(CardAssets.frame(r));
            }
        }
        for (ResourceLocation layer : layers) {
            shrink(layer);
        }
        READY.add(key);
    }

    private static void shrink(ResourceLocation full) {
        if (COPIES.containsKey(full) || !CardAssets.has(full)) {
            return;
        }
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResource(full).orElseThrow().open();
             NativeImage src = NativeImage.read(in)) {
            NativeImage small = box(src);
            ResourceLocation copy = ResourceLocation.fromNamespaceAndPath(CardAssets.NAMESPACE, "card_mini/" + full.getPath());
            DynamicTexture texture = new DynamicTexture(small);
            Minecraft.getInstance().getTextureManager().register(copy, texture);
            texture.setFilter(true, false);
            COPIES.put(full, copy);
        } catch (Exception e) {
            // no copy: the full texture draws instead
        }
    }

    /** One quarter size, averaging each FACTOR x FACTOR block weighted by alpha so edges keep their colour. */
    private static NativeImage box(NativeImage src) {
        int w = Math.max(1, src.getWidth() / FACTOR);
        int h = Math.max(1, src.getHeight() / FACTOR);
        NativeImage out = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                long sa = 0;
                long sr = 0;
                long sg = 0;
                long sb = 0;
                for (int dy = 0; dy < FACTOR; dy++) {
                    for (int dx = 0; dx < FACTOR; dx++) {
                        int c = src.getPixelRGBA(x * FACTOR + dx, y * FACTOR + dy);
                        int ca = c >>> 24;
                        sa += ca;
                        sb += (long) ((c >> 16) & 0xFF) * ca;
                        sg += (long) ((c >> 8) & 0xFF) * ca;
                        sr += (long) (c & 0xFF) * ca;
                    }
                }
                int n = FACTOR * FACTOR;
                int a = (int) (sa / n);
                int r = sa == 0 ? 0 : (int) (sr / sa);
                int g = sa == 0 ? 0 : (int) (sg / sa);
                int b = sa == 0 ? 0 : (int) (sb / sa);
                out.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return out;
    }

    /** Resource reload or logout: free every copy. */
    static void clear() {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation copy : COPIES.values()) {
            textures.release(copy);
        }
        COPIES.clear();
        READY.clear();
        framesDone = false;
    }
}
