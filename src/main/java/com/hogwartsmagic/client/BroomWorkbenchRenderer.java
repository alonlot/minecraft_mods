package com.hogwartsmagic.client;

import com.hogwartsmagic.block.entity.BroomWorkbenchBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class BroomWorkbenchRenderer implements BlockEntityRenderer<BroomWorkbenchBlockEntity> {

    public BroomWorkbenchRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(BroomWorkbenchBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (be.getLevel() == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long gameTime = be.getLevel().getGameTime();
        boolean infusing = be.isInfusing();
        float bobFreq = infusing ? 0.14f : 0.07f;
        float bobAmp = infusing ? 0.05f : 0.032f;
        float bob = Mth.sin((gameTime + partialTick) * bobFreq) * bobAmp;
        float spinSpeed = infusing ? 0.14f : 0.035f;
        float angle = (gameTime + partialTick) * spinSpeed;

        pose.pushPose();
        pose.translate(0.5, 0.74, 0.5);

        ItemStack result = be.getInventory().getStackInSlot(BroomWorkbenchBlockEntity.SLOT_RESULT);
        if (!result.isEmpty()) {
            pose.pushPose();
            pose.scale(0.82f, 0.82f, 0.82f);
            pose.mulPose(Axis.YP.rotationDegrees(angle * 14f));
            mc.getItemRenderer().renderStatic(result, ItemDisplayContext.FIXED, packedLight, packedOverlay, pose, buffer, be.getLevel(), be.getBlockPos().hashCode());
            pose.popPose();
            pose.popPose();
            return;
        }

        float[] xOffsets = {-0.32f, -0.1f, 0.1f, 0.32f};
        for (int i = 0; i < 4; i++) {
            ItemStack stack = be.getInventory().getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            pose.pushPose();
            float wobble = infusing ? Mth.sin((gameTime + partialTick) * 0.26f + i * 1.7f) * 0.022f : 0f;
            pose.translate(xOffsets[i] + wobble, bob * (infusing ? 1.25f : 1f), infusing ? Mth.cos((gameTime + partialTick) * 0.2f + i) * 0.018f : 0f);
            pose.scale(0.38f, 0.38f, 0.38f);
            pose.mulPose(Axis.YP.rotationDegrees(angle * (infusing ? 9f : 6f) + i * 35f));
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, pose, buffer, be.getLevel(), be.getBlockPos().hashCode() + i);
            pose.popPose();
        }
        pose.popPose();
    }
}
