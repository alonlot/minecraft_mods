package com.hogwartsmagic.client;

import com.hogwartsmagic.entity.FiendfyreSerpentEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class FiendfyreSerpentRenderer extends EntityRenderer<FiendfyreSerpentEntity> {
    private static final Map<Integer, Deque<Vec3>> TRAIL_CACHE = new HashMap<>();
    private static final int MAX_TRAIL_POINTS = 24;

    public FiendfyreSerpentRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FiendfyreSerpentEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        Vec3 currentPos = entity.getPosition(partialTick);
        Deque<Vec3> trail = TRAIL_CACHE.computeIfAbsent(entity.getId(), ignored -> new ArrayDeque<>());
        if (trail.isEmpty() || trail.peekFirst().distanceToSqr(currentPos) > 0.0025D) {
            trail.addFirst(currentPos);
        } else {
            trail.removeFirst();
            trail.addFirst(currentPos);
        }
        while (trail.size() > MAX_TRAIL_POINTS) {
            trail.removeLast();
        }

        Vec3 heading = getHeading(entity, trail);
        float age = entity.tickCount + partialTick;
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();
        int light = LightTexture.FULL_BRIGHT;

        drawBody(consumer, matrix, age, light, trail, currentPos, heading);
        drawMane(consumer, matrix, age, light, trail, currentPos);
        drawHead(poseStack, consumer, age, light, heading);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static Vec3 getHeading(FiendfyreSerpentEntity entity, Deque<Vec3> trail) {
        Vec3 heading = entity.getDeltaMovement();
        if (heading.lengthSqr() < 1.0E-4D && trail.size() > 1) {
            Iterator<Vec3> iterator = trail.iterator();
            Vec3 newest = iterator.next();
            Vec3 older = iterator.next();
            heading = newest.subtract(older);
        }
        if (heading.lengthSqr() < 1.0E-4D) {
            float yawRad = (float) Math.toRadians(entity.getYRot());
            heading = new Vec3(-Math.sin(yawRad), 0.0D, Math.cos(yawRad));
        }
        return heading.normalize();
    }

    private static void drawHead(PoseStack poseStack, VertexConsumer consumer, float age, int light, Vec3 heading) {
        float yaw = (float) (Math.atan2(heading.x, heading.z) * (180F / Math.PI));
        float pitch = (float) (Math.atan2(heading.y, Math.sqrt(heading.x * heading.x + heading.z * heading.z)) * (180F / Math.PI));

        poseStack.pushPose();
        poseStack.translate(heading.x * 1.15D, heading.y * 1.15D, heading.z * 1.15D);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));

        Matrix4f matrix = poseStack.last().pose();
        float jawPulse = 0.05f + (float) Math.sin(age * 0.35f) * 0.02f;
        drawBox(consumer, matrix, 0.0f, 0.0f, 0.0f, 1.7f, 0.82f, 0.72f, 1.0f, 0.80f, 0.22f, 0.95f, light);
        drawBox(consumer, matrix, 0.72f, 0.0f, 0.0f, 1.05f, 0.56f + jawPulse, 0.46f, 1.0f, 0.96f, 0.50f, 0.94f, light);
        drawBox(consumer, matrix, 1.40f, 0.04f, 0.0f, 0.42f, 0.22f, 0.18f, 1.0f, 0.99f, 0.76f, 0.94f, light);
        drawFin(consumer, matrix, 0.40f, 0.32f, 0.34f, 0.48f, 0.52f, 1.50f, 0.30f, 0.06f, 0.72f, light);
        drawFin(consumer, matrix, 0.40f, 0.32f, -0.34f, -0.48f, 0.52f, 1.50f, 0.30f, 0.06f, 0.72f, light);
        poseStack.popPose();
    }

    private static void drawBody(VertexConsumer consumer, Matrix4f matrix, float age, int light,
                                 Deque<Vec3> trail, Vec3 currentPos, Vec3 heading) {
        Vec3 previous = currentPos.subtract(heading.scale(0.55D));
        int index = 0;
        for (Vec3 point : trail) {
            if (index == 0) {
                index++;
                continue;
            }

            float progress = Math.min(1.0f, index / (float) Math.max(2, trail.size() - 1));
            float radius = 0.68f * (1.0f - progress * 0.72f) + 0.12f;
            Vec3 relativeStart = previous.subtract(currentPos);
            Vec3 relativeEnd = point.subtract(currentPos);
            float waveLift = (float) Math.sin(age * 0.14f - progress * 5.0f) * 0.08f;

            drawSegment(consumer, matrix,
                    (float) relativeStart.x, (float) relativeStart.y + waveLift, (float) relativeStart.z,
                    (float) relativeEnd.x, (float) relativeEnd.y - waveLift * 0.7f, (float) relativeEnd.z,
                    radius, 1.0f, 0.62f + (1.0f - progress) * 0.18f, 0.12f, 0.70f, light);
            drawSegment(consumer, matrix,
                    (float) relativeStart.x, (float) relativeStart.y + waveLift, (float) relativeStart.z,
                    (float) relativeEnd.x, (float) relativeEnd.y - waveLift * 0.7f, (float) relativeEnd.z,
                    radius * 0.42f, 1.0f, 0.98f, 0.66f, 0.34f, light);

            previous = point;
            index++;
        }
    }

    private static void drawMane(VertexConsumer consumer, Matrix4f matrix, float age, int light,
                                 Deque<Vec3> trail, Vec3 currentPos) {
        int index = 0;
        for (Vec3 point : trail) {
            if (index >= 12) {
                break;
            }
            float progress = index / 11.0f;
            Vec3 relative = point.subtract(currentPos);
            float wave = age * 0.22f - progress * 3.2f;
            float y = (float) relative.y + 0.46f + (float) Math.sin(wave) * 0.12f;
            float z = (float) relative.z + (float) Math.cos(wave * 0.8f) * 0.12f;
            float height = 0.72f * (1.0f - progress * 0.62f);
            drawFin(consumer, matrix, (float) relative.x, y, z, 0.0f, height, 0.96f, 0.38f, 0.12f, 0.62f, light);
            index++;
        }
    }

    private static void drawSegment(VertexConsumer consumer, Matrix4f matrix,
                                    float sx, float sy, float sz, float ex, float ey, float ez,
                                    float radius, float r, float g, float b, float a, int light) {
        addQuad(consumer, matrix,
                sx, sy - radius, sz,
                ex, ey - radius * 0.88f, ez,
                ex, ey + radius * 0.88f, ez,
                sx, sy + radius, sz,
                r, g, b, a, light);

        addQuad(consumer, matrix,
                sx, sy, sz - radius,
                ex, ey, ez - radius * 0.88f,
                ex, ey, ez + radius * 0.88f,
                sx, sy, sz + radius,
                r, g, b, a, light);
    }

    private static void drawBox(VertexConsumer consumer, Matrix4f matrix,
                                float cx, float cy, float cz, float lx, float ly, float lz,
                                float r, float g, float b, float a, int light) {
        float hx = lx * 0.5f;
        float hy = ly * 0.5f;
        float hz = lz * 0.5f;
        addQuad(consumer, matrix, cx - hx, cy - hy, cz - hz, cx + hx, cy - hy, cz - hz, cx + hx, cy + hy, cz - hz, cx - hx, cy + hy, cz - hz, r, g, b, a, light);
        addQuad(consumer, matrix, cx - hx, cy - hy, cz + hz, cx + hx, cy - hy, cz + hz, cx + hx, cy + hy, cz + hz, cx - hx, cy + hy, cz + hz, r, g, b, a, light);
        addQuad(consumer, matrix, cx - hx, cy - hy, cz - hz, cx - hx, cy - hy, cz + hz, cx - hx, cy + hy, cz + hz, cx - hx, cy + hy, cz - hz, r, g, b, a, light);
        addQuad(consumer, matrix, cx + hx, cy - hy, cz - hz, cx + hx, cy - hy, cz + hz, cx + hx, cy + hy, cz + hz, cx + hx, cy + hy, cz - hz, r, g, b, a, light);
    }

    private static void drawFin(VertexConsumer consumer, Matrix4f matrix,
                                float baseX, float baseY, float baseZ,
                                float tipZ, float tipY,
                                float r, float g, float b, float a, int light) {
        addQuad(consumer, matrix,
                baseX, baseY - 0.03f, baseZ,
                baseX - 0.26f, baseY - 0.04f, baseZ * 0.7f,
                baseX - 0.10f, baseY + tipY, baseZ + tipZ,
                baseX + 0.04f, baseY + 0.02f, baseZ,
                r, g, b, a, light);
    }

    private static void addQuad(VertexConsumer consumer, Matrix4f matrix,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float x4, float y4, float z4,
                                float r, float g, float b, float a, int light) {
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, x4, y4, z4).setColor(r, g, b, a).setLight(light);
    }

    @Override
    public ResourceLocation getTextureLocation(FiendfyreSerpentEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
