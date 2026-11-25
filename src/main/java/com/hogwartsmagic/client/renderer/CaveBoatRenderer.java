package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.client.model.CaveSkiffModel;
import com.hogwartsmagic.entity.CaveBoatEntity;
import com.hogwartsmagic.locket.SkiffRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The cave skiff: the hull in world light, the lantern full-bright, a gentle bob on the water, and the bow-first rise
 * driven by the synced rise counter (bow up and the whole boat low, easing level over {@link SkiffRules#RISE_TICKS}).
 */
public class CaveBoatRenderer extends EntityRenderer<CaveBoatEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "textures/entity/cave_skiff.png");
    /** Hull bottom sits a little under the entity position, which floats just below the surface. */
    private static final double HULL_BOTTOM = -0.12D;
    private static final double RISE_DROP = 0.9D;
    private static final float RISE_PITCH = 22.0F;

    private final CaveSkiffModel model;

    public CaveBoatRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.8F;
        this.model = new CaveSkiffModel(context.bakeLayer(CaveSkiffModel.LAYER));
    }

    @Override
    public boolean shouldRender(CaveBoatEntity entity, Frustum frustum, double x, double y, double z) {
        return true; // a 3.5 block hull turned by its yaw is wider than the entity box the frustum tests
    }

    @Override
    public void render(CaveBoatEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float age = entity.tickCount + partialTick;
        int rise = entity.riseTicks();
        float progress = rise > 0 ? Mth.clamp((rise - partialTick) / SkiffRules.RISE_TICKS, 0.0F, 1.0F) : 0.0F;
        float eased = progress * progress * (3.0F - 2.0F * progress);

        poseStack.pushPose();
        poseStack.translate(0.0D, HULL_BOTTOM + SkiffRules.bob(age) - eased * RISE_DROP, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
        poseStack.mulPose(Axis.XP.rotationDegrees(-eased * RISE_PITCH));
        poseStack.scale(1.0F, -1.0F, 1.0F);

        VertexConsumer consumer = buffer.getBuffer(this.model.renderType(TEXTURE));
        this.model.renderHull(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        this.model.renderLantern(poseStack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        if (rise <= 0 && !entity.isUnderWater()) {
            VertexConsumer mask = buffer.getBuffer(RenderType.waterMask());
            this.model.renderWaterPatch(poseStack, mask, packedLight, OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CaveBoatEntity entity) {
        return TEXTURE;
    }
}
