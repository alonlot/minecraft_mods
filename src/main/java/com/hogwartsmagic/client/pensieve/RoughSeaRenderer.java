package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.MemoryRowboatRenderer;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.entity.MemoryRowboatEntity;
import com.hogwartsmagic.pensieve.scene.RoughSea;
import com.hogwartsmagic.pensieve.scene.SeaMesh;
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
 * The {@code rough_sea} effect: the animated storm-sea surface over the event's area. The mesh, its levels of detail
 * and its shading are {@link SeaMesh} (pure, tested: under 20,000 vertices, no cracks, no allocation per build), on the
 * heights of {@link RoughSea} (so the rowing boat rides exactly this water). Per frame, from the scene clock:
 * <ol>
 *   <li>a depth-only lid over every memory rowboat's open hull, so the waves never show inside a boat;</li>
 *   <li>the water, near-opaque and depth-writing, with the ripple texture drifting with the wind;</li>
 *   <li>the lacy foam texture over the crests and where the water meets rock (a shore map sampled from the set,
 *       at most 400 new block columns a frame).</li>
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
    /** At most this many new block columns of the shore map are read from the set per frame. */
    private static final int SHORE_COLUMNS_PER_FRAME = 400;
    /**
     * Exact water reaches this far past a rowboat's bow and stern (and its sides); the patch then blends into the rings
     * over one more coarse cell. 2 keeps one boat's patch on the 8-block ring within 4 x 4 cells (1024 quads).
     */
    static final double BOAT_MARGIN = 2.0;

    private RoughSeaRenderer() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/" + name + ".png");
    }

    /** One running sea: its maths, its world centre, its reusable mesh and a lazily filled shore map. */
    static final class Water implements SeaMesh.Shore {
        final RoughSea.Sea sea;
        /** The still surface's centre in the world. */
        final double ox, oy, oz;
        final SeaMesh mesh = new SeaMesh();
        private final int w, d;
        /** Shore strength per block column of the area, NaN until sampled; reset twice while the set settles. */
        private final float[] shore;
        private final Random random = new Random();
        private final double[] grad = new double[2];
        private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        /** Sea-local {x, z, radius} of the boats on this sea, for the mesh's exact-water patches. */
        private final double[] boats = new double[SeaMesh.MAX_BOATS * 3];
        private int age;
        private int shoreBudget;
        private ClientLevel level;

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
            Random r = random;
            r.setSeed(sea.seed() * 31 + (long) Math.floor(now));
            int tries = (int) Math.ceil(14 * in);
            double clock = sea.clock(now);
            double wx = RoughSea.windX(sea.seed()), wz = RoughSea.windZ(sea.seed());
            for (int i = 0; i < tries; i++) {
                double lx = cam.x - ox + (r.nextDouble() * 2 - 1) * 22, lz = cam.z - oz + (r.nextDouble() * 2 - 1) * 22;
                if (Math.abs(lx) > sea.hx() - 1 || Math.abs(lz) > sea.hz() - 1) continue;
                double h = RoughSea.eval(lx, lz, clock, sea.seed(), grad);
                double f = RoughSea.foamFrom(h, grad[0], grad[1], lx, lz, clock, sea.seed(), in);
                if (f < 0.6) continue;
                double y = oy + h * in;
                for (int k = 0; k < 4; k++) {
                    level.addParticle(ParticleTypes.SPLASH, ox + lx + r.nextGaussian() * 0.3, y + 0.05,
                            oz + lz + r.nextGaussian() * 0.3, wx * 0.12, 0.12 + r.nextDouble() * 0.1, wz * 0.12);
                }
                if (r.nextFloat() < 0.35f * in) {
                    level.addParticle(ParticleTypes.POOF, ox + lx, y + 0.15, oz + lz, wx * 0.08, 0.02, wz * 0.08);
                }
            }
        }

        /** How much the water at sea-local (lx, lz) is broken against rock, 0 to 1 (sampled from the set once). */
        @Override
        public float at(double lx, double lz) {
            if (level == null) return 0f;
            int ix = (int) Math.floor(lx + sea.hx()), iz = (int) Math.floor(lz + sea.hz());
            if (ix < 0 || iz < 0 || ix >= w || iz >= d) return 0f;
            int idx = iz * w + ix;
            float v = shore[idx];
            if (!Float.isNaN(v)) return v;
            if (shoreBudget <= 0) return 0f;
            shoreBudget--;
            int bx = (int) Math.floor(ox + lx), bz = (int) Math.floor(oz + lz), by = (int) Math.floor(oy);
            float best = 0f;
            for (int dx = -2; dx <= 2 && best < 1f; dx++) {
                for (int dz = -2; dz <= 2 && best < 1f; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockState s = level.getBlockState(probe.set(bx + dx, by + dy, bz + dz));
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

        /**
         * Rebuild the mesh for this frame, with exact water round every rowboat on this sea (its hull's half-length plus
         * {@link #BOAT_MARGIN}), where the boat is drawn this frame.
         */
        void build(ClientLevel level, float now, Vec3 cam, float flash, List<ActorRuntime> rowboats, float partial) {
            this.level = level;
            this.shoreBudget = SHORE_COLUMNS_PER_FRAME;
            int n = 0;
            for (int i = 0; i < rowboats.size(); i++) {
                ActorRuntime b = rowboats.get(i);
                // Scene-relative: the world position minus the scene origin (ox is origin + the sea's centre).
                double sx = net.minecraft.util.Mth.lerp(partial, b.entity.xo, b.entity.getX()) - ox + sea.cx();
                double sz = net.minecraft.util.Mth.lerp(partial, b.entity.zo, b.entity.getZ()) - oz + sea.cz();
                n = SeaMesh.addBoat(sea, sx, sz,
                        com.hogwartsmagic.pensieve.scene.Rowboat.HALF_LENGTH * b.def.scale() + BOAT_MARGIN, boats, n);
            }
            mesh.build(sea, now, cam.x - ox, cam.y - oy, cam.z - oz, flash, this, boats, n);
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
        float partial = PensieveClientState.partial();
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
            w.build(level, now, cam, flash, boats, partial);
            SeaMesh mesh = w.mesh;
            if (mesh.quadCount == 0) continue;
            double clock = w.sea.clock(now);
            float wx = (float) RoughSea.windX(w.sea.seed()), wz = (float) RoughSea.windZ(w.sea.seed());
            // Camera-relative offset of the sea's centre.
            float cx = (float) (w.ox - cam.x), cy = (float) (w.oy - cam.y), cz = (float) (w.oz - cam.z);

            VertexConsumer vc = buffer.getBuffer(WATER);
            // The ripple texture drifts with the wind, about 6 blocks per tile.
            float du = (float) (-wx * clock * 0.03), dv = (float) (-wz * clock * 0.03);
            int[] q = mesh.quads;
            for (int n = 0, end = mesh.quadCount * 4; n < end; n++) {
                int k = q[n];
                vc.addVertex(m, mesh.x[k] + cx, mesh.y[k] + cy, mesh.z[k] + cz)
                        .setColor(mesh.r[k], mesh.g[k], mesh.b[k], mesh.alpha[k])
                        .setUv((mesh.x[k] + du) / 6f, (mesh.z[k] + dv) / 6f).setOverlay(OVERLAY).setLight(LIGHT)
                        .setNormal(0f, 1f, 0f);
            }
            buffer.endBatch(WATER);

            if (mesh.foamQuadCount == 0) continue;
            vc = buffer.getBuffer(FOAM);
            float bright = 1f + flash * 0.6f;
            float fr = SeaMesh.FOAM_TINT[0] * bright, fg = SeaMesh.FOAM_TINT[1] * bright, fb = SeaMesh.FOAM_TINT[2] * bright;
            float fu = (float) (wx * clock * 0.02), fv = (float) (wz * clock * 0.02);
            int[] f = mesh.foamQuads;
            for (int n = 0, end = mesh.foamQuadCount * 4; n < end; n++) {
                int k = f[n];
                vc.addVertex(m, mesh.x[k] + cx, mesh.y[k] + cy + 0.02f, mesh.z[k] + cz)
                        .setColor(fr, fg, fb, mesh.foam[k] * mesh.alpha[k])
                        .setUv((mesh.x[k] + fu) / 4.5f, (mesh.z[k] + fv) / 4.5f).setOverlay(OVERLAY).setLight(LIGHT)
                        .setNormal(0f, 1f, 0f);
            }
            buffer.endBatch(FOAM);
        }
    }
}
