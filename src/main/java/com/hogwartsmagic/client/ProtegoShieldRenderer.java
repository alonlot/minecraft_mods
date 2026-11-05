package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.support.ProtegoShield;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Draws every Protego shield this client knows of ({@link ProtegoShield#views}) as a bubble of light around its
 * holder: a fresnel shell (faint where you look straight through it, bright at its silhouette) with light rolling
 * down it, two rune bands turning about it and sparks orbiting on its skin.
 *
 * <p>The bubble swells in when cast, flares when it stops a blow (harder on a parry), flickers as it runs out and
 * bursts outward when it shatters. A ranked (player) Protego only guards the way its holder faces, so its back half
 * is dimmer; each extra layer of an NPC shield is one more shell inside the first. Your own shield in first person
 * is only the faint shell: the bands and sparks would cross the crosshair.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class ProtegoShieldRenderer {

    private static final int LAT = 18;
    private static final int LON = 36;
    private static final int BAND_SEGMENTS = 72;
    private static final int MOTES = 9;

    /** Protego Maxima's bubble, matching the barrier it deflects at. */
    private static final float DOME_RADIUS = 4.5f;

    private static final int FADE_TICKS = 10;
    private static final int FLICKER_TICKS = 24;
    private static final int FLARE_TICKS = 8;
    private static final int BURST_TICKS = 10;
    private static final int DROP_TICKS = 6;

    /** Cold blue body, edges burning toward white. */
    private static final float CORE_R = 0.38f;
    private static final float CORE_G = 0.66f;
    private static final float CORE_B = 1.00f;
    private static final float RIM_R = 0.82f;
    private static final float RIM_G = 0.94f;
    private static final float RIM_B = 1.00f;

    /** Bubbles held up by something other than an entity's shield (a Pensieve memory's cast). Client thread only. */
    private static final Map<Object, Anchor> ANCHORS = new IdentityHashMap<>();

    private record Anchor(Vec3 feet, int age, int lifetime, long heldAt) {
    }

    private ProtegoShieldRenderer() {
    }

    /**
     * Keep a free-standing bubble around someone standing at {@code feet}, {@code age} ticks into a cast that lasts
     * {@code lifetime}. Call every tick; it lapses as soon as the calls stop.
     */
    public static void holdAt(Object key, Vec3 feet, int age, int lifetime) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            ANCHORS.put(key, new Anchor(feet, age, lifetime, level.getGameTime()));
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Map<Integer, ProtegoShield.View> views = ProtegoShield.views();
        if (views.isEmpty() && ANCHORS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }

        // The real partial tick: the event's own is the frame length (see RenderLevelStageEvent in Forge 51).
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        long tick = level.getGameTime();
        float now = tick + partial;
        Vec3 cameraPos = event.getCamera().getPosition();
        // Identity: the camera rotation is already on RenderSystem's stack at this stage. Positions are made
        // camera-relative in double precision instead.
        Matrix4f matrix = new Matrix4f();

        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(ModRenderTypes.PROTEGO_LIGHT);

        for (Map.Entry<Integer, ProtegoShield.View> entry : views.entrySet()) {
            ProtegoShield.View view = ProtegoShield.view(entry.getKey(), tick);
            Entity entity = level.getEntity(entry.getKey());
            // Protego Maxima's dome has its own renderer (ProtegoMaximaRenderer).
            if (view == null || view.dome() || !(entity instanceof LivingEntity living)
                    || (minecraft.player != null && living.isInvisibleTo(minecraft.player))) {
                continue;
            }
            drawEntityShield(consumer, matrix, minecraft, living, view, tick, now, partial, cameraPos);
        }

        Iterator<Anchor> anchors = ANCHORS.values().iterator();
        while (anchors.hasNext()) {
            Anchor anchor = anchors.next();
            if (tick - anchor.heldAt() > 2) {
                anchors.remove();
                continue;
            }
            drawAnchor(consumer, matrix, anchor, now, cameraPos);
        }

        buffer.endBatch(ModRenderTypes.PROTEGO_LIGHT);
    }

    /** Stale shields must not survive into the next world. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ProtegoShield.clearViews();
        ANCHORS.clear();
    }

    private static void drawEntityShield(VertexConsumer consumer, Matrix4f matrix, Minecraft minecraft,
                                         LivingEntity living, ProtegoShield.View view, long tick, float now,
                                         float partial, Vec3 cameraPos) {
        float age = now - view.raisedAt();
        float sinceEvent = now - view.eventAt();
        float scale = 0.35f + 0.65f * easeOutBack(smoothstep(0.0f, ProtegoShield.GROW_TICKS, age));
        float strength;
        if (view.up(tick)) {
            float remaining = view.until() - now;
            strength = Mth.clamp(remaining / FADE_TICKS, 0.0f, 1.0f);
            if (remaining < FLICKER_TICKS) {
                strength *= 0.70f + 0.30f * Mth.sin(age * 1.7f);
            }
        } else if (view.event() == ProtegoShield.SHATTERED) {
            // Bursts: thrown outward, flaring bright, gone in half a second.
            float t = sinceEvent / BURST_TICKS;
            if (t >= 1.0f) {
                return;
            }
            scale *= 1.0f + 0.45f * (1.0f - (1.0f - t) * (1.0f - t));
            strength = 1.8f * (1.0f - t) * (1.0f - t);
        } else {
            float t = sinceEvent / DROP_TICKS;
            if (t >= 1.0f) {
                return;
            }
            strength = 1.0f - t;
        }
        if (strength <= 0.004f) {
            return;
        }

        float flare = 0.0f;
        byte event = view.event();
        if (view.up(tick) && sinceEvent < FLARE_TICKS
                && (event == ProtegoShield.HIT || event == ProtegoShield.PARRIED || event == ProtegoShield.RAISED)) {
            float k = 1.0f - sinceEvent / FLARE_TICKS;
            flare = k * k * (event == ProtegoShield.PARRIED ? 1.6f : event == ProtegoShield.HIT ? 1.0f : 0.7f);
        }
        scale *= 1.0f + 0.05f * flare;

        float height = living.getBbHeight();
        boolean dome = view.dome();
        float radius = dome ? DOME_RADIUS : Math.max(0.85f, Math.max(height, living.getBbWidth()) * 0.5f + 0.38f);
        Vec3 center = new Vec3(
                Mth.lerp(partial, living.xo, living.getX()),
                Mth.lerp(partial, living.yo, living.getY()) + height * (dome ? 0.55 : 0.5),
                Mth.lerp(partial, living.zo, living.getZ())
        ).subtract(cameraPos);

        boolean ownFirstPerson = living == minecraft.getCameraEntity()
                && minecraft.options.getCameraType().isFirstPerson();
        if (ownFirstPerson) {
            strength *= 0.35f;
        }

        Vec3 front = null;
        if (view.ranked()) {
            Vec3 look = living.getViewVector(partial);
            Vec3 flat = new Vec3(look.x, 0.0, look.z);
            front = flat.lengthSqr() < 1.0E-6 ? null : flat.normalize();
        }

        int shells = dome ? 1 : 1 + Math.min(Math.max(view.layers(), 0), 2);
        drawBubble(consumer, matrix, center, radius * scale, strength, flare, age, front, shells, !ownFirstPerson);
    }

    private static void drawAnchor(VertexConsumer consumer, Matrix4f matrix, Anchor anchor, float now, Vec3 cameraPos) {
        float age = anchor.age() + (now - anchor.heldAt());
        float scale = 0.35f + 0.65f * easeOutBack(smoothstep(0.0f, ProtegoShield.GROW_TICKS, age));
        float strength = Mth.clamp((anchor.lifetime() - age) / FADE_TICKS, 0.0f, 1.0f);
        float k = Math.max(0.0f, 1.0f - age / FLARE_TICKS);
        // A memory's caster is a bare-handed stand-in, so the bubble is sized to read from across a hall.
        Vec3 center = anchor.feet().add(0.0, 1.1, 0.0).subtract(cameraPos);
        drawBubble(consumer, matrix, center, 1.6f * scale, strength, 0.7f * k * k, age, null, 1, true);
    }

    private static void drawBubble(VertexConsumer consumer, Matrix4f matrix, Vec3 center, float radius,
                                   float strength, float flare, float age, @Nullable Vec3 front, int shells,
                                   boolean details) {
        for (int shell = 0; shell < shells; shell++) {
            float shellStrength = strength * (shell == 0 ? 1.0f : 0.55f / shell);
            drawShell(consumer, matrix, center, radius * (1.0f - 0.08f * shell), shellStrength, flare, age, front, shell);
        }
        if (details) {
            drawBand(consumer, matrix, center, radius * 1.02f, strength, flare, age, 0.95f, 0.035f, 0.0f);
            drawBand(consumer, matrix, center, radius * 1.03f, strength, flare, age, -0.55f, -0.050f, 1.9f);
            drawMotes(consumer, matrix, center, radius * 1.05f, strength, age);
        }
    }

    /** The bubble's skin, its light computed per vertex: fresnel rim, rolling bands, a slow swirl, the guarded side. */
    private static void drawShell(VertexConsumer consumer, Matrix4f matrix, Vec3 center, float radius,
                                  float strength, float flare, float age, @Nullable Vec3 front, int shell) {
        int stride = LON + 1;
        float[] xs = new float[(LAT + 1) * stride];
        float[] ys = new float[xs.length];
        float[] zs = new float[xs.length];
        float[] alphas = new float[xs.length];
        float[] mixes = new float[xs.length];
        // Each shell's light rolls down a little behind the one outside it.
        float wavePhase = ((age * 0.028f) + shell * 0.33f) % 1.0f;

        for (int i = 0; i <= LAT; i++) {
            float theta = Mth.PI * i / LAT;
            float sinT = Mth.sin(theta);
            float cosT = Mth.cos(theta);
            float lat = i / (float) LAT;
            float wave = 0.30f * (float) Math.exp(-Math.pow((lat - (wavePhase * 1.3f - 0.15f)) / 0.09f, 2.0));
            for (int j = 0; j <= LON; j++) {
                float phi = Mth.TWO_PI * j / LON;
                float nx = sinT * Mth.cos(phi);
                float ny = cosT;
                float nz = sinT * Mth.sin(phi);
                double x = center.x + nx * radius;
                double y = center.y + ny * radius;
                double z = center.z + nz * radius;

                // The camera is the origin, so the view ray to this point is the point itself.
                double length = Math.sqrt(x * x + y * y + z * z);
                float facing = length < 1.0E-4 ? 1.0f : (float) Math.abs((x * nx + y * ny + z * nz) / length);
                float rim = (1.0f - facing) * (1.0f - facing) * (1.0f - facing);
                float swirl = 0.055f * (0.5f + 0.5f * Mth.sin(phi * 3.0f + theta * 5.0f - age * 0.12f));
                float guard = front == null ? 1.0f
                        : 0.40f + 0.60f * smoothstep(-0.35f, 0.65f, (float) (nx * front.x + nz * front.z));

                int index = i * stride + j;
                xs[index] = (float) x;
                ys[index] = (float) y;
                zs[index] = (float) z;
                alphas[index] = Mth.clamp((0.045f + rim * 0.90f + wave + swirl) * guard * strength * (1.0f + flare * 1.5f),
                        0.0f, 1.0f);
                mixes[index] = Mth.clamp(rim + flare * 0.5f, 0.0f, 1.0f);
            }
        }

        for (int i = 0; i < LAT; i++) {
            for (int j = 0; j < LON; j++) {
                int a = i * stride + j;
                int b = (i + 1) * stride + j;
                shellVertex(consumer, matrix, xs, ys, zs, alphas, mixes, a);
                shellVertex(consumer, matrix, xs, ys, zs, alphas, mixes, b);
                shellVertex(consumer, matrix, xs, ys, zs, alphas, mixes, b + 1);
                shellVertex(consumer, matrix, xs, ys, zs, alphas, mixes, a + 1);
            }
        }
    }

    private static void shellVertex(VertexConsumer consumer, Matrix4f matrix, float[] xs, float[] ys, float[] zs,
                                    float[] alphas, float[] mixes, int index) {
        float mix = mixes[index];
        consumer.addVertex(matrix, xs[index], ys[index], zs[index])
                .setColor(Mth.lerp(mix, CORE_R, RIM_R), Mth.lerp(mix, CORE_G, RIM_G), Mth.lerp(mix, CORE_B, RIM_B),
                        alphas[index]);
    }

    /**
     * A band of rune-like dashes on a great circle, its axis tipped by {@code tilt} and wheeling about the vertical
     * at {@code spin} radians a tick, the dashes themselves travelling round the band.
     */
    private static void drawBand(VertexConsumer consumer, Matrix4f matrix, Vec3 center, float radius,
                                 float strength, float flare, float age, float tilt, float spin, float offset) {
        float yaw = age * spin + offset;
        Vec3 axis = new Vec3(Mth.sin(tilt) * Mth.cos(yaw), Mth.cos(tilt), Mth.sin(tilt) * Mth.sin(yaw));
        Vec3 u = axis.cross(new Vec3(0.0, 0.0, 1.0));
        if (u.lengthSqr() < 1.0E-4) {
            u = axis.cross(new Vec3(1.0, 0.0, 0.0));
        }
        u = u.normalize();
        Vec3 v = axis.cross(u).normalize();
        double halfWidth = 0.045;
        float roll = age * 0.03f * Math.signum(spin);

        Vec3 prevCenter = null;
        Vec3 prevLow = null;
        Vec3 prevHigh = null;
        float prevAlpha = 0.0f;
        for (int seg = 0; seg <= BAND_SEGMENTS; seg++) {
            float s = Mth.TWO_PI * seg / BAND_SEGMENTS;
            Vec3 direction = u.scale(Mth.cos(s)).add(v.scale(Mth.sin(s)));
            Vec3 mid = center.add(direction.scale(radius));
            Vec3 low = center.add(direction.add(axis.scale(-halfWidth)).normalize().scale(radius));
            Vec3 high = center.add(direction.add(axis.scale(halfWidth)).normalize().scale(radius));
            float glyph = (float) Math.pow(Math.max(0.0f, Mth.sin(s * 9.0f + roll * 9.0f)), 3.0);
            float alpha = Mth.clamp((0.10f + 0.55f * glyph) * strength * (1.0f + flare), 0.0f, 1.0f);
            if (prevCenter != null) {
                quad(consumer, matrix, prevLow, 0.0f, low, 0.0f, mid, alpha, prevCenter, prevAlpha, 0.6f);
                quad(consumer, matrix, prevCenter, prevAlpha, mid, alpha, high, 0.0f, prevHigh, 0.0f, 0.6f);
            }
            prevCenter = mid;
            prevLow = low;
            prevHigh = high;
            prevAlpha = alpha;
        }
    }

    /** Sparks drifting over the bubble's skin, each a little four-rayed star that twinkles. */
    private static void drawMotes(VertexConsumer consumer, Matrix4f matrix, Vec3 center, float radius,
                                  float strength, float age) {
        float size = 0.07f * Mth.sqrt(radius / 1.3f);
        for (int m = 0; m < MOTES; m++) {
            float theta = (float) Math.acos(1.0 - 2.0 * (m + 0.5) / MOTES) + 0.25f * Mth.sin(age * 0.05f + m);
            float phi = m * 2.39996f + age * 0.03f * (1.0f + (m % 3) * 0.4f);
            Vec3 direction = new Vec3(Mth.sin(theta) * Mth.cos(phi), Mth.cos(theta), Mth.sin(theta) * Mth.sin(phi));
            Vec3 pos = center.add(direction.scale(radius));
            float twinkle = 0.25f + 0.75f * (float) Math.pow(0.5f + 0.5f * Mth.sin(age * 0.35f + m * 1.9f), 2.0);
            drawStar(consumer, matrix, pos, size * (0.8f + 0.4f * twinkle), Mth.clamp(twinkle * strength, 0.0f, 1.0f));
        }
    }

    private static void drawStar(VertexConsumer consumer, Matrix4f matrix, Vec3 pos, float size, float alpha) {
        // Camera-facing: the camera is the origin, so the direction towards it is -pos.
        Vec3 normal = pos.scale(-1.0);
        if (normal.lengthSqr() < 1.0E-6 || alpha <= 0.004f) {
            return;
        }
        normal = normal.normalize();
        Vec3 right = normal.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() < 1.0E-4) {
            right = normal.cross(new Vec3(1.0, 0.0, 0.0));
        }
        right = right.normalize();
        Vec3 up = right.cross(normal).normalize();
        Vec3[] rays = {right, up, right.scale(-1.0), up.scale(-1.0)};
        for (int r = 0; r < 4; r++) {
            Vec3 ray = rays[r];
            Vec3 side = rays[(r + 1) % 4];
            quad(consumer, matrix,
                    pos, alpha,
                    pos.add(side.scale(size * 0.22)), 0.0f,
                    pos.add(ray.scale(size)), 0.0f,
                    pos.add(side.scale(-size * 0.22)), 0.0f, 1.0f);
        }
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             Vec3 p0, float a0, Vec3 p1, float a1, Vec3 p2, float a2, Vec3 p3, float a3, float white) {
        vertex(consumer, matrix, p0, a0, white);
        vertex(consumer, matrix, p1, a1, white);
        vertex(consumer, matrix, p2, a2, white);
        vertex(consumer, matrix, p3, a3, white);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 pos, float alpha, float white) {
        consumer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(Mth.lerp(white, CORE_R, 1.0f), Mth.lerp(white, CORE_G, 1.0f), Mth.lerp(white, CORE_B, 1.0f), alpha);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float u = t - 1.0f;
        return 1.0f + c3 * u * u * u + c1 * u * u;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
