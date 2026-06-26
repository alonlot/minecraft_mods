package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.HungarianHorntailModel;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class HungarianHorntailRenderer extends MobRenderer<HungarianHorntailEntity, HungarianHorntailModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/hungarian_horntail.png");

    public HungarianHorntailRenderer(EntityRendererProvider.Context context) {
        super(context, new HungarianHorntailModel(context.bakeLayer(HungarianHorntailModel.LAYER_LOCATION)), 1.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(HungarianHorntailEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(HungarianHorntailEntity entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(1.35F, 1.35F, 1.35F);
    }
}
