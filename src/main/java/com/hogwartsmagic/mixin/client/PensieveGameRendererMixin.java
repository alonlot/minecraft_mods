package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.pensieve.PensievePost;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the Pensieve memory grade after the level (and its entity outlines) is drawn and before vanilla's own
 * post effect and the GUI. Client-list mixin: never applied on a dedicated server.
 */
@Mixin(GameRenderer.class)
public abstract class PensieveGameRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V",
                    shift = At.Shift.AFTER))
    private void hogwartsmagic$pensievePost(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        PensievePost.process(deltaTracker.getGameTimeDeltaPartialTick(true));
    }
}
