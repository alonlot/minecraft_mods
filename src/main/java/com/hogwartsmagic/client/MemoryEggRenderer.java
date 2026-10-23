package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.pensieve.PensieveClientState;
import com.hogwartsmagic.entity.MemoryBallEntity;
import com.hogwartsmagic.pensieve.scene.GoldenEgg;
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
 * Draws the golden egg of the First Task ({@link GoldenEgg} holds its size, its shape and the glint): one textured
 * egg-shaped UV mesh, upright and the same from every side, lit by the world's light like any entity, and the glint,
 * a camera-facing star sprite at its top, additive and full-bright, flashing on the scene clock. The PoseStack arrives
 * translated to the egg's centre and scaled by the actor scale, so everything is drawn at scale 1 about the origin.
 * Textures from {@code build_scripts/generate_first_task_textures.py}.
 */
public class MemoryEggRenderer extends EntityRenderer<MemoryBallEntity> {
    private static final int LAT = 20, LON = 24;
    private static final ResourceLocation EGG = tex("memory_golden_egg"), GLINT = tex("golden_egg_glint");
    private static final Mesh MESH = Mesh.build();

    public MemoryEggRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(MemoryBallEntity entity) {
        return EGG;
    }

    @Override
    public void render(MemoryBallEntity egg, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        pose.pushPose();
        MESH.emit(buffers.getBuffer(RenderType.entityCutoutNoCull(EGG)), pose.last(), light);
        double now = PensieveClientState.sceneTime(partialTick);
        double glint = GoldenEgg.glint(now, egg.seed);
        if (glint > 0.01) {
            float half = (float) (GoldenEgg.STAR_SIZE * (0.45 + 0.55 * glint) / 2);
            pose.translate(0, GoldenEgg.LENGTH / 2 + GoldenEgg.STAR_LIFT, 0);
            pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) GoldenEgg.starTurn(now, egg.seed)));
            int a = (int) Math.min(255, 255 * glint);
            int argb = (a << 24) | 0xFFFFFF;
            Matrix4f m = pose.last().pose();
            VertexConsumer vc = buffers.getBuffer(RenderType.eyes(GLINT));
            star(vc, pose.last(), m, -half, -half, 0f, 1f, argb);
            star(vc, pose.last(), m, half, -half, 1f, 1f, argb);
            star(vc, pose.last(), m, half, half, 1f, 0f, argb);
            star(vc, pose.last(), m, -half, half, 0f, 0f, argb);
        }
        pose.popPose();
        super.render(egg, yaw, partialTick, pose, buffers, light);
    }

    private static void star(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, float x, float y, float u, float v,
                             int argb) {
        vc.addVertex(m, x, y, 0f).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0).setNormal(pose, 0f, 0f, 1f);
    }

    /** The egg's surface as flat arrays (position, outward normal and uv per vertex), built once. */
    private record Mesh(float[] pos, float[] nrm, float[] uv) {
        static Mesh build() {
            int cols = LON + 1, n = (LAT + 1) * cols;
            float[] pos = new float[n * 3], nrm = new float[n * 3], uv = new float[n * 2];
            double h = GoldenEgg.LENGTH / 2;
            for (int i = 0; i <= LAT; i++) {
                double phi = Math.PI * i / LAT, v = Math.cos(phi);
                double r = GoldenEgg.radiusAt(v);
                double dr = (GoldenEgg.radiusAt(v + 1e-4) - GoldenEgg.radiusAt(v - 1e-4)) / 2e-4;
                for (int j = 0; j <= LON; j++) {
                    double th = 2 * Math.PI * j / LON;
                    double c = Math.cos(th), s = Math.sin(th);
                    int k = i * cols + j;
                    pos[k * 3] = (float) (r * c);
                    pos[k * 3 + 1] = (float) (h * v);
                    pos[k * 3 + 2] = (float) (r * s);
                    // The outward normal of (r(v) cos, h v, r(v) sin): (h cos, -r', h sin), normalised. At the poles
                    // the ring has no width, so the normal is straight up or down.
                    double nx = h * c, ny = -dr, nz = h * s;
                    if (i == 0 || i == LAT) {
                        nx = 0;
                        nz = 0;
                        ny = i == 0 ? 1 : -1;
                    }
                    double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    nrm[k * 3] = (float) (nx / len);
                    nrm[k * 3 + 1] = (float) (ny / len);
                    nrm[k * 3 + 2] = (float) (nz / len);
                    uv[k * 2] = (float) j / LON;
                    uv[k * 2 + 1] = (float) i / LAT;
                }
            }
            return new Mesh(pos, nrm, uv);
        }

        void emit(VertexConsumer vc, PoseStack.Pose pose, int light) {
            Matrix4f m = pose.pose();
            int cols = LON + 1;
            for (int i = 0; i < LAT; i++) {
                for (int j = 0; j < LON; j++) {
                    vertex(vc, pose, m, i * cols + j, light);
                    vertex(vc, pose, m, i * cols + j + 1, light);
                    vertex(vc, pose, m, (i + 1) * cols + j + 1, light);
                    vertex(vc, pose, m, (i + 1) * cols + j, light);
                }
            }
        }

        private void vertex(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, int k, int light) {
            vc.addVertex(m, pos[k * 3], pos[k * 3 + 1], pos[k * 3 + 2])
                    .setColor(0xFFFFFFFF).setUv(uv[k * 2], uv[k * 2 + 1]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light).setNormal(pose, nrm[k * 3], nrm[k * 3 + 1], nrm[k * 3 + 2]);
        }
    }
}
