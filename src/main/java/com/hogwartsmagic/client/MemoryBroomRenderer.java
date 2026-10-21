package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.MemoryBroomEntity;
import com.hogwartsmagic.pensieve.scene.BroomFlight;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/**
 * Draws a memory broom ({@link BroomFlight} holds the frame) with the real broom's model: the school broom with
 * BroomModelV2's training layer and texture, the racing broom with the Nimbus layer, at {@code scale} 1 the size of the
 * real broom. The PoseStack arrives translated to the broom's origin (the seat point on the handle axis; MemoryActors
 * puts the hover bob into the position) and scaled by the actor scale. The renderer turns it by the lerped yaw, pitch
 * and roll through {@link BroomFlight#frame}, so the broom and its riders share one rotation, then maps the model into
 * the broom frame: model -z (the tip) to local +z, the handle axis (model y 16.8 px) to local y 0.
 */
public class MemoryBroomRenderer extends EntityRenderer<MemoryBroomEntity> {
    /** The handle axis's model height (BroomModelV2's root offset, 16.8 px), in blocks. */
    private static final double AXIS_Y = 16.8 / 16.0;

    private final BroomModelV2 model;
    private final ResourceLocation texture;

    public MemoryBroomRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, String texture) {
        super(context);
        this.model = new BroomModelV2(context.bakeLayer(layer));
        this.texture = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/" + texture + ".png");
    }

    /** The school broom: the training layer and the Shooting Star texture, as BroomRenderer's broom type 0. */
    public static MemoryBroomRenderer school(EntityRendererProvider.Context context) {
        return new MemoryBroomRenderer(context, BroomModelV2.TRAINING_LAYER, "broom_shooting_star_v2");
    }

    /** The racing broom: the Nimbus layer and texture, as BroomRenderer's broom type 1. */
    public static MemoryBroomRenderer racing(EntityRendererProvider.Context context) {
        return new MemoryBroomRenderer(context, BroomModelV2.NIMBUS_LAYER, "broom_nimbus_2000_v2");
    }

    @Override
    public ResourceLocation getTextureLocation(MemoryBroomEntity entity) {
        return texture;
    }

    @Override
    public void render(MemoryBroomEntity broom, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        float pitch = Mth.lerp(partialTick, broom.pitchO, broom.pitch);
        float roll = Mth.lerp(partialTick, broom.rollO, broom.roll);
        pose.pushPose();
        pose.mulPose(rotation(yaw, pitch, roll));
        // Model to broom frame: (x, y, z) px -> (x, 16.8 - y, -z) / 16.
        pose.mulPose(Axis.YP.rotationDegrees(180f));
        pose.scale(-1f, -1f, 1f);
        pose.translate(0, -AXIS_Y, 0);
        model.renderToBuffer(pose, buffers.getBuffer(model.renderType(texture)), light, OverlayTexture.NO_OVERLAY, -1);
        pose.popPose();
        super.render(broom, yaw, partialTick, pose, buffers, light);
    }

    /** {@link BroomFlight#frame} as a quaternion. */
    public static Quaternionf rotation(float yaw, float pitch, float roll) {
        double[] r = BroomFlight.frame(yaw, pitch, roll);
        // JOML's constructor takes columns: (m00, m01, m02) is the first column.
        Matrix3f m3 = new Matrix3f((float) r[0], (float) r[3], (float) r[6], (float) r[1], (float) r[4], (float) r[7],
                (float) r[2], (float) r[5], (float) r[8]);
        return new Quaternionf().setFromNormalized(m3);
    }
}
