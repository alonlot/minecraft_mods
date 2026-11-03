package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.BasiliskGazeOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the basilisk's stare over the finished HUD.
 *
 * <p>This is a mixin rather than an event listener because Forge 51 has no HUD
 * hook at all: {@code RenderGuiEvent} and {@code RegisterGuiOverlaysEvent} were
 * both removed in 1.20.5 when Mojang introduced the layered draw system, and
 * Forge never replaced them. Injecting at the tail of {@link Gui#render} puts
 * the overlay above every vanilla layer, which is what we want — a player going
 * to stone should not have a clear view of their own hotbar.
 */
@Mixin(Gui.class)
public abstract class BasiliskGazeGuiMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL"))
    private void hogwartsmagic$renderBasiliskGaze(GuiGraphics guiGraphics, DeltaTracker deltaTracker,
                                                  CallbackInfo ci) {
        BasiliskGazeOverlay.render(guiGraphics);
        com.hogwartsmagic.client.PetrificationClient.renderHud(guiGraphics);
        com.hogwartsmagic.client.DementorDread.render(guiGraphics);
        com.hogwartsmagic.client.TravelFxClient.render(guiGraphics);
        com.hogwartsmagic.client.AvadaChargeOverlay.render(guiGraphics);
    }
}
