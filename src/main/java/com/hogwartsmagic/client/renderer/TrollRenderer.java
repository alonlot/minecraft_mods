package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.TrollModel;
import com.hogwartsmagic.entity.TrollEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class TrollRenderer extends MobRenderer<TrollEntity, TrollModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/troll.png");

    public TrollRenderer(EntityRendererProvider.Context context) {
        super(context, new TrollModel(context.bakeLayer(TrollModel.LAYER_LOCATION)), 0.95F);
    }

    @Override
    public ResourceLocation getTextureLocation(TrollEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(TrollEntity entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(1.15F, 1.15F, 1.15F);
    }
}
