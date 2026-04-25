package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.DementorModel;
import com.hogwartsmagic.entity.DementorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class DementorRenderer extends MobRenderer<DementorEntity, DementorModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/dementor.png");

    public DementorRenderer(EntityRendererProvider.Context context) {
        // Shadow radius of 0.8
        super(context, new DementorModel(context.bakeLayer(DementorModel.LAYER_LOCATION)), 0.8F);
    }

    @Override
    protected void scale(DementorEntity entity, PoseStack poseStack, float partialTickTime) {
        // Slightly upscales the dementor Model to make it 2.5 blocks tall
        poseStack.scale(1.2F, 1.2F, 1.2F);
        super.scale(entity, poseStack, partialTickTime);
    }

    @Override
    public ResourceLocation getTextureLocation(DementorEntity entity) {
        return TEXTURE;
    }
}
