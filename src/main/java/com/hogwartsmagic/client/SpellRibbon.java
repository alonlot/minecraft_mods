package com.hogwartsmagic.client;

import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.EnumSet;
import java.util.Set;

/**
 * The curved ribbon beam: a band of light that bows out of the caster's wand tip and runs to the spell.
 * Glacius whips out a wide, glassy, twisting ribbon of ice that tears loose from the wand once the shard is a
 * few blocks out. Reparo keeps a thin white tether from wand to spell for its whole flight. The ribbon is a
 * quadratic curve from the wand tip to the orb, lifted in the middle and swaying, drawn as a camera-facing
 * strip in the spell light render type.
 */
public final class SpellRibbon {

    /** Spells drawn with a ribbon. */
    public static final Set<SpellType> RIBBON = EnumSet.of(SpellType.GLACIUS, SpellType.REPARO);

    private static final int SAMPLES = 28;
    /** Length of the ice ribbon behind the shard once it has torn loose from the wand. */
    private static final double ICE_LENGTH = 7.0;
    private static final float[] ICE = {0.55F, 0.86F, 1.0F};
    private static final float[] ICE_EDGE = {0.85F, 0.97F, 1.0F};

    private SpellRibbon() {
    }

    /**
     * Draws the ribbon of {@code beam}. {@code matrix} is the pose at the beam's interpolated position (the
     * entity render origin), so world points are written relative to {@code origin}.
     */
    public static void draw(SpellBeamEntity beam, Matrix4f matrix, MultiBufferSource buffer, float partialTick,
                            Camera camera, Vec3 origin, Vec3 orb) {
        SpellType spell = beam.getSpellType();
        boolean ice = spell == SpellType.GLACIUS;
        float age = beam.tickCount + partialTick;
        float fade = ice ? 1.0F - Mth.clamp((age - 8.0F) / 14.0F, 0.0F, 1.0F) : 1.0F;
        if (beam.hasFreshClientImpact()) {
            fade *= 0.4F;
        }
        if (fade <= 0.01F) {
            return;
        }
        Vec3 hand = handPosition(beam.getOwner(), partialTick, camera);
        if (hand == null) {
            return;
        }
        // Your own whip needs no wait for the wind-up: CastDelay sends Glacius as the arm strikes.
        Vec3 d = orb.subtract(hand);
        double length = d.length();
        if (length < 0.4) {
            return;
        }
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 across = d.cross(up);
        across = across.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : across.normalize();
        double bow = ice ? Math.min(2.2, length * 0.2) : Math.min(1.0, length * 0.08);
        double sway = (ice ? Math.min(0.9, length * 0.1) : Math.min(0.5, length * 0.05)) * Mth.sin(age * 0.55F);
        Vec3 control = hand.add(d.scale(0.45)).add(up.scale(bow)).add(across.scale(sway));
        double s0 = ice ? Math.max(0.0, 1.0 - ICE_LENGTH / length) : 0.0;

        Vec3 cameraPos = camera.getPosition();
        Vec3[] points = new Vec3[SAMPLES + 1];
        Vec3[] sides = new Vec3[SAMPLES + 1];
        float[] widths = new float[SAMPLES + 1];
        float[] alphas = new float[SAMPLES + 1];
        for (int i = 0; i <= SAMPLES; i++) {
            double u = i / (double) SAMPLES; // 0 at the loose end (or wand), 1 at the orb
            double s = s0 + (1.0 - s0) * u;
            Vec3 p = bezier(hand, control, orb, s);
            Vec3 tangent = bezierTangent(hand, control, orb, s);
            Vec3 side = tangent.cross(cameraPos.subtract(p));
            side = side.lengthSqr() < 1.0E-8 ? across : side.normalize();
            points[i] = p.subtract(origin);
            float nearCap = (float) p.distanceTo(cameraPos) * 0.06F; // never wider than a thin line up close
            sides[i] = side;
            float taper = (float) Math.sqrt(Math.sin(Math.PI * Mth.clamp(u * 0.92 + 0.04, 0.0, 1.0)));
            if (ice) {
                float twist = 0.3F + 0.7F * Math.abs(Mth.cos((float) (s * length * 0.8) - age * 0.5F));
                widths[i] = Math.min(0.22F * twist * taper, nearCap);
                alphas[i] = fade * (s0 > 0.0 ? smooth((float) u / 0.3F) : 1.0F);
            } else {
                widths[i] = Math.min(0.035F * (0.6F + 0.4F * taper), nearCap);
                alphas[i] = fade;
            }
        }

        VertexConsumer light = buffer.getBuffer(ModRenderTypes.SPELL_GLOW);
        if (ice) {
            strip(light, matrix, points, sides, widths, 1.0F, 0.0F, alphas, ICE, 0.4F);
            strip(light, matrix, points, sides, widths, 0.35F, 0.0F, alphas, new float[]{1.0F, 1.0F, 1.0F}, 0.5F);
            // Bright glassy edges along both sides of the band.
            strip(light, matrix, points, sides, constant(0.022F), 1.0F, 0.85F, alphas, ICE_EDGE, 0.75F, widths);
            strip(light, matrix, points, sides, constant(0.022F), 1.0F, -0.85F, alphas, ICE_EDGE, 0.75F, widths);
        } else {
            float[] c = SpellFx.lightColor(spell);
            strip(light, matrix, points, sides, widths, 3.2F, 0.0F, alphas, c, 0.35F);
            strip(light, matrix, points, sides, widths, 1.0F, 0.0F, alphas, new float[]{1.0F, 1.0F, 1.0F}, 0.9F);
        }
    }

