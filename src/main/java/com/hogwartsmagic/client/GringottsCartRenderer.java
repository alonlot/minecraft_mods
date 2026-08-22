package com.hogwartsmagic.client;

import com.hogwartsmagic.client.model.GringottsCartModel;
import com.hogwartsmagic.entity.GringottsCartEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class GringottsCartRenderer extends EntityRenderer<GringottsCartEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "hogwartsmagic", "textures/entity/gringotts_cart.png");

    /**
     * How far below the cart line the railhead sits.
     *
     * <p>One number, and it is the corridor's: the deck is laid two blocks under the line and its
     * top face is at -1.5. The model is authored with y=0 at the bottom of its wheels, so dropping
     * it by exactly that puts the wheels on the rails and nothing else has to know the cart's
     * height. It was floating a block and a half over the deck before this existed.
     */
    private static final float RAIL_DROP = 1.5F;

    private final GringottsCartModel model;

    public GringottsCartRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new GringottsCartModel(context.bakeLayer(GringottsCartModel.LAYER));
        this.shadowRadius = 0.9F;
    }

    @Override
    public boolean shouldRender(GringottsCartEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
                                double x, double y, double z) {
        // Always. Custom transforms plus steep angles false-cull this mid-plunge, which is exactly
        // when you are looking at it.
        return true;
    }

    @Override
    public void render(GringottsCartEntity entity, float yaw, float partialTicks, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        pose.pushPose();
        // Rotate about the entity origin, which is the cart line itself - the corridor's whole
        // cross-section is built on that frame, so the cart banks with the rock rather than beside it.
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(
                Mth.lerp(partialTicks, entity.prevDisplayPitch, entity.displayPitch)));
        pose.mulPose(Axis.ZP.rotationDegrees(
                Mth.lerp(partialTicks, entity.prevDisplayRoll, entity.displayRoll)));
        pose.scale(1.0F, -1.0F, 1.0F);
        pose.translate(0.0D, RAIL_DROP, 0.0D);

        VertexConsumer buffer = buffers.getBuffer(this.model.renderType(TEXTURE));
        this.model.setupAnim(entity, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        this.model.renderToBuffer(pose, buffer, light, OverlayTexture.NO_OVERLAY, -1);
        pose.popPose();
        super.render(entity, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(GringottsCartEntity entity) {
        return TEXTURE;
    }
}
