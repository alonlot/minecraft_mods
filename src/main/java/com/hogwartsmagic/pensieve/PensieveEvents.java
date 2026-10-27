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
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
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

    /**
     * The memory dimension holds players and nothing else. Rebuilt islands run their redstone and fire onPlace, so
     * a torch could fuse TNT or a piston could drop items there; they would outlive the session in a reused slot.
     * No server-side code puts an entity into the memory level (the scene's creatures are client-side puppets, and
     * a viewer's body stands in the real world), so everything but players and the body is refused.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && isMemoryLevel(event.getLevel())
                && !(event.getEntity() instanceof Player) && !(event.getEntity() instanceof PensieveBodyEntity)) {
            event.setCanceled(true);
        }
    }

    @org.jetbrains.annotations.Nullable
    private static volatile net.minecraft.world.level.Level testMemory;

    /** Gametests: treat this level as the memory dimension (the test server has none). Null restores. */
    public static void overrideMemoryForTests(@org.jetbrains.annotations.Nullable net.minecraft.world.level.Level level) {
        testMemory = level;
    }

    private static boolean isMemoryLevel(net.minecraft.world.level.Level level) {
        return PensieveDimension.isMemory(level) || level == testMemory;
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new MemoryRegistry());
    }

    /**
     * Nothing hurts a viewer except the damage their own body forwards (see PensieveSessions#forwardDamage), and
     * {@code /kill}: an admin must always be able to get a player out. The death path sends them home first.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttacked(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player p && PensieveSessions.isViewer(p) && !PensieveSessions.isForwarding(p)
                && !event.getSource().is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) {
            event.setCanceled(true);
        }
    }

    /** A viewer dying (from the damage of the body) goes home first, so the loot drops there, not in the memory. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onViewerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && PensieveSessions.isViewer(p)) {
            PensieveSessions.onViewerDeath(p);
        }
    }

    /**
     * However a viewer died, the respawned player must not carry a return tag back into the memory dimension.
     * Runs last: other Clone handlers copy the old persistent data over the new player.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer p) {
            PensieveSessions.clearReturnTagAfterDeath(p, (ServerPlayer) event.getOriginal());
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
