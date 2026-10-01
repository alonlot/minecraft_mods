package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.LivingStatueEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the living statue.
 *
 * <p>{@link LivingStatueModel} is generic, so the model type argument is spelled out as
 * {@code LivingStatueModel<LivingStatueEntity>}; the raw type does not satisfy
 * {@code MobRenderer}'s {@code M extends EntityModel<T>} bound. The
 * {@code GoblinRenderer}/{@code GoblinModel} pair is the same shape.
 *
 * <p>There is deliberately no {@code poseStack.scale} here. The geometry already spans model
 * y=-12.5 (crest) to y=34 (soles), i.e. 46.5px = 2.91 blocks, which is the registered 2.9-block
 * hitbox to within a rendering hair. Scaling it up to "fill" the box would only push the halberd
 * -- which reaches past the hitbox by design -- further out still.
 */
public class LivingStatueRenderer extends MobRenderer<LivingStatueEntity, LivingStatueModel<LivingStatueEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/living_statue.png");

    /**
     * Ten pixels of lift, because the model's soles sit at model y=34 while the renderer puts the
     * ground at y=24. Every other model in the mod hangs its parts off a root part declared at
     * {@code PartPose.offset(0, 24, 0)} and so needs no correction. This one has no root part at
     * all: {@code PARTS} in build_scripts/generate_living_statue_model.py is authored with the
     * shoulder line at y=0, its emitter skips cubeless parts, and every limb is therefore attached
     * straight to the mesh root. Without this the statue stands 0.625 blocks buried in the floor --
     * shins gone, halberd butt through the stone.
     *
     * <p>The correction lives here rather than in the model because the geometry is generated: a
     * hand edit to LivingStatueModel.java is thrown away the next time anyone runs that script. The
     * real fix, if the model is ever regenerated for another reason, is to subtract 10 from every
     * pivot and origin y in {@code PARTS} (and set that script's {@code ROOT_H} to 24.0 so the
     * .bbmodel export still lands feet-on-floor), then delete this override.
     */
    private static final float SOLE_LIFT = 10.0F / 16.0F;

    public LivingStatueRenderer(EntityRendererProvider.Context context) {
        // Shadow radius is the hitbox half-width. The boots alone are 0.375 and the pauldrons
        // 0.6875: the first reads as a figure hovering over its own shadow at this height, the
        // second spills past the block the statue is standing on. 0.6 is the box it occupies.
        super(context, new LivingStatueModel<>(context.bakeLayer(LivingStatueModel.LAYER)), 0.6F);
    }

    @Override
    protected void scale(LivingStatueEntity entity, PoseStack poseStack, float partialTick) {
        // LivingEntityRenderer has already flipped the stack with scale(-1, -1, 1) by the time this
        // runs, so a NEGATIVE y here is upward in the world.
        poseStack.translate(0.0F, -SOLE_LIFT, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(LivingStatueEntity entity) {
        return TEXTURE;
    }
}
