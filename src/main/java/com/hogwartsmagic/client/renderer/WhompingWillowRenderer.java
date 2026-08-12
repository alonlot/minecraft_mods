package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.WhompingWillowModel;
import com.hogwartsmagic.entity.WhompingWillowEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class WhompingWillowRenderer extends MobRenderer<WhompingWillowEntity, WhompingWillowModel> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/whomping_willow.png");

    /** The model is built 180px tall for texel density; 180/16 * 0.80 puts the render at exactly the
     *  9.0 blocks the hitbox is registered at. */
    private static final float MODEL_SCALE = 0.80F;

    public WhompingWillowRenderer(EntityRendererProvider.Context context) {
        // Shadow radius is tuned to the 3.2-block root plate, not the canopy. A shadow the width of
        // the branches would read as a bug.
        super(context, new WhompingWillowModel(context.bakeLayer(WhompingWillowModel.LAYER_LOCATION)), 2.6F);
    }

    @Override
    protected void scale(WhompingWillowEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    /**
     * A rooted tree does not turn to face you.
     *
     * <p>{@code MobRenderer} rotates the model by the interpolated {@code yBodyRot}, which arrives
     * over the network as a byte — 1.4 degrees of quantisation, worth 0.17 blocks of drift at the
     * branches' 6.8-block reach. The server pins the yaw anyway, but rendering from the synced
     * <i>float</i> instead means the branch you see and the arc that hurts are computed from bit-identical
     * values, which is the whole point of the shared statics in the entity.
     */
    @Override
    protected void setupRotations(WhompingWillowEntity entity, PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTick, float scale) {
        super.setupRotations(entity, poseStack, ageInTicks, entity.getPlantedYaw(), partialTick, scale);
    }

    @Override
    public ResourceLocation getTextureLocation(WhompingWillowEntity entity) {
        return TEXTURE;
    }
}
