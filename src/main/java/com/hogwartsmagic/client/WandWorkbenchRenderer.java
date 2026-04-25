package com.hogwartsmagic.client;

import com.hogwartsmagic.block.entity.WandWorkbenchBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class WandWorkbenchRenderer implements BlockEntityRenderer<WandWorkbenchBlockEntity> {

    public WandWorkbenchRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(WandWorkbenchBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (be.getLevel() == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long gameTime = be.getLevel().getGameTime();
        boolean crafting = be.isCrafting();
        float bobFreq = crafting ? 0.16f : 0.08f;
        float bobAmp = crafting ? 0.055f : 0.04f;
        float bob = Mth.sin((gameTime + partialTick) * bobFreq) * bobAmp;
        float spinSpeed = crafting ? 0.17f : 0.04f;
        float angle = (gameTime + partialTick) * spinSpeed;
        float windWobble = crafting ? Mth.sin((gameTime + partialTick) * 0.24f) * 0.035f : 0f;
        float windWobbleZ = crafting ? Mth.cos((gameTime + partialTick) * 0.21f) * 0.03f : 0f;

        pose.pushPose();
        pose.translate(0.5 + windWobble, 0.78, 0.5 + windWobbleZ);

        ItemStack result = be.getInventory().getStackInSlot(WandWorkbenchBlockEntity.SLOT_RESULT);
        if (!result.isEmpty()) {
            pose.pushPose();
            pose.scale(0.85f, 0.85f, 0.85f);
            pose.mulPose(Axis.YP.rotationDegrees(angle * (crafting ? 12f : 12f)));
            mc.getItemRenderer().renderStatic(result, ItemDisplayContext.FIXED, packedLight, packedOverlay, pose, buffer, be.getLevel(), be.getBlockPos().hashCode());
            pose.popPose();
            pose.popPose();
            return;
        }

        float[] xOffsets = {-0.28f, 0.0f, 0.28f};
        for (int i = 0; i < 3; i++) {
            ItemStack stack = be.getInventory().getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            pose.pushPose();
            float bobScale = crafting ? 1.35f : 1.0f;
            float ixWobble = crafting ? Mth.sin((gameTime + partialTick) * 0.28f + i) * 0.025f : 0f;
            pose.translate(xOffsets[i] + ixWobble, bob * bobScale, crafting ? Mth.cos((gameTime + partialTick) * 0.22f + i * 2f) * 0.022f : 0f);
            pose.scale(0.45f, 0.45f, 0.45f);
            pose.mulPose(Axis.YP.rotationDegrees(angle * (crafting ? 10f : 8f) + i * 40f));
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, pose, buffer, be.getLevel(), be.getBlockPos().hashCode() + i);
            pose.popPose();
        }
        pose.popPose();
    }
}
