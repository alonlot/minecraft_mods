package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.GiantModel;
import com.hogwartsmagic.entity.GiantEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renders the giant. */
public class GiantRenderer extends MobRenderer<GiantEntity, GiantModel> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/giant.png");

    /**
     * The model is built 111.5px tall so the face and the pelt have room for
     * detail; this brings the rendered giant down to roughly 5.5 blocks, which is
     * the height the hitbox is registered at.
     */
    private static final float MODEL_SCALE = 0.79F;

    public GiantRenderer(EntityRendererProvider.Context context) {
        super(context, new GiantModel(context.bakeLayer(GiantModel.LAYER_LOCATION)), 1.6F);
    }

    @Override
    protected void scale(GiantEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(GiantEntity entity) {
        return TEXTURE;
    }
}
