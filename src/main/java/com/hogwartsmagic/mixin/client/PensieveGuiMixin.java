package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.pensieve.PensieveClientState;
import com.hogwartsmagic.client.pensieve.PensieveOverlay;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the local player is in a Pensieve dive the vanilla HUD (hotbar, hearts, crosshair, the other mods'
 * overlays) is replaced by the dive's own screen layers, with only chat kept on top. Forge 51 has no HUD event
 * (see BasiliskGazeGuiMixin), so this is a cancellable injection at the head of {@link Gui#render}.
 */
@Mixin(Gui.class)
public abstract class PensieveGuiMixin {

    @Shadow
    private void renderChat(GuiGraphics graphics, DeltaTracker deltaTracker) {
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$pensieveHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!PensieveClientState.hidesHud()) {
            return;
        }
        RenderSystem.enableDepthTest();
        PensieveOverlay.render(graphics, deltaTracker.getGameTimeDeltaPartialTick(true));
        if (!Minecraft.getInstance().options.hideGui) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            this.renderChat(graphics, deltaTracker);
            graphics.pose().popPose();
        }
        RenderSystem.disableDepthTest();
        ci.cancel();
    }
}
