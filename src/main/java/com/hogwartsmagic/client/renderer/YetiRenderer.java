package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.YetiModel;
import com.hogwartsmagic.entity.YetiEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** The Yeti. */
public class YetiRenderer extends MobRenderer<YetiEntity, YetiModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/yeti.png");
    /** Built at 32 units a block for the fur; drawn a little over half size: three blocks tall. */
    public static final float SCALE = 0.55F;

    public YetiRenderer(EntityRendererProvider.Context context) {
        super(context, new YetiModel(context.bakeLayer(YetiModel.LAYER_LOCATION)), 1.1F);
    }

    @Override
    protected void scale(YetiEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(YetiEntity entity) {
        return TEXTURE;
    }
}
