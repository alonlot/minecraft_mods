package com.hogwartsmagic.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

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
}
