package com.hogwartsmagic.client.fx.moments;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.fx.bloom.ShaderPacks;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * The colour draining out of the world at a big moment: a post chain
 * ({@code hogwartsmagic:shaders/post/moment_drain.json}) over the main render target, run once the level has been
 * drawn and before the HUD, so the world greys out and the HUD keeps its colour. Uniform: Amount (0..1).
 *
 * <p>Self-contained: its own chain, its own failure flag. If the chain cannot be built it logs once and does
 * nothing from then on. After the pass the main target is bound for writing again and the blend and depth state
 * the level render expects are put back.
 *
 * <p>Depth note: the chain's last pass blits to {@code minecraft:main}, and a post pass clears its output target
 * (colour and depth) before drawing, so main's depth buffer is wiped by this pass. That is harmless where it runs
 * (after the level is drawn, before the HUD, nothing later reads the world depth), but it means this pass must
 * stay AFTER Bloom, which samples the world depth, and must never be moved before anything that still needs it.
 * Skipped entirely while a shader pack is in use, like Bloom.
 */
public final class DrainPost {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID,
            "shaders/post/moment_drain.json");

    private static PostChain chain;
    private static boolean failed;
    private static int width = -1;
    private static int height = -1;

    private DrainPost() {
    }

    /** Run the pass at {@code amount} (0..1). Does nothing for a negligible amount. */
    public static void process(float amount) {
        if (failed || amount < 0.004f || ShaderPacks.inUse()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, CHAIN);
                width = -1;
            }
            if (main.width != width || main.height != height) {
                width = main.width;
                height = main.height;
                chain.resize(width, height);
            }
            chain.setUniform("Amount", Math.min(1.0f, amount));
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            chain.process(mc.getTimer().getGameTimeDeltaTicks());
        } catch (Exception e) {
            failed = true;
            LOGGER.warn("Cinematic moments: the colour drain post effect could not be loaded; skipping it", e);
            close();
        } finally {
            main.bindWrite(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        }
    }

    public static void close() {
        if (chain != null) {
            try {
                chain.close();
            } catch (Exception ignored) {
                // a half-built chain may not close cleanly
            }
        }
        chain = null;
    }

    /** A resource reload rebuilds the chain next time it is needed, and gives a failed load another chance. */
    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Reload {
        private Reload() {
        }

        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> {
                close();
                failed = false;
            });
        }
    }
}
