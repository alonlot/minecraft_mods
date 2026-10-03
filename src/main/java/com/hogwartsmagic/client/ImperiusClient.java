package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.mind.MindControl;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Client half of Imperio thralls. Nameplates ("◆ Thrall of <owner>") come from ThrallLabelPacket. A
 * controlled local player (the imperius effect vanilla syncs to them) has their own controls taken away:
 * movement input is zeroed while they are a thrall (the server walks them), and attack, use, drop, hand
 * swap, hotbar, inventory, containers and the spell menus do nothing. The server refuses all of it on its
 * own too (MindControl); this only keeps the client from fighting it.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class ImperiusClient {
    private static final Map<Integer, String> LABELS = new HashMap<>();

    private ImperiusClient() {
    }

    public static void acceptLabel(SpellPackets.ThrallLabelPacket packet) {
        if (packet.ownerName().isEmpty()) {
            LABELS.remove(packet.entityId());
        } else {
            LABELS.put(packet.entityId(), packet.ownerName());
        }
    }

    public static boolean isLocalPlayerLocked() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && MindControl.isControlled(player);
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        String owner = LABELS.get(event.getEntity().getId());
        if (owner == null) {
            return;
        }
        if (event.getEntity().isInvisible() || event.getEntity().isCrouching()) {
            return;
        }
        event.setContent(Component.translatable("gui." + ExampleMod.MODID + ".imperio.thrall_label", owner)
                .withStyle(ChatFormatting.GREEN));
        event.setResult(Event.Result.ALLOW);
    }

    /** Thralls only: the server drives the body. (A Phase 6b puppet's input is set by PossessionClient instead.) */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!MindControl.isControlled(event.getEntity())) {
            return;
        }
        Input input = event.getInput();
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (isLocalPlayerLocked()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** Runs before vanilla handles key presses this tick, so drained presses never reach it. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END) {
            if (minecraft.player != null && minecraft.player.tickCount % 100 == 0) {
                LABELS.keySet().removeIf(id -> minecraft.level == null || minecraft.level.getEntity(id) == null);
            }
            return;
        }
        if (!isLocalPlayerLocked()) {
            return;
        }
        Options options = minecraft.options;
        drain(options.keyDrop);
        drain(options.keyInventory);
        drain(options.keySwapOffhand);
        for (KeyMapping hotbar : options.keyHotbarSlots) {
            drain(hotbar);
        }
    }

    private static void drain(KeyMapping key) {
        while (key.consumeClick()) {
            // swallowed: a controlled body doesn't answer its owner's keys
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (isLocalPlayerLocked() && (event.getNewScreen() instanceof AbstractContainerScreen<?>
                || event.getNewScreen() instanceof WandRadialMenuScreen
                || event.getNewScreen() instanceof SpellMenuScreen)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (isLocalPlayerLocked() && Minecraft.getInstance().screen == null) {
            event.setCanceled(true);
        }
    }

    /** The entity is gone from the client world (death, unload, portal): its nameplate goes with it. */
    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            LABELS.remove(event.getEntity().getId());
        }
    }

    /** A respawn keeps the entity id of the new local player; nothing from before it may stay labelled. */
    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
        LABELS.clear();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        LABELS.clear();
    }
}
