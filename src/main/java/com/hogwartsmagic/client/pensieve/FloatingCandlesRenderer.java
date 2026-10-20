package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.pensieve.scene.FloatingCandles;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * The {@code floating_candles} effect: the Great Hall's floating candles. Hundreds of tall cream wax candles hang in a
 * box, each bobbing slowly on its own rhythm and drifting a little, each with a flickering, gently leaning flame and a
 * warm halo. One batched pass per layer for every candle, no entities:
 * <ol>
 *   <li>the wax: an upright quad per candle turned to face the camera round its own vertical axis (so it reads as a
 *       round candle from any side), full-bright cream, drawn with depth write so near candles hide far ones;</li>
 *   <li>the flames: additive, the same axial facing, leaning with {@link FloatingCandles#sway} and flickering;</li>
 *   <li>the halos: small additive camera-facing glows round each flame.</li>
 * </ol>
 * The candles light nothing: put {@code minecraft:light} blocks in the scene for that. A function of the scene clock,
 * so every viewer (late joiners included) sees the same candles in the same places. Drawn at
 * {@code AFTER_TRANSLUCENT_BLOCKS} from an identity matrix (the camera rotation is already applied), camera-relative in
 * double precision once; flames and halos fade with the world's fog. State lives in {@link EffectVisuals}, so its
 * {@code clear()} removes the candles with the scene.
 *
 * <p>Textures: {@code build_scripts/generate_candle_textures.py} ({@code candle_wax}, {@code candle_flame}); halos
 * use the ceiling's {@code ceiling_dot}.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class FloatingCandlesRenderer {
    /** Opaque, depth-writing, unshaded, lightmap at full: the text render type does exactly that for any texture. */
    private static final RenderType WAX = RenderType.text(tex("candle_wax"));
    private static final RenderType FLAME = ModRenderTypes.ceilingGlow(tex("candle_flame"));
    private static final RenderType HALO = ModRenderTypes.ceilingGlow(tex("ceiling_dot"));

    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;

    /** Wax colour at the top (lit by the flame) and the foot. */
    private static final float[] WAX_TOP = {1.00f, 0.95f, 0.80f};
    private static final float[] WAX_FOOT = {0.86f, 0.76f, 0.58f};
    /** Flame size in blocks, how far its tip leans at full sway, and how far it stands above the wax. */
    private static final float FLAME_HEIGHT = 0.17f, FLAME_HALF_WIDTH = 0.04f, FLAME_LEAN = 0.025f;
    private static final float FLAME_LIFT = -0.01f;
    /** Halo half-size and strength. */
    private static final float HALO_SIZE = 0.30f, HALO_ALPHA = 0.20f;
    /** Candles further than this from the camera are skipped (they are lost in the fog anyway). */
    private static final float MAX_DISTANCE = 160f;

    private FloatingCandlesRenderer() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/" + name + ".png");
    }

    /** One running flock of candles: the box centre and half-size, and the fixed candles. */
    static final class Flock {
        final double x, y, z;
        final float[][] candles;

        Flock(double x, double y, double z, float hx, float hy, float hz, int count, long seed) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.candles = FloatingCandles.candles(seed, count, hx, hy, hz);
        }
    }

    // Draw order within AFTER_TRANSLUCENT_BLOCKS: ceiling (HIGH), candles (NORMAL), light rays (LOW); see EnchantedCeiling.
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        List<Flock> flocks = EffectVisuals.candles();
        if (flocks.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !PensieveClientState.inMemory()) return;
        float now = PensieveClientState.sceneTime(event.getPartialTick());
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        // Identity, deliberately: the camera rotation is already on RenderSystem's modelview stack.
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        VertexConsumer vc = buffer.getBuffer(WAX);
        for (Flock f : flocks) wax(vc, m, f, now, cam);
        buffer.endBatch(WAX);
        vc = buffer.getBuffer(FLAME);
        for (Flock f : flocks) flames(vc, m, f, now, cam);
        buffer.endBatch(FLAME);
        vc = buffer.getBuffer(HALO);
        Quaternionf rot = camera.rotation();
        Vector3f right = rot.transform(new Vector3f(HALO_SIZE, 0f, 0f));
        Vector3f up = rot.transform(new Vector3f(0f, HALO_SIZE, 0f));
        for (Flock f : flocks) halos(vc, m, f, now, cam, right, up);
        buffer.endBatch(HALO);
    }

    /** The candle's base, camera-relative, or null when it is too far to bother. */
    private static Vector3f base(Flock f, float[] c, float now, Vec3 cam) {
        float[] p = FloatingCandles.position(c, now);
        float x = (float) (f.x - cam.x) + p[0];
        float y = (float) (f.y - cam.y) + p[1];
        float z = (float) (f.z - cam.z) + p[2];
        if (x * x + y * y + z * z > MAX_DISTANCE * MAX_DISTANCE) return null;
        return new Vector3f(x, y, z);
    }

    /** The horizontal unit vector across a candle at {@code b} as seen from the camera (at the origin). */
    private static float[] across(Vector3f b) {
        float len = (float) Math.sqrt(b.x * b.x + b.z * b.z);
        if (len < 1e-4f) return new float[]{1f, 0f};
        return new float[]{-b.z / len, b.x / len};
    }

    private static void wax(VertexConsumer vc, Matrix4f m, Flock f, float now, Vec3 cam) {
        float hw = FloatingCandles.WIDTH * 0.5f;
        for (float[] c : f.candles) {
            Vector3f b = base(f, c, now, cam);
            if (b == null) continue;
            float[] a = across(b);
            float sx = a[0] * hw, sz = a[1] * hw;
            float top = b.y + c[FloatingCandles.HEIGHT];
            put(vc, m, b.x - sx, b.y, b.z - sz, WAX_FOOT, 1f, 0f, 1f);
            put(vc, m, b.x + sx, b.y, b.z + sz, WAX_FOOT, 1f, 1f, 1f);
            put(vc, m, b.x + sx, top, b.z + sz, WAX_TOP, 1f, 1f, 0f);
            put(vc, m, b.x - sx, top, b.z - sz, WAX_TOP, 1f, 0f, 0f);
        }
    }

    private static void flames(VertexConsumer vc, Matrix4f m, Flock f, float now, Vec3 cam) {
        float[] white = {1f, 1f, 1f};
        for (int i = 0; i < f.candles.length; i++) {
            float[] c = f.candles[i];
            Vector3f b = base(f, c, now, cam);
            if (b == null) continue;
            float[] a = across(b);
            float k = FloatingCandles.flicker(now, i);
            float lean = FloatingCandles.sway(now, i) * FLAME_LEAN;
            float h = FLAME_HEIGHT * (0.85f + 0.15f * k);
            float w = FLAME_HALF_WIDTH;
            float y0 = b.y + c[FloatingCandles.HEIGHT] + FLAME_LIFT;
            float tx = a[0] * lean, tz = a[1] * lean;
            put(vc, m, b.x - a[0] * w, y0, b.z - a[1] * w, white, k, 0f, 1f);
            put(vc, m, b.x + a[0] * w, y0, b.z + a[1] * w, white, k, 1f, 1f);
            put(vc, m, b.x + a[0] * w + tx, y0 + h, b.z + a[1] * w + tz, white, k, 1f, 0f);
            put(vc, m, b.x - a[0] * w + tx, y0 + h, b.z - a[1] * w + tz, white, k, 0f, 0f);
        }
    }

    private static void halos(VertexConsumer vc, Matrix4f m, Flock f, float now, Vec3 cam, Vector3f right,
                              Vector3f up) {
        float[] warm = {1f, 0.72f, 0.38f};
        for (int i = 0; i < f.candles.length; i++) {
            float[] c = f.candles[i];
            Vector3f b = base(f, c, now, cam);
            if (b == null) continue;
            float k = HALO_ALPHA * FloatingCandles.flicker(now, i);
            float cx = b.x, cy = b.y + c[FloatingCandles.HEIGHT] + FLAME_HEIGHT * 0.4f, cz = b.z;
            put(vc, m, cx - right.x - up.x, cy - right.y - up.y, cz - right.z - up.z, warm, k, 0f, 0f);
            put(vc, m, cx + right.x - up.x, cy + right.y - up.y, cz + right.z - up.z, warm, k, 1f, 0f);
            put(vc, m, cx + right.x + up.x, cy + right.y + up.y, cz + right.z + up.z, warm, k, 1f, 1f);
            put(vc, m, cx - right.x + up.x, cy - right.y + up.y, cz - right.z + up.z, warm, k, 0f, 1f);
        }
    }

    /**
     * One vertex. For the additive layers the colour is premultiplied by {@code a}; the wax is opaque (a = 1). Every
     * attribute the bigger format needs is set; the text format ignores the extras it does not have.
     */
    private static void put(VertexConsumer vc, Matrix4f m, float x, float y, float z, float[] c, float a, float u,
                            float v) {
        vc.addVertex(m, x, y, z).setColor(c[0] * a, c[1] * a, c[2] * a, a).setUv(u, v).setOverlay(OVERLAY)
                .setLight(LIGHT).setNormal(0f, 1f, 0f);
    }
}
