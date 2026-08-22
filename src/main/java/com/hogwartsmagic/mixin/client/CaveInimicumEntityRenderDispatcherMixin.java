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
 * Hides entities inside a concealed region from viewers who are outside it.
 *
 * <p>Two things use this: Cave Inimicum's wards, and Diagon Alley. Blocks can be withheld from
 * a player -- the server sends them different ones -- but entities are tracked and broadcast to
 * everybody nearby with no per-player filter, so the only place to stop them is here, on the
 * client, at the moment something would have been drawn.
 *
 * <p>Which means it is render suppression rather than concealment: the entities are still sent,
 * and a modified client would still see them.
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

        // Diagon Alley uses the same trick as Cave Inimicum, and shares this one injection
        // rather than adding a second mixin to the same method.
        if (CaveInimicumClientState.shouldHideEntity(entity)
                || com.hogwartsmagic.client.DiagonAlleyClientState.shouldHideEntity(entity)) {
            cir.setReturnValue(false);
        }
    }
}
