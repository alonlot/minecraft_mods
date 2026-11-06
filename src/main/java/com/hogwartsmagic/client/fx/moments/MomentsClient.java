package com.hogwartsmagic.client.fx.moments;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.fx.CameraShake;
import com.hogwartsmagic.client.fx.Cinematic;
import com.hogwartsmagic.client.fx.FxClient;
import com.hogwartsmagic.client.fx.FxOverlays;
import com.hogwartsmagic.client.pensieve.PensieveClientState;
import com.hogwartsmagic.fx.FxConfig;
import com.hogwartsmagic.fx.FxKinds;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Wires the big moments together. A {@code moment} effect from the server (a boss falling, a Killing Curse
 * landing, a Death Eater cut down) arrives with a weight from 0 to 1 and an optional flash colour, and becomes:
 * <ul>
 * <li>about 1: a three-frame freeze on the blow, a bright flash, the colour draining out of the world for three
 * seconds, letterbox bars, the world slowing to a crawl (single player) and a heavy camera shake;</li>
 * <li>about 0.5: a two-frame freeze, a flash and a short, partial drain, with a lighter shake;</li>
 * <li>small: a flash on its own.</li>
 * </ul>
 * This class also runs the per-frame hooks the effects need: the colour-drain pass and the freeze capture once the
 * level is drawn, and the slow-motion clock.
 */
public final class MomentsClient {

    private MomentsClient() {
    }

    /** params: weight (0..1), then optionally flash red, green, blue (0..1; white if absent). */
    private static void onMoment(FxClient.FxEvent e) {
        if (!FxConfig.get(FxConfig.CINEMATIC_MOMENTS) || PensieveClientState.inMemory()) {
            return;
        }
        float weight = Math.max(0.0f, Math.min(1.0f, e.param(0, 1.0f)));
        float r = e.param(1, 1.0f);
        float g = e.param(2, 1.0f);
        float b = e.param(3, 1.0f);
        if (weight >= 0.8f) {
            Cinematic.freeze(3);
            Cinematic.flash(r, g, b, 0.9f, 14);
            Cinematic.drain(0.85f, 60);
            Cinematic.letterbox(70);
            Cinematic.slowMotion(0.3f, 30);
            CameraShake.add(e.pos(), 2.6f, 64.0f, 44.0f, 1.0f);
        } else if (weight >= 0.35f) {
            Cinematic.freeze(2);
            Cinematic.flash(r, g, b, 0.7f, 10);
            Cinematic.drain(0.4f, 30);
            CameraShake.add(e.pos(), 1.2f, 40.0f, 24.0f, 1.0f);
        } else {
            Cinematic.flash(r, g, b, 0.3f + weight * 0.6f, 8);
        }
    }

    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            FxClient.register(FxKinds.MOMENT, MomentsClient::onMoment);
            FxOverlays.register(FxOverlays.ORDER_FLASH - 1, "moment_freeze", FreezeFrame::render);
            FxOverlays.register(FxOverlays.ORDER_FLASH, "moment_flash", Cinematic::renderFlashes);
            FxOverlays.register(FxOverlays.ORDER_LETTERBOX, "moment_letterbox", Cinematic::renderBars);
        }
    }

    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
    public static final class Hooks {
        private Hooks() {
        }

        /** The last thing the level render does: grey the world if a drain is running, then take a freeze picture. */
        @SubscribeEvent
        public static void onLevelStage(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
                return;
            }
            float amount = Cinematic.drainAmount();
            if (amount > 0.0f) {
                DrainPost.process(amount);
            }
            FreezeFrame.captureIfWanted();
        }

        /** The slow-motion clock follows real frames, because game ticks slow down with the world. */
        @SubscribeEvent
        public static void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                SlowMotion.frame();
            }
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            Cinematic.clear();
        }
    }
}
