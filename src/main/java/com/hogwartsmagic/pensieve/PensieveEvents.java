package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge-bus wiring for the Pensieve. Common code only, so it is safe on a dedicated server. Viewers (players
 * inside a memory, see {@link PensieveSessions#isViewer}) are ghosts: nothing hurts them, and they touch nothing.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PensieveEvents {
    private PensieveEvents() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new MemoryRegistry());
    }

    /** Task 7: the body's forwarded damage must be let through here (and past the invulnerable flag). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttacked(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player p && PensieveSessions.isViewer(p)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(PlayerInteractEvent event) {
        if (event.isCancelable() && PensieveSessions.isViewer(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (PensieveSessions.isViewer(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player p && PensieveSessions.isViewer(p)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        if (PensieveSessions.isViewer(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPickup(EntityItemPickupEvent event) {
        if (PensieveSessions.isViewer(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** A cancelled toss deletes the stack, so it goes back into the inventory. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onToss(ItemTossEvent event) {
        Player p = event.getPlayer();
        if (PensieveSessions.isViewer(p)) {
            event.setCanceled(true);
            p.getInventory().placeItemBackInInventory(event.getEntity().getItem().copy());
        }
    }

    /** Viewers stay put: no portals or other mods' dimension moves. The session's own moves are let through. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof Player p && PensieveSessions.isViewer(p) && !PensieveSessions.isOwnMove()) {
            event.setCanceled(true);
        }
    }

    /** Inside a memory only {@code /pensieve} runs (so {@code /pensieve leave} always works), ops included. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        if (!(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer p)
                || !PensieveSessions.isViewer(p)) {
            return;
        }
        String line = event.getParseResults().getReader().getString().trim();
        if (line.startsWith("/")) line = line.substring(1);
        int space = line.indexOf(' ');
        String root = space < 0 ? line : line.substring(0, space);
        if (!root.equals("pensieve")) {
            event.setCanceled(true);
            p.sendSystemMessage(Component.translatable("message.hogwartsmagic.pensieve.no_commands"));
        }
    }
}
