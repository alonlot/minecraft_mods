package com.hogwartsmagic.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Shared drawing for the Killing Curse (its windup and its beam): soft camera-facing strips and discs in
 * camera-relative space, the palette, and stable hashing so a thread, an arc or a puff looks the same every frame.
 * The camera is the origin in this space.
 */
final class AvadaFxGeometry {
    // Sickly killing-curse green, and the white-hot green its cores burn out to.
    static final float GREEN_R = 0.22f;
    static final float GREEN_G = 1.00f;
    static final float GREEN_B = 0.34f;
    static final float HOT_R = 0.80f;
    static final float HOT_G = 1.00f;
    static final float HOT_B = 0.82f;
    // Smoke and shadow: black with the faintest green in it, so it reads as cursed smoke and not a hole in the world.
    static final float SMOKE_R = 0.006f;
    static final float SMOKE_G = 0.030f;
    static final float SMOKE_B = 0.012f;
    static final int DISC_RINGS = 5;
    static final int DISC_SEGMENTS = 16;

    private AvadaFxGeometry() {
    }

    /**
     * A camera-facing soft strip through {@code path}: transparent at both edges, {@code alpha} along the spine.
     * {@code widthScale} and {@code alphaScale} let the same path be laid down once as a wide glow and once as a
     * thin bright core.
     */
    static void ribbon(VertexConsumer out, Matrix4f m, Vec3[] path, float[] width, float[] alpha,
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
            double sideOn = perp.length() / (along.length() * toCamera.length() + 1.0E-12);
            if (sideOn < 0.04) {
                continue;
            }
            // A strip seen end-on (down your own beam) has no width to show; fade it before it degenerates.
            float facing = (float) Mth.clamp((sideOn - 0.04) / 0.22, 0.0, 1.0);
            a0 *= facing;
            a1 *= facing;
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
    static void billboard(VertexConsumer out, Matrix4f m, Vec3 centre, float radius, float alpha,
                          float r, float g, float b, boolean hot) {
        billboard(out, m, centre, radius, alpha, r, g, b, hot, DISC_RINGS, DISC_SEGMENTS);
    }

    /** As above with a chosen tessellation: smoke puffs by the hundred want far fewer quads. */
    static void billboard(VertexConsumer out, Matrix4f m, Vec3 centre, float radius, float alpha,
                          float r, float g, float b, boolean hot, int rings, int segments) {
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
        disc(out, m, centre, right, up, radius, alpha, r, g, b, hot, rings, segments);
    }

    /** A soft horizontal disc lying on the ground. */
    static void groundDisc(VertexConsumer out, Matrix4f m, Vec3 centre, float radius, float alpha) {
        if (alpha <= 0.003f) {
            return;
        }
        disc(out, m, centre, new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0), radius, alpha,
                SMOKE_R, SMOKE_G, SMOKE_B, false, DISC_RINGS, DISC_SEGMENTS);
    }

    static void disc(VertexConsumer out, Matrix4f m, Vec3 centre, Vec3 right, Vec3 up, float radius,
                     float alpha, float r, float g, float b, boolean hot, int rings, int segments) {
        for (int ring = 0; ring < rings; ring++) {
            float f0 = ring / (float) rings;
            float f1 = (ring + 1) / (float) rings;
            float a0 = alpha * falloff(f0, hot);
            float a1 = alpha * falloff(f1, hot);
            for (int seg = 0; seg < segments; seg++) {
                double t0 = Math.PI * 2.0 * seg / segments;
                double t1 = Math.PI * 2.0 * (seg + 1) / segments;
                vertex(out, m, centre.add(onRing(right, up, t0, radius * f0)), r, g, b, a0);
                vertex(out, m, centre.add(onRing(right, up, t1, radius * f0)), r, g, b, a0);
                vertex(out, m, centre.add(onRing(right, up, t1, radius * f1)), r, g, b, a1);
                vertex(out, m, centre.add(onRing(right, up, t0, radius * f1)), r, g, b, a1);
            }
        }
    }

    /**
     * A flat soft ring of light around {@code centre} in the plane of {@code right} and {@code up}: nothing at
     * {@code radius - thickness}, {@code alpha} at {@code radius}, nothing at {@code radius + thickness}.
     */
    static void ring(VertexConsumer out, Matrix4f m, Vec3 centre, Vec3 right, Vec3 up, float radius, float thickness,
                     float alpha, float r, float g, float b, int segments) {
        if (alpha <= 0.003f || radius <= 0.001f) {
            return;
        }
        float inner = Math.max(0.0f, radius - thickness);
        float outer = radius + thickness;
        for (int seg = 0; seg < segments; seg++) {
            double t0 = Math.PI * 2.0 * seg / segments;
            double t1 = Math.PI * 2.0 * (seg + 1) / segments;
            vertex(out, m, centre.add(onRing(right, up, t0, inner)), r, g, b, 0.0f);
            vertex(out, m, centre.add(onRing(right, up, t1, inner)), r, g, b, 0.0f);
            vertex(out, m, centre.add(onRing(right, up, t1, radius)), r, g, b, alpha);
            vertex(out, m, centre.add(onRing(right, up, t0, radius)), r, g, b, alpha);
            vertex(out, m, centre.add(onRing(right, up, t0, radius)), r, g, b, alpha);
            vertex(out, m, centre.add(onRing(right, up, t1, radius)), r, g, b, alpha);
            vertex(out, m, centre.add(onRing(right, up, t1, outer)), r, g, b, 0.0f);
            vertex(out, m, centre.add(onRing(right, up, t0, outer)), r, g, b, 0.0f);
        }
    }

    /** Two unit vectors at right angles to each other and to {@code axis} (unit), for building rings round it. */
    static Vec3[] frame(Vec3 axis) {
        Vec3 side = axis.cross(new Vec3(0.0, 1.0, 0.0));
        side = side.lengthSqr() < 1.0E-4 ? axis.cross(new Vec3(1.0, 0.0, 0.0)).normalize() : side.normalize();
        return new Vec3[]{side, side.cross(axis).normalize()};
    }

    static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /** Hot cores fall off sharply (a point of light); smoke and halos softly. */
    static float falloff(float f, boolean hot) {
        float inv = 1.0f - f;
        return hot ? inv * inv * inv : inv * inv * (0.6f + 0.4f * inv);
    }

    static Vec3 onRing(Vec3 right, Vec3 up, double angle, float radius) {
        return right.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
    }

    /** {@code v} turned {@code angle} radians about the unit {@code axis} (Rodrigues). */
    static Vec3 rotate(Vec3 v, Vec3 axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return v.scale(cos).add(axis.cross(v).scale(sin)).add(axis.scale(axis.dot(v) * (1.0 - cos)));
    }

    static Vec3 randomDirection(int seed) {
        double z = hash(seed, 11, 0) * 2.0 - 1.0;
        double angle = hash(seed, 12, 0) * Math.PI * 2.0;
        double r = Math.sqrt(Math.max(0.0, 1.0 - z * z));
        return new Vec3(r * Math.cos(angle), z, r * Math.sin(angle));
    }

    /** A stable pseudo-random 0..1 from three ints, so a thread or an arc looks the same every frame. */
    static float hash(int a, int b, int c) {
        int h = a * 0x27d4eb2d ^ b * 0x165667b1 ^ c * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    static void vertex(VertexConsumer out, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        out.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, Mth.clamp(a, 0.0f, 1.0f));
    }
}
