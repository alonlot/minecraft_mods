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
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The {@code enchanted_ceiling} effect: the Great Hall roof dissolving into a bewitched sky. A downward-facing stack of
 * planes just under the roof, all full-bright, in one of two styles:
 * <ul>
 *   <li><b>storm</b> (the default, the film's look): a near-black sky; stars that glint only through the gaps; three
 *       layers of heavy billowing cloud rolling slowly in different directions, charcoal blue-grey in the middle and
 *       lit amber from below near the walls by the candles; lightning flickering inside the cloud every 6 to 14 s (the
 *       cloud round the flash lights up from within, sometimes a thin forked bolt shows between the layers) with soft
 *       distant thunder; and, so the sky reads as part of the hall rather than a picture on its lid, a thin mist hanging
 *       about 1.4 blocks below the cloud (the tops of any roof beams fade up into it) and curtains of vapour down the
 *       top 2.5 blocks of every wall, so the walls rise into the cloud with no seam.</li>
 *   <li><b>night</b>: a deep gradient (#0B1030 at the walls to #1A2350 overhead) with a faint Milky Way band, twinkling
 *       stars, two thin cloud layers drifting in opposite directions, and now and then a shooting star.</li>
 * </ul>
 * The reveal dissolves the roof from the middle outward ({@link CeilingSky#dissolve}), with a pale shimmer along the
 * front; the sky starts transparent, so the real roof shows until the front passes. Everything is a function of the
 * shared scene clock, so a late joiner sees the same sky, already revealed, and the same lightning.
 *
 * <p>Drawn at {@code AFTER_TRANSLUCENT_BLOCKS} from an identity matrix (LevelRenderer has already applied the camera
 * rotation; see {@code PatronusShieldRenderer}), with the anchor made camera-relative once in double precision. No
 * layer writes depth, so the candles and banners below, which did, still hide the sky behind them; the layers
 * composite in draw order. The eyes shader fades with the world's spherical fog. State lives in
 * {@link EffectVisuals}, so its {@code clear()} removes the sky with the scene.
 *
 * <p>Fabulous graphics: there the translucent layers render into their own targets and are composited by a post pass,
 * and which target is bound when this stage fires has not been checked. Fancy and Fast are the tested path; Fabulous is
 * on the Task 7 in-game check list.
 *
 * <p>Textures come from {@code build_scripts/generate_ceiling_textures.py}.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class EnchantedCeiling {
    private static final RenderType SKY = ModRenderTypes.ceilingLayer(tex("ceiling_sky"));
    private static final RenderType CLOUDS_A = ModRenderTypes.ceilingLayer(tex("ceiling_clouds_a"));
    private static final RenderType CLOUDS_B = ModRenderTypes.ceilingLayer(tex("ceiling_clouds_b"));
    private static final RenderType STORM_A = ModRenderTypes.ceilingLayer(tex("storm_clouds_a"));
    private static final RenderType STORM_B = ModRenderTypes.ceilingLayer(tex("storm_clouds_b"));
    private static final RenderType MIST = ModRenderTypes.ceilingLayer(tex("storm_mist"));
    private static final RenderType DOTS = ModRenderTypes.ceilingGlow(tex("ceiling_dot"));
    private static final RenderType GLOWS = ModRenderTypes.ceilingGlow(tex("ceiling_star"));
    private static final RenderType SHIMMER = ModRenderTypes.ceilingGlow(tex("ceiling_sky"));

    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;

    /** Layer heights below the sky plane, in blocks: stars just under the sky, clouds under the stars. */
    private static final float STAR_DROP = 0.02f;
    private static final float CLOUD_A_DROP = 0.035f;
    private static final float CLOUD_B_DROP = 0.05f;
    private static final float CLOUD_C_DROP = 0.065f;
    private static final float SHIMMER_DROP = 0.08f;
    /** Storm: the hanging mist sits this far below the cloud, and the wall curtains reach this far down. */
    private static final float MIST_DROP = 1.4f;
    private static final float CURTAIN_DEPTH = 2.5f;
    /** Curtains stand this far in from the rectangle's edge, so they are in front of the wall face. */
    private static final float CURTAIN_INSET = 0.05f;

    /** Grid cell size in blocks (the dissolve and colours are per vertex), and the most cells along a side. */
    private static final float CELL = 2f;
    private static final int MAX_CELLS = 64;
    /** Blocks of ceiling per tile of each texture. */
    private static final float SKY_TILE = 22f;
    private static final float CLOUD_A_TILE = 26f;
    private static final float CLOUD_B_TILE = 15f;
    private static final float STORM_A_TILE = 34f;
    private static final float STORM_B_TILE = 22f;
    private static final float STORM_C_TILE = 47f;
    private static final float MIST_TILE = 11f;
    /** Cloud strengths (night textures top out at alpha 0.35, storm at about 0.97). */
    private static final float CLOUD_A_ALPHA = 0.85f;
    private static final float CLOUD_B_ALPHA = 0.70f;
    private static final float STORM_A_ALPHA = 0.92f;
    private static final float STORM_B_ALPHA = 0.80f;
    private static final float STORM_C_ALPHA = 0.45f;
    private static final float MIST_ALPHA = 0.38f;
    /** Star dot half-size in blocks: faintest, plus this much for the brightest. About 1 to 2 px from the floor. */
    private static final float DOT_MIN = 0.021f;
    private static final float DOT_GROW = 0.039f;
    /** Stars at least this big also get a spiked glow, this big and this strong (about the brightest tenth). */
    private static final float GLOW_FROM = 0.68f;
    private static final float GLOW_SIZE = 0.40f;
    private static final float GLOW_ALPHA = 0.42f;
    /** Thunder: id, volume, and a low pitch range (it is far off). */
    private static final String THUNDER = "minecraft:entity.lightning_bolt.thunder";
    private static final float THUNDER_VOLUME = 0.22f;

    private EnchantedCeiling() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/" + name + ".png");
    }

    /** Colour and alpha of a grid vertex at plane point (u, v), revealed {@code a}, into {@code out} (rgba). */
    private interface Shade {
        void at(float u, float v, float a, float[] out);
    }

    /** One running ceiling: where it is, how big, its style, when it began and its fixed star field. */
    static final class Sky {
        final boolean storm;
        final double x, y, z;
        final float hx, hz;
        final int duration;
        final float startT;
        final long seed;
        final float[][] stars;
        final float[][] tints;
        /** The next strike whose thunder has not been played yet. */
        private int nextThunder;

        Sky(String style, double x, double y, double z, float hx, float hz, int duration, float startT, long seed,
            float elapsed) {
            this.storm = !CeilingSky.NIGHT.equals(style);
            this.x = x;
            this.y = y;
            this.z = z;
            this.hx = hx;
            this.hz = hz;
            this.duration = duration > 0 ? duration : CeilingSky.DEFAULT_REVEAL_TICKS;
            this.startT = startT;
            this.seed = seed;
            this.stars = CeilingSky.stars(seed, hx, hz, CeilingSky.starCount(hx, hz));
            this.tints = new float[stars.length][];
            for (int i = 0; i < stars.length; i++) {
                // Mostly white, some faintly gold, some faintly blue, as real stars are.
                int k = Math.floorMod((int) (seed >>> 7) + i * 0x9E3779B1, 10);
                tints[i] = k < 2 ? new float[]{1f, 0.88f, 0.70f} : k < 4 ? new float[]{0.75f, 0.85f, 1f}
                        : new float[]{1f, 1f, 1f};
            }
            // A late joiner hears only thunder still to come.
            while (CeilingSky.thunderAt(seed, nextThunder) <= elapsed) nextThunder++;
        }

        int nextThunderForTest() {
            return nextThunder;
        }

        /** One client tick at scene time {@code now}: plays each strike's thunder once, as it comes due. */
        void tick(float now) {
            if (!storm) return;
            float local = now - startT;
            while (CeilingSky.thunderAt(seed, nextThunder) <= local) {
                float late = local - CeilingSky.thunderAt(seed, nextThunder);
                if (late < 5f) {
                    float pitch = 0.5f + 0.2f * ((nextThunder * 0x9E3779B1 >>> 8) & 0xFF) / 255f;
                    MemoryAudio.playSfx(THUNDER, THUNDER_VOLUME, pitch);
                }
                nextThunder++;
            }
        }
    }

    // The three world-stage renderers share one stage; their order is fixed by priority so it never depends on
    // registration order: the ceiling first (it writes no depth and must sit behind everything), then the candles
    // (they write depth), then the light rays (additive, drawn last over the lot).
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        List<Sky> skies = EffectVisuals.ceilings();
        if (skies.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !PensieveClientState.inMemory()) return;
        float now = PensieveClientState.sceneTime(event.getPartialTick());
        Vec3 cam = event.getCamera().getPosition();
        // Identity, deliberately: the camera rotation is already on RenderSystem's modelview stack.
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        for (Sky sky : skies) {
            draw(sky, now, cam, m, buffer);
        }
    }

    /** The plane being drawn: anchor (camera-relative), grid size, reveal. */
    private record Plane(Sky sky, float ax, float ay, float az, int nx, int nz, float reveal) {
    }

    private static void draw(Sky sky, float now, Vec3 cam, Matrix4f m, MultiBufferSource.BufferSource buffer) {
        float local = now - sky.startT;
        float reveal = CeilingSky.revealAt(local, sky.duration);
        if (reveal <= 0f) return;
        // Camera-relative anchor, once, in double precision; everything else is a small offset from it.
        Plane p = new Plane(sky, (float) (sky.x - cam.x), (float) (CeilingSky.planeY((float) sky.y) - cam.y),
                (float) (sky.z - cam.z), cells(sky.hx), cells(sky.hz), reveal);
        String style = sky.storm ? CeilingSky.STORM : CeilingSky.NIGHT;
        float drift = CeilingSky.cloudOffset(now);

        // 1. The sky.
        layer(buffer, SKY, m, p, 0f, (u, v, a, o) -> {
            float[] c = CeilingSky.skyColour(style, sky.seed, u, v);
            o[0] = c[0];
            o[1] = c[1];
            o[2] = c[2];
            o[3] = a;
        }, 0f, 0f, SKY_TILE);

        // 2. Stars, veiled by the clouds drawn after them.
        drawStars(sky, now, reveal, p.ax, p.ay - STAR_DROP, p.az, m, buffer);

        // 3. Clouds.
        if (sky.storm) {
            float[] flash = reveal > 0.5f ? CeilingSky.flash(sky.seed, local) : null;
            Shade storm = (u, v, a, o) -> {
                float[] c = CeilingSky.stormCloudColour(u, v, flash, sky.hx, sky.hz);
                o[0] = c[0];
                o[1] = c[1];
                o[2] = c[2];
                o[3] = a;
            };
            layer(buffer, STORM_A, m, p, CLOUD_A_DROP, alpha(storm, STORM_A_ALPHA), drift * 0.8f, drift * 0.25f,
                    STORM_A_TILE);
            if (flash != null) drawFlash(sky, flash, p, p.ay - CLOUD_A_DROP - 0.005f, m, buffer);
            layer(buffer, STORM_B, m, p, CLOUD_B_DROP, alpha(storm, STORM_B_ALPHA), -drift * 1.25f, drift * 0.45f,
                    STORM_B_TILE);
            // A third, sparser pass of the far texture at another scale and heading: the masses seem to boil and
            // roll into each other instead of sliding past as two flat sheets.
            layer(buffer, STORM_A, m, p, CLOUD_C_DROP, alpha(storm, STORM_C_ALPHA), drift * 0.35f + 0.37f,
                    -drift * 0.9f + 0.61f, STORM_C_TILE);
            drawMist(sky, p, storm, drift, m, buffer);
        } else {
            Shade white = (u, v, a, o) -> {
                o[0] = 1f;
                o[1] = 1f;
                o[2] = 1f;
                o[3] = a;
            };
            layer(buffer, CLOUDS_A, m, p, CLOUD_A_DROP, alpha(white, CLOUD_A_ALPHA), drift, drift * 0.35f,
                    CLOUD_A_TILE);
            layer(buffer, CLOUDS_B, m, p, CLOUD_B_DROP, alpha(white, CLOUD_B_ALPHA), -drift * 1.4f, drift * 0.5f,
                    CLOUD_B_TILE);
        }

        // 4. The dissolve front's shimmer, and (at night) the odd shooting star.
        if (reveal < 1f) {
            layer(buffer, SHIMMER, m, p, SHIMMER_DROP, (u, v, a, o) -> {
                float e = CeilingSky.shimmer(a);
                o[0] = 0.55f * e;
                o[1] = 0.65f * e;
                o[2] = 0.95f * e;
                o[3] = e;
            }, 0f, 0f, SKY_TILE);
        }
        if (!sky.storm) {
            float[] shot = CeilingSky.shootingStar(sky.seed, now, sky.hx, sky.hz);
            if (shot != null && reveal >= 1f) drawShootingStar(shot, p.ax, p.ay - SHIMMER_DROP, p.az, m, buffer);
        }
    }

    /** {@code shade} with its alpha scaled by {@code k}. */
    private static Shade alpha(Shade shade, float k) {
        return (u, v, a, o) -> {
            shade.at(u, v, a, o);
            o[3] *= k;
        };
    }

    private static int cells(float half) {
        return Math.max(2, Math.min(MAX_CELLS, (int) Math.ceil(2f * half / CELL)));
    }

    /** One plane layer {@code drop} below the sky as its own batch. */
    private static void layer(MultiBufferSource.BufferSource buffer, RenderType type, Matrix4f m, Plane p, float drop,
                              Shade shade, float du, float dv, float tile) {
        VertexConsumer vc = buffer.getBuffer(type);
        float[] o = new float[4];
        for (int i = 0; i < p.nx; i++) {
            for (int j = 0; j < p.nz; j++) {
                corner(vc, m, p, drop, i, j, shade, du, dv, tile, o);
                corner(vc, m, p, drop, i + 1, j, shade, du, dv, tile, o);
                corner(vc, m, p, drop, i + 1, j + 1, shade, du, dv, tile, o);
                corner(vc, m, p, drop, i, j + 1, shade, du, dv, tile, o);
            }
        }
        buffer.endBatch(type);
    }

    private static void corner(VertexConsumer vc, Matrix4f m, Plane p, float drop, int i, int j, Shade shade,
                               float du, float dv, float tile, float[] o) {
        float u = i / (float) p.nx * 2f - 1f;
        float v = j / (float) p.nz * 2f - 1f;
        float x = u * p.sky.hx;
        float z = v * p.sky.hz;
        shade.at(u, v, CeilingSky.dissolve(p.reveal, u, v), o);
        vertex(vc, m, p.ax + x, p.ay - drop, p.az + z, o[0], o[1], o[2], o[3], x / tile + du, z / tile + dv);
    }

    /**
     * Storm only: the mist hanging {@link #MIST_DROP} below the cloud (it fades out toward the walls, so it never draws
     * a line across them), then a curtain of the same vapour down the top {@link #CURTAIN_DEPTH} of each wall, thick at
     * the cloud and gone at its foot: the walls rise into the cloud.
     */
    private static void drawMist(Sky sky, Plane p, Shade storm, float drift, Matrix4f m,
                                 MultiBufferSource.BufferSource buffer) {
        layer(buffer, MIST, m, p, MIST_DROP, (u, v, a, o) -> {
            storm.at(u, v, a, o);
            float wall = Math.min((1f - Math.abs(u)) * sky.hx, (1f - Math.abs(v)) * sky.hz);
            float keep = Math.max(0f, Math.min(1f, wall / 2f));
            o[0] *= 0.85f;
            o[1] *= 0.85f;
            o[2] *= 0.85f;
            o[3] *= MIST_ALPHA * keep * keep;
        }, drift * 2.2f, drift * 0.7f, MIST_TILE);

        VertexConsumer vc = buffer.getBuffer(MIST);
        float[] o = new float[4];
        float hx = sky.hx - CURTAIN_INSET, hz = sky.hz - CURTAIN_INSET;
        // Each wall: from corner (x0, z0) to (x1, z1), in plane units for the colour and dissolve.
        float[][] walls = {{-1, -1, 1, -1}, {1, -1, 1, 1}, {1, 1, -1, 1}, {-1, 1, -1, -1}};
        float[] rows = {0f, 0.35f, 1f};
        float[] fade = {0.85f, 0.45f, 0f};
        for (float[] w : walls) {
            float length = Math.abs(w[2] - w[0]) * hx + Math.abs(w[3] - w[1]) * hz;
            int n = Math.max(2, Math.min(MAX_CELLS, (int) Math.ceil(length / CELL)));
            for (int i = 0; i < n; i++) {
                for (int r = 0; r + 1 < rows.length; r++) {
                    curtain(vc, m, p, storm, w, i, n, rows[r], fade[r], hx, hz, length, drift, o);
                    curtain(vc, m, p, storm, w, i + 1, n, rows[r], fade[r], hx, hz, length, drift, o);
                    curtain(vc, m, p, storm, w, i + 1, n, rows[r + 1], fade[r + 1], hx, hz, length, drift, o);
                    curtain(vc, m, p, storm, w, i, n, rows[r + 1], fade[r + 1], hx, hz, length, drift, o);
                }
            }
        }
        buffer.endBatch(MIST);
    }

    private static void curtain(VertexConsumer vc, Matrix4f m, Plane p, Shade storm, float[] w, int i, int n,
                                float row, float fade, float hx, float hz, float length, float drift, float[] o) {
        float k = i / (float) n;
        float u = w[0] + (w[2] - w[0]) * k;
        float v = w[1] + (w[3] - w[1]) * k;
        storm.at(u, v, CeilingSky.dissolve(p.reveal, u, v), o);
        float along = k * length;
        float down = row * CURTAIN_DEPTH;
        vertex(vc, m, p.ax + u * hx, p.ay - down, p.az + v * hz, o[0] * 0.9f, o[1] * 0.9f, o[2] * 0.9f,
                o[3] * fade, along / MIST_TILE + drift * 1.5f, down / MIST_TILE - drift * 0.6f);
    }

    /** A flash: the cloud round it glowing from within, and on forked strikes a thin bolt across the sky. */
    private static void drawFlash(Sky sky, float[] flash, Plane p, float y, Matrix4f m,
                                  MultiBufferSource.BufferSource buffer) {
        float i = flash[2];
        VertexConsumer glow = buffer.getBuffer(DOTS);
        float r = CeilingSky.FLASH_RADIUS * 1.8f;
        float c = 0.55f * i;
        flat(glow, m, p.ax + flash[0] * sky.hx, y, p.az + flash[1] * sky.hz, r, c * 0.80f, c * 0.86f, c, c);
        buffer.endBatch(DOTS);
        if (flash[3] < 0.5f || i < 0.25f) return;
        VertexConsumer vc = buffer.getBuffer(SHIMMER);
        for (float[] s : CeilingSky.bolt(sky.seed, (int) flash[4])) {
            float x0 = p.ax + s[0] * sky.hx, z0 = p.az + s[1] * sky.hz;
            float x1 = p.ax + s[2] * sky.hx, z1 = p.az + s[3] * sky.hz;
            streak(vc, m, x0, z0, x1, z1, y, 0.05f * s[4], 0.95f * i, 0.95f * i);       // the white-hot core
            streak(vc, m, x0, z0, x1, z1, y, 0.30f * s[4], 0.30f * i, 0.30f * i);       // its glow
        }
        buffer.endBatch(SHIMMER);
    }

    /** A flat additive strip on the plane from (x0, z0) to (x1, z1), half-width {@code w}, bluish white. */
    private static void streak(VertexConsumer vc, Matrix4f m, float x0, float z0, float x1, float z1, float y,
                               float w, float c, float a) {
        float dx = x1 - x0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4f) return;
        float sx = -dz / len * w, sz = dx / len * w;
        vertex(vc, m, x0 + sx, y, z0 + sz, c * 0.80f, c * 0.86f, c, a, 0.5f, 0.5f);
        vertex(vc, m, x1 + sx, y, z1 + sz, c * 0.80f, c * 0.86f, c, a, 0.5f, 0.5f);
        vertex(vc, m, x1 - sx, y, z1 - sz, c * 0.80f, c * 0.86f, c, a, 0.5f, 0.5f);
        vertex(vc, m, x0 - sx, y, z0 - sz, c * 0.80f, c * 0.86f, c, a, 0.5f, 0.5f);
    }

    private static void drawStars(Sky sky, float now, float reveal, float ax, float ay, float az, Matrix4f m,
                                  MultiBufferSource.BufferSource buffer) {
        VertexConsumer dots = buffer.getBuffer(DOTS);
        for (int i = 0; i < sky.stars.length; i++) {
            float[] s = sky.stars[i];
            float shown = CeilingSky.dissolve(reveal, s[0] / sky.hx, s[1] / sky.hz);
            if (shown <= 0.01f) continue;
            float bright = CeilingSky.twinkle(sky.seed, now, i) * (0.45f + 0.55f * s[2]) * shown;
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
            float tw = CeilingSky.twinkle(sky.seed, now, i);
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
        float tp = Math.max(0f, p - 0.35f);
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
