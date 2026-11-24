package com.hogwartsmagic.client.fx.portkey;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.fx.FxConfig;
import com.hogwartsmagic.portkey.PortkeyTimeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The mark a Portkey leaves where it lands: the ground flattened and scuffed dark in a ragged circle with skid streaks
 * thrown outwards, fading over half a minute, and over it for the first two seconds a ring of runes glowing and
 * dying away. Laid on the top faces of the 5x5 blocks level with the landing (a step up or down is left bare), so it
 * hugs the ground instead of floating as a disc. At most eight at once, not drawn past 64 blocks, off with the
 * lingering-marks setting. Drawn by PortkeyWorldFx in its pass.
 */
final class PortkeyDecals {

    private static final ResourceLocation SCUFF = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID,
            "textures/fx/portkey_scuff.png");
    private static final ResourceLocation RING = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID,
            "textures/fx/portkey_ring.png");
    private static final RenderType SCUFF_TYPE = ModRenderTypes.markSmoke(SCUFF);
    private static final RenderType RING_TYPE = ModRenderTypes.markGlow(RING);
    /** Half the decal's width (blocks): it covers 5x5 blocks. */
    private static final int HALF = 2;
    /** The scuff's size against the 5x5 area (the ring is drawn a little smaller). */
    private static final float SCUFF_SIZE = 4.6f, RING_SIZE = 3.4f;
    /** Lift off the face, so it never fights the block for depth. */
    private static final float LIFT = 0.012f;

    /** One mark: its centre, when it was made, and the top faces it lies on (x, z of each block; y of the face). */
    private record Decal(Vec3 centre, double start, int[] blocks, float y) {
    }

    private static final List<Decal> DECALS = new ArrayList<>();

    private PortkeyDecals() {
    }

    /** A mark at {@code landing} (the feet), made at game time {@code now}. */
    static void add(Vec3 landing, double now) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !FxConfig.get(FxConfig.LINGERING_MARKS)) {
            return;
        }
        BlockPos feet = BlockPos.containing(landing.x, landing.y + 0.01, landing.z);
        BlockPos ground = feet.below();
        // The face height: the top of whatever the feet stand on (a full block, a slab, a path).
        float y = (float) landing.y;
        List<Integer> faces = new ArrayList<>();
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                BlockPos below = ground.offset(dx, 0, dz);
                BlockPos above = below.above();
                BlockState under = level.getBlockState(below);
                BlockState over = level.getBlockState(above);
                if (!under.isFaceSturdy(level, below, Direction.UP) && !(dx == 0 && dz == 0)) {
                    continue; // nothing level to lie on
                }
                if (!over.getCollisionShape(level, above).isEmpty() && !(dx == 0 && dz == 0)) {
                    continue; // covered
                }
                faces.add(below.getX());
                faces.add(below.getZ());
            }
        }
        int[] blocks = new int[faces.size()];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = faces.get(i);
        }
        if (DECALS.size() >= PortkeyTimeline.MAX_DECALS) {
            DECALS.remove(0);
        }
        DECALS.add(new Decal(landing, now, blocks, y));
    }

    static void clear() {
        DECALS.clear();
    }

    /** Draw every mark, camera-relative. */
    static void render(MultiBufferSource.BufferSource buffer, Matrix4f m, Vec3 camera, double now) {
        if (DECALS.isEmpty()) {
            return;
        }
        DECALS.removeIf(d -> now - d.start > PortkeyTimeline.DECAL_SCUFF_TICKS || now < d.start - 5.0);
        if (DECALS.isEmpty() || !FxConfig.get(FxConfig.LINGERING_MARKS)) {
            return;
        }
        float cull = PortkeyTimeline.DECAL_CULL;
        VertexConsumer scuff = buffer.getBuffer(SCUFF_TYPE);
        for (Decal d : DECALS) {
            if (d.centre.distanceToSqr(camera) > cull * cull) {
                continue;
            }
            float age = (float) (now - d.start);
            // In fast (the ground is torn at once), out slowly over the last half of its life.
            float a = 0.9f * PortkeyTimeline.smoothstep(0.0f, 3.0f, age)
                    * (1.0f - PortkeyTimeline.smoothstep(PortkeyTimeline.DECAL_SCUFF_TICKS * 0.5f,
                    PortkeyTimeline.DECAL_SCUFF_TICKS, age));
            faces(scuff, m, camera, d, SCUFF_SIZE, 0.0f, 1.0f, 1.0f, 1.0f, a, false);
        }
        buffer.endBatch(SCUFF_TYPE);
        VertexConsumer ring = buffer.getBuffer(RING_TYPE);
        for (Decal d : DECALS) {
            float age = (float) (now - d.start);
            if (age > PortkeyTimeline.DECAL_GLOW_TICKS || d.centre.distanceToSqr(camera) > cull * cull) {
                continue;
            }
            float f = age / PortkeyTimeline.DECAL_GLOW_TICKS;
            float a = (1.0f - f) * (1.0f - f) * PortkeyTimeline.smoothstep(0.0f, 2.0f, age);
            faces(ring, m, camera, d, RING_SIZE * (0.85f + 0.15f * f), age * 0.01f, PortkeyTimeline.GLOW_R,
                    PortkeyTimeline.GLOW_G, PortkeyTimeline.GLOW_B, a, true);
        }
        buffer.endBatch(RING_TYPE);
    }

    /**
     * The decal over each of its block faces: every face a quad whose UVs are cut from the one big decal of
     * {@code size} blocks centred on the landing (turned {@code turn} radians), so the faces read as one mark.
     */
    private static void faces(VertexConsumer vc, Matrix4f m, Vec3 camera, Decal d, float size, float turn, float r,
                              float g, float b, float a, boolean premultiply) {
        if (a <= 0.004f) {
            return;
        }
        float cr = premultiply ? r * a : r;
        float cg = premultiply ? g * a : g;
        float cb = premultiply ? b * a : b;
        float y = (float) (d.y - camera.y) + LIFT;
        float cos = (float) Math.cos(turn);
        float sin = (float) Math.sin(turn);
        int light = LightTexture.FULL_BRIGHT;
        for (int i = 0; i < d.blocks.length; i += 2) {
            int bx = d.blocks[i];
            int bz = d.blocks[i + 1];
            if (!premultiply) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    light = LevelRenderer.getLightColor(mc.level, new BlockPos(bx, (int) Math.floor(d.y), bz));
                }
            }
            float x0 = bx, x1 = bx + 1, z0 = bz, z1 = bz + 1;
            corner(vc, m, camera, d, size, cos, sin, x0, y, z0, cr, cg, cb, a, light);
            corner(vc, m, camera, d, size, cos, sin, x0, y, z1, cr, cg, cb, a, light);
            corner(vc, m, camera, d, size, cos, sin, x1, y, z1, cr, cg, cb, a, light);
            corner(vc, m, camera, d, size, cos, sin, x1, y, z0, cr, cg, cb, a, light);
        }
    }

    private static void corner(VertexConsumer vc, Matrix4f m, Vec3 camera, Decal d, float size, float cos, float sin,
                               float wx, float y, float wz, float r, float g, float b, float a, int light) {
        float lx = (float) (wx - d.centre.x);
        float lz = (float) (wz - d.centre.z);
        float u = (lx * cos - lz * sin) / size + 0.5f;
        float v = (lx * sin + lz * cos) / size + 0.5f;
        vc.addVertex(m, (float) (wx - camera.x), y, (float) (wz - camera.z)).setColor(r, g, b, a).setUv(u, v)
                .setLight(light).setNormal(0.0f, 1.0f, 0.0f);
    }
}
