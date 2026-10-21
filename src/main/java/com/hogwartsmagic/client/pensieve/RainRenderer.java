package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.pensieve.scene.RoughSea;
import com.hogwartsmagic.pensieve.scene.SkyDome;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The {@code rain} effect: slanted, wind-driven rain in the event's box, as one batched pass of streaks (no particles
 * for the drops themselves). Every drop is a pure function of the scene clock and the effect seed: the drops of one
 * 24-block tile fall at their own speeds (0.55 to 0.75 blocks a tick), drifting with the wind as they fall, and come
 * back at a fresh spot each fall, the tiles repeating across the box with their own hash so no pattern shows. The wind
 * is the wind of the rough sea running under the rain (so the rain slants the way the waves run), else one of the
 * rain's own, gusting between about 0.7 and 1.2 of its strength.
 *
 * <p>A streak is a soft, camera-facing ribbon along the drop's velocity, about a block long (its motion blur), thin
 * and pale grey-blue, fading out toward 26 blocks from the camera and into the fog, brighter in a lightning flash. Drops
 * stop at the first surface under them: the highest motion-blocking block (a roof keeps the rain out of a hut) or the
 * rough sea's waves where they are higher; there, within 16 blocks of the camera, they splash (the vanilla rain
 * splash; on the sea a water splash), at most 80 a tick. Drawn at {@code AFTER_TRANSLUCENT_BLOCKS} after the sea, from
 * an identity matrix; state in {@link EffectVisuals}.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class RainRenderer {
    private static final RenderType STREAK = ModRenderTypes.ceilingLayer(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/ceiling_dot.png"));
    /** The storm's distant rain curtains: vertical streaks (build_scripts/generate_ceiling_textures.py, seeded). */
    private static final RenderType SHEET = ModRenderTypes.ceilingLayer(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/rain_sheet.png"));
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;

    static final int TILE = 24;
    /** Drops per cubic block at intensity 1, and the most in one tile. */
    private static final double DENSITY = 0.03;
    private static final int MAX_PER_TILE = 4000;
    /** Drops are drawn within this distance and splash within the smaller one. */
    private static final double REACH = 26, SPLASH_REACH = 16;
    private static final int MAX_SPLASHES = 80;
    private static final float[] COLOUR = {0.56f, 0.61f, 0.69f};
    private static final double WIND = 0.22;

    private RainRenderer() {
    }

    /** One running rain: its box in the world, intensity, start, length and seed. */
    static final class Shower {
        final double x0, y0, z0, x1, y1, z1;
        final double intensity;
        final float t0;
        final int duration;
        final long seed;
        final int perTile;
        final double height;

        Shower(Vec3 at, float[] spread, double intensity, float t0, int duration, long seed) {
            this.x0 = at.x - spread[0];
            this.x1 = at.x + spread[0];
            this.y0 = at.y - spread[1];
            this.y1 = at.y + spread[1];
            this.z0 = at.z - spread[2];
            this.z1 = at.z + spread[2];
            this.intensity = intensity;
            this.t0 = t0;
            this.duration = duration;
            this.seed = seed;
            this.height = y1 - y0;
            this.perTile = (int) Math.min(MAX_PER_TILE, Math.round(DENSITY * intensity * TILE * TILE * height));
        }

        boolean activeAt(float t) {
            return t >= t0 && (duration <= 0 || t < t0 + duration);
        }

        /** The strength now: eased in over a second from the start, out over the last second of a finite rain. */
        double strength(float t) {
            if (!activeAt(t)) return 0;
            double k = Math.min(1, (t - t0) / 20.0);
            if (duration > 0) k = Math.min(k, (t0 + duration - t) / 20.0);
            return Math.max(0, k);
        }
    }

    /** The wind (unit {x, z} times blocks per tick) at scene time t: the rough sea's direction when one runs. */
    static double[] wind(Shower s, float t, List<RoughSeaRenderer.Water> seas) {
        double dx, dz;
        if (!seas.isEmpty()) {
            dx = RoughSea.windX(seas.get(0).sea.seed());
            dz = RoughSea.windZ(seas.get(0).sea.seed());
        } else {
            double a = (mix(s.seed) >>> 11) * 0x1.0p-53 * Math.PI * 2;
            dx = Math.cos(a);
            dz = Math.sin(a);
        }
        double gust = 0.95 + 0.25 * Math.sin(t * 0.031 + (s.seed & 63)) * Math.sin(t * 0.0137 + 1.3);
        double speed = WIND * gust * Math.sqrt(s.intensity);
        return new double[]{dx * speed, dz * speed};
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    /**
     * Drop {@code i} of tile (tx, tz) at scene time t, into {@code out}: {x, y, z (world, the drop's head), speed}; false
     * when it is outside the box. Tiles are counted from the scene origin {@code o}, so the rain does not depend on
     * where the session slot sits in the world.
     */
    private static boolean drop(Shower s, int i, int tx, int tz, float t, double[] wind, Vec3 o, double[] out) {
        long h = mix(s.seed ^ (i * 0x9E3779B97F4A7C15L));
        double speed = 0.55 + 0.2 * unit(h);
        double phase = unit(mix(h + 1));
        double falls = (t - s.t0) * speed / s.height + phase;
        long cycle = (long) Math.floor(falls);
        double f = falls - cycle;
        long hc = mix(h ^ (cycle * 0xC2B2AE3D27D4EB4FL) ^ ((long) tx * 0x165667B19E3779F9L) ^ ((long) tz * 0x27D4EB2F165667C5L));
        double fallTime = f * s.height / speed;
        double u = unit(hc) * TILE + wind[0] * fallTime, w = unit(mix(hc)) * TILE + wind[1] * fallTime;
        u -= Math.floor(u / TILE) * TILE;
        w -= Math.floor(w / TILE) * TILE;
        double x = o.x + tx * TILE + u, z = o.z + tz * TILE + w, y = s.y1 - f * s.height;
        if (x < s.x0 || x > s.x1 || z < s.z0 || z > s.z1) return false;
        out[0] = x;
        out[1] = y;
        out[2] = z;
        out[3] = speed;
        return true;
    }

    /** The surface a drop at (x, z) stops on: the highest motion-blocking block, or the rough sea where it is higher. */
    static double ground(ClientLevel level, double x, double z, float t, Vec3 origin, List<RoughSeaRenderer.Water> seas) {
        double g = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        for (RoughSeaRenderer.Water w : seas) {
            double lx = x - origin.x, lz = z - origin.z;
            if (w.sea.covers(lx, lz) && w.sea.intensityAt(t) > 0) g = Math.max(g, origin.y + w.sea.surface(lx, lz, t));
        }
        return g;
    }

    // After the sea (NORMAL) and the light rays (LOW): the streaks are depth-tested against the waves.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        List<Shower> rains = EffectVisuals.rains();
        if (rains.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.level instanceof ClientLevel level) || !PensieveClientState.inMemory()) return;
        float now = PensieveClientState.sceneTime(event.getPartialTick());
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vec3 origin = MemoryEvents.sceneOrigin();
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        // One flash for sky and rain: the sky's own flicker (two or three pulses over 1.3 s), not vanilla's 2-tick blink.
        float flash = (float) Math.min(1.0, SkyDome.flash(now, EffectVisuals.strikes()) / SkyDome.FLASH_PEAK);
        List<RoughSeaRenderer.Water> seas = EffectVisuals.seas();
        double[] d = new double[4];
        // Far to near: the distant curtains first (a storm sky is running), then the drops round the camera.
        float storm = SkyRenderer.stormShare(event.getPartialTick());
        if (storm > 0f) {
            Shower first = null;
            double most = 0;
            for (Shower s : rains) {
                double k = s.strength(now) * Math.min(1.0, s.intensity);
                if (k > most) {
                    most = k;
                    first = s;
                }
            }
            if (first != null) {
                sheets(buffer.getBuffer(SHEET), m, cam, origin, wind(first, now, seas), now, (float) most * storm, flash);
                buffer.endBatch(SHEET);
            }
        }
        VertexConsumer vc = buffer.getBuffer(STREAK);
        for (Shower s : rains) {
            double k = s.strength(now);
            if (k <= 0) continue;
            double[] wind = wind(s, now, seas);
            int count = (int) Math.round(s.perTile * k);
            int tx0 = (int) Math.floor((Math.max(s.x0, cam.x - REACH) - origin.x) / TILE), tx1 = (int) Math.floor((Math.min(s.x1, cam.x + REACH) - origin.x) / TILE);
            int tz0 = (int) Math.floor((Math.max(s.z0, cam.z - REACH) - origin.z) / TILE), tz1 = (int) Math.floor((Math.min(s.z1, cam.z + REACH) - origin.z) / TILE);
            for (int tx = tx0; tx <= tx1; tx++) {
                for (int tz = tz0; tz <= tz1; tz++) {
                    for (int i = 0; i < count; i++) {
                        if (!drop(s, i, tx, tz, now, wind, origin, d)) continue;
                        double px = d[0] - cam.x, py = d[1] - cam.y, pz = d[2] - cam.z;
                        double dist2 = px * px + py * py + pz * pz;
                        if (dist2 > REACH * REACH) continue;
                        streak(vc, m, level, s, d, wind, px, py, pz, Math.sqrt(dist2), now, origin, seas, flash);
                    }
                }
            }
        }
        buffer.endBatch(STREAK);
    }

    /** Segments round each curtain, and the rows up it (a fade at the foot, a fade under the cloud). */
    private static final int SHEET_SEGMENTS = 36;
    /** A curtain's streaks repeat this many times round it, and each tile is this many tiles tall per block of radius. */
    private static final double SHEET_REPEATS = 18.0;
    /** Rows of a curtain: the alpha share at its foot, 8 blocks up, 12 under its top, and its top. */
    private static final float[] ROW_ALPHA = {0f, 1f, 1f, 0f};

    /**
     * The storm's distant rain: {@link SkyDome#STORM_BANDS} camera-centred curtains of vertical streaks, 35, 70 and 130
     * blocks out, from the ground of the scene up under the cloud. The streaks scroll down at the fall speed and lean
     * with the wind; a curtain's alpha is its band's share times the rain's strength, brighter in a flash. Translucent,
     * depth-tested (the stands and the castle stand in front of them), no depth written, allocation-free.
     */
    private static void sheets(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 origin, double[] wind, float now,
                               float share, float flash) {
        double foot = origin.y - 1.0;
        double top = Math.max(cam.y + 40.0, origin.y + 80.0);
        double wl = Math.hypot(wind[0], wind[1]);
        for (int b = 0; b < SkyDome.STORM_BANDS.length; b++) {
            double radius = SkyDome.STORM_BANDS[b];
            float alpha = SkyDome.STORM_BAND_ALPHA[b] * share * (1f + flash * 1.2f);
            float br = 1f + flash * 0.6f;
            for (int seg = 0; seg < SHEET_SEGMENTS; seg++) {
                for (int row = 0; row < 3; row++) {
                    for (int corner = 0; corner < 4; corner++) {
                        // (seg, row) bottom-left, (seg+1, row), (seg+1, row+1), (seg, row+1)
                        int sg = seg + (corner == 1 || corner == 2 ? 1 : 0);
                        int rw = row + (corner >= 2 ? 1 : 0);
                        double az = sg * (Math.PI * 2 / SHEET_SEGMENTS);
                        double cs = Math.cos(az), sn = Math.sin(az);
                        double up = rw == 0 ? 0 : rw == 1 ? 8.0 : rw == 2 ? top - foot - 12 : top - foot;
                        // The streaks lean with the wind: along the curtain, a higher point is upwind of the foot.
                        double lean = wl <= 1e-9 ? 0 : (wind[0] * -sn + wind[1] * cs) / 0.65 * up / (radius * 0.35);
                        float u = (float) (az / (Math.PI * 2) * SHEET_REPEATS + lean + b * 0.43);
                        float v = (float) SkyDome.sheetV(up, radius, now, b);   // streaks fall: see sheetV
                        vc.addVertex(m, (float) (cs * radius), (float) (foot + up - cam.y),
                                        (float) (sn * radius))
                                .setColor(COLOUR[0] * br, COLOUR[1] * br, COLOUR[2] * br, alpha * ROW_ALPHA[rw])
                                .setUv(u, v).setOverlay(OVERLAY).setLight(LIGHT).setNormal(0f, 1f, 0f);
                    }
                }
            }
        }
    }

    private static void streak(VertexConsumer vc, Matrix4f m, ClientLevel level, Shower s, double[] d, double[] wind,
                               double px, double py, double pz, double dist, float now, Vec3 origin,
                               List<RoughSeaRenderer.Water> seas, float flash) {
        double speed = d[3];
        // Direction of travel and the streak's length (the drop's motion over 1.6 ticks).
        double vx = wind[0], vy = -speed, vz = wind[1];
        double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
        vx /= vl;
        vy /= vl;
        vz /= vl;
        double len = speed * 1.6;
        double ground = ground(level, d[0], d[2], now, origin, seas);
        double topY = d[1], botY = d[1] + vy * len;
        if (topY <= ground) return;
        double cut = botY < ground ? (topY - ground) / (topY - botY) : 1;
        double l = len * cut;
        float ax = (float) px, ay = (float) py, az = (float) pz;
        float bx = (float) (px + vx * l), by = (float) (py + vy * l), bz = (float) (pz + vz * l);
        // Across the streak, facing the camera.
        double cx = vy * pz - vz * py, cy = vz * px - vx * pz, cz = vx * py - vy * px;
        double cl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (cl < 1e-6) return;
        double half = 0.008 + 0.0018 * dist;
        cx = cx / cl * half;
        cy = cy / cl * half;
        cz = cz / cl * half;
        double fade = 1 - (dist / REACH) * (dist / REACH);
        float a = (float) (0.30 * fade * (1 + flash * 0.8));
        float br = 1f + flash * 0.7f;
        float r = COLOUR[0] * br, g = COLOUR[1] * br, b = COLOUR[2] * br;
        vc.addVertex(m, (float) (ax - cx), (float) (ay - cy), (float) (az - cz)).setColor(r, g, b, a).setUv(0f, 0f)
                .setOverlay(OVERLAY).setLight(LIGHT).setNormal(0f, 1f, 0f);
        vc.addVertex(m, (float) (ax + cx), (float) (ay + cy), (float) (az + cz)).setColor(r, g, b, a).setUv(1f, 0f)
                .setOverlay(OVERLAY).setLight(LIGHT).setNormal(0f, 1f, 0f);
        vc.addVertex(m, (float) (bx + cx), (float) (by + cy), (float) (bz + cz)).setColor(r, g, b, a).setUv(1f, 1f)
                .setOverlay(OVERLAY).setLight(LIGHT).setNormal(0f, 1f, 0f);
        vc.addVertex(m, (float) (bx - cx), (float) (by - cy), (float) (bz - cz)).setColor(r, g, b, a).setUv(0f, 1f)
                .setOverlay(OVERLAY).setLight(LIGHT).setNormal(0f, 1f, 0f);
    }

    /**
     * One client tick of a rain: the drops near the camera that reached their surface since the last tick splash there.
     * Called from {@link EffectVisuals}.
     */
    static void splashes(ClientLevel level, Shower s, float now) {
        if (s.strength(now) <= 0) return;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 origin = MemoryEvents.sceneOrigin();
        List<RoughSeaRenderer.Water> seas = EffectVisuals.seas();
        double[] wind = wind(s, now, seas), d = new double[4], e = new double[4];
        int count = (int) Math.round(s.perTile * s.strength(now));
        int tx0 = (int) Math.floor((Math.max(s.x0, cam.x - SPLASH_REACH) - origin.x) / TILE), tx1 = (int) Math.floor((Math.min(s.x1, cam.x + SPLASH_REACH) - origin.x) / TILE);
        int tz0 = (int) Math.floor((Math.max(s.z0, cam.z - SPLASH_REACH) - origin.z) / TILE), tz1 = (int) Math.floor((Math.min(s.z1, cam.z + SPLASH_REACH) - origin.z) / TILE);
        int made = 0;
        for (int tx = tx0; tx <= tx1 && made < MAX_SPLASHES; tx++) {
            for (int tz = tz0; tz <= tz1 && made < MAX_SPLASHES; tz++) {
                for (int i = 0; i < count && made < MAX_SPLASHES; i++) {
                    if (!drop(s, i, tx, tz, now, wind, origin, d) || !drop(s, i, tx, tz, now - 1, wind, origin, e)) continue;
                    if (e[1] < d[1]) continue;                          // a new fall began: no crossing this tick
                    double dx = d[0] - cam.x, dz = d[2] - cam.z;
                    if (dx * dx + dz * dz > SPLASH_REACH * SPLASH_REACH) continue;
                    double g = ground(level, d[0], d[2], now, origin, seas);
                    if (d[1] <= g && e[1] > g) {
                        boolean sea = g > level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(d[0]), (int) Math.floor(d[2]));
                        level.addParticle(sea ? ParticleTypes.SPLASH : ParticleTypes.RAIN, d[0], g + 0.02, d[2], 0, 0, 0);
                        made++;
                    }
                }
            }
        }
    }
}
