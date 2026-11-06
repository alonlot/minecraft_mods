package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.mastery.MasteryConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only: state synced or timed by the last server (mastery config, Revelio reveals) must not leak into the next world. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class MasteryConfigClientEvents {
    private MasteryConfigClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MasteryConfig.clearSynced();
        PolyjuiceClientState.clearAll();
        WerewolfPlayerClientState.clearAll();
        DarkMagicClientState.clear();
        // The fog region is only ever cleared by the server's exit packet, which never comes after a logout.
        UndercroftFogClientState.clear();
        FiendfyreSerpentRenderer.clearTrails();
    }
}
