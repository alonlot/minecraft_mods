package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.MerpersonModel;
import com.hogwartsmagic.entity.MerpersonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class MerpersonRenderer extends MobRenderer<MerpersonEntity, MerpersonModel> {

    private static final ResourceLocation COMMON =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/merperson.png");
    private static final ResourceLocation CHIEFTAINESS =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/merchieftainess.png");

    public MerpersonRenderer(EntityRendererProvider.Context context) {
        // Built at true scale, so 1.0. The shadow is small because a merperson is
        // almost never actually standing on anything.
        super(context, new MerpersonModel(context.bakeLayer(MerpersonModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new EyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(MerpersonEntity entity) {
        return entity.isChieftainess() ? CHIEFTAINESS : COMMON;
    }

    @Override
    protected void scale(MerpersonEntity entity, PoseStack poseStack, float partialTick) {
        if (entity.isChieftainess()) {
            poseStack.scale(1.15F, 1.15F, 1.15F);
        }
    }

    /**
     * The eyes, lit at any light level.
     *
     * <p>The Black Lake is pitch dark below about twenty blocks, and a merperson
     * with no lit eyes is an invisible grey shape in black water. Two yellow
     * slits coming out of the murk is the whole effect.
     */
    private static class EyesLayer extends RenderLayer<MerpersonEntity, MerpersonModel> {
        private static final RenderType COMMON_EYES = RenderType.eyes(
                ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/merperson_eyes.png"));
        private static final RenderType CHIEFTAINESS_EYES = RenderType.eyes(
                ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/merchieftainess_eyes.png"));

        EyesLayer(RenderLayerParent<MerpersonEntity, MerpersonModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           MerpersonEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isDeadOrDying()) {
                return;
            }
            VertexConsumer consumer = buffer.getBuffer(
                    entity.isChieftainess() ? CHIEFTAINESS_EYES : COMMON_EYES);
            this.getParentModel().renderToBuffer(poseStack, consumer, 15728640,
                    OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
    }
}
