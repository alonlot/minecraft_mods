package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.pensieve.scene.Crowd;
import com.hogwartsmagic.pensieve.scene.CrowdMesh;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SkyDome;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code crowd} effect: thousands of spectators drawn in one batched pass, no entities. Each {@link Stand} (one
 * {@code crowd} event) is cut into chunks of nearby spectators ({@link Crowd#chunks}); each chunk is a GPU vertex
 * buffer that is rebuilt only when its pose step comes round (every tick near the camera, every 2 ticks in two-box
 * range, every 5 beyond, staggered so chunks do not all rebuild on one frame) or never again once the stand stops, and
 * otherwise just drawn. A frame rebuilds at most {@link #REBUILD_VERTICES} vertices; the rest keep last step's pose
 * a frame longer. The geometry is {@link CrowdMesh}, scene-relative; the draw is the terrain's own: the solid block
 * shader with the chunk offset set to the scene origin less the camera, so the figures take the world's fog and
 * lightmap like the stands they sit in. The lightmap value is sampled at each figure's head (refreshed every 2 s, so a
 * set that is still building lights them once it is done), and a lightning flash ({@link SkyDome#flash}) lifts them
 * toward blue-white through the colour modulator.
 *
 * <p>In a {@code dev_} scene a debug line logs the crowd's frame cost every 5 s: CPU time spent here per frame (mesh
 * rebuilds, uploads and draw calls), average and worst, with the level-of-detail counts.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CrowdRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    /** The chunk cell (blocks) and the most spectators in one chunk. */
    static final float CHUNK_CELL = 12f;
    static final int CHUNK_MAX = 96;
    /** The most vertices one frame rebuilds (the rest wait a frame). */
    static final int REBUILD_VERTICES = 60_000;
    /** Pose step periods in ticks: full-detail chunks, two-box chunks and strips. */
    static final int NEAR_PERIOD = 1, MID_PERIOD = 2, FAR_PERIOD = 5;
    /** How often a chunk re-reads the light at its figures' heads (ticks). */
    static final float LIGHT_REFRESH = 40f;
    /** A lightning flash lifts the figures by this much per channel at its peak (r, g, b). */
    private static final float[] FLASH_LIFT = {0.55f, 0.6f, 0.75f};

    private static final CrowdMesh MESH = new CrowdMesh();
    private static final int[] BINS = new int[96];
    private static final float[] CUT = new float[2];
    /** The stands holding GPU buffers, to release them once their effect is gone. */
    private static final List<Stand> DRAWN = new ArrayList<>();
    private static ByteBufferBuilder bytes;
    private static int rebuildCursor;

    // The debug timer.
    private static long timedNanos, worstNanos, lastLog;
    private static int timedFrames, rebuiltVerts, drawnVerts, lastFull, lastMid, lastStrip;

    private CrowdRenderer() {
    }

    /** One running crowd: its spectators, chunks and their GPU buffers. */
    static final class Stand {
        final Crowd crowd;
        final float t0;
        final int duration;
        final int[][] chunks;
        final float[] chunkX, chunkY, chunkZ, chunkR;
        final int[] stagger;
        final long[] built;
        final int[] verts;
        final float[] lightAt;
        final int[] light;
        final float[] d2;
        VertexBuffer[] buffers;
        /** The figure the sink is writing (its light). */
        int current;

        Stand(Crowd crowd, float t0, int duration) {
            this.crowd = crowd;
            this.t0 = t0;
            this.duration = duration;
            this.chunks = crowd.chunks(CHUNK_CELL, CHUNK_MAX);
            int nc = chunks.length;
            chunkX = new float[nc];
            chunkY = new float[nc];
            chunkZ = new float[nc];
            chunkR = new float[nc];
            stagger = new int[nc];
            built = new long[nc];
            verts = new int[nc];
            lightAt = new float[nc];
            java.util.Arrays.fill(built, Long.MIN_VALUE);
            java.util.Arrays.fill(lightAt, Float.NEGATIVE_INFINITY);
            for (int c = 0; c < nc; c++) {
                float sx = 0, sy = 0, sz = 0;
                for (int i : chunks[c]) {
                    sx += crowd.x(i);
                    sy += crowd.y(i);
                    sz += crowd.z(i);
                }
                int m = Math.max(1, chunks[c].length);
                chunkX[c] = sx / m;
                chunkY[c] = sy / m + 1f;
                chunkZ[c] = sz / m;
                float r = 0;
                for (int i : chunks[c]) {
                    float dx = crowd.x(i) - chunkX[c], dz = crowd.z(i) - chunkZ[c];
                    r = Math.max(r, (float) Math.sqrt(dx * dx + dz * dz));
                }
                chunkR[c] = r + 1f;
                stagger[c] = (c * 7) % 10;
            }
            light = new int[crowd.size()];
            d2 = new float[crowd.size()];
        }

        boolean showing(float now) {
            return now >= t0 && (duration <= 0 || now < t0 + duration);
        }

        void release() {
            if (buffers == null) return;
            for (VertexBuffer b : buffers) {
                if (b != null) b.close();
            }
            buffers = null;
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) return;
        List<Stand> stands = EffectVisuals.crowds();
        releaseGone(stands);
        Minecraft mc = Minecraft.getInstance();
        if (stands.isEmpty() || !(mc.level instanceof ClientLevel level) || !PensieveClientState.inMemory()) {
            releaseGone(List.of());
            return;
        }
        long start = System.nanoTime();
        float partial = event.getPartialTick();
        float now = PensieveClientState.sceneTime(partial);
        Vec3 cam = event.getCamera().getPosition();
        Vec3 origin = MemoryEvents.sceneOrigin();
        float cx = (float) (cam.x - origin.x), cy = (float) (cam.y - origin.y), cz = (float) (cam.z - origin.z);
        float[] focus = focus(now, stands.get(0).crowd);
        int budget = REBUILD_VERTICES;
        int drawn = 0;
        lastFull = lastMid = lastStrip = 0;
        for (Stand s : stands) {
            if (!s.showing(now)) continue;
            if (s.buffers == null) {
                s.buffers = new VertexBuffer[s.chunks.length];
                DRAWN.add(s);
            }
            Crowd crowd = s.crowd;
            for (int i = 0; i < crowd.size(); i++) {
                float dx = crowd.x(i) - cx, dy = crowd.y(i) + 1f - cy, dz = crowd.z(i) - cz;
                s.d2[i] = dx * dx + dy * dy + dz * dz;
            }
            Crowd.lodCutoffs(s.d2, crowd.size(), CrowdMesh.FULL_RANGE, CrowdMesh.MID_RANGE, CrowdMesh.MAX_FULL,
                    CrowdMesh.MAX_MID, BINS, CUT);
            for (int i = 0; i < crowd.size(); i++) {
                if (s.d2[i] <= CUT[0]) lastFull++;
                else if (s.d2[i] <= CUT[1]) lastMid++;
                else lastStrip++;
            }
            int nc = s.chunks.length;
            for (int k = 0; k < nc; k++) {
                int c = (k + rebuildCursor) % nc;
                float dx = s.chunkX[c] - cx, dy = s.chunkY[c] - cy, dz = s.chunkZ[c] - cz;
                float near = Math.max(0f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - s.chunkR[c]);
                int period = near * near <= CUT[0] ? NEAR_PERIOD : near * near <= CUT[1] ? MID_PERIOD : FAR_PERIOD;
                long step = (long) Math.floor((now + s.stagger[c]) / period);
                long key = step * 8 + period;
                if (key == s.built[c] || budget <= 0) continue;
                budget -= rebuild(level, s, c, now, cx, cy, cz, focus, origin);
                s.built[c] = key;
            }
        }
        rebuildCursor++;
        // Draw every chunk with the terrain's solid shader: its fog, its lightmap, a white texture.
        RenderType.solid().setupRenderState();
        RenderSystem.setShaderTexture(0, WHITE);
        double flash = SkyDome.flash(now, EffectVisuals.strikes()) * SkyRenderer.stormShare(partial);
        RenderSystem.setShaderColor(1f + (float) flash * FLASH_LIFT[0], 1f + (float) flash * FLASH_LIFT[1],
                1f + (float) flash * FLASH_LIFT[2], 1f);
        ShaderInstance shader = GameRenderer.getRendertypeSolidShader();
        if (shader != null) {
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, RenderSystem.getModelViewMatrix(),
                    RenderSystem.getProjectionMatrix(), mc.getWindow());
            shader.apply();
            // Every chunk is scene-relative, so one offset serves them all (set in double, then narrowed).
            Uniform offset = shader.CHUNK_OFFSET;
            if (offset != null) {
                offset.set((float) (origin.x - cam.x), (float) (origin.y - cam.y), (float) (origin.z - cam.z));
                offset.upload();
            }
            for (Stand s : stands) {
                if (s.buffers == null || !s.showing(now)) continue;
                for (int c = 0; c < s.chunks.length; c++) {
                    VertexBuffer b = s.buffers[c];
                    if (b == null || s.verts[c] == 0) continue;
                    b.bind();
                    b.draw();
                    drawn += s.verts[c];
                }
            }
            if (offset != null) offset.set(0f, 0f, 0f);
            shader.clear();
        }
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderType.solid().clearRenderState();
        drawnVerts = drawn;
        debugTimer(System.nanoTime() - start);
    }

    /** Rebuild chunk {@code c} of {@code s} at {@code now}; returns the vertices written. */
    private static int rebuild(ClientLevel level, Stand s, int c, float now, float cx, float cy, float cz, float[] focus,
                               Vec3 origin) {
        Crowd crowd = s.crowd;
        if (now - s.lightAt[c] >= LIGHT_REFRESH) {
            s.lightAt[c] = now;
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int i : s.chunks[c]) {
                at.set(Math.floor(origin.x + crowd.x(i)), Math.floor(origin.y + crowd.y(i) + 1.2), Math.floor(origin.z + crowd.z(i)));
                s.light[i] = LevelRenderer.getLightColor(level, at);
            }
        }
        if (bytes == null) bytes = new ByteBufferBuilder(1 << 20);
        BufferBuilder bb = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        CrowdMesh.Sink sink = new CrowdMesh.Sink() {
            @Override
            public void figure(int index) {
                s.current = s.light[index];
            }

            @Override
            public void vertex(float x, float y, float z, int argb) {
                bb.addVertex(x, y, z, argb, 0.5f, 0.5f, OverlayTexture.NO_OVERLAY, s.current, 0f, 1f, 0f);
            }
        };
        int n = MESH.build(crowd, s.chunks[c], now, cx, cy, cz, focus[0], focus[1], focus[2], CUT[0], CUT[1], sink);
        MeshData mesh = bb.build();
        s.verts[c] = 0;
        if (mesh != null) {
            if (s.buffers[c] == null) s.buffers[c] = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            s.buffers[c].bind();
            s.buffers[c].upload(mesh);
            VertexBuffer.unbind();
            s.verts[c] = n;
        }
        rebuiltVerts += n;
        return Math.max(n, 1);
    }

    /**
     * Where the heads turn: the camera's look target, averaged over the last second (so the crowd follows the play
     * rather than every whip pan), or the crowd's own centre when the scene has no camera.
     */
    private static float[] focus(float now, Crowd first) {
        SceneDef scene = PensieveClientState.scene();
        float[] out = {first.atX(), first.atY(), first.atZ()};
        if (scene == null || scene.camera() == null || scene.camera().isEmpty()) return out;
        float x = 0, y = 0, z = 0, w = 0;
        for (int k = 0; k < 5; k++) {
            float[] p = scene.camera().lookTarget(Math.max(0f, now - k * 5f), MemoryActors::actorEye);
            if (p == null) continue;
            float wk = 5 - k;
            x += p[0] * wk;
            y += p[1] * wk;
            z += p[2] * wk;
            w += wk;
        }
        if (w > 0) {
            out[0] = x / w;
            out[1] = y / w;
            out[2] = z / w;
        }
        return out;
    }

    /** Release the GPU buffers of every stand that is no longer running. */
    private static void releaseGone(List<Stand> live) {
        for (int i = DRAWN.size() - 1; i >= 0; i--) {
            Stand s = DRAWN.get(i);
            boolean alive = false;
            for (Stand l : live) {
                if (l == s) {
                    alive = true;
                    break;
                }
            }
            if (!alive) {
                s.release();
                DRAWN.remove(i);
            }
        }
    }

    /** The dev scenes' frame-time line: CPU time per frame spent on the crowd, every 5 s. */
    private static void debugTimer(long nanos) {
        SceneDef scene = PensieveClientState.scene();
        if (scene == null || !scene.id().startsWith("dev_")) return;
        timedNanos += nanos;
        worstNanos = Math.max(worstNanos, nanos);
        timedFrames++;
        long wall = System.currentTimeMillis();
        if (lastLog == 0) lastLog = wall;
        if (wall - lastLog < 5000) return;
        LOGGER.info("Pensieve crowd: {} spectators (full {}, two-box {}, strip {}), {} vertices drawn, {} rebuilt per frame, "
                        + "CPU {} ms per frame on average, {} ms at worst, over {} frames",
                lastFull + lastMid + lastStrip, lastFull, lastMid, lastStrip, drawnVerts, rebuiltVerts / Math.max(1, timedFrames),
                String.format(java.util.Locale.ROOT, "%.3f", timedNanos / 1e6 / timedFrames),
                String.format(java.util.Locale.ROOT, "%.3f", worstNanos / 1e6), timedFrames);
        timedNanos = worstNanos = 0;
        timedFrames = rebuiltVerts = 0;
        lastLog = wall;
    }
}
