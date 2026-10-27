package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.pensieve.scene.SmokeFlight;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The streak of a smoke-flight actor ({@code smoke} / {@code unsmoke}, {@link SmokeFlight}): a near-black ribbon along
 * the path it has flown in the last second, fat and dark at the actor and tapering to nothing at the tail, so a Death
 * Eater crossing the sky reads as a black comet at 100 blocks, where its few smoke particles are only a haze. Two
 * layers, a dense core and a wider, fainter halo, each a strip of camera-facing quads (the side of every point is the
 * path's direction crossed with the way to the camera). Straight-alpha (not additive: it must be darker than the
 * sky), unlit, no depth write, drawn at {@code AFTER_TRANSLUCENT_BLOCKS} from an identity matrix like
 * {@link LightRaysRenderer}; the eyes shader fades it with the world's fog.
 *
 * <p>Everything comes from the scene clock ({@link SmokeFlight#trail}), so a late joiner sees the same streak. After an
 * {@code unsmoke} the ribbon stays where it was and fades over the same second. The soft-edged streak texture is the
 * light rays' ({@code light_ray}), tinted black.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class SmokeTrailRenderer {
    private static final RenderType RIBBON = ModRenderTypes.ceilingLayer(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/light_ray.png"));

    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;

    /** Near-black with a hint of blue, so it reads as smoke against a night sky. */
    private static final float R = 0.025f, G = 0.025f, B = 0.035f;
    /** Half-width of the core at the head, at actor scale 1 (blocks), and how much wider and fainter the halo is. */
    private static final float CORE_HALF = 0.85f, HALO_WIDEN = 1.9f;
    private static final float CORE_ALPHA = 1.0f, HALO_ALPHA = 0.32f;
    /** The tail never narrows below this share of the head, so a distant streak keeps a body. */
    private static final float TAIL_WIDTH = 0.12f;

    private static final float[] POINTS = new float[SmokeFlight.MAX_POINTS * SmokeFlight.STRIDE];
    /** Camera-relative points and their sides, reused every frame. */
    private static final float[] REL = new float[SmokeFlight.MAX_POINTS * 3];
    private static final float[] SIDE = new float[SmokeFlight.MAX_POINTS * 3];

    private SmokeTrailRenderer() {
    }

    // Draw order within AFTER_TRANSLUCENT_BLOCKS: ceiling (HIGH), candles (NORMAL), light rays (LOW); the smoke goes with the rays.
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        List<ActorRuntime> actors = MemoryActors.actors();
        BlockPos origin = MemoryActors.origin();
        if (actors.isEmpty() || origin == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !PensieveClientState.inMemory()) return;
        float now = PensieveClientState.sceneTime(PensieveClientState.partial());
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f m = new Matrix4f();                       // identity: the camera rotation is already applied
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        VertexConsumer vc = null;
        for (ActorRuntime a : actors) {
            if (a.smoke == null || !a.visible) continue;
            int n = SmokeFlight.trail(a.def, a.smoke, now, POINTS);
            if (n < 2) continue;
            if (vc == null) vc = buffer.getBuffer(RIBBON);
            ribbon(vc, m, a.def.scale(), n, origin, cam);
        }
        if (vc != null) buffer.endBatch(RIBBON);
    }

    /** The ribbon of the {@code n} points in {@link #POINTS}: a halo first, then the core over it. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, float scale, int n, BlockPos origin, Vec3 cam) {
        for (int k = 0; k < n; k++) {
            REL[k * 3] = (float) (origin.getX() + POINTS[k * SmokeFlight.STRIDE] - cam.x);
            REL[k * 3 + 1] = (float) (origin.getY() + POINTS[k * SmokeFlight.STRIDE + 1] - cam.y);
            REL[k * 3 + 2] = (float) (origin.getZ() + POINTS[k * SmokeFlight.STRIDE + 2] - cam.z);
        }
        // Each point's side: the path's direction through it crossed with the way to the camera (the camera is the origin).
        for (int k = 0; k < n; k++) {
            int a = Math.max(0, k - 1) * 3, b = Math.min(n - 1, k + 1) * 3;
            float tx = REL[b] - REL[a], ty = REL[b + 1] - REL[a + 1], tz = REL[b + 2] - REL[a + 2];
            float cx = -REL[k * 3], cy = -REL[k * 3 + 1], cz = -REL[k * 3 + 2];
            float sx = ty * cz - tz * cy, sy = tz * cx - tx * cz, sz = tx * cy - ty * cx;
            float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1e-6f) {                              // heading straight at or away from the camera
                sx = 1f;
                sy = 0f;
                sz = 0f;
                len = 1f;
            }
            SIDE[k * 3] = sx / len;
            SIDE[k * 3 + 1] = sy / len;
            SIDE[k * 3 + 2] = sz / len;
        }
        layer(vc, m, scale * CORE_HALF * HALO_WIDEN, HALO_ALPHA, n);
        layer(vc, m, scale * CORE_HALF, CORE_ALPHA, n);
    }

    /** One strip over the points: half-width {@code half} at the head tapering with age, alpha {@code alpha} likewise. */
    private static void layer(VertexConsumer vc, Matrix4f m, float half, float alpha, int n) {
        for (int k = 0; k + 1 < n; k++) {
            float t0 = SmokeFlight.taper(POINTS[k * SmokeFlight.STRIDE + 3]);
            float t1 = SmokeFlight.taper(POINTS[(k + 1) * SmokeFlight.STRIDE + 3]);
            float w0 = half * (TAIL_WIDTH + (1f - TAIL_WIDTH) * t0), w1 = half * (TAIL_WIDTH + (1f - TAIL_WIDTH) * t1);
            float a0 = alpha * t0, a1 = alpha * t1;
            if (a0 <= 0.002f && a1 <= 0.002f) continue;
            int i = k * 3, j = (k + 1) * 3;
            float v0 = k / (float) (n - 1), v1 = (k + 1) / (float) (n - 1);
            put(vc, m, REL[i] - SIDE[i] * w0, REL[i + 1] - SIDE[i + 1] * w0, REL[i + 2] - SIDE[i + 2] * w0, a0, 0f, v0);
            put(vc, m, REL[i] + SIDE[i] * w0, REL[i + 1] + SIDE[i + 1] * w0, REL[i + 2] + SIDE[i + 2] * w0, a0, 1f, v0);
            put(vc, m, REL[j] + SIDE[j] * w1, REL[j + 1] + SIDE[j + 1] * w1, REL[j + 2] + SIDE[j + 2] * w1, a1, 1f, v1);
            put(vc, m, REL[j] - SIDE[j] * w1, REL[j + 1] - SIDE[j + 1] * w1, REL[j + 2] - SIDE[j + 2] * w1, a1, 0f, v1);
        }
    }

    private static void put(VertexConsumer vc, Matrix4f m, float x, float y, float z, float a, float u, float v) {
        float k = a < 0f ? 0f : a > 1f ? 1f : a;
        vc.addVertex(m, x, y, z).setColor(R, G, B, k).setUv(u, v).setOverlay(OVERLAY).setLight(LIGHT).setNormal(0f, 1f, 0f);
    }
}
