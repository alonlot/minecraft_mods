package com.hogwartsmagic.client;

import com.hogwartsmagic.entity.FiendfyreSerpentEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Renders the Fiendfyre serpent as a flaming snake: spline-smoothed tube body with
 * sinuous slither, three additive fire layers (deep red core, orange mid, yellow halo),
 * tapered tail, snake head with glowing eyes and a flicking forked tongue.
 */
public class FiendfyreSerpentRenderer extends EntityRenderer<FiendfyreSerpentEntity> {
    private static final Map<Integer, Deque<Vec3>> TRAIL_CACHE = new HashMap<>();
    private static final int MAX_TRAIL_POINTS = 24;
    private static final int SAMPLES_PER_SEGMENT = 4;
    private static final int RING_SIDES = 6;
    private static final float HEAD_RADIUS = 0.65f;
    private static final float TAIL_RADIUS = 0.08f;

    private static final float[][] LAYERS = new float[][] {
            // radius multiplier, r, g, b, a
            { 1.50f, 1.00f, 0.90f, 0.32f, 0.35f }, // yellow halo
            { 1.00f, 1.00f, 0.45f, 0.06f, 0.75f }, // orange mid
            { 0.55f, 0.85f, 0.10f, 0.00f, 1.00f }, // deep red core
    };

    public FiendfyreSerpentRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FiendfyreSerpentEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        Vec3 currentPos = entity.getPosition(partialTick);
        Deque<Vec3> trail = TRAIL_CACHE.computeIfAbsent(entity.getId(), id -> new ArrayDeque<>());
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

