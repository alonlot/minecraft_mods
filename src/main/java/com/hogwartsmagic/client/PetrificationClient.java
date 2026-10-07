package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.petrification.Petrification;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Client half of basilisk stone, driven by the stone effect vanilla syncs to the local player. Movement and clicks
 * are already locked by StunInputLock (stone counts as stunned). This adds the look lock (through
 * PossessionTurnMixin), drains the drop, swap-hands and inventory keys, refuses container screens and the spell
 * menus, opens the choice screen when the stone begins (once no other screen is open), closes it when the stone
 * ends, reopens it on the give-in key, and draws the HUD line (through BasiliskGazeGuiMixin).
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class PetrificationClient {
    /** J by default: G is the Grimoire key. Registered in ClientModEvents.onRegisterKeyMappings. */
    public static final KeyMapping GIVE_IN_KEY = new KeyMapping(
            "key.hogwartsmagic.give_in",
            GLFW.GLFW_KEY_J,
            "key.categories.hogwartsmagic"
    );
    private static final int HUD_COLOR = 0xD0D0D0;

    private static boolean wasPetrified;
    private static boolean openPending;

    private PetrificationClient() {
    }

    public static boolean isLocalPetrified() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && Petrification.isPetrified(player);
    }

    /** Called from PossessionTurnMixin: the stone local player's mouse does not turn them. */
    public static boolean blocksTurn(Entity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && entity == player && Petrification.isPetrified(player);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean petrified = isLocalPetrified();
        if (event.phase == TickEvent.Phase.START) {
            if (petrified) {
                drain(minecraft.options.keyDrop);
                drain(minecraft.options.keySwapOffhand);
                drain(minecraft.options.keyInventory);
            }
            return;
        }
        if (petrified && !wasPetrified) {
            openPending = true;
        }
        if (!petrified) {
            openPending = false;
            if (wasPetrified && minecraft.screen instanceof PetrifiedChoiceScreen) {
                minecraft.setScreen(null);
            }
        }
        wasPetrified = petrified;
        while (GIVE_IN_KEY.consumeClick()) {
            if (petrified && minecraft.screen == null) {
                openPending = true;
            }
        }
        if (openPending && minecraft.screen == null && minecraft.player != null) {
            openPending = false;
            minecraft.setScreen(new PetrifiedChoiceScreen());
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (isLocalPetrified() && (event.getNewScreen() instanceof AbstractContainerScreen<?>
                || event.getNewScreen() instanceof WandRadialMenuScreen
                || event.getNewScreen() instanceof SpellMenuScreen)) {
            event.setCanceled(true);
        }
    }

    /** The HUD line, drawn while no screen is open. */
    public static void renderHud(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.screen != null || !isLocalPetrified()) {
            return;
        }
        Component line = Component.translatable("gui.hogwartsmagic.petrified.hud", GIVE_IN_KEY.getTranslatedKeyMessage());
        graphics.drawCenteredString(minecraft.font, line, graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 24, HUD_COLOR);
    }

    private static void drain(KeyMapping key) {
        while (key.consumeClick()) {
            // swallowed: stone does not drop, swap or open its pockets
        }
    }
}
