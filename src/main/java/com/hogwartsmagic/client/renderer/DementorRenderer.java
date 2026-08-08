package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.DementorModel;
import com.hogwartsmagic.entity.DementorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class DementorRenderer extends MobRenderer<DementorEntity, DementorModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/dementor.png");

    public DementorRenderer(EntityRendererProvider.Context context) {
        // Wide, soft shadow - it is a big hanging mass of cloth, not a footprint.
        super(context, new DementorModel(context.bakeLayer(DementorModel.LAYER_LOCATION)), 0.9F);
    }

    @Override
    protected void scale(DementorEntity entity, PoseStack poseStack, float partialTickTime) {
        // The model stands 59px from the hem to the tip of the hood; 1.1 puts the
        // hood a little over four blocks up, which is the "approximately three
        // metres" of the books read as looming rather than man-sized. The torn
        // tendrils deliberately hang below the origin - it never touches down.
        poseStack.scale(1.1F, 1.1F, 1.1F);
        super.scale(entity, poseStack, partialTickTime);
    }

    @Override
    public ResourceLocation getTextureLocation(DementorEntity entity) {
        return TEXTURE;
    }
}
