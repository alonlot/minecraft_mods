package com.hogwartsmagic.client.fx.creature;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ModRenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Shared drawing bits of the creature effects: the two unlit render types (straight-alpha for smoke and dust,
 * additive for fire) over the soft-edged streak texture the light rays use (opaque in the middle of its width, fading
 * to nothing at both edges, so a strip across it has soft sides), and the one vertex every quad is made of.
 * Client only; a class of statics so the render types are built once.
 */
final class CreatureDraw {

    private static final ResourceLocation STREAK =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/light_ray.png");

    /** Smoke, dust and shockwave rings: straight alpha, unlit, no depth write, depth-tested against the world. */
    static final RenderType SOFT = ModRenderTypes.ceilingLayer(STREAK);
    /** Fire: additive, so colour is faded by scaling it down (alpha is ignored by the blend). */
    static final RenderType GLOW = ModRenderTypes.ceilingGlow(STREAK);

    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;

    private CreatureDraw() {
    }

    static float clamp01(float v) {
        return v < 0.0f ? 0.0f : v > 1.0f ? 1.0f : v;
    }

    /** A smooth 0..1 ramp between {@code a} and {@code b}. */
    static float smoothstep(float a, float b, float v) {
        float t = clamp01((v - a) / (b - a));
        return t * t * (3.0f - 2.0f * t);
    }

    static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float g, float b, float a,
                       float u, float v) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, clamp01(a)).setUv(u, v).setOverlay(OVERLAY).setLight(LIGHT)
                .setNormal(0.0f, 1.0f, 0.0f);
    }
}
