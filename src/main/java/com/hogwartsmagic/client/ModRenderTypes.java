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

    /**
     * The Protego bubble: the same additive, depth-tested, no-depth-write light as {@link #PATRONUS_LIGHT}, but
     * unsorted -- additive blending doesn't care about order, and the bubble is thousands of quads.
     */
    public static final RenderType PROTEGO_LIGHT = create(
            "hogwartsmagic_protego_light",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            16384,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)
    );

    /**
     * Unlit, linearly filtered smoke for the Dark Mark: straight-alpha blending, no depth write so
     * the stacked layers of the volume all show, no culling so it reads from behind too. Sorted on
     * upload so the layers composite back to front.
     */
    public static RenderType markSmoke(ResourceLocation texture) {
        return create("hogwartsmagic_mark_smoke", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 4096, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_BEACON_BEAM_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
    }

    /**
     * The enchanted ceiling's sky and cloud layers: unlit (full-bright), straight-alpha blending, no depth write (the
     * layers are stacked a few centimetres apart and composite in draw order), no culling. The eyes shader fades
     * with the world's own spherical fog, so the sky sinks into the Pensieve dark exactly as the walls do.
     */
    public static RenderType ceilingLayer(ResourceLocation texture) {
        return ceilingLayer(texture, true);
    }

    /** As {@link #ceilingLayer(ResourceLocation)}; {@code blur} false keeps a block texture's pixels crisp. */
    public static RenderType ceilingLayer(ResourceLocation texture, boolean blur) {
        return create("hogwartsmagic_ceiling_layer", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 4096,
                false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_EYES_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, blur, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
    }

    /** As {@link #ceilingLayer} but additive (ONE, ONE), for stars and glows: texture and colour premultiplied. */
    public static RenderType ceilingGlow(ResourceLocation texture) {
        return create("hogwartsmagic_ceiling_glow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 4096,
                false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_EYES_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
    }

    /** As {@link #markSmoke} but additive (ONE, ONE): the texture and vertex colour must be premultiplied. */
    public static RenderType markGlow(ResourceLocation texture) {
        return create("hogwartsmagic_mark_glow", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 2048, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_BEACON_BEAM_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
    }

    /**
     * The rough sea's water: unlit (the mesh carries its own shaded colour), straight-alpha blending, but writing depth
     * like an opaque surface so near crests hide far troughs and rain behind a wave is hidden. No culling (the camera
     * may dip under a crest). The eyes shader fades with the world's fog, so the far sea sinks into the Pensieve dark.
     */
    public static RenderType seaSurface(ResourceLocation texture) {
        return create("hogwartsmagic_sea_surface", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 262144,
                false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_EYES_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));
    }

    /**
     * The {@code sky} effect's dome: plain vertex colour (no texture), straight-alpha blending so it can fade in, no
     * depth write (it is drawn before the world and everything covers it), no culling (seen from inside). The
     * lightning shader fades with the world's fog like the rest; the sky renderer pushes the fog out while it draws.
     */
    public static final RenderType SKY_DOME = create(
            "hogwartsmagic_sky_dome",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            4096,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setWriteMaskState(COLOR_WRITE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)
    );

    /**
     * Depth only: no colour, writes depth, no culling. The rowing boat's lid over its open hull, drawn just before the
     * sea so the waves never show inside the boat.
     */
    public static final RenderType DEPTH_MASK = create("hogwartsmagic_depth_mask", DefaultVertexFormat.POSITION,
            VertexFormat.Mode.QUADS, 1024, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_SHADER)
                    .setWriteMaskState(DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));
}
