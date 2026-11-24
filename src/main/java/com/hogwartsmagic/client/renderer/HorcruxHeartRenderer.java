package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.HorcruxBlockEntity;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.client.fx.HorcruxFx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

/**
 * A waking Horcrux's heart: lifted out of the cradle, it floats over the pedestal and beats. Through the rite it is
 * crimson and quickens with every pulse, shuddering, while a rite circle turns on the floor; once bound it is soul
 * green and beats slow and heavy. Each beat swells it, flares the glowing shell round it and the halo behind it,
 * and (through {@link HorcruxFx#seen}) the light it throws on the room. A dormant vessel draws nothing here: its
 * stone heart is part of the block model.
 */
public class HorcruxHeartRenderer implements BlockEntityRenderer<HorcruxBlockEntity> {
    private static final ResourceLocation HEART =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/block/horcrux_heart_glow.png");
    private static final RenderType FLESH = RenderType.entityCutoutNoCull(HEART);
    /** Created once: render types made per frame would grow the bloom router's cache without end. */
    private static final RenderType SHELL = ModRenderTypes.magicGlow(HEART);
    private static final RenderType GLOW = ModRenderTypes.BLOOM_GLOW;
    private static final RenderType RING = ModRenderTypes.BLOOM_RING;

    /** The heart in pixels round its centre: body, two lobes, the point, a severed vessel. {x0,y0,z0,x1,y1,z1}. */
    private static final float[][] CUBES = {
            {-1.75f, -1.0f, -1.25f, 1.75f, 1.5f, 1.25f},
            {-2.25f, 1.0f, -1.0f, -0.1f, 2.5f, 1.0f},
            {0.1f, 1.0f, -1.0f, 2.25f, 2.5f, 1.0f},
            {-1.0f, -1.75f, -0.75f, 1.0f, -1.0f, 0.75f},
            {-0.4f, 2.25f, -0.4f, 0.4f, 3.25f, 0.4f},
    };

