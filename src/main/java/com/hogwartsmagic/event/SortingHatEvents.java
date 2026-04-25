package com.hogwartsmagic.event;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.sorting.SortingHatManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SortingHatEvents {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }

        SortingHatManager.get().maybeStartSession(player);
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        boolean consumed = SortingHatManager.get().handleChat(event.getPlayer(), event.getRawText());
        if (consumed) {
            event.setCanceled(true);
        }
    }
}
