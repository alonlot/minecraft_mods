package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.MemoryDoorEntity;
import com.hogwartsmagic.pensieve.scene.DoorHinge;
import com.hogwartsmagic.pensieve.scene.DoorRing;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Draws one door leaf as a textured box that swings about its hinge edge and tips about its bottom edge (see {@link DoorHinge}), with an iron
 * ring hanging from a stud on each face that swings as a damped pendulum ({@link DoorRing}). The mirrored
 * variant ({@code memory_door_mirrored}) is the same box with local x negated and its faces re-wound outward.
 *
 * <p>The actor {@code scale} is already on the PoseStack when Pensieve calls this (MemoryActors scales the
 * stack before dispatching), so the geometry is taken at scale 1; passing the scale here would apply it twice.
 *
 * <p>Pensieve actors are never ticked, so the ring is stepped from here once per game tick (the level game
 * time, which holds still while paused) from the leaf yaw, keyed by entity id. {@link #clearState} drops the
 * state when the memory ends, and entries unseen for a while are pruned.
 */
public class MemoryDoorRenderer extends EntityRenderer<MemoryDoorEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/memory_door.png");
    private static final float TEX_W = 128f, TEX_H = 384f;
    // Pixel rects {u0, v0, u1, v1} per face, in DoorHinge.FACES order. Keep in step with the texture script.
    private static final float[][] RECTS = {
            {0, 0, 64, 192}, {64, 0, 128, 192}, {0, 192, 12, 384}, {12, 192, 24, 384}, {32, 192, 96, 204}, {32, 204, 96, 216},
    };
    /** The iron swatch the ring and stud are textured from. */
    private static final float[] IRON = {100, 192, 116, 208};

    // The stud the ring hangs from, near the meeting stile (keep in step with the texture script's RING_X / RING_Y).
    private static final double STUD_X = 0.84, STUD_Y = 1.35;
    private static final double STUD_HALF = 0.032, STUD_DEPTH = 0.05;
    private static final double RING_RADIUS = 0.085, RING_TUBE = 0.016, RING_OFFSET = 0.032;
    private static final int RING_SEGMENTS = 12, TUBE_SIDES = 8;

    private static final Map<Integer, Anim> ANIMS = new HashMap<>();
    private static final long PRUNE_AFTER_TICKS = 400;

    /** Per-leaf secondary motion, stepped once per game tick. */
    private static final class Anim {
        long tick;
        long seen;
        float yaw;
        float yawVel;
        float prevAngle;
        int shudder;
        final DoorRing.State ring = new DoorRing.State();
    }

    private final boolean mirrored;

    public MemoryDoorRenderer(EntityRendererProvider.Context context) {
        this(context, false);
    }

    public MemoryDoorRenderer(EntityRendererProvider.Context context, boolean mirrored) {
        super(context);
        this.mirrored = mirrored;
    }

    /** Forget every leaf's pendulum state: called when the Pensieve actors are cleared. */
    public static void clearState() {
        ANIMS.clear();
    }

    @Override
    public ResourceLocation getTextureLocation(MemoryDoorEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(MemoryDoorEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        // `yaw` is the body yaw interpolated by the caller (MemoryActors) between ticks, so the swing is smooth.
        Anim anim = step(entity);
        float renderYaw = yaw;
        // Look pitch tips the leaf about its bottom edge: 0 upright, -90 fallen flat (see DoorHinge).
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        float ringDeg = 0f;
        if (anim != null) {
            renderYaw += DoorRing.shudder(anim.shudder);
            ringDeg = Mth.lerp(partialTick, anim.prevAngle, anim.ring.angle);
        }
        // Positive yaw acceleration drives the ring angle negative; that lags toward local +Z on the plain leaf
        // and toward local -Z on the mirrored one.
        float swingZ = mirrored ? ringDeg : -ringDeg;

        // The light the caller passes is sampled at the actor's eye height, which is inside the jamb or the wall
        // for a door, so it is ignored: take the brightest light around the leaf instead.
        float scale = pose.last().pose().getScale(new Vector3f()).x;
        int lit = sampleLight(entity, renderYaw, pitch, scale == 0f ? 1f : scale, light);

        double[][] c = new double[8][];
        for (int i = 0; i < 8; i++) c[i] = DoorHinge.corner(renderYaw, pitch, 1f, i, mirrored);

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        PoseStack.Pose p = pose.last();
        Matrix4f m = p.pose();
        // Each corner keeps its own texture coordinate (original FACES order), however the face is wound.
        float[][] uvIndex = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int f = 0; f < DoorHinge.FACES.length; f++) {
            int[] q = DoorHinge.winding(f, mirrored);
            double[] n = DoorHinge.faceNormal(renderYaw, pitch, 1f, f, mirrored);
            float[] r = RECTS[f];
            for (int i = 0; i < 4; i++) {
                int orig = mirrored ? (4 - i) % 4 : i;
                float u = (uvIndex[orig][0] == 0 ? r[0] : r[2]) / TEX_W;
                float v = (uvIndex[orig][1] == 0 ? r[1] : r[3]) / TEX_H;
                vertex(vc, m, p, c[q[i]], u, v, n, lit);
            }
        }

        // The studs and rings are thin and seen from both sides of their tubes, so they are drawn without culling.
        VertexConsumer iron = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (int side = 0; side < 2; side++) {
            boolean front = side == 0;
            // Out from this face: +Z on the front, -Z on the back. The door blocks a ring from swinging into it.
            float out = Mth.clamp(front ? swingZ : -swingZ, 0f, DoorRing.MAX_DEG);
            stud(iron, m, p, renderYaw, pitch, front, lit);
            ring(iron, m, p, renderYaw, pitch, front, out, lit);
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /** Advance this leaf's pendulum to the current game tick. Null until the actor has been placed. */
    private static Anim step(MemoryDoorEntity entity) {
        if (entity.tickCount < 1) return null;      // not placed yet: its yaw is still the default
        long now = entity.level().getGameTime();
        float cur = entity.getYRot();
        Anim a = ANIMS.get(entity.getId());
        if (a == null) {
            if (ANIMS.size() > 32) prune(now);
            a = new Anim();
            a.tick = now;
            a.yaw = cur;
            ANIMS.put(entity.getId(), a);
        } else if (a.tick != now) {
            long dt = Math.max(1, now - a.tick);
            float vel = Mth.wrapDegrees(cur - a.yaw) / dt;
            long n = Math.min(dt, 3);
            for (long i = 0; i < n; i++) {
                if (i == n - 1) a.prevAngle = a.ring.angle;
                DoorRing.step(a.ring, i == 0 ? vel - a.yawVel : 0f);
            }
            if (a.shudder > 0) a.shudder--;
            if (DoorRing.shudders(a.yawVel, vel)) a.shudder = DoorRing.SHUDDER_TICKS;
            a.yaw = cur;
            a.yawVel = vel;
            a.tick = now;
        }
        a.seen = now;
        return a;
    }

    private static void prune(long now) {
        for (Iterator<Anim> it = ANIMS.values().iterator(); it.hasNext(); ) {
            if (now - it.next().seen > PRUNE_AFTER_TICKS) it.remove();
        }
    }

    /**
     * The brightest light around the leaf: the hinge block, its four horizontal neighbours, and the blocks half
     * a leaf-width and a leaf-width along the leaf, each at 1/3 of the leaf height, 2/3 and near the top. Block
     * and sky light are maximised separately.
     */
    private int sampleLight(MemoryDoorEntity entity, float yaw, float pitch, float scale, int fallback) {
        Level level = entity.level();
        if (level == null) return fallback;
        double[] along = DoorHinge.corner(yaw, pitch, scale, 2, mirrored);
        double[][] offsets = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {along[0] * 0.5, along[2] * 0.5}, {along[0], along[2]}};
        double[] heights = {DoorHinge.HEIGHT * scale / 3.0, DoorHinge.HEIGHT * scale * 2.0 / 3.0, DoorHinge.HEIGHT * scale * 0.95};
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int block = 0, sky = 0;
        for (double[] o : offsets) {
            for (double h : heights) {
                int l = LevelRenderer.getLightColor(level, pos.set(Mth.floor(entity.getX() + o[0]),
                        Mth.floor(entity.getY() + h), Mth.floor(entity.getZ() + o[1])));
                block = Math.max(block, LightTexture.block(l));
                sky = Math.max(sky, LightTexture.sky(l));
            }
        }
        return LightTexture.pack(block, sky);
    }

    /** A small iron boss standing off the face where the ring hangs. */
    private void stud(VertexConsumer vc, Matrix4f m, PoseStack.Pose p, float yaw, float pitch, boolean front, int light) {
        double z0 = front ? DoorHinge.THICKNESS : 0.0;
        double z1 = front ? DoorHinge.THICKNESS + STUD_DEPTH : -STUD_DEPTH;
        double x0 = STUD_X - STUD_HALF, x1 = STUD_X + STUD_HALF;
        double y0 = STUD_Y - STUD_HALF, y1 = STUD_Y + STUD_HALF;
        // Five faces (the one against the leaf is hidden): +z or -z cap, then the four sides.
        quad(vc, m, p, yaw, pitch, light, 0, 0, front ? 1 : -1, new double[][] {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}});
        quad(vc, m, p, yaw, pitch, light, 0, 1, 0, new double[][] {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}});
        quad(vc, m, p, yaw, pitch, light, 0, -1, 0, new double[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}});
        quad(vc, m, p, yaw, pitch, light, 1, 0, 0, new double[][] {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}});
        quad(vc, m, p, yaw, pitch, light, -1, 0, 0, new double[][] {{x0, y0, z0}, {x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}});
    }

    /** One flat quad from leaf-local corners, with a leaf-local normal, textured from the iron swatch. */
    private void quad(VertexConsumer vc, Matrix4f m, PoseStack.Pose p, float yaw, float pitch, int light,
                      double nx, double ny, double nz, double[][] local) {
        double[] n = DoorHinge.direction(yaw, pitch, mirrored, nx, ny, nz);
        float[] uv = {IRON[0] / TEX_W, IRON[1] / TEX_H, IRON[2] / TEX_W, IRON[3] / TEX_H};
        float[][] corner = {{uv[0], uv[3]}, {uv[2], uv[3]}, {uv[2], uv[1]}, {uv[0], uv[1]}};
        for (int i = 0; i < 4; i++) {
            double[] w = DoorHinge.point(yaw, pitch, mirrored, local[i][0], local[i][1], local[i][2]);
            vertex(vc, m, p, w, corner[i][0], corner[i][1], n, light);
        }
    }

    /**
     * The ring: a closed tube of {@link #RING_SEGMENTS} segments hanging from the stud, swung {@code outDeg}
     * away from the face (about the horizontal axis through the stud).
     */
    private void ring(VertexConsumer vc, Matrix4f m, PoseStack.Pose p, float yaw, float pitch, boolean front, float outDeg, int light) {
        double th = Math.toRadians(outDeg), ct = Math.cos(th), st = Math.sin(th);
        double faceZ = front ? DoorHinge.THICKNESS : 0.0;
        double dir = front ? 1.0 : -1.0;
        double[][][] pos = new double[RING_SEGMENTS][TUBE_SIDES][];
        double[][][] nor = new double[RING_SEGMENTS][TUBE_SIDES][];
        for (int j = 0; j < RING_SEGMENTS; j++) {
            double a = Math.PI * 2.0 * j / RING_SEGMENTS;
            double sa = Math.sin(a), ca = Math.cos(a);
            for (int k = 0; k < TUBE_SIDES; k++) {
                double phi = Math.PI * 2.0 * k / TUBE_SIDES;
                double cp = Math.cos(phi), sp = Math.sin(phi);
                double rad = RING_RADIUS + RING_TUBE * cp;
                // Ring-local offsets from the stud: across (du), up (dv), away from the face (dw).
                double du = rad * sa;
                double dv = -RING_RADIUS + rad * ca;
                double dw = RING_OFFSET + RING_TUBE * sp;
                double nu = cp * sa, nv = cp * ca, nw = sp;
                // Swing about the stud axis: the bottom of the ring moves away from the face.
                double v2 = ct * dv + st * dw, w2 = ct * dw - st * dv;
                double nv2 = ct * nv + st * nw, nw2 = ct * nw - st * nv;
                pos[j][k] = DoorHinge.point(yaw, pitch, mirrored, STUD_X + du, STUD_Y + v2, faceZ + dir * w2);
                nor[j][k] = DoorHinge.direction(yaw, pitch, mirrored, nu, nv2, dir * nw2);
            }
        }
        for (int j = 0; j < RING_SEGMENTS; j++) {
            int j2 = (j + 1) % RING_SEGMENTS;
            for (int k = 0; k < TUBE_SIDES; k++) {
                int k2 = (k + 1) % TUBE_SIDES;
                ringVertex(vc, m, p, pos[j][k], nor[j][k], 0f, 0f, light);
                ringVertex(vc, m, p, pos[j2][k], nor[j2][k], 1f, 0f, light);
                ringVertex(vc, m, p, pos[j2][k2], nor[j2][k2], 1f, 1f, light);
                ringVertex(vc, m, p, pos[j][k2], nor[j][k2], 0f, 1f, light);
            }
        }
    }

    private static void ringVertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose p, double[] pos, double[] n,
                                   float fu, float fv, int light) {
        float u = (IRON[0] + 1f + fu * (IRON[2] - IRON[0] - 2f)) / TEX_W;
        float v = (IRON[1] + 1f + fv * (IRON[3] - IRON[1] - 2f)) / TEX_H;
        vertex(vc, m, p, pos, u, v, n, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose p, double[] pos,
                               float u, float v, double[] n, int light) {
        vc.addVertex(m, (float) pos[0], (float) pos[1], (float) pos[2]).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, (float) n[0], (float) n[1], (float) n[2]);
    }
}
