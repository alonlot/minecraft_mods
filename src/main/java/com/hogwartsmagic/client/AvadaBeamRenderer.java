package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.mind.AvadaBeamFx;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import static com.hogwartsmagic.client.AvadaFxGeometry.*;

/**
 * The Killing Curse in flight and where it lands, drawn by hand rather than with particles.
 *
 * <p>The beam is a single jet of green from the caster's wand to its head, not a short bolt flying along: a
 * white-hot core in a green body in a wide green glow, writhing slightly and re-struck every other tick, inside a
 * sheath of near-black that makes it read as something wrong rather than something bright. Three crackling
 * filaments coil round it with gaps that jump about. The head burns in a dark corona, and the air the jet has
 * passed through gives off smoke that rises and spreads. When the beam ends the whole jet flares and stays burned
 * into the air for half a second.
 *
 * <p>A strike: a white-green flash, a ring of light thrown out round the line of the beam, arcs lancing outward,
 * a burst of black with smoke flung off it, and a green glow that takes a second to drain. A beam into a wall is
 * the same, smaller; a dodge is mostly black; a beam that ran out of range just gutters.
 *
 * <p>Camera-relative in double precision, identity matrix (this stage is after the camera push), true partial tick.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class AvadaBeamRenderer {
    private static final double MAX_SEGMENT = 0.5;
    private static final int MAX_SEGMENTS = 120;
    private static final int FILAMENTS = 3;
    private static final double WAKE_SPACING = 0.7;
    private static final int IMPACT_ARCS = 7;
    private static final int IMPACT_PUFFS = 10;
    private static final int ARC_SEGMENTS = 7;

    private AvadaBeamRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || AvadaBeamClientState.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        double now = minecraft.level.getGameTime() + partial;
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        VertexConsumer dark = buffer.getBuffer(ModRenderTypes.AVADA_SHADOW);
        for (AvadaBeamClientState.Jet jet : AvadaBeamClientState.jets()) {
            JetFrame frame = JetFrame.of(jet, now, partial, camera);
            if (frame != null) {
                drawSheath(dark, m, frame);
            }
            drawWake(dark, m, jet, now, camera);
        }
        for (AvadaBeamClientState.Impact impact : AvadaBeamClientState.impacts()) {
            drawImpactDark(dark, m, impact, now, camera);
        }
        buffer.endBatch(ModRenderTypes.AVADA_SHADOW);

        VertexConsumer light = buffer.getBuffer(ModRenderTypes.AVADA_LIGHT);
        for (AvadaBeamClientState.Jet jet : AvadaBeamClientState.jets()) {
            JetFrame frame = JetFrame.of(jet, now, partial, camera);
            if (frame != null) {
                drawJet(light, m, frame);
                drawFilaments(light, m, frame);
                drawHead(light, m, frame);
            }
        }
        for (AvadaBeamClientState.Impact impact : AvadaBeamClientState.impacts()) {
            drawImpactLight(light, m, impact, now, camera);
        }
        buffer.endBatch(ModRenderTypes.AVADA_LIGHT);
    }

    // ---- the jet -----------------------------------------------------------------------------------------------

    /** One jet's shape this frame: its path from wand to head, camera-relative, and how bright it still is. */
    private static final class JetFrame {
        final AvadaBeamClientState.Jet jet;
        final Vec3[] path;
        final float[] profile;
        final Vec3 dir;
        final Vec3 side;
        final Vec3 lift;
        final double length;
        final double age;
        /** 1 in flight; flares then drains to 0 once the beam is gone. */
        final float glow;
        /** 1 in flight; the burned-in jet thins as it fades. */
        final float thickness;
        final int strike;

        private JetFrame(AvadaBeamClientState.Jet jet, Vec3[] path, float[] profile, Vec3 dir, Vec3 side, Vec3 lift,
                         double length, double age, float glow, float thickness) {
            this.jet = jet;
            this.path = path;
            this.profile = profile;
            this.dir = dir;
            this.side = side;
            this.lift = lift;
            this.length = length;
            this.age = age;
            this.glow = glow;
            this.thickness = thickness;
            this.strike = (int) Math.floor(age / 2.0);
        }

        static JetFrame of(AvadaBeamClientState.Jet jet, double now, float partial, Vec3 camera) {
            float glow = 1.0f;
            float thickness = 1.0f;
            if (jet.gone()) {
                float since = (float) (now - jet.goneAt);
                if (since > AvadaBeamClientState.AFTERGLOW_TICKS) {
                    return null;
                }
                float f = since / AvadaBeamClientState.AFTERGLOW_TICKS;
                // A flare as it lands, then the burned-in jet drains away.
                glow = (1.0f + 0.6f * (1.0f - smoothstep(0.0f, 0.2f, f))) * (1.0f - f) * (1.0f - f);
                thickness = 1.0f - 0.55f * f;
            }
            Vec3 origin = jet.origin.subtract(camera);
            Vec3 head = jet.head(partial).subtract(camera);
            Vec3 span = head.subtract(origin);
            double length = span.length();
            if (length < 0.05) {
                return null;
            }
            Vec3 dir = span.scale(1.0 / length);
            Vec3[] axes = frame(dir);
            double age = now - jet.born;
            int segments = Mth.clamp((int) Math.ceil(length / MAX_SEGMENT), 4, MAX_SEGMENTS);
            Vec3[] path = new Vec3[segments + 1];
            float[] profile = new float[segments + 1];
            int strike = (int) Math.floor(age / 2.0);
            for (int i = 0; i <= segments; i++) {
                float t = i / (float) segments;
                double along = length * t;
                // Pinned at the wand and the head; between them a writhe plus a jitter re-struck every other tick.
                double pin = Math.sqrt(Math.sin(Math.PI * t));
                double writhe = 0.035 * Math.sin(along * 1.3 - age * 0.8 + jet.seed * 1.0E-3);
                double jitterA = (hash(jet.seed, i, strike) - 0.5) * 0.09;
                double jitterB = (hash(jet.seed, i + 4099, strike) - 0.5) * 0.09;
                path[i] = origin.add(span.scale(t))
                        .add(axes[0].scale((writhe + jitterA) * pin))
                        .add(axes[1].scale((Math.cos(along * 1.1 - age * 0.7) * 0.03 + jitterB) * pin));
                // Eased out of the wand over half a block, then brightening toward the head.
                float fromWand = smoothstep(0.0f, (float) Math.min(1.0, 0.5 / length), t);
                profile[i] = fromWand * (0.62f + 0.38f * t);
            }
            return new JetFrame(jet, path, profile, dir, axes[0], axes[1], length, age, glow, thickness);
        }

        float[] alphas(float scale) {
            float[] a = new float[profile.length];
            for (int i = 0; i < a.length; i++) {
                a[i] = profile[i] * scale * glow;
            }
            return a;
        }

        float[] widths(float width) {
            float[] w = new float[profile.length];
            for (int i = 0; i < w.length; i++) {
                w[i] = width * thickness * (0.75f + 0.25f * profile[i]);
            }
            return w;
        }
    }

    /** The dark round the jet: what makes the green look poisonous instead of merely bright. */
    private static void drawSheath(VertexConsumer out, Matrix4f m, JetFrame f) {
        ribbon(out, m, f.path, f.widths(0.34f), f.alphas(0.5f), 1.0f, 1.0f, SMOKE_R, SMOKE_G, SMOKE_B);
    }

    private static void drawJet(VertexConsumer out, Matrix4f m, JetFrame f) {
        float flicker = (f.age % 2.0) < 1.0 ? 1.0f : 0.86f;
        ribbon(out, m, f.path, f.widths(0.30f), f.alphas(0.26f * flicker), 1.0f, 1.0f, GREEN_R, GREEN_G, GREEN_B);
        ribbon(out, m, f.path, f.widths(0.10f), f.alphas(0.80f * flicker), 1.0f, 1.0f, GREEN_R, GREEN_G, GREEN_B);
        ribbon(out, m, f.path, f.widths(0.035f), f.alphas(1.0f), 1.0f, 1.0f, HOT_R, HOT_G, HOT_B);
    }

    /** Thin crackling strands coiling round the jet, with gaps that jump about every other tick. */
    private static void drawFilaments(VertexConsumer out, Matrix4f m, JetFrame f) {
        if (f.jet.gone() && f.glow < 0.5f) {
            return;
        }
        int n = f.path.length;
        Vec3[] strand = new Vec3[n];
        float[] alpha = new float[n];
        float[] width = new float[n];
        for (int k = 0; k < FILAMENTS; k++) {
            double radius = 0.11 + 0.07 * hash(f.jet.seed, k, 3);
            for (int i = 0; i < n; i++) {
                float t = i / (float) (n - 1);
                double along = f.length * t;
                double angle = along * 2.3 + k * (Math.PI * 2.0 / FILAMENTS) + f.age * 0.9;
                double jag = (hash(f.jet.seed + k * 977, i, f.strike) - 0.5) * 0.08;
                double r = (radius + jag) * Math.min(1.0, along / 0.6);
                Vec3 offset = f.side.scale(Math.cos(angle) * r).add(f.lift.scale(Math.sin(angle) * r));
                strand[i] = f.path[i].add(offset);
                boolean gap = hash(f.jet.seed + k * 31, i / 3, f.strike) > 0.62f;
                alpha[i] = (gap ? 0.0f : 0.9f) * f.profile[i] * Math.min(1.0f, f.glow);
                width[i] = 0.012f * f.thickness;
            }
            ribbon(out, m, strand, width, alpha, 3.2f, 0.30f, GREEN_R, GREEN_G, GREEN_B);
            ribbon(out, m, strand, width, alpha, 1.0f, 1.0f, HOT_R, HOT_G, HOT_B);
        }
    }

    /** The head in flight: a dark corona round a hard green-white point. */
    private static void drawHead(VertexConsumer out, Matrix4f m, JetFrame f) {
        if (f.jet.gone()) {
            return;
        }
        Vec3 head = f.path[f.path.length - 1];
        float pulse = 0.85f + 0.15f * Mth.sin((float) f.age * 1.7f);
        billboard(out, m, head, 0.75f * pulse, 0.45f, GREEN_R, GREEN_G, GREEN_B, false);
        billboard(out, m, head, 0.26f, 0.95f, HOT_R, HOT_G, HOT_B, true);
    }

    /**
     * Smoke given off where the jet has been: a puff every so often along the path, each born as the head passed
     * it, rising and spreading as it dies. It outlives the jet.
     */
    private static void drawWake(VertexConsumer out, Matrix4f m, AvadaBeamClientState.Jet jet, double now, Vec3 camera) {
        double speed = Math.max(0.2, jet.speed);
        double reach = jet.head.distanceTo(jet.origin);
        int count = (int) Math.min(400, reach / WAKE_SPACING);
        Vec3 origin = jet.origin.subtract(camera);
        Vec3 dir = jet.head.subtract(jet.origin);
        if (dir.lengthSqr() < 1.0E-6) {
            return;
        }
        dir = dir.normalize();
        for (int k = 1; k <= count; k++) {
            double distance = k * WAKE_SPACING;
            float age = (float) (now - (jet.born + distance / speed));
            if (age < 0.0f || age > AvadaBeamClientState.WAKE_TICKS) {
                continue;
            }
            float life = age / AvadaBeamClientState.WAKE_TICKS;
            Vec3 drift = randomDirection(jet.seed + k * 131).scale(0.12 + 0.25 * life);
            Vec3 centre = origin.add(dir.scale(distance)).add(drift).add(0.0, 0.035 * age, 0.0);
            float size = 0.22f + 0.55f * life;
            float alpha = 0.32f * (1.0f - life) * (1.0f - life) * smoothstep(0.0f, 1.5f, age);
            billboard(out, m, centre, size, alpha, SMOKE_R, SMOKE_G, SMOKE_B, false, 3, 10);
        }
    }

    // ---- the strike --------------------------------------------------------------------------------------------

    private static float impactScale(byte kind) {
        if (kind == AvadaBeamFx.HIT) {
            return 1.0f;
        }
        if (kind == AvadaBeamFx.EVADE) {
            return 0.75f;
        }
        return kind == AvadaBeamFx.WALL ? 0.7f : 0.4f;
    }

    private static void drawImpactDark(VertexConsumer out, Matrix4f m, AvadaBeamClientState.Impact impact,
                                       double now, Vec3 camera) {
        float age = (float) (now - impact.start);
        if (age < 0.0f) {
            return;
        }
        float f = age / AvadaBeamClientState.IMPACT_TICKS;
        float scale = impactScale(impact.kind);
        boolean evade = impact.kind == AvadaBeamFx.EVADE;
        Vec3 pos = impact.pos.subtract(camera);
        float fade = (float) Math.pow(1.0f - f, 1.5);

        // The burst of black where it struck.
        float burst = smoothstep(0.0f, 0.45f, f);
        float burstRadius = (0.4f + 2.6f * burst) * scale * (evade ? 1.35f : 1.0f);
        billboard(out, m, pos, burstRadius, (evade ? 0.85f : 0.55f) * fade, SMOKE_R, SMOKE_G, SMOKE_B, false);
        // Smoke flung off it, slowing as it goes, then rising.
        for (int k = 0; k < IMPACT_PUFFS; k++) {
            Vec3 dir = randomDirection(impact.seed + k * 7);
            float left = 1.0f - Math.min(1.0f, f * 1.6f);
            float thrown = 1.0f - left * left * left;
            Vec3 centre = pos.add(dir.scale(2.2 * scale * thrown)).add(0.0, 0.7 * f, 0.0);
            billboard(out, m, centre, (0.35f + 0.7f * f) * scale, 0.42f * fade, SMOKE_R, SMOKE_G, SMOKE_B, false, 4, 12);
        }
    }

    private static void drawImpactLight(VertexConsumer out, Matrix4f m, AvadaBeamClientState.Impact impact,
                                        double now, Vec3 camera) {
        float age = (float) (now - impact.start);
        if (age < 0.0f) {
            return;
        }
        float f = age / AvadaBeamClientState.IMPACT_TICKS;
        float scale = impactScale(impact.kind);
        float green = impact.kind == AvadaBeamFx.EVADE ? 0.35f : 1.0f;
        Vec3 pos = impact.pos.subtract(camera);

        // The flash: swells in three ticks, gone in eight.
        float flash = 1.0f - smoothstep(0.0f, 0.3f, f);
        float flashRadius = (0.6f + 2.8f * smoothstep(0.0f, 0.12f, f)) * scale;
        billboard(out, m, pos, flashRadius, flash * green, HOT_R, HOT_G, HOT_B, true);
        // The glow that takes a second to drain.
        billboard(out, m, pos, 1.9f * scale, 0.55f * (1.0f - f) * (1.0f - f) * green, GREEN_R, GREEN_G, GREEN_B, false);

        // A ring of light thrown out round the line of the beam.
        float ringAge = Math.min(1.0f, age / 9.0f);
        if (ringAge < 1.0f && impact.kind != AvadaBeamFx.FADE) {
            Vec3[] axes = frame(impact.dir);
            float radius = 4.0f * scale * (1.0f - (1.0f - ringAge) * (1.0f - ringAge));
            float ringAlpha = 0.75f * (float) Math.pow(1.0f - ringAge, 1.5) * green;
            ring(out, m, pos, axes[0], axes[1], radius, 0.38f * scale, ringAlpha * 0.35f, GREEN_R, GREEN_G, GREEN_B, 48);
            ring(out, m, pos, axes[0], axes[1], radius, 0.10f * scale, ringAlpha, HOT_R, HOT_G, HOT_B, 48);
        }

        // Arcs lancing outward for the first half second, re-struck every other tick.
        if (age < 10.0f && impact.kind != AvadaBeamFx.FADE) {
            int strike = (int) Math.floor(age / 2.0);
            float arcFade = (1.0f - age / 10.0f) * green;
            Vec3[] path = new Vec3[ARC_SEGMENTS + 1];
            float[] alpha = new float[ARC_SEGMENTS + 1];
            float[] width = new float[ARC_SEGMENTS + 1];
            for (int a = 0; a < IMPACT_ARCS; a++) {
                int seed = impact.seed + a * 389 + strike * 6151;
                if (hash(seed, 1, 1) > 0.75f) {
                    continue;
                }
                Vec3 span = randomDirection(seed + 3).scale((0.8 + 1.7 * hash(seed, 2, 2)) * scale);
                Vec3[] axes = frame(span.normalize());
                double length = span.length();
                for (int i = 0; i <= ARC_SEGMENTS; i++) {
                    float t = i / (float) ARC_SEGMENTS;
                    double jag = (i == 0 || i == ARC_SEGMENTS) ? 0.0 : length * 0.18 * Math.sin(Math.PI * t);
                    path[i] = pos.add(span.scale(t))
                            .add(axes[0].scale(jag * (hash(seed, i, 7) * 2.0 - 1.0)))
                            .add(axes[1].scale(jag * (hash(seed, i, 8) * 2.0 - 1.0)));
                    alpha[i] = arcFade * (1.0f - 0.6f * t);
                    width[i] = 0.018f - 0.010f * t;
                }
                ribbon(out, m, path, width, alpha, 4.0f, 0.3f, GREEN_R, GREEN_G, GREEN_B);
                ribbon(out, m, path, width, alpha, 1.0f, 1.0f, HOT_R, HOT_G, HOT_B);
            }
        }
    }
}
