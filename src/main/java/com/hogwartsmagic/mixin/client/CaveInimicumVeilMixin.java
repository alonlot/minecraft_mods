package com.hogwartsmagic.mixin.client;

import com.hogwartsmagic.client.enchant.SpellEnchantClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cave Inimicum armour: the wearer's body is drawn faintly see-through. The body pass switches to the translucent
 * entity type vanilla uses for team-mates under Invisibility, and its colour gets the veil's alpha. Armour and held
 * items are other layers and stay solid.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class CaveInimicumVeilMixin {
    @Unique
    private float hogwartsmagic$veil = 1.0f;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void hogwartsmagic$veilStart(LivingEntity entity, float yaw, float partialTick, PoseStack pose,
                                         MultiBufferSource buffers, int light, CallbackInfo ci) {
        hogwartsmagic$veil = entity.isInvisible() ? 1.0f : SpellEnchantClient.veilAlpha(entity);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void hogwartsmagic$veilEnd(LivingEntity entity, float yaw, float partialTick, PoseStack pose,
                                       MultiBufferSource buffers, int light, CallbackInfo ci) {
        hogwartsmagic$veil = 1.0f;
    }

    @Inject(method = "getRenderType(Lnet/minecraft/world/entity/LivingEntity;ZZZ)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void hogwartsmagic$veilType(LivingEntity entity, boolean bodyVisible, boolean translucent, boolean glowing,
                                        CallbackInfoReturnable<RenderType> cir) {
        if (hogwartsmagic$veil < 1.0f && bodyVisible && !glowing) {
            cir.setReturnValue(RenderType.itemEntityTranslucentCull(
                    ((EntityRenderer<LivingEntity>) (Object) this).getTextureLocation(entity)));
        }
    }

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
            index = 4)
    private int hogwartsmagic$veilAlpha(int colour) {
        if (hogwartsmagic$veil >= 1.0f) {
            return colour;
        }
        int alpha = Math.round(((colour >>> 24) & 0xFF) * hogwartsmagic$veil);
        return (alpha << 24) | (colour & 0x00FFFFFF);
    }
}
