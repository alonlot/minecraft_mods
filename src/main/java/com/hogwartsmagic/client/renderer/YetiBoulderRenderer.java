package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.entity.YetiBoulderEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;

/** The thrown boulder: a tumbling lump of packed snow, about two thirds of a block across. */
public class YetiBoulderRenderer extends EntityRenderer<YetiBoulderEntity> {
    private static final float SIZE = 0.65F;
    private final BlockRenderDispatcher blocks;

    public YetiBoulderRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.3F;
    }

    @Override
    public void render(YetiBoulderEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight) {
        pose.pushPose();
        float spin = (entity.tickCount + partialTick) * 14.0F;
        pose.translate(0.0F, SIZE * 0.5F, 0.0F);
        pose.mulPose(Axis.XP.rotationDegrees(spin));
        pose.mulPose(Axis.ZP.rotationDegrees(spin * 0.6F));
        pose.scale(SIZE, SIZE, SIZE);
        pose.translate(-0.5F, -0.5F, -0.5F);
        blocks.renderSingleBlock(Blocks.SNOW_BLOCK.defaultBlockState(), pose, buffers, packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(YetiBoulderEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
