package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.InvisibilityCloakClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class InvisibilityCloakArmorLayerMixin<T extends LivingEntity, M extends HumanoidModel<T>, A extends HumanoidModel<T>> {

    @Unique
    private LivingEntity hogwartsmagic$currentEntity;

    @Unique
    private float hogwartsmagic$currentPartialTick;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"))
    private void hogwartsmagic$captureEntity(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T livingEntity,
                                             float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                                             float netHeadYaw, float headPitch, CallbackInfo ci) {
        this.hogwartsmagic$currentEntity = livingEntity;
        this.hogwartsmagic$currentPartialTick = partialTick;
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("RETURN"))
    private void hogwartsmagic$clearEntity(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T livingEntity,
                                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                                           float netHeadYaw, float headPitch, CallbackInfo ci) {
        this.hogwartsmagic$currentEntity = null;
        this.hogwartsmagic$currentPartialTick = 0.0F;
    }

    @Inject(method = "setPartVisibility", at = @At("TAIL"))
    private void hogwartsmagic$applyCloakMask(A model, EquipmentSlot slot, CallbackInfo ci) {
        if (!(hogwartsmagic$currentEntity instanceof AbstractClientPlayer clientPlayer)) {
            return;
        }
        if (!InvisibilityCloakClientState.isAnimating(clientPlayer)) {
            return;
        }
        InvisibilityCloakClientState.VisibilityMask mask =
                InvisibilityCloakClientState.getVisibilityMask(clientPlayer, hogwartsmagic$currentPartialTick);
        InvisibilityCloakClientState.applyToHumanoidArmorSlot(model, slot, mask);
    }
}
