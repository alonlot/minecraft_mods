package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.pensieve.DragonFireRenderer;
import com.hogwartsmagic.entity.FiendfyreSerpentEntity;
import com.hogwartsmagic.fire.SerpentRoar;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
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
 * Renders the Fiendfyre serpent the way the films show it: fire with a shape, not a shape on fire.
 * <p>
 * The body is a spline-smoothed tube that slithers, drawn as a dark smoky crust under three additive
 * shells of scrolling fire texture (red haze, orange body, yellow-white core) whose surfaces roil.
 * Flame tongues tear off it backwards and upwards. The head is a pair of lofted jaws that gape and
 * wrench wide in a roar, with fangs, a white-hot throat, slit eyes in dark sockets and a mane of flame.
 * Textures come from {@code build_scripts/generate_fiendfyre_textures.py}.
 * <p>
 * The dragon's fire ({@link DragonFireRenderer}) is handed the drawn jaws each frame, which it roars the Horntail's jet
 * out of on the {@link SerpentRoar} beat, and the body, which it sheds its billows and smoke off.
 */
public class FiendfyreSerpentRenderer extends EntityRenderer<FiendfyreSerpentEntity> {
    private static final ResourceLocation BODY_TEX =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/fiendfyre_body.png");
    private static final ResourceLocation FX_TEX =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/fiendfyre_fx.png");
    private static final RenderType CHAR = Types.smoke(BODY_TEX);
    private static final RenderType BODY_GLOW = Types.glow(BODY_TEX);
    private static final RenderType FX_GLOW = Types.glow(FX_TEX);
    private static final RenderType SOOT = Types.smoke(FX_TEX);
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    private static final Map<Integer, Deque<Vec3>> TRAIL_CACHE = new HashMap<>();
    private static final Map<Integer, Long> LAST_DRAWN = new HashMap<>();
    private static final int MAX_TRAIL_POINTS = 24;
    private static final int SAMPLES_PER_SEGMENT = 4;
    private static final int RING_SIDES = 12;
    private static final float NECK_RADIUS = 0.95f;
    private static final float TAIL_RADIUS = 0.10f;
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);

    // radius multiplier, r, g, b, u repeats round the body, blocks per v repeat, v scroll per tick, u twist per block
    private static final float[][] SHELLS = new float[][] {
            { 1.40f, 0.50f, 0.15f, 0.03f, 1.0f, 3.0f, 0.10f, 0.15f },  // red haze
            { 1.08f, 0.80f, 0.36f, 0.07f, 2.0f, 2.2f, 0.07f, -0.10f }, // orange body
            { 0.72f, 0.55f, 0.36f, 0.14f, 1.0f, 1.6f, 0.05f, 0.20f },  // yellow-white core
    };
    private static final float[] CRUST = { 1.00f, 0.30f, 0.09f, 0.03f, 2.0f, 2.2f, 0.07f, -0.10f };

    // Jaw profiles in head space, one station per row: along, half width, half height, centre height.
    private static final float[][] UPPER_JAW = new float[][] {
            { -0.90f, 0.85f, 0.85f, 0.15f },
            { -0.30f, 1.00f, 0.85f, 0.38f },
            { 0.30f, 1.05f, 0.72f, 0.45f },
            { 0.90f, 0.85f, 0.50f, 0.32f },
            { 1.50f, 0.62f, 0.34f, 0.20f },
            { 2.00f, 0.42f, 0.24f, 0.12f },
            { 2.30f, 0.20f, 0.12f, 0.08f },
            { 2.40f, 0.03f, 0.03f, 0.06f },
    };
    private static final float[][] LOWER_JAW = new float[][] {
            { -0.60f, 0.80f, 0.45f, -0.25f },
            { 0.00f, 0.85f, 0.35f, -0.28f },
            { 0.80f, 0.66f, 0.25f, -0.22f },
            { 1.50f, 0.45f, 0.18f, -0.15f },
            { 2.00f, 0.22f, 0.10f, -0.10f },
            { 2.15f, 0.03f, 0.03f, -0.08f },
    };
    private static final float JAW_PIVOT = -0.40f;
    private static final float HEAD_FORWARD = 0.60f;
    /** Head space is in blocks times this: the head dwarfs the neck, as in the films. */
    private static final float HEAD_SCALE = 1.35f;

    public FiendfyreSerpentRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FiendfyreSerpentEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        Vec3 currentPos = entity.getPosition(partialTick);
        // Drop trails of serpents not drawn for 2 s, so the static cache does not grow for the whole session.
        // Not "no longer in the level": a Pensieve memory's serpent is a puppet that is never added to it.
        long now = entity.level().getGameTime();
        LAST_DRAWN.put(entity.getId(), now);
        if (entity.tickCount % 40 == 0) {
            LAST_DRAWN.values().removeIf(t -> now - t > 40);
            TRAIL_CACHE.keySet().retainAll(LAST_DRAWN.keySet());
        }
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
        Matrix4f m = poseStack.last().pose();
        Camera camera = this.entityRenderDispatcher.camera;
        Vec3 cam = camera == null ? heading.scale(10.0D) : camera.getPosition().subtract(currentPos);

        Body body = trail.size() < 2 ? null : buildBody(new ArrayList<>(trail), currentPos, age, heading);
        Vec3 fwd = body == null ? heading : body.tangents[0].scale(-1.0D);
        Vec3 neck = body == null ? Vec3.ZERO : body.centers[0];
        Head head = new Head(neck.add(fwd.scale(HEAD_FORWARD)), fwd, jawOpen(entity, partialTick, age));
        stampFire(entity, currentPos, head, body);

        // Crust first: straight-alpha smoke darkens the seams the additive fire then lights around.
        VertexConsumer crust = buffer.getBuffer(CHAR);
        if (body != null) {
            drawBodyShell(crust, m, body, CRUST, 0.85f, age, 0);
        }
        drawJaw(crust, m, head, UPPER_JAW, head.upperAngle, CRUST, 0.85f, age, 0);
        drawJaw(crust, m, head, LOWER_JAW, head.lowerAngle, CRUST, 0.85f, age, 0);

        VertexConsumer fire = buffer.getBuffer(BODY_GLOW);
        for (int i = 0; i < SHELLS.length; i++) {
            float shimmer = 0.88f + 0.12f * (float) Math.sin(age * 0.55f + i * 2.1f);
            if (body != null) {
                drawBodyShell(fire, m, body, SHELLS[i], shimmer, age, i + 1);
            }
            drawJaw(fire, m, head, UPPER_JAW, head.upperAngle, SHELLS[i], shimmer, age, i + 1);
            drawJaw(fire, m, head, LOWER_JAW, head.lowerAngle, SHELLS[i], shimmer, age, i + 1);
        }


        VertexConsumer fx = buffer.getBuffer(FX_GLOW);
        if (body != null) {
            drawBodyHalo(fx, m, body, cam);
            drawBodyFlames(fx, m, body, cam, age);
        }
        drawHeadFx(fx, m, head, cam, age);

        // Eye sockets are burnt out of everything drawn so far, so the eyes have darkness to glow in.
        drawSockets(buffer.getBuffer(SOOT), m, head, cam);
        drawEyes(buffer.getBuffer(FX_GLOW), m, head, cam, age);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    // ---------------------------------------------------------------------------------------------
    // body
    // ---------------------------------------------------------------------------------------------

    /** Path samples relative to the entity, head first, with parallel-transported frames so rings never twist. */
    private static final class Body {
        final Vec3[] centers;
        final Vec3[] tangents; // pointing from the head towards the tail
        final Vec3[] rights;
        final Vec3[] ups;
        final float[] arc;
        final float[] radius;
        final float length;

        Body(Vec3[] centers, Vec3[] tangents, Vec3[] rights, Vec3[] ups, float[] arc, float[] radius) {
            this.centers = centers;
            this.tangents = tangents;
            this.rights = rights;
            this.ups = ups;
            this.arc = arc;
            this.radius = radius;
            this.length = arc[arc.length - 1];
        }

        /** Index of the sample at or before arc length {@code s}, and the fraction to the next. */
        int indexAt(float s) {
            for (int i = 0; i < arc.length - 1; i++) {
                if (arc[i + 1] >= s) return i;
            }
            return arc.length - 2;
        }

        float frac(int i, float s) {
            float span = arc[i + 1] - arc[i];
            return span < 1.0E-5f ? 0.0f : Math.max(0.0f, Math.min(1.0f, (s - arc[i]) / span));
        }
    }

    private static Body buildBody(List<Vec3> rawPoints, Vec3 origin, float age, Vec3 heading) {
        List<Vec3> samples = catmullRom(rawPoints, SAMPLES_PER_SEGMENT);
        int n = samples.size();

        Vec3[] tangents = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 t = samples.get(Math.min(n - 1, i + 1)).subtract(samples.get(Math.max(0, i - 1)));
            tangents[i] = t.lengthSqr() < 1.0E-6D ? heading.scale(-1.0D) : t.normalize();
        }
        float[] arc = new float[n];
        for (int i = 1; i < n; i++) {
            arc[i] = arc[i - 1] + (float) samples.get(i).distanceTo(samples.get(i - 1));
        }

        // Slither: lateral sine wave along the arc, ramped in over the first block so the head stays put.
        Vec3[] centers = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 right = perpendicular(tangents[i]);
            Vec3 up = right.cross(tangents[i]).normalize();
            float phase = arc[i] * 1.1f - age * 0.32f;
            float amp = Math.min(1.0f, arc[i]) * 0.30f;
            centers[i] = samples.get(i)
                    .add(right.scale(Math.sin(phase) * amp))
                    .add(up.scale(Math.cos(phase * 0.6f) * amp * 0.4f))
                    .subtract(origin);
        }

        // Re-tangent and re-measure after the slither, then parallel-transport a frame down the body.
        Vec3[] finalTangents = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 t = centers[Math.min(n - 1, i + 1)].subtract(centers[Math.max(0, i - 1)]);
            finalTangents[i] = t.lengthSqr() < 1.0E-6D ? tangents[i] : t.normalize();
        }
        Vec3[] rights = new Vec3[n];
        Vec3[] ups = new Vec3[n];
        rights[0] = perpendicular(finalTangents[0]);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                Vec3 t = finalTangents[i];
                Vec3 r = rights[i - 1].subtract(t.scale(rights[i - 1].dot(t)));
                rights[i] = r.lengthSqr() < 1.0E-6D ? perpendicular(t) : r.normalize();
            }
            ups[i] = rights[i].cross(finalTangents[i]).normalize();
        }
        float[] finalArc = new float[n];
        for (int i = 1; i < n; i++) {
            finalArc[i] = finalArc[i - 1] + (float) centers[i].distanceTo(centers[i - 1]);
        }

        // Thick just behind the head, tapering to a thin tail.
        float total = Math.max(1.0E-3f, finalArc[n - 1]);
        float[] radius = new float[n];
        for (int i = 0; i < n; i++) {
            float f = finalArc[i] / total;
            radius[i] = f < 0.12f
                    ? NECK_RADIUS * (0.85f + 0.15f * f / 0.12f)
                    : NECK_RADIUS + (TAIL_RADIUS - NECK_RADIUS) * (float) Math.pow((f - 0.12f) / 0.88f, 0.8f);
        }
        return new Body(centers, finalTangents, rights, ups, finalArc, radius);
    }

    private static void drawBodyShell(VertexConsumer vc, Matrix4f m, Body body, float[] shell,
                                      float alpha, float age, int layer) {
        int n = body.centers.length;
        Vec3[][] rings = new Vec3[n][RING_SIDES + 1];
        float[] vs = new float[n];
        float[] twist = new float[n];
        for (int i = 0; i < n; i++) {
            float s = body.arc[i];
            vs[i] = s / shell[5] - age * shell[6];
            twist[i] = s * shell[7] + age * 0.004f;
            for (int k = 0; k <= RING_SIDES; k++) {
                double phi = Math.PI * 2.0D * k / RING_SIDES;
                float r = body.radius[i] * shell[0] * (1.0f + lump(s, phi, age, layer));
                Vec3 radial = body.rights[i].scale(Math.cos(phi)).add(body.ups[i].scale(Math.sin(phi)));
                rings[i][k] = body.centers[i].add(radial.scale(r));
            }
        }
        loft(vc, m, rings, vs, twist, shell, alpha);
    }

    /** Roiling surface: the radius bulges and sinks in slow waves that travel round and along the body. */
    private static float lump(float s, double phi, float age, int layer) {
        return 0.13f * (float) (Math.sin(s * 2.2f + phi * 3.0D - age * 0.50f + layer * 1.7f)
                * Math.cos(s * 1.1f - phi * 2.0D + age * 0.31f + layer));
    }

    /** Big soft glows down the body so the fire lights the air around it. */
    private static void drawBodyHalo(VertexConsumer vc, Matrix4f m, Body body, Vec3 cam) {
        for (float s = 0.6f; s < body.length; s += 1.3f) {
            int i = body.indexAt(s);
            float f = body.frac(i, s);
            Vec3 c = lerp(body.centers[i], body.centers[i + 1], f);
            float r = body.radius[i] + (body.radius[i + 1] - body.radius[i]) * f;
            billboard(vc, m, c, cam, r * 4.5f, 0.5f, 0.0f, 0.75f, 0.25f, 0.30f, 0.10f, 0.02f, 1.0f);
        }
    }

    /**
     * Tongues of flame torn off the body: each one is anchored at a fixed distance behind the head,
     * leans backwards and upwards, and grows and dies on its own cycle so the outline never settles.
     */
    private static void drawBodyFlames(VertexConsumer vc, Matrix4f m, Body body, Vec3 cam, float age) {
        int k = 0;
        for (float s = 0.4f; s < body.length - 0.3f; s += 0.45f, k++) {
            int i = body.indexAt(s);
            float f = body.frac(i, s);
            Vec3 c = lerp(body.centers[i], body.centers[i + 1], f);
            Vec3 t = lerp(body.tangents[i], body.tangents[i + 1], f).normalize();
            Vec3 right = body.rights[i];
            Vec3 up = body.ups[i];
            float r = body.radius[i] + (body.radius[i + 1] - body.radius[i]) * f;
            for (int j = 0; j < 2; j++) {
                int id = k * 2 + j;
                float life = life(id, age);
                if (life <= 0.02f) continue;
                double phi = hash(id * 7 + 3) * Math.PI * 2.0D + age * 0.03f * (hash(id + 11) - 0.5f);
                Vec3 radial = right.scale(Math.cos(phi)).add(up.scale(Math.sin(phi)));
                Vec3 dir = radial.add(WORLD_UP.scale(0.7D)).add(t.scale(1.0D)).normalize();
                Vec3 base = c.add(radial.scale(r * 0.55f));
                float len = r * (1.3f + 1.4f * hash(id + 5)) * (0.35f + 0.65f * life);
                flame(vc, m, base, dir, len, len * 0.5f, cam, id % 2, 0.95f * life, 0.70f * life, 0.45f * life);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // head
    // ---------------------------------------------------------------------------------------------

    /** The head's frame and the jaw angles for this frame. */
    private static final class Head {
        final Vec3 origin;
        final Vec3 fwd;
        final Vec3 right;
        final Vec3 up;
        final float open;
        final float upperAngle;
        final float lowerAngle;

        Head(Vec3 origin, Vec3 fwd, float open) {
            this.origin = origin;
            this.fwd = fwd.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : fwd.normalize();
            this.right = perpendicular(this.fwd);
            this.up = this.right.cross(this.fwd).normalize();
            this.open = open;
            this.upperAngle = open * 0.50f;
            this.lowerAngle = -open * 0.62f;
        }

        /** Head-space point (along, across, height) on a jaw hinged at {@link #JAW_PIVOT} and rotated by {@code angle}. */
        Vec3 point(double a, double x, double y, float angle) {
            double da = a - JAW_PIVOT;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double ra = JAW_PIVOT + da * cos - y * sin;
            double ry = da * sin + y * cos;
            return origin.add(fwd.scale(ra * HEAD_SCALE)).add(right.scale(x * HEAD_SCALE)).add(up.scale(ry * HEAD_SCALE));
        }

        Vec3 eye(int side) {
            return point(0.30D, side * 0.80D, 0.80D, upperAngle);
        }

        /** Head-space direction rotated with a jaw. */
        Vec3 dir(double a, double x, double y, float angle) {
            double cos = Math.cos(angle), sin = Math.sin(angle);
            return fwd.scale(a * cos - y * sin).add(right.scale(x)).add(up.scale(a * sin + y * cos)).normalize();
        }
    }

    /** Jaws hang half open and breathe, and are wrenched wide while the roar's fire gathers and pours out. */
    private static float jawOpen(FiendfyreSerpentEntity entity, float partialTick, float age) {
        float open = 0.35f + 0.18f * (float) Math.sin(age * 0.15f);
        int roar = SerpentRoar.roarTick(entity.getLifeTicks());
        return open + 0.50f * SerpentRoar.gape(roar < 0 ? -1.0f : roar + partialTick);
    }

    /** Hands the dragon's fire the jaws (world, and the way they point) and the body samples (world, radius, tangent). */
    private static void stampFire(FiendfyreSerpentEntity entity, Vec3 at, Head head, Body body) {
        float mid = (head.upperAngle + head.lowerAngle) * 0.5f;
        Vec3 lip = at.add(head.point(0.9D, 0.0D, 0.0D, mid));
        Vec3 out = head.dir(1.0D, 0.0D, 0.0D, mid);
        DragonFireRenderer.stampWild(entity, new double[]{lip.x, lip.y, lip.z}, new double[]{out.x, out.y, out.z});
        if (body == null) return;
        double[][] samples = new double[body.centers.length][];
        for (int i = 0; i < samples.length; i++) {
            Vec3 c = at.add(body.centers[i]);
            Vec3 t = body.tangents[i];
            samples[i] = new double[]{c.x, c.y, c.z, body.radius[i], t.x, t.y, t.z};
        }
        DragonFireRenderer.stampBody(entity, samples);
    }

    private static void drawJaw(VertexConsumer vc, Matrix4f m, Head head, float[][] stations, float angle,
                                float[] shell, float alpha, float age, int layer) {
        int sub = 3;
        int n = (stations.length - 1) * sub + 1;
        Vec3[][] rings = new Vec3[n][RING_SIDES + 1];
        float[] vs = new float[n];
        float[] twist = new float[n];
        // The shells scale less on the head than the body, so the haze doesn't swallow the jaws.
        float mul = 1.0f + (shell[0] - 1.0f) * 0.6f;
        for (int i = 0; i < n; i++) {
            int seg = Math.min(stations.length - 2, i / sub);
            float f = (i - seg * sub) / (float) sub;
            float[] p0 = stations[seg];
            float[] p1 = stations[seg + 1];
            float a = p0[0] + (p1[0] - p0[0]) * f;
            float w = (p0[1] + (p1[1] - p0[1]) * f) * mul;
            float h = (p0[2] + (p1[2] - p0[2]) * f) * mul;
            float cy = p0[3] + (p1[3] - p0[3]) * f;
            // Flow runs back along the head, the same way as the body.
            vs[i] = -a / shell[5] - age * shell[6];
            twist[i] = -a * shell[7];
            for (int k = 0; k <= RING_SIDES; k++) {
                double phi = Math.PI * 2.0D * k / RING_SIDES;
                float bulge = 1.0f + 0.6f * lump(a, phi, age, layer);
                rings[i][k] = head.point(a, Math.cos(phi) * w * bulge, cy + Math.sin(phi) * h * bulge, angle);
            }
        }
        loft(vc, m, rings, vs, twist, shell, alpha);
    }

    private static void drawHeadFx(VertexConsumer vc, Matrix4f m, Head head, Vec3 cam, float age) {
        float up = head.upperAngle;
        float low = head.lowerAngle;

        // Glow the air round the head.
        billboard(vc, m, head.point(0.6D, 0.0D, 0.1D, 0.0f), cam, 6.0f * HEAD_SCALE,
                0.5f, 0.0f, 0.75f, 0.25f, 0.34f, 0.12f, 0.03f, 1.0f);

        // Throat: white-hot between the jaws, brighter the wider they gape.
        float throat = Math.min(1.0f, 0.35f + head.open);
        Vec3 mouth = head.point(0.9D, 0.0D, -0.02D, (up + low) * 0.5f);
        billboard(vc, m, mouth, cam, (1.0f + 1.6f * head.open) * HEAD_SCALE,
                0.5f, 0.0f, 0.75f, 0.25f, 1.0f * throat, 0.75f * throat, 0.35f * throat, 1.0f);

        // The fire it roars out of the jaws is the dragon's jet (DragonFireRenderer), handed the lip in stampFire.

        // Mane: flames streaming back off the skull.
        int id = 1000;
        for (float a = -0.7f; a <= 0.75f; a += 0.35f) {
            for (int side = -1; side <= 1; side++, id++) {
                float life = life(id, age);
                if (life <= 0.02f) continue;
                float x = side * 0.45f;
                Vec3 base = head.point(a, x, 0.75D, up);
                Vec3 dir = head.dir(-0.9D, side * 0.45D, 1.0D, up);
                float len = (1.0f + 0.9f * hash(id + 3)) * HEAD_SCALE * (0.35f + 0.65f * life);
                flame(vc, m, base, dir, len, len * 0.55f, cam, id % 2, 0.95f * life, 0.65f * life, 0.40f * life);
            }
        }

        // Fangs and teeth: two great fangs and a row of smaller teeth on each jaw.
        for (int side = -1; side <= 1; side += 2) {
            fang(vc, m, head.point(1.55D, side * 0.42D, 0.02D, up), head.dir(0.25D, 0.0D, -1.0D, up),
                    0.65f * HEAD_SCALE, 0.10f * HEAD_SCALE);
            fang(vc, m, head.point(1.25D, side * 0.36D, -0.08D, low), head.dir(0.20D, 0.0D, 1.0D, low),
                    0.45f * HEAD_SCALE, 0.08f * HEAD_SCALE);
            for (float a = 0.35f; a < 2.1f; a += 0.3f) {
                if (Math.abs(a - 1.55f) < 0.12f) continue;
                float w = jawWidth(UPPER_JAW, a) * 0.82f;
                fang(vc, m, head.point(a, side * w, jawBottom(UPPER_JAW, a), up), head.dir(0.1D, 0.0D, -1.0D, up),
                        0.20f * HEAD_SCALE, 0.05f * HEAD_SCALE);
            }
            for (float a = 0.25f; a < 1.85f; a += 0.3f) {
                if (Math.abs(a - 1.25f) < 0.12f) continue;
                float w = jawWidth(LOWER_JAW, a) * 0.82f;
                fang(vc, m, head.point(a, side * w, jawTop(LOWER_JAW, a), low), head.dir(0.1D, 0.0D, 1.0D, low),
                        0.16f * HEAD_SCALE, 0.045f * HEAD_SCALE);
            }
        }
    }

    /**
     * Where an eye sits and which way it lies: long axis slanting down towards the snout for a scowl, short
     * axis turned to the camera. {@code vis} fades out the eye on the far side, which no depth test hides.
     */
    private static final class EyeSpot {
        final Vec3 centre;
        final Vec3 along;
        final Vec3 across;
        final float vis;

        EyeSpot(Head head, int side, Vec3 cam) {
            this.centre = head.eye(side);
            Vec3 toCam = cam.subtract(centre);
            toCam = toCam.lengthSqr() < 1.0E-6D ? head.fwd : toCam.normalize();
            Vec3 outward = head.dir(0.45D, side * 0.8D, 0.35D, head.upperAngle);
            this.vis = Math.max(0.0f, Math.min(1.0f, (float) outward.dot(toCam) * 2.5f + 0.6f));
            this.along = head.dir(1.0D, side * 0.15D, -0.35D, head.upperAngle);
            Vec3 a = this.along.cross(toCam);
            this.across = a.lengthSqr() < 1.0E-6D ? head.up : a.normalize();
        }
    }

    private static void drawSockets(VertexConsumer vc, Matrix4f m, Head head, Vec3 cam) {
        for (int side = -1; side <= 1; side += 2) {
            EyeSpot eye = new EyeSpot(head, side, cam);
            if (eye.vis <= 0.0f) continue;
            sprite(vc, m, eye.centre, eye.along.scale(0.55D * HEAD_SCALE), eye.across.scale(0.30D * HEAD_SCALE),
                    0.627f, 0.252f, 0.748f, 0.373f, 0.08f, 0.01f, 0.0f, 0.92f * eye.vis);
        }
    }

    /** Eyes: an ember glow under a white-hot almond with a slit pupil. */
    private static void drawEyes(VertexConsumer vc, Matrix4f m, Head head, Vec3 cam, float age) {
        for (int side = -1; side <= 1; side += 2) {
            EyeSpot eye = new EyeSpot(head, side, cam);
            if (eye.vis <= 0.0f) continue;
            float glow = (0.85f + 0.15f * (float) Math.sin(age * 0.5f + side)) * eye.vis;
            sprite(vc, m, eye.centre, eye.along.scale(0.40D * HEAD_SCALE), eye.across.scale(0.24D * HEAD_SCALE),
                    0.5f, 0.0f, 0.75f, 0.25f, 0.8f * glow, 0.35f * glow, 0.05f * glow, 1.0f);
            sprite(vc, m, eye.centre, eye.along.scale(0.30D * HEAD_SCALE), eye.across.scale(0.15D * HEAD_SCALE),
                    0.75f, 0.0f, 1.0f, 0.25f, glow, glow, glow, 1.0f);
        }
    }

    private static float jawWidth(float[][] stations, float a) {
        return jawParam(stations, a, 1);
    }

    private static float jawBottom(float[][] stations, float a) {
        return jawParam(stations, a, 3) - jawParam(stations, a, 2) * 0.8f;
    }

    private static float jawTop(float[][] stations, float a) {
        return jawParam(stations, a, 3) + jawParam(stations, a, 2) * 0.8f;
    }

    private static float jawParam(float[][] stations, float a, int col) {
        for (int i = 0; i < stations.length - 1; i++) {
            if (a <= stations[i + 1][0]) {
                float f = (a - stations[i][0]) / (stations[i + 1][0] - stations[i][0]);
                f = Math.max(0.0f, Math.min(1.0f, f));
                return stations[i][col] + (stations[i + 1][col] - stations[i][col]) * f;
            }
        }
        return stations[stations.length - 1][col];
    }

    // ---------------------------------------------------------------------------------------------
    // primitives
    // ---------------------------------------------------------------------------------------------

    /** Quads between successive rings; u runs round each ring, v along the loft, both scrolled by the shell. */
    private static void loft(VertexConsumer vc, Matrix4f m, Vec3[][] rings, float[] vs, float[] twist,
                             float[] shell, float alpha) {
        float r = shell[1], g = shell[2], b = shell[3];
        float uRep = shell[4];
        for (int i = 0; i < rings.length - 1; i++) {
            for (int k = 0; k < RING_SIDES; k++) {
                float u0 = uRep * k / RING_SIDES;
                float u1 = uRep * (k + 1) / RING_SIDES;
                vert(vc, m, rings[i][k], r, g, b, alpha, u0 + twist[i], vs[i]);
                vert(vc, m, rings[i + 1][k], r, g, b, alpha, u0 + twist[i + 1], vs[i + 1]);
                vert(vc, m, rings[i + 1][k + 1], r, g, b, alpha, u1 + twist[i + 1], vs[i + 1]);
                vert(vc, m, rings[i][k + 1], r, g, b, alpha, u1 + twist[i], vs[i]);
            }
        }
    }

    /** A flame-tongue sprite stood on {@code base}, pointing along {@code dir}, turned to face the camera. */
    private static void flame(VertexConsumer vc, Matrix4f m, Vec3 base, Vec3 dir, float len, float width, Vec3 cam,
                              int variant, float r, float g, float b) {
        Vec3 side = dir.cross(cam.subtract(base));
        if (side.lengthSqr() < 1.0E-6D) side = perpendicular(dir);
        side = side.normalize().scale(width * 0.5f);
        Vec3 root = base.subtract(dir.scale(len * 0.12f));
        Vec3 tip = base.add(dir.scale(len));
        float u0 = variant * 0.25f, u1 = u0 + 0.25f;
        vert(vc, m, root.subtract(side), r, g, b, 1.0f, u0, 0.5f);
        vert(vc, m, root.add(side), r, g, b, 1.0f, u1, 0.5f);
        vert(vc, m, tip.add(side), r, g, b, 1.0f, u1, 0.0f);
        vert(vc, m, tip.subtract(side), r, g, b, 1.0f, u0, 0.0f);
    }

    /** Camera-facing square of side {@code size} showing the atlas rect (u0, v0)-(u1, v1). */
    private static void billboard(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 cam, float size,
                                  float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        Vec3 view = cam.subtract(c);
        if (view.lengthSqr() < 1.0E-6D) return;
        view = view.normalize();
        Vec3 right = perpendicular(view);
        Vec3 up = right.cross(view).normalize();
        sprite(vc, m, c, right.scale(size * 0.5f), up.scale(size * 0.5f), u0, v0, u1, v1, r, g, b, a);
    }

    private static void sprite(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 halfRight, Vec3 halfUp,
                               float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        vert(vc, m, c.subtract(halfRight).subtract(halfUp), r, g, b, a, u0, v1);
        vert(vc, m, c.add(halfRight).subtract(halfUp), r, g, b, a, u1, v1);
        vert(vc, m, c.add(halfRight).add(halfUp), r, g, b, a, u1, v0);
        vert(vc, m, c.subtract(halfRight).add(halfUp), r, g, b, a, u0, v0);
    }

    /** Four-sided cone sampled from the atlas's solid white, hot yellow at the root and white at the point. */
    private static void fang(VertexConsumer vc, Matrix4f m, Vec3 base, Vec3 dir, float len, float radius) {
        Vec3 a = perpendicular(dir);
        Vec3 b = a.cross(dir).normalize();
        Vec3 tip = base.add(dir.scale(len));
        Vec3[] ring = { a, b, a.scale(-1.0D), b.scale(-1.0D) };
        float u = 0.5625f, v = 0.3125f;
        for (int k = 0; k < 4; k++) {
            Vec3 p0 = base.add(ring[k].scale(radius));
            Vec3 p1 = base.add(ring[(k + 1) % 4].scale(radius));
            vert(vc, m, p0, 1.0f, 0.50f, 0.15f, 1.0f, u, v);
            vert(vc, m, p1, 1.0f, 0.50f, 0.15f, 1.0f, u, v);
            vert(vc, m, tip, 1.0f, 0.90f, 0.60f, 1.0f, u, v);
            vert(vc, m, tip, 1.0f, 0.90f, 0.60f, 1.0f, u, v);
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a, float u, float v) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a).setUv(u, v).setLight(LIGHT)
                .setNormal(0.0f, 1.0f, 0.0f);
    }

    // ---------------------------------------------------------------------------------------------
    // maths
    // ---------------------------------------------------------------------------------------------

    /** Unit vector at right angles to {@code t}, horizontal where possible. */
    private static Vec3 perpendicular(Vec3 t) {
        Vec3 right = t.cross(WORLD_UP);
        if (right.lengthSqr() < 1.0E-6D) right = t.cross(new Vec3(1.0D, 0.0D, 0.0D));
        return right.normalize();
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, float f) {
        return a.add(b.subtract(a).scale(f));
    }

    /** 0..1, stable per id. */
    private static float hash(int id) {
        int h = id * 0x27d4eb2d;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h & 0xFFFFFF) / 16777216.0f;
    }

    /** A flame's size through its own short life: grows, flares, dies, then is reborn. */
    private static float life(int id, float age) {
        float period = 9.0f + 9.0f * hash(id + 101);
        float phase = age / period + hash(id + 202);
        phase -= (float) Math.floor(phase);
        return (float) Math.sin(phase * Math.PI);
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

    @Override
    public ResourceLocation getTextureLocation(FiendfyreSerpentEntity entity) {
        return BODY_TEX;
    }

    /** Render types for the serpent; subclassing {@link RenderType} is the only way to reach its shards. */
    private static final class Types extends RenderType {
        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                      boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }

        /** Unlit straight-alpha smoke: no depth write so every layer shows, no culling so it reads from inside. */
        static RenderType smoke(ResourceLocation texture) {
            return create("hogwartsmagic_fiendfyre_smoke", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 8192,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_BEACON_BEAM_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .createCompositeState(false));
        }

        /** As {@link #smoke} but additive (ONE, ONE): texture and vertex colour act as premultiplied light. */
        static RenderType glow(ResourceLocation texture) {
            return create("hogwartsmagic_fiendfyre_glow", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 8192,
                    false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(RENDERTYPE_BEACON_BEAM_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                            .setTransparencyState(ADDITIVE_TRANSPARENCY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .createCompositeState(false));
        }
    }
}
