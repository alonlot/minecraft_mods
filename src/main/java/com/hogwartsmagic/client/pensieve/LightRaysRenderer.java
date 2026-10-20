package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.pensieve.scene.LightRays;
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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * The {@code light_rays} effect: warm god-rays pouring through a doorway as the doors open, into the dark beyond.
 * All additive and full-bright, every piece soft-edged so overlaps blend instead of seaming:
 * <ul>
 *   <li>the gap itself, a bright soft rectangle just inside the doorway, plus a bloom over the wall face around it,
 *       both seen only from the side the light travels toward;</li>
 *   <li>{@link LightRays#SHAFTS} shafts, each a camera-facing ribbon round its axis, fanning slightly outward and
 *       sinking toward the floor, fading along their length and shimmering slowly out of step;</li>
 *   <li>a pool of light on the floor, as wide as the gap at the door and spreading and fading away from it;</li>
 *   <li>{@link LightRays#MOTES} dust motes drifting and glinting where the light reaches them.</li>
 * </ul>
 * The width follows the door gap ({@link LightRays#gap}: a thin bright line, then ease-in to full), so the light
 * squeezes through the crack first. A function of the scene clock: a late joiner sees the doors' light as everyone
 * does. Drawn at {@code AFTER_TRANSLUCENT_BLOCKS} from an identity matrix (the camera rotation is already applied),
 * camera-relative in double precision once; no depth write, so the first-years in the doorway stand silhouetted
 * against it. The eyes shader fades it with the world's fog. State lives in {@link EffectVisuals}, so its
 * {@code clear()} removes the light with the scene.
 *
 * <p>Textures: {@code build_scripts/generate_light_ray_textures.py} ({@code light_ray}, {@code light_glow}); motes use
 * the ceiling's {@code ceiling_dot}.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class LightRaysRenderer {
    private static final RenderType RAY = ModRenderTypes.ceilingGlow(tex("light_ray"));
    private static final RenderType GLOW = ModRenderTypes.ceilingGlow(tex("light_glow"));
    private static final RenderType DOT = ModRenderTypes.ceilingGlow(tex("ceiling_dot"));

    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;

    /** Warm gold (#FFD9A0), and the whiter heat at the doorway. */
    private static final float[] GOLD = {1f, 0.85f, 0.63f};
    private static final float[] HOT = {1f, 0.93f, 0.80f};

    /** A shaft's strength at the door, and how much is left at these fractions of its length (1 = the far end). */
    private static final float SHAFT_ALPHA = 0.13f;
    private static final float[] STATION = {0f, 0.3f, 1f};
    private static final float[] STATION_FADE = {1f, 0.55f, 0f};
    /** The gap's own glow, the bloom over the wall face, and the floor pool. */
    private static final float GAP_ALPHA = 0.85f;
    private static final float BLOOM_ALPHA = 0.22f;
    private static final float BLOOM_MARGIN = 1.4f;
    private static final float POOL_ALPHA = 0.32f;
    private static final float POOL_REACH = 0.75f;
    /** Mote half-size in blocks. */
    private static final float MOTE_SIZE = 0.028f;

    private LightRaysRenderer() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/" + name + ".png");
    }

    /** One running light: the doorway, its direction and size, when it began and its fixed shafts. */
    static final class Beam {
        final Vec3 at;
        final float yaw;
        final float hw;
        final float h;
        final float len;
        final int duration;
        final float startT;
        final long seed;
        final float[][] shafts;

        Beam(Vec3 at, float yaw, float hw, float h, float len, int duration, float startT, long seed) {
            this.at = at;
            this.yaw = yaw;
            this.hw = hw;
            this.h = h;
            this.len = len;
            this.duration = duration > 0 ? duration : LightRays.DEFAULT_OPEN_TICKS;
            this.startT = startT;
            this.seed = seed;
            this.shafts = LightRays.shafts(seed, LightRays.SHAFTS);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        List<Beam> beams = EffectVisuals.lightRays();
        if (beams.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !PensieveClientState.inMemory()) return;
        float now = PensieveClientState.sceneTime(event.getPartialTick());
        Camera camera = event.getCamera();
        // Identity, deliberately: the camera rotation is already on RenderSystem's modelview stack.
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        for (Beam beam : beams) {
            draw(beam, now, camera.getPosition(), camera.rotation(), m, buffer);
        }
    }

    /** The beam's frame, camera-relative: the doorway anchor, then lateral, up and along unit vectors. */
    private record Frame(Vector3f a, Vector3f s, Vector3f f) {
        Vector3f p(float lx, float ly, float lz) {
            return new Vector3f(a).add(s.x * lx + f.x * lz, ly, s.z * lx + f.z * lz);
        }
    }

    private static void draw(Beam beam, float now, Vec3 cam, Quaternionf camRot, Matrix4f m,
                             MultiBufferSource.BufferSource buffer) {
        float elapsed = now - beam.startT;
        float appear = LightRays.appear(elapsed);
        if (appear <= 0f) return;
        float gap = LightRays.gap(elapsed, beam.duration);
        double[] fw = LightRays.worldOffset(beam.yaw, 0f, 0f, 1f);
        double[] sw = LightRays.worldOffset(beam.yaw, 1f, 0f, 0f);
        Frame fr = new Frame(new Vector3f((float) (beam.at.x - cam.x), (float) (beam.at.y - cam.y),
                (float) (beam.at.z - cam.z)), new Vector3f((float) sw[0], 0f, (float) sw[2]),
                new Vector3f((float) fw[0], 0f, (float) fw[2]));
        float open = beam.hw * 0.85f * gap;

        // How squarely the camera sits downstream of the doorway: the gap glows toward that side only.
        Vector3f mid = fr.p(0f, beam.h * 0.5f, 0f);
        Vector3f toCam = new Vector3f(mid).negate();
        float facing = toCam.lengthSquared() > 1e-6f ? toCam.normalize().dot(fr.f) : 1f;
        float front = smooth(0f, 0.35f, facing) * appear;

        // 1. The gap, the bloom on the wall face round it, and the pool on the floor.
        VertexConsumer vc = buffer.getBuffer(GLOW);
        if (front > 0f) {
            float g = GAP_ALPHA * front;
            upright(vc, m, fr, -0.15f, open + 0.08f, 0f, beam.h, HOT, g);
            float b = BLOOM_ALPHA * front * (0.4f + 0.6f * (float) Math.sqrt(gap));
            upright(vc, m, fr, 0.3f, open + BLOOM_MARGIN, -0.3f, beam.h + BLOOM_MARGIN, GOLD, b);
        }
        float pool = POOL_ALPHA * appear * (0.35f + 0.65f * (float) Math.sqrt(gap));
        float reach = beam.len * POOL_REACH;
        float farHalf = open + 0.22f * reach + 0.3f;
        float y = 0.03f;
        put(vc, m, fr.p(-open - 0.1f, y, 0.05f), HOT, pool, 0f, 0.5f);
        put(vc, m, fr.p(open + 0.1f, y, 0.05f), HOT, pool, 1f, 0.5f);
        put(vc, m, fr.p(farHalf, y, reach), GOLD, pool, 1f, 1f);
        put(vc, m, fr.p(-farHalf, y, reach), GOLD, pool, 0f, 1f);
        buffer.endBatch(GLOW);

        // 2. The shafts.
        vc = buffer.getBuffer(RAY);
        for (int i = 0; i < beam.shafts.length; i++) {
            float[] s = beam.shafts[i];
            float[] e = LightRays.shaftEnds(s, gap, beam.hw, beam.h, beam.len);
            float[] w = LightRays.shaftHalfWidths(s, gap, beam.hw, beam.len);
            float a = SHAFT_ALPHA * LightRays.shimmer(now, i) * appear;
            shaft(vc, m, fr, e, w, a);
        }
        buffer.endBatch(RAY);

        // 3. Dust motes, facing the camera.
        Vector3f right = camRot.transform(new Vector3f(MOTE_SIZE, 0f, 0f));
        Vector3f up = camRot.transform(new Vector3f(0f, MOTE_SIZE, 0f));
        vc = buffer.getBuffer(DOT);
        for (int i = 0; i < LightRays.MOTES; i++) {
            float[] mo = LightRays.mote(beam.seed, i, now, gap, beam.hw, beam.h, beam.len);
            float b = mo[3] * appear * 0.9f;
            if (b <= 0.01f) continue;
            Vector3f c = fr.p(mo[0], mo[1], mo[2]);
            put(vc, m, new Vector3f(c).sub(right).sub(up), GOLD, b, 0f, 0f);
            put(vc, m, new Vector3f(c).add(right).sub(up), GOLD, b, 1f, 0f);
            put(vc, m, new Vector3f(c).add(right).add(up), GOLD, b, 1f, 1f);
            put(vc, m, new Vector3f(c).sub(right).add(up), GOLD, b, 0f, 1f);
        }
        buffer.endBatch(DOT);
    }

    /** One shaft: a camera-facing ribbon round its axis, in segments between the stations, fading to the far end. */
    private static void shaft(VertexConsumer vc, Matrix4f m, Frame fr, float[] e, float[] w, float alpha) {
        Vector3f start = fr.p(e[0], e[1], e[2]);
        Vector3f end = fr.p(e[3], e[4], e[5]);
        Vector3f axis = new Vector3f(end).sub(start);
        if (axis.lengthSquared() < 1e-6f) return;
        axis.normalize();
        Vector3f[] lo = new Vector3f[STATION.length];
        Vector3f[] hi = new Vector3f[STATION.length];
        for (int k = 0; k < STATION.length; k++) {
            Vector3f p = new Vector3f(start).lerp(end, STATION[k]);
            // Side = axis x (toward the camera): the ribbon turns its face to the viewer round its own axis.
            Vector3f side = new Vector3f(axis).cross(new Vector3f(p).negate());
            if (side.lengthSquared() < 1e-6f) side.set(fr.s);
            side.normalize().mul(w[0] + (w[1] - w[0]) * STATION[k]);
            lo[k] = new Vector3f(p).sub(side);
            hi[k] = new Vector3f(p).add(side);
        }
        for (int k = 0; k + 1 < STATION.length; k++) {
            float a0 = alpha * STATION_FADE[k];
            float a1 = alpha * STATION_FADE[k + 1];
            float[] c0 = k == 0 ? HOT : GOLD;
            put(vc, m, lo[k], c0, a0, 0f, STATION[k]);
            put(vc, m, hi[k], c0, a0, 1f, STATION[k]);
            put(vc, m, hi[k + 1], GOLD, a1, 1f, STATION[k + 1]);
            put(vc, m, lo[k + 1], GOLD, a1, 0f, STATION[k + 1]);
        }
    }

    /** An upright quad across the doorway at {@code along}: lateral ±half, height y0..y1, the whole glow texture. */
    private static void upright(VertexConsumer vc, Matrix4f m, Frame fr, float along, float half, float y0, float y1,
                                float[] c, float a) {
        put(vc, m, fr.p(-half, y0, along), c, a, 0f, 1f);
        put(vc, m, fr.p(half, y0, along), c, a, 1f, 1f);
        put(vc, m, fr.p(half, y1, along), c, a, 1f, 0f);
        put(vc, m, fr.p(-half, y1, along), c, a, 0f, 0f);
    }

    /** One additive vertex: colour premultiplied by {@code a}. */
    private static void put(VertexConsumer vc, Matrix4f m, Vector3f p, float[] c, float a, float u, float v) {
        float k = a < 0f ? 0f : a > 1f ? 1f : a;
        vc.addVertex(m, p.x, p.y, p.z).setColor(c[0] * k, c[1] * k, c[2] * k, k).setUv(u, v)
                .setOverlay(OVERLAY).setLight(LIGHT).setNormal(0f, 1f, 0f);
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }
}
