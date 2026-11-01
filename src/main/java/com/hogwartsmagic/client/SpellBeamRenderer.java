package com.hogwartsmagic.client;

import com.hogwartsmagic.entity.SpellBeamEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * The spell beam entity's renderer draws nothing of its own: a beam is drawn from the path it has flown, wand to
 * head, by {@link SpellBeamFxRenderer} in its spell's {@link BeamStyle}, which an entity renderer (culled to the
 * entity's small box, and only ever seeing where it is now) can't do.
 */
public class SpellBeamRenderer extends EntityRenderer<SpellBeamEntity> {

    public SpellBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SpellBeamEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Nothing here: every beam is drawn whole, from the wand, by SpellBeamFxRenderer in its spell's style.
    }

    @Override
    public ResourceLocation getTextureLocation(SpellBeamEntity entity) {
        return null;
    }
}
