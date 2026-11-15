package com.hogwartsmagic.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom render types. Subclassing {@link RenderType} is the only way to reach its
 * protected {@code create} and the shared render-state shards.
 */
public final class ModRenderTypes extends RenderType {

    private ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                           boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    /**
     * Volumetric light: additive so overlapping shells accumulate into a glow, no depth write
     * so those shells don't occlude each other, and no culling so the cone is lit from inside
     * as well as out. Depth testing stays on, so terrain still blocks it.
     */
    public static final RenderType PATRONUS_LIGHT = create(
            "hogwartsmagic_patronus_light",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            2048,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)
    );

    /** Soft round glow sprite, white with the shape in alpha; tinted per vertex with the spell colour. */
    public static final ResourceLocation SPELL_GLOW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "textures/misc/spell_glow.png");
    /** Thin ring sprite for impact shockwaves. */
    public static final ResourceLocation SPELL_RING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "textures/misc/spell_ring.png");

    /**
     * Spell light: a textured sprite added onto the scene (source alpha, one), so stacked glows burn
     * towards white like real bloom. No depth write and no culling; depth testing stays on so walls
     * still hide it. Uses the entity-eyes shader, which is unlit: a spell glows the same at night.
     */
    public static final RenderType SPELL_GLOW = spellLight("hogwartsmagic_spell_glow", SPELL_GLOW_TEXTURE);
    public static final RenderType SPELL_RING = spellLight("hogwartsmagic_spell_ring", SPELL_RING_TEXTURE);

    private static RenderType spellLight(String name, ResourceLocation texture) {
        return create(
                name,
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_EYES_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false)
        );
    }
}
