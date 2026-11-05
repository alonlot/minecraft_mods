package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.CaveInimicumWardBlockEntity;
import com.hogwartsmagic.caveinimicum.CaveInimicumWardBounds;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import static com.hogwartsmagic.client.SpellFxGeometry.*;

/**
 * The Ward's own magic, drawn by hand from the synced block entity instead of with particles.
 *
 * <p><b>The outline</b> (Cave Inimicum teal, shown for a few seconds when the ward is primed, sized or clicked):
 * light traces the cube's twelve edges, the floor first, then up the corners, then closed across the top, a spark
 * riding each growing tip. Once drawn, beads of light run along the edges, a sigil turns at every corner and a faint
 * lattice over the faces brightens as a sweep of light climbs through it.
 *
 * <p><b>The Protego Maxima barrier</b> (the cast dome's blues and hex lattice, so the two read as one spell) is the
 * cube itself, because that is where it stops things: a wall of hexes with a fresnel rim, a band of light climbing it,
 * glowing edges, rune bands turning round its foot and brow and sparks drifting up its skin. Raised, it unfurls up
 * and down from the anchor behind a bright seam; released, it draws back into the anchor; knocked down by an
 * Alohomora, it flares and breaks up hex by hex. Hidden while Cave Inimicum conceals the ward, as before.
 *
 * <p>Camera-relative in double precision, identity matrix (this stage is after the camera push), true partial tick.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class WardFxRenderer {
    private static final RenderType HEX = ModRenderTypes.ceilingGlow(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/shield_hex.png"));
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;

    private static final double MAX_DISTANCE = 160.0;
    /** One hex tile is this many blocks across. */
    private static final float HEX_TILE = 1.25f;
    /** The barrier sits a hair outside the ward's cube, off the block faces on its boundary. */
    private static final double SKIN = 0.03;

    private static final float OUTLINE_IN = 16.0f;
    private static final float OUTLINE_OUT = 12.0f;
    private static final float BARRIER_IN = 16.0f;
    private static final float BARRIER_OUT = 12.0f;
    private static final float BURST_TICKS = 14.0f;

    // Cave Inimicum teal, and the near-white it burns to.
    private static final float TEAL_R = 0.46f;
    private static final float TEAL_G = 0.94f;
    private static final float TEAL_B = 0.88f;
    private static final float TEAL_HOT_R = 0.86f;
    private static final float TEAL_HOT_G = 1.00f;
    private static final float TEAL_HOT_B = 0.97f;
    // Protego Maxima's blues (as ProtegoMaximaRenderer).
    private static final float[] CORE = {0.36f, 0.64f, 1.00f};
    private static final float[] RIM = {0.84f, 0.95f, 1.00f};
    private static final float[] HEX_BLUE = {0.40f, 0.70f, 1.00f};

    /** What each ward showed on the last frame, so a change of state can animate. Client thread only. */
    private static final Map<CaveInimicumWardBlockEntity, Visual> VISUALS = new WeakHashMap<>();

    private static final class Visual {
        boolean barrier;
        float barrierAt = -1.0E4f;
        boolean burst;
        boolean outline;
        float outlineAt = -1.0E4f;
    }

    /** One ward's frame: its box (camera-relative), the anchor height and how far each effect has got. */
    private static final class Frame {
        double x0, y0, z0, x1, y1, z1;
        double anchorY;
        int seed;
        float now;
        // Barrier.
        float barrierStrength;
        float reach;
        float seam;
        float dissolve;
        float flare;
        // Outline.
        float outlineStrength;
        float trace;
    }

    private WardFxRenderer() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        VISUALS.clear();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || CaveInimicumWardBlockEntity.clientLoaded().isEmpty()) {
            return;
        }
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        long tick = level.getGameTime();
        float now = tick + partial;
        Vec3 camera = event.getCamera().getPosition();

        List<Frame> barriers = new ArrayList<>();
        List<Frame> outlines = new ArrayList<>();
        for (CaveInimicumWardBlockEntity ward : new ArrayList<>(CaveInimicumWardBlockEntity.clientLoaded())) {
            if (ward.isRemoved() || ward.getLevel() != level) {
                continue;
            }
            Visual v = VISUALS.computeIfAbsent(ward, w -> new Visual());
            boolean wantBarrier = ward.isBarrierVisible(tick);
            if (wantBarrier != v.barrier) {
                v.barrier = wantBarrier;
                v.barrierAt = now;
                v.burst = !wantBarrier && ward.isProtegoMaximaActive() && ward.getBarrierSuppressedUntil() > tick;
            }
            boolean wantOutline = ward.getOutlineUntil() > tick;
            if (wantOutline != v.outline) {
                v.outline = wantOutline;
                v.outlineAt = now;
            }

            CaveInimicumWardBounds bounds = ward.bounds();
            BlockPos min = bounds.min();
            BlockPos max = bounds.max();
            Frame f = new Frame();
            f.x0 = min.getX() - camera.x;
            f.y0 = min.getY() - camera.y;
            f.z0 = min.getZ() - camera.z;
            f.x1 = max.getX() + 1.0 - camera.x;
            f.y1 = max.getY() + 1.0 - camera.y;
            f.z1 = max.getZ() + 1.0 - camera.z;
            f.anchorY = ward.getBlockPos().getY() + 0.5 - camera.y;
            f.seed = (int) ward.getBlockPos().asLong();
            f.now = now;
            double cx = (f.x0 + f.x1) * 0.5;
            double cy = (f.y0 + f.y1) * 0.5;
            double cz = (f.z0 + f.z1) * 0.5;
            if (cx * cx + cy * cy + cz * cz > MAX_DISTANCE * MAX_DISTANCE) {
                continue;
            }
            if (prepareBarrier(f, v, now)) {
                barriers.add(f);
            }
            if (prepareOutline(f, v, now)) {
                outlines.add(f);
            }
        }
        if (barriers.isEmpty() && outlines.isEmpty()) {
            return;
        }

        Matrix4f matrix = new Matrix4f();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        if (!barriers.isEmpty()) {
            VertexConsumer hex = buffer.getBuffer(HEX);
            for (Frame f : barriers) {
                drawBarrierWalls(hex, matrix, f, true);
            }
            buffer.endBatch(HEX);
        }
        VertexConsumer light = buffer.getBuffer(ModRenderTypes.PROTEGO_LIGHT);
        for (Frame f : barriers) {
            drawBarrierWalls(light, matrix, f, false);
            drawBarrierEdges(light, matrix, f);
            drawSeam(light, matrix, f);
            // At the barrier's foot, just above the ground the ward stands on, and round its brow.
            drawRuneBand(light, matrix, f, f.anchorY - 0.2, 1.0f);
            drawRuneBand(light, matrix, f, f.y1 - 0.7, -1.0f);
            drawMotes(light, matrix, f);
        }
        for (Frame f : outlines) {
            drawOutline(light, matrix, f);
        }
        buffer.endBatch(ModRenderTypes.PROTEGO_LIGHT);
    }

    // ---- state ---------------------------------------------------------------------------------------------------

    private static boolean prepareBarrier(Frame f, Visual v, float now) {
        float age = now - v.barrierAt;
        float fullReach = (float) Math.max(f.anchorY - f.y0, f.y1 - f.anchorY) + 0.6f;
        if (v.barrier) {
            float t = smoothstep(0.0f, BARRIER_IN, age);
            f.reach = fullReach * easeOutCubic(t);
            f.barrierStrength = 1.0f;
            f.seam = 1.0f - smoothstep(BARRIER_IN * 0.85f, BARRIER_IN + 6.0f, age);
            f.flare = 0.6f * (1.0f - smoothstep(0.0f, BARRIER_IN, age));
        } else if (v.burst) {
            if (age >= BURST_TICKS) {
                return false;
            }
            float t = age / BURST_TICKS;
            f.reach = fullReach;
            f.barrierStrength = (1.0f - t) * (1.0f - t);
            f.flare = 1.4f * (1.0f - smoothstep(0.0f, 0.35f, t));
            f.dissolve = t;
        } else {
            if (age >= BARRIER_OUT) {
                return false;
            }
            float t = age / BARRIER_OUT;
            f.reach = fullReach * (1.0f - easeInCubic(t));
            f.barrierStrength = 1.0f - 0.4f * t;
            f.seam = 1.0f - t;
        }
        return f.barrierStrength > 0.004f;
    }

    private static boolean prepareOutline(Frame f, Visual v, float now) {
        float age = now - v.outlineAt;
        if (v.outline) {
            f.trace = Mth.clamp(age / OUTLINE_IN, 0.0f, 1.0f);
            f.outlineStrength = 1.0f;
        } else {
            if (age >= OUTLINE_OUT) {
                return false;
            }
            f.trace = 1.0f;
            float t = age / OUTLINE_OUT;
            f.outlineStrength = (1.0f - t) * (0.75f + 0.25f * Mth.sin(age * 2.1f));
        }
        return f.outlineStrength > 0.004f;
    }

    // ---- the barrier ---------------------------------------------------------------------------------------------

    /** The six walls, once as the textured hex lattice ({@code hexPass}) and once as the soft body of light. */
    private static void drawBarrierWalls(VertexConsumer out, Matrix4f m, Frame f, boolean hexPass) {
        double x0 = f.x0 - SKIN;
        double y0 = f.y0 - SKIN;
        double z0 = f.z0 - SKIN;
        double x1 = f.x1 + SKIN;
        double y1 = f.y1 + SKIN;
        double z1 = f.z1 + SKIN;
        // Each face: an origin corner, two edge vectors, its outward normal.
        drawFace(out, m, f, hexPass, 0, new Vec3(x0, y0, z0), new Vec3(x1 - x0, 0, 0), new Vec3(0, y1 - y0, 0), 0, 0, -1);
        drawFace(out, m, f, hexPass, 1, new Vec3(x1, y0, z1), new Vec3(x0 - x1, 0, 0), new Vec3(0, y1 - y0, 0), 0, 0, 1);
        drawFace(out, m, f, hexPass, 2, new Vec3(x0, y0, z1), new Vec3(0, 0, z0 - z1), new Vec3(0, y1 - y0, 0), -1, 0, 0);
        drawFace(out, m, f, hexPass, 3, new Vec3(x1, y0, z0), new Vec3(0, 0, z1 - z0), new Vec3(0, y1 - y0, 0), 1, 0, 0);
        drawFace(out, m, f, hexPass, 4, new Vec3(x0, y1, z0), new Vec3(x1 - x0, 0, 0), new Vec3(0, 0, z1 - z0), 0, 1, 0);
        drawFace(out, m, f, hexPass, 5, new Vec3(x0, y0, z1), new Vec3(x1 - x0, 0, 0), new Vec3(0, 0, z0 - z1), 0, -1, 0);
    }

    private static void drawFace(VertexConsumer out, Matrix4f m, Frame f, boolean hexPass, int face, Vec3 origin,
                                 Vec3 edgeU, Vec3 edgeV, float nx, float ny, float nz) {
        double lenU = edgeU.length();
        double lenV = edgeV.length();
        int segU = Mth.clamp((int) Math.ceil(lenU), 6, 40);
        int segV = Mth.clamp((int) Math.ceil(lenV), 6, 40);
        int stride = segU + 1;
        int count = stride * (segV + 1);
        float[] xs = new float[count];
        float[] ys = new float[count];
        float[] zs = new float[count];
        float[] power = new float[count];
        float[] white = new float[count];
        boolean any = false;
        double height = Math.max(1.0, f.y1 - f.y0);
        // The climbing band of light: rises the full height every ~6 s.
        float climb = frac(f.now / 120.0f + (f.seed & 7) * 0.13f) * 1.3f - 0.15f;
        for (int j = 0; j <= segV; j++) {
            double tv = j / (double) segV;
            for (int i = 0; i <= segU; i++) {
                double tu = i / (double) segU;
                double x = origin.x + edgeU.x * tu + edgeV.x * tv;
                double y = origin.y + edgeU.y * tu + edgeV.y * tv;
                double z = origin.z + edgeU.z * tu + edgeV.z * tv;
                int index = j * stride + i;
                xs[index] = (float) x;
                ys[index] = (float) y;
                zs[index] = (float) z;

                // Unfurled this far from the anchor: in front of the seam, nothing.
                double fromAnchor = Math.abs(y - f.anchorY);
                float cover = 1.0f - smoothstep(f.reach - 0.6f, f.reach, (float) fromAnchor);
                if (f.dissolve > 0.0f) {
                    int cu = (int) Math.floor(tu * lenU / HEX_TILE);
                    int cv = (int) Math.floor(tv * lenV / HEX_TILE);
                    cover *= hash(cu, cv, face * 7919 + f.seed) > f.dissolve * 1.15f ? 1.0f : 0.0f;
                }
                if (cover <= 0.001f) {
                    continue;
                }
                double length = Math.sqrt(x * x + y * y + z * z);
                float facing = length < 1.0E-4 ? 1.0f : (float) Math.abs((x * nx + y * ny + z * nz) / length);
                float rim = (1.0f - facing) * (1.0f - facing) * (1.0f - facing);
                // Distance in blocks to this face's nearest border: the walls burn brighter at their edges.
                double border = Math.min(Math.min(tu, 1.0 - tu) * lenU, Math.min(tv, 1.0 - tv) * lenV);
                float edge = gauss((float) (border / 0.7));
                float yFrac = (float) ((y - f.y0) / height);
                float band = 0.5f * gauss((yFrac - climb) / 0.05f);
                float seam = f.seam * gauss((float) ((fromAnchor - f.reach + 0.3) / 0.45));
                float shimmer = 1.0f + 0.14f * Mth.sin((float) (x * 0.9 + z * 0.7) + f.now * 0.05f)
                        * Mth.sin((float) (y * 1.1 - x * 0.4) - f.now * 0.035f);
                float p = hexPass
                        ? (0.10f + 0.65f * rim + 1.1f * band + 1.6f * seam + 0.7f * edge) * shimmer
                        : (0.025f + 0.28f * rim + 0.30f * band + 0.9f * seam + 0.25f * edge);
                power[index] = Mth.clamp(p * cover * f.barrierStrength * (1.0f + f.flare), 0.0f, hexPass ? 2.0f : 1.0f);
                white[index] = Mth.clamp(seam + band * 0.5f + f.flare * 0.5f + (hexPass ? 0.0f : rim * 0.6f), 0.0f, 1.0f);
                any = true;
            }
        }
        if (!any) {
            return;
        }
        for (int j = 0; j < segV; j++) {
            for (int i = 0; i < segU; i++) {
                int a = j * stride + i;
                int b = (j + 1) * stride + i;
                if (power[a] <= 0.0f && power[a + 1] <= 0.0f && power[b] <= 0.0f && power[b + 1] <= 0.0f) {
                    continue;
                }
                if (hexPass) {
                    float u0 = (float) (lenU * i / segU / HEX_TILE);
                    float u1 = (float) (lenU * (i + 1) / segU / HEX_TILE);
                    float v0 = (float) (lenV * j / segV / HEX_TILE);
                    float v1 = (float) (lenV * (j + 1) / segV / HEX_TILE);
                    hexVertex(out, m, xs, ys, zs, power, white, a, u0, v0);
                    hexVertex(out, m, xs, ys, zs, power, white, a + 1, u1, v0);
                    hexVertex(out, m, xs, ys, zs, power, white, b + 1, u1, v1);
                    hexVertex(out, m, xs, ys, zs, power, white, b, u0, v1);
                } else {
                    glowVertex(out, m, xs, ys, zs, power, white, a);
                    glowVertex(out, m, xs, ys, zs, power, white, a + 1);
                    glowVertex(out, m, xs, ys, zs, power, white, b + 1);
                    glowVertex(out, m, xs, ys, zs, power, white, b);
                }
            }
        }
    }

    private static void hexVertex(VertexConsumer out, Matrix4f m, float[] xs, float[] ys, float[] zs, float[] power,
                                  float[] white, int index, float u, float v) {
        // Additive and textured: the colour is premultiplied by the power.
        float p = power[index];
        float w = white[index];
        out.addVertex(m, xs[index], ys[index], zs[index])
                .setColor(Math.min(1.0f, Mth.lerp(w, HEX_BLUE[0], 1.0f) * p),
                        Math.min(1.0f, Mth.lerp(w, HEX_BLUE[1], 1.0f) * p),
                        Math.min(1.0f, Mth.lerp(w, HEX_BLUE[2], 1.0f) * p), 1.0f)
                .setUv(u, v).setOverlay(OVERLAY).setLight(LIGHT).setNormal(0.0f, 1.0f, 0.0f);
    }

    private static void glowVertex(VertexConsumer out, Matrix4f m, float[] xs, float[] ys, float[] zs, float[] power,
                                   float[] white, int index) {
        float w = white[index];
        out.addVertex(m, xs[index], ys[index], zs[index])
                .setColor(Mth.lerp(w, CORE[0], RIM[0]), Mth.lerp(w, CORE[1], RIM[1]), Mth.lerp(w, CORE[2], RIM[2]),
                        power[index]);
    }

    /** The twelve edges of the barrier, glowing, clipped to how far it has unfurled. */
    private static void drawBarrierEdges(VertexConsumer out, Matrix4f m, Frame f) {
        float alpha = f.barrierStrength * (1.0f + f.flare) * (1.0f - f.dissolve);
        if (alpha <= 0.004f) {
            return;
        }
        double[][] corners = corners(f, SKIN);
        for (int[] e : EDGES) {
            Vec3 a = vec(corners[e[0]]);
            Vec3 b = vec(corners[e[1]]);
            Vec3[] path = clippedPath(a, b, f);
            if (path == null) {
                continue;
            }
            float[] width = filled(path.length, 0.22f);
            float[] alphas = filled(path.length, 0.35f * alpha);
            ribbon(out, m, path, width, alphas, 1.0f, 1.0f, CORE[0], CORE[1], CORE[2]);
            ribbon(out, m, path, width, alphas, 0.18f, 2.2f, RIM[0], RIM[1], RIM[2]);
        }
    }

    /** The bright line where the barrier is still unfurling (or drawing back), round all four walls. */
    private static void drawSeam(VertexConsumer out, Matrix4f m, Frame f) {
        if (f.seam <= 0.01f) {
            return;
        }
        for (int sign = -1; sign <= 1; sign += 2) {
            double y = f.anchorY + sign * (f.reach - 0.3);
            if (y < f.y0 - SKIN || y > f.y1 + SKIN) {
                continue;
            }
            Vec3[] loop = wallLoop(f, y, SKIN + 0.02);
            float[] width = filled(loop.length, 0.35f);
            float[] alphas = filled(loop.length, 0.6f * f.seam * f.barrierStrength);
            ribbon(out, m, loop, width, alphas, 1.0f, 1.0f, CORE[0], CORE[1], CORE[2]);
            ribbon(out, m, loop, width, alphas, 0.2f, 1.6f, 1.0f, 1.0f, 1.0f);
        }
    }

    /**
     * A band of rune dashes running round the four walls at height {@code y}, turning one way or the other. Each dash keeps its shape as it travels, so they read as glyphs, not noise.
     */
    private static void drawRuneBand(VertexConsumer out, Matrix4f m, Frame f, double y, float direction) {
        if (y < f.y0 || y > f.y1 || Math.abs(y - f.anchorY) > f.reach) {
            return;
        }
        float alpha = f.barrierStrength * (1.0f - f.dissolve) * (1.0f + f.flare);
        if (alpha <= 0.004f) {
            return;
        }
        double lx = f.x1 - f.x0;
        double lz = f.z1 - f.z0;
        double perimeter = 2.0 * (lx + lz);
        int cells = Math.max(8, (int) Math.round(perimeter / 0.7));
        double cell = perimeter / cells;
        double scroll = f.now * 0.025 * direction;
        int band = direction > 0.0f ? 1 : 2;
        long first = (long) Math.ceil(-scroll / cell);
        double half = 0.16;
        double skin = SKIN + 0.04;
        for (int k = 0; k < cells; k++) {
            long id = first + k;
            int glyph = Math.floorMod((int) id, cells);
            float h = hash(glyph, band, f.seed);
            if (h < 0.28f) {
                continue; // a gap between words
            }
            double s0 = scroll + id * cell; // in [0, perimeter): first is the first id at or past the start
            double dashLength = cell * (0.35 + 0.5 * hash(glyph, 3, f.seed));
            double s1 = s0 + dashLength;
            double[] p0 = wallPoint(f, s0, skin);
            double[] p1 = wallPoint(f, s1, skin);
            if (p0[3] != p1[3]) {
                continue; // would bend round a corner
            }
            // Some glyphs are tall strokes, some short: a hint of script.
            double tall = half * (0.55 + 0.9 * hash(glyph, 5, f.seed));
            float a = Mth.clamp((0.35f + 0.45f * hash(glyph, 9, f.seed)) * alpha, 0.0f, 1.0f);
            Vec3 a0 = new Vec3(p0[0], y, p0[2]);
            Vec3 a1 = new Vec3(p1[0], y, p1[2]);
            Vec3 up = new Vec3(0.0, tall, 0.0);
            quad(out, m, a0.subtract(up), 0.0f, a1.subtract(up), 0.0f, a1, a, a0, a, 0.5f);
            quad(out, m, a0, a, a1, a, a1.add(up), 0.0f, a0.add(up), 0.0f, 0.5f);
        }
    }

    /** Sparks drifting up the walls, twinkling. */
    private static void drawMotes(VertexConsumer out, Matrix4f m, Frame f) {
        float alpha = f.barrierStrength * (1.0f - f.dissolve);
        if (alpha <= 0.004f) {
            return;
        }
        double lx = f.x1 - f.x0;
        double lz = f.z1 - f.z0;
        double perimeter = 2.0 * (lx + lz);
        double height = f.y1 - f.y0;
        int motes = Mth.clamp((int) (perimeter * 0.45), 12, 48);
        for (int k = 0; k < motes; k++) {
            double s = hash(k, 1, f.seed) * perimeter + Mth.sin(f.now * 0.02f + k) * 0.4;
            double y = f.y0 + frac(hash(k, 2, f.seed) + f.now * (0.002f + 0.002f * hash(k, 3, f.seed))) * height;
            if (Math.abs(y - f.anchorY) > f.reach) {
                continue;
            }
            double[] p = wallPoint(f, Math.floorMod((long) (s * 1000.0), (long) (perimeter * 1000.0)) / 1000.0, SKIN + 0.06);
            float twinkle = 0.25f + 0.75f * (float) Math.pow(0.5f + 0.5f * Mth.sin(f.now * 0.3f + k * 1.9f), 2.0);
            star(out, m, new Vec3(p[0], y, p[2]), 0.12f * (0.8f + 0.4f * twinkle), twinkle * alpha);
        }
    }

    // ---- the outline ---------------------------------------------------------------------------------------------

    private static void drawOutline(VertexConsumer out, Matrix4f m, Frame f) {
        double[][] c = corners(f, 0.0);
        float strength = f.outlineStrength;
        float bottom = smoothstep(0.0f, 0.42f, f.trace);
        float rise = smoothstep(0.25f, 0.7f, f.trace);
        float top = smoothstep(0.58f, 1.0f, f.trace);
        // Floor and roof close from both corners towards the middle; the uprights climb from the floor.
        for (int[] e : EDGES) {
            Vec3 a = vec(c[e[0]]);
            Vec3 b = vec(c[e[1]]);
            if (e[2] == 1) {
                traceLine(out, m, f, a, a.lerp(b, rise), strength, rise < 1.0f);
            } else {
                float p = e[2] == 0 ? bottom : top;
                Vec3 mid = a.lerp(b, 0.5);
                traceLine(out, m, f, a, a.lerp(mid, p), strength, p < 1.0f);
                traceLine(out, m, f, b, b.lerp(mid, p), strength, p < 1.0f);
            }
        }
        float drawn = smoothstep(0.75f, 1.0f, f.trace) * strength;
        if (drawn <= 0.004f) {
            return;
        }
        drawLattice(out, m, f, drawn);
        // Beads of light running along each edge.
        for (int n = 0; n < EDGES.length; n++) {
            Vec3 a = vec(c[EDGES[n][0]]);
            Vec3 b = vec(c[EDGES[n][1]]);
            double length = a.distanceTo(b);
            for (int k = 0; k < 2; k++) {
                float t = frac(hash(n, k, f.seed) + f.now * (float) (0.12 / Math.max(1.0, length)));
                Vec3 pos = a.lerp(b, t);
                billboard(out, m, pos, 0.35f, 0.35f * drawn, TEAL_R, TEAL_G, TEAL_B, false, 3, 10);
                billboard(out, m, pos, 0.1f, 0.9f * drawn, TEAL_HOT_R, TEAL_HOT_G, TEAL_HOT_B, true, 2, 8);
            }
        }
        // A sigil turning at every corner.
        for (int k = 0; k < c.length; k++) {
            Vec3 pos = vec(c[k]);
            if (pos.lengthSqr() < 1.0E-6) {
                continue;
            }
            billboard(out, m, pos, 0.75f, 0.3f * drawn, TEAL_R, TEAL_G, TEAL_B, false, 3, 12);
            billboard(out, m, pos, 0.14f, 0.85f * drawn, TEAL_HOT_R, TEAL_HOT_G, TEAL_HOT_B, true, 2, 8);
            Vec3[] axes = frame(pos.scale(-1.0).normalize());
            double spin = f.now * 0.06 * (k % 2 == 0 ? 1.0 : -1.0);
            for (int arcIndex = 0; arcIndex < 3; arcIndex++) {
                double from = spin + arcIndex * Math.PI * 2.0 / 3.0;
                arc(out, m, pos, axes[0], axes[1], 0.48f, 0.035f, 0.75f * drawn, TEAL_HOT_R, TEAL_HOT_G, TEAL_HOT_B,
                        8, from, from + 1.25);
            }
            ring(out, m, pos, axes[0], axes[1], 0.3f, 0.025f, 0.45f * drawn, TEAL_R, TEAL_G, TEAL_B, 20);
        }
    }

    /** One edge of the outline from {@code a} to {@code tip}: a wide teal glow, a hot thread, a spark at the tip. */
    private static void traceLine(VertexConsumer out, Matrix4f m, Frame f, Vec3 a, Vec3 tip, float strength,
                                  boolean growing) {
        double length = a.distanceTo(tip);
        if (length < 0.02) {
            return;
        }
        Vec3[] path = subdivide(a, tip, length);
        float[] width = filled(path.length, 0.16f);
        float[] alphas = new float[path.length];
        for (int i = 0; i < path.length; i++) {
            // A slow pulse running along the line.
            float wave = 0.75f + 0.25f * Mth.sin((float) (i * 0.9) - f.now * 0.25f);
            alphas[i] = 0.45f * strength * wave;
        }
        ribbon(out, m, path, width, alphas, 1.0f, 1.0f, TEAL_R, TEAL_G, TEAL_B);
        ribbon(out, m, path, width, alphas, 0.2f, 2.0f, TEAL_HOT_R, TEAL_HOT_G, TEAL_HOT_B);
        if (growing) {
            billboard(out, m, tip, 0.5f, 0.5f * strength, TEAL_R, TEAL_G, TEAL_B, false, 3, 10);
            star(out, m, tip, 0.3f, strength);
        }
    }

    /** A faint lattice over the six faces (as the old dust grid was), a sweep of light climbing through it. */
    private static void drawLattice(VertexConsumer out, Matrix4f m, Frame f, float strength) {
        int divisions = 8;
        double height = f.y1 - f.y0;
        float sweep = frac(f.now / 50.0f) * 1.4f - 0.2f;
        for (int k = 1; k < divisions; k++) {
            double t = k / (double) divisions;
            double x = f.x0 + (f.x1 - f.x0) * t;
            double z = f.z0 + (f.z1 - f.z0) * t;
            double y = f.y0 + height * t;
            float rowGlow = gauss((float) (t - sweep) / 0.06f);
            // Uprights on the four walls.
            latticeLine(out, m, new Vec3(x, f.y0, f.z0), new Vec3(x, f.y1, f.z0), strength, sweep);
            latticeLine(out, m, new Vec3(x, f.y0, f.z1), new Vec3(x, f.y1, f.z1), strength, sweep);
            latticeLine(out, m, new Vec3(f.x0, f.y0, z), new Vec3(f.x0, f.y1, z), strength, sweep);
            latticeLine(out, m, new Vec3(f.x1, f.y0, z), new Vec3(f.x1, f.y1, z), strength, sweep);
            // Courses round the walls.
            Vec3[] loop = wallLoop(f, y, 0.0);
            float a = (0.10f + 0.5f * rowGlow) * strength;
            ribbon(out, m, loop, filled(loop.length, 0.06f), filled(loop.length, a), 1.0f, 1.0f,
                    TEAL_R, TEAL_G, TEAL_B);
            // Roof and floor grids.
            for (double yy : new double[]{f.y0, f.y1}) {
                float capGlow = (0.10f + 0.4f * gauss((float) ((yy - f.y0) / height - sweep) / 0.08f)) * strength;
                Vec3[] across = subdivide(new Vec3(x, yy, f.z0), new Vec3(x, yy, f.z1), f.z1 - f.z0);
                Vec3[] along = subdivide(new Vec3(f.x0, yy, z), new Vec3(f.x1, yy, z), f.x1 - f.x0);
                ribbon(out, m, across, filled(across.length, 0.06f), filled(across.length, capGlow), 1.0f, 1.0f,
                        TEAL_R, TEAL_G, TEAL_B);
                ribbon(out, m, along, filled(along.length, 0.06f), filled(along.length, capGlow), 1.0f, 1.0f,
                        TEAL_R, TEAL_G, TEAL_B);
            }
        }
    }

    private static void latticeLine(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, float strength, float sweep) {
        Vec3[] path = subdivide(a, b, a.distanceTo(b));
        float[] alphas = new float[path.length];
        for (int i = 0; i < path.length; i++) {
            float t = i / (float) (path.length - 1);
            alphas[i] = (0.10f + 0.5f * gauss((t - sweep) / 0.06f)) * strength;
        }
        ribbon(out, m, path, filled(path.length, 0.06f), alphas, 1.0f, 1.0f, TEAL_R, TEAL_G, TEAL_B);
    }

    // ---- shared ----------------------------------------------------------------------------------------------------

    /** Corner indices of each edge, and its kind: 0 floor, 1 upright, 2 roof. */
    private static final int[][] EDGES = {
            {0, 1, 0}, {1, 2, 0}, {2, 3, 0}, {3, 0, 0},
            {0, 4, 1}, {1, 5, 1}, {2, 6, 1}, {3, 7, 1},
            {4, 5, 2}, {5, 6, 2}, {6, 7, 2}, {7, 4, 2},
    };

    private static double[][] corners(Frame f, double grow) {
        double x0 = f.x0 - grow;
        double y0 = f.y0 - grow;
        double z0 = f.z0 - grow;
        double x1 = f.x1 + grow;
        double y1 = f.y1 + grow;
        double z1 = f.z1 + grow;
        return new double[][]{
                {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1},
                {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1},
        };
    }

    /** The part of the edge from {@code a} to {@code b} the barrier has unfurled over, subdivided, or null. */
    private static Vec3[] clippedPath(Vec3 a, Vec3 b, Frame f) {
        double low = f.anchorY - f.reach;
        double high = f.anchorY + f.reach;
        if (Math.abs(a.y - b.y) < 1.0E-6) {
            return a.y < low || a.y > high ? null : subdivide(a, b, a.distanceTo(b));
        }
        double ya = Mth.clamp(a.y, low, high);
        double yb = Mth.clamp(b.y, low, high);
        if (Math.abs(ya - yb) < 0.05) {
            return null;
        }
        Vec3 from = new Vec3(a.x, ya, a.z);
        Vec3 to = new Vec3(b.x, yb, b.z);
        return subdivide(from, to, from.distanceTo(to));
    }

    /** About a point a block, so strips stay camera-facing along their whole length. */
    private static Vec3[] subdivide(Vec3 a, Vec3 b, double length) {
        int segments = Mth.clamp((int) Math.ceil(length), 1, 48);
        Vec3[] path = new Vec3[segments + 1];
        for (int i = 0; i <= segments; i++) {
            path[i] = a.lerp(b, i / (double) segments);
        }
        return path;
    }

    /** A closed loop round the four walls at height {@code y}, {@code skin} outside them. */
    private static Vec3[] wallLoop(Frame f, double y, double skin) {
        double x0 = f.x0 - skin;
        double z0 = f.z0 - skin;
        double x1 = f.x1 + skin;
        double z1 = f.z1 + skin;
        Vec3[] a = subdivide(new Vec3(x0, y, z0), new Vec3(x1, y, z0), x1 - x0);
        Vec3[] b = subdivide(new Vec3(x1, y, z0), new Vec3(x1, y, z1), z1 - z0);
        Vec3[] c = subdivide(new Vec3(x1, y, z1), new Vec3(x0, y, z1), x1 - x0);
        Vec3[] d = subdivide(new Vec3(x0, y, z1), new Vec3(x0, y, z0), z1 - z0);
        Vec3[] loop = new Vec3[a.length + b.length + c.length + d.length - 3];
        int n = 0;
        for (Vec3 p : a) {
            loop[n++] = p;
        }
        for (int i = 1; i < b.length; i++) {
            loop[n++] = b[i];
        }
        for (int i = 1; i < c.length; i++) {
            loop[n++] = c[i];
        }
        for (int i = 1; i < d.length; i++) {
            loop[n++] = d[i];
        }
        return loop;
    }

    /** The point {@code s} blocks round the walls (from the north-west corner, clockwise from above): x, -, z, wall. */
    private static double[] wallPoint(Frame f, double s, double skin) {
        double x0 = f.x0 - skin;
        double z0 = f.z0 - skin;
        double x1 = f.x1 + skin;
        double z1 = f.z1 + skin;
        double lx = x1 - x0;
        double lz = z1 - z0;
        double perimeter = 2.0 * (lx + lz);
        s = ((s % perimeter) + perimeter) % perimeter;
        if (s < lx) {
            return new double[]{x0 + s, 0.0, z0, 0};
        }
        s -= lx;
        if (s < lz) {
            return new double[]{x1, 0.0, z0 + s, 1};
        }
        s -= lz;
        if (s < lx) {
            return new double[]{x1 - s, 0.0, z1, 2};
        }
        s -= lx;
        return new double[]{x0, 0.0, z1 - Math.min(s, lz), 3};
    }

    /** A little four-rayed star facing the camera. */
    private static void star(VertexConsumer out, Matrix4f m, Vec3 pos, float size, float alpha) {
        if (alpha <= 0.004f || pos.lengthSqr() < 1.0E-6) {
            return;
        }
        Vec3[] axes = frame(pos.scale(-1.0).normalize());
        Vec3[] rays = {axes[0], axes[1], axes[0].scale(-1.0), axes[1].scale(-1.0)};
        for (int r = 0; r < 4; r++) {
            Vec3 ray = rays[r];
            Vec3 side = rays[(r + 1) % 4];
            quad(out, m, pos, alpha, pos.add(side.scale(size * 0.22)), 0.0f, pos.add(ray.scale(size)), 0.0f,
                    pos.add(side.scale(-size * 0.22)), 0.0f, 1.0f);
        }
    }

    private static void quad(VertexConsumer out, Matrix4f m, Vec3 p0, float a0, Vec3 p1, float a1, Vec3 p2, float a2,
                             Vec3 p3, float a3, float white) {
        float r = Mth.lerp(white, CORE[0], 1.0f);
        float g = Mth.lerp(white, CORE[1], 1.0f);
        float b = Mth.lerp(white, CORE[2], 1.0f);
        vertex(out, m, p0, r, g, b, a0);
        vertex(out, m, p1, r, g, b, a1);
        vertex(out, m, p2, r, g, b, a2);
        vertex(out, m, p3, r, g, b, a3);
    }

    private static Vec3 vec(double[] p) {
        return new Vec3(p[0], p[1], p[2]);
    }

    private static float[] filled(int n, float value) {
        float[] out = new float[n];
        java.util.Arrays.fill(out, value);
        return out;
    }

    private static float gauss(float x) {
        return (float) Math.exp(-x * x);
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static float easeOutCubic(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }

    private static float easeInCubic(float t) {
        return t * t * t;
    }
}
