package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.MindControlOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge 51 has no HUD event; draw the mind-control overlay over the finished HUD (same as BasiliskGazeGuiMixin). */
@Mixin(Gui.class)
public abstract class MindControlGuiMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL"))
    private void hogwartsmagic$renderMindControl(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        MindControlOverlay.render(guiGraphics);
    }
}
