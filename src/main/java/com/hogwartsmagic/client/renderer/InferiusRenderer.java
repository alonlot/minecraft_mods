package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.InferiusModel;
import com.hogwartsmagic.entity.InferiusEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renders the Inferius with its Blockbench model. No glowing layer: the sockets are dead. */
public class InferiusRenderer extends MobRenderer<InferiusEntity, InferiusModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/inferius.png");

    public InferiusRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new InferiusModel(ctx.bakeLayer(InferiusModel.LAYER_LOCATION)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(InferiusEntity entity) {
        return TEXTURE;
    }
}