    private static float[] constant(float value) {
        float[] out = new float[SAMPLES + 1];
        java.util.Arrays.fill(out, value);
        return out;
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static void strip(VertexConsumer consumer, Matrix4f matrix, Vec3[] points, Vec3[] sides, float[] widths,
                              float widthScale, float offset, float[] alphas, float[] c, float alpha) {
        strip(consumer, matrix, points, sides, widths, widthScale, offset, alphas, c, alpha, widths);
    }

    /**
     * One band along the curve: each sample is widened sideways by width * widthScale, centred at
     * offset * offsetWidths (so an edge line can ride on the rim of a wider band).
     */
    private static void strip(VertexConsumer consumer, Matrix4f matrix, Vec3[] points, Vec3[] sides, float[] widths,
                              float widthScale, float offset, float[] alphas, float[] c, float alpha,
                              float[] offsetWidths) {
        for (int i = 0; i < SAMPLES; i++) {
            emit(consumer, matrix, points[i], sides[i], widths[i] * widthScale, offset * offsetWidths[i], c, alpha * alphas[i],
                    points[i + 1], sides[i + 1], widths[i + 1] * widthScale, offset * offsetWidths[i + 1], alpha * alphas[i + 1]);
        }
    }

    private static void emit(VertexConsumer consumer, Matrix4f matrix,
                             Vec3 p0, Vec3 side0, float w0, float off0, float[] c, float a0,
                             Vec3 p1, Vec3 side1, float w1, float off1, float a1) {
        Vec3 c0 = p0.add(side0.scale(off0));
        Vec3 c1 = p1.add(side1.scale(off1));
        vertex(consumer, matrix, c0.add(side0.scale(w0)), 0.0F, c, a0);
        vertex(consumer, matrix, c0.subtract(side0.scale(w0)), 1.0F, c, a0);
        vertex(consumer, matrix, c1.subtract(side1.scale(w1)), 1.0F, c, a1);
        vertex(consumer, matrix, c1.add(side1.scale(w1)), 0.0F, c, a1);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 p, float u, float[] c, float a) {
        // Across the band u runs over the middle row of the glow sprite: bright centre, soft rims.
        SpellFx.vertex(consumer, matrix, (float) p.x, (float) p.y, (float) p.z, u, 0.5F, c[0], c[1], c[2], a);
    }

    private static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, double s) {
        double r = 1.0 - s;
        return a.scale(r * r).add(b.scale(2.0 * r * s)).add(c.scale(s * s));
    }

    private static Vec3 bezierTangent(Vec3 a, Vec3 b, Vec3 c, double s) {
        Vec3 t = b.subtract(a).scale(2.0 * (1.0 - s)).add(c.subtract(b).scale(2.0 * s));
        return t.lengthSqr() < 1.0E-8 ? c.subtract(a).normalize() : t.normalize();
    }

    /**
     * Where the caster's wand tip is: the drawn first-person wand for the local player in first person, else
     * a point out in front of the caster's main-hand shoulder.
     */
    @javax.annotation.Nullable
    static Vec3 handPosition(@javax.annotation.Nullable Entity owner, float partialTick, Camera camera) {
        if (!(owner instanceof LivingEntity caster)) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (caster == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
            Vec3 tip = FirstPersonWand.localTipInWorld(camera);
            if (tip != null) {
                return tip;
            }
        }
        Vec3 drawnTip = WandSwipeLayer.recentTip(caster); // the animated wand, when it is mid-stroke on screen
        if (drawnTip != null) {
            return drawnTip;
        }
        Vec3 look = caster.getViewVector(partialTick);
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        float side = caster.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        return caster.getEyePosition(partialTick).add(look.scale(0.85)).add(right.scale(0.33 * side)).add(0.0, -0.28, 0.0);
    }
}