        // Build dense, smoothed path samples in world-space, then translate to entity-local.
        List<Vec3> rawPoints = new ArrayList<>(trail);
        if (rawPoints.size() < 2) {
            // Not enough trail yet — render a tiny pulse at the head.
            drawHead(poseStack, consumer, age, light, heading);
            poseStack.popPose();
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        List<Vec3> samples = catmullRom(rawPoints, SAMPLES_PER_SEGMENT);

        // Apply lateral slither offset perpendicular to each sample's tangent.
        List<Vec3> tangents = new ArrayList<>(samples.size());
        for (int i = 0; i < samples.size(); i++) {
            Vec3 prev = samples.get(Math.max(0, i - 1));
            Vec3 next = samples.get(Math.min(samples.size() - 1, i + 1));
            Vec3 t = next.subtract(prev);
            tangents.add(t.lengthSqr() < 1.0E-6D ? heading : t.normalize());
        }

        // Slither: lateral sine wave along arc length.
        float[] arcLength = new float[samples.size()];
        for (int i = 1; i < samples.size(); i++) {
            arcLength[i] = arcLength[i - 1] + (float) samples.get(i).distanceTo(samples.get(i - 1));
        }

        List<Vec3> centers = new ArrayList<>(samples.size());
        List<Vec3> rightDirs = new ArrayList<>(samples.size());
        List<Vec3> upDirs = new ArrayList<>(samples.size());
        for (int i = 0; i < samples.size(); i++) {
            Vec3 t = tangents.get(i);
            Vec3 right = t.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (right.lengthSqr() < 1.0E-6D) right = new Vec3(1.0D, 0.0D, 0.0D);
            right = right.normalize();
            Vec3 up = right.cross(t).normalize();

            float wavePhase = arcLength[i] * 1.3f - age * 0.35f;
            // Don't wave the very head; ramp slither in from 0 over the first ~1 unit of arc.
            float waveAmp = Math.min(1.0f, arcLength[i] / 1.0f) * 0.22f;
            double lateral = Math.sin(wavePhase) * waveAmp;
            double vertical = Math.cos(wavePhase * 0.6f) * waveAmp * 0.4f;

            Vec3 c = samples.get(i).add(right.scale(lateral)).add(up.scale(vertical));
            centers.add(c);
            rightDirs.add(right);
            upDirs.add(up);
        }

        // Recompute tangents post-slither for smoother basis frames at the ring stage.
        // (Cheap pass: just re-tangent based on neighbor centers.)
        List<Vec3> finalTangents = new ArrayList<>(centers.size());
        for (int i = 0; i < centers.size(); i++) {
            Vec3 prev = centers.get(Math.max(0, i - 1));
            Vec3 next = centers.get(Math.min(centers.size() - 1, i + 1));
            Vec3 t = next.subtract(prev);
            finalTangents.add(t.lengthSqr() < 1.0E-6D ? tangents.get(i) : t.normalize());
        }

        // Translate all centers to be relative to entity position (we already pushed pose at entity origin).
        List<Vec3> localCenters = new ArrayList<>(centers.size());
        for (Vec3 c : centers) {
            localCenters.add(c.subtract(currentPos));
        }

        // Layered fire passes.
        for (float[] layer : LAYERS) {
            float radMul = layer[0];
            float r = layer[1], g = layer[2], b = layer[3], a = layer[4];
            // Shimmer the alpha so highlight layer flickers.
            float shimmer = (float) (0.85 + 0.15 * Math.sin(age * 0.6 + radMul * 7.0));
            drawTube(consumer, matrix, localCenters, finalTangents, rightDirs, upDirs,
                    radMul, r, g, b, a * shimmer, light);
        }

        // Head: positioned at the front (newest) center, oriented along its tangent.
        Vec3 headCenter = localCenters.get(0);
        Vec3 headTangent = finalTangents.get(0);
        drawSnakeHead(poseStack, consumer, age, light, headCenter, headTangent);

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

    /** Catmull-Rom interpolation: returns densified path with `samplesPerSegment` between each pair. */
    private static List<Vec3> catmullRom(List<Vec3> pts, int samplesPerSegment) {
        List<Vec3> out = new ArrayList<>();
        if (pts.size() < 2) {
            out.addAll(pts);
            return out;
        }
        for (int i = 0; i < pts.size() - 1; i++) {
            Vec3 p0 = pts.get(Math.max(0, i - 1));
            Vec3 p1 = pts.get(i);
            Vec3 p2 = pts.get(i + 1);
            Vec3 p3 = pts.get(Math.min(pts.size() - 1, i + 2));
            int steps = (i == pts.size() - 2) ? samplesPerSegment + 1 : samplesPerSegment;
            for (int s = 0; s < steps; s++) {
                double t = s / (double) samplesPerSegment;
                double t2 = t * t;
                double t3 = t2 * t;
                double x = 0.5 * (2 * p1.x
                        + (-p0.x + p2.x) * t
                        + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2
                        + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3);
                double y = 0.5 * (2 * p1.y
                        + (-p0.y + p2.y) * t
                        + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2
                        + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3);
                double z = 0.5 * (2 * p1.z
                        + (-p0.z + p2.z) * t
                        + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2
                        + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3);
                out.add(new Vec3(x, y, z));
            }
        }
        return out;
    }

    /** Builds a hexagonal tube along the centers, with linear radius taper from head to tail. */
    private static void drawTube(VertexConsumer consumer, Matrix4f matrix,
                                 List<Vec3> centers, List<Vec3> tangents,
                                 List<Vec3> rights, List<Vec3> ups,
                                 float radiusMul, float r, float g, float b, float a, int light) {
        int n = centers.size();
        if (n < 2) return;

        // Pre-compute ring vertices per sample.
        float[][][] rings = new float[n][RING_SIDES][3];
        for (int i = 0; i < n; i++) {
            float taper = i / (float) (n - 1);
            // Head fat, tail thin.
            float radius = (HEAD_RADIUS * (1 - taper) + TAIL_RADIUS * taper) * radiusMul;

            Vec3 c = centers.get(i);
            Vec3 t = tangents.get(i);
            // Rebuild right/up to keep tube ring perpendicular to current tangent.
            Vec3 right = t.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (right.lengthSqr() < 1.0E-6D) right = rights.get(i);
            right = right.normalize();
            Vec3 up = right.cross(t).normalize();

            for (int s = 0; s < RING_SIDES; s++) {
                double theta = (Math.PI * 2.0 * s) / RING_SIDES;
                double rx = right.x * Math.cos(theta) + up.x * Math.sin(theta);
                double ry = right.y * Math.cos(theta) + up.y * Math.sin(theta);
                double rz = right.z * Math.cos(theta) + up.z * Math.sin(theta);
                rings[i][s][0] = (float) (c.x + rx * radius);
                rings[i][s][1] = (float) (c.y + ry * radius);
                rings[i][s][2] = (float) (c.z + rz * radius);
            }
        }

        // Connect adjacent rings with quads.
        for (int i = 0; i < n - 1; i++) {
            for (int s = 0; s < RING_SIDES; s++) {
                int s1 = (s + 1) % RING_SIDES;
                float[] a0 = rings[i][s];
                float[] a1 = rings[i][s1];
                float[] b0 = rings[i + 1][s];
                float[] b1 = rings[i + 1][s1];
                consumer.addVertex(matrix, a0[0], a0[1], a0[2]).setColor(r, g, b, a).setLight(light);
                consumer.addVertex(matrix, b0[0], b0[1], b0[2]).setColor(r, g, b, a).setLight(light);
                consumer.addVertex(matrix, b1[0], b1[1], b1[2]).setColor(r, g, b, a).setLight(light);
                consumer.addVertex(matrix, a1[0], a1[1], a1[2]).setColor(r, g, b, a).setLight(light);
            }
        }
    }

    /** Snake head: narrowing wedge with two glowing eyes and a flicking forked tongue. */
    private static void drawSnakeHead(PoseStack poseStack, VertexConsumer consumer, float age, int light,
                                      Vec3 headCenter, Vec3 headTangent) {
        // Build a local frame oriented along headTangent.
        Vec3 t = headTangent.lengthSqr() < 1.0E-6D ? new Vec3(0, 0, 1) : headTangent.normalize();
        Vec3 right = t.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-6D) right = new Vec3(1.0D, 0.0D, 0.0D);
        right = right.normalize();
        Vec3 up = right.cross(t).normalize();

        Matrix4f matrix = poseStack.last().pose();

        // Head wedge: base at body end, narrows toward snout.
        // 4 base vertices (right/up perpendicular cross at +0 along t), 1 snout vertex.
        float baseRadius = 0.55f;
        float wedgeLength = 0.95f;
        Vec3 base = headCenter.add(t.scale(0.05D));
        Vec3 snout = base.add(t.scale(wedgeLength));

        Vec3 br = base.add(right.scale(baseRadius));
        Vec3 bl = base.add(right.scale(-baseRadius));
        Vec3 bu = base.add(up.scale(baseRadius * 0.85));
        Vec3 bd = base.add(up.scale(-baseRadius * 0.65));

        // Mid-jaw widening point so it doesn't look like a pyramid — broaden ~30% along.
        float midF = 0.35f;
        Vec3 midC = base.add(t.scale(wedgeLength * midF));
        float midR = baseRadius * 1.05f;
        Vec3 mr = midC.add(right.scale(midR));
        Vec3 ml = midC.add(right.scale(-midR));
        Vec3 mu = midC.add(up.scale(midR * 0.7));
        Vec3 md = midC.add(up.scale(-midR * 0.55));

        // Color: bright orange head, fades to white at the snout.
        float hr = 1.0f, hg = 0.55f, hb = 0.10f, ha = 0.95f;
        float sr = 1.0f, sg = 0.95f, sb = 0.45f, sa = 0.92f;

        // Base ring to mid ring (4 quads).
        quad(consumer, matrix, br, mr, mu, bu, hr, hg, hb, ha, light);
        quad(consumer, matrix, bu, mu, ml, bl, hr, hg, hb, ha, light);
        quad(consumer, matrix, bl, ml, md, bd, hr, hg, hb, ha, light);
        quad(consumer, matrix, bd, md, mr, br, hr, hg, hb, ha, light);

        // Mid ring to snout (4 triangles, drawn as degenerate quads).
        quad(consumer, matrix, mr, snout, snout, mu, sr, sg, sb, sa, light);
        quad(consumer, matrix, mu, snout, snout, ml, sr, sg, sb, sa, light);
        quad(consumer, matrix, ml, snout, snout, md, sr, sg, sb, sa, light);
        quad(consumer, matrix, md, snout, snout, mr, sr, sg, sb, sa, light);

        // Eyes: two bright spots on the upper-side faces, at ~midF position.
        float eyeR = 0.12f;
        float pulse = 0.85f + 0.15f * (float) Math.sin(age * 0.5f);
        Vec3 eyePosR = midC.add(right.scale(midR * 0.85)).add(up.scale(midR * 0.50));
        Vec3 eyePosL = midC.add(right.scale(-midR * 0.85)).add(up.scale(midR * 0.50));
        drawEye(consumer, matrix, eyePosR, right, up, eyeR, 1.0f, 1.0f, 0.85f, pulse, light);
        drawEye(consumer, matrix, eyePosL, right, up, eyeR, 1.0f, 1.0f, 0.85f, pulse, light);

        // Forked tongue: time-driven flick.
        float tonguePhase = (float) ((Math.sin(age * 0.4) + 1.0) * 0.5); // 0..1
        if (tonguePhase > 0.55f) {
            float ext = (tonguePhase - 0.55f) / 0.45f; // 0..1
            float tongueLen = 0.55f * ext;
            float forkSpread = 0.10f + 0.06f * ext;
            Vec3 mouth = snout.add(t.scale(-0.02D));
            Vec3 tongueEnd = snout.add(t.scale(tongueLen));
            Vec3 forkR = tongueEnd.add(right.scale(forkSpread)).add(t.scale(0.08D));
            Vec3 forkL = tongueEnd.add(right.scale(-forkSpread)).add(t.scale(0.08D));
            float tr = 1.0f, tg = 0.15f, tb = 0.15f, ta = 0.9f;
            // Two thin tapered fork tines as flat ribbons.
            ribbon(consumer, matrix, mouth, tongueEnd, forkR, right, 0.04f, 0.025f, tr, tg, tb, ta, light);
            ribbon(consumer, matrix, mouth, tongueEnd, forkL, right, 0.04f, 0.025f, tr, tg, tb, ta, light);
        }
    }

