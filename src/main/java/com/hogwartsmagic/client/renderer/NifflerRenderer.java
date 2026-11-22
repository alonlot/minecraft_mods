package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.NifflerModel;
import com.hogwartsmagic.entity.NifflerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** The Niffler. */
public class NifflerRenderer extends MobRenderer<NifflerEntity, NifflerModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/niffler.png");
    /** The model is built at 32 units a block for finer fur, and a Niffler is small: about rabbit length. */
    public static final float SCALE = 0.4F;
    public static final float BABY_SCALE = 0.6F;

    public NifflerRenderer(EntityRendererProvider.Context context) {
        super(context, new NifflerModel(context.bakeLayer(NifflerModel.LAYER_LOCATION)), 0.3F);
    }

    @Override
    protected void scale(NifflerEntity entity, PoseStack poseStack, float partialTick) {
        float s = SCALE * (entity.isBaby() ? BABY_SCALE : 1.0F);
        poseStack.scale(s, s, s);
    }

    @Override
    public ResourceLocation getTextureLocation(NifflerEntity entity) {
        return TEXTURE;
    }
}
