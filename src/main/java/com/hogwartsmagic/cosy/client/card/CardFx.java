package com.hogwartsmagic.cosy.client.card;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/** GUI helpers for the reveal: gradient polygons (shimmer band, light rays), plain or additive. Client only. */
final class CardFx {
    private CardFx() {
    }

    /** A triangle fan with a colour (and alpha) per vertex. {@code additive} adds light instead of covering. */
    static void fan(GuiGraphics g, float[] xs, float[] ys, int[] argb, boolean additive) {
        if (xs.length < 3) {
            return;
        }
        g.flush();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        // Counter-clockwise on a y-down screen is the front face (culling is on in the GUI): callers list their
        // polygons clockwise, so emit them reversed.
        for (int i = xs.length - 1; i >= 0; i--) {
            b.addVertex(m, xs[i], ys[i], 0.0F).setColor(argb[i]);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** Alpha-scaled colour: {@code rgb} with alpha {@code a} (0..1). */
    static int withAlpha(int rgb, float a) {
        int alpha = Math.max(0, Math.min(255, (int) (a * 255.0F)));
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * Light rays fanning out of ({@code cx}, {@code cy}): {@code count} thin wedges, turned by {@code rotation}
     * radians, bright at the centre and fading out at {@code radius}. Additive.
     */
    static void rays(GuiGraphics g, float cx, float cy, float radius, float rotation, int count, int rgb, float strength) {
        float step = (float) (Math.PI * 2.0 / count);
        float half = step * 0.17F;
        for (int i = 0; i < count; i++) {
            float a = rotation + i * step;
            float len = radius * (0.7F + 0.3F * (float) Math.sin(i * 2.399F + rotation * 2.0F));
            float[] xs = {cx, cx + (float) Math.cos(a - half) * len, cx + (float) Math.cos(a + half) * len};
            float[] ys = {cy, cy + (float) Math.sin(a - half) * len, cy + (float) Math.sin(a + half) * len};
            int[] cols = {withAlpha(rgb, strength), withAlpha(rgb, 0.0F), withAlpha(rgb, 0.0F)};
            fan(g, xs, ys, cols, true);
        }
    }
}
