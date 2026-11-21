package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.SphinxModel;
import com.hogwartsmagic.entity.SphinxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** The Sphinx, drawn a fifth larger than her model: she should look up at nobody. */
public class SphinxRenderer extends MobRenderer<SphinxEntity, SphinxModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/sphinx.png");
    public static final float SCALE = 1.2F;

    public SphinxRenderer(EntityRendererProvider.Context context) {
        super(context, new SphinxModel(context.bakeLayer(SphinxModel.LAYER_LOCATION)), 1.0F);
    }

    @Override
    protected void scale(SphinxEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(SphinxEntity entity) {
        return TEXTURE;
    }
}
