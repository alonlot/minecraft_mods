package com.hogwartsmagic.cosy.client.card;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * MOD bus, client only (a top-level class: nested mod-bus subscribers have gone unheard in this mod). A resource
 * reload drops the cached art lookups and the uploaded clip frames.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CardClientSetup {
    private CardClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            CardAssets.invalidate();
            CardVideo.releaseAll();
            CardArt.releaseAll();
        });
    }
}
