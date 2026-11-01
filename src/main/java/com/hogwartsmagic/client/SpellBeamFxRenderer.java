package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellBeamFx;
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

import java.util.List;

import static com.hogwartsmagic.client.SpellFxGeometry.*;

/**
 * Every spell beam in flight and where it lands, drawn by hand from its {@link BeamStyle} rather than with
 * particles.
 *
 * <p>A beam is a path recorded from the wand ({@link SpellBeamClientState}): a jet shows all of it, a bolt the
 * last few blocks fading to a tail. Down that path go a wide glow, a body and a hot core, writhing and re-struck
 * every other tick as the style asks, inside a sheath that is either dark (the curses: it darkens the world behind
 * it) or a soft light. Strands coil round it (jagged and gappy, or smooth twisting ribbons), beads of light run
 * along it, rings are shed into the air behind the head, and the air it passed through gives off the style's wake:
 * smoke, mist, cinders, sparks or hanging motes. When it ends it flares and stays burned in for a moment.
 *
 * <p>A strike is a flash and a draining glow, then whatever the style has of: a ring thrown out (or drawn in)
 * round the beam's line or flat on the air, arcs, a pillar of light, a burst of mist or of black, and debris flung
 * out under its own gravity.
 *
 * <p>Two passes, the dark first (straight alpha, so it really darkens) and the light on top (additive).
 * Camera-relative in double precision, identity matrix (this stage is after the camera push), true partial tick.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class SpellBeamFxRenderer {
    private static final double SAMPLE_SPACING = 0.45;
    private static final int MAX_SAMPLES = 120;
    private static final int IMPACT_PUFFS = 10;
    private static final int ARC_SEGMENTS = 7;
    private static final int MIST_PUFFS = 8;
    private static final float SPARK_GRAVITY = 0.012f;

    private SpellBeamFxRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || SpellBeamClientState.isEmpty()) {
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
        for (SpellBeamClientState.Beam beam : SpellBeamClientState.beams()) {
            Frame frame = Frame.of(beam, now, partial, camera);
            BeamStyle style = beam.style;
            if (frame != null) {
                if (style.sheathDark && style.sheathWidth > 0.0f) {
                    ribbon(dark, m, frame.path, frame.widths(style.sheathWidth), frame.alphas(style.sheathAlpha, true),
                            1.0f, 1.0f, SMOKE_R, SMOKE_G, SMOKE_B);
                }
                if (style.headDark && !beam.gone()) {
                    billboard(dark, m, frame.head(), style.headRadius * 1.25f, 0.4f, SMOKE_R, SMOKE_G, SMOKE_B, false);
                }
            }
            if (style.wake == BeamStyle.WAKE_SMOKE || style.smokeToo) {
                drawWake(dark, m, beam, now, camera, true);
            }
        }
        for (SpellBeamClientState.Impact impact : SpellBeamClientState.impacts()) {
            drawImpactDark(dark, m, impact, now, camera);
        }
        buffer.endBatch(ModRenderTypes.AVADA_SHADOW);

        VertexConsumer light = buffer.getBuffer(ModRenderTypes.AVADA_LIGHT);
        for (SpellBeamClientState.Beam beam : SpellBeamClientState.beams()) {
            Frame frame = Frame.of(beam, now, partial, camera);
            if (frame != null) {
                drawBeam(light, m, frame);
                drawStrands(light, m, frame);
                drawBeads(light, m, frame);
                drawHead(light, m, frame);
            }
            drawShedRings(light, m, beam, now, camera);
            if (beam.style.wake != BeamStyle.WAKE_SMOKE && beam.style.wake != BeamStyle.WAKE_NONE) {
                drawWake(light, m, beam, now, camera, false);
            }
        }
        for (SpellBeamClientState.Impact impact : SpellBeamClientState.impacts()) {
            drawImpactLight(light, m, impact, now, camera);
        }
        buffer.endBatch(ModRenderTypes.AVADA_LIGHT);
    }

    // ---- the beam's shape this frame ---------------------------------------------------------------------------

    /** One beam's drawn path this frame, camera-relative, tail end first, and how bright it still is. */
    private static final class Frame {
        final SpellBeamClientState.Beam beam;
        final BeamStyle style;
        final Vec3[] path;
        /** Brightness along the path: eased out of the wand, fading down a bolt's tail. */
        final float[] profile;
        /** Distance of each sample from the wand, along the path. */
        final double[] fromWand;
        final Vec3 side;
        final Vec3 lift;
        final double age;
        final int strike;
        /** 1 in flight; flares then drains to 0 once the beam is gone. */
        final float glow;
        /** 1 in flight; the burned-in beam thins as it fades. */
        final float thickness;

        private Frame(SpellBeamClientState.Beam beam, Vec3[] path, float[] profile, double[] fromWand, Vec3 side,
                      Vec3 lift, double age, float glow, float thickness) {
            this.beam = beam;
            this.style = beam.style;
            this.path = path;
            this.profile = profile;
            this.fromWand = fromWand;
            this.side = side;
            this.lift = lift;
            this.age = age;
            this.strike = (int) Math.floor(age / 2.0);
            this.glow = glow;
            this.thickness = thickness;
        }

        Vec3 head() {
            return path[path.length - 1];
        }

        static Frame of(SpellBeamClientState.Beam beam, double now, float partial, Vec3 camera) {
            BeamStyle style = beam.style;
            float glow = 1.0f;
            float thickness = 1.0f;
            if (beam.gone()) {
                float since = (float) (now - beam.goneAt);
                if (since > style.afterglow) {
                    return null;
                }
                float f = since / style.afterglow;
                // A flare as it lands, then the burned-in beam drains away.
                glow = (1.0f + 0.6f * (1.0f - smoothstep(0.0f, 0.2f, f))) * (1.0f - f) * (1.0f - f);
                thickness = 1.0f - 0.55f * f;
            }

            // The recorded path, newest point lerped to this frame, and distances measured back from the head.
            List<Vec3> trail = beam.trail;
            int n = trail.size();
            Vec3[] points = new Vec3[n];
            for (int i = 0; i < n - 1; i++) {
                points[i] = trail.get(i).subtract(camera);
            }
            points[n - 1] = beam.head(partial).subtract(camera);
            double[] fromHead = new double[n];
            for (int i = n - 2; i >= 0; i--) {
                fromHead[i] = fromHead[i + 1] + points[i].distanceTo(points[i + 1]);
            }
            double total = fromHead[0];
            if (total < 0.05) {
                return null;
            }
            double visible = Math.min(total, style.tail);
            int samples = Mth.clamp((int) Math.ceil(visible / SAMPLE_SPACING), 3, MAX_SAMPLES);

            Vec3 tailEnd = pointBack(points, fromHead, visible);
            Vec3 axis = points[n - 1].subtract(tailEnd);
            axis = axis.lengthSqr() < 1.0E-8 ? beam.dir : axis.normalize();
            Vec3[] frame = frame(axis);
            double age = now - beam.times.get(0);
            int strike = (int) Math.floor(age / 2.0);

            Vec3[] path = new Vec3[samples + 1];
            float[] profile = new float[samples + 1];
            double[] fromWand = new double[samples + 1];
            float wandEase = (float) Math.min(1.0, 0.5 / visible);
            for (int i = 0; i <= samples; i++) {
                float t = i / (float) samples;
                double back = visible * (1.0 - t);
                double along = total - back;
                Vec3 base = pointBack(points, fromHead, back);
                // Pinned at both ends; a smooth travelling wave plus a jag re-struck every other tick between them.
                double pin = Math.sqrt(Math.sin(Math.PI * t));
                double wave = style.writhe * Math.sin(along * style.writheFreq - age * style.writheSpeed);
                double wave2 = style.writhe * 0.8 * Math.cos(along * style.writheFreq * 0.85 - age * style.writheSpeed * 0.9);
                double jagA = (hash(beam.seed, i, strike) - 0.5) * 2.0 * style.jitter;
                double jagB = (hash(beam.seed, i + 4099, strike) - 0.5) * 2.0 * style.jitter;
                path[i] = base.add(frame[0].scale((wave + jagA) * pin)).add(frame[1].scale((wave2 + jagB) * pin));
                fromWand[i] = along;
                // A jet brightens toward the head; a bolt fades down its tail. Either eases out of the wand while
                // it still reaches back to it.
                float body = style.isJet() ? 0.62f + 0.38f * t
                        : (float) Math.pow(1.0 - back / Math.max(0.01, style.tail), 1.3);
                float wand = visible >= total ? smoothstep(0.0f, wandEase, t) : 1.0f;
                profile[i] = wand * body;
            }
            return new Frame(beam, path, profile, fromWand, frame[0], frame[1], age, glow, thickness);
        }

        /** The point {@code back} blocks back from the head along the recorded path. */
        private static Vec3 pointBack(Vec3[] points, double[] fromHead, double back) {
            int n = points.length;
            for (int i = n - 2; i >= 0; i--) {
                if (fromHead[i] >= back) {
                    double span = fromHead[i] - fromHead[i + 1];
                    double f = span < 1.0E-9 ? 0.0 : (back - fromHead[i + 1]) / span;
                    return points[i + 1].lerp(points[i], f);
                }
            }
            return points[0];
        }

        float[] alphas(float scale, boolean capGlow) {
            float g = capGlow ? Math.min(1.0f, glow) : glow;
            float[] a = new float[profile.length];
            for (int i = 0; i < a.length; i++) {
                a[i] = profile[i] * scale * g;
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

    // ---- the beam ----------------------------------------------------------------------------------------------

    private static void drawBeam(VertexConsumer out, Matrix4f m, Frame f) {
        BeamStyle s = f.style;
        float flicker = 1.0f - s.flicker * hash(f.beam.seed, 77, f.strike);
        if (!s.sheathDark && s.sheathWidth > 0.0f) {
            ribbon(out, m, f.path, f.widths(s.sheathWidth), f.alphas(s.sheathAlpha * 0.35f, false), 1.0f, 1.0f,
                    s.glowR, s.glowG, s.glowB);
        }
        ribbon(out, m, f.path, f.widths(s.glowWidth), f.alphas(s.glowAlpha * flicker, false), 1.0f, 1.0f,
                s.glowR, s.glowG, s.glowB);
        ribbon(out, m, f.path, f.widths(s.bodyWidth), f.alphas(s.bodyAlpha * flicker, false), 1.0f, 1.0f,
                s.bodyR, s.bodyG, s.bodyB);
        ribbon(out, m, f.path, f.widths(s.coreWidth), f.alphas(1.0f, false), 1.0f, 1.0f, s.coreR, s.coreG, s.coreB);
    }

    /** Strands coiling round the beam: jagged and gappy (crackle) or smooth twisting ribbons. */
    private static void drawStrands(VertexConsumer out, Matrix4f m, Frame f) {
        BeamStyle s = f.style;
        if (s.strands <= 0 || (f.beam.gone() && f.glow < 0.5f)) {
            return;
        }
        int n = f.path.length;
        Vec3[] strand = new Vec3[n];
        float[] alpha = new float[n];
        float[] width = new float[n];
        for (int k = 0; k < s.strands; k++) {
            double radius = s.strandRadius * (0.8 + 0.4 * hash(f.beam.seed, k, 3));
            for (int i = 0; i < n; i++) {
                double along = f.fromWand[i];
                double angle = along * s.strandTwist + k * (Math.PI * 2.0 / s.strands) + f.age * s.strandSpin;
                double jag = s.strandsCrackle ? (hash(f.beam.seed + k * 977, i, f.strike) - 0.5) * 0.08 : 0.0;
                double r = (radius + jag) * Math.min(1.0, along / 0.6);
                strand[i] = f.path[i].add(f.side.scale(Math.cos(angle) * r)).add(f.lift.scale(Math.sin(angle) * r));
                boolean gap = s.strandsCrackle && hash(f.beam.seed + k * 31, i / 3, f.strike) > 0.62f;
                alpha[i] = (gap ? 0.0f : 0.9f) * f.profile[i] * Math.min(1.0f, f.glow);
                width[i] = (s.strandsCrackle ? 0.012f : 0.018f) * f.thickness;
            }
            if (s.strandsCrackle) {
                ribbon(out, m, strand, width, alpha, 3.2f, 0.30f, s.glowR, s.glowG, s.glowB);
                ribbon(out, m, strand, width, alpha, 1.0f, 1.0f, s.coreR, s.coreG, s.coreB);
            } else {
                ribbon(out, m, strand, width, alpha, 3.0f, 0.25f, s.glowR, s.glowG, s.glowB);
                ribbon(out, m, strand, width, alpha, 1.0f, 0.75f, s.bodyR, s.bodyG, s.bodyB);
            }
        }
    }

    /** Beads of light running along the beam, out to the head or (negative speed) back to the wand. */
    private static void drawBeads(VertexConsumer out, Matrix4f m, Frame f) {
        BeamStyle s = f.style;
        if (s.beads <= 0 || f.beam.gone()) {
            return;
        }
        int last = f.path.length - 1;
        double length = Math.max(0.5, f.fromWand[last] - f.fromWand[0]);
        for (int k = 0; k < s.beads; k++) {
            double phase = f.age * Math.abs(s.beadSpeed) / length + k / (double) s.beads;
            float u = (float) (phase - Math.floor(phase));
            float at = (s.beadSpeed >= 0.0f ? u : 1.0f - u) * last;
            int i = Math.min(last - 1, (int) at);
            float frac = at - i;
            Vec3 pos = f.path[i].lerp(f.path[i + 1], frac);
            float alpha = Mth.lerp(frac, f.profile[i], f.profile[i + 1]) * smoothstep(0.0f, 0.1f, u)
                    * (1.0f - smoothstep(0.9f, 1.0f, u));
            billboard(out, m, pos, s.beadSize * 2.4f, 0.5f * alpha, s.glowR, s.glowG, s.glowB, false, 3, 12);
            billboard(out, m, pos, s.beadSize, 0.95f * alpha, s.coreR, s.coreG, s.coreB, true, 3, 12);
        }
    }

    private static void drawHead(VertexConsumer out, Matrix4f m, Frame f) {
        BeamStyle s = f.style;
        if (f.beam.gone()) {
            return;
        }
        float pulse = 0.85f + 0.15f * Mth.sin((float) f.age * 1.7f);
        billboard(out, m, f.head(), s.headRadius * pulse, s.headAlpha, s.glowR, s.glowG, s.glowB, false);
        billboard(out, m, f.head(), s.headCore, 0.95f, s.coreR, s.coreG, s.coreB, true);
    }

    // ---- what the beam leaves behind ---------------------------------------------------------------------------

    /**
     * Calls {@code visit} for each point every {@code spacing} blocks along the recorded path, with the moment the
     * head passed it, newest first, stopping once points are older than {@code life}.
     */
    private static void alongTrail(SpellBeamClientState.Beam beam, double spacing, double now, double life,
                                   TrailVisitor visit) {
        List<Vec3> trail = beam.trail;
        List<Double> times = beam.times;
        int n = trail.size();
        double[] fromWand = new double[n];
        for (int i = 1; i < n; i++) {
            fromWand[i] = fromWand[i - 1] + trail.get(i).distanceTo(trail.get(i - 1));
        }
        for (int i = n - 1; i >= 1; i--) {
            double t0 = times.get(i - 1);
            double t1 = times.get(i);
            if (now - t1 > life) {
                break;
            }
            double a = fromWand[i - 1];
            double b = fromWand[i];
            if (b - a < 1.0E-6) {
                continue;
            }
            Vec3 dir = trail.get(i).subtract(trail.get(i - 1)).normalize();
            for (long e = (long) Math.floor(b / spacing); e * spacing > a; e--) {
                double f = (e * spacing - a) / (b - a);
                double born = Mth.lerp(f, t0, t1);
                double age = now - born;
                if (age >= 0.0 && age <= life) {
                    visit.at(trail.get(i - 1).lerp(trail.get(i), f), dir, (float) age, (int) e);
                }
            }
        }
    }

    @FunctionalInterface
    private interface TrailVisitor {
        void at(Vec3 pos, Vec3 dir, float age, int index);
    }

    private static void drawShedRings(VertexConsumer out, Matrix4f m, SpellBeamClientState.Beam beam, double now,
                                      Vec3 camera) {
        BeamStyle s = beam.style;
        if (s.ringEvery <= 0.0f) {
            return;
        }
        alongTrail(beam, s.ringEvery, now, s.ringLife, (pos, dir, age, index) -> {
            float f = age / s.ringLife;
            Vec3[] axes = frame(dir);
            float radius = s.ringRadius * (0.3f + 0.7f * (float) Math.sqrt(f));
            float alpha = 0.6f * (float) Math.pow(1.0f - f, 1.5) * smoothstep(0.0f, 1.0f, age);
            Vec3 centre = pos.subtract(camera);
            ring(out, m, centre, axes[0], axes[1], radius, 0.16f, alpha * 0.45f, s.glowR, s.glowG, s.glowB, 32);
            ring(out, m, centre, axes[0], axes[1], radius, 0.04f, alpha, s.coreR, s.coreG, s.coreB, 32);
        });
    }

    private static void drawWake(VertexConsumer out, Matrix4f m, SpellBeamClientState.Beam beam, double now,
                                 Vec3 camera, boolean smokePass) {
        BeamStyle s = beam.style;
        boolean smoke = smokePass;
        float spacing = smoke && s.wake != BeamStyle.WAKE_SMOKE ? Math.max(0.6f, s.wakeSpacing * 2.0f) : s.wakeSpacing;
        float life = smoke && s.wake != BeamStyle.WAKE_SMOKE ? 24.0f : s.wakeLife;
        alongTrail(beam, spacing, now, life, (pos, dir, age, index) -> {
            float l = age / life;
            int seed = beam.seed + index * 131;
            Vec3 at = pos.subtract(camera);
            if (smoke) {
                // Smoke (or a fire's smoke): rises and spreads, near-black.
                float size = s.wake == BeamStyle.WAKE_SMOKE ? s.wakeSize : 0.25f;
                float alpha = s.wake == BeamStyle.WAKE_SMOKE ? s.wakeAlpha : 0.22f;
                Vec3 drift = randomDirection(seed).scale(0.12 + s.wakeSpread * l);
                Vec3 c = at.add(drift).add(0.0, 0.035 * age, 0.0);
                billboard(out, m, c, size + s.wakeGrow * l, alpha * (1.0f - l) * (1.0f - l) * smoothstep(0.0f, 1.5f, age),
                        SMOKE_R, SMOKE_G, SMOKE_B, false, 3, 10);
                return;
            }
            float r = s.glowR;
            float g = s.glowG;
            float b = s.glowB;
            if (s.wakeRainbow) {
                int rgb = Mth.hsvToRgb(hash(seed, 5, 5), 0.55f, 1.0f);
                r = ((rgb >> 16) & 0xFF) / 255.0f;
                g = ((rgb >> 8) & 0xFF) / 255.0f;
                b = (rgb & 0xFF) / 255.0f;
            }
            if (s.wake == BeamStyle.WAKE_MIST) {
                Vec3 c = at.add(randomDirection(seed).scale(0.1 + s.wakeSpread * l)).add(0.0, s.wakeRise * age, 0.0);
                billboard(out, m, c, s.wakeSize + s.wakeGrow * l, s.wakeAlpha * (1.0f - l) * (1.0f - l)
                        * smoothstep(0.0f, 2.0f, age), r, g, b, false, 3, 10);
            } else if (s.wake == BeamStyle.WAKE_EMBERS) {
                Vec3 c = at.add(randomDirection(seed).scale(s.wakeSpread * 0.6 * l)).add(0.0, s.wakeRise * age, 0.0);
                float flick = 0.6f + 0.4f * hash(seed, (int) age, 9);
                float er = Mth.lerp(l, s.coreR, s.bodyR);
                float eg = Mth.lerp(l, s.coreG, s.bodyG);
                float eb = Mth.lerp(l, s.coreB, s.bodyB);
                billboard(out, m, c, s.wakeSize * (1.0f - 0.5f * l), s.wakeAlpha * (1.0f - l) * flick, er, eg, eb,
                        true, 2, 8);
            } else if (s.wake == BeamStyle.WAKE_SPARKS) {
                Vec3 v = randomDirection(seed).scale(0.05 + 0.04 * hash(seed, 6, 6));
                Vec3 p1 = sparkAt(at, v, age);
                Vec3 p0 = sparkAt(at, v, Math.max(0.0f, age - 1.5f));
                float alpha = s.wakeAlpha * (1.0f - l);
                streak(out, m, p0, p1, 0.014f, alpha, Mth.lerp(0.5f, s.coreR, s.bodyR), Mth.lerp(0.5f, s.coreG, s.bodyG),
                        Mth.lerp(0.5f, s.coreB, s.bodyB));
            } else if (s.wake == BeamStyle.WAKE_MOTES) {
                Vec3 c = at.add(randomDirection(seed).scale(0.08 + s.wakeSpread * 0.4 * l))
                        .add(0.0, s.wakeRise * age, 0.0);
                float twinkle = 0.5f + 0.5f * Mth.sin(age * 0.8f + index);
                float alpha = s.wakeAlpha * (1.0f - l) * twinkle * smoothstep(0.0f, 2.0f, age);
                billboard(out, m, c, s.wakeSize * 2.5f, alpha * 0.35f, r, g, b, false, 2, 8);
                billboard(out, m, c, s.wakeSize, alpha, s.wakeRainbow ? r : s.coreR, s.wakeRainbow ? g : s.coreG,
                        s.wakeRainbow ? b : s.coreB, true, 2, 8);
            }
        });
    }

    private static Vec3 sparkAt(Vec3 start, Vec3 velocity, float t) {
        return start.add(velocity.scale(t)).add(0.0, -0.5 * SPARK_GRAVITY * t * t, 0.0);
    }

    /** A short soft streak of light from {@code p0} to {@code p1}. */
    private static void streak(VertexConsumer out, Matrix4f m, Vec3 p0, Vec3 p1, float width, float alpha,
                               float r, float g, float b) {
        if (alpha <= 0.003f || p0.distanceToSqr(p1) < 1.0E-8) {
            return;
        }
        ribbon(out, m, new Vec3[]{p0, p1}, new float[]{width * 0.4f, width}, new float[]{alpha * 0.3f, alpha},
                1.0f, 1.0f, r, g, b);
    }

    // ---- the strike --------------------------------------------------------------------------------------------

    private static void drawImpactDark(VertexConsumer out, Matrix4f m, SpellBeamClientState.Impact impact,
                                       double now, Vec3 camera) {
        boolean evade = impact.kind == SpellBeamFx.EVADE;
        if (!(impact.style.impactDark && impact.full()) && !evade) {
            return;
        }
        float age = (float) (now - impact.start);
        if (age < 0.0f) {
            return;
        }
        float f = age / SpellBeamClientState.IMPACT_TICKS;
        float scale = impact.scale();
        Vec3 pos = impact.pos.subtract(camera);
        float fade = (float) Math.pow(1.0f - f, 1.5);

        float burst = smoothstep(0.0f, 0.45f, f);
        float burstRadius = (0.4f + 2.6f * burst) * scale * (evade ? 1.35f : 1.0f);
        billboard(out, m, pos, burstRadius, (evade ? 0.85f : 0.55f) * fade, SMOKE_R, SMOKE_G, SMOKE_B, false);
        for (int k = 0; k < IMPACT_PUFFS; k++) {
            Vec3 dir = randomDirection(impact.seed + k * 7);
            float left = 1.0f - Math.min(1.0f, f * 1.6f);
            float thrown = 1.0f - left * left * left;
            Vec3 centre = pos.add(dir.scale(2.2 * scale * thrown)).add(0.0, 0.7 * f, 0.0);
            billboard(out, m, centre, (0.35f + 0.7f * f) * scale, 0.42f * fade, SMOKE_R, SMOKE_G, SMOKE_B, false, 4, 12);
        }
    }

    private static void drawImpactLight(VertexConsumer out, Matrix4f m, SpellBeamClientState.Impact impact,
                                        double now, Vec3 camera) {
        float age = (float) (now - impact.start);
        if (age < 0.0f) {
            return;
        }
        BeamStyle s = impact.style;
        float f = age / SpellBeamClientState.IMPACT_TICKS;
        float scale = impact.scale();
        float bright = impact.kind == SpellBeamFx.EVADE ? 0.35f : 1.0f;
        Vec3 pos = impact.pos.subtract(camera);

        // The flash: swells in three ticks, gone in eight. Then the glow that takes a second to drain.
        float flash = (1.0f - smoothstep(0.0f, 0.3f, f)) * s.impactFlash * bright;
        float flashRadius = (0.6f + 2.8f * smoothstep(0.0f, 0.12f, f)) * scale;
        billboard(out, m, pos, flashRadius, Math.min(1.0f, flash), s.coreR, s.coreG, s.coreB, true);
        billboard(out, m, pos, 1.9f * scale, s.impactGlow * (1.0f - f) * (1.0f - f) * bright, s.glowR, s.glowG, s.glowB,
                false);

        if (impact.full()) {
            drawImpactRing(out, m, impact, pos, age, scale, bright);
            drawImpactArcs(out, m, impact, pos, age, scale, bright);
            if (s.pillar) {
                float rise = smoothstep(0.0f, 0.35f, f);
                float alpha = 0.55f * (1.0f - f) * bright;
                Vec3[] column = new Vec3[7];
                float[] width = new float[7];
                float[] alpha2 = new float[7];
                for (int i = 0; i < 7; i++) {
                    float t = i / 6.0f;
                    column[i] = pos.add(0.0, 4.5 * scale * rise * t, 0.0);
                    width[i] = 0.5f * scale * (1.0f - 0.6f * t);
                    alpha2[i] = alpha * (1.0f - t);
                }
                ribbon(out, m, column, width, alpha2, 1.0f, 0.6f, s.glowR, s.glowG, s.glowB);
                ribbon(out, m, column, width, alpha2, 0.2f, 1.0f, s.coreR, s.coreG, s.coreB);
            }
            if (s.mistBurst) {
                for (int k = 0; k < MIST_PUFFS; k++) {
                    Vec3 dir = randomDirection(impact.seed + k * 13);
                    float out1 = 1.0f - (1.0f - f) * (1.0f - f);
                    Vec3 c = pos.add(dir.scale(1.6 * scale * out1));
                    billboard(out, m, c, (0.5f + 0.8f * f) * scale, 0.25f * (float) Math.pow(1.0f - f, 1.5) * bright,
                            s.glowR, s.glowG, s.glowB, false, 3, 12);
                }
            }
        }
        int debris = impact.full() ? s.debris : s.debris / 2 + 3;
        for (int k = 0; k < debris; k++) {
            int seed = impact.seed + k * 29;
            Vec3 v = randomDirection(seed).scale(s.debrisSpeed * scale * (0.6 + 0.8 * hash(seed, 1, 1)));
            float g = s.debrisGravity;
            Vec3 p1 = pos.add(v.scale(age)).add(0.0, -0.5 * g * age * age, 0.0);
            float before = Math.max(0.0f, age - 1.2f);
            Vec3 p0 = pos.add(v.scale(before)).add(0.0, -0.5 * g * before * before, 0.0);
            float r = s.coreR;
            float gg = s.coreG;
            float b = s.coreB;
            if (s.wakeRainbow) {
                int rgb = Mth.hsvToRgb(hash(seed, 2, 2), 0.6f, 1.0f);
                r = ((rgb >> 16) & 0xFF) / 255.0f;
                gg = ((rgb >> 8) & 0xFF) / 255.0f;
                b = (rgb & 0xFF) / 255.0f;
            }
            streak(out, m, p0, p1, 0.022f, (1.0f - f) * bright, r, gg, b);
        }
    }

    /** A ring thrown out (or drawn in) round the beam's line, or flat on the air. */
    private static void drawImpactRing(VertexConsumer out, Matrix4f m, SpellBeamClientState.Impact impact, Vec3 pos,
                                       float age, float scale, float bright) {
        BeamStyle s = impact.style;
        float ringAge = Math.min(1.0f, age / 9.0f);
        if (s.impactRing <= 0.0f || ringAge >= 1.0f) {
            return;
        }
        Vec3 right;
        Vec3 up;
        if (s.flatRing) {
            right = new Vec3(1.0, 0.0, 0.0);
            up = new Vec3(0.0, 0.0, 1.0);
        } else {
            Vec3[] axes = frame(impact.dir);
            right = axes[0];
            up = axes[1];
        }
        float eased = 1.0f - (1.0f - ringAge) * (1.0f - ringAge);
        float radius = s.impactRing * scale * (s.ringInward ? 1.0f - eased * 0.92f : eased);
        float alpha = 0.75f * (float) Math.pow(1.0f - ringAge, 1.5) * bright;
        ring(out, m, pos, right, up, radius, 0.38f * scale, alpha * 0.35f, s.glowR, s.glowG, s.glowB, 48);
        ring(out, m, pos, right, up, radius, 0.10f * scale, alpha, s.coreR, s.coreG, s.coreB, 48);
    }

    /** Arcs lancing outward for the first half second, re-struck every other tick; jag 0 draws clean slashes. */
    private static void drawImpactArcs(VertexConsumer out, Matrix4f m, SpellBeamClientState.Impact impact, Vec3 pos,
                                       float age, float scale, float bright) {
        BeamStyle s = impact.style;
        if (s.impactArcs <= 0 || age >= 10.0f) {
            return;
        }
        // Slashes are cut once and fade; crackling arcs are re-struck every other tick.
        int strike = s.arcJag <= 0.0f ? 0 : (int) Math.floor(age / 2.0);
        float fade = (1.0f - age / 10.0f) * bright;
        Vec3[] path = new Vec3[ARC_SEGMENTS + 1];
        float[] alpha = new float[ARC_SEGMENTS + 1];
        float[] width = new float[ARC_SEGMENTS + 1];
        for (int a = 0; a < s.impactArcs; a++) {
            int seed = impact.seed + a * 389 + strike * 6151;
            if (s.arcJag > 0.0f && hash(seed, 1, 1) > 0.75f) {
                continue;
            }
            Vec3 span = randomDirection(seed + 3).scale((0.8 + 1.7 * hash(seed, 2, 2)) * scale);
            Vec3[] axes = frame(span.normalize());
            double length = span.length();
            for (int i = 0; i <= ARC_SEGMENTS; i++) {
                float t = i / (float) ARC_SEGMENTS;
                double jag = (i == 0 || i == ARC_SEGMENTS) ? 0.0 : length * s.arcJag * Math.sin(Math.PI * t);
                path[i] = pos.add(span.scale(t))
                        .add(axes[0].scale(jag * (hash(seed, i, 7) * 2.0 - 1.0)))
                        .add(axes[1].scale(jag * (hash(seed, i, 8) * 2.0 - 1.0)));
                alpha[i] = fade * (1.0f - 0.6f * t);
                width[i] = 0.018f - 0.010f * t;
            }
            ribbon(out, m, path, width, alpha, 4.0f, 0.3f, s.glowR, s.glowG, s.glowB);
            ribbon(out, m, path, width, alpha, 1.0f, 1.0f, s.coreR, s.coreG, s.coreB);
        }
    }
}
