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
import net.minecraft.client.CloudStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
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
 *   <li><b>real</b> ({@link CeilingSky#REAL}): a window onto the world's own vanilla sky, read from the client level
 *       when it draws. Each vertex of a grid on the plane is coloured the way vanilla colours the sky along the camera
 *       ray through it (its sky colour fogged toward the horizon as vanilla's sky disc is, the sunrise / sunset fan at
 *       dawn and dusk, rain and thunder darkening it); vanilla's sun and moon and its own star field (seed 10842) sit at
 *       their true places, each projected onto the plane and cut to the rectangle; and vanilla's flat cloud layer
 *       shows where each ray reaches the cloud height, drifting with vanilla's clouds. A light magic on top: a soft
 *       glow round the sun and moon, gently twinkling stars (a few faintly coloured), a shooting star on clear nights,
 *       a few gold motes drifting just under the plane, and in a thunderstorm the ceiling's lightning flickers.</li>
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
    /**
     * The real sky, a window onto vanilla's: plain white under the per-vertex sky colour, vanilla's own stars (additive
     * white), sun and moon (additive, crisp like vanilla's) and clouds (straight alpha, crisp, so they stay blocky).
     */
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final RenderType WINDOW = ModRenderTypes.ceilingLayer(WHITE);
    private static final RenderType STARS = ModRenderTypes.ceilingGlow(WHITE, false);
    private static final RenderType VANILLA_SUN = ModRenderTypes.ceilingGlow(
            ResourceLocation.withDefaultNamespace("textures/environment/sun.png"), false);
    private static final RenderType VANILLA_MOON = ModRenderTypes.ceilingGlow(
            ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png"), false);
    private static final RenderType VANILLA_CLOUDS = ModRenderTypes.ceilingLayer(
            ResourceLocation.withDefaultNamespace("textures/environment/clouds.png"), false);

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
        /** The world's own sky ({@link CeilingSky#REAL}): a window onto vanilla's, read from the level at draw time. */
        final boolean real;
        final double x, y, z;
        final float hx, hz;
        /** Blocks of clear space above the plane before the real roof, as the effect gives it (0: none). */
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
            // The flat star field is the night and storm styles' (the real sky draws vanilla's own stars).
            this.stars = real ? new float[0][] : CeilingSky.stars(seed, hx, hz, CeilingSky.starCount(hx, hz));
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
            if (sky.real) drawReal(sky, p, now, local, cam, m, buffer);
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

    // ------------------------------------------------------------------ the real sky: a window onto vanilla's

    /** The window's grid: a vertex every this many blocks, and at least this many cells a side (small rooms). */
    private static final float REAL_CELL = 2f;
    private static final int REAL_MIN_CELLS = 8;
    /** What a window grid works out at each vertex. Int constants, not an enum: no switch in render code. */
    private static final int GRID_SKY = 0, GRID_CLOUD = 1;
    /** Floats per grid vertex in a row buffer: x, z, r, g, b, a, u, v. */
    private static final int GRID_STRIDE = 8;
    /** Each layer's drop below the plane, far to near: the sun and moon, the stars, the clouds. */
    private static final float BODY_DROP = 0.008f, STARS_DROP = 0.016f, CLOUDS_DROP = 0.03f;
    /** Each sun, moon and glow quad is cut into this many cells a side, every corner projected exactly. */
    private static final int BODY_CELLS = 6;
    /** Past this thunder level the window's sky flickers with the ceiling's lightning, vanilla's flash colour. */
    private static final float STORM_FROM = 0.5f, FLASH_MIX = 0.45f;
    private static final float[] FLASH_COLOUR = {0.8f, 0.8f, 1.0f};
    /** The magic glows, premultiplied for additive blending: soft gold round the sun, pale violet round the moon. */
    private static final float[] SUN_GLOW = {0.34f, 0.25f, 0.10f}, MOON_GLOW = {0.13f, 0.11f, 0.22f};
    /** Sparkle motes: billboard half-size in blocks and their pale gold at full glow (additive). */
    private static final float MOTE_HALF = 0.045f;
    private static final float[] MOTE_COLOUR = {0.42f, 0.35f, 0.18f};
    /** Shooting stars cross only a dark, clear sky: vanilla's star brightness (0.5 at midnight) times the clear. */
    private static final float SHOOTING_FROM = 0.3f;

    /** The sky this frame, read once from the level for every window drawn. Render thread only. */
    private static final class Frame {
        boolean normal;
        float skyR, skyG, skyB;
        float fogR, fogG, fogB, fogA;
        float skyFogEnd, fogStart, fogEnd;
        boolean sunrise;
        float sunriseR, sunriseG, sunriseB, sunriseA;
        float flash;
        float cloudR, cloudG, cloudB;
        /** The cloud layer's height above the camera (NaN: no clouds). */
        float cloudAbove;
    }

    private static final Frame FRAME = new Frame();
    // Scratch for the window. Render thread only, one sky at a time, so sharing it is safe and nothing is allocated
    // per vertex, per star or per cell.
    private static final float[] VEC = new float[3], PT = new float[2], TEX = new float[2], CLOUD_BASE = new float[2];
    private static final float[] FAN = new float[CeilingSky.FAN_FLOATS], MOTE = new float[4], TINT = new float[3];
    private static final float[] STAR_PTS = new float[8];
    private static final float[] ROW_A = new float[(MAX_CELLS + 1) * GRID_STRIDE];
    private static final float[] ROW_B = new float[(MAX_CELLS + 1) * GRID_STRIDE];
    /** One projected sky quad: per grid corner x, z, u, v and whether its ray reached the plane. */
    private static final float[] BODY = new float[(BODY_CELLS + 1) * (BODY_CELLS + 1) * 5];
    /** Polygon clipping buffers: up to 12 vertices of x, z, u, v. */
    private static final float[] CLIP_A = new float[48], CLIP_B = new float[48];
    /** Vanilla's star field, built on first use (it never changes). */
    private static float[] vanillaStars;

    private static float[] vanillaStars() {
        if (vanillaStars == null) vanillaStars = CeilingSky.vanillaStars();
        return vanillaStars;
    }

    /**
     * The world's own sky through the ceiling, as a window onto vanilla's: every vertex of a grid on the plane takes
     * the colour vanilla gives the sky along the camera's ray through it (the sky colour, fogged toward the horizon the
     * way vanilla's sky disc is, with the sunrise / sunset fan blended in at dawn and dusk; rain and thunder darken it
     * as {@code getSkyColor} does); vanilla's sun and moon (its textures, phase and size) at their true places, each
     * projected onto the plane and cut to the rectangle; vanilla's own star field (seed 10842) turning with the sky;
     * and vanilla's flat cloud layer where the ray through each point meets the cloud height, drifting as vanilla's
     * does. The magic is light: a soft glow round the sun and moon, gently twinkling stars (a few faintly coloured), a
     * shooting star on clear nights and a few gold motes drifting just under the plane. In a thunderstorm the sky
     * flickers with the ceiling's lightning. Read at draw time, so every viewer sees the same sky.
     */
    private static void drawReal(Sky sky, Plane p, float now, float local, Vec3 cam, Matrix4f m,
                                 MultiBufferSource.BufferSource buffer) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float partial = PensieveClientState.partial();
        Frame f = FRAME;
        readFrame(f, mc, level, cam, partial, sky, local, p.reveal);
        windowGrid(buffer, WINDOW, m, p, 0f, GRID_SKY, f);
        if (!(p.ay > CLOUDS_DROP) || !f.normal) {
            drawMotes(sky, p, now, m, buffer);
            return;
        }
        float sunAngle = level.getSunAngle(partial);
        float cosA = (float) Math.cos(sunAngle), sinA = (float) Math.sin(sunAngle);
        float clear = 1f - Mth.clamp(level.getRainLevel(partial), 0f, 1f);
        drawBodies(sky, p, level.getMoonPhase(), cosA, sinA, clear, m, buffer);
        float starLight = Mth.clamp(level.getStarBrightness(partial), 0f, 1f) * clear;
        if (starLight > 0f) drawVanillaStars(sky, p, now, cosA, sinA, starLight, m, buffer);
        float[] shot = CeilingSky.shootingStar(sky.seed, now, sky.hx, sky.hz);
        if (shot != null && p.reveal >= 1f && starLight > SHOOTING_FROM) {
            drawShootingStar(shot, p.ax, p.ay - STARS_DROP, p.az, m, buffer);
        }
        if (Float.isFinite(f.cloudAbove) && f.cloudAbove > p.ay - CLOUDS_DROP) {
            // Vanilla's clouds drift on the renderer's own tick counter, so these keep pace with the ones outdoors.
            float ticks = (float) mc.levelRenderer.getTicks() + partial;
            CeilingSky.cloudBase(cam.x, cam.z, ticks, CLOUD_BASE);
            windowGrid(buffer, VANILLA_CLOUDS, m, p, CLOUDS_DROP, GRID_CLOUD, f);
        }
        drawMotes(sky, p, now, m, buffer);
    }

    /** Everything the window needs from the level this frame, into {@code f}, made safe (finite, in range). */
    private static void readFrame(Frame f, Minecraft mc, ClientLevel level, Vec3 cam, float partial, Sky sky,
                                  float local, float reveal) {
        f.normal = level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL;
        float[] fog = RenderSystem.getShaderFogColor();
        f.fogR = safe(fog.length > 0 ? fog[0] : 0f);
        f.fogG = safe(fog.length > 1 ? fog[1] : 0f);
        f.fogB = safe(fog.length > 2 ? fog[2] : 0f);
        f.fogA = fog.length > 3 ? safe(fog[3]) : 1f;
        f.skyFogEnd = CeilingSky.skyFogEnd(mc.gameRenderer.getRenderDistance());
        f.fogStart = RenderSystem.getShaderFogStart();
        f.fogEnd = RenderSystem.getShaderFogEnd();
        if (f.normal) {
            Vec3 c = level.getSkyColor(cam, partial);
            f.skyR = safe((float) c.x);
            f.skyG = safe((float) c.y);
            f.skyB = safe((float) c.z);
        } else {
            // Vanilla draws no sky disc here: what shows is the clear colour, the fog's.
            f.skyR = f.fogR;
            f.skyG = f.fogG;
            f.skyB = f.fogB;
        }
        float[] sr = f.normal ? level.effects().getSunriseColor(level.getTimeOfDay(partial), partial) : null;
        f.sunrise = sr != null && sr.length >= 4;
        if (f.sunrise) {
            f.sunriseR = safe(sr[0]);
            f.sunriseG = safe(sr[1]);
            f.sunriseB = safe(sr[2]);
            f.sunriseA = safe(sr[3]);
            CeilingSky.sunriseFan(!(Mth.sin(level.getSunAngle(partial)) < 0f), f.sunriseA, FAN);
        }
        f.flash = 0f;
        if (reveal >= 1f && level.getThunderLevel(partial) > STORM_FROM) {
            float[] flash = CeilingSky.flash(sky.seed, local);
            if (flash != null) f.flash = FLASH_MIX * safe(flash[2]);
        }
        // Seen from below the cloud shows its underside: Fancy shades it as vanilla's buildClouds does, Fast not.
        CloudStatus clouds = mc.options.getCloudsType();
        float shade = CeilingSky.cloudUnderside(clouds == CloudStatus.FANCY);
        Vec3 cloud = level.getCloudColor(partial);
        f.cloudR = safe((float) cloud.x) * shade;
        f.cloudG = safe((float) cloud.y) * shade;
        f.cloudB = safe((float) cloud.z) * shade;
        float height = level.effects().getCloudHeight();
        f.cloudAbove = clouds == CloudStatus.OFF || !Float.isFinite(height) ? Float.NaN
                : (float) (height + CeilingSky.CLOUD_LIFT - cam.y);
    }

    /** {@code v} clamped to 0..1, NaN to 0. */
    private static float safe(float v) {
        return v > 0f ? (v < 1f ? v : 1f) : 0f;
    }

    private static int windowCells(float half) {
        return Math.max(REAL_MIN_CELLS, Math.min(MAX_CELLS, (int) Math.ceil(2f * half / REAL_CELL)));
    }

    /**
     * One window layer on the plane, {@code drop} below it, as its own batch: a grid whose vertices are each worked out
     * once (rows streamed through two scratch buffers). {@code mode} picks what: the sky's colour along each vertex's
     * ray, or vanilla's cloud layer where that ray reaches it (the mapping is a uniform scale about the camera, so the
     * texture lies straight on the plane; the grid only carries the fog).
     */
    private static void windowGrid(MultiBufferSource.BufferSource buffer, RenderType type, Matrix4f m, Plane p,
                                   float drop, int mode, Frame f) {
        Sky sky = p.sky;
        int nx = windowCells(sky.hx), nz = windowCells(sky.hz);
        VertexConsumer vc = buffer.getBuffer(type);
        float[] row0 = ROW_A, row1 = ROW_B;
        windowRow(p, nx, nz, 0, drop, mode, f, row0);
        for (int i = 0; i < nx; i++) {
            windowRow(p, nx, nz, i + 1, drop, mode, f, row1);
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

    /** Grid row {@code i} of a window layer into {@code out}, {@link #GRID_STRIDE} floats per vertex. */
    private static void windowRow(Plane p, int nx, int nz, int i, float drop, int mode, Frame f, float[] out) {
        Sky sky = p.sky;
        float x = (i / (float) nx * 2f - 1f) * sky.hx;
        float ay = p.ay - drop;
        for (int j = 0; j <= nz; j++) {
            float z = (j / (float) nz * 2f - 1f) * sky.hz;
            float rx = p.ax + x, rz = p.az + z;                   // camera-relative
            int o = j * GRID_STRIDE;
            out[o] = x;
            out[o + 1] = z;
            if (mode == GRID_SKY) {
                float len = (float) Math.sqrt(rx * rx + ay * ay + rz * rz);
                float inv = len > 1e-4f ? 1f / len : 0f;
                float dx = rx * inv, dy = len > 1e-4f ? Math.abs(ay) * inv : 1f, dz = rz * inv;
                float fog = CeilingSky.skyFog(dy, (float) Math.sqrt(dx * dx + dz * dz), f.skyFogEnd) * f.fogA;
                float r = f.skyR + (f.fogR - f.skyR) * fog;
                float g = f.skyG + (f.fogG - f.skyG) * fog;
                float b = f.skyB + (f.fogB - f.skyB) * fog;
                if (f.sunrise) {
                    float a = CeilingSky.sunriseAlpha(FAN, f.sunriseA, dx, dy, dz);
                    r += (f.sunriseR - r) * a;
                    g += (f.sunriseG - g) * a;
                    b += (f.sunriseB - b) * a;
                }
                if (f.flash > 0f) {
                    r += (FLASH_COLOUR[0] - r) * f.flash;
                    g += (FLASH_COLOUR[1] - g) * f.flash;
                    b += (FLASH_COLOUR[2] - b) * f.flash;
                }
                out[o + 2] = r;
                out[o + 3] = g;
                out[o + 4] = b;
                out[o + 5] = 1f;
                out[o + 6] = 0.5f;
                out[o + 7] = 0.5f;
            } else {
                // Where this vertex's ray reaches the cloud layer, and vanilla's texel there.
                float h = f.cloudAbove - ay;
                float k = CeilingSky.parallaxScale(ay, h);
                float cx = rx * k, cz = rz * k;
                CeilingSky.cloudTexel(CLOUD_BASE, rx, rz, ay, h, TEX);
                float dist = Math.max((float) Math.sqrt(cx * cx + cz * cz), Math.abs(f.cloudAbove));
                float fog = CeilingSky.terrainFog(dist, f.fogStart, f.fogEnd) * f.fogA;
                boolean there = Math.abs(cx) <= CeilingSky.CLOUD_REACH && Math.abs(cz) <= CeilingSky.CLOUD_REACH;
                out[o + 2] = f.cloudR + (f.fogR - f.cloudR) * fog;
                out[o + 3] = f.cloudG + (f.fogG - f.cloudG) * fog;
                out[o + 4] = f.cloudB + (f.fogB - f.cloudB) * fog;
                out[o + 5] = there ? CeilingSky.CLOUD_ALPHA : 0f;
                out[o + 6] = TEX[0] / CeilingSky.CLOUD_TEXTURE;
                out[o + 7] = TEX[1] / CeilingSky.CLOUD_TEXTURE;
            }
        }
    }

    private static void gridVertex(VertexConsumer vc, Matrix4f m, Plane p, float drop, float[] row, int j) {
        int o = j * GRID_STRIDE;
        vertex(vc, m, p.ax + row[o], p.ay - drop, p.az + row[o + 1], row[o + 2], row[o + 3], row[o + 4], row[o + 5],
                row[o + 6], row[o + 7]);
    }

    /**
     * Vanilla's sun and moon, each with its magic glow: the sun's quad (half-size 30 at 100, sun.png) and the moon's
     * (20, its phase's cell of moon_phases.png, laid on as vanilla lays it), at the sky's rotation; rain fades them as it
     * fades vanilla's. Additive, like vanilla's.
     */
    private static void drawBodies(Sky sky, Plane p, int moonPhase, float cosA, float sinA, float clear, Matrix4f m,
                                   MultiBufferSource.BufferSource buffer) {
        if (clear <= 0.001f) return;
        float ay = p.ay - BODY_DROP;
        float d = CeilingSky.SKY_DISTANCE;
        VertexConsumer glow = buffer.getBuffer(DOTS);
        skyQuad(glow, m, p, ay, cosA, sinA, d, CeilingSky.SUN_GLOW_HALF, 0f, 1f, 0f, 1f,
                SUN_GLOW[0] * clear, SUN_GLOW[1] * clear, SUN_GLOW[2] * clear, clear);
        int phase = Math.floorMod(moonPhase, 8);
        float lit = 1f - Math.min(phase, 8 - phase) / 4f;            // 1 at full (phase 0), 0 at new (phase 4)
        float mg = clear * (0.3f + 0.7f * lit);
        skyQuad(glow, m, p, ay, cosA, sinA, -d, CeilingSky.MOON_GLOW_HALF, 0f, 1f, 0f, 1f,
                MOON_GLOW[0] * mg, MOON_GLOW[1] * mg, MOON_GLOW[2] * mg, mg);
        buffer.endBatch(DOTS);
        skyQuad(buffer.getBuffer(VANILLA_SUN), m, p, ay, cosA, sinA, d, CeilingSky.SUN_HALF, 0f, 1f, 0f, 1f,
                clear, clear, clear, clear);
        buffer.endBatch(VANILLA_SUN);
        int col = phase % 4, row = phase / 4 % 2;
        float u0 = col / 4f, v0 = row / 2f, u1 = (col + 1) / 4f, v1 = (row + 1) / 2f;
        // Vanilla's moon: x from -20 to 20 runs u from u1 to u0, z from -20 to 20 runs v from v0 to v1.
        skyQuad(buffer.getBuffer(VANILLA_MOON), m, p, ay, cosA, sinA, -d, CeilingSky.MOON_HALF, u1, u0, v0, v1,
                clear, clear, clear, clear);
        buffer.endBatch(VANILLA_MOON);
    }

    /**
     * A square of the sky's own frame at {@code y = ly}, half-size {@code half} in x and z, turned with the sky and
     * projected from the camera onto the plane {@code ay} above it, cut to the ceiling's rectangle: it is split into
     * {@link #BODY_CELLS} cells a side, each corner projected exactly (so the texture follows the true projection), and
     * the cells crossing the rectangle's edge clipped to it. u runs {@code us0..us1} along x, v {@code vt0..vt1} along z.
     */
    private static void skyQuad(VertexConsumer vc, Matrix4f m, Plane p, float ay, float cosA, float sinA, float ly,
                                float half, float us0, float us1, float vt0, float vt1, float r, float g, float b,
                                float a) {
        int n = BODY_CELLS, w = n + 1;
        for (int i = 0; i <= n; i++) {
            float s = i / (float) n;
            for (int j = 0; j <= n; j++) {
                float t = j / (float) n;
                CeilingSky.skyToWorld(cosA, sinA, -half + 2f * half * s, ly, -half + 2f * half * t, VEC, 0);
                int o = (i * w + j) * 5;
                boolean ok = CeilingSky.rayToPlane(VEC[0], VEC[1], VEC[2], ay, PT);
                BODY[o] = ok ? PT[0] : 0f;
                BODY[o + 1] = ok ? PT[1] : 0f;
                BODY[o + 2] = us0 + (us1 - us0) * s;
                BODY[o + 3] = vt0 + (vt1 - vt0) * t;
                BODY[o + 4] = ok ? 1f : 0f;
            }
        }
        float x0 = p.ax - p.sky.hx, x1 = p.ax + p.sky.hx, z0 = p.az - p.sky.hz, z1 = p.az + p.sky.hz;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int c0 = (i * w + j) * 5, c1 = ((i + 1) * w + j) * 5, c2 = ((i + 1) * w + j + 1) * 5,
                        c3 = (i * w + j + 1) * 5;
                if (BODY[c0 + 4] < 0.5f || BODY[c1 + 4] < 0.5f || BODY[c2 + 4] < 0.5f || BODY[c3 + 4] < 0.5f) continue;
                float minX = Math.min(Math.min(BODY[c0], BODY[c1]), Math.min(BODY[c2], BODY[c3]));
                float maxX = Math.max(Math.max(BODY[c0], BODY[c1]), Math.max(BODY[c2], BODY[c3]));
                float minZ = Math.min(Math.min(BODY[c0 + 1], BODY[c1 + 1]), Math.min(BODY[c2 + 1], BODY[c3 + 1]));
                float maxZ = Math.max(Math.max(BODY[c0 + 1], BODY[c1 + 1]), Math.max(BODY[c2 + 1], BODY[c3 + 1]));
                if (maxX < x0 || minX > x1 || maxZ < z0 || minZ > z1) continue;     // wholly outside
                int count = 4;
                loadCorner(c0, 0);
                loadCorner(c1, 1);
                loadCorner(c2, 2);
                loadCorner(c3, 3);
                if (minX < x0 || maxX > x1 || minZ < z0 || maxZ > z1) {
                    count = clipEdge(CLIP_A, CLIP_B, count, 0, x0, 1f);
                    count = clipEdge(CLIP_B, CLIP_A, count, 0, x1, -1f);
                    count = clipEdge(CLIP_A, CLIP_B, count, 1, z0, 1f);
                    count = clipEdge(CLIP_B, CLIP_A, count, 1, z1, -1f);
                }
                if (count == 4) {
                    for (int k = 0; k < 4; k++) clipVertex(vc, m, ay, k, r, g, b, a);
                    continue;
                }
                // Any other convex polygon as a fan of triangles, each a quad with its last corner doubled.
                for (int k = 1; k + 1 < count; k++) {
                    clipVertex(vc, m, ay, 0, r, g, b, a);
                    clipVertex(vc, m, ay, k, r, g, b, a);
                    clipVertex(vc, m, ay, k + 1, r, g, b, a);
                    clipVertex(vc, m, ay, k + 1, r, g, b, a);
                }
            }
        }
    }

    private static void loadCorner(int c, int at) {
        int o = at * 4;
        CLIP_A[o] = BODY[c];
        CLIP_A[o + 1] = BODY[c + 1];
        CLIP_A[o + 2] = BODY[c + 2];
        CLIP_A[o + 3] = BODY[c + 3];
    }

    private static void clipVertex(VertexConsumer vc, Matrix4f m, float ay, int k, float r, float g, float b,
                                   float a) {
        int o = k * 4;
        vertex(vc, m, CLIP_A[o], ay, CLIP_A[o + 1], r, g, b, a, CLIP_A[o + 2], CLIP_A[o + 3]);
    }

    /**
     * One Sutherland-Hodgman step: the polygon {@code src} ({@code count} vertices of x, z, u, v) cut to the side of
     * {@code coord (0 x, 1 z) = bound} where {@code sign * (coord - bound) >= 0}, into {@code dst}; the new count.
     */
    private static int clipEdge(float[] src, float[] dst, int count, int coord, float bound, float sign) {
        int out = 0;
        int max = dst.length / 4;
        for (int i = 0; i < count; i++) {
            int a = i * 4, b = ((i + 1) % count) * 4;
            float da = sign * (src[a + coord] - bound), db = sign * (src[b + coord] - bound);
            if (da >= 0f && out < max) {
                System.arraycopy(src, a, dst, out * 4, 4);
                out++;
            }
            if ((da >= 0f) != (db >= 0f) && out < max) {
                float t = da / (da - db);
                int o = out * 4;
                for (int c = 0; c < 4; c++) dst[o + c] = src[a + c] + (src[b + c] - src[a + c]) * t;
                out++;
            }
        }
        return out;
    }

    /**
     * Vanilla's stars ({@link CeilingSky#vanillaStars}) turned with the sky, each drawn where its four corners' rays
     * cross the plane, wholly inside the rectangle or not at all, at vanilla's brightness ({@code starLight}, the star
     * brightness times the rain's clear, added squared as vanilla's blend does), twinkling gently and, a few, faintly
     * coloured.
     */
    private static void drawVanillaStars(Sky sky, Plane p, float now, float cosA, float sinA, float starLight,
                                         Matrix4f m, MultiBufferSource.BufferSource buffer) {
        float[] stars = vanillaStars();
        float ay = p.ay - STARS_DROP;
        float x0 = p.ax - sky.hx, x1 = p.ax + sky.hx, z0 = p.az - sky.hz, z1 = p.az + sky.hz;
        float base = starLight * starLight;
        VertexConsumer vc = buffer.getBuffer(STARS);
        int n = stars.length / CeilingSky.STAR_STRIDE;
        for (int s = 0; s < n; s++) {
            int o = s * CeilingSky.STAR_STRIDE;
            boolean in = true;
            for (int c = 0; c < 4 && in; c++) {
                CeilingSky.skyToWorld(cosA, sinA, stars[o + c * 3], stars[o + c * 3 + 1], stars[o + c * 3 + 2], VEC, 0);
                if (!CeilingSky.rayToPlane(VEC[0], VEC[1], VEC[2], ay, PT)
                        || PT[0] < x0 || PT[0] > x1 || PT[1] < z0 || PT[1] > z1) {
                    in = false;
                } else {
                    STAR_PTS[c * 2] = PT[0];
                    STAR_PTS[c * 2 + 1] = PT[1];
                }
            }
            if (!in) continue;
            float bright = base * CeilingSky.starTwinkle(s, now);
            CeilingSky.starTint(s, TINT);
            for (int c = 0; c < 4; c++) {
                vertex(vc, m, STAR_PTS[c * 2], ay, STAR_PTS[c * 2 + 1], TINT[0] * bright, TINT[1] * bright,
                        TINT[2] * bright, bright, 0.5f, 0.5f);
            }
        }
        buffer.endBatch(STARS);
    }

    /** A few faint gold motes drifting just under the plane ({@link CeilingSky#mote}), sparkling softly. */
    private static void drawMotes(Sky sky, Plane p, float now, Matrix4f m, MultiBufferSource.BufferSource buffer) {
        if (p.reveal <= 0f) return;
        int n = CeilingSky.moteCount(sky.hx, sky.hz);
        VertexConsumer vc = buffer.getBuffer(DOTS);
        for (int i = 0; i < n; i++) {
            CeilingSky.mote(sky.seed, i, now, sky.hx, sky.hz, MOTE);
            float glow = MOTE[3] * p.reveal * (0.75f + 0.25f * (float) Math.sin(now * 0.31f + i * 1.7f));
            if (glow <= 0.01f) continue;
            float x = p.ax + MOTE[0], y = p.ay - MOTE[2], z = p.az + MOTE[1];
            float dist = (float) Math.sqrt(x * x + y * y + z * z);
            if (dist < 0.05f) continue;
            billboard(vc, m, x, y, z, dist, MOTE_HALF, MOTE_COLOUR[0] * glow, MOTE_COLOUR[1] * glow,
                    MOTE_COLOUR[2] * glow, glow, 0f, 0f, 1f, 1f);
        }
        buffer.endBatch(DOTS);
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
