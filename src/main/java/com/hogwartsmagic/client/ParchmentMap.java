package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.mojang.blaze3d.platform.NativeImage;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Hand-inked parchment map of loaded terrain, baked once into a texture: hill shading, contour lines,
 * inked coastlines, stippled forests, sand, snow and lava washes, and pale hatched fog where chunks are
 * not loaded. Used by the Floo and Apparition screens. Client only; call {@link #close()} when the
 * screen goes away.
 */
public final class ParchmentMap {
    /** Per-texel sampling: world X/Z of texel (tx, tz), or null to leave the texel transparent. */
    public interface Sampler {
        @Nullable
        int[] worldXZ(int tx, int tz);
    }

    private static int nextId;

    private static final int KIND_UNKNOWN = 0, KIND_LAND = 1, KIND_WATER = 2, KIND_FOREST = 3,
            KIND_SAND = 4, KIND_SNOW = 5, KIND_LAVA = 6, KIND_NONE = 7;

    private final ResourceLocation location;
    private final DynamicTexture texture;
    private final int width;
    private final int height;
    private final boolean[] known;

    private ParchmentMap(ResourceLocation location, DynamicTexture texture, int width, int height, boolean[] known) {
        this.location = location;
        this.texture = texture;
        this.width = width;
        this.height = height;
        this.known = known;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    /** True when texel (tx, tz) is loaded, sampled ground. */
    public boolean isKnown(int tx, int tz) {
        return tx >= 0 && tz >= 0 && tx < this.width && tz < this.height && this.known[tz * this.width + tx];
    }

    /** Draws the whole map stretched to {@code w x h} GUI pixels at (x, y). */
    public void draw(GuiGraphics g, int x, int y, int w, int h) {
        g.blit(this.location, x, y, w, h, 0f, 0f, this.width, this.height, this.width, this.height);
    }

    public void close() {
        Minecraft.getInstance().getTextureManager().release(this.location);
    }

    /**
     * Samples {@code w x h} texels. {@code blocksPerTexel} only tunes the shading (slope per texel and
     * contour spacing) so near and far maps read alike.
     */
    public static ParchmentMap bake(Level level, int w, int h, float blocksPerTexel, Sampler sampler) {
        int[] kind = new int[w * h];
        int[] terrain = new int[w * h];
        int[] depth = new int[w * h];
        boolean[] known = new boolean[w * h];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int sea = level.getSeaLevel();
        for (int tz = 0; tz < h; tz++) {
            for (int tx = 0; tx < w; tx++) {
                int i = tz * w + tx;
                int[] xz = sampler.worldXZ(tx, tz);
                if (xz == null) {
                    kind[i] = KIND_NONE;
                    continue;
                }
                int wx = xz[0];
                int wz = xz[1];
                if (!level.hasChunk(wx >> 4, wz >> 4)) {
                    kind[i] = KIND_UNKNOWN;
                    terrain[i] = sea;
                    continue;
                }
                known[i] = true;
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
                pos.set(wx, surface - 1, wz);
                BlockState top = level.getBlockState(pos);
                terrain[i] = ground;
                if (top.getFluidState().is(FluidTags.WATER)) {
                    kind[i] = KIND_WATER;
                    int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, wx, wz);
                    depth[i] = Math.max(0, surface - floor);
                    terrain[i] = surface;
                } else if (top.getFluidState().is(FluidTags.LAVA)) {
                    kind[i] = KIND_LAVA;
                } else if (top.is(BlockTags.LEAVES)) {
                    kind[i] = KIND_FOREST;
                } else if (top.is(BlockTags.SAND)) {
                    kind[i] = KIND_SAND;
                } else if (top.is(Blocks.SNOW) || top.is(Blocks.SNOW_BLOCK) || top.is(Blocks.POWDER_SNOW)
                        || top.is(BlockTags.ICE)) {
                    kind[i] = KIND_SNOW;
                } else {
                    kind[i] = KIND_LAND;
                }
            }
        }

        // Height range of the known land, for the hypsometric tint.
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        for (int i = 0; i < kind.length; i++) {
            if (known[i] && kind[i] != KIND_WATER) {
                lo = Math.min(lo, terrain[i]);
                hi = Math.max(hi, terrain[i]);
            }
        }
        if (lo > hi) {
            lo = sea;
            hi = sea + 1;
        }
        float span = Math.max(8, hi - lo);
        int contour = Math.max(4, Math.round(6 * Mth.sqrt(Math.max(1f, blocksPerTexel))));

        NativeImage image = new NativeImage(w, h, false);
        for (int tz = 0; tz < h; tz++) {
            for (int tx = 0; tx < w; tx++) {
                int i = tz * w + tx;
                int k = kind[i];
                int argb;
                if (k == KIND_NONE) {
                    image.setPixelRGBA(tx, tz, 0);
                    continue;
                }
                int fiber = (hash(tx, tz) & 15) - 8;
                if (k == KIND_UNKNOWN) {
                    argb = ((tx + tz) & 3) == 0 ? 0xFFD6C49A : 0xFFEBDDBA;
                    image.setPixelRGBA(tx, tz, toAbgr(shade(argb, 1f + fiber / 200f)));
                    continue;
                }
                if (k == KIND_WATER) {
                    float d = Mth.clamp(depth[i] / 10f, 0f, 1f);
                    argb = ThemedGui.lerpColor(0xB4CBB8, 0x7E9E98, d);
                    // Shore ink: a water texel touching known non-water.
                    if (touches(kind, known, w, h, tx, tz, false)) {
                        argb = 0xFF5A4026;
                    } else if (((tx * 3 + tz * 7) % 11) == 0 && d > 0.3f) {
                        argb = shade(argb, 0.92f); // faint ripples
                    }
                } else {
                    float t = Mth.clamp((terrain[i] - lo) / span, 0f, 1f);
                    argb = ThemedGui.lerpColor(0xE8D6A8, 0xC2A574, t);
                    if (k == KIND_SAND) {
                        argb = ThemedGui.lerpColor(argb, 0xF1E2B6, 0.6f);
                    } else if (k == KIND_SNOW) {
                        argb = ThemedGui.lerpColor(argb, 0xF6F1E4, 0.8f);
                    } else if (k == KIND_LAVA) {
                        argb = 0xFFD2703A;
                    }
                    // Hill shading, light from the north-west.
                    float dx = heightAt(terrain, kind, w, h, tx + 1, tz, terrain[i]) - heightAt(terrain, kind, w, h, tx - 1, tz, terrain[i]);
                    float dz = heightAt(terrain, kind, w, h, tx, tz + 1, terrain[i]) - heightAt(terrain, kind, w, h, tx, tz - 1, terrain[i]);
                    float slope = (dx + dz) / (2f * Math.max(1f, blocksPerTexel));
                    argb = shade(argb, Mth.clamp(1f - slope * 0.09f, 0.72f, 1.16f));
                    // Contour lines where the height band changes.
                    int band = Math.floorDiv(terrain[i], contour);
                    boolean edge = bandAt(terrain, kind, w, h, tx + 1, tz, contour, band) != band
                            || bandAt(terrain, kind, w, h, tx, tz + 1, contour, band) != band;
                    if (edge) {
                        argb = ThemedGui.lerpColor(argb, 0x8A6A40, 0.45f);
                    }
                    if (k == KIND_FOREST) {
                        // Stippled woods: a few ink dots on a faint olive wash.
                        argb = ThemedGui.lerpColor(argb, 0xB9B47E, 0.35f);
                        int hsh = hash(tx * 5 + 1, tz * 3 + 7) & 7;
                        if (hsh == 0) {
                            argb = 0xFF4F5A30;
                        } else if (hsh == 1) {
                            argb = ThemedGui.lerpColor(argb, 0x4F5A30, 0.5f);
                        }
                    }
                    if (touches(kind, known, w, h, tx, tz, true)) {
                        argb = ThemedGui.lerpColor(argb, 0x5A4026, 0.55f);
                    }
                }
                image.setPixelRGBA(tx, tz, toAbgr(shade(argb, 1f + fiber / 260f)));
            }
        }

        DynamicTexture texture = new DynamicTexture(image);
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "dynamic/parchment_map_" + (nextId++));
        Minecraft.getInstance().getTextureManager().register(location, texture);
        return new ParchmentMap(location, texture, w, h, known);
    }

    /** Does a texel of the opposite medium (water vs land) sit next to this one? */
    private static boolean touches(int[] kind, boolean[] known, int w, int h, int tx, int tz, boolean lookForWater) {
        int[][] n = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : n) {
            int x = tx + d[0];
            int z = tz + d[1];
            if (x < 0 || z < 0 || x >= w || z >= h) {
                continue;
            }
            int j = z * w + x;
            if (!known[j]) {
                continue;
            }
            boolean water = kind[j] == KIND_WATER;
            if (water == lookForWater) {
                return true;
            }
        }
        return false;
    }

    private static int heightAt(int[] terrain, int[] kind, int w, int h, int x, int z, int fallback) {
        if (x < 0 || z < 0 || x >= w || z >= h) {
            return fallback;
        }
        int k = kind[z * w + x];
        return k == KIND_UNKNOWN || k == KIND_NONE ? fallback : terrain[z * w + x];
    }

    private static int bandAt(int[] terrain, int[] kind, int w, int h, int x, int z, int contour, int fallback) {
        if (x < 0 || z < 0 || x >= w || z >= h) {
            return fallback;
        }
        int k = kind[z * w + x];
        if (k == KIND_UNKNOWN || k == KIND_NONE || k == KIND_WATER) {
            return fallback;
        }
        return Math.floorDiv(terrain[z * w + x], contour);
    }

    private static int shade(int argb, float f) {
        int r = Mth.clamp(Math.round(((argb >> 16) & 0xFF) * f), 0, 255);
        int g = Mth.clamp(Math.round(((argb >> 8) & 0xFF) * f), 0, 255);
        int b = Mth.clamp(Math.round((argb & 0xFF) * f), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int toAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int hash(int x, int z) {
        int hsh = x * 0x27D4EB2D ^ z * 0x165667B1;
        hsh ^= hsh >>> 15;
        hsh *= 0x2C1B3C6D;
        return hsh ^ (hsh >>> 12);
    }
}
