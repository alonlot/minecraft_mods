package com.hogwartsmagic.client.portrait;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.portrait.PortraitKind;
import com.hogwartsmagic.portrait.PortraitRegistry;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** MOD-bus, client only: the portrait renderer, and a reload listener that drops cached asset data. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PortraitClientSetup {
    private PortraitClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (PortraitKind kind : PortraitKind.ALL) {
            event.registerEntityRenderer(PortraitRegistry.typeOf(kind), context -> new PortraitRenderer(context, kind));
        }
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            PortraitAssets.invalidate();
            PortraitVideo.releaseAll();
        });
    }
}
