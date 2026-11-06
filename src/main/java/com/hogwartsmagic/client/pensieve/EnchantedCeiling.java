package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ambience.AmbientEffect;
import com.hogwartsmagic.client.ambience.AmbienceClock;
import com.hogwartsmagic.client.ambience.WorldAmbience;
import com.hogwartsmagic.client.ambience.WorldCeilingReveal;
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
 *   <li><b>real</b> ({@link CeilingSky#REAL}): the world's own sky, read from the client level when it draws, with
 *       depth. The open sky on the plane is coloured per vertex by the elevation of the camera's ray (deep overhead,
 *       hazy toward the walls, warm toward the sun at dusk), day, dusk and night blended by {@link
 *       CeilingSky#realWeights}. The far layers (the stars and Milky Way, high cirrus, a cloud deck) are drawn on the
 *       plane but placed by the camera ray as if they lay 300, 120 and 50 blocks up, so they slide past each other as
 *       you walk; the sun and moon sit where the ray toward them crosses the plane; and under a roof with room, real
 *       cloud slices hang between the plane and the roof, lit from the sun's side. Rain greys and closes the cloud and
 *       hides the sun; shooting stars cross clear nights. When the thunder level passes a half the sky darkens and
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
 * clock ({@code AmbienceClock}) rather than the scene's, open, except a sealed one (the castle's hall) shows its plank roof until the server tells this client a reveal began
 * ({@code WorldAmbience#revealStart}, when the roof dissolves from that moment) or that the player already had it
 * ({@code WorldAmbience#isOpened}); see {@code WorldCeilingReveal}. The Pensieve path is unchanged.
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
    /** The real sky: plain white under the per-vertex atmosphere, its cirrus and deck, the sun disc and the moon. */
    private static final RenderType CLEAR = ModRenderTypes.ceilingLayer(
            ResourceLocation.withDefaultNamespace("textures/misc/white.png"));
    private static final RenderType CIRRUS = ModRenderTypes.ceilingLayer(tex("sky_cirrus"));
    private static final RenderType DECK = ModRenderTypes.ceilingLayer(tex("sky_deck"));
    private static final RenderType SUN_DISC = ModRenderTypes.ceilingGlow(tex("ceiling_sun"));
    private static final RenderType MOON = ModRenderTypes.ceilingGlow(
            ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png"));

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
        /**
         * Real only (else null): the repeating star tile of the star layer ({@link CeilingSky#starField}, flat
         * {a, b, size}), each star's colour (flat rgb), and the Milky Way's direction and offset in the world.
         */
        final float[] field;
        final float[] fieldTint;
        final float bandCos, bandSin, bandOffset;
        /** Real only: the heights of the cloud slices that fit under the roof (empty with no room). */
        final float[] volume;
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
            this.field = real ? CeilingSky.starField(seed) : null;
            this.fieldTint = real ? fieldTints(seed, field.length / 3) : null;
            double band = CeilingSky.bandAngle(seed);
            this.bandCos = (float) Math.cos(band);
            this.bandSin = (float) Math.sin(band);
            this.bandOffset = CeilingSky.fieldBandOffset(seed);
            this.volume = real ? CeilingSky.volumeHeights(this.hy) : new float[0];
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

        /** The real sky's star colours, flat rgb: {@link CeilingSky#starColour} for each star of the tile. */
        private static float[] fieldTints(long seed, int n) {
            float[] out = new float[n * 3];
            float[] c = new float[3];
            for (int i = 0; i < n; i++) {
                CeilingSky.starColour(seed, i, c);
                out[i * 3] = c[0];
                out[i * 3 + 1] = c[1];
                out[i * 3 + 2] = c[2];
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
     * The world's ceilings in range, as skies ready to draw: the real-sky style, with the roof dissolving out of its
     * reveal start (a spell ceiling's creation, or the reveal the server told for a sealed one), one cached per effect.
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
            boolean told = reveal != WorldAmbience.NO_REVEAL;
            // Sealed (the castle's hall) until this player is told its reveal started, or that they already had it; a
            // spell's ceiling dissolves from the tick it was made, the same moment for every client.
            cached.sky.revealT = WorldCeilingReveal.start(e.sealed(), WorldAmbience.isOpened(e.id()), told,
                    told ? AmbienceClock.at(level, reveal) : 0f, AmbienceClock.at(level, e.createdTick()));
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

    /** Most passes one cloud layer is drawn in (each at vertex alpha up to 1), however thick the sky asks for. */
    private static final int MAX_CLOUD_PASSES = 4;
    /** Rain thickens the cloud deck by this factor, and dims the stars and the open sky. */
    private static final float OVERCAST_THICK = 1.2f, OVERCAST_STAR_DIM = 0.9f, OVERCAST_SKY_DIM = 0.55f;
    /** Past this thunder level the real sky switches to the storm style's cloud; the open sky then darkens by up to STORM_SKY_DIM. */
    private static final float STORM_FROM = 0.5f, STORM_SKY_DIM = 0.6f;

    // ------------------------------------------------------------------ the real sky, with depth

    /** What a real-sky grid layer works out at each vertex. Int constants, not an enum: no switch in render code. */
    private static final int GRID_SKY = 0, GRID_BAND = 1, GRID_CLOUD = 2;
    /** Floats per grid vertex in a row buffer: x, z, r, g, b, a, u, v. */
    private static final int GRID_STRIDE = 8;
    /** The real sky's grid: a vertex every this many blocks, and at least this many cells a side (small rooms). */
    private static final float REAL_CELL = 2f;
    private static final int REAL_MIN_CELLS = 8;
    /** Blocks of plane (seen from the hall floor) per tile: the band's grain, the cirrus and the cloud deck. */
    private static final float BAND_TILE = 9f, CIRRUS_TILE = 40f, DECK_TILE = 30f;
    /** The Milky Way's glow: its colour and strength (additive, over the night navy). */
    private static final float[] BAND_COLOUR = {0.55f, 0.60f, 0.80f};
    private static final float BAND_STRENGTH = 0.13f;
    private static final float BAND_DROP = 0.01f;
    /** Each layer's opacity by day, dusk and night (several passes past 1, as each pass tops out at vertex alpha 1). */
    private static final float CIRRUS_DAY = 0.9f, CIRRUS_DUSK = 1.1f, CIRRUS_NIGHT = 0.5f;
    private static final float DECK_DAY = 1.0f, DECK_DUSK = 0.95f, DECK_NIGHT = 0.75f;
    /**
     * Star sizes as angles (half-width over distance), so a star looks the same size from the floor or a ladder:
     * the flat sky's sizes as seen from 18 blocks. The glow of the brightest likewise.
     */
    private static final float STAR_ANGLE_MIN = DOT_MIN / 18f, STAR_ANGLE_GROW = DOT_GROW / 18f,
            STAR_GLOW_ANGLE = GLOW_SIZE / 18f;
    /** At most this many stars drawn (near the plane far more of the star layer is in view) and glows among them. */
    private static final float STAR_CAP = 2400f;
    private static final int MAX_GLOWS = 400;
    /** Floats per remembered glow: x, y, z, distance, size, brightness, star index. */
    private static final int GLOW_STRIDE = 7;
    /**
     * The sun: its disc's billboard half-size and its glow's, as angles (the disc fills 0.42 of its billboard, about
     * 2 degrees across, larger than life so it reads), and the glow's strength. The moon's billboard and glow likewise.
     */
    private static final float SUN_SIZE = 0.083f, SUN_GLOW = 0.5f, SUN_GLOW_STRENGTH = 0.45f;
    private static final float MOON_SIZE = 0.075f, MOON_GLOW = 0.3f, MOON_GLOW_STRENGTH = 0.25f;
    /** Rain hides the sun and moon by this much per unit of rain (gone before the rain is full); day pales the moon. */
    private static final float SUN_OVERCAST_HIDE = 1.25f, MOON_DAY_DIM = 0.8f;
    /**
     * The sun and moon billboards stand this fraction of the way from the camera to where their ray meets the plane:
     * the same spot on screen, but in the hall's air, so the roof's slope never clips them.
     */
    private static final float BILLBOARD_PULL = 0.85f;
    /** Real cloud slices: grid cell size and the most cells a side, how far toward the light the shading looks. */
    private static final float VOLUME_CELL = 4f, LIGHT_STEP = 3.5f, VOLUME_TILE = 16f;
    private static final int VOLUME_CELLS_MAX = 24;
    /** Night thins the real cloud slices (they are only moonlit). */
    private static final float VOLUME_NIGHT = 0.85f;

    // Scratch for the real sky. Render thread only, one sky at a time, so sharing it is safe and nothing is allocated
    // per vertex or per star.
    private static final float[] SUN = new float[3];
    private static final float[] HIT = new float[3];
    private static final float[] RGB = new float[3];
    private static final float[] ROW_A = new float[(MAX_CELLS + 1) * GRID_STRIDE];
    private static final float[] ROW_B = new float[(MAX_CELLS + 1) * GRID_STRIDE];
    private static final float[] GLOW_LIST = new float[MAX_GLOWS * GLOW_STRIDE];

    /**
     * The world's own sky, with depth. Everything far is drawn on the plane but placed by the camera ray as if it lay
     * high above ({@link CeilingSky#parallaxScale}), so walking under it the layers slide past one another as a deep
     * sky's would. Far to near: the open sky, coloured per vertex by the elevation of the camera's ray through it
     * ({@link CeilingSky#atmosphere}: deep overhead, hazy toward the walls, warm toward the sun at dusk); the Milky Way
     * and the stars on the star layer {@link CeilingSky#STAR_LAYER_H} up; the sun or moon where the ray toward it
     * meets the plane; a shooting star; then (unless it thunders, when the storm style's slices take over) the high
     * cirrus, the cloud deck, and, under a roof with room, real translucent cloud slices between the plane and the
     * roof, lit from the sun's side. The day / dusk / night blend is {@link CeilingSky#realWeights}; the time and
     * weather are read here, at draw time, so every viewer in the same world sees the same sky.
     */
    private static void drawReal(Sky sky, Plane p, float now, float local, float drift, Matrix4f m,
                                 MultiBufferSource.BufferSource buffer) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        float partial = PensieveClientState.partial();
        float[] w = CeilingSky.realWeights(level.getDayTime() % 24000L, level.getRainLevel(partial),
                level.getThunderLevel(partial));
        float day = w[0], dusk = w[1], night = w[2], overcast = w[3], storm = w[4];
        CeilingSky.sunDirection(level.getTimeOfDay(partial), SUN);
        // Rain and thunder dim the open sky, so a day storm reads as dark cloud rather than blue behind it.
        float dim = (1f - OVERCAST_SKY_DIM * overcast) * (1f - STORM_SKY_DIM * storm);
        realGrid(buffer, CLEAR, m, p, 0f, CeilingSky.STAR_LAYER_H, 1f, 0f, 0f, GRID_SKY, dim, 0f, w);
        float stars = (night + 0.3f * dusk) * (1f - OVERCAST_STAR_DIM * overcast) * (1f - storm);
        if (stars > 0.01f) {
            realGrid(buffer, SHIMMER, m, p, BAND_DROP, CeilingSky.STAR_LAYER_H, BAND_TILE, 0f, 0f, GRID_BAND,
                    stars * BAND_STRENGTH, 0f, w);
            drawFieldStars(sky, p, now, stars, m, buffer);
        }
        drawSunAndMoon(sky, p, w, level.getMoonPhase(), m, buffer);
        float[] shot = CeilingSky.shootingStar(sky.seed, now, sky.hx, sky.hz);
        if (shot != null && p.reveal >= 1f && night > 0.5f && overcast < 0.5f) {
            drawShootingStar(shot, p.ax, p.ay - SHIMMER_DROP, p.az, m, buffer);
        }
        if (storm > STORM_FROM) {
            drawRealStorm(sky, p, now, local, drift, m, buffer);
            return;
        }
        // The cirrus, high and slow, drifting across the wind of the deck below it (rain hides it behind the deck).
        float cirrus = (day * CIRRUS_DAY + dusk * CIRRUS_DUSK + night * CIRRUS_NIGHT) * (1f - 0.6f * overcast);
        realClouds(buffer, CIRRUS, m, p, CLOUD_A_DROP, CeilingSky.CIRRUS_H, CIRRUS_TILE, drift * 0.25f,
                -drift * 0.1f, cirrus, 1f, w);
        // The deck: rain thickens it and, past a little rain, a second copy offset by half a tile fills its gaps.
        float deck = (day * DECK_DAY + dusk * DECK_DUSK + night * DECK_NIGHT) * (1f + OVERCAST_THICK * overcast);
        realClouds(buffer, DECK, m, p, CLOUD_B_DROP, CeilingSky.DECK_H, DECK_TILE, drift * 0.6f, drift * 0.2f, deck,
                0f, w);
        if (overcast > 0.05f) {
            realGrid(buffer, DECK, m, p, CLOUD_B_DROP, CeilingSky.DECK_H, DECK_TILE, drift * 0.6f + 0.5f,
                    drift * 0.2f + 0.37f, GRID_CLOUD, Math.min(1f, overcast * 0.95f), 0f, w);
        }
        drawRealVolume(sky, p, now, drift, w, m, buffer);
    }

    /**
     * A cloud layer of the real sky wanting total opacity {@code wanted} (which may exceed 1; the vertex alpha cannot):
     * {@code ceil(wanted)} stacked passes (at most {@link #MAX_CLOUD_PASSES}), each at {@code wanted / n}.
     */
    private static void realClouds(MultiBufferSource.BufferSource buffer, RenderType type, Matrix4f m, Plane p,
                                   float drop, float h, float tile, float du, float dv, float wanted, float high,
                                   float[] w) {
        if (wanted <= 0.01f) return;
        int passes = Math.max(1, Math.min(MAX_CLOUD_PASSES, (int) Math.ceil(wanted)));
        float each = Math.min(1f, wanted / passes);
        for (int i = 0; i < passes; i++) realGrid(buffer, type, m, p, drop, h, tile, du, dv, GRID_CLOUD, each, high, w);
    }

    /**
     * One real-sky layer on the plane as its own batch, a grid of vertices each worked out once (rows streamed through
     * two scratch buffers): the texture is sampled where the camera ray through the vertex meets a layer {@code h}
     * above the plane ({@link CeilingSky#parallaxScale}; for a flat layer that is a uniform scale about the camera, so
     * the grid is only for the colour, which follows the ray). {@code mode} picks the colour; {@code strength} scales
     * it (the sky's dim, the band's strength, a cloud pass's opacity); {@code high} is the cloud's height class.
     */
    private static void realGrid(MultiBufferSource.BufferSource buffer, RenderType type, Matrix4f m, Plane p,
                                 float drop, float h, float tile, float du, float dv, int mode, float strength,
                                 float high, float[] w) {
        Sky sky = p.sky;
        int nx = Math.max(REAL_MIN_CELLS, Math.min(MAX_CELLS, (int) Math.ceil(2f * sky.hx / REAL_CELL)));
        int nz = Math.max(REAL_MIN_CELLS, Math.min(MAX_CELLS, (int) Math.ceil(2f * sky.hz / REAL_CELL)));
        float k = CeilingSky.parallaxScale(p.ay, h);
        float tileV = CeilingSky.layerTile(tile, h);
        VertexConsumer vc = buffer.getBuffer(type);
        float[] row0 = ROW_A, row1 = ROW_B;
        gridRow(p, nx, nz, 0, drop, k, tileV, du, dv, mode, strength, high, w, row0);
        for (int i = 0; i < nx; i++) {
            gridRow(p, nx, nz, i + 1, drop, k, tileV, du, dv, mode, strength, high, w, row1);
            for (int j = 0; j < nz; j++) {
                gridVertex(vc, m, p, drop, row0, j);
                gridVertex(vc, m, p, drop, row1, j);
                gridVertex(vc, m, p, drop, row1, j + 1);
                gridVertex(vc, m, p, drop, row0, j + 1);
            }
            float[] t = row0;
            row0 = row1;
            row1 = t;
        }
        buffer.endBatch(type);
    }

    /** Grid row {@code i} of a real-sky layer into {@code out}, {@link #GRID_STRIDE} floats per vertex. */
    private static void gridRow(Plane p, int nx, int nz, int i, float drop, float k, float tileV, float du, float dv,
                                int mode, float strength, float high, float[] w, float[] out) {
        Sky sky = p.sky;
        float cx = -p.ax, cz = -p.az;                       // the camera, from the ceiling's centre
        float x = (i / (float) nx * 2f - 1f) * sky.hx;
        float ry0 = p.ay - drop;
        for (int j = 0; j <= nz; j++) {
            float z = (j / (float) nz * 2f - 1f) * sky.hz;
            // The layer's point on this vertex's camera ray (CeilingSky.parallaxPoint, inlined: k is per layer).
            float sx = cx + (x - cx) * k, sz = cz + (z - cz) * k;
            float rx = p.ax + x, rz = p.az + z;
            float len = (float) Math.sqrt(rx * rx + ry0 * ry0 + rz * rz);
            float inv = len > 1e-4f ? 1f / len : 0f;
            float ry = len > 1e-4f ? ry0 * inv : 1f;
            rx *= inv;
            rz *= inv;
            int o = j * GRID_STRIDE;
            out[o] = x;
            out[o + 1] = z;
            if (mode == GRID_SKY) {
                CeilingSky.atmosphere(rx, ry, rz, SUN[0], SUN[1], SUN[2], w, RGB);
                out[o + 2] = RGB[0] * strength;
                out[o + 3] = RGB[1] * strength;
                out[o + 4] = RGB[2] * strength;
                out[o + 5] = 1f;
            } else if (mode == GRID_BAND) {
                float b = -sx * sky.bandSin + sz * sky.bandCos - sky.bandOffset;
                float g = CeilingSky.fieldBandGlow(b) * strength;
                out[o + 2] = BAND_COLOUR[0] * g;
                out[o + 3] = BAND_COLOUR[1] * g;
                out[o + 4] = BAND_COLOUR[2] * g;
                out[o + 5] = g;
            } else {
                CeilingSky.cloudTint(rx, ry, rz, SUN[0], SUN[1], SUN[2], w, high, RGB);
                out[o + 2] = RGB[0];
                out[o + 3] = RGB[1];
                out[o + 4] = RGB[2];
                out[o + 5] = strength;
            }
            out[o + 6] = sx / tileV + du;
            out[o + 7] = sz / tileV + dv;
        }
    }

    private static void gridVertex(VertexConsumer vc, Matrix4f m, Plane p, float drop, float[] row, int j) {
        int o = j * GRID_STRIDE;
        vertex(vc, m, p.ax + row[o], p.ay - drop, p.az + row[o + 1], row[o + 2], row[o + 3], row[o + 4], row[o + 5],
                row[o + 6], row[o + 7]);
    }

    /**
     * The stars and the Milky Way's crowd, on the star layer {@link CeilingSky#STAR_LAYER_H} up: the sky's repeating
     * star tile ({@link CeilingSky#starField}, in the band's frame) is laid over the part of the layer in view (the
     * rectangle scaled about the camera), each star drawn where its camera ray crosses the plane, as a camera-facing
     * dot of a fixed angular size in its own colour, twinkling gently. Dusk shows only the brightest
     * ({@link CeilingSky#starVisibility}). Very near the plane more of the layer is in view than the sky should
     * spend; the stars are then thinned evenly to {@link #STAR_CAP}.
     */
    private static void drawFieldStars(Sky sky, Plane p, float now, float strength, Matrix4f m,
                                       MultiBufferSource.BufferSource buffer) {
        float k = CeilingSky.parallaxScale(p.ay, CeilingSky.STAR_LAYER_H);
        float cx = -p.ax, cz = -p.az;
        float ca = sky.bandCos, sa = sky.bandSin, off = sky.bandOffset;
        // The window of the star layer in view, as a box in the band's frame.
        float amin = Float.MAX_VALUE, amax = -Float.MAX_VALUE, bmin = Float.MAX_VALUE, bmax = -Float.MAX_VALUE;
        for (int c = 0; c < 4; c++) {
            float px = (c & 1) == 0 ? -sky.hx : sky.hx, pz = (c & 2) == 0 ? -sky.hz : sky.hz;
            float sx = cx + (px - cx) * k, sz = cz + (pz - cz) * k;
            float a = sx * ca + sz * sa, b = -sx * sa + sz * ca - off;
            amin = Math.min(amin, a);
            amax = Math.max(amax, a);
            bmin = Math.min(bmin, b);
            bmax = Math.max(bmax, b);
        }
        float[] field = sky.field, tint = sky.fieldTint;
        int n = field.length / 3;
        float expected = CeilingSky.FIELD_DENSITY * 4f * sky.hx * sky.hz * k * k;
        // A fractional budget; the stars nearest its end fade (starBudgetFade) so it never pops them by index.
        float budget = expected > STAR_CAP ? n * STAR_CAP / expected : n + CeilingSky.STAR_BUDGET_FADE;
        int keep = Math.min(n, (int) Math.ceil(budget));
        float y = p.ay - STAR_DROP;
        int glows = 0;
        VertexConsumer dots = buffer.getBuffer(DOTS);
        for (int i = 0; i < keep; i++) {
            float size = field[i * 3 + 2];
            float vis = CeilingSky.starVisibility(strength, size);
            if (vis <= 0.01f) continue;
            float a0 = field[i * 3], b0 = field[i * 3 + 1];
            int ia0 = (int) Math.ceil((amin - a0) / CeilingSky.FIELD_ALONG);
            int ia1 = (int) Math.floor((amax - a0) / CeilingSky.FIELD_ALONG);
            int ib0 = (int) Math.ceil((bmin - b0) / CeilingSky.FIELD_ACROSS);
            int ib1 = (int) Math.floor((bmax - b0) / CeilingSky.FIELD_ACROSS);
            for (int ia = ia0; ia <= ia1; ia++) {
                for (int ib = ib0; ib <= ib1; ib++) {
                    float a = a0 + ia * CeilingSky.FIELD_ALONG, b = b0 + ib * CeilingSky.FIELD_ACROSS + off;
                    float sx = a * ca - b * sa, sz = a * sa + b * ca;
                    // Where its camera ray crosses the plane (CeilingSky.planePoint, inlined).
                    float px = cx + (sx - cx) / k, pz = cz + (sz - cz) / k;
                    if (Math.abs(px) > sky.hx || Math.abs(pz) > sky.hz) continue;
                    float x = p.ax + px, z = p.az + pz;
                    float dist = (float) Math.sqrt(x * x + y * y + z * z);
                    if (dist < 1e-3f) continue;
                    float bright = CeilingSky.realTwinkle(sky.seed, now, i, y / dist) * (0.40f + 0.60f * size) * vis;
                    float half = (STAR_ANGLE_MIN + STAR_ANGLE_GROW * size) * dist;
                    int t = i * 3;
                    billboard(dots, m, x, y, z, dist, half, tint[t] * bright, tint[t + 1] * bright,
                            tint[t + 2] * bright, bright, 0f, 0f, 1f, 1f);
                    if (size >= GLOW_FROM && glows < MAX_GLOWS) {
                        int g = glows++ * GLOW_STRIDE;
                        GLOW_LIST[g] = x;
                        GLOW_LIST[g + 1] = y;
                        GLOW_LIST[g + 2] = z;
                        GLOW_LIST[g + 3] = dist;
                        GLOW_LIST[g + 4] = size;
                        GLOW_LIST[g + 5] = bright;
                        GLOW_LIST[g + 6] = i;
                    }
                }
            }
        }
        buffer.endBatch(DOTS);
        if (glows == 0) return;
        VertexConsumer vc = buffer.getBuffer(GLOWS);
        for (int g = 0; g < glows; g++) {
            int o = g * GLOW_STRIDE;
            float size = GLOW_LIST[o + 4], bright = GLOW_LIST[o + 5], dist = GLOW_LIST[o + 3];
            float c = GLOW_ALPHA * bright * bright * size;
            int t = (int) GLOW_LIST[o + 6] * 3;
            billboard(vc, m, GLOW_LIST[o], GLOW_LIST[o + 1] - 0.002f, GLOW_LIST[o + 2], dist,
                    STAR_GLOW_ANGLE * (0.6f + 0.4f * size) * dist, tint[t] * c, tint[t + 1] * c, tint[t + 2] * c, c,
                    0f, 0f, 1f, 1f);
        }
        buffer.endBatch(GLOWS);
    }

    /**
     * The sun and the moon at their true places in the sky ({@link CeilingSky#sunDirection} from the level's time of
     * day; the moon opposite, in its phase from vanilla's moon texture), each drawn only where the camera's ray toward
     * it crosses the plane inside the rectangle ({@link CeilingSky#planeHit}), fading out near the edge. The sun is a
     * bright disc in a wide soft glow, gold low down and white high; the moon a cool disc and glow, pale by day. Rain
     * hides both and thunder too.
     */
    private static void drawSunAndMoon(Sky sky, Plane p, float[] w, int moonPhase, Matrix4f m,
                                       MultiBufferSource.BufferSource buffer) {
        float clear = Math.max(0f, 1f - SUN_OVERCAST_HIDE * w[3]) * (1f - w[4]);
        if (clear <= 0.01f) return;
        float cx = -p.ax, cz = -p.az;
        if (CeilingSky.planeHit(cx, cz, p.ay, SUN[0], SUN[1], SUN[2], sky.hx, sky.hz, HIT)) {
            float fade = clear * CeilingSky.insideFade(HIT[0], HIT[1], sky.hx, sky.hz, 2f + 0.1f * HIT[2]);
            if (fade > 0.01f) {
                float d = HIT[2] * BILLBOARD_PULL;
                float x = SUN[0] * d, y = SUN[1] * d, z = SUN[2] * d;
                CeilingSky.sunColour(SUN[1], RGB);
                float g = SUN_GLOW_STRENGTH * fade;
                billboard(buffer.getBuffer(DOTS), m, x, y, z, d, SUN_GLOW * d, RGB[0] * g, RGB[1] * g, RGB[2] * g, g,
                        0f, 0f, 1f, 1f);
                buffer.endBatch(DOTS);
                billboard(buffer.getBuffer(SUN_DISC), m, x, y, z, d, SUN_SIZE * d, RGB[0] * fade, RGB[1] * fade,
                        RGB[2] * fade, fade, 0f, 0f, 1f, 1f);
                buffer.endBatch(SUN_DISC);
            }
        }
        float mx = -SUN[0], my = -SUN[1], mz = -SUN[2];
        if (CeilingSky.planeHit(cx, cz, p.ay, mx, my, mz, sky.hx, sky.hz, HIT)) {
            float fade = clear * (1f - MOON_DAY_DIM * w[0])
                    * CeilingSky.insideFade(HIT[0], HIT[1], sky.hx, sky.hz, 2f + 0.1f * HIT[2]);
            if (fade > 0.01f) {
                float d = HIT[2] * BILLBOARD_PULL;
                float x = mx * d, y = my * d, z = mz * d;
                int phase = Math.floorMod(moonPhase, 8);
                float lit = 1f - Math.min(phase, 8 - phase) / 4f;     // 1 at full (phase 0), 0 at new (phase 4)
                float g = MOON_GLOW_STRENGTH * fade * (0.2f + 0.8f * lit);
                billboard(buffer.getBuffer(DOTS), m, x, y, z, d, MOON_GLOW * d, 0.62f * g, 0.72f * g, g, g,
                        0f, 0f, 1f, 1f);
                buffer.endBatch(DOTS);
                // Vanilla's sheet: four phases a row, two rows; laid out across the billboard as vanilla lays it.
                int col = phase % 4, row = phase / 4;
                billboard(buffer.getBuffer(MOON), m, x, y, z, d, MOON_SIZE * d, 0.92f * fade, 0.95f * fade, fade, fade,
                        (col + 1) / 4f, row / 2f, col / 4f, (row + 1) / 2f);
                buffer.endBatch(MOON);
            }
        }
    }

    /**
     * Real cloud under the roof: when the roof leaves room ({@link CeilingSky#volumeHeights}), a few translucent
     * slices between the plane and the roof, highest first (the camera is under them), each a grid of the storm's 3D
     * {@link CeilingSky#density} field drifting slowly, with fair-weather gaps ({@link CeilingSky#volumeAlpha}), lit
     * from the sun by day and the moon by night ({@link CeilingSky#cloudLight}: brighter higher up and on the side the
     * light comes from) and coloured by the hour ({@link CeilingSky#volumeCloudColour}). They thin out where the roof
     * slopes down toward the walls ({@link CeilingSky#volumeFade}), so no slice ever shows the line where it meets it.
     */
    private static void drawRealVolume(Sky sky, Plane p, float now, float drift, float[] w, Matrix4f m,
                                       MultiBufferSource.BufferSource buffer) {
        float[] heights = sky.volume;
        if (heights.length == 0) return;
        // The light comes from the sun, or the moon when the sun is down, and never quite from the side.
        float lx = SUN[0], ly = SUN[1], lz = SUN[2];
        if (ly < 0f) {
            lx = -lx;
            ly = -ly;
            lz = -lz;
        }
        ly = Math.max(ly, 0.25f);
        float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= ll;
        ly /= ll;
        lz /= ll;
        float nightThin = 1f - (1f - VOLUME_NIGHT) * w[2];
        for (int s = heights.length - 1; s >= 0; s--) {
            realSlice(sky, p, heights[s], s, now, drift, w, lx, ly, lz, nightThin, m, buffer);
        }
    }

    private static void realSlice(Sky sky, Plane p, float h, int index, float now, float drift, float[] w, float lx,
                                  float ly, float lz, float nightThin, Matrix4f m,
                                  MultiBufferSource.BufferSource buffer) {
        // The deck's fair-weather texture rather than the storm's (whose thick cores are charcoal): the density field
        // places the masses, the texture gives them their lumpy edges; each slice samples its own part of it.
        RenderType type = DECK;
        int nx = Math.max(4, Math.min(VOLUME_CELLS_MAX, (int) Math.ceil(2f * sky.hx / VOLUME_CELL)));
        int nz = Math.max(4, Math.min(VOLUME_CELLS_MAX, (int) Math.ceil(2f * sky.hz / VOLUME_CELL)));
        float tile = VOLUME_TILE + h * 1.2f;
        float du = h * 0.031f + index * 0.37f + drift * (0.5f + 0.03f * h);
        float dv = h * 0.017f + index * 0.21f + drift * 0.2f;
        VertexConsumer vc = buffer.getBuffer(type);
        float[] row0 = ROW_A, row1 = ROW_B;
        volumeRow(sky, h, nx, nz, 0, now, w, lx, ly, lz, nightThin, tile, du, dv, row0);
        for (int i = 0; i < nx; i++) {
            volumeRow(sky, h, nx, nz, i + 1, now, w, lx, ly, lz, nightThin, tile, du, dv, row1);
            for (int j = 0; j < nz; j++) {
                volumeVertex(vc, m, p, h, row0, j);
                volumeVertex(vc, m, p, h, row1, j);
                volumeVertex(vc, m, p, h, row1, j + 1);
                volumeVertex(vc, m, p, h, row0, j + 1);
            }
            float[] t = row0;
            row0 = row1;
            row1 = t;
        }
        buffer.endBatch(type);
    }

    /** One grid row of a real cloud slice into {@code out}, {@link #GRID_STRIDE} floats per vertex. */
    private static void volumeRow(Sky sky, float h, int nx, int nz, int i, float now, float[] w, float lx, float ly,
                                  float lz, float nightThin, float tile, float du, float dv, float[] out) {
        float u = i / (float) nx * 2f - 1f;
        float x = u * sky.hx;
        float heightFrac = h / sky.hy;
        for (int j = 0; j <= nz; j++) {
            float v = j / (float) nz * 2f - 1f;
            float z = v * sky.hz;
            float fade = CeilingSky.volumeFade(h, sky.hy, u, v, sky.hx, sky.hz);
            float a = 0f, light = 0.5f;
            if (fade > 0f) {
                float dens = CeilingSky.density(sky.seed, x, h, z, now);
                a = CeilingSky.volumeAlpha(dens, heightFrac, w[3]) * fade * nightThin;
                if (a > 0.001f) {
                    float toward = CeilingSky.density(sky.seed, x + lx * LIGHT_STEP, h + ly * LIGHT_STEP,
                            z + lz * LIGHT_STEP, now);
                    light = CeilingSky.cloudLight(dens, toward, heightFrac);
                }
            }
            CeilingSky.volumeCloudColour(light, w, RGB);
            int o = j * GRID_STRIDE;
            out[o] = x;
            out[o + 1] = z;
            out[o + 2] = RGB[0];
            out[o + 3] = RGB[1];
            out[o + 4] = RGB[2];
            out[o + 5] = a;
            out[o + 6] = x / tile + du;
            out[o + 7] = z / tile + dv;
        }
    }

    private static void volumeVertex(VertexConsumer vc, Matrix4f m, Plane p, float h, float[] row, int j) {
        int o = j * GRID_STRIDE;
        vertex(vc, m, p.ax + row[o], p.ay + h, p.az + row[o + 1], row[o + 2], row[o + 3], row[o + 4], row[o + 5],
                row[o + 6], row[o + 7]);
    }

    /**
     * A square of half-size {@code half} centred on the camera-relative point (x, y, z), {@code dist} from the camera,
     * turned square-on to the camera's ray to it (so it stays round wherever it is on the plane), the texture's
     * {@code (u0, v0)..(u1, v1)} across it: u along the horizontal axis, v along the other.
     */
    private static void billboard(VertexConsumer vc, Matrix4f m, float x, float y, float z, float dist, float half,
                                  float r, float g, float b, float a, float u0, float v0, float u1, float v1) {
        float dx = x / dist, dy = y / dist, dz = z / dist;
        float hl = (float) Math.sqrt(dx * dx + dz * dz);
        float e1x = 1f, e1z = 0f;                              // straight up or down: any horizontal axis will do
        if (hl > 1e-4f) {
            e1x = -dz / hl;
            e1z = dx / hl;
        }
        // e2 = d x e1 (e1 has no y)
        float e2x = dy * e1z, e2y = dz * e1x - dx * e1z, e2z = -dy * e1x;
        float ax = e1x * half, az = e1z * half;
        float bx = e2x * half, by = e2y * half, bz = e2z * half;
        vertex(vc, m, x - ax - bx, y - by, z - az - bz, r, g, b, a, u0, v0);
        vertex(vc, m, x + ax - bx, y - by, z + az - bz, r, g, b, a, u1, v0);
        vertex(vc, m, x + ax + bx, y + by, z + az + bz, r, g, b, a, u1, v1);
        vertex(vc, m, x - ax + bx, y + by, z - az + bz, r, g, b, a, u0, v1);
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
