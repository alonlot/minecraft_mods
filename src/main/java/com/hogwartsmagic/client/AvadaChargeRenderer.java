package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.mind.AvadaChargeFx;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The Killing Curse gathering, drawn by hand rather than with particles.
 *
 * <p>Two passes. First the dark: a shroud of near-black smoke coiling in around the caster, a pool of shadow
 * spreading under their feet and a hole of darkness behind the wand tip, drawn with ordinary alpha so it really
 * darkens the world behind it. Then the light, additive on top of that dark: threads of sickly green drawn out of
 * the air and spiralling into the wand, faster and more of them as the charge builds; jagged arcs snapping off
 * the tip and crawling up the caster's arm; and the tip itself, a hard white-green core in a green halo that
 * flickers like something barely held. On the cast the core flares and the shroud is blown outward; if the curse
 * is broken the core implodes, a burst of black goes off and the threads are flung away.
 *
 * <p>Everything is camera-relative in double precision, drawn with an identity matrix (this stage comes after the
 * camera rotation is pushed), and lerped with the true partial tick rather than the event's frame delta.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class AvadaChargeRenderer {
    // Sickly killing-curse green, and the white-hot green its cores burn out to.
    private static final float GREEN_R = 0.22f;
    private static final float GREEN_G = 1.00f;
    private static final float GREEN_B = 0.34f;
    private static final float HOT_R = 0.80f;
    private static final float HOT_G = 1.00f;
    private static final float HOT_B = 0.82f;
    // The shroud: black with the faintest green in it, so it reads as cursed smoke and not a hole in the world.
    private static final float SMOKE_R = 0.006f;
    private static final float SMOKE_G = 0.030f;
    private static final float SMOKE_B = 0.012f;

    private static final int SHROUD_PUFFS = 16;
    private static final int MAX_WISPS = 12;
    private static final int WISP_SEGMENTS = 12;
    private static final int ARC_SEGMENTS = 8;
    private static final int DISC_RINGS = 5;
    private static final int DISC_SEGMENTS = 16;

    private AvadaChargeRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || AvadaChargeClientState.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        double now = AvadaChargeClientState.now(partial);
        AvadaChargeClientState.prune(now);
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f matrix = new Matrix4f();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        VertexConsumer dark = buffer.getBuffer(ModRenderTypes.AVADA_SHADOW);
        boolean drewDark = false;
        for (AvadaChargeClientState.Charge charge : AvadaChargeClientState.all()) {
            LivingEntity caster = caster(minecraft, charge);
            if (caster == null) {
                continue;
            }
            Scene scene = new Scene(caster, charge, now, partial, camera);
            drawShroud(dark, matrix, scene);
            drewDark = true;
        }
        if (drewDark) {
            buffer.endBatch(ModRenderTypes.AVADA_SHADOW);
        }

        VertexConsumer light = buffer.getBuffer(ModRenderTypes.AVADA_LIGHT);
        boolean drewLight = false;
        for (AvadaChargeClientState.Charge charge : AvadaChargeClientState.all()) {
            LivingEntity caster = caster(minecraft, charge);
            if (caster == null) {
                continue;
            }
            Scene scene = new Scene(caster, charge, now, partial, camera);
            drawWisps(light, matrix, scene);
            drawArcs(light, matrix, scene);
            drawCore(light, matrix, scene);
            drewLight = true;
        }
        if (drewLight) {
            buffer.endBatch(ModRenderTypes.AVADA_LIGHT);
        }
    }

    private static LivingEntity caster(Minecraft minecraft, AvadaChargeClientState.Charge charge) {
        Entity entity = minecraft.level.getEntity(charge.entityId);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /** Everything one charge's drawing needs this frame, worked out once. */
    private static final class Scene {
        final AvadaChargeClientState.Charge charge;
        final boolean firstPerson;
        /** Camera-relative. */
        final Vec3 tip;
        final Vec3 feet;
        final Vec3 shoulder;
        final float height;
        /** 0 to 1 through the windup, frozen when it ends. */
        final float c;
        /** 0 to 1 through the aftermath of the ending; 0 while still gathering. */
        final float after;
        final byte phase;
        final double age;
        /** Size of everything near the tip: your own wand is half a block from your eye. */
        final float near;

        Scene(LivingEntity caster, AvadaChargeClientState.Charge charge, double now, float partial, Vec3 camera) {
            this.charge = charge;
            this.firstPerson = AvadaChargePose.isOwnFirstPerson(caster);
            this.tip = AvadaChargePose.wandTip(caster, charge, partial, firstPerson).subtract(camera);
            double x = Mth.lerp(partial, caster.xo, caster.getX());
            double y = Mth.lerp(partial, caster.yo, caster.getY());
            double z = Mth.lerp(partial, caster.zo, caster.getZ());
            this.feet = new Vec3(x, y, z).subtract(camera);
            this.height = caster.getBbHeight();
            this.shoulder = feet.add(0.0, height * 0.72, 0.0);
            this.c = charge.charge(now);
            this.after = charge.aftermath(now);
            this.phase = charge.ended() ? charge.endPhase() : -1;
            this.age = charge.elapsed(now);
            this.near = firstPerson ? 0.45f : 1.0f;
        }

        boolean gathering() {
            return phase < 0;
        }

        /** The dark gathers fast: most of it is there by a third of the windup. */
        float gloom() {
            return AvadaChargePose.smoothstep(0.0f, 0.35f, c);
        }
    }

    // ---- the dark ----------------------------------------------------------------------------------------------

    private static void drawShroud(VertexConsumer out, Matrix4f m, Scene s) {
        float gloom = s.gloom();
        float fade = 1.0f;
        float blast = 0.0f;
        if (!s.gathering()) {
            fade = (1.0f - s.after) * (1.0f - s.after);
            // Fired: blown out by the cast. Broken: thrown off harder by the collapse. Dropped: just thins.
            blast = s.phase == AvadaChargeFx.FIRED ? 2.4f * s.after
                    : s.phase == AvadaChargeFx.BROKEN ? 3.4f * (float) Math.sqrt(s.after) : 0.4f * s.after;
        }
        float strength = gloom * fade;
        if (strength <= 0.004f) {
            return;
        }

        // Pool of shadow under the feet, tightening as the dark draws in.
        float poolRadius = (2.6f - 0.7f * s.c) * (1.0f + blast * 0.5f);
        groundDisc(out, m, s.feet.add(0.0, 0.03, 0.0), poolRadius, 0.55f * strength);

        // The shroud: puffs coiling around the body, closing in and speeding up as the charge builds.
        for (int i = 0; i < SHROUD_PUFFS; i++) {
            float h0 = hash(s.charge.seed, i, 1);
            float h1 = hash(s.charge.seed, i, 2);
            float h2 = hash(s.charge.seed, i, 3);
            double angle = i * 2.39996 + s.age * (0.035 + 0.07 * s.c) * (h0 < 0.5f ? 1.0 : -1.0);
            double radius = (0.95 + 0.75 * h1) * (1.0 - 0.42 * s.c) + blast;
            double lift = s.height * (0.08 + 0.95 * h2) + 0.18 * Math.sin(s.age * 0.06 + i) + blast * 0.25;
            Vec3 centre = s.feet.add(Math.cos(angle) * radius, lift, Math.sin(angle) * radius);
            float size = (0.55f + 0.45f * h0) * (1.0f + 0.12f * Mth.sin((float) s.age * 0.13f + i))
                    * (1.0f + blast * 0.4f);
            billboard(out, m, centre, size, 0.42f * strength, SMOKE_R, SMOKE_G, SMOKE_B, false);
        }

        // A hole of darkness behind the tip for the green to burn against.
        if (!s.firstPerson) {
            billboard(out, m, s.tip, (0.35f + 0.75f * s.c) * (1.0f + blast * 0.3f), 0.6f * strength,
                    SMOKE_R, SMOKE_G, SMOKE_B, false);
        }

        // Broken: a burst of black where the curse was.
        if (s.phase == AvadaChargeFx.BROKEN) {
            float burst = AvadaChargePose.smoothstep(0.0f, 0.5f, s.after);
            billboard(out, m, s.tip, (0.2f + 1.9f * burst) * s.near, 0.75f * (1.0f - s.after) * Math.max(0.4f, gloom),
                    SMOKE_R, SMOKE_G, SMOKE_B, false);
        }
    }

    // ---- the light ---------------------------------------------------------------------------------------------

    /**
     * Threads of green drawn out of the air into the wand. Each runs a spiral from a few blocks out to the tip and
     * is reborn somewhere new; the run speeds up through the windup (distance is the integral of a speed that
     * ramps with the charge, so nothing jumps), and more threads join as the charge builds.
     */
    private static void drawWisps(VertexConsumer out, Matrix4f m, Scene s) {
        if (s.phase == AvadaChargeFx.FIRED || s.phase == AvadaChargeFx.DROPPED && s.after > 0.6f) {
            return;
        }
        boolean flung = s.phase == AvadaChargeFx.BROKEN;
        float fade = s.gathering() ? 1.0f : 1.0f - s.after;
        int count = Math.min(MAX_WISPS, 3 + Math.round(9.0f * s.c));
        double travelled = travel(s.gathering() ? s.age : s.charge.duration * (double) s.c, s.charge.duration);
        Vec3[] path = new Vec3[WISP_SEGMENTS + 1];
        float[] alpha = new float[WISP_SEGMENTS + 1];
        float[] width = new float[WISP_SEGMENTS + 1];

        for (int k = 0; k < count; k++) {
            double run = travelled + k * 0.6180339;
            int cycle = (int) Math.floor(run);
            float u = (float) (run - cycle);
            int seed = s.charge.seed + k * 7919 + cycle * 104729;
            Vec3 dir = randomDirection(seed);
            Vec3 axis = randomDirection(seed + 31).cross(dir);
            axis = axis.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : axis.normalize();
            double startRadius = (2.3 + 1.1 * hash(seed, 5, 0)) * s.near;
            double swirl = (2.2 + 1.6 * hash(seed, 6, 0)) * (hash(seed, 7, 0) < 0.5f ? 1.0 : -1.0);
            float join = k < 3 ? 1.0f : Mth.clamp(s.c * 9.0f - (k - 3), 0.0f, 1.0f);
            float strength = fade * join * (0.55f + 0.45f * s.c);
            if (strength <= 0.01f) {
                continue;
            }
            for (int i = 0; i <= WISP_SEGMENTS; i++) {
                float tail = i / (float) WISP_SEGMENTS;
                // Head at u, trailing back along its own path. Flung threads run the spiral backwards, out of
                // the tip, their tails still in it.
                float p = flung ? Mth.clamp(1.0f - s.after * 1.6f, 0.0f, 1.0f) + tail * 0.25f
                        : u - tail * 0.30f;
                p = Mth.clamp(p, 0.0f, 1.0f);
                double radius = startRadius * Math.pow(1.0 - p, 1.7);
                Vec3 point = rotate(dir, axis, swirl * p).scale(radius);
                path[i] = s.tip.add(point);
                float head = 1.0f - tail;
                float bornDie = AvadaChargePose.smoothstep(0.0f, 0.18f, u) * (1.0f - AvadaChargePose.smoothstep(0.90f, 1.0f, u));
                alpha[i] = strength * head * head * (flung ? 1.0f : bornDie);
                width[i] = (0.012f + 0.05f * head) * s.near;
            }
            // Glow then core.
            ribbon(out, m, path, width, alpha, 3.2f, 0.22f, GREEN_R, GREEN_G, GREEN_B);
            ribbon(out, m, path, width, alpha, 1.0f, 0.95f, HOT_R, HOT_G, HOT_B);
        }
    }

    /** Distance along the thread cycle after {@code elapsed} ticks: speed ramps from 1/16 to 1/5 cycles a tick. */
    private static double travel(double elapsed, int duration) {
        double s0 = 1.0 / 16.0;
        double s1 = 1.0 / 5.0;
        double d = Math.max(1, duration);
        double e = Math.max(0.0, elapsed);
        if (e <= d) {
            return s0 * e + (s1 - s0) * e * e / (2.0 * d);
        }
        return s0 * d + (s1 - s0) * d / 2.0 + s1 * (e - d);
    }

    /**
     * Jagged arcs snapping off the tip into the air and back up the caster's arm, re-struck every other tick.
     * Few and short at first, then a crackling cage around the wand.
     */
    private static void drawArcs(VertexConsumer out, Matrix4f m, Scene s) {
        float fade;
        if (s.gathering()) {
            fade = AvadaChargePose.smoothstep(0.1f, 0.4f, s.c);
        } else if (s.phase == AvadaChargeFx.BROKEN) {
            fade = 1.0f - AvadaChargePose.smoothstep(0.0f, 0.4f, s.after); // a last convulsion, then gone
        } else {
            return;
        }
        if (fade <= 0.01f) {
            return;
        }
        int strike = (int) Math.floor(s.age / 2.0);
        float flicker = (s.age % 2.0) < 1.0 ? 1.0f : 0.6f;
        int count = s.firstPerson ? 1 + Math.round(2.0f * s.c) : 1 + Math.round(4.0f * s.c);
        Vec3[] path = new Vec3[ARC_SEGMENTS + 1];
        float[] alpha = new float[ARC_SEGMENTS + 1];
        float[] width = new float[ARC_SEGMENTS + 1];

        for (int a = 0; a < count; a++) {
            int seed = s.charge.seed * 31 + strike * 977 + a * 131;
            // Not every arc strikes every time: the gaps are what make it crackle.
            if (hash(seed, 1, 1) > 0.35f + 0.6f * s.c) {
                continue;
            }
            Vec3 end;
            if (!s.firstPerson && hash(seed, 2, 2) < 0.3f) {
                // Back down the arm toward the body.
                end = s.shoulder.add(randomDirection(seed + 3).scale(0.25));
            } else {
                double reach = (0.35 + (0.5 + 0.8 * s.c) * hash(seed, 4, 4)) * s.near;
                end = s.tip.add(randomDirection(seed + 5).scale(reach));
            }
            Vec3 span = end.subtract(s.tip);
            double length = span.length();
            if (length < 1.0E-3) {
                continue;
            }
            Vec3 side = span.cross(new Vec3(0.0, 1.0, 0.0));
            side = side.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
            Vec3 lift = side.cross(span).normalize();
            for (int i = 0; i <= ARC_SEGMENTS; i++) {
                float t = i / (float) ARC_SEGMENTS;
                double jag = (i == 0 || i == ARC_SEGMENTS) ? 0.0 : length * 0.16 * Math.sin(Math.PI * t);
                Vec3 point = s.tip.add(span.scale(t))
                        .add(side.scale(jag * (hash(seed, i, 7) * 2.0 - 1.0)))
                        .add(lift.scale(jag * (hash(seed, i, 8) * 2.0 - 1.0)));
                path[i] = point;
                alpha[i] = fade * flicker * (1.0f - 0.5f * t);
                width[i] = (0.016f - 0.008f * t) * s.near;
            }
            ribbon(out, m, path, width, alpha, 4.0f, 0.28f, GREEN_R, GREEN_G, GREEN_B);
            ribbon(out, m, path, width, alpha, 1.0f, 1.0f, HOT_R, HOT_G, HOT_B);
        }
    }

    /** The tip: a hard white-green core in a green halo, flickering like something barely held. */
    private static void drawCore(VertexConsumer out, Matrix4f m, Scene s) {
        float t = (float) s.age;
        float flicker = 0.82f + 0.12f * Mth.sin(t * 0.9f + s.charge.phase) + 0.10f * Mth.sin(t * 2.3f)
                + 0.08f * (hash(s.charge.seed, (int) t, 9) - 0.5f);
        float halo = 0.22f + 0.85f * s.c;
        float core = 0.05f + 0.15f * s.c;
        float haloAlpha = 0.55f * s.gloom() * flicker;
        float coreAlpha = (0.55f + 0.45f * s.c) * flicker;

        if (!s.gathering()) {
            if (s.phase == AvadaChargeFx.FIRED) {
                // The release: a flare that swells and is gone in half a second.
                float flare = 1.0f - s.after;
                billboard(out, m, s.tip, (0.9f + 1.8f * s.after) * s.near, 0.9f * flare * flare,
                        HOT_R, HOT_G, HOT_B, true);
                halo *= 1.0f + s.after;
                haloAlpha *= flare;
                coreAlpha *= flare * flare;
            } else if (s.phase == AvadaChargeFx.BROKEN) {
                // Implodes into the tip in three ticks, with one last flash as it goes.
                float squeeze = 1.0f - AvadaChargePose.smoothstep(0.0f, 0.22f, s.after);
                halo *= squeeze;
                core *= squeeze;
                float pop = Math.max(0.0f, 1.0f - Math.abs(s.after - 0.18f) / 0.1f);
                billboard(out, m, s.tip, 0.5f * s.near, 0.9f * pop, HOT_R, HOT_G, HOT_B, true);
            } else {
                float fade = 1.0f - s.after;
                haloAlpha *= fade;
                coreAlpha *= fade;
            }
        }
        if (s.firstPerson) {
            // Your own tip sits just below the crosshair; keep it from whiting out the view.
            haloAlpha *= 0.55f;
            coreAlpha *= 0.65f;
        }
        billboard(out, m, s.tip, halo * s.near, haloAlpha, GREEN_R, GREEN_G, GREEN_B, false);
        billboard(out, m, s.tip, core * s.near, coreAlpha, HOT_R, HOT_G, HOT_B, true);
    }

    // ---- geometry ----------------------------------------------------------------------------------------------

    /**
     * A camera-facing soft strip through {@code path}: transparent at both edges, {@code alpha} along the spine.
     * {@code widthScale} and {@code alphaScale} let the same path be laid down once as a wide glow and once as a
     * thin bright core.
     */
    private static void ribbon(VertexConsumer out, Matrix4f m, Vec3[] path, float[] width, float[] alpha,
                               float widthScale, float alphaScale, float r, float g, float b) {
        for (int i = 0; i < path.length - 1; i++) {
            float a0 = alpha[i] * alphaScale;
            float a1 = alpha[i + 1] * alphaScale;
            if (a0 <= 0.003f && a1 <= 0.003f) {
                continue;
            }
            Vec3 p0 = path[i];
            Vec3 p1 = path[i + 1];
            Vec3 along = p1.subtract(p0);
            if (along.lengthSqr() < 1.0E-10) {
                continue;
            }
            // The camera is the origin, so the way to it from the segment is minus its midpoint.
            Vec3 toCamera = p0.add(p1).scale(-0.5);
            Vec3 perp = along.cross(toCamera);
            if (perp.lengthSqr() < 1.0E-10) {
                continue;
            }
            perp = perp.normalize();
            Vec3 e0 = perp.scale(width[i] * widthScale);
            Vec3 e1 = perp.scale(width[i + 1] * widthScale);
            vertex(out, m, p0.subtract(e0), r, g, b, 0.0f);
            vertex(out, m, p1.subtract(e1), r, g, b, 0.0f);
            vertex(out, m, p1, r, g, b, a1);
            vertex(out, m, p0, r, g, b, a0);
            vertex(out, m, p0, r, g, b, a0);
            vertex(out, m, p1, r, g, b, a1);
            vertex(out, m, p1.add(e1), r, g, b, 0.0f);
            vertex(out, m, p0.add(e0), r, g, b, 0.0f);
        }
    }

    /** A soft camera-facing disc, {@code alpha} at the centre falling to nothing at {@code radius}. */
    private static void billboard(VertexConsumer out, Matrix4f m, Vec3 centre, float radius, float alpha,
                                  float r, float g, float b, boolean hot) {
        if (alpha <= 0.003f || radius <= 0.001f) {
            return;
        }
        Vec3 normal = centre.scale(-1.0);
        if (normal.lengthSqr() < 1.0E-6) {
            return;
        }
        normal = normal.normalize();
        Vec3 right = normal.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-4 ? normal.cross(new Vec3(1.0, 0.0, 0.0)).normalize() : right.normalize();
        Vec3 up = right.cross(normal).normalize();
        disc(out, m, centre, right, up, radius, alpha, r, g, b, hot);
    }

    /** A soft horizontal disc lying on the ground. */
    private static void groundDisc(VertexConsumer out, Matrix4f m, Vec3 centre, float radius, float alpha) {
        if (alpha <= 0.003f) {
            return;
        }
        disc(out, m, centre, new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0), radius, alpha,
                SMOKE_R, SMOKE_G, SMOKE_B, false);
    }

    private static void disc(VertexConsumer out, Matrix4f m, Vec3 centre, Vec3 right, Vec3 up, float radius,
                             float alpha, float r, float g, float b, boolean hot) {
        for (int ring = 0; ring < DISC_RINGS; ring++) {
            float f0 = ring / (float) DISC_RINGS;
            float f1 = (ring + 1) / (float) DISC_RINGS;
            float a0 = alpha * falloff(f0, hot);
            float a1 = alpha * falloff(f1, hot);
            for (int seg = 0; seg < DISC_SEGMENTS; seg++) {
                double t0 = Math.PI * 2.0 * seg / DISC_SEGMENTS;
                double t1 = Math.PI * 2.0 * (seg + 1) / DISC_SEGMENTS;
                vertex(out, m, centre.add(onRing(right, up, t0, radius * f0)), r, g, b, a0);
                vertex(out, m, centre.add(onRing(right, up, t1, radius * f0)), r, g, b, a0);
                vertex(out, m, centre.add(onRing(right, up, t1, radius * f1)), r, g, b, a1);
                vertex(out, m, centre.add(onRing(right, up, t0, radius * f1)), r, g, b, a1);
            }
        }
    }

    /** Hot cores fall off sharply (a point of light); smoke and halos softly. */
    private static float falloff(float f, boolean hot) {
        float inv = 1.0f - f;
        return hot ? inv * inv * inv : inv * inv * (0.6f + 0.4f * inv);
    }

    private static Vec3 onRing(Vec3 right, Vec3 up, double angle, float radius) {
        return right.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
    }

    /** {@code v} turned {@code angle} radians about the unit {@code axis} (Rodrigues). */
    private static Vec3 rotate(Vec3 v, Vec3 axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return v.scale(cos).add(axis.cross(v).scale(sin)).add(axis.scale(axis.dot(v) * (1.0 - cos)));
    }

    private static Vec3 randomDirection(int seed) {
        double z = hash(seed, 11, 0) * 2.0 - 1.0;
        double angle = hash(seed, 12, 0) * Math.PI * 2.0;
        double r = Math.sqrt(Math.max(0.0, 1.0 - z * z));
        return new Vec3(r * Math.cos(angle), z, r * Math.sin(angle));
    }

    /** A stable pseudo-random 0..1 from three ints, so a thread or an arc looks the same every frame. */
    private static float hash(int a, int b, int c) {
        int h = a * 0x27d4eb2d ^ b * 0x165667b1 ^ c * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static void vertex(VertexConsumer out, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        out.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, Mth.clamp(a, 0.0f, 1.0f));
    }
}
