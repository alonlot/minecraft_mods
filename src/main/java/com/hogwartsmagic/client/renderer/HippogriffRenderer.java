package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.HippogriffModel;
import com.hogwartsmagic.entity.HippogriffEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class HippogriffRenderer extends MobRenderer<HippogriffEntity, HippogriffModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/hippogriff.png");

    public HippogriffRenderer(EntityRendererProvider.Context context) {
        super(context, new HippogriffModel(context.bakeLayer(HippogriffModel.LAYER_LOCATION)), 0.95F);
    }

    @Override
    public ResourceLocation getTextureLocation(HippogriffEntity entity) {
        return TEXTURE;
    }
}
