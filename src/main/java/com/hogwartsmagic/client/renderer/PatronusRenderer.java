package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.PatronusModel;
import com.hogwartsmagic.entity.PatronusEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class PatronusRenderer extends MobRenderer<PatronusEntity, PatronusModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/patronus.png");

    public PatronusRenderer(EntityRendererProvider.Context context) {
        super(context, new PatronusModel(context.bakeLayer(PatronusModel.LAYER_LOCATION)), 0.0F); // No shadow for light spirits
    }

    @Override
    protected void scale(PatronusEntity entity, PoseStack poseStack, float partialTickTime) {
        poseStack.scale(1.2F, 1.2F, 1.2F); // Majestic size
        super.scale(entity, poseStack, partialTickTime);
    }

    @Override
    protected net.minecraft.client.renderer.RenderType getRenderType(PatronusEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        // Force the translucent emissive (glowing) render type for the celestial look
        return RenderType.entityTranslucentEmissive(getTextureLocation(entity));
    }

    @Override
    public ResourceLocation getTextureLocation(PatronusEntity entity) {
        return TEXTURE;
    }
}
