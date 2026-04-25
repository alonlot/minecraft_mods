package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.OwlEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class OwlRenderer extends MobRenderer<OwlEntity, OwlModel<OwlEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/owl.png");

    public OwlRenderer(EntityRendererProvider.Context context) {
        super(context, new OwlModel<>(context.bakeLayer(OwlModel.LAYER_LOCATION)), 0.3F);
        this.addLayer(new OwlCarriedItemLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(OwlEntity entity) {
        return TEXTURE;
    }
}
