package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.SnakeModel;
import com.hogwartsmagic.entity.SnakeEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class SnakeRenderer extends MobRenderer<SnakeEntity, SnakeModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/snake.png");

    public SnakeRenderer(EntityRendererProvider.Context context) {
        super(context, new SnakeModel(context.bakeLayer(SnakeModel.LAYER_LOCATION)), 0.25F);
    }

    @Override
    public ResourceLocation getTextureLocation(SnakeEntity entity) {
        return TEXTURE;
    }
}
