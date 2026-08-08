package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.PatronusShield;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws the non-corporeal Patronus as a solid shaft of light thrown from the wand.
 *
 * <p>Nested hollow shells were the obvious way to build this and the wrong one: with additive
 * blending you see every shell wall edge-on, so the beam read as a handful of separate ribbons.
 * Smooth volumetric light needs geometry that never presents an edge to the camera, which means
 * camera-facing surfaces carrying a soft gradient.
 *
 * <p>One billboard alone won't do either. A billboard is built perpendicular to the view, so when
 * the caster looks down their own beam -- exactly the first-person case -- it collapses to nothing.
 * So this draws two forms and cross-fades them on view alignment: a camera-facing ribbon that
 * carries the beam when seen from the side, and a stack of soft discs that carries it when seen
 * end-on. Both are gradients that fall to zero alpha at their edges, so wherever they overlap they
 * blend instead of seaming.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class PatronusShieldRenderer {

    private static final int RIBBON_SEGMENTS = 32;
    private static final int BLOOM_RINGS = 5;
    private static final int BLOOM_SEGMENTS = 20;
    private static final int CAP_RINGS = 16;
    private static final int CAP_SEGMENTS = 40;

    /**
     * The shield proper: curved barriers across the cone's mouth, nearest and brightest first.
     * Fractions of the full reach, so the front barrier fills the view and the ones behind it
     * show how deep the ward actually goes.
     */
    private static final float[] CAP_DISTANCE = {0.22f, 0.42f, 0.65f, 0.88f};
    private static final float[] CAP_ALPHA = {1.00f, 0.55f, 0.32f, 0.18f};

    /** Width and strength of each haze layer. Faint: this only fills volume between barriers. */
    private static final float[] LAYER_WIDTH = {0.24f, 0.58f, 1.00f};
    private static final float[] LAYER_ALPHA = {0.30f, 0.18f, 0.09f};

    /** Radius of the flare at the wand tip, in blocks. */
    private static final float BLOOM_RADIUS = 1.5f;

    /** Near-white, barely cold. The core pushes to pure white. */
    private static final float RED = 0.86f;
    private static final float GREEN = 0.95f;
    private static final float BLUE = 1.00f;

    private PatronusShieldRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (PatronusShieldClientState.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        float partialTick = event.getPartialTick();

        // Identity, deliberately. LevelRenderer already pushed the camera rotation onto
        // RenderSystem's modelview stack before this point, so multiplying in the event's matrix
        // would apply that rotation a second time and swing the beam away as the caster turns.
        // Vanilla does the same thing: the PoseStack it hands to entity renderers starts empty
        // and carries only a camera-relative translation. Positions below are made camera-relative
        // in double precision instead of baking a large translation into this float matrix.
        Matrix4f matrix = new Matrix4f();

        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(ModRenderTypes.PATRONUS_LIGHT);

        boolean drewAnything = false;
        for (Player player : minecraft.level.players()) {
            if (!PatronusShieldClientState.isChanneling(player.getId()) || !player.isAlive()) {
                continue;
            }
            // Your own beam points away from you, so in first person you are looking straight
            // down its throat. Drawn at full strength that is a white-out over the crosshair.
            boolean ownFirstPerson = player == minecraft.player
                    && minecraft.options.getCameraType().isFirstPerson();
            drawShield(consumer, matrix, player, cameraPos, partialTick, ownFirstPerson);
            drewAnything = true;
        }

        if (drewAnything) {
            buffer.endBatch(ModRenderTypes.PATRONUS_LIGHT);
        }
    }

    /** Stale casters must not survive into the next world. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PatronusShieldClientState.clear();
    }

    private static void drawShield(VertexConsumer consumer, Matrix4f matrix, Player player,
                                   Vec3 cameraPos, float partialTick, boolean ownFirstPerson) {
        Vec3 axis = player.getViewVector(partialTick).normalize();

        Vec3 side = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-4D) {
            side = axis.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize();
        Vec3 up = side.cross(axis).normalize();

        Vec3 eye = new Vec3(
                Mth.lerp(partialTick, player.xo, player.getX()),
                Mth.lerp(partialTick, player.yo, player.getY()) + player.getEyeHeight(),
                Mth.lerp(partialTick, player.zo, player.getZ())
        );
        // Everything from here on is relative to the camera, which sits at the origin. Doing the
        // subtraction once here in double precision keeps the geometry exact however far from
        // world origin the caster is; baking it into the float matrix would not.
        Vec3 apex = eye.add(axis.scale(0.5D)).add(side.scale(-0.30D)).add(up.scale(-0.12D))
                .subtract(cameraPos);

        float length = (float) PatronusShield.RANGE;
        float mouth = (float) PatronusShield.MOUTH_RADIUS;

        // A slow breath, plus a faster shimmer, so the light never sits still.
        float age = player.tickCount + partialTick;
        float pulse = 0.90f + 0.10f * Mth.sin(age * 0.17f) + 0.035f * Mth.sin(age * 0.71f);

        // How end-on the ward is. 0 = seen fully from the side, 1 = looking straight down the aim.
        // The camera is the origin in this space, so the vector towards it is just the negated
        // position of the midpoint.
        Vec3 shaftMid = apex.add(axis.scale(length * 0.45D));
        Vec3 toCamera = shaftMid.scale(-1.0D);
        double toCameraLen = toCamera.length();
        float alignment = toCameraLen < 1.0E-4D
                ? 1.0f
                : (float) Math.abs(toCamera.scale(1.0D / toCameraLen).dot(axis));

        // The haze is a camera-facing sheet, so it degenerates when viewed end-on. Faded out there
        // and left to the barriers, which face you squarely at exactly that angle.
        float ribbonWeight = 1.0f - smoothstep(0.55f, 0.97f, alignment);

        if (ribbonWeight > 0.01f) {
            Vec3 perpendicular = axis.cross(toCamera);
            if (perpendicular.lengthSqr() < 1.0E-6D) {
                perpendicular = side;
            }
            perpendicular = perpendicular.normalize();

            for (int layer = 0; layer < LAYER_WIDTH.length; layer++) {
                drawRibbon(consumer, matrix, apex, axis, perpendicular, length,
                        mouth * LAYER_WIDTH[layer], LAYER_ALPHA[layer] * pulse * ribbonWeight, layer == 0);
            }
        }

        // The shield itself: curved barriers spanning the cone, square-on to wherever you aim.
        // Every point sits exactly one radius from the wand and the bright rim falls exactly on
        // the cone's edge, so the barrier you see is the boundary that actually stops Dementors.
        float halfAngle = (float) Math.acos(PatronusShield.COS_HALF_ANGLE);
        float capStrength = pulse * (ownFirstPerson ? 0.55f : 1.0f);
        for (int cap = 0; cap < CAP_DISTANCE.length; cap++) {
            drawShieldCap(consumer, matrix, apex, axis, side, up,
                    length * CAP_DISTANCE[cap], halfAngle, CAP_ALPHA[cap] * capStrength, age);
        }

        // The hot spot at the wand tip, always facing the viewer. Skipped for your own first-person
        // cast: the apex sits half a block from the camera, so the flare would cover the crosshair.
        if (!ownFirstPerson) {
            drawBloom(consumer, matrix, apex, BLOOM_RADIUS, 1.0f * pulse);
        }
    }

    /**
     * A camera-facing sheet down the beam, transparent at both edges and brightest along its
     * centreline. Because it always faces the viewer it can never be caught edge-on.
     */
    private static void drawRibbon(VertexConsumer consumer, Matrix4f matrix, Vec3 apex, Vec3 axis,
                                   Vec3 perpendicular, float length, float maxHalfWidth,
                                   float alpha, boolean core) {
        for (int i = 0; i < RIBBON_SEGMENTS; i++) {
            float t0 = i / (float) RIBBON_SEGMENTS;
            float t1 = (i + 1) / (float) RIBBON_SEGMENTS;

            float a0 = alpha * lengthProfile(t0);
            float a1 = alpha * lengthProfile(t1);
            if (a0 <= 0.002f && a1 <= 0.002f) {
                continue;
            }

            float w0 = maxHalfWidth * widthProfile(t0);
            float w1 = maxHalfWidth * widthProfile(t1);

            Vec3 c0 = apex.add(axis.scale(length * t0));
            Vec3 c1 = apex.add(axis.scale(length * t1));
            Vec3 e0 = perpendicular.scale(w0);
            Vec3 e1 = perpendicular.scale(w1);

            // Left half: transparent outer edge into the bright centreline.
            quad(consumer, matrix,
                    c0.subtract(e0), 0.0f,
                    c1.subtract(e1), 0.0f,
                    c1, a1,
                    c0, a0, core);
            // Right half.
            quad(consumer, matrix,
                    c0, a0,
                    c1, a1,
                    c1.add(e1), 0.0f,
                    c0.add(e0), 0.0f, core);
        }
    }

    /**
     * A curved barrier of light bulging out along the aim: a spherical cap centred on the wand,
     * so it reads as a shield held in front of you rather than a shaft going away from you.
     *
     * <p>Because every vertex sits {@code radius} from the wand and the cap is swept out to the
     * cone's own half-angle, the bright rim traces the exact edge of the volume that repels
     * Dementors. What you see is the boundary, not an approximation of it.
     */
    private static void drawShieldCap(VertexConsumer consumer, Matrix4f matrix, Vec3 apex, Vec3 axis,
                                      Vec3 side, Vec3 up, float radius, float halfAngle,
                                      float alpha, float age) {
        // Swept slightly past the rim so the edge glow feathers out instead of ending in a cut.
        float overshoot = 1.20f;

        for (int r = 0; r < CAP_RINGS; r++) {
            float u0 = (r / (float) CAP_RINGS) * overshoot;
            float u1 = ((r + 1) / (float) CAP_RINGS) * overshoot;

            float a0 = alpha * capProfile(u0, age);
            float a1 = alpha * capProfile(u1, age);
            if (a0 <= 0.002f && a1 <= 0.002f) {
                continue;
            }

            float theta0 = u0 * halfAngle;
            float theta1 = u1 * halfAngle;

            for (int s = 0; s < CAP_SEGMENTS; s++) {
                double phi0 = (Math.PI * 2.0D / CAP_SEGMENTS) * s;
                double phi1 = (Math.PI * 2.0D / CAP_SEGMENTS) * (s + 1);

                quad(consumer, matrix,
                        capPoint(apex, axis, side, up, theta0, phi0, radius), a0,
                        capPoint(apex, axis, side, up, theta1, phi0, radius), a1,
                        capPoint(apex, axis, side, up, theta1, phi1, radius), a1,
                        capPoint(apex, axis, side, up, theta0, phi1, radius), a0, false);
            }
        }
    }

    /** A point on the barrier: {@code theta} off the aim, {@code phi} around it, at {@code radius}. */
    private static Vec3 capPoint(Vec3 apex, Vec3 axis, Vec3 side, Vec3 up,
                                 float theta, double phi, float radius) {
        double sin = Math.sin(theta);
        Vec3 direction = axis.scale(Math.cos(theta))
                .add(side.scale(Math.cos(phi) * sin))
                .add(up.scale(Math.sin(phi) * sin));
        return apex.add(direction.scale(radius));
    }

    /**
     * Brightness across the barrier as a fraction of the way to its rim: a faint fill you can see
     * through, a hard bright ring on the boundary itself, and ripples travelling outwards.
     */
    private static float capProfile(float u, float age) {
        float rim = (float) Math.exp(-Math.pow((u - 1.0f) / 0.11f, 2.0D));
        float interior = 0.14f * (1.0f - 0.45f * smoothstep(0.35f, 1.0f, u));
        float ripple = 0.075f * (0.5f + 0.5f * Mth.sin(u * 15.0f - age * 0.26f));
        float feather = 1.0f - smoothstep(1.02f, 1.20f, u);
        return (interior + rim * 0.95f + ripple) * feather;
    }

    /** Camera-facing radial glow, for the flare at the wand tip. Positions are camera-relative. */
    private static void drawBloom(VertexConsumer consumer, Matrix4f matrix, Vec3 centre,
                                  float radius, float alpha) {
        // The camera is the origin here, so the direction towards it is simply -centre.
        Vec3 normal = centre.scale(-1.0D);
        if (normal.lengthSqr() < 1.0E-6D) {
            return;
        }
        normal = normal.normalize();

        Vec3 right = normal.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-4D) {
            right = normal.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        right = right.normalize();
        Vec3 top = right.cross(normal).normalize();

        for (int ring = 0; ring < BLOOM_RINGS; ring++) {
            float r0 = radius * (ring / (float) BLOOM_RINGS);
            float r1 = radius * ((ring + 1) / (float) BLOOM_RINGS);
            float a0 = alpha * falloff(ring / (float) BLOOM_RINGS);
            float a1 = alpha * falloff((ring + 1) / (float) BLOOM_RINGS);

            for (int s = 0; s < BLOOM_SEGMENTS; s++) {
                double angle0 = (Math.PI * 2.0D / BLOOM_SEGMENTS) * s;
                double angle1 = (Math.PI * 2.0D / BLOOM_SEGMENTS) * (s + 1);

                quad(consumer, matrix,
                        centre.add(ring(right, top, angle0, r0)), a0,
                        centre.add(ring(right, top, angle1, r0)), a0,
                        centre.add(ring(right, top, angle1, r1)), a1,
                        centre.add(ring(right, top, angle0, r1)), a1, true);
            }
        }
    }

    private static float falloff(float t) {
        float inv = 1.0f - t;
        return inv * inv;
    }

    /** How wide the beam is at {@code t}: a tight throat at the wand flaring toward the mouth. */
    private static float widthProfile(float t) {
        return 0.05f + 0.95f * (float) Math.pow(t, 0.75D);
    }

    /**
     * Brightness down the beam: a short ramp out of the wand so a first-person caster is not
     * staring into white, a long bright body, then a soft diffuse mouth.
     */
    private static float lengthProfile(float t) {
        float ramp = t >= 0.07f ? 1.0f : (t / 0.07f) * (t / 0.07f);
        float fade = 1.0f - 0.68f * (float) Math.pow(t, 1.4D);
        return Math.max(0.0f, ramp * fade);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static Vec3 ring(Vec3 right, Vec3 top, double angle, float radius) {
        return right.scale(Math.cos(angle) * radius).add(top.scale(Math.sin(angle) * radius));
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             Vec3 p0, float a0, Vec3 p1, float a1,
                             Vec3 p2, float a2, Vec3 p3, float a3, boolean core) {
        vertex(consumer, matrix, p0, a0, core);
        vertex(consumer, matrix, p1, a1, core);
        vertex(consumer, matrix, p2, a2, core);
        vertex(consumer, matrix, p3, a3, core);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 pos, float alpha, boolean core) {
        // The core layer burns out to white; the halo keeps the cold silver tint.
        float r = core ? Mth.lerp(0.75f, RED, 1.0f) : RED;
        float g = core ? Mth.lerp(0.75f, GREEN, 1.0f) : GREEN;
        consumer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(r, g, BLUE, alpha);
    }
}
