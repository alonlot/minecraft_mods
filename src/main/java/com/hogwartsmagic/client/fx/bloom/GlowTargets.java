package com.hogwartsmagic.client.fx.bloom;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * The glow buffers, all raw GL objects of our own (vanilla's RenderTarget is fixed to 8-bit colour and its own depth).
 *
 * <ul>
 *   <li>The capture: full size, half-float colour where the driver allows it (so stacked additive light can go past
 *   white and spread wider), and the main target's own depth texture attached as its depth, so walls hide the glow
 *   exactly as they hide the light, with no copy.</li>
 *   <li>The chain: {@link #LEVELS} levels, each half the size of the one before, for the blur.</li>
 *   <li>The scene copy: the finished frame, for the heat haze to bend; made only once fire is seen.</li>
 * </ul>
 *
 * Every method expects the caller to save and restore the framebuffer binding round it.
 */
final class GlowTargets {
    static final int LEVELS = 5;

    private static final float[] TRANSPARENT = {0.0f, 0.0f, 0.0f, 0.0f};

    int width = -1;
    int height = -1;
    int captureTex = -1;
    int captureFbo = -1;
    /** Index 1..LEVELS; level i is the capture halved i times. */
    final int[] levelTex = new int[LEVELS + 1];
    final int[] levelFbo = new int[LEVELS + 1];
    final int[] levelW = new int[LEVELS + 1];
    final int[] levelH = new int[LEVELS + 1];
    int sceneTex = -1;
    int sceneFbo = -1;
    private boolean hdr = true;
    /** The main depth texture id the capture framebuffer was last verified complete with (-1: not yet). */
    private int checkedDepth = -1;

    GlowTargets() {
        java.util.Arrays.fill(levelTex, -1);
        java.util.Arrays.fill(levelFbo, -1);
    }

    /**
     * Ready the capture for this frame at the main target's size: rebuilt after a resize, and the main depth texture
     * attached afresh every frame (a resize makes a new one, and GL may hand it the old name). Returns true when the
     * buffers are new, so their colour is undefined and needs clearing.
     */
    boolean prepare(RenderTarget main) {
        boolean fresh = false;
        if (captureFbo < 0 || main.width != width || main.height != height) {
            release();
            allocate(main.width, main.height);
            fresh = true;
            checkedDepth = -1;
        }
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, captureFbo);
        int depthId = main.getDepthTextureId();
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D,
                depthId, 0);
        if (depthId == checkedDepth) {
            return fresh; // same attachment as the last verified frame: no driver round trip to re-check it
        }
        int status = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE && hdr) {
            // Some drivers will not pair half-float colour with this depth format: fall back to 8-bit.
            hdr = false;
            release();
            allocate(main.width, main.height);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, captureFbo);
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D,
                    depthId, 0);
            status = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            fresh = true;
        }
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("glow capture framebuffer incomplete: 0x" + Integer.toHexString(status));
        }
        checkedDepth = depthId;
        return fresh;
    }

    /** Clear the capture's colour (and haze alpha) to nothing; the shared depth is untouched. */
    void clearCapture() {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, captureFbo);
        RenderSystem.colorMask(true, true, true, true);
        GL30.glClearBufferfv(GL11.GL_COLOR, 0, TRANSPARENT);
    }

    /** The scene copy for the heat haze, made the first time it is needed at this size. */
    void ensureScene() {
        if (sceneFbo >= 0) {
            return;
        }
        sceneTex = texture(width, height, GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE);
        sceneFbo = framebuffer(sceneTex);
    }

    private void allocate(int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        int internal = hdr ? GL30.GL_RGBA16F : GL11.GL_RGBA8;
        int type = hdr ? GL11.GL_FLOAT : GL11.GL_UNSIGNED_BYTE;
        captureTex = texture(width, height, internal, type);
        captureFbo = framebuffer(captureTex);
        for (int i = 1; i <= LEVELS; i++) {
            levelW[i] = Math.max(1, width >> i);
            levelH[i] = Math.max(1, height >> i);
            levelTex[i] = texture(levelW[i], levelH[i], internal, type);
            levelFbo[i] = framebuffer(levelTex[i]);
            int status = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("glow level " + i + " incomplete: 0x" + Integer.toHexString(status));
            }
        }
    }

    void release() {
        if (captureFbo >= 0) {
            GlStateManager._glDeleteFramebuffers(captureFbo);
        }
        if (captureTex >= 0) {
            TextureUtil.releaseTextureId(captureTex);
        }
        captureFbo = -1;
        captureTex = -1;
        for (int i = 1; i <= LEVELS; i++) {
            if (levelFbo[i] >= 0) {
                GlStateManager._glDeleteFramebuffers(levelFbo[i]);
            }
            if (levelTex[i] >= 0) {
                TextureUtil.releaseTextureId(levelTex[i]);
            }
            levelFbo[i] = -1;
            levelTex[i] = -1;
        }
        if (sceneFbo >= 0) {
            GlStateManager._glDeleteFramebuffers(sceneFbo);
        }
        if (sceneTex >= 0) {
            TextureUtil.releaseTextureId(sceneTex);
        }
        sceneFbo = -1;
        sceneTex = -1;
        width = -1;
        height = -1;
    }

    /** A linearly filtered, edge-clamped texture with undefined contents. Leaves texture 0 bound. */
    private static int texture(int w, int h, int internal, int type) {
        int id = TextureUtil.generateTextureId();
        GlStateManager._bindTexture(id);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, internal, w, h, 0, GL11.GL_RGBA, type, null);
        GlStateManager._bindTexture(0);
        return id;
    }

    /** A framebuffer with {@code tex} as its only colour attachment, left bound. */
    private static int framebuffer(int tex) {
        int fbo = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                tex, 0);
        return fbo;
    }
}
