package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.pensieve.PensieveVoid;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A memory floats in a shadow void: no sky, sun, moon or stars, no clouds and no rain or snow while the Pensieve
 * void is on screen. Client-list mixin: never applied on a dedicated server.
 */
@Mixin(LevelRenderer.class)
public abstract class PensieveLevelRendererMixin {

    @Inject(method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
            at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$pensieveNoSky(CallbackInfo ci) {
        if (PensieveVoid.hideSky()) ci.cancel();
    }

    @Inject(method = "renderClouds(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V",
            at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$pensieveNoClouds(CallbackInfo ci) {
        if (PensieveVoid.hideSky()) ci.cancel();
    }

    @Inject(method = "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
            at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$pensieveNoWeather(CallbackInfo ci) {
        if (PensieveVoid.hideSky()) ci.cancel();
    }
}