    public HorcruxHeartRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HorcruxBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null || !(be.isActive() || be.isPrimed())) {
            return;
        }
        boolean rite = be.isPrimed();
        int rgb = rite ? HorcruxBlockEntity.RITE_CRIMSON : HorcruxBlockEntity.SOUL_GREEN;
        float r = ((rgb >> 16) & 0xFF) / 255.0f, g = ((rgb >> 8) & 0xFF) / 255.0f, b = (rgb & 0xFF) / 255.0f;
        float time = level.getGameTime() + partialTick;
        float beat = be.beatNow(partialTick);
        float feed = be.getRitualPulses() / (float) HorcruxBlockEntity.RITUAL_PULSES;
        float bob = 0.035f * Mth.sin(time * 0.06f);
        double heartY = be.heartPos().y - be.getBlockPos().getY() + bob;
        HorcruxFx.seen(be.getBlockPos(), be.heartPos().add(0.0, bob, 0.0), rgb, beat, rite);

        // ---- the heart
        pose.pushPose();
        pose.translate(0.5, heartY, 0.5);
        if (rite) {
            // It strains against the rite, harder as it feeds.
            float j = 0.006f + 0.014f * feed;
            pose.translate(j * Mth.sin(time * 5.3f), j * Mth.sin(time * 6.1f + 1.0f), j * Mth.sin(time * 4.7f + 2.0f));
        }
        pose.mulPose(Axis.YP.rotationDegrees(time * (rite ? 2.4f : 0.9f)));
        pose.mulPose(Axis.ZP.rotationDegrees(6.0f * Mth.sin(time * 0.045f)));
        float s = (1.0f / 16.0f) * (1.0f + 0.17f * beat);
        pose.scale(s, s, s);
        // Dark flesh that flushes with each beat.
        float flush = 0.42f + 0.5f * beat;
        drawHeart(buffers.getBuffer(FLESH), pose, r * flush + 0.08f, g * flush + 0.04f, b * flush + 0.05f, 1.0f);
        pose.scale(1.16f, 1.16f, 1.16f);
        float shell = 0.18f + 0.55f * beat;
        drawHeart(buffers.getBuffer(SHELL), pose, r * shell, g * shell, b * shell, shell);
        pose.popPose();

        // ---- halo and core, facing the camera
        pose.pushPose();
        pose.translate(0.5, heartY, 0.5);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        Matrix4f m = pose.last().pose();
        VertexConsumer glow = buffers.getBuffer(GLOW);
        float halo = 0.18f + 0.42f * beat + (rite ? 0.1f : 0.0f);
        quad(glow, m, 0.0f, 0.0f, 0.95f + 0.55f * beat, r * halo, g * halo, b * halo, halo);
        float core = 0.2f + 0.6f * beat;
        quad(glow, m, 0.0f, 0.0f, 0.35f + 0.15f * beat, 0.9f * core, core, 0.92f * core, core);
        pose.popPose();

        // ---- on the floor: a soft pool of the heart's light, and through the rite, the turning circle
        Matrix4f f = pose.last().pose();
        float pool = (rite ? 0.22f : 0.12f) + 0.22f * beat;
        flat(glow, f, rite ? 1.8f : 1.2f, 0.0f, r * pool, g * pool, b * pool, pool, 0.03f);
        if (rite) {
            VertexConsumer ring = buffers.getBuffer(RING);
            float a = 0.55f + 0.35f * beat;
            float open = 0.75f + 0.25f * feed;
            flat(ring, f, 2.35f * open, time * 0.02f, r * a, g * a, b * a, a, 0.035f);
            flat(ring, f, 1.55f * open, -time * 0.035f, r * a * 0.8f, g * a * 0.8f, b * a * 0.8f, a * 0.8f, 0.036f);
            // A ring that runs inward with every beat, as if the floor were being drawn into the stone.
            int period = be.beatPeriod();
            float phase = (float) Math.floorMod(level.getGameTime(), (long) period) + partialTick;
            float t = Mth.clamp(phase / (period * 0.8f), 0.0f, 1.0f);
            float ia = (1.0f - t) * 0.8f;
            flat(ring, f, 2.6f * (1.0f - t) + 0.4f, 0.0f, r * ia, g * ia, b * ia, ia, 0.037f);
        }
    }

    private static void drawHeart(VertexConsumer vc, PoseStack pose, float r, float g, float b, float a) {
        PoseStack.Pose last = pose.last();
        int light = LightTexture.FULL_BRIGHT;
        for (int i = 0; i < CUBES.length; i++) {
            float[] c = CUBES[i];
            cube(vc, last, c[0], c[1], c[2], c[3], c[4], c[5], i * 3.0f, r, g, b, a, light);
        }
    }

    /** A box with its faces mapped onto the 16px texture at one pixel per pixel, offset per box. */
    private static void cube(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
                             float off, float r, float g, float b, float a, int light) {
        float w = x1 - x0, h = y1 - y0, d = z1 - z0;
        // north (-z), south (+z), west (-x), east (+x), down, up
        face(vc, p, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0, 0, 0, -1, off, w, h, r, g, b, a, light);
        face(vc, p, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, 0, 0, 1, off + 1, w, h, r, g, b, a, light);
        face(vc, p, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, -1, 0, 0, off + 2, d, h, r, g, b, a, light);
        face(vc, p, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, 1, 0, 0, off + 3, d, h, r, g, b, a, light);
        face(vc, p, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, 0, -1, 0, off + 4, w, d, r, g, b, a, light);
        face(vc, p, x0, y1, z1, x0, y1, z0, x1, y1, z0, x1, y1, z1, 0, 1, 0, off + 5, w, d, r, g, b, a, light);
    }

    private static void face(VertexConsumer vc, PoseStack.Pose p,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float nx, float ny, float nz, float off, float uw, float vh,
                             float r, float g, float b, float a, int light) {
        float u0 = (off % 10.0f) / 16.0f, v0 = ((off * 7.0f) % 10.0f) / 16.0f;
        float u1 = u0 + uw / 16.0f, v1 = v0 + vh / 16.0f;
        vertex(vc, p, ax, ay, az, u0, v0, nx, ny, nz, r, g, b, a, light);
        vertex(vc, p, bx, by, bz, u1, v0, nx, ny, nz, r, g, b, a, light);
        vertex(vc, p, cx, cy, cz, u1, v1, nx, ny, nz, r, g, b, a, light);
        vertex(vc, p, dx, dy, dz, u0, v1, nx, ny, nz, r, g, b, a, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, float r, float g, float b, float a, int light) {
        vc.addVertex(p.pose(), x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(p, nx, ny, nz);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x, float y, float size, float r, float g, float b, float a) {
        float h = size * 0.5f;
        com.hogwartsmagic.client.SpellFx.vertex(vc, m, x - h, y + h, 0.0f, 0, 0, r, g, b, a);
        com.hogwartsmagic.client.SpellFx.vertex(vc, m, x + h, y + h, 0.0f, 1, 0, r, g, b, a);
        com.hogwartsmagic.client.SpellFx.vertex(vc, m, x + h, y - h, 0.0f, 1, 1, r, g, b, a);
        com.hogwartsmagic.client.SpellFx.vertex(vc, m, x - h, y - h, 0.0f, 0, 1, r, g, b, a);
    }

    /** A sprite lying flat on the floor round the block's foot, {@code half} blocks to each side, turned by {@code rot}. */
    private static void flat(VertexConsumer vc, Matrix4f m, float half, float rot, float r, float g, float b, float a, float lift) {
        float c = Mth.cos(rot) * half, s = Mth.sin(rot) * half;
        float cx = 0.5f, cz = 0.5f;
        com.hogwartsmagic.client.SpellFx.vertex(vc, m, cx - c + s, lift, cz - s - c, 0, 0, r, g, b, a);
        com.hogwartsmagic.client.SpellFx.vertex(vc, m, cx + c + s, lift, cz + s - c, 1, 0, r, g, b, a);
        com.hogwartsmagic.client.SpellFx.vertex(vc, m, cx + c - s, lift, cz + s + c, 1, 1, r, g, b, a);
        com.hogwartsmagic.client.SpellFx.vertex(vc, m, cx - c - s, lift, cz - s + c, 0, 1, r, g, b, a);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
