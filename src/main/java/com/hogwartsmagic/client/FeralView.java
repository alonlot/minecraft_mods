package com.hogwartsmagic.client;

import com.hogwartsmagic.spell.mind.MindControl;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * A feral werewolf's own view: the wolf has the body, and the player only watches from somewhere deep inside it.
 * The finished world frame is copied, the screen goes dark, and the frame is drawn back shrunk into a small window
 * that breathes and sways a little, its edges sunk in shadow. The HUD is drawn afterwards, full size, as usual.
 *
 * Runs at the head of Gui#render (MindControlGuiMixin), when the main target holds the world but no HUD yet. The
 * whole state is the feral effect vanilla syncs to the local player, eased in and out over a second or two.
 */
public final class FeralView {
    /** The window's size, as a fraction of the screen, when the wolf has you completely. */
    private static final float WINDOW_SCALE = 0.55F;
    private static final float EASE_IN_SECONDS = 1.6F;
    private static final float EASE_OUT_SECONDS = 0.9F;
    private static final int SHADOW_STRIPS = 16;
    /** The dark around the window: almost black, a little blood in it. */
    private static final int SURROUND = 0x0B0303;

    private static TextureTarget copy;
    private static float amount;
    private static long lastNanos;

    private FeralView() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean feral = player != null && minecraft.level != null && MindControl.isFeral(player);
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0F : Math.min(0.1F, (now - lastNanos) / 1.0E9F);
        lastNanos = now;
        float step = dt / (feral ? EASE_IN_SECONDS : EASE_OUT_SECONDS);
        amount = feral ? Math.min(1.0F, amount + step) : Math.max(0.0F, amount - step);
        if (amount <= 0.0F || player == null) {
            return;
        }

        RenderTarget main = minecraft.getMainRenderTarget();
        int w = main.width;
        int h = main.height;
        if (w <= 0 || h <= 0) {
            return;
        }
        if (copy == null) {
            copy = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        } else if (copy.width != w || copy.height != h) {
            copy.resize(w, h, Minecraft.ON_OSX);
        }

        graphics.flush();
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);

        float ease = amount * amount * (3.0F - 2.0F * amount); // smoothstep
        int gw = graphics.guiWidth();
        int gh = graphics.guiHeight();
        float t = (player.tickCount + minecraft.getTimer().getGameTimeDeltaPartialTick(false)) / 20.0F;
        float breathe = 1.0F + 0.015F * Mth.sin(t * 1.4F) * ease;
        float scale = Mth.lerp(ease, 1.0F, WINDOW_SCALE) * breathe;
        float cx = gw / 2.0F + Mth.sin(t * 0.7F) * gw * 0.012F * ease;
        float cy = gh / 2.0F + Mth.cos(t * 0.9F) * gh * 0.010F * ease;
        float x0 = cx - gw * scale / 2.0F;
        float x1 = cx + gw * scale / 2.0F;
        float y0 = cy - gh * scale / 2.0F;
        float y1 = cy + gh * scale / 2.0F;

        RenderSystem.disableDepthTest();
        graphics.fill(0, 0, gw, gh, ((int) (255 * ease) << 24) | SURROUND);
        graphics.flush();

        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        Matrix4f pose = graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        // The framebuffer's rows run bottom-up: the top of the window shows v = 1.
        buffer.addVertex(pose, x0, y1, 0.0F).setUv(0.0F, 0.0F);
        buffer.addVertex(pose, x1, y1, 0.0F).setUv(1.0F, 0.0F);
        buffer.addVertex(pose, x1, y0, 0.0F).setUv(1.0F, 1.0F);
        buffer.addVertex(pose, x0, y0, 0.0F).setUv(0.0F, 1.0F);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableBlend();

        innerShadow(graphics, x0, y0, x1, y1, Math.min(x1 - x0, y1 - y0) * 0.16F, ease);
        graphics.flush();
        RenderSystem.enableDepthTest();
    }

    /** The window's edges sink into the dark: rings of falling opacity just inside its border. */
    private static void innerShadow(GuiGraphics graphics, float x0, float y0, float x1, float y1, float depth, float ease) {
        float band = depth / SHADOW_STRIPS;
        for (int i = 0; i < SHADOW_STRIPS; i++) {
            float fade = 1.0F - (float) i / SHADOW_STRIPS;
            int alpha = (int) (220 * fade * fade * ease);
            if (alpha <= 0) {
                continue;
            }
            int color = (alpha << 24) | SURROUND;
            int l = Math.round(x0 + band * i);
            int r = Math.round(x1 - band * i);
            int t = Math.round(y0 + band * i);
            int b = Math.round(y1 - band * i);
            int nb = Math.max(1, Math.round(band));
            if (r - l <= 2 * nb || b - t <= 2 * nb) {
                break;
            }
            graphics.fill(l, t, r, t + nb, color);          // top
            graphics.fill(l, b - nb, r, b, color);          // bottom
            graphics.fill(l, t + nb, l + nb, b - nb, color); // left
            graphics.fill(r - nb, t + nb, r, b - nb, color); // right
        }
    }
}
