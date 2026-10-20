package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.MemoryDoorEntity;
import com.hogwartsmagic.pensieve.scene.DoorHinge;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Draws one door leaf as a textured box that swings about its hinge edge (see {@link DoorHinge}).
 *
 * <p>The actor {@code scale} is already on the PoseStack when Pensieve calls this (MemoryActors scales the
 * stack before dispatching), so the corners are taken at scale 1; passing the scale here would apply it twice.
 */
public class MemoryDoorRenderer extends EntityRenderer<MemoryDoorEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/memory_door.png");
    private static final float TEX_W = 64f, TEX_H = 192f;
    // Pixel rects {u0, v0, u1, v1} per face, in DoorHinge.FACES order. Keep in step with the texture script.
    private static final float[][] RECTS = {
            {0, 0, 32, 96}, {32, 0, 64, 96}, {0, 96, 6, 192}, {6, 96, 12, 192}, {16, 96, 48, 102}, {16, 102, 48, 108},
    };

    public MemoryDoorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(MemoryDoorEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(MemoryDoorEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        // `yaw` is the interpolated body yaw from the dispatcher, so the swing is smooth between ticks.
        double[][] c = new double[8][];
        for (int i = 0; i < 8; i++) c[i] = DoorHinge.corner(yaw, 1f, i);

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        PoseStack.Pose p = pose.last();
        Matrix4f m = p.pose();
        for (int f = 0; f < DoorHinge.FACES.length; f++) {
            int[] q = DoorHinge.FACES[f];
            double[] n = DoorHinge.faceNormal(yaw, 1f, f);
            float[] r = RECTS[f];
            float u0 = r[0] / TEX_W, v0 = r[1] / TEX_H, u1 = r[2] / TEX_W, v1 = r[3] / TEX_H;
            // Corners run bottom-left, bottom-right, top-right, top-left as seen from outside.
            vertex(vc, m, p, c[q[0]], u0, v1, n, light);
            vertex(vc, m, p, c[q[1]], u1, v1, n, light);
            vertex(vc, m, p, c[q[2]], u1, v0, n, light);
            vertex(vc, m, p, c[q[3]], u0, v0, n, light);
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose p, double[] pos,
                               float u, float v, double[] n, int light) {
        vc.addVertex(m, (float) pos[0], (float) pos[1], (float) pos[2]).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, (float) n[0], (float) n[1], (float) n[2]);
    }
}
