package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.BasiliskModel;
import com.hogwartsmagic.entity.BasiliskEntity;
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

public class BasiliskRenderer extends MobRenderer<BasiliskEntity, BasiliskModel> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/basilisk.png");

    public BasiliskRenderer(EntityRendererProvider.Context context) {
        // The model is built at true scale — 16 px to the block — so it renders
        // at 1.0 and the shadow is sized to the swell of the body, not the hitbox.
        super(context, new BasiliskModel(context.bakeLayer(BasiliskModel.LAYER_LOCATION)), 1.6F);
        this.addLayer(new EyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(BasiliskEntity entity) {
        return TEXTURE;
    }

    /**
     * The killing eyes, lit whatever the light level. Dropping this layer is
     * what makes a blinded basilisk read as blinded: the base skin underneath
     * carries the gouged socket.
     */
    private static class EyesLayer extends RenderLayer<BasiliskEntity, BasiliskModel> {
        private static final RenderType EYES = RenderType.eyes(
                ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/basilisk_eyes.png"));

        EyesLayer(RenderLayerParent<BasiliskEntity, BasiliskModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           BasiliskEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.isBlinded() || entity.isFullyBurrowed()) {
                return;
            }
            VertexConsumer consumer = buffer.getBuffer(EYES);
            this.getParentModel().renderToBuffer(poseStack, consumer, 15728640,
                    OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
    }
}
