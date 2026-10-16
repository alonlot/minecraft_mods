package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.pensieve.MemoryActors;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pensieve puppet players are not in the connection's player list, so vanilla's skin lookup (which goes through
 * {@code getPlayerInfo()}) would find nothing. Their skin comes from the scene instead.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class ActorSkinMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$actorSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin skin = MemoryActors.skinOf((AbstractClientPlayer) (Object) this);
        if (skin != null) cir.setReturnValue(skin);
    }
}
