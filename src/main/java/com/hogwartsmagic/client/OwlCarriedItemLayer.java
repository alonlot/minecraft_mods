package com.hogwartsmagic.client;

import com.hogwartsmagic.entity.OwlEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class OwlCarriedItemLayer extends RenderLayer<OwlEntity, OwlModel<OwlEntity>> {
    public OwlCarriedItemLayer(RenderLayerParent<OwlEntity, OwlModel<OwlEntity>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, OwlEntity owl,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        ItemStack carried = owl.getCarriedMail();
        if (carried.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        this.getParentModel().body().translateAndRotate(poseStack);
        poseStack.translate(0.0D, 0.48D, 0.02D);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.scale(0.34F, 0.34F, 0.34F);
        net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(
                carried,
                ItemDisplayContext.FIXED,
                packedLight,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                owl.level(),
                owl.getId()
        );
        poseStack.popPose();
    }
}
