package com.hogwartsmagic.cosy.client.card;

import com.hogwartsmagic.ExampleMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * FORGE bus, client only: opens queued reveals, lets go of idle clip frames about once a second, and forgets the
 * waiting reveals on logout. Exists only on a client, so a dedicated server never loads it.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CardClientEvents {
    private static int ticks;

    private CardClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        CardRevealClient.tick();
        if (++ticks % 20 == 0) {
            CardVideo.releaseIdle(System.nanoTime());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CardRevealClient.clear();
        CardVideo.releaseAll();
    }
}
