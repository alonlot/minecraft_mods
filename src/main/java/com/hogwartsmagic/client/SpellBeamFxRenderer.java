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
 * <p>Two beams meeting lock together: both spells' light knotted in a churning ball, crackling arcs of each colour
 * and a spray of sparks thrown out across the line they push along, until it is sucked into a point and blows: a
 * white flash and a sphere of light, a shockwave in each spell's colour and a flat one across the air, shards of
 * both, a burst of black if either is dark, and a small scar of the two lights spinning where they met. When one
 * beam wins (the Elder Wand), the loser shatters back along the winner's line and the winner punches through.
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

    /** A strike's hold: the flash peaks and everything else waits, then bursts out together. */
    private static final float IMPACT_HOLD = 1.5f;

    private static final java.util.Map<SpellBeamClientState.Beam, Frame> FRAMES = new java.util.IdentityHashMap<>();
    private static final java.util.Map<SpellBeamClientState.Beam, double[]> TRAIL_DISTANCES =
            new java.util.IdentityHashMap<>();

    private SpellBeamFxRenderer() {
    }

    /** A memory's held stream, grown out of the wand over its first six ticks. */
    private static SpellBeamClientState.Beam heldOf(SpellBeamClientState.Scenery item, double now) {
        float grow = smoothstep(0.0f, 6.0f, (float) (now - item.start));
        Vec3 to = item.from.lerp(item.to, Math.max(0.05f, grow));
        return SpellBeamClientState.held(item.seed, item.style.asJet(), item.from, to, item.start, now);
    }

    /** A memory's spell with no beam: a held orb, a dome of light, a held stream, or a stream ending in a shield. */
    private static void drawSceneryLight(VertexConsumer out, Matrix4f m, SpellBeamClientState.Scenery item,
                                         double now, Vec3 camera) {
        BeamStyle s = item.style;
        float strength = item.strength(now);
        float t = (float) (now - item.start);
        if (strength <= 0.003f) {
            return;
        }
        if (item.kind == SpellBeamClientState.SCENERY_ORB) {
            Vec3 pos = item.from.subtract(camera);
            float flicker = 0.9f + 0.1f * Mth.sin(t * 1.3f);
            billboard(out, m, pos, 0.9f * flicker, 0.55f * strength, s.glowR, s.glowG, s.glowB, false);
            billboard(out, m, pos, 0.22f, strength, s.coreR, s.coreG, s.coreB, true);
        } else if (item.kind == SpellBeamClientState.SCENERY_DOME) {
            Vec3 centre = item.from.subtract(camera).add(0.0, -1.3, 0.0);
            float radius = 1.9f * (0.7f + 0.3f * smoothstep(0.0f, 6.0f, t));
            Vec3 up = new Vec3(0.0, 1.0, 0.0);
            for (int k = 0; k < 8; k++) {
                double phi = k * Math.PI / 8.0 + t * 0.03;
                Vec3 right = new Vec3(Math.cos(phi), 0.0, Math.sin(phi));
                arc(out, m, centre, right, up, radius, 0.06f, 0.3f * strength, s.glowR, s.glowG, s.glowB, 16, 0.0, Math.PI);
            }
            for (int k = 1; k <= 3; k++) {
                double theta = k * Math.PI * 0.5 / 4.0;
                ring(out, m, centre.add(0.0, Math.sin(theta) * radius, 0.0), new Vec3(1.0, 0.0, 0.0),
                        new Vec3(0.0, 0.0, 1.0), (float) (Math.cos(theta) * radius), 0.05f, 0.25f * strength,
                        s.coreR, s.coreG, s.coreB, 40);
            }
        } else {
            SpellBeamClientState.Beam beam = heldOf(item, now);
            drawHeldLight(out, m, beam, now, camera);
            if (item.kind == SpellBeamClientState.SCENERY_WISP) {
                // The weak Patronus: a hazy shield hanging at the end of the stream.
                Vec3 end = item.to.subtract(camera);
                Vec3[] axes = frame(item.to.subtract(item.from).normalize());
                billboard(out, m, end, 1.7f, 0.35f * strength, s.glowR, s.glowG, s.glowB, false);
                ring(out, m, end, axes[0], axes[1], 1.6f, 0.2f, 0.5f * strength, s.coreR, s.coreG, s.coreB, 40);
            }
        }
    }

    /** The release at the wand: a quick hot flare and a ring snapping out across the line of the beam. */
    private static void drawMuzzle(VertexConsumer out, Matrix4f m, SpellBeamClientState.Muzzle muzzle, double now,
                                   Vec3 camera) {
        float age = (float) (now - muzzle.start);
        if (age < 0.0f || age > SpellBeamClientState.MUZZLE_TICKS) {
            return;
        }
        BeamStyle s = muzzle.style;
        float f = age / SpellBeamClientState.MUZZLE_TICKS;
        Vec3 pos = muzzle.pos.subtract(camera);
        // Smaller when it is your own wand, half a block from your eye.
        float near = (float) Mth.clamp(pos.length() / 1.5, 0.45, 1.0);
        float fade = (1.0f - f) * (1.0f - f);
        billboard(out, m, pos, 0.35f * near * (1.0f - 0.5f * f), fade, s.coreR, s.coreG, s.coreB, true);
        billboard(out, m, pos, 0.9f * near, 0.6f * fade, s.glowR, s.glowG, s.glowB, false);
        Vec3[] axes = frame(muzzle.dir);
        ring(out, m, pos, axes[0], axes[1], (0.15f + 0.8f * smoothstep(0.0f, 0.6f, f)) * near, 0.06f * near,
                0.7f * fade, s.coreR, s.coreG, s.coreB, 28);
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

        // Each beam's shape is worked out once a frame and shared by both passes, as are its trail distances.
        FRAMES.clear();
        TRAIL_DISTANCES.clear();
        for (SpellBeamClientState.Beam beam : SpellBeamClientState.beams()) {
            Frame frame = Frame.of(beam, now, partial, camera);
            if (frame != null) {
                FRAMES.put(beam, frame);
            }
        }

        VertexConsumer dark = buffer.getBuffer(ModRenderTypes.AVADA_SHADOW);
        for (SpellBeamClientState.Beam beam : SpellBeamClientState.beams()) {
            Frame frame = FRAMES.get(beam);
            BeamStyle style = beam.style;
            if (frame != null && !(style.sheathDark && style.sheathWidth > 0.0f)) {
                // A light spell against a bright sky washes out: give it a faint dark backing, by daylight only.
                Vec3 head = frame.head().add(camera);
                float daylight = minecraft.level.getMaxLocalRawBrightness(net.minecraft.core.BlockPos.containing(head))
                        / 15.0f;
                if (daylight > 0.3f) {
                    ribbon(dark, m, frame.path, frame.widths(style.glowWidth * 0.75f),
                            frame.alphas(0.14f * (daylight - 0.3f) / 0.7f, true), 1.0f, 1.0f, SMOKE_R, SMOKE_G, SMOKE_B);
                }
            }
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
        for (SpellBeamClientState.Scenery item : SpellBeamClientState.scenery()) {
            if (item.kind == SpellBeamClientState.SCENERY_HELD || item.kind == SpellBeamClientState.SCENERY_WISP) {
                drawHeldDark(dark, m, heldOf(item, now), now, camera);
            }
        }
        for (SpellBeamClientState.Clash clash : SpellBeamClientState.clashes()) {
            drawClashDark(dark, m, clash, now, camera);
        }
        buffer.endBatch(ModRenderTypes.AVADA_SHADOW);

        VertexConsumer light = buffer.getBuffer(ModRenderTypes.AVADA_LIGHT);
        for (SpellBeamClientState.Muzzle muzzle : SpellBeamClientState.muzzles()) {
            drawMuzzle(light, m, muzzle, now, camera);
        }
        for (SpellBeamClientState.Scenery item : SpellBeamClientState.scenery()) {
            drawSceneryLight(light, m, item, now, camera);
        }
        for (SpellBeamClientState.Beam beam : SpellBeamClientState.beams()) {
            Frame frame = FRAMES.get(beam);
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
        for (SpellBeamClientState.Clash clash : SpellBeamClientState.clashes()) {
            drawClashLight(light, m, clash, now, camera);
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

    // ---- a beam held between two points (Priori Incantatem) -----------------------------------------------------

    /** The dark of a held beam: its sheath, if the spell has a dark one. */
    static void drawHeldDark(VertexConsumer out, Matrix4f m, SpellBeamClientState.Beam beam, double now, Vec3 camera) {
        Frame f = Frame.of(beam, now, 1.0f, camera);
        BeamStyle s = beam.style;
        if (f != null && s.sheathDark && s.sheathWidth > 0.0f) {
            ribbon(out, m, f.path, f.widths(s.sheathWidth), f.alphas(s.sheathAlpha, true), 1.0f, 1.0f,
                    SMOKE_R, SMOKE_G, SMOKE_B);
        }
    }

    /** The light of a held beam: glow, body, core, strands and beads, as in flight. */
    static void drawHeldLight(VertexConsumer out, Matrix4f m, SpellBeamClientState.Beam beam, double now, Vec3 camera) {
        Frame f = Frame.of(beam, now, 1.0f, camera);
        if (f != null) {
            drawBeam(out, m, f);
            drawStrands(out, m, f);
            drawBeads(out, m, f);
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
        if (s.bodyRainbow) {
            rainbowRibbon(out, m, f, f.widths(s.glowWidth), f.alphas(s.glowAlpha * flicker, false));
            rainbowRibbon(out, m, f, f.widths(s.bodyWidth), f.alphas(s.bodyAlpha * flicker, false));
        } else {
            ribbon(out, m, f.path, f.widths(s.glowWidth), f.alphas(s.glowAlpha * flicker, false), 1.0f, 1.0f,
                    s.glowR, s.glowG, s.glowB);
            ribbon(out, m, f.path, f.widths(s.bodyWidth), f.alphas(s.bodyAlpha * flicker, false), 1.0f, 1.0f,
                    s.bodyR, s.bodyG, s.bodyB);
        }
        ribbon(out, m, f.path, f.widths(s.coreWidth), f.alphas(1.0f, false), 1.0f, 1.0f, s.coreR, s.coreG, s.coreB);
    }

    /** A strip whose colour runs through the rainbow along its length and over time, a short piece at a time. */
    private static void rainbowRibbon(VertexConsumer out, Matrix4f m, Frame f, float[] width, float[] alpha) {
        int n = f.path.length;
        for (int i = 0; i < n - 1; i += 2) {
            int end = Math.min(n - 1, i + 2);
            int count = end - i + 1;
            Vec3[] piece = new Vec3[count];
            float[] w = new float[count];
            float[] a = new float[count];
            System.arraycopy(f.path, i, piece, 0, count);
            System.arraycopy(width, i, w, 0, count);
            System.arraycopy(alpha, i, a, 0, count);
            float hue = (float) ((f.fromWand[i] * 0.12 - f.age * 0.03) % 1.0);
            int rgb = Mth.hsvToRgb(hue < 0.0f ? hue + 1.0f : hue, 0.6f, 1.0f);
            ribbon(out, m, piece, w, a, 1.0f, 1.0f, ((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f,
                    (rgb & 0xFF) / 255.0f);
        }
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
        double[] fromWand = TRAIL_DISTANCES.get(beam);
        if (fromWand == null || fromWand.length != n) {
            fromWand = new double[n];
            for (int i = 1; i < n; i++) {
                fromWand[i] = fromWand[i - 1] + trail.get(i).distanceTo(trail.get(i - 1));
            }
            TRAIL_DISTANCES.put(beam, fromWand);
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
                float far = 1.0f - smoothstep(32.0f, 56.0f, (float) c.length());
                billboard(out, m, c, size + s.wakeGrow * l,
                        alpha * far * (1.0f - l) * (1.0f - l) * smoothstep(0.0f, 1.5f, age),
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
        float age = (float) (now - impact.start) - IMPACT_HOLD;
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

        if (impact.kind == SpellBeamFx.SHIELD) {
            drawShieldRipple(out, m, impact, pos, age);
        }
        if (impact.kind == SpellBeamFx.GLOW) {
            // The magic took: a soft swell of the spell's light and motes rising out of it.
            float swell = smoothstep(0.0f, 3.0f, age) * (1.0f - smoothstep(6.0f, SpellBeamClientState.IMPACT_TICKS, age));
            billboard(out, m, pos, 1.3f * scale, 0.5f * swell, s.glowR, s.glowG, s.glowB, false);
            billboard(out, m, pos, 0.3f * scale, 0.8f * swell, s.coreR, s.coreG, s.coreB, true);
            for (int k = 0; k < 10; k++) {
                float life = age - k * 1.2f;
                if (life < 0.0f || life > 16.0f) {
                    continue;
                }
                Vec3 mote = pos.add(randomDirection(impact.seed + k * 17).scale(0.45 * scale)).add(0.0, 0.06 * life, 0.0);
                billboard(out, m, mote, 0.05f, 0.9f * (1.0f - life / 16.0f), s.coreR, s.coreG, s.coreB, true, 2, 8);
            }
            return;
        }
        // The flash: swells in three ticks, gone in eight. Then the glow that takes a second to drain.
        float flash = (1.0f - smoothstep(0.0f, 0.3f, f)) * s.impactFlash * bright;
        float flashRadius = (0.6f + 2.8f * smoothstep(0.0f, 0.12f, f)) * scale;
        billboard(out, m, pos, flashRadius, Math.min(1.0f, flash), s.coreR, s.coreG, s.coreB, true);
        billboard(out, m, pos, 1.9f * scale, s.impactGlow * (1.0f - f) * (1.0f - f) * bright, s.glowR, s.glowG, s.glowB,
                false);
        if (age < IMPACT_HOLD) {
            // The moment of contact: a tight white-hot point, everything else held back.
            billboard(out, m, pos, 0.4f * scale, bright, 1.0f, 1.0f, 1.0f, true);
            return;
        }
        age -= IMPACT_HOLD;
        f = age / SpellBeamClientState.IMPACT_TICKS;

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

    /** A spell stopped on a shield: three pale ripples running out across it from the point it struck. */
    private static void drawShieldRipple(VertexConsumer out, Matrix4f m, SpellBeamClientState.Impact impact, Vec3 pos,
                                         float age) {
        Vec3[] axes = frame(impact.dir);
        for (int k = 0; k < 3; k++) {
            float t = (age - k * 2.5f) / 12.0f;
            if (t < 0.0f || t >= 1.0f) {
                continue;
            }
            float radius = 0.2f + 1.7f * (1.0f - (1.0f - t) * (1.0f - t));
            float alpha = 0.7f * (1.0f - t) * (1.0f - t);
            ring(out, m, pos, axes[0], axes[1], radius, 0.16f, alpha * 0.4f, 0.55f, 0.8f, 1.0f, 40);
            ring(out, m, pos, axes[0], axes[1], radius, 0.04f, alpha, 0.9f, 0.96f, 1.0f, 40);
        }
        float flash = Math.max(0.0f, 1.0f - age / 5.0f);
        billboard(out, m, pos, 0.9f, 0.5f * flash, 0.7f, 0.88f, 1.0f, false);
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
        if (s.doubleRing) {
            // A second shockwave three ticks behind the first, a little smaller.
            float second = Math.min(1.0f, Math.max(0.0f, age - 3.0f) / 9.0f);
            if (age > 3.0f && second < 1.0f) {
                float e2 = 1.0f - (1.0f - second) * (1.0f - second);
                float r2 = s.impactRing * 0.7f * scale * (s.ringInward ? 1.0f - e2 * 0.92f : e2);
                float a2 = 0.6f * (float) Math.pow(1.0f - second, 1.5) * bright;
                ring(out, m, pos, right, up, r2, 0.3f * scale, a2 * 0.35f, s.glowR, s.glowG, s.glowB, 48);
                ring(out, m, pos, right, up, r2, 0.08f * scale, a2, s.coreR, s.coreG, s.coreB, 48);
            }
        }
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
        if (s.parallelSlashes) {
            // A blade's cuts: straight, side by side, across the line the beam came in on.
            Vec3[] axes = frame(impact.dir);
            Vec3 across = axes[0].scale(0.8).add(axes[1].scale(0.6)).normalize();
            Vec3 stack = impact.dir.cross(across).normalize();
            float open = smoothstep(0.0f, 1.5f, age);
            for (int a = 0; a < s.impactArcs; a++) {
                double offset = (a - (s.impactArcs - 1) * 0.5) * 0.22 * scale;
                Vec3 centre = pos.add(stack.scale(offset));
                Vec3 half = across.scale(0.85 * scale * open);
                Vec3[] cut = {centre.subtract(half), centre, centre.add(half)};
                float[] w = {0.004f, 0.02f, 0.004f};
                float[] al = {0.0f, fade, 0.0f};
                ribbon(out, m, cut, w, al, 3.5f, 0.35f, s.glowR, s.glowG, s.glowB);
                ribbon(out, m, cut, w, al, 1.0f, 1.0f, s.coreR, s.coreG, s.coreB);
            }
            return;
        }
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

    // ---- two beams meeting -------------------------------------------------------------------------------------

    private static final int CLASH_ARCS = 8;
    private static final int CLASH_SPARKS = 36;
    private static final int CLASH_SHARDS = 28;
    private static final float WHITE_R = 0.95f;
    private static final float WHITE_G = 0.97f;
    private static final float WHITE_B = 1.0f;

    private static void drawClashDark(VertexConsumer out, Matrix4f m, SpellBeamClientState.Clash c, double now,
                                      Vec3 camera) {
        if (!c.dark()) {
            return;
        }
        float age = (float) (now - c.start);
        if (age < 0.0f) {
            return;
        }
        Vec3 pos = c.pos.subtract(camera);
        float lock = c.lock();
        if (age < lock + 2.0f) {
            // A hole of dark behind the knot for the light to burn against.
            float grow = smoothstep(0.0f, lock, age);
            billboard(out, m, pos, 1.1f * grow + 0.3f, 0.45f, SMOKE_R, SMOKE_G, SMOKE_B, false);
        }
        float d = age - lock;
        if (d < 0.0f) {
            return;
        }
        float f = d / (SpellBeamClientState.CLASH_TICKS - lock);
        float fade = (float) Math.pow(1.0f - f, 1.5);
        float size = c.mutual() ? 1.0f : 0.6f;
        billboard(out, m, pos, (0.5f + 3.2f * smoothstep(0.0f, 12.0f, d)) * size, 0.6f * fade,
                SMOKE_R, SMOKE_G, SMOKE_B, false);
        for (int k = 0; k < 10; k++) {
            Vec3 dir = randomDirection(c.seed + k * 7);
            float left = 1.0f - Math.min(1.0f, d / 14.0f);
            float thrown = 1.0f - left * left * left;
            Vec3 centre = pos.add(dir.scale(3.0 * size * thrown)).add(0.0, 0.03 * d, 0.0);
            billboard(out, m, centre, (0.4f + 0.06f * d) * size, 0.4f * fade, SMOKE_R, SMOKE_G, SMOKE_B, false, 4, 12);
        }
    }

    private static void drawClashLight(VertexConsumer out, Matrix4f m, SpellBeamClientState.Clash c, double now,
                                       Vec3 camera) {
        float age = (float) (now - c.start);
        if (age < 0.0f) {
            return;
        }
        Vec3 pos = c.pos.subtract(camera);
        BeamStyle a = c.styleA;
        BeamStyle b = c.styleB;
        float lock = c.lock();
        Vec3[] across = frame(c.axis);

        if (age < lock + 2.0f) {
            drawClashLock(out, m, c, pos, age, lock, across);
        }
        float d = age - lock;
        if (d < 0.0f) {
            return;
        }
        float f = d / (SpellBeamClientState.CLASH_TICKS - lock);
        if (!c.mutual()) {
            drawClashShatter(out, m, c, pos, d, f);
            return;
        }

        // The blast: a white flash, each spell's glow, and a sphere of light thrown out.
        float flash = 1.0f - smoothstep(0.0f, 8.0f, d);
        billboard(out, m, pos, 0.8f + 4.0f * smoothstep(0.0f, 3.0f, d), flash, WHITE_R, WHITE_G, WHITE_B, true);
        float glow = 0.45f * (1.0f - f) * (1.0f - f);
        billboard(out, m, pos.add(c.dirA.scale(-0.3)), 2.6f, glow, a.glowR, a.glowG, a.glowB, false);
        billboard(out, m, pos.add(c.dirB.scale(-0.3)), 2.6f, glow, b.glowR, b.glowG, b.glowB, false);
        float shell = Math.min(1.0f, d / 8.0f);
        if (shell < 1.0f) {
            Vec3 toCamera = pos.lengthSqr() < 1.0E-6 ? c.axis : pos.scale(-1.0).normalize();
            Vec3[] facing = frame(toCamera);
            float radius = 5.0f * (1.0f - (1.0f - shell) * (1.0f - shell));
            ring(out, m, pos, facing[0], facing[1], radius, 0.3f, 0.5f * (float) Math.pow(1.0f - shell, 1.5),
                    WHITE_R, WHITE_G, WHITE_B, 56);
        }

        // Shockwaves: one in each spell's colour across the line they pushed along, one flat on the air.
        shockwave(out, m, pos, across[0], across[1], d, 6.5f, a.glowR, a.glowG, a.glowB, a.coreR, a.coreG, a.coreB);
        shockwave(out, m, pos, across[0], across[1], d - 2.0f, 5.5f, b.glowR, b.glowG, b.glowB, b.coreR, b.coreG,
                b.coreB);
        shockwave(out, m, pos, new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0), d - 1.0f, 7.5f, WHITE_R * 0.8f,
                WHITE_G * 0.8f, WHITE_B * 0.8f, WHITE_R, WHITE_G, WHITE_B);

        // Shards of both, flung out and falling.
        for (int k = 0; k < CLASH_SHARDS; k++) {
            int seed = c.seed + k * 41;
            BeamStyle st = (k & 1) == 0 ? a : b;
            Vec3 v = randomDirection(seed).scale(0.3 + 0.25 * hash(seed, 1, 1));
            shard(out, m, pos, v, 0.02f, d, Mth.clamp(1.0f - d / 22.0f, 0.0f, 1.0f), st.coreR, st.coreG, st.coreB);
        }

        // Mist of both colours rolling out.
        for (int k = 0; k < 10; k++) {
            BeamStyle st = (k & 1) == 0 ? a : b;
            Vec3 dir = randomDirection(c.seed + k * 13);
            Vec3 centre = pos.add(dir.scale(2.2 * (1.0f - (1.0f - f) * (1.0f - f))));
            billboard(out, m, centre, 0.6f + 1.0f * f, 0.2f * (float) Math.pow(1.0f - f, 1.5),
                    st.glowR, st.glowG, st.glowB, false, 3, 12);
        }

        // The scar: the two lights spinning where they met, shedding a few falling motes as it dies.
        float scar = smoothstep(4.0f, 8.0f, d) * (1.0f - f);
        if (scar > 0.003f) {
            Vec3 spinA = rotate(across[0], c.axis, d * 0.35);
            Vec3 spinB = rotate(across[1], spinA, d * 0.28);
            Vec3 tiltA = spinA.cross(c.axis).normalize();
            Vec3 tiltB = spinB.cross(spinA).normalize();
            ring(out, m, pos, spinA, c.axis, 0.38f, 0.05f, 0.75f * scar, a.coreR, a.coreG, a.coreB, 28);
            ring(out, m, pos, tiltA, spinB, 0.3f, 0.05f, 0.75f * scar, b.coreR, b.coreG, b.coreB, 28);
            ring(out, m, pos, tiltB, spinA, 0.46f, 0.12f, 0.25f * scar, WHITE_R, WHITE_G, WHITE_B, 28);
            for (int k = 0; k < 10; k++) {
                int seed = c.seed + k * 97;
                float life = d - 4.0f - k * 1.6f;
                if (life < 0.0f || life > 14.0f) {
                    continue;
                }
                BeamStyle st = (k & 1) == 0 ? a : b;
                Vec3 mote = pos.add(randomDirection(seed).scale(0.3)).add(0.0, -0.025 * life, 0.0);
                float twinkle = 0.5f + 0.5f * Mth.sin(life * 1.1f + k);
                billboard(out, m, mote, 0.05f, 0.9f * (1.0f - life / 14.0f) * twinkle, st.coreR, st.coreG, st.coreB,
                        true, 2, 8);
            }
        }
    }

    /**
     * The lock: both lights knotted and straining, each pushing into the other from its own side, swelling, with
     * crackling arcs of each colour and sparks ground off across the line they push along, then sucked into a
     * point with one last pop.
     */
    private static void drawClashLock(VertexConsumer out, Matrix4f m, SpellBeamClientState.Clash c, Vec3 pos,
                                      float age, float lock, Vec3[] across) {
        BeamStyle a = c.styleA;
        BeamStyle b = c.styleB;
        float grow = smoothstep(0.0f, lock, age);
        float collapse = age < lock ? 1.0f : 1.0f - smoothstep(lock, lock + 2.0f, age);
        // The knot slides back and forth along the line the beams push along as each gains and gives ground.
        pos = pos.add(c.axis.scale(0.28 * grow * Mth.sin(age * 0.7f)));
        float strain = 1.0f + 0.25f * Mth.sin(age * 2.3f);
        float halo = (0.45f + 0.55f * grow) * collapse;
        billboard(out, m, pos.add(c.dirA.scale(-0.16 * strain)), halo, 0.6f, a.glowR, a.glowG, a.glowB, false);
        billboard(out, m, pos.add(c.dirB.scale(-0.16 / strain)), halo, 0.6f, b.glowR, b.glowG, b.glowB, false);
        billboard(out, m, pos, (0.15f + 0.4f * grow) * collapse * strain, 0.95f, WHITE_R, WHITE_G, WHITE_B, true);

        // Arcs of each colour, re-struck every tick.
        int strike = (int) Math.floor(age);
        Vec3[] path = new Vec3[ARC_SEGMENTS + 1];
        float[] alpha = new float[ARC_SEGMENTS + 1];
        float[] width = new float[ARC_SEGMENTS + 1];
        for (int k = 0; k < CLASH_ARCS; k++) {
            int seed = c.seed + k * 389 + strike * 6151;
            BeamStyle st = (k & 1) == 0 ? a : b;
            Vec3 span = randomDirection(seed + 3).scale((0.5 + 1.1 * grow * hash(seed, 2, 2)) * collapse + 0.05);
            Vec3[] axes = frame(span.normalize());
            double length = span.length();
            for (int i = 0; i <= ARC_SEGMENTS; i++) {
                float t = i / (float) ARC_SEGMENTS;
                double jag = (i == 0 || i == ARC_SEGMENTS) ? 0.0 : length * 0.25 * Math.sin(Math.PI * t);
                path[i] = pos.add(span.scale(t))
                        .add(axes[0].scale(jag * (hash(seed, i, 7) * 2.0 - 1.0)))
                        .add(axes[1].scale(jag * (hash(seed, i, 8) * 2.0 - 1.0)));
                alpha[i] = collapse * (1.0f - 0.6f * t);
                width[i] = 0.016f - 0.008f * t;
            }
            ribbon(out, m, path, width, alpha, 4.0f, 0.3f, st.glowR, st.glowG, st.glowB);
            ribbon(out, m, path, width, alpha, 1.0f, 1.0f, st.coreR, st.coreG, st.coreB);
        }

        // Sparks ground off the meeting, thrown out across the line the beams push along.
        for (int k = 0; k < CLASH_SPARKS; k++) {
            int seed = c.seed + k * 53;
            float born = k / (float) CLASH_SPARKS * lock;
            float t = age - born;
            if (t < 0.0f || t > 10.0f) {
                continue;
            }
            BeamStyle st = (k & 1) == 0 ? a : b;
            double angle = hash(seed, 1, 0) * Math.PI * 2.0;
            Vec3 v = across[0].scale(Math.cos(angle)).add(across[1].scale(Math.sin(angle)))
                    .scale(0.22 + 0.2 * hash(seed, 2, 0)).add(c.axis.scale((hash(seed, 3, 0) - 0.5) * 0.06));
            shard(out, m, pos, v, 0.015f, t, 1.0f - t / 10.0f, st.coreR, st.coreG, st.coreB);
        }

        // The pop as it is sucked into a point.
        float pop = Math.max(0.0f, 1.0f - Math.abs(age - lock - 0.5f) / 1.2f);
        billboard(out, m, pos, 1.0f, pop, WHITE_R, WHITE_G, WHITE_B, true);
    }

    /** One beam broke the other: the loser shatters back along the winner's line, the winner punches through. */
    private static void drawClashShatter(VertexConsumer out, Matrix4f m, SpellBeamClientState.Clash c, Vec3 pos,
                                         float d, float f) {
        BeamStyle win = c.winner();
        BeamStyle lose = c.loser();
        Vec3 through = c.winnerDir();
        float flash = 1.0f - smoothstep(0.0f, 6.0f, d);
        billboard(out, m, pos, 0.6f + 2.2f * smoothstep(0.0f, 3.0f, d), flash, win.coreR, win.coreG, win.coreB, true);
        billboard(out, m, pos, 1.8f, 0.5f * (1.0f - f) * (1.0f - f), lose.glowR, lose.glowG, lose.glowB, false);
        Vec3[] across = frame(through);
        shockwave(out, m, pos, across[0], across[1], d, 3.5f, win.glowR, win.glowG, win.glowB, win.coreR, win.coreG,
                win.coreB);
        for (int k = 0; k < CLASH_SHARDS; k++) {
            int seed = c.seed + k * 41;
            Vec3 v = through.scale(0.3 + 0.25 * hash(seed, 1, 1)).add(randomDirection(seed).scale(0.15));
            float r = (k & 1) == 0 ? lose.coreR : lose.glowR;
            float g = (k & 1) == 0 ? lose.coreG : lose.glowG;
            float b = (k & 1) == 0 ? lose.coreB : lose.glowB;
            shard(out, m, pos, v, 0.015f, d, Mth.clamp(1.0f - d / 18.0f, 0.0f, 1.0f), r, g, b);
        }
    }

    /** A ring of light thrown out across {@code right}/{@code up}, {@code d} ticks into its run (none before 0). */
    private static void shockwave(VertexConsumer out, Matrix4f m, Vec3 pos, Vec3 right, Vec3 up, float d, float reach,
                                  float gr, float gg, float gb, float cr, float cg, float cb) {
        float run = d / 11.0f;
        if (run < 0.0f || run >= 1.0f) {
            return;
        }
        float radius = reach * (1.0f - (1.0f - run) * (1.0f - run));
        float alpha = 0.8f * (float) Math.pow(1.0f - run, 1.5);
        ring(out, m, pos, right, up, radius, 0.45f, alpha * 0.35f, gr, gg, gb, 56);
        ring(out, m, pos, right, up, radius, 0.11f, alpha, cr, cg, cb, 56);
    }

    /** A streak flung from {@code pos} at {@code v} a tick, falling, seen {@code t} ticks in. */
    private static void shard(VertexConsumer out, Matrix4f m, Vec3 pos, Vec3 v, float gravity, float t, float alpha,
                              float r, float g, float b) {
        Vec3 p1 = pos.add(v.scale(t)).add(0.0, -0.5 * gravity * t * t, 0.0);
        float before = Math.max(0.0f, t - 1.4f);
        Vec3 p0 = pos.add(v.scale(before)).add(0.0, -0.5 * gravity * before * before, 0.0);
        streak(out, m, p0, p1, 0.022f, alpha, r, g, b);
    }
}
