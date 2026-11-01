package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.LivingStatueEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

/**
 * Renders the living statue.
 *
 * <p>{@link LivingStatueModel} is generic, so the model type argument is spelled out as
 * {@code LivingStatueModel<LivingStatueEntity>}; the raw type does not satisfy
 * {@code MobRenderer}'s {@code M extends EntityModel<T>} bound.
 *
 * <p>The model stands its soles on y=24 like every vanilla model, so there is no lift or scale
 * here: 47 units to the crest is the registered 2.9-block box. The halberd reaches past the box
 * by design.
 */
public class LivingStatueRenderer extends MobRenderer<LivingStatueEntity, LivingStatueModel<LivingStatueEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/living_statue.png");

    public LivingStatueRenderer(EntityRendererProvider.Context context) {
        // Shadow radius is the hitbox half-width.
        super(context, new LivingStatueModel<>(context.bakeLayer(LivingStatueModel.LAYER)), 0.6F);
        this.addLayer(new Glow(this));
    }

    /**
     * Facing only. Vanilla tips a dying mob over onto its side; a statue breaks where it stands,
     * and the model already drops it onto its knees and knocks its head off. Rolling the whole
     * rig on top of that laid the kneeling wreck on its back.
     */
    @Override
    protected void setupRotations(LivingStatueEntity entity, PoseStack poseStack, float bob,
                                  float yBodyRot, float partialTick, float scale) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yBodyRot));
    }

    @Override
    public ResourceLocation getTextureLocation(LivingStatueEntity entity) {
        return TEXTURE;
    }

    /**
     * A cold light in the visor slit while it is animate -- the one tell, from across a hall,
     * that a suit of armour is no longer just a suit of armour. Dark while dormant; it gutters on
     * as the head turns on the first wake beat, and goes out as it falls.
     */
    static final class Glow extends RenderLayer<LivingStatueEntity, LivingStatueModel<LivingStatueEntity>> {
        private static final RenderType GLOW = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(
                ExampleMod.MODID, "textures/entity/living_statue_glow.png"));

        Glow(RenderLayerParent<LivingStatueEntity, LivingStatueModel<LivingStatueEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light,
                           LivingStatueEntity e, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            float glow = level(e, partialTick, ageInTicks);
            if (glow <= 0.01F) {
                return;
            }
            // RenderType.eyes blends additively and ignores alpha, so the fade scales the colour.
            int c = (int) (glow * 255.0F);
            VertexConsumer vc = buffers.getBuffer(GLOW);
            this.getParentModel().renderToBuffer(poseStack, vc, 0xF000F0, OverlayTexture.NO_OVERLAY,
                    FastColor.ARGB32.color(255, c, c, c));
        }

        private static float level(LivingStatueEntity e, float partialTick, float ageInTicks) {
            if (e.isReforming()) {
                return 0.0F;
            }
            float crumble = e.getCrumbleProgress(partialTick);
            if (crumble > 0.0F) {
                return Math.max(0.0F, 1.0F - crumble * 2.5F);
            }
            float wake = e.getWakeTime(partialTick);
            if (wake < 5.0F) {
                return 0.0F;
            }
            if (wake < 13.0F) {
                // Guttering on: a couple of stutters before it holds.
                float t = (wake - 5.0F) / 8.0F;
                float flicker = (float) Math.sin(ageInTicks * 2.3F) > 0.2F ? 1.0F : 0.35F;
                return t * flicker;
            }
            return 1.0F;
        }
    }
}
