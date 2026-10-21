package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.MemoryLetterEntity;
import com.hogwartsmagic.pensieve.scene.LetterFlood;
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
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Draws the Hogwarts letter envelope on a double-sided quad that flutters while it moves. The quad lies in the XY
 * plane (facing +Z), so look pitch 0 stands it upright and pitch 90 lays it flat, the convention the scene format
 * documents.
 */
public class MemoryLetterRenderer extends EntityRenderer<MemoryLetterEntity> {
    /** The envelope cropped out of the item art and downscaled to 128 x 84, so the whole texture is the envelope. */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/memory_letter.png");
    private static final float HALF_W = LetterFlood.ENVELOPE_HALF_W;
    private static final float HALF_H = LetterFlood.ENVELOPE_HALF_H;

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
        float amplitude = LetterFlutter.amplitude(speed);
        float roll = amplitude * LetterFlutter.roll(age, entity.flutterSeed);
        float wobble = amplitude * LetterFlutter.wobble(age, entity.flutterSeed);
        // yaw is the dispatcher's interpolated entity yaw; the pitch is interpolated here the same way.
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());

        pose.pushPose();
        pose.translate(0.0, 0.025, 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch + wobble));
        pose.mulPose(Axis.YP.rotationDegrees(roll));

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        PoseStack.Pose p = pose.last();
        Matrix4f m = p.pose();
        // Front face: counter-clockwise seen from +Z, normal +Z.
        vertex(vc, m, p, -HALF_W, -HALF_H, 0f, 1f, 1, light);
        vertex(vc, m, p, HALF_W, -HALF_H, 1f, 1f, 1, light);
        vertex(vc, m, p, HALF_W, HALF_H, 1f, 0f, 1, light);
        vertex(vc, m, p, -HALF_W, HALF_H, 0f, 0f, 1, light);
        // Back face: counter-clockwise seen from -Z, normal -Z, with U mirrored so the art reads the right way round.
        vertex(vc, m, p, HALF_W, -HALF_H, 0f, 1f, -1, light);
        vertex(vc, m, p, -HALF_W, -HALF_H, 1f, 1f, -1, light);
        vertex(vc, m, p, -HALF_W, HALF_H, 1f, 0f, -1, light);
        vertex(vc, m, p, HALF_W, HALF_H, 0f, 0f, -1, light);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose p, float x, float y,
                               float u, float v, int nz, int light) {
        vc.addVertex(m, x, y, 0f).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0f, 0f, nz);
    }
}
