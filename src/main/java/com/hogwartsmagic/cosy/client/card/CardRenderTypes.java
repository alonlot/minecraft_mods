package com.hogwartsmagic.cosy.client.card;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * The card layers in the world: vanilla text type (lightmapped, translucent) but with a blurred (linear) texture.
 * RenderType.text rebinds every texture with blur off on each draw, so setFilter on the texture alone never sticks.
 */
final class CardRenderTypes extends RenderType {
    private CardRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                            boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    static final Function<ResourceLocation, RenderType> SMOOTH_TEXT = Util.memoize(texture -> create(
            "hogwartsmagic_card_text",
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            256,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_TEXT_SHADER)
                    .setTextureState(new TextureStateShard(texture, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .createCompositeState(false)));
}
