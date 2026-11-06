package com.hogwartsmagic.client.fx.marks;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.fx.FxConfig;
import com.hogwartsmagic.pensieve.scene.ScorchDecal;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The marks magic leaves on the room, drawn on the real block faces (tops, sides, part-blocks such as slabs and
 * stairs) round a spot, never placed as blocks.
 *
 * <ul>
 *   <li><b>Scorch:</b> deep soot with a darker core and a ragged edge, laid in a few ticks; for the first few seconds
 *       ember cells glow orange on it and flicker (full-bright, additive) and then die; the soot then fades slowly.</li>
 *   <li><b>Frost:</b> pale blue-white rime in crystalline ridges, with a few glinting cells (additive), thinning out
 *       from its edge inwards until it is gone.</li>
 * </ul>
 *
 * <p><b>The cost:</b> a mark's faces are listed once (at most {@link #FACES_PER_FRAME} marks' worth a frame, and not
 * before its delay is over, so a Bombarda blast marks the crater the blast leaves, not the stone it removes), baked by
 * {@link ScorchDecal} once for its radius and replayed with only the camera offset added. At most {@link #MAX_MARKS}
 * marks live (the oldest go first); marks out of view or past {@link ScorchDecal#CULL_DISTANCE} are skipped, and a pass
 * draws at most {@link ScorchDecal#MAX_FRAME_CELLS} cells, nearest first. A few marks a tick are rechecked against the
 * world so a mark on a block that has gone goes with it. All of it is dropped on logout and when the level changes.
 *
 * <p>Drawn at {@code AFTER_TRANSLUCENT_BLOCKS} from an identity matrix with camera-relative coordinates, each face a
 * little off its block (more with distance) on top of a polygon offset, to keep clear of the block's own face.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class MarkRenderer {
    /** At most this many marks alive; adding one past it drops the oldest. */
    public static final int MAX_MARKS = 40;
    /** At most this many marks have their faces listed in one frame (the rest wait a frame: no hitch). */
    private static final int FACES_PER_FRAME = 2;
    /** A mark lists at most this many faces. */
    private static final int MAX_FACES = 220;
    /** Marks rechecked against the world each client tick, and how often a young mark (still being blasted) is. */
    private static final int RECHECKS_PER_TICK = 3;
    private static final int YOUNG_TICKS = 100;
    /** A new mark this close to a live one of its kind (as a share of its radius) refreshes that one instead. */
    private static final double MERGE_SHARE = 0.3;
    /** Ticks the soot and frost take to appear. */
    private static final float FADE_IN = 6f;
    /** The embers glow this many ticks, hottest first. */
    private static final float EMBER_TICKS = 110f;
    /** The embers cover this share of the soot's radius. */
    private static final double EMBER_REACH = 0.72;
    /** The frost glints cover this share of its radius. */
    private static final double GLINT_REACH = 0.9;

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, path);
    }

    private static final RenderType SOOT = MarkRenderTypes.decal(tex("textures/entity/dragon_scorch.png"));
    private static final RenderType FROST = MarkRenderTypes.decal(tex("textures/fx/marks_frost.png"));
    private static final RenderType EMBERS = MarkRenderTypes.glow(tex("textures/entity/dragon_embers.png"));
    private static final RenderType GLINTS = MarkRenderTypes.glow(tex("textures/fx/marks_glint.png"));
    private static final int FULL_LIGHT = LightTexture.FULL_BRIGHT;
    private static final int NO_OVERLAY = OverlayTexture.NO_OVERLAY;

    /** One mark. */
    private static final class Mark {
        final boolean frost;
        final double x, y, z;
        double radius;
        /** Game time (ticks) it begins, and how long it lasts. */
        double start;
        double life;
        final int seed;
        List<ScorchDecal.Face> faces;
        int[] light;
        ScorchDecal.Baked base, glow;
        long lastRecheck = Long.MIN_VALUE;

        Mark(boolean frost, Vec3 at, double radius, double start, double life, int seed) {
            this.frost = frost;
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.radius = radius;
            this.start = start;
            this.life = life;
            this.seed = seed;
        }
    }

    private static final List<Mark> MARKS = new ArrayList<>();
    private static ClientLevel forLevel;
    private static int recheckCursor;
    private static int seedCounter;
    /** Scratch: per-face glow or glint strength for the pass in hand. */
    private static float[] scratch = new float[MAX_FACES];

    private MarkRenderer() {
    }

    // ---- Adding ------------------------------------------------------------------------------------------------

    /** Adds a mark (frost or scorch) of {@code radius} blocks at {@code at}, lasting {@code seconds}, after a delay. */
    public static void add(boolean frost, Vec3 at, float radius, float seconds, int delayTicks) {
        if (!FxConfig.get(FxConfig.LINGERING_MARKS)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || radius < 0.2f || seconds <= 0f) return;
        double r = Math.min(radius, 6.0);
        double start = mc.level.getGameTime() + Math.max(0, delayTicks);
        double life = Math.min(seconds, 180f) * 20.0;
        for (Mark m : MARKS) {
            if (m.frost == frost && at.distanceToSqr(m.x, m.y, m.z) < sq(Math.min(r, m.radius) * MERGE_SHARE)) {
                // The same place struck again: it is freshly marked (and darker, never smaller).
                m.start = start;
                m.life = life;
                if (r > m.radius) {
                    m.radius = r;
                    m.base = null;
                    m.glow = null;
                }
                return;
            }
        }
        if (MARKS.size() >= MAX_MARKS) MARKS.remove(0);
        MARKS.add(new Mark(frost, at, r, start, life, seedCounter++ * 7919 + 13));
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Drops every mark. */
    public static void clear() {
        MARKS.clear();
        forLevel = null;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    // ---- The world check ---------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.level instanceof ClientLevel level)) {
            if (!MARKS.isEmpty()) clear();
            return;
        }
        if (forLevel != level) {
            // A new level (a dimension change, a rejoin): nothing of the old one stays.
            MARKS.clear();
            forLevel = level;
            return;
        }
        if (MARKS.isEmpty() || mc.isPaused()) return;
        long game = level.getGameTime();
        MARKS.removeIf(m -> game - m.start > m.life + 5);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        // Marks still young (a blast is breaking the blocks under them) are rechecked often, the rest in turn.
        for (Mark m : MARKS) {
            if (m.faces != null && game - m.start < YOUNG_TICKS && game - m.lastRecheck >= 4) recheck(level, m, p, game);
        }
        for (int k = 0; k < Math.min(RECHECKS_PER_TICK, MARKS.size()); k++) {
            Mark m = MARKS.get(Math.floorMod(recheckCursor++, MARKS.size()));
            if (m.faces != null) recheck(level, m, p, game);
        }
    }

    /** Drops the faces of a mark whose block has gone (air, or no shape left); the mark is rebaked next frame. */
    private static void recheck(ClientLevel level, Mark m, BlockPos.MutableBlockPos p, long game) {
        m.lastRecheck = game;
        List<ScorchDecal.Face> kept = new ArrayList<>(m.faces.size());
        for (ScorchDecal.Face f : m.faces) {
            p.set(BlockPos.getX(f.block()), BlockPos.getY(f.block()), BlockPos.getZ(f.block()));
            BlockState st = level.getBlockState(p);
            if (!st.isAir() && !st.getCollisionShape(level, p).isEmpty()) kept.add(f);
        }
        if (kept.size() == m.faces.size()) return;
        m.faces = kept;
        m.base = null;
        m.glow = null;
        m.light = null;
    }

    // ---- Drawing -----------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (MARKS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.level instanceof ClientLevel level) || level != forLevel) return;
        if (!FxConfig.get(FxConfig.LINGERING_MARKS)) return;
        // The real partial tick, never the render event's (that one is the frame length).
        double now = level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Frustum frustum = event.getFrustum();
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        List<Mark> live = new ArrayList<>();
        int builds = 0;
        for (Mark mk : MARKS) {
            double age = now - mk.start;
            if (age < 0 || age > mk.life) continue;
            double d = Math.sqrt(sq(mk.x - cam.x) + sq(mk.y - cam.y) + sq(mk.z - cam.z));
            if (d > ScorchDecal.CULL_DISTANCE + mk.radius) continue;
            double e = mk.radius + 0.6;
            if (frustum != null && !frustum.isVisible(new AABB(mk.x - e, mk.y - e, mk.z - e, mk.x + e, mk.y + e, mk.z + e))) {
                continue;
            }
            if (mk.faces == null) {
                if (builds >= FACES_PER_FRAME) continue;
                mk.faces = facesNear(level, mk);
                builds++;
            }
            if (mk.faces.isEmpty()) continue;
            if (mk.light == null || ((long) age) % 20 == 0) refreshLight(level, mk);
            live.add(mk);
        }
        if (live.isEmpty()) return;
        live.sort(Comparator.comparingDouble(a -> sq(a.x - cam.x) + sq(a.y - cam.y) + sq(a.z - cam.z)));

        int n = live.size();
        double[] dist = new double[n], rad = new double[n];
        boolean[] vis = new boolean[n];
        int[] cells = new int[n];

        // 1. The soot and the frost (lit by the world).
        for (boolean frost : new boolean[]{false, true}) {
            Arrays.fill(cells, 0);
            for (int i = 0; i < n; i++) {
                Mark mk = live.get(i);
                dist[i] = Math.sqrt(sq(mk.x - cam.x) + sq(mk.y - cam.y) + sq(mk.z - cam.z));
                rad[i] = mk.radius;
                vis[i] = mk.frost == frost;
                if (vis[i]) {
                    if (mk.base == null) mk.base = ScorchDecal.bake(mk.faces, mk.x, mk.y, mk.z, mk.radius);
                    cells[i] = mk.base.cells;
                }
            }
            int[] plan = ScorchDecal.plan(dist, rad, vis, cells, ScorchDecal.MAX_FRAME_CELLS);
            if (plan.length == 0) continue;
            RenderType type = frost ? FROST : SOOT;
            VertexConsumer vc = buffer.getBuffer(type);
            for (int i : plan) layer(vc, m, live.get(i), now, cam, dist[i]);
            buffer.endBatch(type);
        }

        // 2. The embers (while they glow) and the frost's glints (additive, full-bright).
        for (boolean frost : new boolean[]{false, true}) {
            Arrays.fill(cells, 0);
            for (int i = 0; i < n; i++) {
                Mark mk = live.get(i);
                vis[i] = false;
                if (mk.frost != frost) continue;
                double age = now - mk.start;
                if (!frost && emberGlow(age) <= 0.01) continue;
                double reach = mk.radius * (frost ? GLINT_REACH : EMBER_REACH);
                if (mk.glow == null) mk.glow = ScorchDecal.bake(mk.faces, mk.x, mk.y, mk.z, reach);
                vis[i] = true;
                cells[i] = mk.glow.cells;
            }
            int[] plan = ScorchDecal.plan(dist, rad, vis, cells, ScorchDecal.MAX_FRAME_CELLS);
            if (plan.length == 0) continue;
            RenderType type = frost ? GLINTS : EMBERS;
            VertexConsumer vc = buffer.getBuffer(type);
            for (int i : plan) light(vc, m, live.get(i), now, cam, dist[i]);
            buffer.endBatch(type);
        }
    }

    /** The embers' heat 0..1 {@code age} ticks after the strike: hot at once, dying away over {@link #EMBER_TICKS}. */
    static double emberGlow(double age) {
        if (age < 0 || age > EMBER_TICKS) return 0;
        double k = 1 - age / EMBER_TICKS;
        return Math.min(1, age / 2.0 + 0.3) * k * Math.sqrt(k);
    }

    /** How present the mark is 0..1 at {@code age}: in over {@link #FADE_IN} ticks, held, then slowly gone. */
    private static double presence(Mark mk, double age) {
        double in = Math.min(1, age / FADE_IN);
        double hold = mk.frost ? 0.3 : 0.4;
        double out = age <= mk.life * hold ? 0 : Math.min(1, (age - mk.life * hold) / (mk.life * (1 - hold)));
        // Soot fades by its own weight; frost by thinning (see layer), with a light fade as well.
        return in * (mk.frost ? 1 - 0.5 * out * out : 1 - out);
    }

    /** One mark's soot or frost: its baked cells with the camera offset, ragged edge and fade applied. */
    private static void layer(VertexConsumer vc, Matrix4f m, Mark mk, double now, Vec3 cam, double dist) {
        ScorchDecal.Baked b = mk.base;
        double age = now - mk.start;
        float alpha = (float) presence(mk, age);
        if (alpha <= 0.003f) return;
        double hold = mk.frost ? 0.3 : 0.4;
        // Frost thins from its edge in: the fall-off is cut by a rising threshold.
        float thin = mk.frost && age > mk.life * hold ? (float) Math.min(1, (age - mk.life * hold) / (mk.life * (1 - hold))) : 0f;
        double lift = ScorchDecal.lift(dist);
        double ox = mk.x - cam.x, oy = mk.y - cam.y, oz = mk.z - cam.z;
        float peak = mk.frost ? 0.85f : 0.94f;
        for (int c = 0; c < b.cells; c++) {
            ScorchDecal.Face f = mk.faces.get(b.face[c]);
            double lx = f.nx() * lift + ox, ly = f.ny() * lift + oy, lz = f.nz() * lift + oz;
            int lightCoord = mk.light[Math.min(b.face[c], mk.light.length - 1)];
            for (int i = 0; i < 4; i++) {
                float fall = ragged(b.fall[c * 4 + i], b.xyz, c * 12 + i * 3, mk.seed);
                if (thin > 0f) fall = clamp((fall - thin) / (1.02f - thin * 0.9f));
                float a = fall * peak * alpha;
                float shade;
                if (mk.frost) {
                    shade = 1f;
                } else {
                    // Deep soot, darkest in the core.
                    shade = 0.62f - 0.42f * fall;
                }
                vc.addVertex(m, (float) (b.xyz[c * 12 + i * 3] + lx), (float) (b.xyz[c * 12 + i * 3 + 1] + ly),
                                (float) (b.xyz[c * 12 + i * 3 + 2] + lz))
                        .setColor(shade, shade, mk.frost ? 1f : shade, clamp(a))
                        .setUv(b.uv[c * 8 + i * 2], b.uv[c * 8 + i * 2 + 1])
                        .setOverlay(NO_OVERLAY).setLight(lightCoord).setNormal(f.nx(), f.ny(), f.nz());
            }
        }
    }

    /** The additive light of one mark: dying embers on a scorch, glints on frost. */
    private static void light(VertexConsumer vc, Matrix4f m, Mark mk, double now, Vec3 cam, double dist) {
        ScorchDecal.Baked b = mk.glow;
        double age = now - mk.start;
        double glow = mk.frost ? presence(mk, age) : emberGlow(age);
        if (glow <= 0.01) return;
        if (scratch.length < mk.faces.size()) scratch = new float[mk.faces.size()];
        for (int f = 0; f < mk.faces.size(); f++) {
            int h = mk.faces.get(f).hash();
            if (mk.frost) {
                // Only some faces glint, each on its own beat, a short bright wink.
                float wink = (float) Math.sin(now * 0.11 + h * 1.7);
                scratch[f] = ((h & 1) == 0 && wink > 0.55f) ? (float) (glow * (wink - 0.55) * 2.2) : 0f;
            } else {
                // Embers flicker, and each face dies at its own moment (the small ones first).
                double own = clamp((float) (glow * 1.25 - ((h * 0.0013) % 0.25)));
                scratch[f] = (float) (own * (0.6 + 0.4 * Math.sin(now * 0.37 + h * 0.71) * Math.sin(now * 0.23 + h)));
            }
        }
        double lift = ScorchDecal.lift(dist) * 1.5;
        double ox = mk.x - cam.x, oy = mk.y - cam.y, oz = mk.z - cam.z;
        for (int c = 0; c < b.cells; c++) {
            float s = scratch[b.face[c]];
            if (s <= 0.01f) continue;
            ScorchDecal.Face f = mk.faces.get(b.face[c]);
            double lx = f.nx() * lift + ox, ly = f.ny() * lift + oy, lz = f.nz() * lift + oz;
            for (int i = 0; i < 4; i++) {
                float fall = ragged(b.fall[c * 4 + i], b.xyz, c * 12 + i * 3, mk.seed + 5);
                float k = fall * fall * s;
                float r, g, bl;
                if (mk.frost) {
                    r = 0.75f * k;
                    g = 0.92f * k;
                    bl = k;
                } else {
                    r = k;
                    g = 0.58f * k;
                    bl = 0.26f * k;
                }
                vc.addVertex(m, (float) (b.xyz[c * 12 + i * 3] + lx), (float) (b.xyz[c * 12 + i * 3 + 1] + ly),
                                (float) (b.xyz[c * 12 + i * 3 + 2] + lz))
                        .setColor(clamp(r), clamp(g), clamp(bl), 1f)
                        .setUv(b.uv[c * 8 + i * 2], b.uv[c * 8 + i * 2 + 1])
                        .setOverlay(NO_OVERLAY).setLight(FULL_LIGHT).setNormal(f.nx(), f.ny(), f.nz());
            }
        }
    }

    /**
     * A fall-off roughened into a ragged edge: scaled by a hash of the vertex's place (the same at the cells that
     * share it, so the edge has no cracks), the core (fall 1) left whole.
     */
    private static float ragged(float fall, float[] xyz, int at, int seed) {
        if (fall >= 0.999f) return 1f;
        int qx = Math.round(xyz[at] * 6f), qy = Math.round(xyz[at + 1] * 6f), qz = Math.round(xyz[at + 2] * 6f);
        int h = qx * 73856093 ^ qy * 19349663 ^ qz * 83492791 ^ seed * 668265263;
        h ^= h >>> 13;
        h *= 1274126177;
        h ^= h >>> 16;
        float n = (h & 0xFFFF) / 65535f;
        return clamp(fall * (0.5f + 0.85f * n));
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    /** Refreshes the light value of each face (the air in front of it), a mark at a time, once a second. */
    private static void refreshLight(ClientLevel level, Mark mk) {
        int[] out = new int[mk.faces.size()];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < out.length; i++) {
            ScorchDecal.Face f = mk.faces.get(i);
            p.set(BlockPos.getX(f.block()) + f.nx(), BlockPos.getY(f.block()) + f.ny(), BlockPos.getZ(f.block()) + f.nz());
            out[i] = LevelRenderer.getLightColor(level, p);
        }
        mk.light = out;
    }

    // ---- The faces ---------------------------------------------------------------------------------------------

    /**
     * The open faces of the blocks round a mark (on the shape's own box) that look toward its centre and out of the
     * stone into air, within its radius, nearest first, at most {@link #MAX_FACES}.
     */
    private static List<ScorchDecal.Face> facesNear(ClientLevel level, Mark mk) {
        List<ScorchDecal.Face> out = new ArrayList<>();
        List<Double> dist = new ArrayList<>();
        double reach = mk.radius + 0.5;
        int r = (int) Math.ceil(reach) + 1;
        BlockPos c = BlockPos.containing(mk.x, mk.y, mk.z);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState st = level.getBlockState(p);
                    if (st.isAir()) continue;
                    VoxelShape shape = st.getCollisionShape(level, p);
                    if (shape.isEmpty()) continue;
                    AABB bb = shape.bounds();
                    for (Direction d : Direction.values()) {
                        BlockPos nb = p.relative(d);
                        BlockState ns = level.getBlockState(nb);
                        if (!ns.isAir() && ns.isSolidRender(level, nb) && touches(bb, d)) continue;
                        ScorchDecal.Face f = face(p, bb, d);
                        double[] mid = f.at(0.5, 0.5);
                        // The face must look toward the centre: not the far side of a wall.
                        double look = d.getStepX() * (mid[0] - mk.x) + d.getStepY() * (mid[1] - mk.y)
                                + d.getStepZ() * (mid[2] - mk.z);
                        if (look > 0.15) continue;
                        double dd = Math.sqrt(sq(mid[0] - mk.x) + sq(mid[1] - mk.y) + sq(mid[2] - mk.z));
                        if (dd > reach) continue;
                        out.add(f);
                        dist.add(dd);
                    }
                }
            }
        }
        Integer[] idx = new Integer[out.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble(dist::get));
        List<ScorchDecal.Face> sorted = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_FACES, idx.length); i++) sorted.add(out.get(idx[i]));
        return sorted;
    }

    /** Whether the shape's face on side d lies on the block's own boundary (so a solid neighbour hides it). */
    private static boolean touches(AABB bb, Direction d) {
        // An if-chain, not a switch: no enum switch in client code.
        if (d == Direction.UP) return bb.maxY >= 1 - 1e-4;
        if (d == Direction.DOWN) return bb.minY <= 1e-4;
        if (d == Direction.EAST) return bb.maxX >= 1 - 1e-4;
        if (d == Direction.WEST) return bb.minX <= 1e-4;
        if (d == Direction.SOUTH) return bb.maxZ >= 1 - 1e-4;
        return bb.minZ <= 1e-4;
    }

    /** The face of box {@code bb} (block-local) of the block at p on side d: four corners in order round it. */
    private static ScorchDecal.Face face(BlockPos p, AABB bb, Direction d) {
        double x0 = p.getX() + bb.minX, x1 = p.getX() + bb.maxX, y0 = p.getY() + bb.minY, y1 = p.getY() + bb.maxY;
        double z0 = p.getZ() + bb.minZ, z1 = p.getZ() + bb.maxZ;
        double[][] c;
        if (d == Direction.UP) c = new double[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
        else if (d == Direction.DOWN) c = new double[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
        else if (d == Direction.EAST) c = new double[][]{{x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}};
        else if (d == Direction.WEST) c = new double[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
        else if (d == Direction.SOUTH) c = new double[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
        else c = new double[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}};
        int hash = (p.getX() * 73856093) ^ (p.getY() * 19349663) ^ (p.getZ() * 83492791) ^ d.ordinal();
        return new ScorchDecal.Face(c[0], c[1], c[2], c[3], d.getStepX(), d.getStepY(), d.getStepZ(), hash & 0xFFFF,
                p.asLong());
    }
}
