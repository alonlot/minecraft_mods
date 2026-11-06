package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.fx.bloom.Bloom;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the mod's glowing batches draw into the bloom's glow buffer as well as the frame: {@link Bloom#capture} draws
 * the batch itself (twice, same state) and the vanilla draw is skipped; every other batch, and every batch outside the
 * level render, goes through untouched. Client-list mixin: never applied on a dedicated server.
 */
@Mixin(RenderType.class)
public abstract class BloomRenderTypeMixin {

    @Inject(method = "draw(Lcom/mojang/blaze3d/vertex/MeshData;)V", at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$captureGlow(MeshData mesh, CallbackInfo ci) {
        if (Bloom.capture((RenderType) (Object) this, mesh)) {
            ci.cancel();
        }
    }
}
