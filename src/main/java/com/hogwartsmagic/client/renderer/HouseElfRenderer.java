package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.HouseElfModel;
import com.hogwartsmagic.entity.HouseElfEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the house elf. Bound and wild elves share the filthy pillowcase; a
 * freed one gets the same geometry in mismatched knitwear and odd socks.
 */
public class HouseElfRenderer extends MobRenderer<HouseElfEntity, HouseElfModel> {

    private static final ResourceLocation BOUND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/house_elf.png");
    private static final ResourceLocation FREE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/house_elf_free.png");

    /**
     * The model is built about 26px tall so the enormous eyes and ears have room
     * for detail; this brings the rendered elf down to roughly 0.85 blocks, which
     * is the height the hitbox is registered at.
     */
    private static final float MODEL_SCALE = 0.52F;

    public HouseElfRenderer(EntityRendererProvider.Context context) {
        super(context, new HouseElfModel(context.bakeLayer(HouseElfModel.LAYER_LOCATION)), 0.25F);
    }

    @Override
    protected void scale(HouseElfEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(HouseElfEntity entity) {
        return entity.isFree() ? FREE_TEXTURE : BOUND_TEXTURE;
    }
}
