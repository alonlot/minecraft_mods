package com.hogwartsmagic.mixin;

import com.hogwartsmagic.pensieve.capture.CaptureHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the Pensieve recorder an entity swung an arm. Common mixin; the hook ignores client entities. */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingCaptureMixin {

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
    private void hogwartsmagic$captureSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        CaptureHooks.onSwing((LivingEntity) (Object) this, hand);
    }
}
