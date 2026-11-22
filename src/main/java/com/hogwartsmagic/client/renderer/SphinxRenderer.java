package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.SphinxModel;
import com.hogwartsmagic.entity.SphinxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** The Sphinx. */
public class SphinxRenderer extends MobRenderer<SphinxEntity, SphinxModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/sphinx.png");
    /** The model is built at 32 units a block for finer texture detail, so it is drawn at about half size. */
    public static final float SCALE = 0.6F;

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
