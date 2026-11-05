package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.hogwartsmagic.client.SpellFxGeometry.*;

/**
 * A ribbon of light left in the air by the wand tip while it moves through a cast (each spell's gesture, and the
 * Killing Curse's windup and strike), and by a wand knocked flying by Expelliarmus, in the spell's own colours. The tip is sampled every frame and the last few
 * ticks of samples are drawn, fading with age, with a glint at the tip itself. Not drawn for your own first-person
 * view: the held wand doesn't move through the world there, so a world-space trail would only smear.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class WandTrailRenderer {
    private static final float TRAIL_TICKS = 5.0f;
    private static final int MAX_SAMPLES = 64;

    private static final class Trail {
        final ArrayDeque<Vec3> points = new ArrayDeque<>();
        final ArrayDeque<Double> times = new ArrayDeque<>();
        BeamStyle style;
        double lastSeen;
    }

    private static final Map<Integer, Trail> TRAILS = new HashMap<>();

    private WandTrailRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (TRAILS.isEmpty() && CastMotions.isEmpty() && AvadaChargeClientState.isEmpty() && !HitReactions.hasFlights()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        double now = AvadaChargeClientState.now(partial);
        sample(minecraft, partial, now);

        Vec3 camera = event.getCamera().getPosition();
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer out = buffer.getBuffer(ModRenderTypes.AVADA_LIGHT);
        Iterator<Trail> it = TRAILS.values().iterator();
        while (it.hasNext()) {
            Trail trail = it.next();
            while (!trail.times.isEmpty() && now - trail.times.peekFirst() > TRAIL_TICKS) {
                trail.times.pollFirst();
                trail.points.pollFirst();
            }
            if (trail.points.size() < 2) {
                if (now - trail.lastSeen > TRAIL_TICKS) {
                    it.remove();
                }
                continue;
            }
            draw(out, m, trail, now, camera);
        }
        buffer.endBatch(ModRenderTypes.AVADA_LIGHT);
    }

    /** Push this frame's wand tip for everyone mid-gesture. */
    private static void sample(Minecraft minecraft, float partial, double now) {
        for (Map.Entry<Integer, CastMotions.Cast> entry : CastMotions.all()) {
            Entity entity = minecraft.level.getEntity(entry.getKey());
            CastMotions.Cast cast = entry.getValue();
            if (!(entity instanceof LivingEntity caster) || AvadaChargePose.isOwnFirstPerson(caster)
                    || cast.over(now) || hidden(minecraft, caster)) {
                continue;
            }
            push(entity.getId(), BeamStyles.of(cast.spell()), CastMotions.wandTip(caster, cast, partial, false), now);
        }
        for (AvadaChargeClientState.Charge charge : AvadaChargeClientState.all()) {
            Entity entity = minecraft.level.getEntity(charge.entityId);
            if (!(entity instanceof LivingEntity caster) || AvadaChargePose.isOwnFirstPerson(caster)
                    || hidden(minecraft, caster)) {
                continue;
            }
            push(entity.getId() ^ 0x40000000, BeamStyles.of(SpellType.AVADA_KEDAVRA),
                    AvadaChargePose.wandTip(caster, charge, partial, false), now);
        }
        // A wand knocked flying (Expelliarmus) trails the spell's light as it goes.
        for (Map.Entry<Integer, HitReactions.Flight> entry : HitReactions.flights(now)) {
            Entity entity = minecraft.level.getEntity(entry.getKey());
            if (entity == null || entity.isRemoved() || entity.onGround()) {
                continue;
            }
            Vec3 at = entity.getPosition(partial).add(0.0, 0.25, 0.0);
            push(entity.getId() ^ 0x20000000, BeamStyles.of(entry.getValue().spell), at, now);
        }
    }

    /** An invisible (cloaked) caster leaves no trail. */
    private static boolean hidden(Minecraft minecraft, Entity caster) {
        return minecraft.player != null && caster.isInvisibleTo(minecraft.player);
    }

    private static void push(int key, BeamStyle style, Vec3 tip, double now) {
        if (style == null) {
            return;
        }
        Trail trail = TRAILS.computeIfAbsent(key, k -> new Trail());
        trail.style = style;
        trail.lastSeen = now;
        Vec3 last = trail.points.peekLast();
        if (last != null && last.distanceToSqr(tip) < 1.0E-6) {
            return;
        }
        trail.points.addLast(tip);
        trail.times.addLast(now);
        while (trail.points.size() > MAX_SAMPLES) {
            trail.points.pollFirst();
            trail.times.pollFirst();
        }
    }

    private static void draw(VertexConsumer out, Matrix4f m, Trail trail, double now, Vec3 camera) {
        BeamStyle s = trail.style;
        int n = trail.points.size();
        Vec3[] path = new Vec3[n];
        float[] alpha = new float[n];
        float[] width = new float[n];
        List<Double> times = new ArrayList<>(trail.times);
        int i = 0;
        for (Vec3 point : trail.points) {
            float age = (float) (now - times.get(i)) / TRAIL_TICKS;
            float life = Math.max(0.0f, 1.0f - age);
            path[i] = point.subtract(camera);
            alpha[i] = (float) Math.pow(life, 1.5);
            width[i] = 0.012f + 0.03f * life;
            i++;
        }
        ribbon(out, m, path, width, alpha, 3.0f, 0.3f, s.glowR, s.glowG, s.glowB);
        ribbon(out, m, path, width, alpha, 1.0f, 0.9f, s.coreR, s.coreG, s.coreB);
        // A glint at the tip while it is still moving.
        if (now - trail.lastSeen < 0.5) {
            billboard(out, m, path[n - 1], 0.18f, 0.6f, s.glowR, s.glowG, s.glowB, false, 3, 12);
            billboard(out, m, path[n - 1], 0.06f, 0.95f, s.coreR, s.coreG, s.coreB, true, 3, 12);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TRAILS.clear();
    }
}
