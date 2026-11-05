package com.hogwartsmagic.client.portrait;

import com.hogwartsmagic.portrait.PortraitEntity;
import com.hogwartsmagic.portrait.PortraitKind;
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
 * Draws a living portrait like a vanilla painting (same orientation, a canvas of the kind's size: 2 x 4 for the Fat
 * Lady, 4 x 2 for Sir Cadogan), but as a paper puppet: one grid mesh over the canvas, deformed every frame
 * ({@link PuppetMesh}), with the face-part overlays blended on top by their weights ({@link PortraitAnim}). A video
 * line draws the Flow clip's current frame as a flat picture instead. A gilded frame runs round the canvas and a
 * back closes the box. One renderer instance serves one kind.
 *
 * <p>The light is the world light in front of each block of the canvas (like a painting), so night and torches
 * show. Image-right is local -x here, exactly as in the vanilla {@code PaintingRenderer}.
 */
public class PortraitRenderer extends EntityRenderer<PortraitEntity> {
    /** The painting plane, and how far each overlay sits in front of the one before it. */
    private static final float CANVAS_Z = -0.03125f;
    private static final float LAYER_STEP = 0.0025f;
    /** Frame depth: its face stands this far in front of the canvas, its back is the entity's back. */
    private static final float FRAME_FRONT_Z = -0.0625f;
    private static final float BACK_Z = 0.03125f;
    private static final float FRAME_WIDTH = 0.1f;
    private static final float MIN_WEIGHT = 0.02f;

    private final PortraitKind kind;
    private final PuppetMesh mesh;
    private final ResourceLocation[] layerTextures = new ResourceLocation[PortraitAnim.LAYERS];
    private final ResourceLocation frameTexture;
    /** Canvas size in blocks and half of it (the canvas runs from -half to +half in image coordinates). */
    private final int tilesX, tilesY;
    private final float halfW, halfH;
    /** Mesh cells per light tile, horizontally and vertically. */
    private final int cellsPerTileX, cellsPerTileY;

    private final float[] meshX, meshY;
    private final int[] lights;

    public PortraitRenderer(EntityRendererProvider.Context context, PortraitKind kind) {
        super(context);
        this.kind = kind;
        this.mesh = new PuppetMesh(kind);
        for (int i = 0; i < PortraitAnim.LAYERS; i++) {
            layerTextures[i] = PortraitAssets.texture(kind, PortraitAnim.LAYER_TEXTURE[i]);
        }
        this.frameTexture = PortraitAssets.texture(kind, "frame");
        this.tilesX = kind.widthBlocks();
        this.tilesY = kind.heightBlocks();
        this.halfW = tilesX / 2f;
        this.halfH = tilesY / 2f;
        this.cellsPerTileX = (mesh.gx - 1) / tilesX;
        this.cellsPerTileY = (mesh.gy - 1) / tilesY;
        this.meshX = new float[mesh.count];
        this.meshY = new float[mesh.count];
        this.lights = new int[tilesX * tilesY];
    }

    @Override
    public ResourceLocation getTextureLocation(PortraitEntity entity) {
        return layerTextures[PortraitAnim.BASE];
    }

