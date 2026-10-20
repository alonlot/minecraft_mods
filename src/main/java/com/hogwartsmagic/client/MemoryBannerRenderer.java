package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.MemoryBannerEntity;
import com.hogwartsmagic.pensieve.scene.BannerCloth;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Draws a hanging cloth banner ({@code memory_banner_<design>}): a {@link BannerCloth#COLS} x {@link BannerCloth#ROWS}
 * quad strip hanging from a gold rod at the actor's position, swaying softly ({@link BannerCloth#offset}), with its
 * normals taken from the swayed mesh so the folds catch the light. The front (the art reading normally) faces the
 * actor's yaw; the back is drawn as its own faces with the art flipped so it reads normally from behind too. The
 * point and the fringe are cut out by the texture's alpha.
 *
 * <p>The actor {@code scale} is already on the PoseStack when Pensieve calls this (MemoryActors scales the stack
 * before dispatching), so the geometry is built at scale 1. The sway runs off the level game time, which holds
 * still while paused, and a seed from the actor's id (its Pensieve UUID).
 */
public class MemoryBannerRenderer extends EntityRenderer<MemoryBannerEntity> {
    /** The gold sleeve band the rod is textured from (keep in step with generate_banner_textures.py SLEEVE). */
    private static final float ROD_V0 = 3f / 512f, ROD_V1 = 15f / 512f;
    private static final float ROD_HALF = 0.045f, ROD_Y = -0.1f, ROD_OVERHANG = 0.14f, KNOB = 0.07f;
    /** Light bands down the cloth: sampled at these fractions of its length and blended between. */
    private static final float[] BANDS = {0f, 0.25f, 0.5f, 0.75f, 1f};

    private final ResourceLocation texture;

    public MemoryBannerRenderer(EntityRendererProvider.Context context, String design) {
        super(context);
        this.texture = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID,
                "textures/entity/memory_banner_" + design + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(MemoryBannerEntity entity) {
        return texture;
    }

    @Override
    public void render(MemoryBannerEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        UUID id = entity.getUUID();
        float[] params = BannerCloth.params(BannerCloth.seed(id.getMostSignificantBits(), id.getLeastSignificantBits()));
        Level level = entity.level();
        float t = level == null ? 0f : (level.getGameTime() + partialTick) / 20f;

        // The light the caller passes is sampled at a point near the rod, often in the dark under a ceiling: take
        // the brightest light round each band of the cloth instead and blend down its length.
        float scale = pose.last().pose().getScale(new Vector3f()).x;
        float[][] bands = sampleLight(entity, yaw, scale == 0f ? 1f : scale, light);

        int cols = BannerCloth.COLS, rows = BannerCloth.ROWS;
        float w = BannerCloth.WIDTH, h = BannerCloth.HEIGHT;
        float[][][] pos = new float[cols + 1][rows + 1][];
        for (int i = 0; i <= cols; i++) {
            float u = i / (float) cols;
            for (int j = 0; j <= rows; j++) {
                float s = j / (float) rows;
                float[] o = BannerCloth.offset(params, u, s, t);
                pos[i][j] = new float[]{(u - 0.5f) * w + o[0], -s * h + o[1], o[2]};
            }
        }

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        PoseStack.Pose p = pose.last();
        Matrix4f m = p.pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(texture));
        int[] rowLight = new int[rows + 1];
        for (int j = 0; j <= rows; j++) rowLight[j] = blend(bands, j / (float) rows);
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                // front: counter-clockwise seen from +z (bottom-left, bottom-right, top-right, top-left)
                int[][] q = {{i, j + 1}, {i + 1, j + 1}, {i + 1, j}, {i, j}};
                for (int[] c : q) {
                    float[] n = normal(pos, c[0], c[1]);
                    vertex(vc, m, p, pos[c[0]][c[1]], c[0] / (float) cols, c[1] / (float) rows, n, 1f, rowLight[c[1]]);
                }
                // back: the same corners wound the other way, the art flipped across so it reads from behind
                for (int k = 3; k >= 0; k--) {
                    int[] c = q[k];
                    float[] n = normal(pos, c[0], c[1]);
                    vertex(vc, m, p, pos[c[0]][c[1]], 1f - c[0] / (float) cols, c[1] / (float) rows, n, -1f,
                            rowLight[c[1]]);
                }
            }
        }
        rod(vc, m, p, w, rowLight[0]);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /** The unit normal of the swayed cloth at grid corner (i, j), toward the front. */
    private static float[] normal(float[][][] pos, int i, int j) {
        int cols = pos.length - 1, rows = pos[0].length - 1;
        float[] a = pos[Math.max(0, i - 1)][j], b = pos[Math.min(cols, i + 1)][j];
        float[] up = pos[i][Math.max(0, j - 1)], dn = pos[i][Math.min(rows, j + 1)];
        float ax = b[0] - a[0], ay = b[1] - a[1], az = b[2] - a[2];          // across (+x)
        float vx = up[0] - dn[0], vy = up[1] - dn[1], vz = up[2] - dn[2];    // up (+y)
        float nx = ay * vz - az * vy, ny = az * vx - ax * vz, nz = ax * vy - ay * vx;
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) return new float[]{0f, 0f, 1f};
        return new float[]{nx / len, ny / len, nz / len};
    }

    /** The hanging rod: a thin gold box across the top, a little wider than the cloth, with a knob at each end. */
    private static void rod(VertexConsumer vc, Matrix4f m, PoseStack.Pose p, float w, int light) {
        float x1 = w / 2f + ROD_OVERHANG;
        box(vc, m, p, -x1, ROD_Y - ROD_HALF, -ROD_HALF, x1, ROD_Y + ROD_HALF, ROD_HALF, light);
        for (float sx : new float[]{-1f, 1f}) {
            float cx = sx * (x1 + KNOB * 0.6f);
            box(vc, m, p, cx - KNOB, ROD_Y - KNOB, -KNOB, cx + KNOB, ROD_Y + KNOB, KNOB, light);
        }
    }

    private static void box(VertexConsumer vc, Matrix4f m, PoseStack.Pose p, float x0, float y0, float z0,
                            float x1, float y1, float z1, int light) {
        float[][] c = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0},
                {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
        // each face counter-clockwise from outside: front (+z), back (-z), right (+x), left (-x), top, bottom
        int[][] faces = {{4, 5, 6, 7}, {1, 0, 3, 2}, {5, 1, 2, 6}, {0, 4, 7, 3}, {7, 6, 2, 3}, {0, 1, 5, 4}};
        float[][] normals = {{0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
        float[][] uv = {{0.05f, ROD_V1}, {0.95f, ROD_V1}, {0.95f, ROD_V0}, {0.05f, ROD_V0}};
        for (int f = 0; f < faces.length; f++) {
            for (int k = 0; k < 4; k++) {
                float[] v = c[faces[f][k]];
                vc.addVertex(m, v[0], v[1], v[2]).setColor(255, 255, 255, 255).setUv(uv[k][0], uv[k][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(p, normals[f][0], normals[f][1], normals[f][2]);
            }
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose p, float[] pos, float u, float v,
                               float[] n, float side, int light) {
        vc.addVertex(m, pos[0], pos[1], pos[2]).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(p, n[0] * side, n[1] * side, n[2] * side);
    }

    /**
     * {block, sky} light per {@link #BANDS} entry: the brightest of the cells across the cloth (both edges and the
     * middle) and a block in front of and behind it, at that fraction of the cloth's length below the rod.
     */
    private static float[][] sampleLight(MemoryBannerEntity entity, float yaw, float scale, int fallback) {
        float[][] out = new float[BANDS.length][];
        Level level = entity.level();
        if (level == null) {
            for (int b = 0; b < BANDS.length; b++) {
                out[b] = new float[]{LightTexture.block(fallback), LightTexture.sky(fallback)};
            }
            return out;
        }
        double r = Math.toRadians(yaw);
        double fx = -Math.sin(r), fz = Math.cos(r);         // the front's facing
        double ax = Math.cos(r), az = Math.sin(r);          // across the cloth
        double half = BannerCloth.WIDTH * 0.5 * scale;
        double[][] offsets = {{0, 0}, {half, 0}, {-half, 0}, {0, 1}, {0, -1}, {half, 1}, {-half, 1}, {half, -1},
                {-half, -1}};
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        for (int b = 0; b < BANDS.length; b++) {
            double y = entity.getY() - (BANDS[b] * 0.94 + 0.03) * BannerCloth.HEIGHT * scale;
            int block = 0, sky = 0;
            for (double[] o : offsets) {
                double x = entity.getX() + ax * o[0] + fx * o[1];
                double z = entity.getZ() + az * o[0] + fz * o[1];
                int l = LevelRenderer.getLightColor(level, bp.set(Mth.floor(x), Mth.floor(y), Mth.floor(z)));
                block = Math.max(block, LightTexture.block(l));
                sky = Math.max(sky, LightTexture.sky(l));
            }
            out[b] = new float[]{block, sky};
        }
        return out;
    }

    /** The packed light at fraction {@code s} down the cloth, blended between the bands (lightmap texels are 1/16 a level). */
    private static int blend(float[][] bands, float s) {
        int b = 0;
        while (b < BANDS.length - 2 && s > BANDS[b + 1]) b++;
        float f = Mth.clamp((s - BANDS[b]) / (BANDS[b + 1] - BANDS[b]), 0f, 1f);
        float block = Mth.lerp(f, bands[b][0], bands[b + 1][0]);
        float sky = Mth.lerp(f, bands[b][1], bands[b + 1][1]);
        int bu = Mth.clamp(Math.round(block * 16f), 0, 240);
        int sv = Mth.clamp(Math.round(sky * 16f), 0, 240);
        return bu | sv << 16;
    }
}
