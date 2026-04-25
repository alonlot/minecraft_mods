package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.FlyingMobEntity;
import com.hogwartsmagic.client.model.FlyingMobModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class FlyingMobRenderer extends MobRenderer<FlyingMobEntity, FlyingMobModel<FlyingMobEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/flying_mob.png");

    public FlyingMobRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new FlyingMobModel<>(ctx.bakeLayer(FlyingMobModel.LAYER_LOCATION)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(FlyingMobEntity entity) {
        return TEXTURE;
    }
}