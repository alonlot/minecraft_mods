package com.hogwartsmagic.spell.support.ceiling;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps Caelum Fascino's pending corners tidy: they glow and lapse on the server tick, and a logout or a server stop
 * forgets them (the marks live in a static, which an integrated server would otherwise carry into the next world).
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CaelumFascinoEvents {
    private CaelumFascinoEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        CaelumFascinoSpell.tickMarks(server, server.overworld().getGameTime());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CaelumFascinoSpell.clearMark(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CaelumFascinoSpell.clearAllMarks();
    }
}
