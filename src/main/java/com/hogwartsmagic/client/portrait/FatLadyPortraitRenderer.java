package com.hogwartsmagic.client.portrait;

import com.hogwartsmagic.portrait.FatLadyPortraitEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Fat Lady like a vanilla painting (same orientation, a 2 x 4 canvas), but as a paper puppet: one grid
 * mesh over the canvas, deformed every frame ({@link PuppetMesh}), with the face-part overlays blended on top by
 * their weights ({@link PortraitAnim}). A video line draws the Flow clip's current frame as a flat picture
 * instead. A gilded frame runs round the canvas and a back closes the box.
 *
 * <p>The light is the world light in front of each quarter of the canvas (2 x 4 tiles, like a painting), so night
 * and torches show. Image-right is local -x here, exactly as in the vanilla {@code PaintingRenderer}.
 */
public class FatLadyPortraitRenderer extends EntityRenderer<FatLadyPortraitEntity> {
    private static final ResourceLocation[] LAYER_TEXTURES = new ResourceLocation[PortraitAnim.LAYERS];
    private static final ResourceLocation FRAME_TEXTURE = PortraitAssets.texture("frame");

    static {
        for (int i = 0; i < PortraitAnim.LAYERS; i++) {
            LAYER_TEXTURES[i] = PortraitAssets.texture(PortraitAnim.LAYER_TEXTURE[i]);
        }
    }

    /** The painting plane, and how far each overlay sits in front of the one before it. */
    private static final float CANVAS_Z = -0.03125f;
    private static final float LAYER_STEP = 0.0025f;
    /** Frame depth: its face stands this far in front of the canvas, its back is the entity's back. */
    private static final float FRAME_FRONT_Z = -0.0625f;
    private static final float BACK_Z = 0.03125f;
    private static final float FRAME_WIDTH = 0.1f;
    /** The face parts all sit in the top part of the painting: overlays need not cover the rest of the grid. */
    private static final int OVERLAY_ROWS = 26;
    private static final float MIN_WEIGHT = 0.02f;

    private final float[] meshX = new float[PuppetMesh.COUNT];
    private final float[] meshY = new float[PuppetMesh.COUNT];
    private final int[] lights = new int[8];

    public FatLadyPortraitRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(FatLadyPortraitEntity entity) {
        return LAYER_TEXTURES[PortraitAnim.BASE];
    }

