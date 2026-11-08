package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.TrollModel;
import com.hogwartsmagic.entity.TrollEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renders the mountain troll. */
public class TrollRenderer extends MobRenderer<TrollEntity, TrollModel> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/troll.png");

    /**
     * The model stands about 58px to the top of the skull so the face and the club
     * have room for detail; this brings it down to roughly 3.25 blocks, which is the
     * height the hitbox is registered at.
     */
    private static final float MODEL_SCALE = 0.9F;

    public TrollRenderer(EntityRendererProvider.Context context) {
        super(context, new TrollModel(context.bakeLayer(TrollModel.LAYER_LOCATION)), 1.1F);
    }

    @Override
    protected void scale(TrollEntity entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(TrollEntity entity) {
        return TEXTURE;
    }
}
