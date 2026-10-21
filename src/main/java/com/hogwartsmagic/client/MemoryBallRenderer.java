package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.MemoryBallEntity;
import com.hogwartsmagic.client.pensieve.PensieveClientState;
import com.hogwartsmagic.pensieve.scene.MemoryBall;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Draws the memory balls: the scuffed red Quaffle, the dented black Bludger and the Remembrall's glass with white smoke
 * swirling inside ({@link MemoryBall} holds the sizes, the roll, the dents, the shake and the smoke). Each is one
 * textured UV sphere emitted straight into one buffer (the Remembrall's glass and smoke into one more): batched, no
 * model parts, no layers, and lit by the world's light like any entity (never full-bright). The PoseStack arrives
 * translated to the ball centre and scaled by the actor scale, so everything is drawn at scale 1 about the origin.
 * Textures from {@code build_scripts/generate_memory_ball_textures.py}.
 */
public class MemoryBallRenderer extends EntityRenderer<MemoryBallEntity> {
    private static final int LAT = 14, LON = 24;
    private static final int GLASS_LAT = 12, GLASS_LON = 20, PUFF_LAT = 5, PUFF_LON = 8;
    /** The glass shell's colour (white, mostly transparent; the texture carries the tint and highlights). */
    private static final int WHITE = 0xFFFFFFFF;
    /** Smoke puffs are soft: alpha 0.55 of the white. */
    private static final int SMOKE = 0x8CFFFFFF;

    private static final java.util.Map<String, Mesh> MESHES = new java.util.concurrent.ConcurrentHashMap<>();

    private final ResourceLocation texture;
    private final ResourceLocation smokeTexture;

    private MemoryBallRenderer(EntityRendererProvider.Context context, String texture, String smokeTexture) {
        super(context);
        this.shadowRadius = 0f;
        this.texture = tex(texture);
        this.smokeTexture = smokeTexture == null ? null : tex(smokeTexture);
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/" + name + ".png");
    }

    public static MemoryBallRenderer quaffle(EntityRendererProvider.Context context) {
        return new MemoryBallRenderer(context, "memory_quaffle", null);
    }

    public static MemoryBallRenderer bludger(EntityRendererProvider.Context context) {
        return new MemoryBallRenderer(context, "memory_bludger", null);
    }

    public static MemoryBallRenderer remembrall(EntityRendererProvider.Context context) {
        return new MemoryBallRenderer(context, "memory_remembrall", "memory_remembrall_smoke");
    }

    @Override
    public ResourceLocation getTextureLocation(MemoryBallEntity entity) {
        return texture;
    }

    @Override
    public void render(MemoryBallEntity ball, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        String type = ball.ballType();
        float r = (float) (MemoryBall.diameter(type) / 2);
        pose.pushPose();
        if (smokeTexture != null) {
            drawGlass(ball, r, partialTick, pose, buffers, light);
        } else {
            // The Bludger's tremor, in blocks at scale 1 (the stack's scale is already on).
            double[] shake = MemoryBall.vibration(ball.speed, ball.tickCount + partialTick);
            pose.translate(shake[0], shake[1], shake[2]);
            pose.mulPose(spin(ball, partialTick));
            Mesh mesh = MESHES.computeIfAbsent(type, k -> Mesh.build(LAT, LON, k));
            mesh.emit(buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), pose.last(), r, light, WHITE);
        }
        pose.popPose();
        super.render(ball, yaw, partialTick, pose, buffers, light);
    }

    /** The smoke first (inside), then the glass over it; the glass never turns, so its highlights stay put. */
    private void drawGlass(MemoryBallEntity ball, float r, float partial, PoseStack pose, MultiBufferSource buffers,
                           int light) {
        Mesh puff = MESHES.computeIfAbsent("puff", k -> Mesh.build(PUFF_LAT, PUFF_LON, k));
        VertexConsumer smoke = buffers.getBuffer(RenderType.entityTranslucent(smokeTexture));
        double t = PensieveClientState.sceneTime(partial);
        for (int i = 0; i < MemoryBall.PUFFS; i++) {
            double[] p = MemoryBall.smoke(ball.seed, i, t);
            pose.pushPose();
            pose.translate(p[0] * r, p[1] * r, p[2] * r);
            puff.emit(smoke, pose.last(), (float) (p[3] * r), light, SMOKE);
            pose.popPose();
        }
        Mesh glass = MESHES.computeIfAbsent("glass", k -> Mesh.build(GLASS_LAT, GLASS_LON, k));
        glass.emit(buffers.getBuffer(RenderType.entityTranslucent(texture)), pose.last(), r, light, WHITE);
    }

    /** The interpolated roll as a quaternion. */
    private static Quaternionf spin(MemoryBallEntity ball, float partial) {
        double[] a = ball.spinO.quaternion(), b = ball.spin.quaternion();
        Quaternionf qa = new Quaternionf((float) a[1], (float) a[2], (float) a[3], (float) a[0]);
        Quaternionf qb = new Quaternionf((float) b[1], (float) b[2], (float) b[3], (float) b[0]);
        return qa.slerp(qb, partial);
    }

    /**
     * A sphere's vertices as flat arrays (position and normal on the unit sphere, with the type's dents on the
     * position), built once per shape and emitted as quads wound as MemoryBall.unit says.
     */
    private record Mesh(int lat, int lon, float[] pos, float[] nrm, float[] uv) {
        static Mesh build(int lat, int lon, String type) {
            int cols = lon + 1, n = (lat + 1) * cols;
            float[] pos = new float[n * 3], nrm = new float[n * 3], uv = new float[n * 2];
            double[][] p = new double[n][];
            for (int i = 0; i <= lat; i++) {
                for (int j = 0; j <= lon; j++) {
                    double[] u = MemoryBall.unit(i, j, lat, lon);
                    double f = MemoryBall.dent(type, u[0], u[1], u[2]);
                    int k = i * cols + j;
                    p[k] = new double[]{u[0] * f, u[1] * f, u[2] * f};
                    uv[k * 2] = (float) j / lon;
                    uv[k * 2 + 1] = (float) i / lat;
                }
            }
            for (int i = 0; i <= lat; i++) {
                for (int j = 0; j <= lon; j++) {
                    int k = i * cols + j;
                    double[] u = MemoryBall.unit(i, j, lat, lon);
                    double[] nn = u;
                    if (i > 0 && i < lat) {
                        double[] e = p[i * cols + (j + 1) % lon];
                        double[] w = p[i * cols + (j - 1 + lon) % lon];
                        double[] dn = p[(i + 1) * cols + j], up = p[(i - 1) * cols + j];
                        double[] a = {e[0] - w[0], e[1] - w[1], e[2] - w[2]};
                        double[] b = {dn[0] - up[0], dn[1] - up[1], dn[2] - up[2]};
                        double[] c = {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
                        double len = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
                        if (len > 1e-12) {
                            nn = new double[]{c[0] / len, c[1] / len, c[2] / len};
                            if (nn[0] * u[0] + nn[1] * u[1] + nn[2] * u[2] < 0) nn = new double[]{-nn[0], -nn[1], -nn[2]};
                        }
                    }
                    for (int c = 0; c < 3; c++) {
                        pos[k * 3 + c] = (float) p[k][c];
                        nrm[k * 3 + c] = (float) nn[c];
                    }
                }
            }
            return new Mesh(lat, lon, pos, nrm, uv);
        }

        void emit(VertexConsumer vc, PoseStack.Pose pose, float radius, int light, int argb) {
            Matrix4f m = pose.pose();
            int cols = lon + 1;
            for (int i = 0; i < lat; i++) {
                for (int j = 0; j < lon; j++) {
                    vertex(vc, pose, m, i * cols + j, radius, light, argb);
                    vertex(vc, pose, m, i * cols + j + 1, radius, light, argb);
                    vertex(vc, pose, m, (i + 1) * cols + j + 1, radius, light, argb);
                    vertex(vc, pose, m, (i + 1) * cols + j, radius, light, argb);
                }
            }
        }

        private void vertex(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, int k, float radius, int light, int argb) {
            vc.addVertex(m, pos[k * 3] * radius, pos[k * 3 + 1] * radius, pos[k * 3 + 2] * radius)
                    .setColor(argb).setUv(uv[k * 2], uv[k * 2 + 1]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light).setNormal(pose, nrm[k * 3], nrm[k * 3 + 1], nrm[k * 3 + 2]);
        }
    }
}
