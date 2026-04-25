package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.BasiliskModel;
import com.hogwartsmagic.entity.BasiliskEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class BasiliskRenderer extends MobRenderer<BasiliskEntity, BasiliskModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/basilisk.png");

    public BasiliskRenderer(EntityRendererProvider.Context context) {
        super(context, new BasiliskModel(context.bakeLayer(BasiliskModel.LAYER_LOCATION)), 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(BasiliskEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(BasiliskEntity entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(2.2F, 2.2F, 2.2F);
    }
}
