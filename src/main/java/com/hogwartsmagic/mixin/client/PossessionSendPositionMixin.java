package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.PossessionClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * With the camera on the victim, isControlledCamera() is false and vanilla stops sending the caster's own
 * position, so an airborne caster would be kicked for flying. Inside sendPosition only, a possessing caster
 * still counts as controlling the camera; isControlledCamera itself is untouched (serverAiStep relies on it
 * to keep the caster's input off the body). Client-list mixin.
 */
@Mixin(LocalPlayer.class)
public abstract class PossessionSendPositionMixin {

    @Shadow
    protected abstract boolean isControlledCamera();

    @Redirect(method = "sendPosition", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isControlledCamera()Z"))
    private boolean hogwartsmagic$sendPositionWhilePossessing(LocalPlayer self) {
        return PossessionClient.isCaster() || this.isControlledCamera();
    }
}
