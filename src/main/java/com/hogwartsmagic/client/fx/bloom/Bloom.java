package com.hogwartsmagic.client.fx.bloom;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.fx.FxConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

/**
 * Magic glows into the air round it. A spell beam, a strike, two locked beams, a wand trail, a Patronus and its
 * shield, Protego, the Ward and the shield domes, the Killing Curse gathering, the Dark Mark, Fiendfyre and dragon fire
 * each throw a soft halo that bleeds past their edges and lifts the dark round them, wide and faint far out, tight and
 * bright close in. Nothing else blooms: not the sky, the sun, snow or white wool. Over fire the air shimmers too
 * ({@link HeatHaze}).
 *
 * <p>How: the mod's glowing render types ({@link GlowRouting}) are drawn twice, once as ever into whatever target is
 * bound and once more into a glow buffer that shares the main target's depth ({@code BloomRenderTypeMixin} wraps
 * {@link RenderType#draw}), so the order and depth of every draw stay as they were and walls still hide the glow.
 * At {@code AFTER_LEVEL} (after the whole level, Fabulous's transparency composite included, before the hand, the
 * Pensieve grade and the GUI) the glow buffer is halved five times and grown back (a dual-filter blur), the heat
 * haze bends the frame inside its mask, and the blur is screen-blended over the frame. A frame with nothing glowing
 * costs nothing.
 *
 * <p>Off with {@code FxConfig.BLOOM} (or a zero {@code BLOOM_STRENGTH}) and {@code HEAT_HAZE}, and while an Oculus /
 * Iris shader pack is in use. Any GL or shader failure turns it off for the session and logs once. Every pass
 * restores the framebuffer binding, viewport, blend, depth test, colour mask and texture unit it found.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class Bloom {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How hard the blur is laid over the frame at a bloom strength of 1 (the shader eases it: 1 - e^-x). */
    private static final float COMPOSITE_GAIN = 0.65f;
    /** The upsample's tent radius, in texels of the smaller level: wider is softer. */
    private static final float UPSAMPLE_RADIUS = 0.85f;
    /** The haze levels the mask is read from (a quarter and an eighth of the frame: soft past the flames' edges). */
    private static final int HAZE_NEAR_LEVEL = 2;
    private static final int HAZE_WIDE_LEVEL = 3;

    private static final GlowTargets TARGETS = new GlowTargets();
    private static BloomPrograms programs;

    private static boolean failed;
    /** Between AFTER_SKY and AFTER_LEVEL of a frame we may glow in. */
    private static boolean capturing;
    /** The capture is sized and has this frame's depth attached. */
    private static boolean frameReady;
    private static boolean bloomOn;
    private static boolean hazeOn;
    private static boolean glowDirty;
    private static boolean hazeDirty;
    /** Set if a frame captured but never reached its composite: the next one clears first. */
    private static boolean stale;
    private static boolean held;

    private Bloom() {
    }

    // ---- what other code calls --------------------------------------------------------------------------------

    /**
     * Stop routing glow for a moment (and resume with false): for a batch drawn with a glowing render type that is
     * not itself magic light, like the coloured light {@code SpellLight} throws on walls.
     */
    public static void holdCapture(boolean hold) {
        held = hold;
    }

    /** Whether heat points are wanted this frame. */
    static boolean wantsHaze() {
        return capturing && hazeOn && !failed;
    }

    /** Ready the capture for drawing into this frame; false if it can't be. */
    static boolean readyCapture() {
        return capturing && ensureFrame();
    }

    static void bindCapture() {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, TARGETS.captureFbo);
    }

    static void markHaze() {
        hazeDirty = true;
    }

    /**
     * From {@code BloomRenderTypeMixin}, at the head of every {@link RenderType#draw}: draws a glowing batch itself,
     * into the bound target and then the glow buffer, and returns true so the vanilla draw is skipped. Anything else
     * (or any frame we are not glowing in) returns false and draws as ever.
     */
    public static boolean capture(RenderType type, MeshData mesh) {
        if (!capturing || held || !RenderSystem.isOnRenderThread()) {
            return false;
        }
        int route = GlowRouting.of(type);
        if (route == GlowRouting.NONE) {
            return false;
        }
        if (route == GlowRouting.GLOW && !bloomOn) {
            return false; // the haze alone needs only the fire
        }
        if (route == GlowRouting.OCCLUDE && !(bloomOn && glowDirty)) {
            return false; // nothing glowing yet to hide
        }
        if (!ensureFrame()) {
            return false;
        }
        boolean uploaded = false;
        type.setupRenderState();
        try {
            VertexBuffer buffer = mesh.drawState().format().getImmediateDrawVertexBuffer();
            buffer.bind();
            uploaded = true; // upload consumes the mesh even if it throws: vanilla must not draw it again
            buffer.upload(mesh);
            ShaderInstance shader = RenderSystem.getShader();
            Matrix4f modelView = RenderSystem.getModelViewMatrix();
            Matrix4f projection = RenderSystem.getProjectionMatrix();
            buffer.drawWithShader(modelView, projection, shader);

            int previous = GlStateManager.getBoundFramebuffer();
            bindCapture();
            try {
                if (route == GlowRouting.GLOW) {
                    RenderSystem.colorMask(true, true, true, false);
                } else if (route == GlowRouting.FIRE) {
                    RenderSystem.colorMask(bloomOn, bloomOn, bloomOn, hazeOn);
                } else {
                    RenderSystem.colorMask(true, true, true, false);
                    // Straight-alpha smoke: dims what is behind it by its alpha and adds nothing of its own.
                    RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO,
                            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ZERO,
                            GlStateManager.DestFactor.ONE);
                }
                buffer.drawWithShader(modelView, projection, shader);
            } finally {
                RenderSystem.colorMask(true, true, true, true);
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
            }
            if (route == GlowRouting.GLOW || (route == GlowRouting.FIRE && bloomOn)) {
                glowDirty = true;
            }
            if (route == GlowRouting.FIRE && hazeOn) {
                hazeDirty = true;
            }
        } catch (RuntimeException e) {
            fail(e);
        } finally {
            type.clearRenderState();
        }
        return uploaded;
    }

    // ---- the frame --------------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            beginFrame();
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            finishFrame();
        }
    }

    /** The first stage of the level: decide whether this frame glows. No GL work until something does. */
    private static void beginFrame() {
        if (glowDirty || hazeDirty) {
            stale = true;
        }
        capturing = false;
        frameReady = false;
        glowDirty = false;
        hazeDirty = false;
        held = false;
        if (failed) {
            return;
        }
        bloomOn = FxConfig.get(FxConfig.BLOOM) && FxConfig.get(FxConfig.BLOOM_STRENGTH) > 0.001;
        hazeOn = FxConfig.get(FxConfig.HEAT_HAZE);
        if ((!bloomOn && !hazeOn) || ShaderPacks.inUse()) {
            return;
        }
        if (programs == null) {
            try {
                programs = BloomPrograms.load(Minecraft.getInstance().getResourceManager());
            } catch (Exception e) {
                fail(e);
                return;
            }
        }
        capturing = true;
    }

    /** The capture sized to the main target, this frame's depth attached, cleared if new or stale. */
    private static boolean ensureFrame() {
        if (frameReady) {
            return true;
        }
        if (failed) {
            return false;
        }
        int previous = GlStateManager.getBoundFramebuffer();
        try {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            boolean fresh = TARGETS.prepare(main);
            if (fresh || stale) {
                TARGETS.clearCapture();
                stale = false;
            }
            frameReady = true;
        } catch (RuntimeException e) {
            fail(e);
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        }
        return frameReady;
    }

    /** After the level, before the hand: blur, haze, composite, then clear the capture for the next frame. */
    private static void finishFrame() {
        boolean was = capturing;
        capturing = false;
        held = false;
        if (!was || failed || !frameReady || (!glowDirty && !hazeDirty)) {
            glowDirty = false;
            hazeDirty = false;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int drawFbo = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFbo = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean doBloom = bloomOn && glowDirty;
        boolean doHaze = hazeOn && hazeDirty;
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.colorMask(true, true, true, true);
            BloomPrograms fx = programs;
            GlowTargets t = TARGETS;

            // Down: each level half the last, a five-tap filter; the first also tames stacked additive light.
            int levels = doBloom ? GlowTargets.LEVELS : HAZE_WIDE_LEVEL;
            for (int i = 1; i <= levels; i++) {
                int src = i == 1 ? t.captureTex : t.levelTex[i - 1];
                int srcW = i == 1 ? t.width : t.levelW[i - 1];
                int srcH = i == 1 ? t.height : t.levelH[i - 1];
                fx.down.setSampler("DiffuseSampler", () -> src);
                fx.down.safeGetUniform("InTexel").set(1.0f / srcW, 1.0f / srcH);
                fx.down.safeGetUniform("Prefilter").set(i == 1 ? 1.0f : 0.0f);
                BloomPrograms.pass(fx.down, t.levelFbo[i], t.levelW[i], t.levelH[i], BloomPrograms.OPAQUE);
            }

            // The heat haze bends a copy of the frame inside the mask, before the glow goes over it.
            if (doHaze) {
                t.ensureScene();
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, t.sceneFbo);
                GlStateManager._glBlitFrameBuffer(0, 0, t.width, t.height, 0, 0, t.width, t.height,
                        GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
                EffectInstance haze = fx.haze;
                int scene = t.sceneTex;
                int near = t.levelTex[HAZE_NEAR_LEVEL];
                int wide = t.levelTex[HAZE_WIDE_LEVEL];
                haze.setSampler("SceneSampler", () -> scene);
                haze.setSampler("MaskSampler", () -> near);
                haze.setSampler("WideSampler", () -> wide);
                haze.safeGetUniform("Time").set((Util.getMillis() % 3_600_000L) / 1000.0f);
                haze.safeGetUniform("Aspect").set(t.width / (float) t.height);
                RenderSystem.colorMask(true, true, true, false);
                BloomPrograms.pass(haze, main.frameBufferId, t.width, t.height, BloomPrograms.OPAQUE);
                RenderSystem.colorMask(true, true, true, true);
            }

            if (doBloom) {
                // Up: each wider level added onto the next tighter one (colour only: the haze mask stays put).
                RenderSystem.colorMask(true, true, true, false);
                for (int i = GlowTargets.LEVELS; i >= 2; i--) {
                    int src = t.levelTex[i];
                    fx.up.setSampler("DiffuseSampler", () -> src);
                    fx.up.safeGetUniform("InTexel").set(1.0f / t.levelW[i], 1.0f / t.levelH[i]);
                    fx.up.safeGetUniform("Radius").set(UPSAMPLE_RADIUS);
                    BloomPrograms.pass(fx.up, t.levelFbo[i - 1], t.levelW[i - 1], t.levelH[i - 1],
                            BloomPrograms.ADD);
                }
                // Over the frame: screen-blended, colour only.
                int top = t.levelTex[1];
                fx.composite.setSampler("DiffuseSampler", () -> top);
                fx.composite.safeGetUniform("InTexel").set(1.0f / t.levelW[1], 1.0f / t.levelH[1]);
                fx.composite.safeGetUniform("Strength").set(
                        (float) (COMPOSITE_GAIN * FxConfig.get(FxConfig.BLOOM_STRENGTH)));
                BloomPrograms.pass(fx.composite, main.frameBufferId, t.width, t.height, BloomPrograms.SCREEN);
                RenderSystem.colorMask(true, true, true, true);
            }

            t.clearCapture();
        } catch (RuntimeException e) {
            fail(e);
        } finally {
            glowDirty = false;
            hazeDirty = false;
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.defaultBlendFunc();
            if (blend) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
            if (depthTest) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    // ---- failure and reload -----------------------------------------------------------------------------------

    private static void fail(Throwable e) {
        if (!failed) {
            LOGGER.warn("Hogwarts Magic: the spell glow and heat haze could not run on this machine and are off "
                    + "for this session", e);
        }
        failed = true;
        capturing = false;
        frameReady = false;
        glowDirty = false;
        hazeDirty = false;
        try {
            TARGETS.release();
        } catch (RuntimeException ignored) {
            // nothing more to undo
        }
        closePrograms();
    }

    private static void closePrograms() {
        if (programs != null) {
            programs.close();
            programs = null;
        }
    }

    /**
     * A resource reload (a pack change, F3+T) rebuilds the programs next frame, so a pack's own versions of them are
     * picked up. A failure stays failed for the session.
     */
    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Reload {
        private Reload() {
        }

        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> {
                capturing = false;
                closePrograms();
            });
        }
    }
}
