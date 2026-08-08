package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.PatronusShield;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Draws the non-corporeal Patronus as actual light rather than particles: a cone of nested,
 * additively blended shells that accumulate into a bright core with a soft volumetric edge,
 * the way a beam looks through mist.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class PatronusShieldRenderer {

    /** Shells from the axis outwards. More shells means a smoother falloff and more geometry. */
    private static final int SHELLS = 5;
    /** Rings along the cone's length; alpha is shaped per ring. */
    private static final int RINGS = 10;
    /** Radial subdivisions. */
    private static final int SEGMENTS = 20;

    /** Cold silver-white, a touch of blue. */
    private static final float RED = 0.80f;
    private static final float GREEN = 0.93f;
    private static final float BLUE = 1.00f;

    /**
     * The light is born a little in front of the wand rather than at the eye, so a
     * first-person caster isn't staring into a wall of white.
     */
    private static final float NEAR_FADE = 0.14f;

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

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(event.getPoseStack());
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(ModRenderTypes.PATRONUS_LIGHT);

        boolean drewAnything = false;
        for (Player player : minecraft.level.players()) {
            if (!PatronusShieldClientState.isChanneling(player.getId()) || !player.isAlive()) {
                continue;
            }
            drawShield(consumer, matrix, player, partialTick);
            drewAnything = true;
        }

        if (drewAnything) {
            buffer.endBatch(ModRenderTypes.PATRONUS_LIGHT);
        }
    }

    /** Stale casters must not survive into the next world. */
    @SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        PatronusShieldClientState.clear();
    }

    private static void drawShield(VertexConsumer consumer, Matrix4f matrix, Player player, float partialTick) {
        Vec3 axis = player.getViewVector(partialTick).normalize();

        // Apex sits at the wand hand: eye height, pushed forward and out to the casting side.
        Vec3 side = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-4D) {
            side = axis.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        side = side.normalize();
        Vec3 up = side.cross(axis).normalize();

        Vec3 eye = new Vec3(
                net.minecraft.util.Mth.lerp(partialTick, player.xo, player.getX()),
                net.minecraft.util.Mth.lerp(partialTick, player.yo, player.getY()) + player.getEyeHeight(),
                net.minecraft.util.Mth.lerp(partialTick, player.zo, player.getZ())
        );
        Vec3 apex = eye.add(axis.scale(0.45D)).add(side.scale(-0.28D)).add(up.scale(-0.12D));

        // A slow breath so the light never looks like a static cardboard cone.
        float age = player.tickCount + partialTick;
        float pulse = 0.88f + 0.12f * (float) Math.sin(age * 0.18f);

        float length = (float) PatronusShield.RANGE;
        float mouth = (float) PatronusShield.MOUTH_RADIUS;

        for (int shell = 0; shell < SHELLS; shell++) {
            // Shell 0 is the tight core; the outermost is the wide, faint halo.
            float shellT = (shell + 1) / (float) SHELLS;
            float shellRadius = mouth * shellT;
            // Core carries most of the brightness, the halo only suggests volume.
            float shellAlpha = 0.30f * (float) Math.pow(1.0f - shellT, 1.7f) + 0.035f;
            shellAlpha *= pulse;

            drawConeShell(consumer, matrix, apex, axis, side, up, length, shellRadius, shellAlpha);
        }
    }

    /** One hollow cone of light, built as quad strips between rings along its length. */
    private static void drawConeShell(VertexConsumer consumer, Matrix4f matrix,
                                      Vec3 apex, Vec3 axis, Vec3 side, Vec3 up,
                                      float length, float maxRadius, float alpha) {
        for (int ring = 0; ring < RINGS; ring++) {
            float t0 = ring / (float) RINGS;
            float t1 = (ring + 1) / (float) RINGS;

            float a0 = alpha * lengthProfile(t0);
            float a1 = alpha * lengthProfile(t1);
            if (a0 <= 0.001f && a1 <= 0.001f) {
                continue;
            }

            float r0 = maxRadius * t0;
            float r1 = maxRadius * t1;
            Vec3 c0 = apex.add(axis.scale(length * t0));
            Vec3 c1 = apex.add(axis.scale(length * t1));

            for (int seg = 0; seg < SEGMENTS; seg++) {
                double angle0 = (Math.PI * 2.0D / SEGMENTS) * seg;
                double angle1 = (Math.PI * 2.0D / SEGMENTS) * (seg + 1);

                Vec3 o0a = offset(side, up, angle0, r0);
                Vec3 o0b = offset(side, up, angle1, r0);
                Vec3 o1a = offset(side, up, angle0, r1);
                Vec3 o1b = offset(side, up, angle1, r1);

                vertex(consumer, matrix, c0.add(o0a), a0);
                vertex(consumer, matrix, c1.add(o1a), a1);
                vertex(consumer, matrix, c1.add(o1b), a1);
                vertex(consumer, matrix, c0.add(o0b), a0);
            }
        }
    }

    /**
     * Brightness along the cone: a quick ramp out of the wand so the caster isn't blinded,
     * a long bright body, then a soft diffuse falloff at the mouth.
     */
    private static float lengthProfile(float t) {
        float ramp = t <= NEAR_FADE ? (t / NEAR_FADE) * (t / NEAR_FADE) : 1.0f;
        float fade = 1.0f - (t * t * 0.82f);
        return Math.max(0.0f, ramp * fade);
    }

    private static Vec3 offset(Vec3 side, Vec3 up, double angle, float radius) {
        return side.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 pos, float alpha) {
        consumer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(RED, GREEN, BLUE, alpha);
    }
}
