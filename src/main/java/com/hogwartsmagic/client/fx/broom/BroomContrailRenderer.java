package com.hogwartsmagic.client.fx.broom;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.client.SpellFxGeometry;
import com.hogwartsmagic.entity.BroomEntity;
import com.hogwartsmagic.fx.FxConfig;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A faint ribbon of light off the bristles of every broom in view that is flying fast, fading as it ages: a
 * pale streak behind a Training broom, cold blue behind a Nimbus, a warm gold behind a Firebolt. It thickens and
 * brightens with the broom's speed, so a cruise leaves nothing and a boost leaves a long tail. Your own broom
 * draws one only in third person (from the saddle the bristles are behind your back). Off with
 * {@code broomSpeedFx}. Drawn the way {@code WandTrailRenderer} draws a wand's trail.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class BroomContrailRenderer {
    /** How long a point of the trail lasts, in ticks. */
    private static final float TRAIL_TICKS = 16.0f;
    /** Samples closer together in time than this (ticks) are not kept. */
    private static final double SAMPLE_GAP = 0.4;
    private static final int MAX_SAMPLES = 56;
    private static final int MAX_TRAILS = 24;
    private static final double MAX_DISTANCE_SQR = 128.0 * 128.0;
    /** A broom shows a trail from this speed fraction up. */
    private static final float SHOWS_FROM = 0.12f;
    /** The bristles sit this far behind the broom's centre. */
    private static final double BRISTLE_BACK = 1.3;

    private record Sample(Vec3 pos, double time, float strength) {
    }

    private static final class Trail {
        final ArrayDeque<Sample> samples = new ArrayDeque<>();
        float smooth;
        int type;
        boolean seen;
    }

    private static final Map<Integer, Trail> TRAILS = new HashMap<>();

    private BroomContrailRenderer() {
    }

    /** Once a tick: how fast each broom in view is going, smoothed. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        if (!FxConfig.get(FxConfig.BROOM_SPEED_FX)) {
            TRAILS.clear();
            return;
        }
        for (Trail trail : TRAILS.values()) {
            trail.seen = false;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof BroomEntity broom)) {
                continue;
            }
            float target = BroomSpeedFx.fraction(broom, BroomSpeedFx.movedLastTick(broom));
            Trail trail = TRAILS.get(broom.getId());
            if (trail == null) {
                if (target < SHOWS_FROM || TRAILS.size() >= MAX_TRAILS) {
                    continue;
                }
                trail = new Trail();
                TRAILS.put(broom.getId(), trail);
            }
            trail.seen = true;
            trail.type = broom.getBroomType();
            trail.smooth += (target - trail.smooth) * (target > trail.smooth ? 0.2f : 0.1f);
        }
        // A broom that is gone stops feeding its trail; the points already laid fade on their own.
        for (Trail trail : TRAILS.values()) {
            if (!trail.seen) {
                trail.smooth = 0.0f;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || TRAILS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        double now = minecraft.level.getGameTime() + partial;
        Vec3 camera = event.getCamera().getPosition();
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();

        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer out = null;
        Iterator<Map.Entry<Integer, Trail>> it = TRAILS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Trail> entry = it.next();
            Trail trail = entry.getValue();
            Entity entity = minecraft.level.getEntity(entry.getKey());
            Vec3 tip = null;
            if (entity instanceof BroomEntity broom && trail.seen && trail.smooth >= SHOWS_FROM
                    && broom.distanceToSqr(camera) < MAX_DISTANCE_SQR
                    && !(firstPerson && minecraft.player != null && broom.hasPassenger(minecraft.player))) {
                tip = bristles(broom, partial);
                Sample last = trail.samples.peekLast();
                if (last == null || now - last.time() >= SAMPLE_GAP) {
                    trail.samples.addLast(new Sample(tip, now, trail.smooth));
                    if (trail.samples.size() > MAX_SAMPLES) {
                        trail.samples.pollFirst();
                    }
                }
            }
            while (!trail.samples.isEmpty() && now - trail.samples.peekFirst().time() > TRAIL_TICKS) {
                trail.samples.pollFirst();
            }
            if (trail.samples.isEmpty() && !trail.seen) {
                it.remove();
                continue;
            }
            if (trail.samples.isEmpty() || (trail.samples.size() < 2 && tip == null)) {
                continue;
            }
            if (out == null) {
                out = buffer.getBuffer(ModRenderTypes.AVADA_LIGHT);
            }
            draw(out, m, trail, tip, now, camera);
        }
        if (out != null) {
            buffer.endBatch(ModRenderTypes.AVADA_LIGHT);
        }
    }

    /** The end of the twigs: behind the broom along its heading and pitch. */
    private static Vec3 bristles(BroomEntity broom, float partial) {
        float yaw = Mth.lerp(partial, broom.yRotO, broom.getYRot()) * ((float) Math.PI / 180.0f);
        float pitch = Mth.lerp(partial, broom.xRotO, broom.getXRot()) * ((float) Math.PI / 180.0f);
        double cp = Math.cos(pitch);
        // Minecraft's look vector for (yaw, pitch); the bristles are the other way.
        Vec3 forward = new Vec3(-Math.sin(yaw) * cp, -Math.sin(pitch), Math.cos(yaw) * cp);
        return broom.getPosition(partial).add(0.0, 0.1, 0.0).subtract(forward.scale(BRISTLE_BACK));
    }

    private static void draw(VertexConsumer out, Matrix4f m, Trail trail, Vec3 liveTip, double now, Vec3 camera) {
        int n = trail.samples.size() + (liveTip != null ? 1 : 0);
        if (n < 2) {
            return;
        }
        Vec3[] path = new Vec3[n];
        float[] alpha = new float[n];
        float[] width = new float[n];
        int i = 0;
        for (Sample sample : trail.samples) {
            float life = Math.max(0.0f, 1.0f - (float) (now - sample.time()) / TRAIL_TICKS);
            path[i] = sample.pos().subtract(camera);
            alpha[i] = sample.strength() * (float) Math.pow(life, 1.6);
            width[i] = (0.015f + 0.05f * sample.strength()) * (0.35f + 0.65f * life);
            i++;
        }
        if (liveTip != null) {
            path[i] = liveTip.subtract(camera);
            alpha[i] = trail.smooth;
            width[i] = 0.015f + 0.05f * trail.smooth;
        }
        float r;
        float g;
        float b;
        if (trail.type == 2) {
            r = 1.0f; g = 0.72f; b = 0.32f;
        } else if (trail.type == 1) {
            r = 0.55f; g = 0.78f; b = 1.0f;
        } else {
            r = 0.88f; g = 0.92f; b = 1.0f;
        }
        // A wide soft glow and a thinner brighter core, both kept faint.
        SpellFxGeometry.ribbon(out, m, path, width, alpha, 3.0f, 0.16f, r, g, b);
        SpellFxGeometry.ribbon(out, m, path, width, alpha, 1.0f, 0.34f, 0.5f + 0.5f * r, 0.5f + 0.5f * g, 0.5f + 0.5f * b);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TRAILS.clear();
    }
}
