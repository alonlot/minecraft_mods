package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.pensieve.scene.CeilingSky;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

import java.util.List;

/**
 * The {@code enchanted_ceiling} effect: the Great Hall roof dissolving into a night sky. A downward-facing stack of
 * planes just under the roof, all full-bright:
 * <ol>
 *   <li>the sky: a deep gradient (#0B1030 at the walls to #1A2350 overhead) with a faint Milky Way band, over a
 *       mottled tiling texture so it is never a flat fill;</li>
 *   <li>the stars: several hundred soft dots, twinkling, crowding along the band; the brightest few carry a
 *       spiked glow;</li>
 *   <li>two layers of thin grey-blue cloud drifting slowly in opposite directions, veiling the stars;</li>
 *   <li>now and then a shooting star, and while the reveal runs, a pale shimmer along the dissolve front.</li>
 * </ol>
 * The reveal dissolves the roof from the middle outward ({@link CeilingSky#dissolve}): the sky starts transparent, so
 * the real roof shows through until the front passes. Everything is a function of the shared scene clock, so a late
 * joiner sees the same sky, already revealed.
 *
 * <p>Drawn at {@code AFTER_TRANSLUCENT_BLOCKS} from an identity matrix (LevelRenderer has already applied the camera
 * rotation; see {@code PatronusShieldRenderer}), with the anchor made camera-relative once in double precision. No
 * layer writes depth, so the candles and banners below, which did, still hide the sky behind them; the layers
 * composite in draw order. The eyes shader fades with the world's spherical fog. State lives in
 * {@link EffectVisuals}, so its {@code clear()} removes the sky with the scene.
 *
 * <p>Textures come from {@code build_scripts/generate_ceiling_textures.py}.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class EnchantedCeiling {
    private static final ResourceLocation SKY_TEX = tex("ceiling_sky");
    private static final ResourceLocation CLOUDS_A_TEX = tex("ceiling_clouds_a");
    private static final ResourceLocation CLOUDS_B_TEX = tex("ceiling_clouds_b");
    private static final ResourceLocation STAR_TEX = tex("ceiling_star");
    private static final ResourceLocation DOT_TEX = tex("ceiling_dot");

    private static final RenderType SKY = ModRenderTypes.ceilingLayer(SKY_TEX);
    private static final RenderType CLOUDS_A = ModRenderTypes.ceilingLayer(CLOUDS_A_TEX);
    private static final RenderType CLOUDS_B = ModRenderTypes.ceilingLayer(CLOUDS_B_TEX);
    private static final RenderType DOTS = ModRenderTypes.ceilingGlow(DOT_TEX);
    private static final RenderType GLOWS = ModRenderTypes.ceilingGlow(STAR_TEX);
    private static final RenderType SHIMMER = ModRenderTypes.ceilingGlow(SKY_TEX);

    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;

    /** Night gradient: the walls, and overhead. */
    private static final float[] EDGE = {0x0B / 255f, 0x10 / 255f, 0x30 / 255f};
    private static final float[] CENTRE = {0x1A / 255f, 0x23 / 255f, 0x50 / 255f};
    /** What the Milky Way band adds on its spine. */
    private static final float[] BAND = {0.055f, 0.060f, 0.095f};

    /** Layer heights below the sky plane, in blocks: stars just under the sky, clouds under the stars. */
    private static final float STAR_DROP = 0.02f;
    private static final float CLOUD_A_DROP = 0.035f;
    private static final float CLOUD_B_DROP = 0.05f;
    private static final float SHIMMER_DROP = 0.06f;

    /** Grid cell size in blocks (the dissolve and gradient are per vertex), and the most cells along a side. */
    private static final float CELL = 2f;
    private static final int MAX_CELLS = 64;
    /** Blocks of ceiling per tile of each texture. */
    private static final float SKY_TILE = 22f;
    private static final float CLOUD_A_TILE = 26f;
    private static final float CLOUD_B_TILE = 15f;
    /** Cloud strengths (the textures already top out at alpha 0.35). */
    private static final float CLOUD_A_ALPHA = 0.85f;
    private static final float CLOUD_B_ALPHA = 0.70f;
    /** Star dot half-size in blocks: faintest, plus this much for the brightest. About 1 to 3 px from the floor. */
    private static final float DOT_MIN = 0.035f;
    private static final float DOT_GROW = 0.065f;
    /** Stars at least this big also get a spiked glow, this big and this strong. */
    private static final float GLOW_FROM = 0.62f;
    private static final float GLOW_SIZE = 0.55f;
    private static final float GLOW_ALPHA = 0.42f;
    /** Stars per square block of ceiling, and the bounds. */
    private static final float STAR_DENSITY = 0.75f;
    private static final int MIN_STARS = 400, MAX_STARS = 1600;

    private EnchantedCeiling() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/" + name + ".png");
    }

    /** One running ceiling: where it is, how big, when it began and its fixed star field. */
    static final class Sky {
        final Vec3 at;
        final float hx;
        final float hz;
        final int duration;
        final float startT;
        final long seed;
        final float[][] stars;
        final float[][] tints;

        Sky(Vec3 at, float hx, float hz, int duration, float startT, long seed) {
            this.at = at;
            this.hx = hx;
            this.hz = hz;
            this.duration = duration > 0 ? duration : CeilingSky.DEFAULT_REVEAL_TICKS;
            this.startT = startT;
            this.seed = seed;
            int count = Math.round(4f * hx * hz * STAR_DENSITY);
            this.stars = CeilingSky.stars(seed, hx, hz, Math.max(MIN_STARS, Math.min(MAX_STARS, count)));
            this.tints = new float[stars.length][];
            for (int i = 0; i < stars.length; i++) {
                // Mostly white, some faintly gold, some faintly blue, as real stars are.
                int k = Math.floorMod((int) (seed >>> 7) + i * 0x9E3779B1, 10);
                tints[i] = k < 2 ? new float[]{1f, 0.88f, 0.70f} : k < 4 ? new float[]{0.75f, 0.85f, 1f}
                        : new float[]{1f, 1f, 1f};
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        List<Sky> skies = EffectVisuals.ceilings();
        if (skies.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !PensieveClientState.inMemory()) return;
        float partial = event.getPartialTick();
        float now = PensieveClientState.sceneTime(partial);
        Vec3 cam = event.getCamera().getPosition();
        // Identity, deliberately: the camera rotation is already on RenderSystem's modelview stack.
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        for (Sky sky : skies) {
            draw(sky, now, cam, m, buffer);
        }
    }

    private static void draw(Sky sky, float now, Vec3 cam, Matrix4f m, MultiBufferSource.BufferSource buffer) {
        float reveal = CeilingSky.revealAt(now - sky.startT, sky.duration);
        if (reveal <= 0f) return;
        // Camera-relative anchor, once, in double precision; everything else is a small offset from it.
        float ax = (float) (sky.at.x - cam.x);
        float ay = (float) (CeilingSky.planeY((float) sky.at.y) - cam.y);
        float az = (float) (sky.at.z - cam.z);
        int nx = cells(sky.hx);
        int nz = cells(sky.hz);

        // 1. The sky.
        VertexConsumer vc = buffer.getBuffer(SKY);
        grid(vc, m, sky, ax, ay, az, nx, nz, reveal, 0, 0f, 0f, SKY_TILE, 1f);
        buffer.endBatch(SKY);

        // 2. Stars, veiled by the clouds drawn after them.
        drawStars(sky, now, reveal, ax, ay - STAR_DROP, az, m, buffer);

        // 3. Clouds: the far layer drifts one way, the near one the other, a little faster.
        float drift = CeilingSky.cloudOffset(now);
        vc = buffer.getBuffer(CLOUDS_A);
        grid(vc, m, sky, ax, ay - CLOUD_A_DROP, az, nx, nz, reveal, 1, drift, drift * 0.35f, CLOUD_A_TILE,
                CLOUD_A_ALPHA);
        buffer.endBatch(CLOUDS_A);
        vc = buffer.getBuffer(CLOUDS_B);
        grid(vc, m, sky, ax, ay - CLOUD_B_DROP, az, nx, nz, reveal, 1, -drift * 1.4f, drift * 0.5f, CLOUD_B_TILE,
                CLOUD_B_ALPHA);
        buffer.endBatch(CLOUDS_B);

        // 4. The dissolve front's shimmer, and the odd shooting star.
        if (reveal < 1f) {
            vc = buffer.getBuffer(SHIMMER);
            grid(vc, m, sky, ax, ay - SHIMMER_DROP, az, nx, nz, reveal, 2, 0f, 0f, SKY_TILE, 1f);
            buffer.endBatch(SHIMMER);
        }
        float[] shot = CeilingSky.shootingStar(sky.seed, now, sky.hx, sky.hz);
        if (shot != null && reveal >= 1f) {
            drawShootingStar(shot, ax, ay - SHIMMER_DROP, az, m, buffer);
        }
    }

    private static int cells(float half) {
        return Math.max(2, Math.min(MAX_CELLS, (int) Math.ceil(2f * half / CELL)));
    }

    /**
     * The plane as an {@code nx} by {@code nz} grid. {@code mode} 0 is the sky (gradient and band colour, alpha from the
     * dissolve), 1 a cloud layer (white, alpha from the dissolve times {@code strength}), 2 the shimmer (additive,
     * strongest where the dissolve is half done). UVs are world-scaled by {@code tile} and shifted by {@code du, dv}.
     */
    private static void grid(VertexConsumer vc, Matrix4f m, Sky sky, float ax, float ay, float az, int nx, int nz,
                             float reveal, int mode, float du, float dv, float tile, float strength) {
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < nz; j++) {
                corner(vc, m, sky, ax, ay, az, i, j, nx, nz, reveal, mode, du, dv, tile, strength);
                corner(vc, m, sky, ax, ay, az, i + 1, j, nx, nz, reveal, mode, du, dv, tile, strength);
                corner(vc, m, sky, ax, ay, az, i + 1, j + 1, nx, nz, reveal, mode, du, dv, tile, strength);
                corner(vc, m, sky, ax, ay, az, i, j + 1, nx, nz, reveal, mode, du, dv, tile, strength);
            }
        }
    }

    private static void corner(VertexConsumer vc, Matrix4f m, Sky sky, float ax, float ay, float az, int i, int j,
                               int nx, int nz, float reveal, int mode, float du, float dv, float tile, float strength) {
        float u = i / (float) nx * 2f - 1f;
        float v = j / (float) nz * 2f - 1f;
        float x = u * sky.hx;
        float z = v * sky.hz;
        float a = CeilingSky.dissolve(reveal, u, v);
        float r, g, b, alpha;
        if (mode == 0) {
            float d = Math.min(1f, (float) Math.sqrt(u * u + v * v) / 1.2f);
            float k = 1f - d * d * (3f - 2f * d);                    // 1 overhead, 0 at the walls
            float band = CeilingSky.bandGlow(sky.seed, u, v) * (0.4f + 0.6f * k);
            r = EDGE[0] + (CENTRE[0] - EDGE[0]) * k + BAND[0] * band;
            g = EDGE[1] + (CENTRE[1] - EDGE[1]) * k + BAND[1] * band;
            b = EDGE[2] + (CENTRE[2] - EDGE[2]) * k + BAND[2] * band;
            alpha = a;
        } else if (mode == 1) {
            r = g = b = 1f;
            alpha = a * strength;
        } else {
            float edge = 4f * a * (1f - a);                          // 0 untouched or done, 1 mid-dissolve
            float e = edge * edge * 0.35f;
            r = 0.55f * e;
            g = 0.65f * e;
            b = 0.95f * e;
            alpha = e;
        }
        vertex(vc, m, ax + x, ay, az + z, r, g, b, alpha, x / tile + du, z / tile + dv);
    }

    private static void drawStars(Sky sky, float now, float reveal, float ax, float ay, float az, Matrix4f m,
                                  MultiBufferSource.BufferSource buffer) {
        VertexConsumer dots = buffer.getBuffer(DOTS);
        for (int i = 0; i < sky.stars.length; i++) {
            float[] s = sky.stars[i];
            float shown = CeilingSky.dissolve(reveal, s[0] / sky.hx, s[1] / sky.hz);
            if (shown <= 0.01f) continue;
            float bright = CeilingSky.twinkle(now, i) * (0.45f + 0.55f * s[2]) * shown;
            float half = DOT_MIN + DOT_GROW * s[2];
            float[] t = sky.tints[i];
            flat(dots, m, ax + s[0], ay, az + s[1], half, t[0] * bright, t[1] * bright, t[2] * bright, bright);
        }
        buffer.endBatch(DOTS);
        VertexConsumer glows = buffer.getBuffer(GLOWS);
        for (int i = 0; i < sky.stars.length; i++) {
            float[] s = sky.stars[i];
            if (s[2] < GLOW_FROM) continue;
            float shown = CeilingSky.dissolve(reveal, s[0] / sky.hx, s[1] / sky.hz);
            if (shown <= 0.01f) continue;
            float tw = CeilingSky.twinkle(now, i);
            float bright = GLOW_ALPHA * tw * tw * shown * s[2];
            float half = GLOW_SIZE * (0.6f + 0.4f * s[2]) * (0.85f + 0.15f * tw);
            float[] t = sky.tints[i];
            flat(glows, m, ax + s[0], ay - 0.002f, az + s[1], half, t[0] * bright, t[1] * bright, t[2] * bright,
                    bright);
        }
        buffer.endBatch(GLOWS);
    }

    /** A streak with a bright head, fading in and out over its flight. */
    private static void drawShootingStar(float[] s, float ax, float ay, float az, Matrix4f m,
                                         MultiBufferSource.BufferSource buffer) {
        float p = s[4];
        float fade = (float) Math.sin(Math.PI * p);
        float hx = s[0] + (s[2] - s[0]) * p;
        float hz = s[1] + (s[3] - s[1]) * p;
        float tail = 0.35f;
        float tp = Math.max(0f, p - tail);
        float tx = s[0] + (s[2] - s[0]) * tp;
        float tz = s[1] + (s[3] - s[1]) * tp;
        float dx = hx - tx, dz = hz - tz;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len > 1e-3f) {
            // The tail: a thin quad, transparent at the back, bright at the head.
            float w = 0.045f;
            float sx = -dz / len * w, sz = dx / len * w;
            VertexConsumer vc = buffer.getBuffer(SHIMMER);
            float c = 0.8f * fade;
            vertex(vc, m, ax + tx + sx, ay, az + tz + sz, 0f, 0f, 0f, 0f, 0.5f, 0.5f);
            vertex(vc, m, ax + hx + sx, ay, az + hz + sz, c * 0.85f, c * 0.92f, c, c, 0.5f, 0.5f);
            vertex(vc, m, ax + hx - sx, ay, az + hz - sz, c * 0.85f, c * 0.92f, c, c, 0.5f, 0.5f);
            vertex(vc, m, ax + tx - sx, ay, az + tz - sz, 0f, 0f, 0f, 0f, 0.5f, 0.5f);
            buffer.endBatch(SHIMMER);
        }
        VertexConsumer head = buffer.getBuffer(GLOWS);
        float h = 0.9f * fade;
        flat(head, m, ax + hx, ay - 0.002f, az + hz, 0.3f, h, h, h, h);
        buffer.endBatch(GLOWS);
    }

    /** A horizontal square of half-size {@code half} centred on (x, y, z), the whole texture on it. */
    private static void flat(VertexConsumer vc, Matrix4f m, float x, float y, float z, float half,
                             float r, float g, float b, float a) {
        vertex(vc, m, x - half, y, z - half, r, g, b, a, 0f, 0f);
        vertex(vc, m, x + half, y, z - half, r, g, b, a, 1f, 0f);
        vertex(vc, m, x + half, y, z + half, r, g, b, a, 1f, 1f);
        vertex(vc, m, x - half, y, z + half, r, g, b, a, 0f, 1f);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                               float r, float g, float b, float a, float u, float v) {
        vc.addVertex(m, x, y, z).setColor(clamp(r), clamp(g), clamp(b), clamp(a)).setUv(u, v)
                .setOverlay(OVERLAY).setLight(LIGHT).setNormal(0f, -1f, 0f);
    }

    private static float clamp(float f) {
        return f < 0f ? 0f : f > 1f ? 1f : f;
    }
}
