package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.CastGestures;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Poses the wand arm of a casting player in third person. Runs at the end of HumanoidModel.setupAnim, so
 * after vanilla's arm poses and attack swing, and before PlayerModel copies the arms onto the sleeves.
 */
@Mixin(HumanoidModel.class)
public abstract class WandGestureModelMixin<T extends LivingEntity> {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void hogwartsmagic$wandGesture(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                           float netHeadYaw, float headPitch, CallbackInfo ci) {
        CastGestures.applyThirdPerson((HumanoidModel<?>) (Object) this, entity, ageInTicks);
    }
}
