package com.hogwartsmagic.client.fx.marks;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The render types of the lingering marks. Subclassing {@link RenderType} is the only way to reach its protected
 * {@code create} and the shared state shards (as {@code client/ModRenderTypes} does).
 *
 * <p>Both layers use a polygon offset (so they win the depth fight with the block face they lie on, on top of the
 * small lift off the face) and write no depth. The soot and frost are lit by the world's light (a scorch in a dark
 * cellar does not glow grey); the embers and glints are full-bright additive light.
 */
public final class MarkRenderTypes extends RenderType {

    private MarkRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                            boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    /** A straight-alpha decal lit by the world's light: soot, frost. */
    public static RenderType decal(ResourceLocation texture) {
        return create("hogwartsmagic_mark_decal", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 4096,
                false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setLayeringState(POLYGON_OFFSET_LAYERING)
                        .createCompositeState(false));
    }

    /** Additive full-bright light over the decal: embers, glints. Colour is premultiplied (it is the light). */
    public static RenderType glow(ResourceLocation texture) {
        return create("hogwartsmagic_mark_glow_decal", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 4096,
                false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_EYES_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setLayeringState(POLYGON_OFFSET_LAYERING)
                        .createCompositeState(false));
    }
}
