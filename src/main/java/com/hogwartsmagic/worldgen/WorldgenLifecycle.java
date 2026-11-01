package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.worldgen.undercroft.UndercroftBuilder;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Clears the static over-ticks build state when the server goes away.
 *
 * <p>Every builder in this package keeps its in-progress job in a static field, and each job holds
 * the {@link net.minecraft.server.level.ServerLevel} it is writing into. (Mini Hogwarts is no longer one
 * of them: it is a worldgen structure now, with no job of its own.) Nothing cleared those
 * fields when the server stopped, so in singleplayer a castle, bank, alley or undercroft that was
 * still going up when the player left the world kept its job armed: the next world's first ticks
 * would carry on writing into the previous world's (unloaded) level object, and that level and
 * everything hanging off it could never be collected.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorldgenLifecycle {

    private WorldgenLifecycle() {
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DiagonAlleyManager.reset();
        VillageGringottsBank.reset();
        UndercroftBuilder.reset();
        DiagonAlleyPortal.reset();
    }
}
