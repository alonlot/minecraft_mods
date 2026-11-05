package com.hogwartsmagic.client.portrait;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Render types for portraits. The video frames (192 x 384 or 384 x 192) are stretched over the whole canvas, so they need linear
 * filtering; vanilla's entity types rebind every texture with blur off on each draw (TextureStateShard), so
 * calling setFilter on the texture alone never sticks.
 */
final class PortraitRenderTypes extends RenderType {
    private PortraitRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    /** entitySolid with a blurred (linear) texture. */
    static final Function<ResourceLocation, RenderType> SMOOTH_SOLID = Util.memoize(texture -> create(
            "hogwartsmagic_portrait_smooth_solid",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            true,
            false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER)
                    .setTextureState(new TextureStateShard(texture, true, false))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true)));
}