    private static void drawHead(PoseStack poseStack, VertexConsumer consumer, float age, int light, Vec3 heading) {
        // Fallback when trail is too short — render a tiny glowing nub at origin facing heading.
        Vec3 t = heading.lengthSqr() < 1.0E-6D ? new Vec3(0, 0, 1) : heading.normalize();
        drawSnakeHead(poseStack, consumer, age, light, new Vec3(0, 0, 0), t);
    }

    private static void drawEye(VertexConsumer consumer, Matrix4f matrix, Vec3 c,
                                Vec3 right, Vec3 up, float radius,
                                float r, float g, float b, float a, int light) {
        // Render as a small billboard cross.
        Vec3 r1 = c.add(right.scale(radius));
        Vec3 r2 = c.add(right.scale(-radius));
        Vec3 u1 = c.add(up.scale(radius));
        Vec3 u2 = c.add(up.scale(-radius));
        consumer.addVertex(matrix, (float) r1.x, (float) u1.y, (float) r1.z).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, (float) r2.x, (float) u1.y, (float) r2.z).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, (float) r2.x, (float) u2.y, (float) r2.z).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, (float) r1.x, (float) u2.y, (float) r1.z).setColor(r, g, b, a).setLight(light);
    }

    private static void ribbon(VertexConsumer consumer, Matrix4f matrix,
                               Vec3 start, Vec3 mid, Vec3 end, Vec3 widthDir,
                               float startWidth, float endWidth,
                               float r, float g, float b, float a, int light) {
        Vec3 w0 = widthDir.scale(startWidth);
        Vec3 w1 = widthDir.scale((startWidth + endWidth) * 0.5);
        Vec3 w2 = widthDir.scale(endWidth);

        Vec3 a0 = start.subtract(w0), b0v = start.add(w0);
        Vec3 a1 = mid.subtract(w1), b1v = mid.add(w1);
        Vec3 a2 = end.subtract(w2), b2v = end.add(w2);

        quad(consumer, matrix, a0, b0v, b1v, a1, r, g, b, a, light);
        quad(consumer, matrix, a1, b1v, b2v, a2, r, g, b, a, light);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             Vec3 v1, Vec3 v2, Vec3 v3, Vec3 v4,
                             float r, float g, float b, float a, int light) {
        consumer.addVertex(matrix, (float) v1.x, (float) v1.y, (float) v1.z).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, (float) v2.x, (float) v2.y, (float) v2.z).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, (float) v3.x, (float) v3.y, (float) v3.z).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, (float) v4.x, (float) v4.y, (float) v4.z).setColor(r, g, b, a).setLight(light);
    }

    @Override
    public ResourceLocation getTextureLocation(FiendfyreSerpentEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
