package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.AcromantulaModel;
import com.hogwartsmagic.entity.AcromantulaEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class AcromantulaRenderer extends MobRenderer<AcromantulaEntity, AcromantulaModel<AcromantulaEntity>> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/acromantula.png");

    public AcromantulaRenderer(EntityRendererProvider.Context context) {
        super(context, new AcromantulaModel<>(context.bakeLayer(AcromantulaModel.LAYER_LOCATION)), 0.8f);
        this.addLayer(new AcromantulaEyesLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(AcromantulaEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(AcromantulaEntity entity, com.mojang.blaze3d.vertex.PoseStack poseStack, float partialTick) {
        float f = entity.getSpiderScale();
        poseStack.scale(2.5F * f, 2.5F * f, 2.5F * f);
    }
}
