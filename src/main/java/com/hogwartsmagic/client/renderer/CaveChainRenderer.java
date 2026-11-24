package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.block.entity.CaveRingBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the cave chain from the iron ring down to the water with the vanilla chain links: two crossed
 * strips per link, sagging between the ring and the water. While revealed it glows faint green at full
 * brightness; while it is being hauled in it shortens from the water end toward the ring. Client only; it
 * reads nothing but the ring block entity synced state.
 */
@OnlyIn(Dist.CLIENT)
public class CaveChainRenderer implements BlockEntityRenderer<CaveRingBlockEntity> {
    private static final ResourceLocation CHAIN = ResourceLocation.withDefaultNamespace("block/chain");
    private static final float HALF_WIDTH = 1.5f / 16f;
    private static final float LINK = 0.5f;
    private static final float SAG_PER_BLOCK = 0.12f;
    private static final float MAX_SAG = 1.6f;
    /** Chain texture: the first plane is pixels 0..3 wide, the second 3..6, both the full 16 tall. */
    private static final float PLANE_A_U0 = 0f;
    private static final float PLANE_B_U0 = 3f;
    private static final float PLANE_WIDTH = 3f;

    private TextureAtlas cachedAtlas;
    private TextureAtlasSprite sprite;

    public CaveChainRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(CaveRingBlockEntity ring) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(CaveRingBlockEntity ring, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        BlockPos end = ring.chainEnd();
        if (end == null || !ring.revealed()) {
            return;
        }
        BlockPos at = ring.getBlockPos();
        float sx = 0.5f;
        float sy = 1.08f;
        float sz = 0.5f;
        float ex = end.getX() - at.getX() + 0.5f;
        float ey = end.getY() - at.getY() + 0.85f;
        float ez = end.getZ() - at.getZ() + 0.5f;
        float dx = ex - sx;
        float dy = ey - sy;
        float dz = ez - sz;
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.01f) {
            return;
        }
        float sag = Math.min(MAX_SAG, length * SAG_PER_BLOCK);

        float hauled = ring.haulTicks() > 0 ? ring.haulTicks() + partialTick : 0f;
        float shown = 1f - Mth.clamp(hauled / CaveRingBlockEntity.HAUL_TICKS, 0f, 1f);
        int links = Math.max(1, (int) Math.ceil(length / LINK));
        int drawn = (int) Math.ceil(links * shown);
        if (drawn <= 0) {
            return;
        }
        float time = ring.getLevel() == null ? 0f : (ring.getLevel().getGameTime() + partialTick);

        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        if (sprite == null || atlas != cachedAtlas) {
            cachedAtlas = atlas;
            sprite = atlas.getSprite(CHAIN);
        }
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        int light = LightTexture.FULL_BRIGHT;
        float pulse = 0.8f + 0.2f * Mth.sin(time * 0.12f);
        int r = (int) (110 * pulse);
        int g = (int) (255 * pulse);
        int b = (int) (140 * pulse);

        Vector3f prev = point(sx, sy, sz, dx, dy, dz, sag, 0f, time);
        for (int i = 1; i <= drawn; i++) {
            float t = Math.min(1f, i * LINK / length);
            Vector3f next = point(sx, sy, sz, dx, dy, dz, sag, t, time);
            link(vc, m, last, prev, next, PLANE_A_U0, r, g, b, light, i);
            link(vc, m, last, prev, next, PLANE_B_U0, r, g, b, light, i);
            prev = next;
        }
    }

    /** A point t of the way down the sagging line, with a slow sway that grows toward the free end. */
    private static Vector3f point(float sx, float sy, float sz, float dx, float dy, float dz, float sag, float t,
                                  float time) {
        float droop = -sag * 4f * t * (1f - t);
        float sway = 0.04f * t * Mth.sin(time * 0.07f + t * 3f);
        return new Vector3f(sx + dx * t + sway, sy + dy * t + droop, sz + dz * t - sway);
    }

    private void link(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose, Vector3f a, Vector3f b, float planeU0,
                      int r, int g, int bl, int light, int index) {
        float tx = b.x - a.x;
        float ty = b.y - a.y;
        float tz = b.z - a.z;
        float len = Mth.sqrt(tx * tx + ty * ty + tz * tz);
        if (len < 1.0E-4f) {
            return;
        }
        tx /= len;
        ty /= len;
        tz /= len;
        // A side vector across the strip: horizontal and square to the link, or the other plane (turned 90 degrees).
        float sx = -tz;
        float sy = 0f;
        float sz = tx;
        float sl = Mth.sqrt(sx * sx + sz * sz);
        if (sl < 1.0E-3f) {
            sx = 1f;
            sz = 0f;
            sl = 1f;
        }
        sx /= sl;
        sz /= sl;
        if (planeU0 == PLANE_B_U0) {
            float cx = ty * sz - tz * sy;
            float cy = tz * sx - tx * sz;
            float cz = tx * sy - ty * sx;
            sx = cx;
            sy = cy;
            sz = cz;
        }
        float ox = sx * HALF_WIDTH;
        float oy = sy * HALF_WIDTH;
        float oz = sz * HALF_WIDTH;
        float u0 = sprite.getU(planeU0 / 16f);
        float u1 = sprite.getU((planeU0 + PLANE_WIDTH) / 16f);
        // Each half-block segment shows half of the 16 px link, alternating so the pattern continues.
        float vh0 = (index & 1) == 0 ? 0f : 0.5f;
        float v0 = sprite.getV(vh0);
        float v1 = sprite.getV(vh0 + 0.5f);
        vertex(vc, m, pose, a.x - ox, a.y - oy, a.z - oz, u0, v0, r, g, bl, light);
        vertex(vc, m, pose, b.x - ox, b.y - oy, b.z - oz, u0, v1, r, g, bl, light);
        vertex(vc, m, pose, b.x + ox, b.y + oy, b.z + oz, u1, v1, r, g, bl, light);
        vertex(vc, m, pose, a.x + ox, a.y + oy, a.z + oz, u1, v0, r, g, bl, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, int r, int g, int b, int light) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0f, 1f, 0f);
    }
}
