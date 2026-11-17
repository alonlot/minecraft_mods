package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.TenebrisView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge 51 has no HUD event: the Tenebris smoke edge is drawn first, under the rest of the HUD. */
@Mixin(Gui.class)
public abstract class TenebrisGuiMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"))
    private void hogwartsmagic$renderTenebris(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        TenebrisView.render(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false));
    }
}
