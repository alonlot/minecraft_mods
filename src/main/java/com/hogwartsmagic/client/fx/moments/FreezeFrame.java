package com.hogwartsmagic.client.fx.moments;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

/**
 * The impact frame: when a freeze is asked for, the next finished world picture is copied once into a texture of
 * our own and drawn full screen over everything below the flash for a few rendered frames, so the instant of the
 * blow holds still while the game carries on underneath. One texture, made on first use, re-made if the window is
 * resized and dropped on logout; the copy is a single framebuffer blit. If anything goes wrong the freeze is
 * switched off for the session rather than risk the frame.
 */
public final class FreezeFrame {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** A freeze never lasts longer than this in real time, whatever the frame rate (a lag spike must not hold it). */
    private static final long MAX_NANOS = 400_000_000L;
    /** The most frames a freeze can ask for. */
    private static final int MAX_FRAMES = 8;

    private static RenderTarget copy;
    private static boolean failed;
    private static int pending;
    private static int showing;
    private static long deadline;

    private FreezeFrame() {
    }

    /** Ask for a freeze of {@code frames} frames, starting from the next finished world picture. */
    public static void request(int frames) {
        if (failed) {
            return;
        }
        pending = Math.max(pending, Math.min(frames, MAX_FRAMES));
    }

    /** Called when the level has just been drawn (main thread, render thread): takes the picture if one is wanted. */
    public static void captureIfWanted() {
        if (pending <= 0) {
            return;
        }
        int frames = pending;
        pending = 0;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        try {
            if (copy != null && (copy.width != main.width || copy.height != main.height)) {
                release();
            }
            if (copy == null) {
                copy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
                copy.setClearColor(0, 0, 0, 1);
            }
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
            GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height,
                    GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
            showing = frames;
            deadline = System.nanoTime() + MAX_NANOS;
        } catch (RuntimeException e) {
            failed = true;
            LOGGER.warn("Cinematic moments: the freeze frame could not be captured; switching it off", e);
            release();
            showing = 0;
        } finally {
            main.bindWrite(true);
        }
    }

    /** The overlay layer (just below the flash): draws the held picture over the HUD for the frames left. */
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (showing <= 0 || copy == null) {
            return;
        }
        if (System.nanoTime() > deadline) {
            showing = 0;
            return;
        }
        showing--;
        Minecraft mc = Minecraft.getInstance();
        if (copy.width != mc.getMainRenderTarget().width || copy.height != mc.getMainRenderTarget().height) {
            showing = 0;
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        Matrix4f pose = graphics.pose().last().pose();
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        // Framebuffer textures are stored upside down: flip V.
        buffer.addVertex(pose, 0, h, 0).setUv(0, 0);
        buffer.addVertex(pose, w, h, 0).setUv(1, 0);
        buffer.addVertex(pose, w, 0, 0).setUv(1, 1);
        buffer.addVertex(pose, 0, 0, 0).setUv(0, 1);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
    }

    private static void release() {
        if (copy != null) {
            try {
                copy.destroyBuffers();
            } catch (RuntimeException ignored) {
                // nothing more to free
            }
            copy = null;
        }
    }

    /** Drop any freeze and the texture (logout, a memory starting). */
    public static void clear() {
        pending = 0;
        showing = 0;
        release();
    }
}
