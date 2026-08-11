package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.GrindylowModel;
import com.hogwartsmagic.entity.GrindylowEntity;
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

public class GrindylowRenderer extends MobRenderer<GrindylowEntity, GrindylowModel> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/grindylow.png");

    /**
     * The model is built oversized so the head and teeth have pixels to spare;
     * this brings it back to something that hides in a weed bed.
     */
    private static final float SCALE = 0.7F;

    public GrindylowRenderer(EntityRendererProvider.Context context) {
        super(context, new GrindylowModel(context.bakeLayer(GrindylowModel.LAYER_LOCATION)), 0.4F);
        this.addLayer(new EyesLayer(this));
    }

    @Override
    protected void scale(GrindylowEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(GrindylowEntity entity) {
        return TEXTURE;
    }

    /**
     * Two green eyes, lit at any light level.
     *
     * <p>They are the tell. A lurking grindylow is a motionless olive lump in a
     * weed bed and you will never pick it out by its outline — but a pair of
     * lit eyes in the dark is something a careful player can learn to spot
     * before the chest is opened.
     */
    private static class EyesLayer extends RenderLayer<GrindylowEntity, GrindylowModel> {
        private static final RenderType EYES = RenderType.eyes(
                ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/grindylow_eyes.png"));

        EyesLayer(RenderLayerParent<GrindylowEntity, GrindylowModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           GrindylowEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isDeadOrDying()) {
                return;
            }
            VertexConsumer consumer = buffer.getBuffer(EYES);
            this.getParentModel().renderToBuffer(poseStack, consumer, 15728640,
                    OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
    }
}