    @Override
    public void render(PortraitEntity entity, float yaw, float partialTick, PoseStack pose,
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
            ResourceLocation texture = PortraitVideo.use(kind, anim.line.clip(), anim.clip, frame, now);
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

    /** Packed light in front of each tile (index = row * tilesX + column, row 0 on top, column 0 on the left). */
    private void sampleLights(PortraitEntity entity) {
        Level level = entity.level();
        Vec3 c = entity.position();
        Vec3 f = entity.frontVector();
        double rightX = f.z, rightZ = -f.x; // image-right = facing.getCounterClockWise()
        for (int row = 0; row < tilesY; row++) {
            double y = c.y + (tilesY - 1) / 2.0 - row;
            for (int col = 0; col < tilesX; col++) {
                double side = col - (tilesX - 1) / 2.0;
                BlockPos pos = BlockPos.containing(c.x + rightX * side + f.x * 0.3, y, c.z + rightZ * side + f.z * 0.3);
                lights[row * tilesX + col] = LevelRenderer.getLightColor(level, pos);
            }
        }
    }

    // ---------------------------------------------------------------- the puppet

    private void drawPuppet(MultiBufferSource buffers, PoseStack.Pose p, PortraitAnim anim) {
        mesh.deform(anim, meshX, meshY);
        VertexConsumer base = buffers.getBuffer(RenderType.entitySolid(layerTextures[PortraitAnim.BASE]));
        grid(base, p, mesh.gy - 1, CANVAS_Z, 255);
        int depth = 1;
        for (int layer = PortraitAnim.HAPPY; layer < PortraitAnim.LAYERS; layer++) {
            float weight = anim.w[layer];
            if (weight < MIN_WEIGHT || !PortraitAssets.exists(layerTextures[layer])) {
                continue;
            }
            VertexConsumer overlay = buffers.getBuffer(RenderType.entityTranslucent(layerTextures[layer]));
            grid(overlay, p, mesh.overlayRows, CANVAS_Z - LAYER_STEP * depth, Math.min(255, Math.round(weight * 255f)));
            depth++;
        }
    }

    /** Emits the first {@code rows} rows of the deformed grid as quads. */
    private void grid(VertexConsumer vc, PoseStack.Pose p, int rows, float z, int alpha) {
        int gx = mesh.gx;
        for (int j = 0; j < rows; j++) {
            int tileRow = Math.min(tilesY - 1, j / cellsPerTileY);
            for (int i = 0; i < gx - 1; i++) {
                int tileCol = Math.min(tilesX - 1, i / cellsPerTileX);
                int light = lights[tileRow * tilesX + tileCol];
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
        vertex(vc, p, -meshX[k], meshY[k], z, mesh.u(k), mesh.v(k), alpha, light, 0f, 0f, -1f);
    }

    // ---------------------------------------------------------------- the video

    /** The clip frame as a flat picture over the whole canvas (one quad per block so each takes its own light). */
    private void drawVideo(MultiBufferSource buffers, PoseStack.Pose p, ResourceLocation texture) {
        VertexConsumer vc = buffers.getBuffer(PortraitRenderTypes.SMOOTH_SOLID.apply(texture));
        for (int row = 0; row < tilesY; row++) {
            for (int col = 0; col < tilesX; col++) {
                int light = lights[row * tilesX + col];
                float x0 = -halfW + col, x1 = x0 + 1f;   // image-right coordinates
                float y1 = halfH - row, y0 = y1 - 1f;
                float u0 = col / (float) tilesX, u1 = (col + 1) / (float) tilesX;
                float v0 = row / (float) tilesY, v1 = (row + 1) / (float) tilesY;
                vertex(vc, p, -x0, y0, CANVAS_Z, u0, v1, 255, light, 0f, 0f, -1f);
                vertex(vc, p, -x1, y0, CANVAS_Z, u1, v1, 255, light, 0f, 0f, -1f);
                vertex(vc, p, -x1, y1, CANVAS_Z, u1, v0, 255, light, 0f, 0f, -1f);
                vertex(vc, p, -x0, y1, CANVAS_Z, u0, v0, 255, light, 0f, 0f, -1f);
            }
        }
    }

    // ---------------------------------------------------------------- frame and back

    private void drawFrame(MultiBufferSource buffers, PoseStack.Pose p) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(frameTexture));
        float b = FRAME_WIDTH;
        int top = lights[0], bottom = lights[(tilesY - 1) * tilesX];
        int sideRow = Math.min(1, tilesY - 1) * tilesX; // the side strips take the light of the second row
        int leftSide = lights[sideRow], rightSide = lights[sideRow + tilesX - 1];
        // four strips round the canvas edge, standing proud of the painting
        strip(vc, p, -halfW, halfW, halfH - b, halfH, top);
        strip(vc, p, -halfW, halfW, -halfH, -halfH + b, bottom);
        strip(vc, p, -halfW, -halfW + b, -halfH + b, halfH - b, leftSide);
        strip(vc, p, halfW - b, halfW, -halfH + b, halfH - b, rightSide);
        // the back, for when the wall behind her is gone
        int light = leftSide;
        vertex(vc, p, halfW, -halfH, BACK_Z, 0f, 0f, 255, light, 0f, 0f, 1f);
        vertex(vc, p, -halfW, -halfH, BACK_Z, tilesX, 0f, 255, light, 0f, 0f, 1f);
        vertex(vc, p, -halfW, halfH, BACK_Z, tilesX, tilesY, 255, light, 0f, 0f, 1f);
        vertex(vc, p, halfW, halfH, BACK_Z, 0f, tilesY, 255, light, 0f, 0f, 1f);
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
        float zEnd = Math.abs(x) > halfW - 0.01f ? zBack : CANVAS_Z; // on the canvas edge: outer face; else inner
        vertex(vc, p, -x, y0, FRAME_FRONT_Z, 0f, y0, 255, light, nx, 0f, 0f);
        vertex(vc, p, -x, y0, zEnd, 1f, y0, 255, light, nx, 0f, 0f);
        vertex(vc, p, -x, y1, zEnd, 1f, y1, 255, light, nx, 0f, 0f);
        vertex(vc, p, -x, y1, FRAME_FRONT_Z, 0f, y1, 255, light, nx, 0f, 0f);
    }

    private void sideY(VertexConsumer vc, PoseStack.Pose p, float y, float x0, float x1, float zBack, int light,
                       float dir) {
        float zEnd = Math.abs(y) > halfH - 0.01f ? zBack : CANVAS_Z;
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
