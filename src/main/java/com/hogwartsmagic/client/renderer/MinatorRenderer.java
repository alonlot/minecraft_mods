package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.MinatorModel;
import com.hogwartsmagic.entity.MinatorEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class MinatorRenderer extends MobRenderer<MinatorEntity, MinatorModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/minator.png");

    public MinatorRenderer(EntityRendererProvider.Context context) {
        super(context, new MinatorModel(context.bakeLayer(MinatorModel.LAYER_LOCATION)), 0.6F);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(MinatorEntity entity) {
        return TEXTURE;
    }
}
