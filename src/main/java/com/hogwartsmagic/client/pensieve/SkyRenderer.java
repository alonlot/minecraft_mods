package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.pensieve.scene.SkyDome;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The {@code sky} effect: a daytime sky for a memory whose biome sky and fog are black. A dome centred on the camera,
 * its colour running from the style's horizon colour to its zenith ({@link SkyDome#colour}), with a few soft cloud
 * layers drifting under it. It is drawn at {@code AFTER_SKY}, before any terrain, entity or effect, writes no depth and
 * is not fogged, so everything else in the memory covers it; the radius ({@link SkyDome#domeRadius}) is beyond the
 * scene's farthest block and inside the far plane. While it shows, {@link #fogColour} lifts the fog (and the clear
 * colour) from black to the horizon colour, so distant blocks fade into haze, not into night, and the sky and the haze
 * meet without a seam.
 *
 * <p>Everything is a function of the shared scene clock and the effect seed (the cloud pattern's start), so a replay and
 * a late joiner see the same sky. At this stage the camera rotation is the event's view matrix and RenderSystem's
 * modelview is still the identity, so the event matrix is used as the pose (unlike the later stages, where it has
 * already been applied). State lives in {@link EffectVisuals}, so its {@code clear()} removes the sky with the scene.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class SkyRenderer {
    private static final RenderType CLOUDS_A = ModRenderTypes.ceilingLayer(tex("ceiling_clouds_a"));
    private static final RenderType CLOUDS_B = ModRenderTypes.ceilingLayer(tex("ceiling_clouds_b"));
    /** The storm's own hard-edged, billowing textures (build_scripts/generate_ceiling_textures.py, seeded). */
    private static final RenderType STORM_A = ModRenderTypes.ceilingLayer(tex("storm_clouds_a"));
    private static final RenderType STORM_B = ModRenderTypes.ceilingLayer(tex("storm_clouds_b"));
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;

    /** Dome rings, as elevations in radians from just under the horizon to the zenith, and segments round. */
    private static final float[] RINGS = {-0.30f, -0.08f, 0f, 0.07f, 0.17f, 0.30f, 0.46f, 0.65f, 0.88f, 1.12f, 1.34f,
            (float) (Math.PI / 2)};
    private static final int SEGMENTS = 36;
    /** The cloud grid cells along a side. */
    private static final int CLOUD_CELLS = 16, STORM_CELLS = 28;
    /** Fog pushed out this far while the sky draws, so the dome keeps its gradient. */
    private static final float NO_FOG_START = 1.0e6f, NO_FOG_END = 2.0e6f;

    private SkyRenderer() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/" + name + ".png");
    }

    /** One running sky: its look, when it begins, how long, and the seed that places its clouds. */
    static final class Sky {
        final String style;
        final float t;
        final int duration;
        final long seed;

        Sky(String style, float t, int duration, long seed) {
            this.style = SkyDome.style(style);
            this.t = t;
            this.duration = Math.max(0, duration);
            this.seed = seed;
        }
    }

    /** One cloud layer: texture, tint, strength, texture scale and drift speed relative to the others. */
    private record Layer(RenderType type, float r, float g, float b, float alpha, float scale, float speed, float height) {
    }

    private static Layer[] layers(String style) {
        if (SkyDome.STORM.equals(style)) {
            // A full deck down to the horizon: dark billowing undersides (darker than the dome), a second deck, and
            // lighter ragged scud under both that sweeps a little faster (SkyDome.STORM_LAYERS: scale, speed, height).
            float[][] l = SkyDome.STORM_LAYERS;
            return new Layer[] {
                    new Layer(STORM_A, 0.24f, 0.27f, 0.32f, 0.97f, l[0][0], l[0][1], l[0][2]),
                    new Layer(STORM_B, 0.20f, 0.23f, 0.28f, 0.88f, l[1][0], l[1][1], l[1][2]),
                    new Layer(STORM_B, 0.58f, 0.62f, 0.69f, 0.50f, l[2][0], l[2][1], l[2][2])};
        }
        if ("overcast".equals(style)) {
            return new Layer[] {
                    new Layer(CLOUDS_A, 0.86f, 0.88f, 0.90f, 1f, 1.0f, 1.0f, 0.22f),
                    new Layer(CLOUDS_B, 0.84f, 0.86f, 0.89f, 1f, 0.7f, 1.4f, 0.23f),
                    new Layer(CLOUDS_A, 0.82f, 0.84f, 0.87f, 0.85f, 1.7f, 0.6f, 0.24f)};
        }
        if ("dawn".equals(style)) {
            return new Layer[] {
                    new Layer(CLOUDS_A, 1.00f, 0.86f, 0.78f, 0.9f, 1.0f, 1.0f, 0.30f),
                    new Layer(CLOUDS_B, 1.00f, 0.90f, 0.84f, 0.55f, 0.7f, 1.4f, 0.31f)};
        }
        return new Layer[] {
                new Layer(CLOUDS_A, 1.00f, 1.00f, 1.00f, 1f, 1.0f, 1.0f, 0.30f),
                new Layer(CLOUDS_B, 0.98f, 0.99f, 1.00f, 0.6f, 0.7f, 1.4f, 0.31f)};
    }

    /** How much of the memory's sky the phase allows: all in the build and play, easing out over the exit, none after. */
    private static float phaseShare(float partial) {
        int p = PensieveClientState.phase();
        if (p == PensieveClientState.BUILD || p == PensieveClientState.PLAY) return 1f;
        if (p == PensieveClientState.EXIT && PensieveClientState.inMemory()) {
            float k = PensieveClientState.phaseTime(partial) / PensieveTimeline.EXIT_TICKS;
            return Math.max(0f, Math.min(1f, 1f - k));
        }
        return 0f;
    }

    private static float shown(Sky sky, float now, float partial) {
        return SkyDome.fade(now - sky.t, sky.duration, sky.t) * phaseShare(partial);
    }

    /** How much of a storm sky shows right now, 0 (none) to 1: the fog closes in with it, the rain sheets follow it. */
    static float stormShare(float partial) {
        List<Sky> skies = EffectVisuals.skies();
        if (skies.isEmpty() || !PensieveClientState.inMemory()) return 0f;
        float now = PensieveClientState.sceneTime(partial);
        float most = 0f;
        for (Sky s : skies) {
            if (SkyDome.STORM.equals(s.style)) most = Math.max(most, shown(s, now, partial));
        }
        return most;
    }

    /** The direction (world x, z, from the camera) of the latest lightning strike already begun, or null. */
    private static double[] strikeDir(Vec3 cam, float now) {
        double[] ts = EffectVisuals.strikes();
        for (int i = ts.length - 1; i >= 0; i--) {
            if (ts[i] <= now) {
                STRIKE_DIR[0] = EffectVisuals.strikeXs()[i] - cam.x;
                STRIKE_DIR[1] = EffectVisuals.strikeZs()[i] - cam.z;
                return STRIKE_DIR;
            }
        }
        return null;
    }

    /** Scratch for {@link #strikeDir}: the render thread only, read within the frame, so a flash allocates nothing. */
    private static final double[] STRIKE_DIR = new double[2];

    /**
     * The fog and clear colour the sky asks for right now, or null when no sky shows (the memory's own black stands).
     * Called by {@link PensieveVoid#fogColor}; with several skies the most visible one wins.
     */
    static float[] fogColour(float partial) {
        List<Sky> skies = EffectVisuals.skies();
        if (skies.isEmpty() || !PensieveClientState.inMemory()) return null;
        float now = PensieveClientState.sceneTime(partial);
        Sky best = null;
        float most = 0f;
        for (Sky s : skies) {
            float f = shown(s, now, partial);
            if (f > most) {
                most = f;
                best = s;
            }
        }
        if (best == null) return null;
        float[] fog = SkyDome.fogColour(best.style, most);
        // The fog lights up with the lightning, a little less than the sky, so the haze flashes with it.
        if (SkyDome.STORM.equals(best.style)) fog = SkyDome.flashed(fog, SkyDome.flash(now, EffectVisuals.strikes()) * 0.7 * most);
        return fog;
    }

    /** The unit {x, z} wind the storm clouds slide along: the rain's own when one runs, else the sky's own from its seed. */
    private static double[] windDir(Sky sky, float now) {
        List<RainRenderer.Shower> rains = EffectVisuals.rains();
        if (!rains.isEmpty()) {
            double[] w = RainRenderer.wind(rains.get(0), now, EffectVisuals.seas());
            double l = Math.hypot(w[0], w[1]);
            if (l > 1.0e-9) return new double[] {w[0] / l, w[1] / l};
        }
        return SkyDome.stormWind(sky.seed);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        List<Sky> skies = EffectVisuals.skies();
        if (skies.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !PensieveClientState.inMemory()) return;
        float partial = PensieveClientState.partial();
        float now = PensieveClientState.sceneTime(partial);
        Vec3 cam = event.getCamera().getPosition();
        float far = mc.gameRenderer.getDepthFar();
        float radius = SkyDome.domeRadius(PensieveClientState.radius(), far);
        // The event matrix is the camera rotation and is not on the modelview stack yet (see the class comment).
        Matrix4f m = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(NO_FOG_START);
        RenderSystem.setShaderFogEnd(NO_FOG_END);
        try {
            for (Sky sky : skies) {
                float f = shown(sky, now, partial);
                if (f <= 0.002f) continue;
                boolean storm = SkyDome.STORM.equals(sky.style);
                double flash = storm ? SkyDome.flash(now, EffectVisuals.strikes()) : 0.0;
                double[] dir = flash > 0.0 ? strikeDir(cam, now) : null;
                dome(sky, f, flash, dir, radius, m, buffer);
                clouds(sky, f, flash, dir, now, radius, m, buffer);
            }
        } finally {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
        }
    }

    /** The dome: rings of quads, coloured by elevation, camera-centred; a flash lights the side toward the bolt most. */
    private static void dome(Sky sky, float f, double flash, double[] dir, float radius, Matrix4f m,
                             MultiBufferSource.BufferSource buffer) {
        VertexConsumer vc = buffer.getBuffer(ModRenderTypes.SKY_DOME);
        float[][] ringColour = new float[RINGS.length][];
        for (int i = 0; i < RINGS.length; i++) {
            ringColour[i] = SkyDome.colour(sky.style, Math.max(0f, RINGS[i]) / (float) (Math.PI / 2));
        }
        for (int i = 0; i + 1 < RINGS.length; i++) {
            for (int j = 0; j < SEGMENTS; j++) {
                domeVertex(vc, m, radius, i, j, ringColour[i], f, flash, dir);
                domeVertex(vc, m, radius, i, j + 1, ringColour[i], f, flash, dir);
                domeVertex(vc, m, radius, i + 1, j + 1, ringColour[i + 1], f, flash, dir);
                domeVertex(vc, m, radius, i + 1, j, ringColour[i + 1], f, flash, dir);
            }
        }
        buffer.endBatch(ModRenderTypes.SKY_DOME);
    }

    private static void domeVertex(VertexConsumer vc, Matrix4f m, float radius, int ring, int seg, float[] c, float a,
                                   double flash, double[] dir) {
        float el = RINGS[ring];
        float az = seg * (float) (2 * Math.PI) / SEGMENTS;
        float flat = radius * (float) Math.cos(el);
        float cx = (float) Math.cos(az), cz = (float) Math.sin(az);
        double fl = flash > 0.0 ? (dir == null ? flash : flash * SkyDome.flashWeight(cx, cz, dir[0], dir[1])) : 0.0;
        vc.addVertex(m, flat * cx, radius * (float) Math.sin(el), flat * cz)
                .setColor(clamp(SkyDome.lift(c[0], 0, fl)), clamp(SkyDome.lift(c[1], 1, fl)),
                        clamp(SkyDome.lift(c[2], 2, fl)), clamp(a));
    }

    /**
     * Cloud layers on planes above the camera. The soft kinds thin away toward the horizon; a storm's are a bowl that
     * sinks to the horizon at the edge and stays opaque almost out to it, a full deck.
     */
    private static void clouds(Sky sky, float f, double flash, double[] dir, float now, float radius, Matrix4f m,
                               MultiBufferSource.BufferSource buffer) {
        boolean storm = SkyDome.STORM.equals(sky.style);
        int cells = storm ? STORM_CELLS : CLOUD_CELLS;
        float drift = SkyDome.cloudDrift(now);
        // A storm's clouds run before the wind (the rain's, when one runs); the others drift slowly their own way.
        double[] sd = storm ? windDriftOf(sky, now) : null;
        float u0 = ((sky.seed >>> 8) & 0xFF) / 255f, v0 = ((sky.seed >>> 16) & 0xFF) / 255f;
        int n = 0;
        for (Layer layer : layers(sky.style)) {
            float half = radius * 0.95f, y = radius * layer.height, tile = radius * SkyDome.CLOUD_TILE_SHARE * layer.scale;
            float du = storm ? (float) (-sd[0] * layer.speed) : drift * layer.speed;
            float dv = storm ? (float) (-sd[1] * layer.speed) : drift * layer.speed * 0.3f;
            // Each layer's pattern starts somewhere else, so the layers sharing a texture do not line up.
            float lu = storm ? u0 + n * 0.37f : u0, lv = storm ? v0 + n * 0.61f : v0;
            n++;
            VertexConsumer vc = buffer.getBuffer(layer.type);
            for (int i = 0; i < cells; i++) {
                for (int j = 0; j < cells; j++) {
                    cloudVertex(vc, m, layer, f, storm, flash, dir, cells, half, y, tile, i, j, lu, lv, du, dv);
                    cloudVertex(vc, m, layer, f, storm, flash, dir, cells, half, y, tile, i + 1, j, lu, lv, du, dv);
                    cloudVertex(vc, m, layer, f, storm, flash, dir, cells, half, y, tile, i + 1, j + 1, lu, lv, du, dv);
                    cloudVertex(vc, m, layer, f, storm, flash, dir, cells, half, y, tile, i, j + 1, lu, lv, du, dv);
                }
            }
            buffer.endBatch(layer.type);
        }
    }

    private static double[] windDriftOf(Sky sky, float now) {
        double[] w = windDir(sky, now);
        return SkyDome.stormDrift(now, w[0], w[1]);
    }

    private static void cloudVertex(VertexConsumer vc, Matrix4f m, Layer layer, float f, boolean storm, double flash,
                                    double[] dir, int cells, float half, float y, float tile, int i, int j, float u0,
                                    float v0, float du, float dv) {
        float u = i / (float) cells * 2f - 1f, v = j / (float) cells * 2f - 1f;
        float r = (float) Math.sqrt(u * u + v * v);
        float x = u * half, z = v * half;
        float a, py = y;
        if (storm) {
            float rr = Math.min(1f, r);
            py = y * (1f - 0.93f * rr * rr);                    // the deck sinks to the horizon at the rim
            float k = clamp((r - 0.9f) / 0.1f);
            a = f * layer.alpha * (1f - k * k * (3f - 2f * k));
        } else {
            float k = clamp((r - 0.3f) / 0.7f);
            a = f * layer.alpha * (1f - k * k * (3f - 2f * k));
        }
        double fl = flash > 0.0 ? (dir == null ? flash : flash * SkyDome.flashWeight(x, z, dir[0], dir[1])) : 0.0;
        vc.addVertex(m, x, py, z)
                .setColor(clamp(SkyDome.lift(layer.r, 0, fl)), clamp(SkyDome.lift(layer.g, 1, fl)),
                        clamp(SkyDome.lift(layer.b, 2, fl)), clamp(a))
                .setUv(x / tile + u0 + du, z / tile + v0 + dv)
                .setOverlay(OVERLAY).setLight(LIGHT).setNormal(0f, -1f, 0f);
    }

    private static float clamp(float f) {
        return f < 0f ? 0f : f > 1f ? 1f : f;
    }
}
