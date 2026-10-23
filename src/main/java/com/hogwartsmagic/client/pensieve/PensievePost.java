package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.hogwartsmagic.client.pensieve.PensieveTimeline.*;

/**
 * The memory's colour grade: a post chain ({@code hogwartsmagic:shaders/post/pensieve.json}) over the main render
 * target, run by {@code PensieveGameRendererMixin} after the level and before the GUI. Uniforms: Desaturation,
 * Darkness (1 = black, about 0.6 = grey shadow shapes, 0 = the real thing), Tint (silver, vec3), Vignette and
 * RedEdge (a hit on the body). {@code Time} is fed by vanilla's PostPass itself.
 *
 * <p>If the chain fails to load (a driver that rejects the shader, a broken resource pack) it logs once and is
 * a no-op from then on; {@link #working()} turns false and {@code PensieveOverlay} draws a flat fallback for the
 * darkness and the red edge.
 */
public final class PensievePost {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID,
            "shaders/post/pensieve.json");
    private static final float[] SILVER = {0.93f, 0.97f, 1.06f};

    private static Chain chain;
    private static boolean failed;
    private static int width;
    private static int height;

    private PensievePost() {
    }

    /** False once the chain has failed to load: the overlay then stands in for it. */
    public static boolean working() {
        return !failed;
    }

    /** The grade is on while the memory itself is on screen. */
    public static boolean wanted() {
        return PensieveClientState.inMemory();
    }

    public static float desaturation(float partial) {
        int p = PensieveClientState.phase();
        if (p == PensieveClientState.BUILD || p == PensieveClientState.PLAY) {
            return revealDesaturation(PensieveClientState.setReveal(partial));
        }
        if (p == PensieveClientState.EXIT) {
            return lerp(DESAT_PLAY, DESAT_BUILD, exitDarkness(PensieveClientState.phaseTime(partial)));
        }
        return DESAT_PLAY;
    }

    public static float darkness(float partial) {
        int p = PensieveClientState.phase();
        // A chained scene's fade to black at its length holds through a hand-over, and through an exit after it.
        if (p == PensieveClientState.BUILD || p == PensieveClientState.PLAY) {
            // Only the black part: a white hand-over is drawn over the view by PensieveOverlay.
            return PensieveClientState.handoverCover(partial)[0];
        }
        if (p == PensieveClientState.EXIT) {
            return Math.max(exitDarkness(PensieveClientState.phaseTime(partial)),
                    PensieveClientState.chainEndDarkness(partial));
        }
        return 0;
    }

    public static float vignette(float partial) {
        return VIGNETTE_PLAY * (1 - darkness(partial)) + 0.25f * darkness(partial);
    }

    public static void process(float partial) {
        if (failed || !wanted()) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        try {
            if (chain == null) {
                chain = new Chain(mc.getTextureManager(), mc.getResourceManager(), main, CHAIN);
                width = -1;
            }
            if (main.width != width || main.height != height) {
                width = main.width;
                height = main.height;
                chain.resize(width, height);
            }
            chain.setUniform("Desaturation", desaturation(partial));
            chain.setUniform("Darkness", darkness(partial));
            chain.setUniform("Vignette", vignette(partial));
            chain.setUniform("RedEdge", PensieveClientState.thud(partial));
            chain.setTint(SILVER[0], SILVER[1], SILVER[2]);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            chain.process(mc.getTimer().getGameTimeDeltaTicks());
            main.bindWrite(true);
        } catch (Exception e) {
            fail(e);
        }
    }

    private static void fail(Exception e) {
        failed = true;
        LOGGER.warn("Pensieve: the memory post effect could not be loaded; using the plain overlay instead", e);
        close();
        try {
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        } catch (Exception ignored) {
            // nothing more to undo
        }
    }

    private static void close() {
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

    /**
     * PostChain keeps its passes private and only takes float uniforms by name. Overriding the public
     * {@code addPass} hands us each pass as the chain is built (from inside the super constructor, which is why
     * {@code passes} has no initializer: one would run after super() and wipe the list).
     */
    private static final class Chain extends PostChain {
        private List<PostPass> passes;

        Chain(TextureManager textures, ResourceProvider resources, RenderTarget target, ResourceLocation id)
                throws IOException {
            super(textures, resources, target, id);
        }

        @Override
        public PostPass addPass(String name, RenderTarget in, RenderTarget out, boolean linear) throws IOException {
            PostPass pass = super.addPass(name, in, out, linear);
            if (passes == null) passes = new ArrayList<>();
            passes.add(pass);
            return pass;
        }

        void setTint(float r, float g, float b) {
            if (passes == null) return;
            for (PostPass pass : passes) {
                pass.getEffect().safeGetUniform("Tint").set(r, g, b);
            }
        }
    }
}
