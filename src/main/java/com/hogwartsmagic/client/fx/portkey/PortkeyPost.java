package com.hogwartsmagic.client.fx.portkey;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.fx.bloom.ShaderPacks;
import com.hogwartsmagic.portkey.PortkeyTimeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

/**
 * The Portkey's tunnel on the traveller's own screen: a post chain ({@code hogwartsmagic:shaders/post/portkey.json})
 * over the main target, run once the level has been drawn and before the hand and the HUD, so the boot in the hand
 * stays still and sharp in the middle of a world being wrung into a spinning tunnel. During the charge the same
 * pass swirls and fringes the frame very slightly; at the hook it wrings it hard and blends into the tunnel; at the
 * far end it lets go and the new world unwinds.
 *
 * <p>Self-contained, as {@code moments.DrainPost}: its own chain and failure flag (logs once, then the HUD draws a
 * tunnel instead), skipped while a shader pack is in use. LOW priority at {@code AFTER_LEVEL}: after Bloom (which
 * still needs the world depth this pass's blit wipes) and after the colour drain. Every GL state it touches is put
 * back in {@code finally}: the main target bound for writing (which also restores its viewport), the depth test and
 * mask the level render left, and the blend state it found. A trip that ends early (logout, death, a dimension
 * change, a closed screen) simply stops calling it: the pass holds no state between frames but the chain itself.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class PortkeyPost {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID,
            "shaders/post/portkey.json");
    /** The shader's clock wraps here (seconds) so the floats never lose their fraction. */
    private static final double TIME_WRAP = 600.0;

    private static PostChain chain;
    private static boolean failed;
    private static int width = -1;
    private static int height = -1;
    private static float seed;

    private PortkeyPost() {
    }

    /** Whether the tunnel cannot be drawn by the shader (the HUD draws its own then). */
    public static boolean unavailable() {
        return failed || ShaderPacks.inUse();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        PortkeyTimeline.Frame f = PortkeyClient.current();
        if (f == null) {
            seed = (float) (Math.random() * 65536.0); // a new streak pattern for the next trip
            return;
        }
        if (f.mix() > 0.002f || f.twist() > 0.002f || Math.abs(f.pull()) > 0.002f || f.chroma() > 0.05f
                || f.glow() > 0.01f) {
            process(f, (float) ((PortkeyClient.now() / 20.0) % TIME_WRAP), seed);
        }
    }

    /** Run the pass for frame {@code f}. */
    static void process(PortkeyTimeline.Frame f, float timeSeconds, float seedValue) {
        if (unavailable()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
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
            chain.setUniform("PkTime", timeSeconds);
            chain.setUniform("Mix", Math.min(1.0f, f.mix()));
            chain.setUniform("Twist", f.twist());
            chain.setUniform("Pull", f.pull());
            chain.setUniform("Blur", f.blur());
            chain.setUniform("Chroma", f.chroma());
            chain.setUniform("Spin", f.spin());
            chain.setUniform("CentreX", f.centreX());
            chain.setUniform("CentreY", f.centreY());
            chain.setUniform("Glow", f.glow());
            chain.setUniform("Seed", seedValue);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            chain.process(mc.getTimer().getGameTimeDeltaTicks());
        } catch (Exception e) {
            failed = true;
            LOGGER.warn("Portkey: the tunnel post effect could not be loaded; the HUD draws it instead", e);
            close();
        } finally {
            main.bindWrite(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            if (blend) {
                RenderSystem.enableBlend();
            } else {
                RenderSystem.disableBlend();
            }
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

    /** A resource reload rebuilds the chain next time, and gives a failed load another chance. */
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
