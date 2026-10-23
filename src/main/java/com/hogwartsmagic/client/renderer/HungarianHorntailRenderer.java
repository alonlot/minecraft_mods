package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.HungarianHorntailModel;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class HungarianHorntailRenderer extends MobRenderer<HungarianHorntailEntity, HungarianHorntailModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/hungarian_horntail.png");

    public HungarianHorntailRenderer(EntityRendererProvider.Context context) {
        this(context, new HungarianHorntailModel(context.bakeLayer(HungarianHorntailModel.LAYER_LOCATION)));
    }

    /** With a model of its own (the memory dragon's blends its poses: client/MemoryHorntailRenderer). */
    protected HungarianHorntailRenderer(EntityRendererProvider.Context context, HungarianHorntailModel model) {
        super(context, model, 1.6F);
        this.addLayer(new EyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(HungarianHorntailEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(HungarianHorntailEntity entity, PoseStack poseStack, float partialTickTime) {
        // The model's ground plane sits at the renderer's scale pivot, so the feet stay planted at
        // any scale — this only controls how big the dragon reads next to the player.
        poseStack.scale(1.15F, 1.15F, 1.15F);
    }

    /** Burning amber eyes that stay lit in the dark, the way the film dragon's do. */
    private static class EyesLayer extends RenderLayer<HungarianHorntailEntity, HungarianHorntailModel> {
        private static final RenderType EYES = RenderType.eyes(
                ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/hungarian_horntail_eyes.png"));

        EyesLayer(RenderLayerParent<HungarianHorntailEntity, HungarianHorntailModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           HungarianHorntailEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            VertexConsumer consumer = buffer.getBuffer(EYES);
            this.getParentModel().renderToBuffer(poseStack, consumer, 15728640,
                    OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
    }
}
