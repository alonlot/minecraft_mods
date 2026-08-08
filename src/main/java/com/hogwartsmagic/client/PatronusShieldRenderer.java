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

    private static final int RIBBON_SEGMENTS = 24;
    private static final int DISCS = 16;
    private static final int DISC_SEGMENTS = 24;
    private static final int BLOOM_RINGS = 4;
    private static final int BLOOM_SEGMENTS = 18;

    /** Width and strength of each ribbon layer: a hot core inside a broad soft halo. */
    private static final float[] LAYER_WIDTH = {0.26f, 0.62f, 1.00f};
    private static final float[] LAYER_ALPHA = {0.90f, 0.40f, 0.16f};

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

        // How end-on the beam is. 0 = seen fully from the side, 1 = looking straight down it.
        // The camera is the origin in this space, so the vector towards it is just the negated
        // position of the shaft's midpoint.
        Vec3 shaftMid = apex.add(axis.scale(length * 0.45D));
        Vec3 toCamera = shaftMid.scale(-1.0D);
        double toCameraLen = toCamera.length();
        float alignment = toCameraLen < 1.0E-4D
                ? 1.0f
                : (float) Math.abs(toCamera.scale(1.0D / toCameraLen).dot(axis));

        // Cross-fade, weighted so the pair sums to roughly full strength at every angle.
        float ribbonWeight = 1.0f - smoothstep(0.55f, 0.97f, alignment);
        float discWeight = smoothstep(0.30f, 0.90f, alignment);

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

        if (discWeight > 0.01f) {
            // Discs stack along the view ray, so looking down the beam accumulates all of them at
            // once. Held well back in first person, where that ray is longest.
            float discStrength = pulse * discWeight * (ownFirstPerson ? 0.45f : 1.0f);
            drawDiscStack(consumer, matrix, apex, axis, side, up, length, mouth, discStrength);
        }

        // The hot spot at the wand tip, always facing the viewer. Skipped for your own first-person
        // cast: the apex sits half a block from the camera, so the flare would cover the crosshair.
        if (!ownFirstPerson) {
            drawBloom(consumer, matrix, apex, 0.85f, 0.95f * pulse);
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
     * Soft discs stacked down the beam. Seen end-on these read as a glowing throat of light,
     * which is what the ribbon cannot do.
     */
    private static void drawDiscStack(VertexConsumer consumer, Matrix4f matrix, Vec3 apex, Vec3 axis,
                                      Vec3 side, Vec3 up, float length, float mouth, float strength) {
        for (int d = 0; d < DISCS; d++) {
            float t = (d + 0.5f) / DISCS;
            // Every disc in front of the viewer contributes to the same pixel, so each one has to
            // be far fainter than a ribbon layer or the stack blows out to solid white. The extra
            // near fade keeps the first few from forming a wall right at the wand.
            float alpha = strength * lengthProfile(t) * smoothstep(0.04f, 0.30f, t) * 0.06f;
            if (alpha <= 0.002f) {
                continue;
            }

            float radius = mouth * widthProfile(t);
            Vec3 centre = apex.add(axis.scale(length * t));

            for (int s = 0; s < DISC_SEGMENTS; s++) {
                double angle0 = (Math.PI * 2.0D / DISC_SEGMENTS) * s;
                double angle1 = (Math.PI * 2.0D / DISC_SEGMENTS) * (s + 1);

                Vec3 rim0 = centre.add(ring(side, up, angle0, radius));
                Vec3 rim1 = centre.add(ring(side, up, angle1, radius));

                // Centre bright, rim transparent -- a radial gradient with no visible edge.
                quad(consumer, matrix, centre, alpha, centre, alpha, rim1, 0.0f, rim0, 0.0f, false);
            }
        }
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
