package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.pensieve.PensieveCamera;
import com.hogwartsmagic.client.pensieve.PensieveCameraAccess;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Pensieve's directed camera. After vanilla has put the camera at the player's eye, PensieveCamera may move
 * and turn it (the swallow into the basin, the scene's shots, the yank). Roll and fov go through the Forge
 * viewport events instead. Client-list mixin: never applied on a dedicated server.
 */
@Mixin(Camera.class)
public abstract class PensieveCameraMixin implements PensieveCameraAccess {

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At("TAIL"))
    private void hogwartsmagic$pensieveCamera(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                                              float partial, CallbackInfo ci) {
        PensieveCamera.apply((Camera) (Object) this, partial);
    }

    @Override
    public void hogwartsmagic$setPosition(Vec3 position) {
        this.setPosition(position);
    }
}
