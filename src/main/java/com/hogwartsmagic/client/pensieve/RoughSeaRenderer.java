package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.MemoryRowboatRenderer;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.entity.MemoryRowboatEntity;
import com.hogwartsmagic.pensieve.scene.RoughSea;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The {@code rough_sea} effect: an animated storm-sea surface over the event's area, the heights straight from
 * {@link RoughSea} (so the rowing boat rides exactly this water). Per frame, from the scene clock:
 * <ol>
 *   <li>a depth-only lid over every memory rowboat's open hull, so the waves never show inside a boat;</li>
 *   <li>the water: a fine grid (0.5 blocks) within 28 blocks of the camera and a coarse one (2 blocks) out to 96, the
 *       fine grid's rim stitched to the coarse edges so there are no cracks. Each vertex is shaded on the CPU: a dark
 *       grey-green body, lighter and greener where light comes through a crest, a Fresnel reflection of the storm sky
 *       (bright during a lightning flash), a moon glint and a broad sheen off the wave backs, finer ripples in the
 *       shading normal near the camera ({@link RoughSea#detailNormal}), the ripple texture drifting with the wind, and
 *       the foam amount whitening the crests. Drawn with depth write, near-opaque, fading out over the last 4 blocks of
 *       the area and into the world fog;</li>
 *   <li>the foam: the lacy foam texture over the crests and where the water meets rock (a shore map sampled from the
 *       set), alpha from {@link RoughSea#foamFrom}.</li>
 * </ol>
 * Spray: each tick a handful of seeded points near the camera that sit on a foaming crest throw splash and mist
 * ({@link Water#tick}). Drawn at {@code AFTER_TRANSLUCENT_BLOCKS} from an identity matrix (the camera rotation is already
 * on RenderSystem's modelview stack), so it hides the set's own water beneath it: keep that water at least one block
 * below the sea's {@code at} so a trough never shows it. Client-only; the state lives in {@link EffectVisuals} and its
 * {@code clear()} removes the sea with the scene.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class RoughSeaRenderer {
    private static final RenderType WATER = ModRenderTypes.seaSurface(tex("sea_ripple"));
    private static final RenderType FOAM = ModRenderTypes.ceilingLayer(tex("sea_foam"));
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;

    /** Fine and coarse grid spacing and reach (blocks from the camera). */
    private static final double FINE = 0.5, COARSE = 2.0, FINE_REACH = 28, COARSE_REACH = 96;
    /** Shading: the body, the crest's green, the storm sky, a flash, the moon glint, the foam. */
    private static final float[] DEEP = {0.020f, 0.042f, 0.046f}, CREST = {0.055f, 0.120f, 0.105f},
            SKY = {0.115f, 0.140f, 0.155f}, FLASH = {0.62f, 0.68f, 0.78f}, GLINT = {0.42f, 0.47f, 0.52f},
            FOAM_TINT = {0.60f, 0.64f, 0.65f};
    /** The edge fade (blocks) and how far out the shading ripples reach. */
    private static final double EDGE_FADE = 4.0, DETAIL_REACH = 36.0;
    /** At most this many new block columns of the shore map are read from the set per frame. */
    private static final int SHORE_COLUMNS_PER_FRAME = 400;

    private RoughSeaRenderer() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/" + name + ".png");
    }

    /** One running sea: its maths, its world centre, and a lazily filled map of where it meets solid blocks. */
    static final class Water {
        final RoughSea.Sea sea;
        final double ox, oy, oz;
        /** The scene origin, for the sea-local clock and the world position of the centre. */
        final int w, d;
        /** Shore strength per block column of the area, NaN until sampled; reset twice while the set settles. */
        final float[] shore;
        private int age;
        /** Shore columns still allowed to be sampled this frame (sampling is spread over frames). */
        int shoreBudget;

        Water(RoughSea.Sea sea, Vec3 origin) {
            this.sea = sea;
            this.ox = origin.x + sea.cx();
            this.oy = origin.y + sea.cy();
            this.oz = origin.z + sea.cz();
            this.w = (int) Math.ceil(sea.hx() * 2) + 1;
            this.d = (int) Math.ceil(sea.hz() * 2) + 1;
            this.shore = new float[w * d];
            Arrays.fill(shore, Float.NaN);
        }

        /** One client tick: the shore map is re-read while the set may still be arriving, and the crests throw spray. */
        void tick(ClientLevel level, float now) {
            age++;
            if (age == 60 || age == 160) Arrays.fill(shore, Float.NaN);
            double in = sea.intensityAt(now);
            if (in <= 0) return;
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Random r = new Random(sea.seed() * 31 + (long) Math.floor(now));
            int tries = (int) Math.ceil(14 * in);
            double clock = sea.clock(now);
            double[] g = new double[2];
            for (int i = 0; i < tries; i++) {
                double lx = cam.x - ox + (r.nextDouble() * 2 - 1) * 22, lz = cam.z - oz + (r.nextDouble() * 2 - 1) * 22;
                if (Math.abs(lx) > sea.hx() - 1 || Math.abs(lz) > sea.hz() - 1) continue;
                double h = RoughSea.eval(lx, lz, clock, sea.seed(), g);
                double f = RoughSea.foamFrom(h, g[0], g[1], lx, lz, clock, sea.seed(), in);
                if (f < 0.6) continue;
                double y = oy + h * in;
                double[] wind = RoughSea.wind(sea.seed());
                for (int k = 0; k < 4; k++) {
                    level.addParticle(ParticleTypes.SPLASH, ox + lx + r.nextGaussian() * 0.3, y + 0.05,
                            oz + lz + r.nextGaussian() * 0.3, wind[0] * 0.12, 0.12 + r.nextDouble() * 0.1, wind[1] * 0.12);
                }
                if (r.nextFloat() < 0.35f * in) {
                    level.addParticle(ParticleTypes.POOF, ox + lx, y + 0.15, oz + lz, wind[0] * 0.08, 0.02, wind[1] * 0.08);
                }
            }
        }

        /** How much the water at sea-local (lx, lz) is broken against rock, 0 to 1 (sampled from the set once). */
        float shoreAt(ClientLevel level, double lx, double lz) {
            int ix = (int) Math.floor(lx + sea.hx()), iz = (int) Math.floor(lz + sea.hz());
            if (ix < 0 || iz < 0 || ix >= w || iz >= d) return 0f;
            int idx = iz * w + ix;
            float v = shore[idx];
            if (!Float.isNaN(v)) return v;
            if (shoreBudget <= 0) return 0f;
            shoreBudget--;
            int bx = (int) Math.floor(ox + lx), bz = (int) Math.floor(oz + lz), by = (int) Math.floor(oy);
            float best = 0f;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dx = -2; dx <= 2 && best < 1f; dx++) {
                for (int dz = -2; dz <= 2 && best < 1f; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockState s = level.getBlockState(p.set(bx + dx, by + dy, bz + dz));
                        if (!s.isAir() && s.getFluidState().isEmpty()) {
                            float k = Math.max(Math.abs(dx), Math.abs(dz)) <= 1 ? 1f : 0.45f;
                            best = Math.max(best, k);
                            break;
                        }
                    }
                }
            }
            shore[idx] = best;
            return best;
        }
    }

    // Draw order within AFTER_TRANSLUCENT_BLOCKS: ceiling (HIGH), candles and the sea (NORMAL), light rays (LOW),
    // rain (LOWEST, so it is depth-tested against the waves).
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        List<Water> seas = EffectVisuals.seas();
        if (seas.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.level instanceof ClientLevel level) || !PensieveClientState.inMemory()) return;
        float partial = event.getPartialTick();
        float now = PensieveClientState.sceneTime(partial);
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        // Identity, deliberately: the camera rotation is already on RenderSystem's modelview stack.
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        List<ActorRuntime> boats = MemoryActors.rowboats();
        if (!boats.isEmpty()) {
            VertexConsumer lid = buffer.getBuffer(ModRenderTypes.DEPTH_MASK);
            for (ActorRuntime b : boats) {
                if (b.entity instanceof MemoryRowboatEntity boat) {
                    MemoryRowboatRenderer.lid(lid, m, boat, b.def.scale(), partial, cam);
                }
            }
            buffer.endBatch(ModRenderTypes.DEPTH_MASK);
        }

        float flash = Math.min(1f, level.getSkyFlashTime() / 2f);
        for (Water w : seas) {
            if (w.sea.intensityAt(now) <= 0) continue;
            w.shoreBudget = SHORE_COLUMNS_PER_FRAME;
            Grid g = new Grid(w, level, now, cam, flash);
            VertexConsumer vc = buffer.getBuffer(WATER);
            g.water(vc, m);
            buffer.endBatch(WATER);
            vc = buffer.getBuffer(FOAM);
            g.foam(vc, m);
            buffer.endBatch(FOAM);
        }
    }

    /** The two grids of one sea for one frame: heights, gradients and colours per vertex, then the quads. */
    private static final class Grid {
        final Water w;
        final ClientLevel level;
        final double in, clock, lcx, lcz;
        final Vec3 cam;
        final float flash;
        final double[] light;
        final double[] wind;
        // Fine grid: indices fx0..fx1 (times FINE) and fz0..fz1, aligned to the coarse grid at its rim.
        int fx0, fx1, fz0, fz1;
        int cx0, cx1, cz0, cz1;
        Level fine, coarse;

        Grid(Water w, ClientLevel level, float now, Vec3 cam, float flash) {
            this.w = w;
            this.level = level;
            this.cam = cam;
            this.flash = flash;
            this.in = w.sea.intensityAt(now);
            this.clock = w.sea.clock(now);
            this.lcx = cam.x - w.ox;
            this.lcz = cam.z - w.oz;
            this.wind = RoughSea.wind(w.sea.seed());
            double[] l = {-wind[0] * 0.75, 0.42, -wind[1] * 0.75};
            double len = Math.sqrt(l[0] * l[0] + l[1] * l[1] + l[2] * l[2]);
            this.light = new double[]{l[0] / len, l[1] / len, l[2] / len};
            double hx = w.sea.hx(), hz = w.sea.hz();
            int step = (int) Math.round(COARSE / FINE);
            // The coarse grid covers the whole reach; the fine grid is a block of whole coarse cells round the camera.
            cx0 = clampIdx(Math.floor((lcx - COARSE_REACH) / COARSE), -hx, COARSE, true);
            cx1 = clampIdx(Math.ceil((lcx + COARSE_REACH) / COARSE), hx, COARSE, false);
            cz0 = clampIdx(Math.floor((lcz - COARSE_REACH) / COARSE), -hz, COARSE, true);
            cz1 = clampIdx(Math.ceil((lcz + COARSE_REACH) / COARSE), hz, COARSE, false);
            int fcx0 = Math.max(cx0, (int) Math.floor((lcx - FINE_REACH) / COARSE));
            int fcx1 = Math.min(cx1, (int) Math.ceil((lcx + FINE_REACH) / COARSE));
            int fcz0 = Math.max(cz0, (int) Math.floor((lcz - FINE_REACH) / COARSE));
            int fcz1 = Math.min(cz1, (int) Math.ceil((lcz + FINE_REACH) / COARSE));
            fx0 = fcx0 * step;
            fx1 = fcx1 * step;
            fz0 = fcz0 * step;
            fz1 = fcz1 * step;
            coarse = new Level(cx0, cx1, cz0, cz1, COARSE, false);
            fine = fx1 > fx0 && fz1 > fz0 ? new Level(fx0, fx1, fz0, fz1, FINE, true) : null;
            if (fine != null) stitch(step);
        }

        /** The grid index range end for a coordinate, kept inside the area (half-size {@code edge}). */
        private static int clampIdx(double idx, double edge, double spacing, boolean low) {
            double lim = low ? Math.ceil(edge / spacing) : Math.floor(edge / spacing);
            return (int) (low ? Math.max(idx, lim) : Math.min(idx, lim));
        }

        /** The fine grid's rim takes the coarse edge's straight-line heights, so the two grids meet with no crack. */
        private void stitch(int step) {
            Level f = fine;
            for (int i = 0; i <= f.nx; i++) {
                stitchVertex(f, i, 0, step, true);
                stitchVertex(f, i, f.nz, step, true);
            }
            for (int j = 0; j <= f.nz; j++) {
                stitchVertex(f, 0, j, step, false);
                stitchVertex(f, f.nx, j, step, false);
            }
        }

        private void stitchVertex(Level f, int i, int j, int step, boolean alongX) {
            int gi = f.i0 + i, gj = f.j0 + j;
            int along = alongX ? gi : gj;
            int a = Math.floorDiv(along, step);
            double frac = (along - a * step) / (double) step;
            if (frac == 0) return;
            int ci = alongX ? a - coarse.i0 : Math.floorDiv(gi, step) - coarse.i0;
            int cj = alongX ? Math.floorDiv(gj, step) - coarse.j0 : a - coarse.j0;
            int ci2 = alongX ? ci + 1 : ci, cj2 = alongX ? cj : cj + 1;
            if (ci < 0 || cj < 0 || ci2 > coarse.nx || cj2 > coarse.nz) return;
            double h = coarse.y[coarse.idx(ci, cj)] * (1 - frac) + coarse.y[coarse.idx(ci2, cj2)] * frac;
            f.y[f.idx(i, j)] = h;
        }

        void water(VertexConsumer vc, Matrix4f m) {
            if (fine != null) emit(vc, m, fine, null, false);
            emit(vc, m, coarse, fine, false);
        }

        void foam(VertexConsumer vc, Matrix4f m) {
            if (fine != null) emit(vc, m, fine, null, true);
            emit(vc, m, coarse, fine, true);
        }

        /** The quads of one grid level; the coarse level skips the cells the fine level covers. */
        private void emit(VertexConsumer vc, Matrix4f m, Level lv, Level hole, boolean foam) {
            int step = (int) Math.round(COARSE / FINE);
            for (int j = 0; j < lv.nz; j++) {
                for (int i = 0; i < lv.nx; i++) {
                    if (hole != null) {
                        int gi = (lv.i0 + i) * step, gj = (lv.j0 + j) * step;
                        if (gi >= hole.i0 && gi + step <= hole.i0 + hole.nx && gj >= hole.j0 && gj + step <= hole.j0 + hole.nz) continue;
                    }
                    int a = lv.idx(i, j), b = lv.idx(i + 1, j), c = lv.idx(i + 1, j + 1), d = lv.idx(i, j + 1);
                    if (foam) {
                        if (lv.foam[a] + lv.foam[b] + lv.foam[c] + lv.foam[d] < 0.04f) continue;
                        if (lv.alpha[a] + lv.alpha[b] + lv.alpha[c] + lv.alpha[d] <= 0f) continue;
                        putFoam(vc, m, lv, a);
                        putFoam(vc, m, lv, b);
                        putFoam(vc, m, lv, c);
                        putFoam(vc, m, lv, d);
                    } else {
                        if (lv.alpha[a] + lv.alpha[b] + lv.alpha[c] + lv.alpha[d] <= 0f) continue;
                        putWater(vc, m, lv, a);
                        putWater(vc, m, lv, b);
                        putWater(vc, m, lv, c);
                        putWater(vc, m, lv, d);
                    }
                }
            }
        }

        private void putWater(VertexConsumer vc, Matrix4f m, Level lv, int k) {
            float x = (float) (lv.x[k] + w.ox - cam.x), y = (float) (lv.y[k] + w.oy - cam.y), z = (float) (lv.z[k] + w.oz - cam.z);
            // The ripple texture drifts with the wind, about 6 blocks per tile.
            float u = (float) ((lv.x[k] - wind[0] * clock * 0.03) / 6.0), v = (float) ((lv.z[k] - wind[1] * clock * 0.03) / 6.0);
            vc.addVertex(m, x, y, z).setColor(lv.r[k], lv.g[k], lv.b[k], lv.alpha[k]).setUv(u, v).setOverlay(OVERLAY)
                    .setLight(LIGHT).setNormal(0f, 1f, 0f);
        }

        private void putFoam(VertexConsumer vc, Matrix4f m, Level lv, int k) {
            float x = (float) (lv.x[k] + w.ox - cam.x), y = (float) (lv.y[k] + w.oy - cam.y + 0.02), z = (float) (lv.z[k] + w.oz - cam.z);
            float u = (float) ((lv.x[k] + wind[0] * clock * 0.02) / 4.5), v = (float) ((lv.z[k] + wind[1] * clock * 0.02) / 4.5);
            float bright = 1f + flash * 0.6f;
            float a = Math.min(1f, lv.foam[k]) * lv.alpha[k];
            vc.addVertex(m, x, y, z).setColor(FOAM_TINT[0] * bright, FOAM_TINT[1] * bright, FOAM_TINT[2] * bright, a)
                    .setUv(u, v).setOverlay(OVERLAY).setLight(LIGHT).setNormal(0f, 1f, 0f);
        }

        /** One grid level's vertices: sea-local x, z, the height above the still surface, the colour and the foam. */
        private final class Level {
            final int i0, j0, nx, nz;
            final double[] x, y, z;
            final float[] r, g, b, alpha, foam;

            Level(int i0, int i1, int j0, int j1, double spacing, boolean fineLevel) {
                this.i0 = i0;
                this.j0 = j0;
                this.nx = Math.max(0, i1 - i0);
                this.nz = Math.max(0, j1 - j0);
                int n = (nx + 1) * (nz + 1);
                x = new double[n];
                y = new double[n];
                z = new double[n];
                r = new float[n];
                g = new float[n];
                b = new float[n];
                alpha = new float[n];
                foam = new float[n];
                long seed = w.sea.seed();
                double[] grad = new double[2];
                double hx = w.sea.hx(), hz = w.sea.hz();
                for (int j = 0; j <= nz; j++) {
                    for (int i = 0; i <= nx; i++) {
                        int k = idx(i, j);
                        double lx = Math.max(-hx, Math.min(hx, (i0 + i) * spacing));
                        double lz = Math.max(-hz, Math.min(hz, (j0 + j) * spacing));
                        double h1 = RoughSea.eval(lx, lz, clock, seed, grad);
                        double h = h1 * in;
                        double gx = grad[0] * in, gz = grad[1] * in;
                        double fo = RoughSea.foamFrom(h1, grad[0], grad[1], lx, lz, clock, seed, in);
                        x[k] = lx;
                        y[k] = h;
                        z[k] = lz;
                        // Camera-relative position, for the view direction and the distance fades.
                        double px = lx + w.ox - cam.x, py = h + w.oy - cam.y, pz = lz + w.oz - cam.z;
                        double dist = Math.sqrt(px * px + py * py + pz * pz);
                        double detail = Math.max(0, 1 - dist / DETAIL_REACH);
                        double[] sg = {gx, gz};
                        RoughSea.addDetail(lx, lz, clock, seed, in, detail, sg);
                        if (fineLevel) {
                            float s = w.shoreAt(level, lx, lz);
                            if (s > 0) fo = Math.max(fo, s * (0.55 + 0.45 * RoughSea.sin(lx * 1.7 + lz * 1.3 - clock * 0.11)));
                        }
                        shade(k, sg[0], sg[1], h1, fo, px, py, pz, dist);
                        double edge = Math.min(hx - Math.abs(lx), hz - Math.abs(lz));
                        float fade = (float) Math.max(0, Math.min(1, edge / EDGE_FADE));
                        alpha[k] *= fade * fade * (3 - 2 * fade);
                        foam[k] = (float) fo;
                    }
                }
            }

            int idx(int i, int j) {
                return j * (nx + 1) + i;
            }

            private void shade(int k, double gx, double gz, double h1, double fo, double px, double py, double pz, double dist) {
                double nl = Math.sqrt(gx * gx + 1 + gz * gz);
                double nx = -gx / nl, ny = 1 / nl, nz = -gz / nl;
                double vx = -px / dist, vy = -py / dist, vz = -pz / dist;
                double ndv = Math.max(0, nx * vx + ny * vy + nz * vz);
                double f1 = 1 - ndv, f2 = f1 * f1;
                double fresnel = 0.02 + 0.98 * f2 * f2 * f1;
                // The reflected view ray against the moon behind the clouds: a tight glint and a broad sheen.
                double dot2 = 2 * (nx * vx + ny * vy + nz * vz);
                double rx = dot2 * nx - vx, ry = dot2 * ny - vy, rz = dot2 * nz - vz;
                double rl = Math.max(0, rx * light[0] + ry * light[1] + rz * light[2]);
                double r2 = rl * rl, r4 = r2 * r2, r8 = r4 * r4, r16 = r8 * r8, r32 = r16 * r16;
                double glint = r32 * r16 * 0.9 + r4 * r2 * 0.10;
                double crest = Math.max(0, Math.min(1, (h1 / RoughSea.MAX_AMPLITUDE + 0.1) / 0.9));
                double sky0 = SKY[0] + (FLASH[0] - SKY[0]) * flash, sky1 = SKY[1] + (FLASH[1] - SKY[1]) * flash,
                        sky2 = SKY[2] + (FLASH[2] - SKY[2]) * flash;
                double body0 = DEEP[0] + CREST[0] * crest, body1 = DEEP[1] + CREST[1] * crest, body2 = DEEP[2] + CREST[2] * crest;
                double white = Math.min(1, fo) * 0.35;
                double gl = glint * (1 - Math.min(1, fo)) * (1 + flash);
                r[k] = (float) (mix(mix(body0, sky0, fresnel), FOAM_TINT[0], white) + GLINT[0] * gl);
                g[k] = (float) (mix(mix(body1, sky1, fresnel), FOAM_TINT[1], white) + GLINT[1] * gl);
                b[k] = (float) (mix(mix(body2, sky2, fresnel), FOAM_TINT[2], white) + GLINT[2] * gl);
                alpha[k] = (float) (0.90 + 0.10 * fresnel);
            }

            private double mix(double a, double b, double t) {
                return a + (b - a) * t;
            }
        }
    }
}
