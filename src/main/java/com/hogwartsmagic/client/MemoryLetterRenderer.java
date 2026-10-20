package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.MemoryLetterEntity;
import com.hogwartsmagic.pensieve.scene.LetterFlutter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Draws the Hogwarts letter envelope on a flat double-sided quad that flutters while it moves. */
public class MemoryLetterRenderer extends EntityRenderer<MemoryLetterEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/item/hogwarts_invite.png");
    // The envelope's opaque bounds in the 1024 x 1024 texture.
    private static final float U0 = 0.19f, V0 = 0.265f, U1 = 0.81f, V1 = 0.672f;
    private static final float HALF_W = 0.225f;
    private static final float HALF_D = 0.15f;

    public MemoryLetterRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(MemoryLetterEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(MemoryLetterEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        double dx = entity.getX() - entity.xo;
        double dy = entity.getY() - entity.yo;
        double dz = entity.getZ() - entity.zo;
        float speed = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float age = entity.tickCount + partialTick;
        boolean still = LetterFlutter.resting(speed);
        float roll = still ? 0f : LetterFlutter.roll(age, entity.flutterSeed);
        float wobble = still ? 0f : LetterFlutter.wobble(age, entity.flutterSeed);

        pose.pushPose();
        pose.translate(0.0, 0.025, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(entity.getXRot() + wobble));
        pose.mulPose(Axis.ZP.rotationDegrees(roll));

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose p = pose.last();
        Matrix4f m = p.pose();
        // Lies in the XZ plane (pitch 0 = flat); an up-facing quad then a down-facing one so both sides show.
        vertex(vc, m, p, -HALF_W, -HALF_D, U0, V0, 1, light);
        vertex(vc, m, p, -HALF_W, HALF_D, U0, V1, 1, light);
        vertex(vc, m, p, HALF_W, HALF_D, U1, V1, 1, light);
        vertex(vc, m, p, HALF_W, -HALF_D, U1, V0, 1, light);
        vertex(vc, m, p, HALF_W, -HALF_D, U1, V0, -1, light);
        vertex(vc, m, p, HALF_W, HALF_D, U1, V1, -1, light);
        vertex(vc, m, p, -HALF_W, HALF_D, U0, V1, -1, light);
        vertex(vc, m, p, -HALF_W, -HALF_D, U0, V0, -1, light);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose p, float x, float z,
                               float u, float v, float ny, int light) {
        vc.addVertex(m, x, 0f, z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0f, ny, 0f);
    }
}
