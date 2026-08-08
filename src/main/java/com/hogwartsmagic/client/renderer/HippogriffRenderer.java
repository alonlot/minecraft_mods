package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.HippogriffModel;
import com.hogwartsmagic.entity.HippogriffEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class HippogriffRenderer extends MobRenderer<HippogriffEntity, HippogriffModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/hippogriff.png");

    public HippogriffRenderer(EntityRendererProvider.Context context) {
        super(context, new HippogriffModel(context.bakeLayer(HippogriffModel.LAYER_LOCATION)), 0.95F);
    }

    /**
     * Living entities are normally rendered bolt upright no matter which way they are
     * actually moving, which is why flight felt like walking through the air. This tips her
     * body along her flight path and rolls her into her turns.
     *
     * <p>Both are scaled by flight progress so they fade out completely as she lands.
     */
    @Override
    protected void setupRotations(HippogriffEntity entity, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTick, float scale) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTick, scale);

        if (entity.getFlightProgress(partialTick) <= 0.001F) {
            return;
        }

        // Positive pitch is nose-up; positive roll is a bank to her right. These come from
        // the entity rather than being worked out here, because the saddle position is
        // derived from the same two numbers - otherwise the rider drifts off her back.
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getVisualPitch(partialTick)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-entity.getVisualRoll(partialTick)));
    }

    @Override
    public ResourceLocation getTextureLocation(HippogriffEntity entity) {
        return TEXTURE;
    }
}
