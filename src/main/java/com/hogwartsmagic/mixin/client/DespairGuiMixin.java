package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.DespairOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the Drink of Despair over the HUD (Forge 51 has no HUD event; see BasiliskGazeGuiMixin). */
@Mixin(Gui.class)
public abstract class DespairGuiMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL"))
    private void hogwartsmagic$renderDespair(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        DespairOverlay.render(guiGraphics);
    }
}
