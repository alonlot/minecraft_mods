package com.hogwartsmagic.cosy.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.cosy.ChocolateFrogEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Draws the chocolate frog. It is a plain entity, so this is an {@link EntityRenderer}, not a living renderer. */
public class ChocolateFrogRenderer extends EntityRenderer<ChocolateFrogEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/chocolate_frog.png");

    private final ChocolateFrogModel model;

    public ChocolateFrogRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new ChocolateFrogModel(context.bakeLayer(ChocolateFrogModel.LAYER_LOCATION));
        this.shadowRadius = 0.15F;
    }

    @Override
    public void render(ChocolateFrogEntity frog, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, frog.yRotO, frog.getYRot())));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        this.model.setupAnim(frog, 0.0F, 0.0F, frog.tickCount + partialTick, 0.0F, 0.0F);
        VertexConsumer vertices = buffer.getBuffer(this.model.renderType(TEXTURE));
        this.model.renderToBuffer(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY, -1);
        poseStack.popPose();
        super.render(frog, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ChocolateFrogEntity frog) {
        return TEXTURE;
    }
}
