package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.block.entity.CaveInimicumWardBlockEntity;
import com.hogwartsmagic.caveinimicum.CaveInimicumWardBounds;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import static com.hogwartsmagic.client.SpellFxGeometry.*;

/**
 * The Ward's own magic, drawn by hand from the synced block entity instead of with particles.
 *
 * <p><b>The Protego Maxima dome</b> is woven, not switched on. A rune circle lights on the ground where its foot will
 * stand, strands of light climb out of it and spiral up over each other to the crown, and the shell fills in behind
 * them: a pearly skin, brightest where you see it edge-on, with veils of light drifting over it like an aurora and
 * sparkles breathing off it. Released, it sinks back into the circle; knocked down by an Alohomora it flares, comes
 * apart in drifting patches and throws its sparkles outward. Hidden while Cave Inimicum conceals the ward. The dome is
 * the real barrier: CaveInimicumManager stops projectiles where they cross this sphere.
 *
 * <p><b>The outline</b> (Cave Inimicum's teal, for a few seconds when the ward is primed, sized or clicked): light
 * traces the cube's edges, the floor first, then up the corners, then closed across the top, a star riding each
 * growing tip; once drawn, beads of light run along the edges, a sigil turns at every corner and the lines shed
 * sparkles into the air.
 *
 * <p>The dome is heard as well as seen, from the point of its skin nearest the listener: the weave rising, a soft
 * hum while it stands, the sinking away or the shatter. Played here on the change of state, so the sound lands with
 * the picture and stays as hidden as it is under Cave Inimicum.
 *
 * <p>Camera-relative in double precision, identity matrix (this stage is after the camera push), true partial tick.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class WardFxRenderer {
    private static final double MAX_DISTANCE = 160.0;

    private static final float CIRCLE_IN = 7.0f;
    private static final float WEAVE_START = 4.0f;
    private static final float BARRIER_IN = 24.0f;
    private static final float BARRIER_OUT = 14.0f;
    private static final float BURST_TICKS = 18.0f;
    private static final float OUTLINE_IN = 16.0f;
    private static final float OUTLINE_OUT = 12.0f;
    private static final float FULL_REACH = Mth.HALF_PI + 0.3f;
    private static final int STRANDS = 6;

    // Pearl: cold blue to lavender across the skin, aqua in the veils, warm white in the strands and sparkles.
    private static final float[] BLUE = {0.50f, 0.72f, 1.00f};
    private static final float[] LAVENDER = {0.76f, 0.66f, 1.00f};
    private static final float[] AQUA = {0.62f, 1.00f, 0.95f};
    private static final float[] WARM = {1.00f, 0.94f, 0.80f};
    // Cave Inimicum teal, and the near-white it burns to.
    private static final float[] TEAL = {0.46f, 0.94f, 0.88f};
    private static final float[] TEAL_HOT = {0.86f, 1.00f, 0.97f};

    /** What each ward showed on the last frame, so a change of state can animate. Client thread only. */
    private static final Map<CaveInimicumWardBlockEntity, Visual> VISUALS = new WeakHashMap<>();

    private static final class Visual {
        boolean barrier;
        float barrierAt = -1.0E4f;
        boolean burst;
        boolean outline;
        float outlineAt = -1.0E4f;
        HumSound hum;
    }

    /** One ward's frame, camera-relative. */
    private static final class Frame {
        int seed;
        float now;
        // The dome.
        double cx, cy, cz;
        float radius;
        float strength;
        float circle;
        float reach;
        float front;
        float dissolve;
        float flare;
        float burstAge = -1.0f;
        // The outline's cube.
        double x0, y0, z0, x1, y1, z1;
        float outlineStrength;
        float trace;
    }

    private WardFxRenderer() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        VISUALS.clear();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || CaveInimicumWardBlockEntity.clientLoaded().isEmpty()) {
            return;
        }
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        long tick = level.getGameTime();
        float now = tick + partial;
        Vec3 camera = event.getCamera().getPosition();

        List<Frame> domes = new ArrayList<>();
        List<Frame> outlines = new ArrayList<>();
        for (CaveInimicumWardBlockEntity ward : new ArrayList<>(CaveInimicumWardBlockEntity.clientLoaded())) {
            if (ward.isRemoved() || ward.getLevel() != level) {
                continue;
            }
            boolean wantBarrier = ward.isBarrierVisible(tick);
            Visual v = VISUALS.get(ward);
            if (v == null) {
                // Already standing when it came into view: shown whole, no weave and no sound.
                v = new Visual();
                v.barrier = wantBarrier;
                v.outline = ward.getOutlineUntil() > tick;
                VISUALS.put(ward, v);
            }
            if (wantBarrier != v.barrier) {
                v.barrier = wantBarrier;
                v.barrierAt = now;
                v.burst = !wantBarrier && ward.isProtegoMaximaActive() && ward.getBarrierSuppressedUntil() > tick;
                if (wantBarrier) {
                    playAt(ModSounds.WARD_BARRIER_RAISE.get(), ward, 1.3f);
                } else if (v.burst) {
                    playAt(ModSounds.WARD_BARRIER_SHATTER.get(), ward, 1.4f);
                } else {
                    playAt(ModSounds.WARD_BARRIER_RELEASE.get(), ward, 1.1f);
                }
            }
            if (v.barrier && (v.hum == null || v.hum.isStopped())) {
                v.hum = new HumSound(ward);
                minecraft.getSoundManager().play(v.hum);
            }
            boolean wantOutline = ward.getOutlineUntil() > tick;
            if (wantOutline != v.outline) {
                v.outline = wantOutline;
                v.outlineAt = now;
            }

            Frame f = new Frame();
            f.seed = (int) ward.getBlockPos().asLong();
            f.now = now;
            Vec3 centre = ward.barrierCentre();
            f.cx = centre.x - camera.x;
            f.cy = centre.y - camera.y;
            f.cz = centre.z - camera.z;
            f.radius = (float) ward.barrierRadius();
            CaveInimicumWardBounds bounds = ward.bounds();
            BlockPos min = bounds.min();
            BlockPos max = bounds.max();
            f.x0 = min.getX() - camera.x;
            f.y0 = min.getY() - camera.y;
            f.z0 = min.getZ() - camera.z;
            f.x1 = max.getX() + 1.0 - camera.x;
            f.y1 = max.getY() + 1.0 - camera.y;
            f.z1 = max.getZ() + 1.0 - camera.z;
            if (f.cx * f.cx + f.cy * f.cy + f.cz * f.cz > MAX_DISTANCE * MAX_DISTANCE) {
                continue;
            }
            if (prepareDome(f, v, now)) {
                domes.add(f);
            }
            if (prepareOutline(f, v, now)) {
                outlines.add(f);
            }
        }
        if (domes.isEmpty() && outlines.isEmpty()) {
            return;
        }

        Matrix4f matrix = new Matrix4f();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer light = buffer.getBuffer(ModRenderTypes.PROTEGO_LIGHT);
        for (Frame f : domes) {
            drawShell(light, matrix, f);
            drawStrands(light, matrix, f);
            drawRuneCircle(light, matrix, f);
            drawSparkles(light, matrix, f);
        }
        for (Frame f : outlines) {
            drawOutline(light, matrix, f);
        }
        buffer.endBatch(ModRenderTypes.PROTEGO_LIGHT);
    }

    // ---- state ---------------------------------------------------------------------------------------------------

    private static boolean prepareDome(Frame f, Visual v, float now) {
        float age = now - v.barrierAt;
        if (v.barrier) {
            f.strength = 1.0f;
            f.circle = smoothstep(0.0f, CIRCLE_IN, age);
            f.reach = FULL_REACH * easeOutCubic(smoothstep(WEAVE_START, BARRIER_IN, age));
            f.front = 1.0f - smoothstep(BARRIER_IN - 4.0f, BARRIER_IN + 8.0f, age);
            f.flare = 0.5f * gauss((age - BARRIER_IN) / 5.0f);
        } else if (v.burst) {
            if (age >= BURST_TICKS) {
                return false;
            }
            float t = age / BURST_TICKS;
            f.strength = 1.0f - t * t;
            f.circle = 1.0f - t;
            f.reach = FULL_REACH;
            f.flare = 1.5f * (1.0f - smoothstep(0.0f, 0.3f, t));
            f.dissolve = smoothstep(0.05f, 0.85f, t);
            f.burstAge = age;
        } else {
            if (age >= BARRIER_OUT) {
                return false;
            }
            float t = age / BARRIER_OUT;
            f.strength = 1.0f;
            f.reach = FULL_REACH * (1.0f - easeInCubic(t));
            f.front = 1.0f - t * 0.5f;
            f.circle = 1.0f - smoothstep(0.55f, 1.0f, t);
        }
        return f.strength > 0.004f;
    }

    private static boolean prepareOutline(Frame f, Visual v, float now) {
        float age = now - v.outlineAt;
        if (v.outline) {
            f.trace = Mth.clamp(age / OUTLINE_IN, 0.0f, 1.0f);
            f.outlineStrength = 1.0f;
        } else {
            if (age >= OUTLINE_OUT) {
                return false;
            }
            f.trace = 1.0f;
            float t = age / OUTLINE_OUT;
            f.outlineStrength = (1.0f - t) * (0.75f + 0.25f * Mth.sin(age * 2.1f));
        }
        return f.outlineStrength > 0.004f;
    }

    // ---- the dome ------------------------------------------------------------------------------------------------

    /**
     * The skin: a fresnel rim, two layers of drifting aurora veils, a slow sheen, the soft glow of the weaving front
     * and the light where the dome meets the ground, its colour wandering between blue and lavender like a pearl.
     */
    private static void drawShell(VertexConsumer out, Matrix4f m, Frame f) {
        float radius = f.radius;
        int lon = Mth.clamp(Math.round(Mth.TWO_PI * radius / 0.9f), 48, 144);
        int lat = lon / 2;
        int stride = lon + 1;
        int count = (lat + 1) * stride;
        float[] xs = new float[count];
        float[] ys = new float[count];
        float[] zs = new float[count];
        float[] rs = new float[count];
        float[] gs = new float[count];
        float[] bs = new float[count];
        float[] as = new float[count];
        float t = f.now;
        float seed = (f.seed & 1023) * 0.37f;
        for (int i = 0; i <= lat; i++) {
            float e = -Mth.HALF_PI + Mth.PI * i / lat;
            float ny = Mth.sin(e);
            float ring = Mth.cos(e);
            float absE = Math.abs(e);
            float cover = 1.0f - smoothstep(f.reach - 0.25f, f.reach, absE);
            for (int j = 0; j <= lon; j++) {
                float phi = Mth.TWO_PI * j / lon;
                float nx = ring * Mth.cos(phi);
                float nz = ring * Mth.sin(phi);
                double x = f.cx + nx * radius;
                double y = f.cy + ny * radius;
                double z = f.cz + nz * radius;
                int index = i * stride + j;
                xs[index] = (float) x;
                ys[index] = (float) y;
                zs[index] = (float) z;
                float c = cover;
                if (f.dissolve > 0.0f && c > 0.0f) {
                    // Comes apart in drifting patches rather than tiles.
                    float blotch = 0.5f + 0.25f * Mth.sin(phi * 5.0f + e * 3.0f + seed)
                            + 0.25f * Mth.sin(phi * 3.0f - e * 7.0f + seed * 2.0f + t * 0.05f);
                    c *= smoothstep(f.dissolve * 1.25f - 0.2f, f.dissolve * 1.25f, blotch);
                }
                if (c <= 0.001f) {
                    continue;
                }
                double length = Math.sqrt(x * x + y * y + z * z);
                float facing = length < 1.0E-4 ? 1.0f : (float) Math.abs((x * nx + y * ny + z * nz) / length);
                float rim = (float) Math.pow(1.0f - facing, 2.5);
                // Aurora curtains: hanging folds that sway and drift round the dome, fading towards its crown.
                float fold = phi * 3.0f + 1.3f * Mth.sin(e * 3.0f + t * 0.021f + phi + seed);
                float veil = (float) Math.pow(0.5f + 0.5f * Mth.sin(fold + t * 0.012f), 8.0)
                        * (0.6f + 0.4f * Mth.sin(e * 5.0f - t * 0.04f + phi * 2.0f));
                float fold2 = phi * 5.0f - 1.1f * Mth.sin(e * 4.0f - t * 0.017f + phi * 2.0f) + seed;
                float veil2 = (float) Math.pow(0.5f + 0.5f * Mth.sin(fold2 - t * 0.009f), 10.0);
                float crown = 1.0f - smoothstep(1.05f, 1.5f, e);
                veil *= crown;
                veil2 *= crown;
                float sheen = 0.75f + 0.25f * Mth.sin(phi * 2.0f + e * 4.0f - t * 0.015f);
                float frontGlow = f.front * gauss((absE - f.reach + 0.12f) / 0.14f);
                float foot = 0.5f * gauss(e / 0.06f);
                float alpha = (0.02f + 0.38f * rim + 0.32f * veil + 0.2f * veil2 + 0.9f * frontGlow + foot)
                        * sheen * c * f.strength * (1.0f + f.flare);
                as[index] = Mth.clamp(alpha, 0.0f, 1.0f);
                float hue = 0.5f + 0.5f * Mth.sin(phi + e * 2.0f + t * 0.01f + seed);
                float r = Mth.lerp(hue, BLUE[0], LAVENDER[0]);
                float g = Mth.lerp(hue, BLUE[1], LAVENDER[1]);
                float b = Mth.lerp(hue, BLUE[2], LAVENDER[2]);
                float veilMix = Mth.clamp(veil + veil2 * 0.6f, 0.0f, 1.0f);
                r = Mth.lerp(veilMix, r, AQUA[0]);
                g = Mth.lerp(veilMix, g, AQUA[1]);
                b = Mth.lerp(veilMix, b, AQUA[2]);
                float white = Mth.clamp(rim * 0.45f + frontGlow + foot + f.flare * 0.5f, 0.0f, 1.0f);
                rs[index] = Mth.lerp(white, r, 1.0f);
                gs[index] = Mth.lerp(white, g, 1.0f);
                bs[index] = Mth.lerp(white, b, 1.0f);
            }
        }
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                int a = i * stride + j;
                int b = (i + 1) * stride + j;
                if (as[a] <= 0.0f && as[a + 1] <= 0.0f && as[b] <= 0.0f && as[b + 1] <= 0.0f) {
                    continue;
                }
                shellVertex(out, m, xs, ys, zs, rs, gs, bs, as, a);
                shellVertex(out, m, xs, ys, zs, rs, gs, bs, as, a + 1);
                shellVertex(out, m, xs, ys, zs, rs, gs, bs, as, b + 1);
                shellVertex(out, m, xs, ys, zs, rs, gs, bs, as, b);
            }
        }
    }

    private static void shellVertex(VertexConsumer out, Matrix4f m, float[] xs, float[] ys, float[] zs, float[] rs,
                                    float[] gs, float[] bs, float[] as, int index) {
        out.addVertex(m, xs[index], ys[index], zs[index]).setColor(rs[index], gs[index], bs[index], as[index]);
    }

    /**
     * Strands of light spiralling from the rune circle to the crown, alternate ones winding the other way so they
     * cross like a weave. While the dome is raised they lead the shell up, a star at each tip; then pulses of light
     * keep climbing them.
     */
    private static void drawStrands(VertexConsumer out, Matrix4f m, Frame f) {
        float alpha = f.strength * (1.0f - f.dissolve) * (1.0f + f.flare * 0.5f);
        if (alpha <= 0.004f) {
            return;
        }
        int points = 28;
        float top = Mth.HALF_PI - 0.04f;
        float lead = Math.min(top, f.reach + 0.2f);
        if (lead <= 0.02f) {
            return;
        }
        float radius = f.radius * 1.006f;
        for (int k = 0; k < STRANDS; k++) {
            float spin = (k % 2 == 0 ? 1.0f : -1.0f);
            float phi0 = k * Mth.TWO_PI / STRANDS + f.now * 0.004f * spin;
            Vec3[] path = new Vec3[points + 1];
            float[] width = new float[points + 1];
            float[] alphas = new float[points + 1];
            float pulse = frac(f.now * 0.012f + k * 0.17f);
            for (int i = 0; i <= points; i++) {
                float s = i / (float) points;
                float e = lead * s;
                float phi = phi0 + spin * 1.7f * (e / top);
                path[i] = onSphere(f, e, phi, radius);
                float along = e / top;
                width[i] = 0.16f;
                alphas[i] = alpha * (0.28f + 0.7f * gauss((along - pulse) / 0.07f)) * smoothstep(0.0f, 0.06f, s + 0.02f);
            }
            ribbon(out, m, path, width, alphas, 1.0f, 1.0f, BLUE[0], BLUE[1], BLUE[2]);
            ribbon(out, m, path, width, alphas, 0.22f, 1.8f, WARM[0], WARM[1], WARM[2]);
            if (f.front > 0.05f && lead < top) {
                star(out, m, path[points], 0.35f, alpha * f.front, WARM);
                billboard(out, m, path[points], 0.6f, 0.4f * alpha * f.front, BLUE[0], BLUE[1], BLUE[2], false, 3, 10);
            }
        }
        if (f.reach >= top) {
            // Where the strands meet: a soft star at the crown.
            Vec3 crown = new Vec3(f.cx, f.cy + radius, f.cz);
            float twinkle = 0.8f + 0.2f * Mth.sin(f.now * 0.2f);
            billboard(out, m, crown, 1.4f, 0.3f * alpha, BLUE[0], BLUE[1], BLUE[2], false, 3, 12);
            star(out, m, crown, 0.6f * twinkle, 0.8f * alpha, WARM);
        }
    }

    /**
     * The circle of runes on the ground at the dome's foot, turning slowly between two fine rings: each glyph a few
     * strokes (an upright, a slash, a chevron, a little ring), the same glyph every frame.
     */
    private static void drawRuneCircle(VertexConsumer out, Matrix4f m, Frame f) {
        float alpha = f.circle * f.strength * (1.0f + f.flare * 0.5f);
        if (alpha <= 0.004f) {
            return;
        }
        float radius = f.radius;
        Vec3 centre = new Vec3(f.cx, f.cy + 0.04, f.cz);
        Vec3 east = new Vec3(1.0, 0.0, 0.0);
        Vec3 south = new Vec3(0.0, 0.0, 1.0);
        int segments = Mth.clamp(Math.round(radius * 10.0f), 48, 200);
        ring(out, m, centre, east, south, radius - 0.38f, 0.05f, 0.7f * alpha, WARM[0], WARM[1], WARM[2], segments);
        ring(out, m, centre, east, south, radius + 0.38f, 0.05f, 0.7f * alpha, WARM[0], WARM[1], WARM[2], segments);
        ring(out, m, centre, east, south, radius, 0.7f, 0.12f * alpha, BLUE[0], BLUE[1], BLUE[2], segments);

        int glyphs = Math.max(12, Math.round(Mth.TWO_PI * radius / 0.6f));
        float turn = f.now * 0.003f;
        for (int k = 0; k < glyphs; k++) {
            float angle = turn + k * Mth.TWO_PI / glyphs;
            Vec3 radial = new Vec3(Mth.cos(angle), 0.0, Mth.sin(angle));
            Vec3 tangent = new Vec3(-Mth.sin(angle), 0.0, Mth.cos(angle));
            Vec3 base = centre.add(radial.scale(radius));
            float glow = alpha * (0.55f + 0.45f * Mth.sin(f.now * 0.08f + k * 1.3f));
            int shape = (int) (hash(k, 21, f.seed) * 4.0f);
            Vec3 low = base.add(radial.scale(-0.22));
            Vec3 high = base.add(radial.scale(0.22));
            Vec3 side = tangent.scale(0.13);
            glyphStrokes(out, m, shape, low, high, side, base, radial, tangent, glow);
        }
    }

    /** One glyph's strokes. An if-chain, not a switch: see the no-enum-switch rule for render code. */
    private static void glyphStrokes(VertexConsumer out, Matrix4f m, int shape, Vec3 low, Vec3 high, Vec3 side,
                                     Vec3 base, Vec3 radial, Vec3 tangent, float alpha) {
        if (shape == 0) {
            flatStroke(out, m, low, high, alpha);
            flatStroke(out, m, base.subtract(side), base.add(side), alpha * 0.8f);
        } else if (shape == 1) {
            flatStroke(out, m, low, high, alpha);
            flatStroke(out, m, high.subtract(side), base.add(side), alpha * 0.8f);
        } else if (shape == 2) {
            flatStroke(out, m, low.subtract(side), high, alpha);
            flatStroke(out, m, high, low.add(side), alpha);
        } else {
            flatStroke(out, m, low, base, alpha);
            ring(out, m, base.add(radial.scale(0.1)), radial, tangent, 0.1f, 0.025f, alpha, WARM[0], WARM[1], WARM[2], 12);
        }
    }

    /** A stroke of light lying flat on the ground, soft across its width. */
    private static void flatStroke(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, float alpha) {
        Vec3 along = b.subtract(a);
        if (along.lengthSqr() < 1.0E-6 || alpha <= 0.004f) {
            return;
        }
        Vec3 across = new Vec3(-along.z, 0.0, along.x).normalize().scale(0.045);
        float r = WARM[0];
        float g = WARM[1];
        float bl = WARM[2];
        vertex(out, m, a.subtract(across), r, g, bl, 0.0f);
        vertex(out, m, b.subtract(across), r, g, bl, 0.0f);
        vertex(out, m, b, r, g, bl, alpha);
        vertex(out, m, a, r, g, bl, alpha);
        vertex(out, m, a, r, g, bl, alpha);
        vertex(out, m, b, r, g, bl, alpha);
        vertex(out, m, b.add(across), r, g, bl, 0.0f);
        vertex(out, m, a.add(across), r, g, bl, 0.0f);
    }

    /** Sparkles breathing off the skin and drifting up and away, twinkling out; flung outward when it breaks. */
    private static void drawSparkles(VertexConsumer out, Matrix4f m, Frame f) {
        float radius = f.radius;
        int count = Mth.clamp(Math.round(radius * 4.0f), 18, 90);
        if (f.burstAge >= 0.0f) {
            float t = f.burstAge / BURST_TICKS;
            for (int k = 0; k < count * 2; k++) {
                float e = hash(k, 31, f.seed) * Mth.HALF_PI;
                float phi = hash(k, 32, f.seed) * Mth.TWO_PI;
                float speed = 0.12f + 0.2f * hash(k, 33, f.seed);
                Vec3 pos = onSphere(f, e, phi, radius + speed * f.burstAge).add(0.0, -0.004 * f.burstAge * f.burstAge, 0.0);
                star(out, m, pos, 0.16f, (1.0f - t) * (0.6f + 0.4f * hash(k, 34, f.seed)), k % 3 == 0 ? WARM : AQUA);
            }
        }
        float alpha = f.strength * (1.0f - f.dissolve);
        if (alpha <= 0.004f) {
            return;
        }
        for (int k = 0; k < count; k++) {
            float life = frac(f.now / (60.0f + 40.0f * hash(k, 41, f.seed)) + hash(k, 42, f.seed));
            float e = 0.05f + hash(k, 43, f.seed) * 1.35f;
            if (e > f.reach) {
                continue;
            }
            float phi = hash(k, 44, f.seed) * Mth.TWO_PI + life * 0.3f;
            Vec3 pos = onSphere(f, e, phi, radius + 0.15f + life * 1.1f).add(0.0, life * 0.9, 0.0);
            float twinkle = 0.4f + 0.6f * (float) Math.pow(0.5f + 0.5f * Mth.sin(f.now * 0.35f + k * 1.9f), 2.0);
            float fade = Mth.sin(life * Mth.PI);
            star(out, m, pos, 0.13f * (0.8f + 0.4f * twinkle), alpha * fade * twinkle, k % 3 == 0 ? AQUA : WARM);
        }
    }

    private static Vec3 onSphere(Frame f, float elevation, float phi, float radius) {
        float ring = Mth.cos(elevation) * radius;
        return new Vec3(f.cx + ring * Mth.cos(phi), f.cy + Mth.sin(elevation) * radius, f.cz + ring * Mth.sin(phi));
    }

    // ---- the outline ---------------------------------------------------------------------------------------------

    private static void drawOutline(VertexConsumer out, Matrix4f m, Frame f) {
        double[][] c = corners(f);
        float strength = f.outlineStrength;
        float bottom = smoothstep(0.0f, 0.42f, f.trace);
        float rise = smoothstep(0.25f, 0.7f, f.trace);
        float top = smoothstep(0.58f, 1.0f, f.trace);
        // Floor and roof close from both corners towards the middle; the uprights climb from the floor.
        for (int[] e : EDGES) {
            Vec3 a = vec(c[e[0]]);
            Vec3 b = vec(c[e[1]]);
            if (e[2] == 1) {
                traceLine(out, m, f, a, a.lerp(b, rise), strength, rise < 1.0f);
            } else {
                float p = e[2] == 0 ? bottom : top;
                Vec3 mid = a.lerp(b, 0.5);
                traceLine(out, m, f, a, a.lerp(mid, p), strength, p < 1.0f);
                traceLine(out, m, f, b, b.lerp(mid, p), strength, p < 1.0f);
            }
        }
        float drawn = smoothstep(0.75f, 1.0f, f.trace) * strength;
        if (drawn <= 0.004f) {
            return;
        }
        for (int n = 0; n < EDGES.length; n++) {
            Vec3 a = vec(c[EDGES[n][0]]);
            Vec3 b = vec(c[EDGES[n][1]]);
            double length = a.distanceTo(b);
            // Beads of light running along the edge.
            for (int k = 0; k < 2; k++) {
                float t = frac(hash(n, k, f.seed) + f.now * (float) (0.12 / Math.max(1.0, length)));
                Vec3 pos = a.lerp(b, t);
                billboard(out, m, pos, 0.35f, 0.35f * drawn, TEAL[0], TEAL[1], TEAL[2], false, 3, 10);
                billboard(out, m, pos, 0.1f, 0.9f * drawn, TEAL_HOT[0], TEAL_HOT[1], TEAL_HOT[2], true, 2, 8);
            }
            // Sparkles shed off the line, drifting up and away.
            int shed = Math.max(2, (int) (length / 3.0));
            for (int k = 0; k < shed; k++) {
                float life = frac(f.now / 45.0f + hash(n, k + 10, f.seed));
                Vec3 drift = randomDirection(n * 131 + k + f.seed).scale(0.6 * life).add(0.0, 0.7 * life, 0.0);
                Vec3 pos = a.lerp(b, hash(n, k + 20, f.seed)).add(drift);
                star(out, m, pos, 0.11f, drawn * Mth.sin(life * Mth.PI), TEAL_HOT);
            }
        }
        // A sigil turning at every corner.
        for (int k = 0; k < c.length; k++) {
            Vec3 pos = vec(c[k]);
            if (pos.lengthSqr() < 1.0E-6) {
                continue;
            }
            billboard(out, m, pos, 0.75f, 0.3f * drawn, TEAL[0], TEAL[1], TEAL[2], false, 3, 12);
            billboard(out, m, pos, 0.14f, 0.85f * drawn, TEAL_HOT[0], TEAL_HOT[1], TEAL_HOT[2], true, 2, 8);
            Vec3[] axes = frame(pos.scale(-1.0).normalize());
            double spin = f.now * 0.06 * (k % 2 == 0 ? 1.0 : -1.0);
            for (int arcIndex = 0; arcIndex < 3; arcIndex++) {
                double from = spin + arcIndex * Math.PI * 2.0 / 3.0;
                arc(out, m, pos, axes[0], axes[1], 0.48f, 0.035f, 0.75f * drawn, TEAL_HOT[0], TEAL_HOT[1], TEAL_HOT[2],
                        8, from, from + 1.25);
            }
            ring(out, m, pos, axes[0], axes[1], 0.3f, 0.025f, 0.45f * drawn, TEAL[0], TEAL[1], TEAL[2], 20);
        }
    }

    /** One edge of the outline from {@code a} to {@code tip}: a wide teal glow, a hot thread, a star at the tip. */
    private static void traceLine(VertexConsumer out, Matrix4f m, Frame f, Vec3 a, Vec3 tip, float strength,
                                  boolean growing) {
        double length = a.distanceTo(tip);
        if (length < 0.02) {
            return;
        }
        Vec3[] path = subdivide(a, tip, length);
        float[] width = new float[path.length];
        float[] alphas = new float[path.length];
        for (int i = 0; i < path.length; i++) {
            // Light breathing along the line, never quite even.
            float wave = 0.7f + 0.3f * Mth.sin((float) (i * 0.9) - f.now * 0.25f);
            alphas[i] = 0.45f * strength * wave;
            width[i] = 0.16f * (0.85f + 0.15f * Mth.sin(i * 1.7f + f.now * 0.11f));
        }
        ribbon(out, m, path, width, alphas, 1.0f, 1.0f, TEAL[0], TEAL[1], TEAL[2]);
        ribbon(out, m, path, width, alphas, 0.2f, 2.0f, TEAL_HOT[0], TEAL_HOT[1], TEAL_HOT[2]);
        if (growing) {
            billboard(out, m, tip, 0.5f, 0.5f * strength, TEAL[0], TEAL[1], TEAL[2], false, 3, 10);
            star(out, m, tip, 0.3f, strength, TEAL_HOT);
        }
    }

    // ---- sound -----------------------------------------------------------------------------------------------------

    /** The point of the dome's skin nearest the listener (the upper half: the dome stands on the ground). */
    private static Vec3 nearestOnDome(CaveInimicumWardBlockEntity ward) {
        Vec3 centre = ward.barrierCentre();
        Vec3 listener = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 d = listener.subtract(centre);
        if (d.y < 0.0) {
            d = new Vec3(d.x, 0.0, d.z);
        }
        if (d.lengthSqr() < 1.0E-4) {
            d = new Vec3(0.0, 1.0, 0.0);
        }
        return centre.add(d.normalize().scale(ward.barrierRadius()));
    }

    private static void playAt(SoundEvent sound, CaveInimicumWardBlockEntity ward, float volume) {
        Vec3 at = nearestOnDome(ward);
        float pitch = 0.95f + 0.1f * (float) Math.random();
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.BLOCKS, volume, pitch,
                SoundInstance.createUnseededRandom(), at.x, at.y, at.z));
    }

    /**
     * The hum of a standing dome, following the listener round its skin, fading in and out with it. Holds its ward
     * weakly: the visual it hangs off is keyed weakly by that ward.
     */
    private static final class HumSound extends AbstractTickableSoundInstance {
        private final WeakReference<CaveInimicumWardBlockEntity> ward;
        private float fade;

        HumSound(CaveInimicumWardBlockEntity ward) {
            super(ModSounds.WARD_BARRIER_HUM.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.ward = new WeakReference<>(ward);
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001f;
            place(ward);
        }

        private void place(CaveInimicumWardBlockEntity w) {
            Vec3 at = nearestOnDome(w);
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            CaveInimicumWardBlockEntity w = ward.get();
            boolean up = w != null && minecraft.level != null && !w.isRemoved() && w.getLevel() == minecraft.level
                    && w.isBarrierVisible(minecraft.level.getGameTime());
            fade = Mth.clamp(fade + (up ? 0.04f : -0.1f), 0.0f, 1.0f);
            if (!up && fade <= 0.0f) {
                stop();
                return;
            }
            volume = Math.max(0.001f, 0.5f * fade);
            if (w != null) {
                place(w);
            }
        }
    }

    // ---- shared ----------------------------------------------------------------------------------------------------

    /** Corner indices of each edge, and its kind: 0 floor, 1 upright, 2 roof. */
    private static final int[][] EDGES = {
            {0, 1, 0}, {1, 2, 0}, {2, 3, 0}, {3, 0, 0},
            {0, 4, 1}, {1, 5, 1}, {2, 6, 1}, {3, 7, 1},
            {4, 5, 2}, {5, 6, 2}, {6, 7, 2}, {7, 4, 2},
    };

    private static double[][] corners(Frame f) {
        return new double[][]{
                {f.x0, f.y0, f.z0}, {f.x1, f.y0, f.z0}, {f.x1, f.y0, f.z1}, {f.x0, f.y0, f.z1},
                {f.x0, f.y1, f.z0}, {f.x1, f.y1, f.z0}, {f.x1, f.y1, f.z1}, {f.x0, f.y1, f.z1},
        };
    }

    /** About a point a block, so strips stay camera-facing along their whole length. */
    private static Vec3[] subdivide(Vec3 a, Vec3 b, double length) {
        int segments = Mth.clamp((int) Math.ceil(length), 1, 48);
        Vec3[] path = new Vec3[segments + 1];
        for (int i = 0; i <= segments; i++) {
            path[i] = a.lerp(b, i / (double) segments);
        }
        return path;
    }

    /** A little four-rayed star facing the camera, white at its heart and {@code colour} along its rays. */
    private static void star(VertexConsumer out, Matrix4f m, Vec3 pos, float size, float alpha, float[] colour) {
        if (alpha <= 0.004f || pos.lengthSqr() < 1.0E-6) {
            return;
        }
        Vec3[] axes = frame(pos.scale(-1.0).normalize());
        Vec3[] rays = {axes[0], axes[1], axes[0].scale(-1.0), axes[1].scale(-1.0)};
        float r = colour[0];
        float g = colour[1];
        float b = colour[2];
        for (int k = 0; k < 4; k++) {
            Vec3 ray = rays[k];
            Vec3 side = rays[(k + 1) % 4];
            vertex(out, m, pos, 1.0f, 1.0f, 1.0f, alpha);
            vertex(out, m, pos.add(side.scale(size * 0.22)), r, g, b, 0.0f);
            vertex(out, m, pos.add(ray.scale(size)), r, g, b, 0.0f);
            vertex(out, m, pos.add(side.scale(-size * 0.22)), r, g, b, 0.0f);
        }
        billboard(out, m, pos, size * 0.6f, alpha * 0.35f, r, g, b, false, 2, 8);
    }

    private static Vec3 vec(double[] p) {
        return new Vec3(p[0], p[1], p[2]);
    }

    private static float gauss(float x) {
        return (float) Math.exp(-x * x);
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static float easeOutCubic(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }

    private static float easeInCubic(float t) {
        return t * t * t;
    }
}