    @Override
    public void render(FatLadyPortraitEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        long now = System.nanoTime();
        PortraitAnim anim = PortraitClient.anim(entity);
        anim.update(entity, partialTick, now);
        sampleLights(entity);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        PoseStack.Pose p = pose.last();

        boolean video = false;
        int frame = anim.videoFrame(now);
        if (frame >= 0 && anim.line != null && anim.clip != null) {
            ResourceLocation texture = PortraitVideo.use(anim.line.clip(), anim.clip, frame, now);
            if (PortraitAssets.exists(texture)) {
                drawVideo(buffers, p, texture);
                video = true;
            }
        }
        if (!video) {
            drawPuppet(buffers, p, anim);
        }
        drawFrame(buffers, p);

        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    // ---------------------------------------------------------------- light

    /** Packed light in front of each of the 2 x 4 tiles (index = row * 2 + column, row 0 on top, column 0 left). */
    private void sampleLights(FatLadyPortraitEntity entity) {
        Level level = entity.level();
        Vec3 c = entity.position();
        Vec3 f = entity.frontVector();
        double rightX = f.z, rightZ = -f.x; // image-right = facing.getCounterClockWise()
        for (int row = 0; row < 4; row++) {
            double y = c.y + 1.5 - row;
            for (int col = 0; col < 2; col++) {
                double side = col == 0 ? -0.5 : 0.5;
                BlockPos pos = BlockPos.containing(c.x + rightX * side + f.x * 0.3, y, c.z + rightZ * side + f.z * 0.3);
                lights[row * 2 + col] = LevelRenderer.getLightColor(level, pos);
            }
        }
    }

    // ---------------------------------------------------------------- the puppet

    private void drawPuppet(MultiBufferSource buffers, PoseStack.Pose p, PortraitAnim anim) {
        PuppetMesh.deform(anim, meshX, meshY);
        VertexConsumer base = buffers.getBuffer(RenderType.entitySolid(LAYER_TEXTURES[PortraitAnim.BASE]));
        grid(base, p, PuppetMesh.GY - 1, CANVAS_Z, 255);
        int depth = 1;
        for (int layer = PortraitAnim.HAPPY; layer < PortraitAnim.LAYERS; layer++) {
            float weight = anim.w[layer];
            if (weight < MIN_WEIGHT || !PortraitAssets.exists(LAYER_TEXTURES[layer])) {
                continue;
            }
            VertexConsumer overlay = buffers.getBuffer(RenderType.entityTranslucent(LAYER_TEXTURES[layer]));
            grid(overlay, p, OVERLAY_ROWS, CANVAS_Z - LAYER_STEP * depth, Math.min(255, Math.round(weight * 255f)));
            depth++;
        }
    }

    /** Emits the first {@code rows} rows of the deformed grid as quads. */
    private void grid(VertexConsumer vc, PoseStack.Pose p, int rows, float z, int alpha) {
        int gx = PuppetMesh.GX;
        for (int j = 0; j < rows; j++) {
            int tileRow = Math.min(3, j / 11);
            for (int i = 0; i < gx - 1; i++) {
                int light = lights[tileRow * 2 + (i < 12 ? 0 : 1)];
                int a = j * gx + i, b = a + 1, c = a + gx, d = c + 1;
                // bottom-left, bottom-right, top-right, top-left as the viewer sees it (vanilla winding)
                gridVertex(vc, p, c, z, alpha, light);
                gridVertex(vc, p, d, z, alpha, light);
                gridVertex(vc, p, b, z, alpha, light);
                gridVertex(vc, p, a, z, alpha, light);
            }
        }
    }

    private void gridVertex(VertexConsumer vc, PoseStack.Pose p, int k, float z, int alpha, int light) {
        vertex(vc, p, -meshX[k], meshY[k], z, PuppetMesh.u(k), PuppetMesh.v(k), alpha, light, 0f, 0f, -1f);
    }

    // ---------------------------------------------------------------- the video

    /** The clip frame as a flat picture over the whole canvas (2 x 4 tiles so each takes its own light). */
    private void drawVideo(MultiBufferSource buffers, PoseStack.Pose p, ResourceLocation texture) {
        VertexConsumer vc = buffers.getBuffer(PortraitRenderTypes.SMOOTH_SOLID.apply(texture));
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 2; col++) {
                int light = lights[row * 2 + col];
                float x0 = -1f + col, x1 = x0 + 1f;   // image-right coordinates
                float y1 = 2f - row, y0 = y1 - 1f;
                float u0 = col * 0.5f, u1 = u0 + 0.5f, v0 = row * 0.25f, v1 = v0 + 0.25f;
                vertex(vc, p, -x0, y0, CANVAS_Z, u0, v1, 255, light, 0f, 0f, -1f);
                vertex(vc, p, -x1, y0, CANVAS_Z, u1, v1, 255, light, 0f, 0f, -1f);
                vertex(vc, p, -x1, y1, CANVAS_Z, u1, v0, 255, light, 0f, 0f, -1f);
                vertex(vc, p, -x0, y1, CANVAS_Z, u0, v0, 255, light, 0f, 0f, -1f);
            }
        }
    }

    // ---------------------------------------------------------------- frame and back

    private void drawFrame(MultiBufferSource buffers, PoseStack.Pose p) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FRAME_TEXTURE));
        float b = FRAME_WIDTH;
        int top = lights[0], bottom = lights[6];
        // four strips round the canvas edge, standing proud of the painting
        strip(vc, p, -1f, 1f, 2f - b, 2f, top);
        strip(vc, p, -1f, 1f, -2f, -2f + b, bottom);
        strip(vc, p, -1f, -1f + b, -2f + b, 2f - b, lights[2]);
        strip(vc, p, 1f - b, 1f, -2f + b, 2f - b, lights[3]);
        // the back, for when the wall behind her is gone
        int light = lights[2];
        vertex(vc, p, 1f, -2f, BACK_Z, 0f, 0f, 255, light, 0f, 0f, 1f);
        vertex(vc, p, -1f, -2f, BACK_Z, 2f, 0f, 255, light, 0f, 0f, 1f);
        vertex(vc, p, -1f, 2f, BACK_Z, 2f, 4f, 255, light, 0f, 0f, 1f);
        vertex(vc, p, 1f, 2f, BACK_Z, 0f, 4f, 255, light, 0f, 0f, 1f);
    }

    /** One box of the frame, x0..x1 / y0..y1 in image coordinates (x toward the image's right). */
    private void strip(VertexConsumer vc, PoseStack.Pose p, float x0, float x1, float y0, float y1, int light) {
        // front face
        vertex(vc, p, -x0, y0, FRAME_FRONT_Z, x0, y0, 255, light, 0f, 0f, -1f);
        vertex(vc, p, -x1, y0, FRAME_FRONT_Z, x1, y0, 255, light, 0f, 0f, -1f);
        vertex(vc, p, -x1, y1, FRAME_FRONT_Z, x1, y1, 255, light, 0f, 0f, -1f);
        vertex(vc, p, -x0, y1, FRAME_FRONT_Z, x0, y1, 255, light, 0f, 0f, -1f);
        // side faces: outer ones run to the back of the box, inner ones only down to the painting
        sideX(vc, p, x0, y0, y1, BACK_Z, light, -1f); // image-left side, facing image-left
        sideX(vc, p, x1, y0, y1, BACK_Z, light, 1f);
        sideY(vc, p, y0, x0, x1, BACK_Z, light, -1f);
        sideY(vc, p, y1, x0, x1, BACK_Z, light, 1f);
    }

    /** A face at image x = x, between y0..y1, from the frame front to z; faces image direction {@code dir}. */
    private void sideX(VertexConsumer vc, PoseStack.Pose p, float x, float y0, float y1, float zBack, int light,
                       float dir) {
        float nx = -dir; // local x is minus image x
        float zEnd = Math.abs(x) > 0.99f ? zBack : CANVAS_Z; // on the canvas edge: outer face; else inner
        vertex(vc, p, -x, y0, FRAME_FRONT_Z, 0f, y0, 255, light, nx, 0f, 0f);
        vertex(vc, p, -x, y0, zEnd, 1f, y0, 255, light, nx, 0f, 0f);
        vertex(vc, p, -x, y1, zEnd, 1f, y1, 255, light, nx, 0f, 0f);
        vertex(vc, p, -x, y1, FRAME_FRONT_Z, 0f, y1, 255, light, nx, 0f, 0f);
    }

    private void sideY(VertexConsumer vc, PoseStack.Pose p, float y, float x0, float x1, float zBack, int light,
                       float dir) {
        float zEnd = Math.abs(y) > 1.99f ? zBack : CANVAS_Z;
        vertex(vc, p, -x0, y, FRAME_FRONT_Z, x0, 0f, 255, light, 0f, dir, 0f);
        vertex(vc, p, -x1, y, FRAME_FRONT_Z, x1, 0f, 255, light, 0f, dir, 0f);
        vertex(vc, p, -x1, y, zEnd, x1, 1f, 255, light, 0f, dir, 0f);
        vertex(vc, p, -x0, y, zEnd, x0, 1f, 255, light, 0f, dir, 0f);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v,
                               int alpha, int light, float nx, float ny, float nz) {
        vc.addVertex(p, x, y, z)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(p, nx, ny, nz);
    }
}
