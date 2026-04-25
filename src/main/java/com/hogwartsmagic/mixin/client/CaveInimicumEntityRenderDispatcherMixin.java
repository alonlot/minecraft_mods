package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.CaveInimicumClientState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides entities inside active Cave Inimicum wards from viewers who are still
 * outside the ward.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class CaveInimicumEntityRenderDispatcherMixin {

    @Inject(
            method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void hogwartsmagic$hideWardedEntities(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                                  CallbackInfoReturnable<Boolean> cir) {
        EntityRenderDispatcher self = (EntityRenderDispatcher) (Object) this;
        EntityRenderer<? super Entity> renderer = self.getRenderer(entity);
        // Defensive guard: if an entity has no renderer mapping, avoid crashing the render thread.
        if (renderer == null) {
            cir.setReturnValue(false);
            return;
        }

        if (CaveInimicumClientState.shouldHideEntity(entity)) {
            cir.setReturnValue(false);
        }
    }
}
