package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ambience.AmbientEffect;
import com.hogwartsmagic.client.ambience.AmbienceClock;
import com.hogwartsmagic.client.ambience.WorldAmbience;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.pensieve.scene.CeilingSky;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The {@code enchanted_ceiling} effect: the Great Hall's bewitched sky, open above the walls (the set has no roof over
 * the hall), in one of three styles, all full-bright:
 * <ul>
 *   <li><b>storm</b> (the default, the film's look), a real volume: {@link CeilingSky#SLICES} translucent cloud slices
 *       stacked from just above the wall tops to {@link CeilingSky#SKY_TOP} blocks up, each reaching further past the
 *       walls than the one below, with a 3D density field (thick and lit amber from below near the walls low down,
 *       thin, cool and hazy up high, real gaps throughout); stars on a wide plane {@link CeilingSky#STAR_HEIGHT} blocks
 *       up under a near-black backdrop, so as the camera moves the cloud slides across nearly still stars; lightning
 *       every 6 to 14 s lighting the slices round the strike from inside, falling off with depth, sometimes with a
 *       forked bolt at the strike's height, and soft distant thunder; and vapour curtains on every wall, from 2.5
 *       blocks below the wall top up 6 blocks into the cloud, so the walls rise into the storm with no seam.</li>
 *   <li><b>night</b>: the calm sky on the plane: a deep gradient (#0B1030 at the walls to #1A2350 overhead) with a
 *       faint Milky Way band, twinkling stars, two thin cloud layers drifting in opposite directions, and now and then
 *       a shooting star.</li>
 *   <li><b>real</b> ({@link CeilingSky#REAL}): the world's own sky, read from the client level when it draws. One
 *       opaque gradient on the plane blends the day blue, the dusk glow and the night sky by {@link
 *       CeilingSky#realWeights}; white clouds drift over it (greyer and thicker as the rain rises, with the stars
 *       dimmed), and shooting stars cross it on clear nights. When the thunder level passes a half the sky darkens and
 *       the storm style's cloud slices fill the space under the roof (only the slices below the effect's {@code hy}
 *       roof clearance, and no wider than the hall: there is a real roof above), with lightning.</li>
 * </ul>
 * Until its {@code t} the effect is a solid dark-plank roof plane at the ceiling height (the ceiling is registered when
 * the scene starts, so the open sky is never seen early); the reveal then dissolves that roof from the middle outward
 * ({@link CeilingSky#dissolve}) with a pale shimmer along the front, and the depth opens up behind it. Everything is a
 * function of the shared scene clock, so a late joiner sees the same sky, already revealed, and the same lightning.
 *
 * <p>Drawn at {@code AFTER_TRANSLUCENT_BLOCKS} from an identity matrix (LevelRenderer has already applied the camera
 * rotation; see {@code PatronusShieldRenderer}), with the anchor made camera-relative once in double precision. No
 * layer writes depth, so the walls, candles and banners, which did, still hide the sky behind them; the layers
 * composite in draw order, far to near (the camera is below the plane, so the top slice first). The floating candles
 * draw after this (lower priority) and write depth; they all sit below the plane, so along any view ray from the hall
 * they are nearer than every slice, and drawing them over the sky is exactly right. The eyes shader fades with the world's spherical fog. State lives in
 * {@link EffectVisuals}, so its {@code clear()} removes the sky with the scene.
 *
 * <p>Fabulous graphics: there the translucent layers render into their own targets and are composited by a post pass,
 * and which target is bound when this stage fires has not been checked. Fancy and Fast are the tested path; Fabulous is
 * on the Task 7 in-game check list.
 *
 * <p>Outside a memory the same drawing serves the world's own ceilings (the registered {@code KIND_CEILING} effects, the
 * castle's hall and the ceiling spell's rooms) within 128 blocks of the camera: always the real style, on the world's
 * clock ({@code AmbienceClock}) rather than the scene's, open unless the server told this client a reveal
 * ({@code WorldAmbience#revealStart}), when the roof dissolves from that moment. The Pensieve path is unchanged.
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
    /** The solid ceiling the sky dissolves out of: the vanilla dark oak planks, crisp (no blur). */
    private static final RenderType ROOF = ModRenderTypes.ceilingLayer(
            ResourceLocation.withDefaultNamespace("textures/block/dark_oak_planks.png"), false);
    private static final RenderType DOTS = ModRenderTypes.ceilingGlow(tex("ceiling_dot"));
    private static final RenderType GLOWS = ModRenderTypes.ceilingGlow(tex("ceiling_star"));
    private static final RenderType SHIMMER = ModRenderTypes.ceilingGlow(tex("ceiling_sky"));

    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;

    /** Layer heights below the sky plane, in blocks: stars just under the sky, clouds under the stars. */
    private static final float STAR_DROP = 0.02f;
    private static final float CLOUD_A_DROP = 0.035f;
    private static final float CLOUD_B_DROP = 0.05f;
    private static final float SHIMMER_DROP = 0.08f;
    /** Storm curtains: they reach this far below the plane and this far up into the cloud. */
    private static final float CURTAIN_DEPTH = 2.5f, CURTAIN_RISE = 6f;
    /** Curtain rows (height above the plane) and their opacity: gone at both ends, thickest at the wall top. */
    private static final float[] CURTAIN_ROWS = {CURTAIN_RISE, 3f, 0f, -0.9f, -CURTAIN_DEPTH};
    private static final float[] CURTAIN_FADE = {0f, 0.4f, 0.85f, 0.45f, 0f};
    /** The roof plane's colour: the planks in the dim of the candle-lit hall. */
    private static final float[] ROOF_COLOUR = {0.34f, 0.29f, 0.25f};
    /** Storm slices: grid cell size in blocks and the most cells along a side (coarse: the noise is smooth). */
    private static final float SLICE_CELL = 5f;
    private static final int SLICE_CELLS_MAX = 20;
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
    private static final float MIST_TILE = 11f;
    /** Cloud strengths (night textures top out at alpha 0.35, storm at about 0.97). */
    private static final float CLOUD_A_ALPHA = 0.85f;
    private static final float CLOUD_B_ALPHA = 0.70f;
    /** Star dot half-size in blocks: faintest, plus this much for the brightest. About 1 to 2 px from the floor. */
    private static final float DOT_MIN = 0.021f;
    private static final float DOT_GROW = 0.039f;
    /** Stars at least this big also get a spiked glow, this big and this strong (about the brightest tenth). */
    private static final float GLOW_FROM = 0.68f;
    private static final float GLOW_SIZE = 0.40f;
    private static final float GLOW_ALPHA = 0.42f;
    /** Storm stars, about 80 blocks off: bigger in blocks so they are still a pixel or two, and a few more of them. */
    private static final float FAR_DOT_MIN = 0.09f, FAR_DOT_GROW = 0.20f, FAR_GLOW_SIZE = 1.3f;
    private static final int FAR_STARS = 2400;
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
        /** The world's own sky ({@link CeilingSky#REAL}): weights come from the client level at draw time. */
        final boolean real;
        final double x, y, z;
        final float hx, hz;
        /** Real only: blocks of clear space above the plane before the roof (0: none, so no cloud slices fit). */
        final float hy;
        final int duration;
        final float startT;
        /**
         * When the roof starts to dissolve, on the same clock as {@code startT}. A memory's ceiling reveals at its own
         * start, so the two are equal; a world ceiling keeps a fixed {@code startT} (its lightning does not move) and
         * has this set from the reveal the server told.
         */
        float revealT;
        final long seed;
        final float[][] stars;
        final float[][] tints;
        /** Storm only: the high star field, far wider than the hall (null at night). */
        final float[][] farStars;
        final float[][] farTints;
        /** The next strike whose thunder has not been played yet. */
        private int nextThunder;

        Sky(String style, double x, double y, double z, float hx, float hz, int duration, float startT, long seed,
            float elapsed) {
            this(style, x, y, z, hx, hz, 0f, duration, startT, seed, elapsed);
        }

        Sky(String style, double x, double y, double z, float hx, float hz, float hy, int duration, float startT,
            long seed, float elapsed) {
            this.real = CeilingSky.REAL.equals(style);
            this.storm = !real && !CeilingSky.NIGHT.equals(style);
            this.hy = Math.max(0f, hy);
            this.x = x;
            this.y = y;
            this.z = z;
            this.hx = hx;
            this.hz = hz;
            this.duration = duration > 0 ? duration : CeilingSky.DEFAULT_REVEAL_TICKS;
            this.startT = startT;
            this.revealT = startT;
            this.seed = seed;
            this.stars = CeilingSky.stars(seed, hx, hz, CeilingSky.starCount(hx, hz));
            this.tints = tints(seed, stars.length);
            this.farStars = storm ? CeilingSky.stars(seed ^ 0x5F3759DFL, hx + CeilingSky.FAR_REACH,
                    hz + CeilingSky.FAR_REACH, FAR_STARS) : null;
            this.farTints = storm ? tints(seed ^ 0x5F3759DFL, FAR_STARS) : null;
            // A late joiner hears only thunder still to come.
            while (CeilingSky.thunderAt(seed, nextThunder) <= elapsed) nextThunder++;
        }

        /** Mostly white, some faintly gold, some faintly blue, as real stars are. */
        private static float[][] tints(long seed, int n) {
            float[][] out = new float[n][];
            for (int i = 0; i < n; i++) {
                int k = Math.floorMod((int) (seed >>> 7) + i * 0x9E3779B1, 10);
                out[i] = k < 2 ? new float[]{1f, 0.88f, 0.70f} : k < 4 ? new float[]{0.75f, 0.85f, 1f}
                        : new float[]{1f, 1f, 1f};
            }
            return out;
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
        Minecraft mc = Minecraft.getInstance();
        if (PensieveClientState.inMemory()) {
            List<Sky> skies = EffectVisuals.ceilings();
            if (skies.isEmpty() || mc.level == null) return;
            drawAll(skies, PensieveClientState.sceneTime(PensieveClientState.partial()), event.getCamera().getPosition(),
                    mc);
            return;
        }
        // Not in a memory: the world's own ceilings (the castle's hall, a bewitched room), on the world clock.
        if (mc.level == null || WorldAmbience.ofKind(AmbientEffect.KIND_CEILING).isEmpty()) {
            WORLD_SKIES.clear();
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        List<Sky> skies = worldSkies(mc.level, cam);
        if (skies.isEmpty()) return;
        drawAll(skies, AmbienceClock.now(mc.level, PensieveClientState.partial()), cam, mc);
    }

    private static void drawAll(List<Sky> skies, float now, Vec3 cam, Minecraft mc) {
        // Identity, deliberately: the camera rotation is already on RenderSystem's modelview stack.
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        for (Sky sky : skies) {
            draw(sky, now, cam, m, buffer);
        }
    }

    /** Ceilings further than this from the camera (to the nearest point of their box) are not drawn. */
    private static final double WORLD_RANGE = 128.0;
    /** A world ceiling's reveal has long finished at its fixed start; lightning is timed from it as well. */
    private static final float WORLD_START = -2f * CeilingSky.DEFAULT_REVEAL_TICKS;
    /** The sky built for each world ceiling, kept while it is registered and unchanged (its star field is not free). */
    private static final Map<UUID, WorldSky> WORLD_SKIES = new HashMap<>();

    private record WorldSky(AmbientEffect effect, Sky sky) {
    }

    /**
     * The world's ceilings in range, as skies ready to draw: the real-sky style, open (or, from the reveal the server
     * told, with the roof dissolving out of its start), one cached per effect.
     */
    private static List<Sky> worldSkies(ClientLevel level, Vec3 cam) {
        List<AmbientEffect> ceilings = WorldAmbience.ofKind(AmbientEffect.KIND_CEILING);
        WORLD_SKIES.keySet().removeIf(id -> ceilings.stream().noneMatch(e -> e.id().equals(id)));
        List<Sky> out = new ArrayList<>(ceilings.size());
        for (AmbientEffect e : ceilings) {
            if (WorldAmbience.distanceSqr(e, cam) > WORLD_RANGE * WORLD_RANGE) continue;
            WorldSky cached = WORLD_SKIES.get(e.id());
            if (cached == null || !cached.effect.equals(e)) {
                // Real style: the sky follows the client level's clock and weather when drawn. No duration, so the
                // reveal takes the default; the seed comes from the id, so every viewer's stars are the same.
                Sky sky = new Sky(CeilingSky.REAL, e.x(), e.y(), e.z(), Mth.clamp(e.hx(), 1f, 100f),
                        Mth.clamp(e.hz(), 1f, 100f), Mth.clamp(e.hy(), 0f, 100f), 0, WORLD_START,
                        e.id().getMostSignificantBits() ^ e.id().getLeastSignificantBits(), 0f);
                cached = new WorldSky(e, sky);
                WORLD_SKIES.put(e.id(), cached);
            }
            long reveal = WorldAmbience.revealStart(e.id());
            cached.sky.revealT = reveal == WorldAmbience.NO_REVEAL ? WORLD_START : AmbienceClock.at(level, reveal);
            out.add(cached.sky);
        }
        return out;
    }

    /** The plane being drawn: anchor (camera-relative), grid size, reveal. */
    private record Plane(Sky sky, float ax, float ay, float az, int nx, int nz, float reveal) {
    }

    private static void draw(Sky sky, float now, Vec3 cam, Matrix4f m, MultiBufferSource.BufferSource buffer) {
        float local = now - sky.startT;
        float reveal = CeilingSky.revealAt(now - sky.revealT, sky.duration);
        // Camera-relative anchor, once, in double precision; everything else is a small offset from it.
        Plane p = new Plane(sky, (float) (sky.x - cam.x), (float) (CeilingSky.planeY((float) sky.y) - cam.y),
                (float) (sky.z - cam.z), cells(sky.hx), cells(sky.hz), reveal);
        float drift = CeilingSky.cloudOffset(now);
        if (reveal > 0f) {
            if (sky.real) drawReal(sky, p, now, local, drift, m, buffer);
            else if (sky.storm) drawStorm(sky, p, now, local, drift, m, buffer);
            else drawNight(sky, p, now, drift, m, buffer);
        }
        // The roof: solid until the reveal, then dissolving from the middle out with a shimmer on its front.
        if (reveal < 1f) {
            layer(buffer, ROOF, m, p, 0f, (u, v, a, o) -> {
                o[0] = ROOF_COLOUR[0];
                o[1] = ROOF_COLOUR[1];
                o[2] = ROOF_COLOUR[2];
                o[3] = 1f - a;
            }, 0f, 0f, 1f);
            if (reveal > 0f) {
                layer(buffer, SHIMMER, m, p, SHIMMER_DROP, (u, v, a, o) -> {
                    float e = CeilingSky.shimmer(a);
                    o[0] = 0.55f * e;
                    o[1] = 0.65f * e;
                    o[2] = 0.95f * e;
                    o[3] = e;
                }, 0f, 0f, SKY_TILE);
            }
        }
    }

    /** The calm night: the old flat sky on the plane, now opaque (the roof above it is gone; the roof plane hides it). */
    private static void drawNight(Sky sky, Plane p, float now, float drift, Matrix4f m,
                                  MultiBufferSource.BufferSource buffer) {
        Plane full = new Plane(sky, p.ax, p.ay, p.az, p.nx, p.nz, 1f);
        layer(buffer, SKY, m, full, 0f, (u, v, a, o) -> {
            float[] c = CeilingSky.skyColour(CeilingSky.NIGHT, sky.seed, u, v);
            o[0] = c[0];
            o[1] = c[1];
            o[2] = c[2];
            o[3] = 1f;
        }, 0f, 0f, SKY_TILE);
        drawStars(sky, now, 1f, 1f, p.ax, p.ay - STAR_DROP, p.az, m, buffer);
        Shade white = (u, v, a, o) -> {
            o[0] = 1f;
            o[1] = 1f;
            o[2] = 1f;
            o[3] = a;
        };
        layer(buffer, CLOUDS_A, m, full, CLOUD_A_DROP, alpha(white, CLOUD_A_ALPHA), drift, drift * 0.35f, CLOUD_A_TILE);
        layer(buffer, CLOUDS_B, m, full, CLOUD_B_DROP, alpha(white, CLOUD_B_ALPHA), -drift * 1.4f, drift * 0.5f,
                CLOUD_B_TILE);
        float[] shot = CeilingSky.shootingStar(sky.seed, now, sky.hx, sky.hz);
        if (shot != null && p.reveal >= 1f) drawShootingStar(shot, p.ax, p.ay - SHIMMER_DROP, p.az, m, buffer);
    }

    /**
     * Cloud opacity multipliers by time of day, on top of CLOUD_A_ALPHA / CLOUD_B_ALPHA. A vertex alpha cannot exceed 1 and
     * the cloud textures top out at alpha 0.35, so a layer wanting more than that is drawn as several passes (see
     * {@link #cloudLayer}): night 1 is the old single pass, dusk and day need more to read as white cloud.
     */
    private static final float CLOUD_OPACITY_NIGHT = 1f, CLOUD_OPACITY_DUSK = 1.7f, CLOUD_OPACITY_DAY = 2.6f;
    /** Most passes one cloud layer is drawn in (each at vertex alpha up to 1), however thick the sky asks for. */
    private static final int MAX_CLOUD_PASSES = 4;
    private static final float[] NO_COLOUR = new float[3];
    /** Rain greys the cloud by up to this much, thickens it by this factor, and dims the stars and the open sky. */
    private static final float OVERCAST_GREY = 0.45f, OVERCAST_THICK = 1.2f, OVERCAST_STAR_DIM = 0.9f,
            OVERCAST_SKY_DIM = 0.55f;
    /** Past this thunder level the real sky switches to the storm style's cloud; the open sky then darkens by up to STORM_SKY_DIM. */
    private static final float STORM_FROM = 0.5f, STORM_SKY_DIM = 0.6f;

    /**
     * The world's own sky: the gradient (day, dusk and night blended by {@link CeilingSky#realWeights}), the stars, the
     * drifting clouds and, in a thunderstorm, storm slices and lightning, all from the client level's clock and weather.
     * The time and weather are read here, at draw time, so every viewer in the same world sees the same sky.
     */
    private static void drawReal(Sky sky, Plane p, float now, float local, float drift, Matrix4f m,
                                 MultiBufferSource.BufferSource buffer) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        float partial = PensieveClientState.partial();
        float[] w = CeilingSky.realWeights(level.getDayTime() % 24000L, level.getRainLevel(partial),
                level.getThunderLevel(partial));
        float day = w[0], dusk = w[1], night = w[2], overcast = w[3], storm = w[4];
        // Rain and thunder dim the open sky, so a day storm reads as dark cloud rather than blue behind it.
        float dim = (1f - OVERCAST_SKY_DIM * overcast) * (1f - STORM_SKY_DIM * storm);
        Plane full = new Plane(sky, p.ax, p.ay, p.az, p.nx, p.nz, 1f);
        float texture = 1f / CeilingSky.SKY_TEXTURE_MEAN;
        layer(buffer, SKY, m, full, 0f, (u, v, a, o) -> {
            // The night colour already carries 1 / SKY_TEXTURE_MEAN; the two packed colours get it here.
            float[] c = night > 0f ? CeilingSky.skyColour(CeilingSky.NIGHT, sky.seed, u, v) : NO_COLOUR;
            int d = day > 0f ? CeilingSky.daySkyColour(sky.seed, u, v) : 0;
            int s = dusk > 0f ? CeilingSky.duskTint(u, v) : 0;
            for (int ch = 0; ch < 3; ch++) {
                int shift = 16 - 8 * ch;
                float dc = ((d >> shift) & 0xFF) / 255f * texture;
                float sc = ((s >> shift) & 0xFF) / 255f * texture;
                o[ch] = (day * dc + dusk * sc + night * c[ch]) * dim;
            }
            o[3] = 1f;
        }, 0f, 0f, SKY_TILE);
        float stars = (night + 0.3f * dusk) * (1f - OVERCAST_STAR_DIM * overcast) * (1f - storm);
        if (stars > 0.01f) drawStars(sky, now, 1f, stars, p.ax, p.ay - STAR_DROP, p.az, m, buffer);
        if (storm > STORM_FROM) {
            drawRealStorm(sky, p, now, local, drift, m, buffer);
            return;
        }
        // Clouds: white by day, warm at dusk, as at night; rain greys and thickens them.
        float opacity = (day * CLOUD_OPACITY_DAY + dusk * CLOUD_OPACITY_DUSK + night * CLOUD_OPACITY_NIGHT)
                * (1f + OVERCAST_THICK * overcast);
        float grey = 1f - OVERCAST_GREY * overcast;
        Shade cloud = (u, v, a, o) -> {
            o[0] = grey;
            o[1] = grey * (1f - 0.18f * dusk);
            o[2] = grey * (1f - 0.35f * dusk);
            o[3] = a;
        };
        cloudLayer(buffer, CLOUDS_A, m, full, CLOUD_A_DROP, cloud, CLOUD_A_ALPHA * opacity, drift, drift * 0.35f,
                CLOUD_A_TILE);
        cloudLayer(buffer, CLOUDS_B, m, full, CLOUD_B_DROP, cloud, CLOUD_B_ALPHA * opacity, -drift * 1.4f,
                drift * 0.5f, CLOUD_B_TILE);
        float[] shot = CeilingSky.shootingStar(sky.seed, now, sky.hx, sky.hz);
        if (shot != null && p.reveal >= 1f && night > 0.5f && overcast < 0.5f) {
            drawShootingStar(shot, p.ax, p.ay - SHIMMER_DROP, p.az, m, buffer);
        }
    }

    /**
     * A cloud layer wanting total opacity {@code wanted} (which may exceed 1; the vertex alpha cannot): drawn as
     * {@code ceil(wanted)} stacked passes (at most {@link #MAX_CLOUD_PASSES}), each at vertex alpha {@code wanted / n}
     * (at most 1), so thicker sky really is thicker. {@code wanted <= 1} is one pass, exactly {@link #layer}.
     */
    private static void cloudLayer(MultiBufferSource.BufferSource buffer, RenderType type, Matrix4f m, Plane p,
                                   float drop, Shade shade, float wanted, float du, float dv, float tile) {
        int passes = Math.max(1, Math.min(MAX_CLOUD_PASSES, (int) Math.ceil(wanted)));
        Shade each = alpha(shade, Math.min(1f, wanted / passes));
        for (int i = 0; i < passes; i++) layer(buffer, type, m, p, drop, each, du, dv, tile);
    }

    /**
     * The real sky in a thunderstorm: a dark storm cloud layer on the plane, then the storm slices that fit under the
     * roof (those below {@code sky.hy}; none when the roof is right overhead), far to near, with the lightning's glow
     * slipped in at its own height (squeezed under the roof) and its bolt. The slices stay inside the walls.
     */
    private static void drawRealStorm(Sky sky, Plane p, float now, float local, float drift, Matrix4f m,
                                      MultiBufferSource.BufferSource buffer) {
        float[] flash = CeilingSky.flash(sky.seed, local);
        Plane full = new Plane(sky, p.ax, p.ay, p.az, p.nx, p.nz, 1f);
        // The cloud on the plane itself is always there: it is all the storm there is when the roof is right overhead.
        layer(buffer, STORM_A, m, full, CLOUD_A_DROP, (u, v, a, o) -> {
            float[] c = CeilingSky.stormCloudColour(u, v, flash, sky.hx, sky.hz);
            o[0] = c[0];
            o[1] = c[1];
            o[2] = c[2];
            o[3] = 0.9f;
        }, drift * 0.8f, drift * 0.25f, STORM_A_TILE);
        // Only slices below the roof, highest first: the camera is under the plane.
        float room = Math.max(0f, sky.hy - CeilingSky.ROOF_GAP);
        float flashY = Math.min(flash == null ? 0f : flash[5], room);
        boolean flashDrawn = flash == null;
        for (int k = 0; k < CeilingSky.SLICES; k++) {
            int i = CeilingSky.SLICES - 1 - k;
            float h = CeilingSky.sliceHeight(i, CeilingSky.SLICES);
            if (h >= room) continue;
            if (!flashDrawn && h < flashY) {
                drawFlash(sky, flash, p, p.ay + flashY, m, buffer);
                flashDrawn = true;
            }
            slice(sky, p, i, h, 0f, flash, now, drift, m, buffer);
        }
        if (!flashDrawn) drawFlash(sky, flash, p, p.ay + flashY, m, buffer);
    }

    /**
     * The storm as a volume. Far to near: a dark backdrop and the stars, high above; the {@link CeilingSky#SLICES}
     * cloud slices from the top down (each translucent, so they composite back to front for a camera below the plane);
     * the lightning glow and bolt slipped in at the strike's own height; then the vapour curtains on the walls. The far
     * sky (backdrop, stars, slices) is fogged by the scene's fog stretched {@link CeilingSky#FAR_FOG_STRETCH} times, so
     * it reads as distant sky rather than being swallowed by a fog sized for the hall, but still closes in with the
     * memory's own fog when it collapses.
     */
    private static void drawStorm(Sky sky, Plane p, float now, float local, float drift, Matrix4f m,
                                  MultiBufferSource.BufferSource buffer) {
        float[] flash = p.reveal > 0.5f ? CeilingSky.flash(sky.seed, local) : null;
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(fogStart * CeilingSky.FAR_FOG_STRETCH);
        RenderSystem.setShaderFogEnd(fogEnd * CeilingSky.FAR_FOG_STRETCH);
        try {
            drawBackdrop(sky, p, m, buffer);
            drawFarStars(sky, now, p, m, buffer);
            boolean below = p.ay > 0f;                       // the camera is under the plane: draw the top slice first
            boolean flashDrawn = flash == null;
            for (int k = 0; k < CeilingSky.SLICES; k++) {
                int i = below ? CeilingSky.SLICES - 1 - k : k;
                float h = CeilingSky.sliceHeight(i, CeilingSky.SLICES);
                if (!flashDrawn && (below ? h < flash[5] : h > flash[5])) {
                    drawFlash(sky, flash, p, p.ay + flash[5], m, buffer);
                    flashDrawn = true;
                }
                slice(sky, p, i, h, 1f, flash, now, drift, m, buffer);
            }
            if (!flashDrawn) drawFlash(sky, flash, p, p.ay + flash[5], m, buffer);
        } finally {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
        }
        drawCurtains(sky, p, flash, drift, m, buffer);
    }

    /**
     * One cloud slice {@code h} above the plane, reaching {@link CeilingSky#sliceReach} times {@code reachScale} past
     * the hall on every side (the real sky passes 0: its slices stay inside the walls, under the roof).
     */
    private static void slice(Sky sky, Plane p, int i, float h, float reachScale, float[] flash, float now,
                              float drift, Matrix4f m, MultiBufferSource.BufferSource buffer) {
        RenderType type = i % 2 == 0 ? STORM_A : STORM_B;
        float reach = CeilingSky.sliceReach(h) * reachScale;
        float ex = sky.hx + reach, ez = sky.hz + reach;
        int nx = Math.max(4, Math.min(SLICE_CELLS_MAX, (int) Math.ceil(2f * ex / SLICE_CELL)));
        int nz = Math.max(4, Math.min(SLICE_CELLS_MAX, (int) Math.ceil(2f * ez / SLICE_CELL)));
        float tile = STORM_A_TILE + h * 1.1f;
        // Neighbouring slices sample nearby parts of the texture, so the stack reads as one rolling mass.
        float du = h * 0.021f + drift * (0.8f + 0.03f * h);
        float dv = h * 0.013f + drift * 0.25f;
        VertexConsumer vc = buffer.getBuffer(type);
        float[][] row0 = new float[nz + 1][];
        float[][] row1 = new float[nz + 1][];
        sliceRow(sky, h, reach, ex, ez, nx, nz, 0, flash, now, row0);
        for (int gi = 0; gi < nx; gi++) {
            sliceRow(sky, h, reach, ex, ez, nx, nz, gi + 1, flash, now, row1);
            for (int gj = 0; gj < nz; gj++) {
                sliceVertex(vc, m, p, h, row0[gj], tile, du, dv);
                sliceVertex(vc, m, p, h, row1[gj], tile, du, dv);
                sliceVertex(vc, m, p, h, row1[gj + 1], tile, du, dv);
                sliceVertex(vc, m, p, h, row0[gj + 1], tile, du, dv);
            }
            float[][] t = row0;
            row0 = row1;
            row1 = t;
        }
        buffer.endBatch(type);
    }

    /** One grid row of a slice, {x, z, r, g, b, a} per vertex (each vertex is shared by four cells, so work it once). */
    private static void sliceRow(Sky sky, float h, float reach, float ex, float ez, int nx, int nz, int gi,
                                 float[] flash, float now, float[][] out) {
        float x = (gi / (float) nx * 2f - 1f) * ex;
        for (int gj = 0; gj <= nz; gj++) {
            float z = (gj / (float) nz * 2f - 1f) * ez;
            float a = CeilingSky.sliceAlpha(sky.seed, x, h, z, now);
            if (reach > 0.5f) {
                // Past the walls the slice thins away toward its edge, so no sheet of cloud ever shows a rim.
                float beyond = Math.max(Math.abs(x) - sky.hx, Math.abs(z) - sky.hz) / reach;
                float k = Math.max(0f, Math.min(1f, (beyond - 0.45f) / 0.55f));
                a *= 1f - k * k * (3f - 2f * k);
            }
            float[] c = CeilingSky.sliceColour(x, h, z, sky.hx, sky.hz, flash);
            out[gj] = new float[]{x, z, c[0], c[1], c[2], a};
        }
    }

    private static void sliceVertex(VertexConsumer vc, Matrix4f m, Plane p, float h, float[] v, float tile, float du,
                                    float dv) {
        vertex(vc, m, p.ax + v[0], p.ay + h, p.az + v[1], v[2], v[3], v[4], v[5], v[0] / tile + du, v[1] / tile + dv);
    }

    /** The dark of the far sky, a wide plane above the stars: near-black blue, a touch lighter overhead. */
    private static void drawBackdrop(Sky sky, Plane p, Matrix4f m, MultiBufferSource.BufferSource buffer) {
        float ex = sky.hx + CeilingSky.FAR_REACH, ez = sky.hz + CeilingSky.FAR_REACH;
        float y = p.ay + CeilingSky.BACKDROP_HEIGHT;
        VertexConsumer vc = buffer.getBuffer(SKY);
        int n = 10;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                backdrop(vc, m, p, i, j, n, ex, ez, y);
                backdrop(vc, m, p, i + 1, j, n, ex, ez, y);
                backdrop(vc, m, p, i + 1, j + 1, n, ex, ez, y);
                backdrop(vc, m, p, i, j + 1, n, ex, ez, y);
            }
        }
        buffer.endBatch(SKY);
    }

    private static void backdrop(VertexConsumer vc, Matrix4f m, Plane p, int i, int j, int n, float ex, float ez,
                                 float y) {
        float u = i / (float) n * 2f - 1f, v = j / (float) n * 2f - 1f;
        float d = Math.min(1f, (float) Math.sqrt(u * u + v * v));
        float k = 1f - d * d;
        float s = 1f / CeilingSky.SKY_TEXTURE_MEAN;
        vertex(vc, m, p.ax + u * ex, y, p.az + v * ez, (0.020f + 0.022f * k) * s, (0.024f + 0.028f * k) * s,
                (0.045f + 0.050f * k) * s, 1f, u * ex / 60f, v * ez / 60f);
    }

    /** The stars, far above the cloud on their own wide plane: the cloud slides across them, they barely move. */
    private static void drawFarStars(Sky sky, float now, Plane p, Matrix4f m, MultiBufferSource.BufferSource buffer) {
        float y = p.ay + CeilingSky.STAR_HEIGHT;
        VertexConsumer dots = buffer.getBuffer(DOTS);
        for (int i = 0; i < sky.farStars.length; i++) {
            float[] s = sky.farStars[i];
            float bright = CeilingSky.twinkle(sky.seed, now, i) * (0.45f + 0.55f * s[2]);
            float half = FAR_DOT_MIN + FAR_DOT_GROW * s[2];
            float[] t = sky.farTints[i];
            flat(dots, m, p.ax + s[0], y, p.az + s[1], half, t[0] * bright, t[1] * bright, t[2] * bright, bright);
        }
        buffer.endBatch(DOTS);
        VertexConsumer glows = buffer.getBuffer(GLOWS);
        for (int i = 0; i < sky.farStars.length; i++) {
            float[] s = sky.farStars[i];
            if (s[2] < GLOW_FROM) continue;
            float tw = CeilingSky.twinkle(sky.seed, now, i);
            float bright = GLOW_ALPHA * tw * tw * s[2];
            float[] t = sky.farTints[i];
            flat(glows, m, p.ax + s[0], y - 0.05f, p.az + s[1], FAR_GLOW_SIZE * (0.6f + 0.4f * s[2]),
                    t[0] * bright, t[1] * bright, t[2] * bright, bright);
        }
        buffer.endBatch(GLOWS);
    }

    /**
     * Curtains of vapour at every wall, from {@link #CURTAIN_DEPTH} below the plane (gone) through the wall top (thick)
     * up into the cloud ({@link #CURTAIN_RISE} above, gone): the walls rise into the storm with no seam.
     */
    private static void drawCurtains(Sky sky, Plane p, float[] flash, float drift, Matrix4f m,
                                     MultiBufferSource.BufferSource buffer) {
        VertexConsumer vc = buffer.getBuffer(MIST);
        float[] o = new float[4];
        float hx = sky.hx - CURTAIN_INSET, hz = sky.hz - CURTAIN_INSET;
        // Each wall: from corner (x0, z0) to (x1, z1), in plane units for the colour and dissolve.
        float[][] walls = {{-1, -1, 1, -1}, {1, -1, 1, 1}, {1, 1, -1, 1}, {-1, 1, -1, -1}};
        for (float[] w : walls) {
            float length = Math.abs(w[2] - w[0]) * hx + Math.abs(w[3] - w[1]) * hz;
            int n = Math.max(2, Math.min(MAX_CELLS, (int) Math.ceil(length / CELL)));
            for (int i = 0; i < n; i++) {
                for (int r = 0; r + 1 < CURTAIN_ROWS.length; r++) {
                    curtain(vc, m, p, sky, flash, w, i, n, r, hx, hz, length, drift, o);
                    curtain(vc, m, p, sky, flash, w, i + 1, n, r, hx, hz, length, drift, o);
                    curtain(vc, m, p, sky, flash, w, i + 1, n, r + 1, hx, hz, length, drift, o);
                    curtain(vc, m, p, sky, flash, w, i, n, r + 1, hx, hz, length, drift, o);
                }
            }
        }
        buffer.endBatch(MIST);
    }

    private static void curtain(VertexConsumer vc, Matrix4f m, Plane p, Sky sky, float[] flash, float[] w, int i, int n,
                                int row, float hx, float hz, float length, float drift, float[] o) {
        float k = i / (float) n;
        float u = w[0] + (w[2] - w[0]) * k;
        float v = w[1] + (w[3] - w[1]) * k;
        float h = CURTAIN_ROWS[row];
        float[] c = h > 0f ? CeilingSky.sliceColour(u * hx, h, v * hz, sky.hx, sky.hz, flash)
                : CeilingSky.stormCloudColour(u, v, flash, sky.hx, sky.hz);
        float a = CeilingSky.dissolve(p.reveal, u, v) * CURTAIN_FADE[row];
        float along = k * length;
        vertex(vc, m, p.ax + u * hx, p.ay + h, p.az + v * hz, c[0] * 0.9f, c[1] * 0.9f, c[2] * 0.9f, a,
                along / MIST_TILE + drift * 1.5f, -h / MIST_TILE - drift * 0.6f);
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
            streak(vc, m, x0, z0, x1, z1, y, 0.08f * s[4], 0.95f * i, 0.95f * i);       // the white-hot core
            streak(vc, m, x0, z0, x1, z1, y, 0.45f * s[4], 0.30f * i, 0.30f * i);       // its glow
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

    /** The star field on the plane; {@code strength} (0..1) scales every star's brightness (1 at night, less under cloud). */
    private static void drawStars(Sky sky, float now, float reveal, float strength, float ax, float ay, float az,
                                  Matrix4f m, MultiBufferSource.BufferSource buffer) {
        VertexConsumer dots = buffer.getBuffer(DOTS);
        for (int i = 0; i < sky.stars.length; i++) {
            float[] s = sky.stars[i];
            float shown = CeilingSky.dissolve(reveal, s[0] / sky.hx, s[1] / sky.hz);
            if (shown <= 0.01f) continue;
            float bright = CeilingSky.twinkle(sky.seed, now, i) * (0.45f + 0.55f * s[2]) * shown * strength;
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
            float bright = GLOW_ALPHA * tw * tw * shown * s[2] * strength;
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
