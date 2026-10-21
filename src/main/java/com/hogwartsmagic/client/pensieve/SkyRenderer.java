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
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;

    /** Dome rings, as elevations in radians from just under the horizon to the zenith, and segments round. */
    private static final float[] RINGS = {-0.30f, -0.08f, 0f, 0.07f, 0.17f, 0.30f, 0.46f, 0.65f, 0.88f, 1.12f, 1.34f,
            (float) (Math.PI / 2)};
    private static final int SEGMENTS = 36;
    /** The cloud grid cells along a side. */
    private static final int CLOUD_CELLS = 16;
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
            // Three low, fast, torn layers: a ragged small-scale one in front, a wide slow one behind, dark slate.
            return new Layer[] {
                    new Layer(CLOUDS_A, 0.30f, 0.33f, 0.38f, 0.95f, 0.8f, 2.0f, 0.11f),
                    new Layer(CLOUDS_B, 0.22f, 0.25f, 0.30f, 0.85f, 0.5f, 3.2f, 0.14f),
                    new Layer(CLOUDS_A, 0.16f, 0.18f, 0.22f, 0.75f, 1.3f, 1.3f, 0.18f)};
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
        float partial = event.getPartialTick();
        float now = PensieveClientState.sceneTime(partial);
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
                double flash = SkyDome.STORM.equals(sky.style) ? SkyDome.flash(now, EffectVisuals.strikes()) : 0.0;
                dome(sky, f, flash, radius, m, buffer);
                clouds(sky, f, flash, now, radius, m, buffer);
            }
        } finally {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
        }
    }

    /** The dome: rings of quads, coloured by elevation, camera-centred. */
    private static void dome(Sky sky, float f, double flash, float radius, Matrix4f m,
                             MultiBufferSource.BufferSource buffer) {
        VertexConsumer vc = buffer.getBuffer(ModRenderTypes.SKY_DOME);
        float[][] ringColour = new float[RINGS.length][];
        for (int i = 0; i < RINGS.length; i++) {
            float[] c = SkyDome.colour(sky.style, Math.max(0f, RINGS[i]) / (float) (Math.PI / 2));
            ringColour[i] = flash > 0.0 ? SkyDome.flashed(c, flash) : c;
        }
        for (int i = 0; i + 1 < RINGS.length; i++) {
            for (int j = 0; j < SEGMENTS; j++) {
                domeVertex(vc, m, radius, i, j, ringColour[i], f);
                domeVertex(vc, m, radius, i, j + 1, ringColour[i], f);
                domeVertex(vc, m, radius, i + 1, j + 1, ringColour[i + 1], f);
                domeVertex(vc, m, radius, i + 1, j, ringColour[i + 1], f);
            }
        }
        buffer.endBatch(ModRenderTypes.SKY_DOME);
    }

    private static void domeVertex(VertexConsumer vc, Matrix4f m, float radius, int ring, int seg, float[] c, float a) {
        float el = RINGS[ring];
        float az = seg * (float) (2 * Math.PI) / SEGMENTS;
        float flat = radius * (float) Math.cos(el);
        vc.addVertex(m, flat * (float) Math.cos(az), radius * (float) Math.sin(el), flat * (float) Math.sin(az))
                .setColor(clamp(c[0]), clamp(c[1]), clamp(c[2]), clamp(a));
    }

    /** Soft cloud layers on flat planes above the camera, thinning away toward the horizon. */
    private static void clouds(Sky sky, float f, double flash, float now, float radius, Matrix4f m,
                               MultiBufferSource.BufferSource buffer) {
        boolean storm = SkyDome.STORM.equals(sky.style);
        float drift = SkyDome.cloudDrift(now);
        // A storm's clouds run before the wind (the rain's, when one runs); the others drift slowly their own way.
        double[] sd = storm ? windDriftOf(sky, now) : null;
        float u0 = ((sky.seed >>> 8) & 0xFF) / 255f, v0 = ((sky.seed >>> 16) & 0xFF) / 255f;
        for (Layer layer : layers(sky.style)) {
            float half = radius * 0.95f, y = radius * layer.height, tile = radius * 0.6f * layer.scale;
            float du = storm ? (float) (-sd[0] * layer.speed) : drift * layer.speed;
            float dv = storm ? (float) (-sd[1] * layer.speed) : drift * layer.speed * 0.3f;
            float cr = layer.r, cg = layer.g, cb = layer.b;
            if (flash > 0.0) {
                float[] lit = SkyDome.flashed(new float[] {cr, cg, cb}, flash);
                cr = lit[0];
                cg = lit[1];
                cb = lit[2];
            }
            VertexConsumer vc = buffer.getBuffer(layer.type);
            for (int i = 0; i < CLOUD_CELLS; i++) {
                for (int j = 0; j < CLOUD_CELLS; j++) {
                    cloudVertex(vc, m, layer, cr, cg, cb, f, half, y, tile, i, j, u0, v0, du, dv);
                    cloudVertex(vc, m, layer, cr, cg, cb, f, half, y, tile, i + 1, j, u0, v0, du, dv);
                    cloudVertex(vc, m, layer, cr, cg, cb, f, half, y, tile, i + 1, j + 1, u0, v0, du, dv);
                    cloudVertex(vc, m, layer, cr, cg, cb, f, half, y, tile, i, j + 1, u0, v0, du, dv);
                }
            }
            buffer.endBatch(layer.type);
        }
    }

    private static double[] windDriftOf(Sky sky, float now) {
        double[] w = windDir(sky, now);
        return SkyDome.stormDrift(now, w[0], w[1]);
    }

    private static void cloudVertex(VertexConsumer vc, Matrix4f m, Layer layer, float cr, float cg, float cb, float f,
                                    float half, float y, float tile, int i, int j, float u0, float v0, float du,
                                    float dv) {
        float u = i / (float) CLOUD_CELLS * 2f - 1f, v = j / (float) CLOUD_CELLS * 2f - 1f;
        float r = (float) Math.sqrt(u * u + v * v);
        float k = clamp((r - 0.3f) / 0.7f);
        float a = f * layer.alpha * (1f - k * k * (3f - 2f * k));
        float x = u * half, z = v * half;
        vc.addVertex(m, x, y, z).setColor(clamp(cr), clamp(cg), clamp(cb), clamp(a))
                .setUv(x / tile + u0 + du, z / tile + v0 + dv)
                .setOverlay(OVERLAY).setLight(LIGHT).setNormal(0f, -1f, 0f);
    }

    private static float clamp(float f) {
        return f < 0f ? 0f : f > 1f ? 1f : f;
    }
}
