package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.BiomeFogClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The sun, moon and stars fade behind real fog the way they fade behind rain.
 *
 * <p>{@code renderSky} draws them with alpha {@code 1 - rainLevel} (and the stars with that on top of their own
 * brightness); that is its only {@code getRainLevel} call, so reporting {@code max(rain, fog veil)} there veils
 * all three and nothing else. The real rain level - sound, particles, sky darkening - is untouched.
 * Client-list mixin: never applied on a dedicated server.
 */
@Mixin(LevelRenderer.class)
public abstract class SkyFogVeilMixin {

    @Redirect(method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private float hogwartsmagic$fogVeilsSky(ClientLevel level, float partialTick) {
        return Math.max(level.getRainLevel(partialTick), BiomeFogClient.skyVeil());
    }
}
