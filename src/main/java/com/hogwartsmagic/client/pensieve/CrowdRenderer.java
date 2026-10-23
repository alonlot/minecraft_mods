package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.pensieve.scene.Crowd;
import com.hogwartsmagic.pensieve.scene.CrowdMesh;
import com.hogwartsmagic.pensieve.scene.CrowdSchedule;
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
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code crowd} effect: thousands of spectators drawn in one batched pass, no entities. Each {@link Stand} (one
 * {@code crowd} event) is cut into chunks of nearby spectators; each chunk is a GPU vertex buffer that is rebuilt and
 * re-uploaded only when {@link CrowdSchedule} says its figures changed (the nearest ~150 every frame, other full-detail
 * chunks every tick, reacting chunks on their pose step, idle far chunks never), and otherwise just drawn. A frame
 * rebuilds at most {@link #REBUILD_VERTICES} vertices beyond the every-frame chunks; the rest keep their pose a frame
 * longer. The geometry is {@link CrowdMesh}, scene-relative; the draw is the terrain's own: the solid block shader with
 * the chunk offset set to the scene origin less the camera, so the figures take the world's fog and lightmap like the
 * stands they sit in. The lightmap value is sampled at each figure's head (re-read every 2 s, a change rebuilds the
 * chunk, so a set still building lights them once it is done), and a lightning flash ({@link SkyDome#flash}) lifts
 * them toward blue-white through the colour modulator.
 *
 * <p><b>The matrices.</b> This draws at {@code AFTER_CUTOUT_BLOCKS}, which Forge fires from inside
 * {@code LevelRenderer.renderSectionLayer} for the cutout layer (1.21: LevelRenderer l.954, dispatched at l.1304),
 * before {@code renderLevel} pushes the camera rotation onto RenderSystem's model-view stack (l.979-982). So
 * RenderSystem's model-view is still identity here, and the event's pose matrix is the frustum (rotation) matrix that
 * vanilla's own section draw passes to the same shader: use the event's matrices, as the terrain does. (At
 * {@code AFTER_TRANSLUCENT_BLOCKS} and later the rotation is on RenderSystem's stack instead.)
 *
 * <p>In a {@code dev_} scene a debug line logs the crowd's frame cost every 5 s: CPU time spent here per frame (mesh
 * rebuilds, uploads and draw calls), average and worst, the vertices and bytes uploaded per frame, and the
 * level-of-detail counts.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CrowdRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    /** The most vertices one frame rebuilds beyond its every-frame chunks (the rest wait a frame). */
    static final int REBUILD_VERTICES = 60_000;
    /** How often a chunk re-reads the light at its figures' heads (ticks), and how many chunks may do so a frame. */
    static final float LIGHT_REFRESH = 40f;
    static final int LIGHT_CHUNKS_PER_FRAME = 4;
    /** Bytes per vertex of the block format (position, colour, uv, light, normal and pad). */
    static final int VERTEX_BYTES = 32;
    /** A lightning flash lifts the figures by this much per channel at its peak (r, g, b). */
    private static final float[] FLASH_LIFT = {0.55f, 0.6f, 0.75f};

    private static final CrowdMesh MESH = new CrowdMesh();
    private static final Sink SINK = new Sink();
    private static final BlockPos.MutableBlockPos AT = new BlockPos.MutableBlockPos();
    private static final float[] FOCUS = new float[3];
    /** The stands holding GPU buffers, to release them once their effect is gone. */
    private static final List<Stand> DRAWN = new ArrayList<>();
    private static ByteBufferBuilder bytes;
    private static long frame;

    // The debug timer.
    private static long timedNanos, worstNanos, lastLog, uploadedVerts;
    private static int timedFrames, drawnVerts, lastFull, lastMid, lastStrip;

    private CrowdRenderer() {
    }

    /** One running crowd: its spectators, its rebuild schedule and its chunks' GPU buffers. */
    static final class Stand {
        final Crowd crowd;
        final float t0;
        final int duration;
        final CrowdSchedule schedule;
        final int[] verts;
        final float[] lightAt;
        final int[] light;
        VertexBuffer[] buffers;

        Stand(Crowd crowd, float t0, int duration) {
            this.crowd = crowd;
            this.t0 = t0;
            this.duration = duration;
            this.schedule = new CrowdSchedule(crowd);
            int nc = schedule.chunks.length;
            verts = new int[nc];
            lightAt = new float[nc];
            java.util.Arrays.fill(lightAt, Float.NEGATIVE_INFINITY);
            light = new int[crowd.size()];
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

    /** Writes the mesh into the block format, with the light of the figure being written. */
    private static final class Sink implements CrowdMesh.Sink {
        BufferBuilder bb;
        int[] light;
        int current;

        @Override
        public void figure(int index) {
            current = light[index];
        }

        @Override
        public void vertex(float x, float y, float z, int argb) {
            bb.addVertex(x, y, z, argb, 0.5f, 0.5f, OverlayTexture.NO_OVERLAY, current, 0f, 1f, 0f);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("removal")
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
        frame++;
        float partial = PensieveClientState.partial();
        float now = PensieveClientState.sceneTime(partial);
        Vec3 cam = event.getCamera().getPosition();
        Vec3 origin = MemoryEvents.sceneOrigin();
        float cx = (float) (cam.x - origin.x), cy = (float) (cam.y - origin.y), cz = (float) (cam.z - origin.z);
        focus(now, stands.get(0).crowd);
        int budget = REBUILD_VERTICES;
        int drawn = 0;
        lastFull = lastMid = lastStrip = 0;
        for (Stand s : stands) {
            if (!s.showing(now)) continue;
            if (s.buffers == null) {
                s.buffers = new VertexBuffer[s.schedule.chunks.length];
                DRAWN.add(s);
            }
            refreshLight(level, s, now, origin);
            CrowdSchedule plan = s.schedule;
            int dirty = plan.plan(now, frame, cx, cy, cz);
            lastFull += plan.full;
            lastMid += plan.mid;
            lastStrip += plan.strip;
            for (int k = 0; k < dirty; k++) {
                int c = plan.chunk(k);
                boolean always = plan.everyFrame(c);
                if (!always && budget <= 0) continue;
                int n = rebuild(s, c, now, cx, cy, cz, plan.cut());
                if (!always) budget -= Math.max(1, n);
                plan.built(c);
            }
        }
        // Draw every chunk with the terrain's solid shader and the terrain's matrices (see the class comment).
        RenderType.solid().setupRenderState();
        RenderSystem.setShaderTexture(0, WHITE);
        double flash = SkyDome.flash(now, EffectVisuals.strikes()) * SkyRenderer.stormShare(partial);
        RenderSystem.setShaderColor(1f + (float) flash * FLASH_LIFT[0], 1f + (float) flash * FLASH_LIFT[1],
                1f + (float) flash * FLASH_LIFT[2], 1f);
        ShaderInstance shader = GameRenderer.getRendertypeSolidShader();
        if (shader != null) {
            Matrix4f view = event.getPoseStack();
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, view, event.getProjectionMatrix(), mc.getWindow());
            shader.apply();
            // Every chunk is scene-relative, so one offset serves them all (taken in double, then narrowed).
            Uniform offset = shader.CHUNK_OFFSET;
            if (offset != null) {
                offset.set((float) (origin.x - cam.x), (float) (origin.y - cam.y), (float) (origin.z - cam.z));
                offset.upload();
            }
            for (Stand s : stands) {
                if (s.buffers == null || !s.showing(now)) continue;
                for (int c = 0; c < s.buffers.length; c++) {
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

    /** Re-read the light at the heads of a few chunks whose reading is old; a change makes the chunk rebuild. */
    private static void refreshLight(ClientLevel level, Stand s, float now, Vec3 origin) {
        int done = 0;
        int[][] chunks = s.schedule.chunks;
        for (int c = 0; c < chunks.length; c++) {
            boolean first = s.lightAt[c] == Float.NEGATIVE_INFINITY;   // never read: read now, before its first build
            if (!first && (done >= LIGHT_CHUNKS_PER_FRAME || now - s.lightAt[c] < LIGHT_REFRESH)) continue;
            s.lightAt[c] = now;
            done++;
            long sig = 0;
            for (int i : chunks[c]) {
                AT.set(Math.floor(origin.x + s.crowd.x(i)), Math.floor(origin.y + s.crowd.y(i) + 1.2),
                        Math.floor(origin.z + s.crowd.z(i)));
                s.light[i] = LevelRenderer.getLightColor(level, AT);
                sig = sig * 31 + s.light[i];
            }
            s.schedule.light(c, sig);
        }
    }

    /** Rebuild chunk {@code c} of {@code s} at {@code now}; returns the vertices written. */
    private static int rebuild(Stand s, int c, float now, float cx, float cy, float cz, float[] cut) {
        if (bytes == null) bytes = new ByteBufferBuilder(1 << 20);
        SINK.bb = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        SINK.light = s.light;
        int n = MESH.build(s.crowd, s.schedule.chunks[c], now, cx, cy, cz, FOCUS[0], FOCUS[1], FOCUS[2], cut[0], cut[1], SINK);
        MeshData mesh = SINK.bb.build();
        SINK.bb = null;
        s.verts[c] = 0;
        if (mesh != null) {
            if (s.buffers[c] == null) s.buffers[c] = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            s.buffers[c].bind();
            s.buffers[c].upload(mesh);
            VertexBuffer.unbind();
            s.verts[c] = n;
        }
        uploadedVerts += n;
        return n;
    }

    /**
     * Where the heads turn ({@link #FOCUS}): the camera's look target, averaged over the last second (so the crowd
     * follows the play rather than every whip pan), or the crowd's own centre when the scene has no camera.
     */
    private static void focus(float now, Crowd first) {
        SceneDef scene = PensieveClientState.scene();
        FOCUS[0] = first.atX();
        FOCUS[1] = first.atY();
        FOCUS[2] = first.atZ();
        if (scene == null || scene.camera() == null || scene.camera().isEmpty()) return;
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
            FOCUS[0] = x / w;
            FOCUS[1] = y / w;
            FOCUS[2] = z / w;
        }
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

    /** The dev scenes' frame-time line: CPU time and upload per frame spent on the crowd, every 5 s. */
    private static void debugTimer(long nanos) {
        SceneDef scene = PensieveClientState.scene();
        if (scene == null || !scene.id().startsWith("dev_")) return;
        timedNanos += nanos;
        worstNanos = Math.max(worstNanos, nanos);
        timedFrames++;
        long wall = System.currentTimeMillis();
        if (lastLog == 0) lastLog = wall;
        if (wall - lastLog < 5000) return;
        long perFrame = uploadedVerts / Math.max(1, timedFrames);
        LOGGER.info("Pensieve crowd: t {} s, {} spectators (full {}, two-box {}, strip {}), {} vertices drawn, {} vertices "
                        + "({} KB) uploaded per frame, CPU {} ms per frame on average, {} ms at worst, over {} frames",
                String.format(java.util.Locale.ROOT, "%.1f", PensieveClientState.sceneTime(0) / 20f),
                lastFull + lastMid + lastStrip, lastFull, lastMid, lastStrip, drawnVerts, perFrame,
                perFrame * VERTEX_BYTES / 1024,
                String.format(java.util.Locale.ROOT, "%.3f", timedNanos / 1e6 / timedFrames),
                String.format(java.util.Locale.ROOT, "%.3f", worstNanos / 1e6), timedFrames);
        timedNanos = worstNanos = 0;
        timedFrames = 0;
        uploadedVerts = 0;
        lastLog = wall;
    }
}
