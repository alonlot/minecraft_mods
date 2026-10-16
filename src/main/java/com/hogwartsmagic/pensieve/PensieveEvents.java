package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.ExampleMod;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus wiring for the Pensieve. Common code only, so it is safe on a dedicated server. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PensieveEvents {
    private PensieveEvents() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new MemoryRegistry());
    }
}
