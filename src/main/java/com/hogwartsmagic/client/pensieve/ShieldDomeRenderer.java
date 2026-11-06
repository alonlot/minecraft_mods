package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.pensieve.scene.ShieldDome;
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
 * The {@code shield_dome} effect: a Protego Maxima over a whole place (see {@link ShieldDome} for the maths). A
 * latitude-longitude mesh of the upper half-ellipsoid, additive and full-bright, a hex lattice texture tiled over it,
 * pale blue with a rim glow where the view grazes it; spell hits ({@code shield_hit}) flash white and ripple out; at
 * its end it burns away from the last hit with an orange ember front. Drawn at {@code AFTER_TRANSLUCENT_BLOCKS} from
 * an identity matrix, camera-relative, no depth write but depth tested, so the hills and the castle hide its foot and
 * it fades with the world's fog. State lives in {@link EffectVisuals}, so its {@code clear()} removes the dome.
 *
 * <p>Texture: {@code build_scripts/generate_shield_dome_texture.py} ({@code shield_hex}).
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class ShieldDomeRenderer {
    private static final RenderType HEX = ModRenderTypes.magicGlow(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/shield_hex.png"));

    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;
    private static final int RINGS = 36;
    private static final int SEGS = 120;
    /** Hex tiles round the dome and from foot to apex. */
    private static final float TILES_ROUND = 60f;
    private static final float TILES_UP = 9f;

    private static final float[] BLUE = {0.55f, 0.80f, 1.0f};
    private static final float[] WHITE = {0.92f, 0.97f, 1.0f};
    private static final float[] EMBER = {1.0f, 0.50f, 0.16f};

    private ShieldDomeRenderer() {
    }

    /** One running dome: its base centre, half-axes, cast time and life (0: until the scene ends). */
    record Dome(Vec3 at, float rx, float ry, float rz, float startT, int duration) {
        float span() {
            return 2f * Math.max(rx, Math.max(ry, rz));
        }
    }

    /** One spell hit: where and when it struck. */
    record Hit(Vec3 at, float t) {
    }

    // Draw order within AFTER_TRANSLUCENT_BLOCKS: after the ceiling and candles, beside the light rays.
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        List<Dome> domes = EffectVisuals.shieldDomes();
        if (domes.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !PensieveClientState.inMemory()) return;
        float now = PensieveClientState.sceneTime(PensieveClientState.partial());
        Vec3 cam = event.getCamera().getPosition();
        List<Hit> hits = EffectVisuals.shieldHits();
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        for (Dome d : domes) {
            draw(d, hits, now, cam, m, buffer.getBuffer(HEX));
            buffer.endBatch(HEX);
        }
    }

    private static void draw(Dome d, List<Hit> hits, float now, Vec3 cam, Matrix4f m, VertexConsumer vc) {
        float elapsed = now - d.startT();
        float rise = ShieldDome.rise(elapsed);
        if (rise <= 0f || !ShieldDome.alive(elapsed, d.duration())) return;
        float since = d.duration() > 0 ? elapsed - d.duration() : -1f;
        Vec3 breakAt = null;
        if (since > 0f) {
            float breakT = d.startT() + d.duration();
            Hit last = null;
            for (Hit h : hits) {
                if (h.t() <= breakT + 0.01f && (last == null || h.t() > last.t())) last = h;
            }
            breakAt = last != null ? last.at() : d.at().add(0, d.ry(), 0);
        }
        float front = since > 0f ? ShieldDome.front(since, d.span()) : 0f;
        // The hits still rippling.
        int live = 0;
        Hit[] rip = new Hit[hits.size()];
        for (Hit h : hits) {
            float age = now - h.t();
            if (age >= 0f && age < ShieldDome.HIT_TICKS) rip[live++] = h;
        }

        // Vertex grid: position (camera-relative) and colour with alpha.
        float[][] px = new float[RINGS + 1][SEGS + 1];
        float[][] py = new float[RINGS + 1][SEGS + 1];
        float[][] pz = new float[RINGS + 1][SEGS + 1];
        float[][][] col = new float[RINGS + 1][SEGS + 1][];
        for (int i = 0; i <= RINGS; i++) {
            double phi = Math.PI / 2 * i / RINGS;
            float h = (float) Math.sin(phi);
            double cp = Math.cos(phi);
            float pres = ShieldDome.risen(h, rise);
            float seam = ShieldDome.seam(h, rise);
            for (int j = 0; j <= SEGS; j++) {
                double th = 2 * Math.PI * j / SEGS;
                double lx = d.rx() * cp * Math.cos(th), ly = d.ry() * h, lz = d.rz() * cp * Math.sin(th);
                double wx = d.at().x + lx, wy = d.at().y + ly, wz = d.at().z + lz;
                px[i][j] = (float) (wx - cam.x);
                py[i][j] = (float) (wy - cam.y);
                pz[i][j] = (float) (wz - cam.z);
                if (pres <= 0f) {
                    col[i][j] = null;
                    continue;
                }
                // Ellipsoid normal against the view direction.
                double nx = lx / (d.rx() * d.rx()), ny = ly / (d.ry() * d.ry()), nz = lz / (d.rz() * d.rz());
                double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                double vl = Math.sqrt(px[i][j] * px[i][j] + py[i][j] * py[i][j] + pz[i][j] * pz[i][j]);
                float cos = nl > 1e-9 && vl > 1e-6
                        ? (float) Math.abs((nx * px[i][j] + ny * py[i][j] + nz * pz[i][j]) / (nl * vl)) : 1f;
                float base = ShieldDome.fresnel(cos) * ShieldDome.shimmer((float) th, h, now);
                float white = seam * 1.6f;
                for (int k = 0; k < live; k++) {
                    Hit hit = rip[k];
                    float dist = (float) Math.sqrt((wx - hit.at().x) * (wx - hit.at().x)
                            + (wy - hit.at().y) * (wy - hit.at().y) + (wz - hit.at().z) * (wz - hit.at().z));
                    if (dist < ShieldDome.HIT_REACH + 8f) white += ShieldDome.ripple(dist, now - hit.t());
                }
                float ember = 0f, keep = 1f;
                if (breakAt != null) {
                    float dist = (float) Math.sqrt((wx - breakAt.x) * (wx - breakAt.x)
                            + (wy - breakAt.y) * (wy - breakAt.y) + (wz - breakAt.z) * (wz - breakAt.z));
                    keep = ShieldDome.burnt(dist, front);
                    ember = ShieldDome.embers(dist, front);
                }
                float a = (base * keep + white * 0.5f * keep) * pres;
                float r = (BLUE[0] * base + WHITE[0] * white * 0.5f) * keep + EMBER[0] * ember;
                float g = (BLUE[1] * base + WHITE[1] * white * 0.5f) * keep + EMBER[1] * ember;
                float b = (BLUE[2] * base + WHITE[2] * white * 0.5f) * keep + EMBER[2] * ember;
                col[i][j] = new float[]{r * pres, g * pres, b * pres, Math.min(1f, a + ember * 0.6f)};
            }
        }
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SEGS; j++) {
                float[] c00 = col[i][j], c01 = col[i][j + 1], c11 = col[i + 1][j + 1], c10 = col[i + 1][j];
                if (c00 == null && c01 == null && c10 == null && c11 == null) continue;
                float u0 = TILES_ROUND * j / SEGS, u1 = TILES_ROUND * (j + 1) / SEGS;
                float v0 = TILES_UP * i / RINGS, v1 = TILES_UP * (i + 1) / RINGS;
                put(vc, m, px[i][j], py[i][j], pz[i][j], c00, u0, v0);
                put(vc, m, px[i][j + 1], py[i][j + 1], pz[i][j + 1], c01, u1, v0);
                put(vc, m, px[i + 1][j + 1], py[i + 1][j + 1], pz[i + 1][j + 1], c11, u1, v1);
                put(vc, m, px[i + 1][j], py[i + 1][j], pz[i + 1][j], c10, u0, v1);
            }
        }
    }

    /** One additive vertex; a null colour is fully clear. */
    private static void put(VertexConsumer vc, Matrix4f m, float x, float y, float z, float[] c, float u, float v) {
        float r = 0f, g = 0f, b = 0f, a = 0f;
        if (c != null) {
            r = Math.min(1f, c[0]);
            g = Math.min(1f, c[1]);
            b = Math.min(1f, c[2]);
            a = Math.max(0f, c[3]);
        }
        vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OVERLAY).setLight(LIGHT)
                .setNormal(0f, 1f, 0f);
    }
}
