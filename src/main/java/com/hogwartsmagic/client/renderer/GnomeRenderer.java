package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.model.GnomeModel;
import com.hogwartsmagic.entity.GnomeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * The garden gnome. Held by the ankle or whirled, it hangs upside down from the hand (the way a Dinnerbone
 * mob does); whirled, it also swings out so its head leads; flung, it tumbles end over end.
 */
public class GnomeRenderer extends MobRenderer<GnomeEntity, GnomeModel> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/gnome.png");
    /** The model is built at 32 units a block and stands 16 units tall: half a block. */
    public static final float SCALE = 0.5F;
    private static final float TUMBLE_DEGREES_PER_TICK = 35.0F;
    private static final float SWING_OUT_DEGREES = -70.0F;

    public GnomeRenderer(EntityRendererProvider.Context context) {
        super(context, new GnomeModel(context.bakeLayer(GnomeModel.LAYER_LOCATION)), 0.25F);
    }

    @Override
    protected void setupRotations(GnomeEntity entity, PoseStack poseStack, float bob, float yBodyRot,
                                  float partialTick, float scale) {
        super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
        GnomeEntity.State s = entity.state();
        if (s == GnomeEntity.State.CARRIED || s == GnomeEntity.State.SPUN) {
            poseStack.translate(0.0F, entity.getBbHeight() + 0.1F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
            if (s == GnomeEntity.State.SPUN) {
                poseStack.mulPose(Axis.XP.rotationDegrees(SWING_OUT_DEGREES));
            }
        } else if (s == GnomeEntity.State.THROWN) {
            float half = entity.getBbHeight() * 0.5F;
            poseStack.translate(0.0F, half, 0.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(TUMBLE_DEGREES_PER_TICK * (entity.tickCount + partialTick)));
            poseStack.translate(0.0F, -half, 0.0F);
        }
    }

    @Override
    protected void scale(GnomeEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(GnomeEntity entity) {
        return TEXTURE;
    }
}
