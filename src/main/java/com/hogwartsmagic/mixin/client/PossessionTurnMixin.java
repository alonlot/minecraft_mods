package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.PossessionClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The mouse turns the local player through Entity#turn. While possessing, the turn steers the caster's
 * virtual look (the victim's eyes) and the frozen body stays put; while possessed, the player's own mouse
 * does nothing. Client-list mixin: never applied on a dedicated server.
 */
@Mixin(Entity.class)
public abstract class PossessionTurnMixin {

    @Inject(method = "turn(DD)V", at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$possessionTurn(double yaw, double pitch, CallbackInfo ci) {
        if (PossessionClient.interceptTurn((Entity) (Object) this, yaw, pitch)) {
            ci.cancel();
        }
    }
}
