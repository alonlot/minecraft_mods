package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.disable.IncarcerousRopeTimeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws Incarcerous the way the films show it: real twisted hemp ropes that whip out of the caster's wand,
 * snake round the target's chest and legs, cinch tight with a jerk and hold, creaking as the target strains,
 * and slacken and drop away when the bind ends. The leg rope runs on to a stake in the ground (the tether
 * point), so the leash reads at a glance; a Master bind also ropes each extra target to the main one.
 *
 * <p>Every rope is one path (the flight from the wand, then the coil) with a visible window sliding along
 * it: the head leads, and the tail trails by exactly the coil's length, so the rope pulls itself off the
 * wand as it wraps and the last coil lands with the tail tucked at the coil start. Coils land loose and
 * spring tight behind the head. Ropes are textured tubes, lit by the world, drawn after entities from
 * identity camera-relative coordinates (see {@link ProtegoShieldRenderer}). Timings: {@link IncarcerousRopeTimeline}.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class IncarcerousRopeRenderer {
    private static final ResourceLocation ROPE_TEX =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/incarcerous_rope.png");
    private static final RenderType ROPE = RenderType.entityCutoutNoCull(ROPE_TEX);
    private static final double RADIUS = 0.05;
    private static final int SIDES = 8;
    /** Texture repeats per block of rope: one strand crossing per 0.3 blocks lays the twist at about 45 degrees. */
    private static final double V_PER_BLOCK = 1.0 / 0.3;
    private static final int COIL_SAMPLES_PER_TURN = 22;
    private static final int FLIGHT_SAMPLES = 36;
    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    private IncarcerousRopeRenderer() {
    }

    /** One coil round the body: heights as fractions of the hitbox, half-axes of its ellipse in blocks. */
    private record Coil(double yTop, double yBottom, double turns, double halfWidth, double halfDepth,
                        double phase, double delay, double wiggle, double strainGain) {
    }

    /** A rope as drawn this frame: its whole path, cumulative arc length, and where its visible window sits. */
    private static final class Rope {
        final List<Vec3> path = new ArrayList<>();
        double[] arc;
        double flightLength;
        double coilLength;
        double head;
        double tail;
        boolean complete;
        double ropeTime;
    }

    /** The glowing ribbon behind a flying rope head: camera-relative points, tail to head. */
    private record Streak(List<Vec3> points, double fade) {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || IncarcerousRopesClientState.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        // The real partial tick: the event's own is the frame length (see RenderLevelStageEvent in Forge 51).
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partial;
        Vec3 camera = event.getCamera().getPosition();
        // Identity: the camera rotation is already on RenderSystem's stack at this stage.
        Matrix4f matrix = new Matrix4f();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        List<Streak> streaks = new ArrayList<>();

        VertexConsumer rope = buffer.getBuffer(ROPE);
        for (IncarcerousRopesClientState.Bind bind : IncarcerousRopesClientState.all()) {
            if (!(level.getEntity(bind.entityId) instanceof LivingEntity living)
                    || (minecraft.player != null && living.isInvisibleTo(minecraft.player))) {
                continue;
            }
            drawBind(rope, matrix, level, minecraft, living, bind, now, partial, camera, streaks);
        }
        buffer.endBatch(ROPE);

        if (!streaks.isEmpty()) {
            VertexConsumer glow = buffer.getBuffer(ModRenderTypes.PATRONUS_LIGHT);
            for (Streak streak : streaks) {
                drawStreak(glow, matrix, streak);
            }
            buffer.endBatch(ModRenderTypes.PATRONUS_LIGHT);
        }
    }

    private static void drawBind(VertexConsumer vc, Matrix4f m, ClientLevel level, Minecraft minecraft, LivingEntity living,
                                 IncarcerousRopesClientState.Bind bind, double now, float partial, Vec3 camera,
                                 List<Streak> streaks) {
        double t = bind.age(now);
        double sinceRelease = bind.sinceRelease(now);
        double release = IncarcerousRopeTimeline.release(sinceRelease);
        double thickness = RADIUS * (1.0 - smoothstep(0.55, 1.0, release));
        if (thickness < 0.004) {
            return;
        }
        Vec3 feet = living.getPosition(partial);
        double yaw = Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        double w = living.getBbWidth();
        double h = living.getBbHeight();
        boolean upright = h > w * 1.4;
        double strain = bind.released() ? 0.0 : IncarcerousRopeTimeline.strain(bind.seed, t);
        double ground = feet.y;

        Coil torso = upright
                ? new Coil(0.80, 0.52, 3.25, w * 0.80 + RADIUS, w * 0.32 + RADIUS, 2.4, 0.0, 1.0, 0.10)
                : new Coil(0.88, 0.50, 2.5, w * 0.58 + RADIUS, w * 0.58 + RADIUS, 2.4, 0.0, 1.0, 0.08);
        Coil legs = upright
                ? new Coil(0.40, 0.07, 3.0, w * 0.42 + RADIUS, w * 0.24 + RADIUS, -0.8, IncarcerousRopeTimeline.LEG_DELAY, -1.0, 0.04)
                : new Coil(0.38, 0.06, 2.0, w * 0.58 + RADIUS, w * 0.58 + RADIUS, -0.8, IncarcerousRopeTimeline.LEG_DELAY, -1.0, 0.04);

        // Seen from inside your own head the chest coil would hover round the camera: keep only its flight.
        boolean ownFirstPerson = living == minecraft.player && minecraft.options.getCameraType().isFirstPerson();

        Rope chest = buildRope(bind, torso, feet, forward, right, h, t, strain, sinceRelease, ground);
        if (ownFirstPerson) {
            chest.head = Math.min(chest.head, chest.flightLength);
        }
        drawWindow(vc, m, level, chest, thickness, camera, streaks);
        if (chest.complete && !ownFirstPerson) {
            drawFreeEnd(vc, m, level, chest, feet, thickness, t, strain, sinceRelease, ground, bind.seed, camera);
        }

        Rope shins = buildRope(bind, legs, feet, forward, right, h, t, strain, sinceRelease, ground);
        drawWindow(vc, m, level, shins, thickness, camera, streaks);
        if (shins.complete) {
            drawAnchor(vc, m, level, bind, shins, feet, thickness, t, sinceRelease, ground, camera);
        }

        if (bind.anchorEntityId >= 0 && bind.anchorEntityId != bind.entityId
                && level.getEntity(bind.anchorEntityId) instanceof LivingEntity main && main.isAlive()) {
            drawLink(vc, m, level, living, main, partial, thickness, t, sinceRelease, camera);
        }
    }

    /** The flight from the wand into the coil, then the coil itself, sampled; plus where the head and tail are. */
    private static Rope buildRope(IncarcerousRopesClientState.Bind bind, Coil coil, Vec3 feet, Vec3 forward, Vec3 right,
                                  double h, double t, double strain, double sinceRelease, double ground) {
        Rope rope = new Rope();
        double rt = t - coil.delay;
        rope.ropeTime = rt;
        int samples = Math.max(8, (int) Math.ceil(coil.turns * COIL_SAMPLES_PER_TURN));

        // Rest coil first: its length is what the timeline lays out.
        List<Vec3> rest = new ArrayList<>(samples + 1);
        for (int k = 0; k <= samples; k++) {
            rest.add(coilPoint(coil, k / (double) samples, 1.0, feet, forward, right, h, bind.seed));
        }
        double restLength = length(rest);

        double cinch = IncarcerousRopeTimeline.cinch(t);
        double tremor = strain * 0.012 * Math.sin(t * 2.9);
        double loosen = sinceRelease > 0.0 ? 1.1 * easeOut(Math.min(1.0, sinceRelease / 8.0)) : 0.0;
        List<Vec3> coilPts = new ArrayList<>(samples + 1);
        for (int k = 0; k <= samples; k++) {
            double s = k / (double) samples;
            double since = rt - IncarcerousRopeTimeline.laidAt(s * restLength, restLength);
            double k1 = 1.0 + IncarcerousRopeTimeline.looseness(since) + cinch + coil.strainGain * strain + tremor + loosen;
            Vec3 p = coilPoint(coil, s, k1, feet, forward, right, h, bind.seed);
            coilPts.add(fall(p, ground, sinceRelease));
        }

        // Flight: a curve from the wand that arrives along the coil's own tangent, snaking as it flies.
        Vec3 start = bind.origin;
        Vec3 end = coilPts.get(0);
        Vec3 into = coilPts.get(1).subtract(end).normalize();
        double span = start.distanceTo(end);
        Vec3 c1 = start.add(end.subtract(start).scale(0.33)).add(0.0, 0.12 * span, 0.0);
        Vec3 c2 = end.subtract(into.scale(Math.min(1.2, span * 0.4))).add(0.0, 0.25, 0.0);
        Vec3 side = end.subtract(start).cross(UP);
        side = side.lengthSqr() < 1.0E-6 ? right : side.normalize();
        double settle = 1.0 - Mth.clamp((rt - IncarcerousRopeTimeline.FLIGHT) / IncarcerousRopeTimeline.WRAP, 0.0, 1.0);
        double amplitude = 0.32 * coil.wiggle * Math.min(1.0, span / 4.0) * settle;
        List<Vec3> flight = new ArrayList<>(FLIGHT_SAMPLES + 1);
        for (int i = 0; i <= FLIGHT_SAMPLES; i++) {
            double s = i / (double) FLIGHT_SAMPLES;
            Vec3 p = cubic(start, c1, c2, end, s);
            double snake = Math.sin(Math.PI * s) * Math.sin(s * 11.0 - rt * 1.9 + (bind.seed & 7));
            flight.add(fall(p.add(side.scale(amplitude * snake)), ground, sinceRelease));
        }

        rope.path.addAll(flight);
        for (int k = 1; k < coilPts.size(); k++) {
            rope.path.add(coilPts.get(k));
        }
        rope.arc = cumulative(rope.path);
        rope.flightLength = rope.arc[FLIGHT_SAMPLES];
        rope.coilLength = rope.arc[rope.arc.length - 1] - rope.flightLength;
        rope.head = IncarcerousRopeTimeline.head(rt, rope.flightLength, rope.coilLength);
        rope.tail = IncarcerousRopeTimeline.tail(rope.head, rope.coilLength);
        rope.complete = rt >= IncarcerousRopeTimeline.FLIGHT + IncarcerousRopeTimeline.WRAP;
        return rope;
    }

    private static Vec3 coilPoint(Coil coil, double s, double radial, Vec3 feet, Vec3 forward, Vec3 right,
                                  double h, long seed) {
        double theta = coil.phase + coil.wiggle * Math.PI * 2.0 * coil.turns * s;
        double wobble = 1.0 + 0.035 * Math.sin(theta * 3.0 + (seed & 15));
        double y = h * (coil.yTop + (coil.yBottom - coil.yTop) * s);
        double a = coil.halfWidth * radial * wobble;
        double b = coil.halfDepth * radial * wobble;
        return feet.add(right.scale(a * Math.cos(theta))).add(forward.scale(b * Math.sin(theta))).add(0.0, y, 0.0);
    }

    /** On release the ropes drop and pile on the ground round the target's feet. */
    private static Vec3 fall(Vec3 p, double ground, double sinceRelease) {
        if (sinceRelease <= 2.0) {
            return p;
        }
        double drop = 0.04 * (sinceRelease - 2.0) * (sinceRelease - 2.0);
        return new Vec3(p.x, Math.max(ground + RADIUS, p.y - drop), p.z);
    }

    /** Draw the part of the rope between its tail and head, tapering the ends that are still in motion. */
    private static void drawWindow(VertexConsumer vc, Matrix4f m, ClientLevel level, Rope rope, double thickness,
                                   Vec3 camera, List<Streak> streaks) {
        if (rope.head <= rope.tail + 1.0E-3) {
            return;
        }
        List<Vec3> pts = new ArrayList<>();
        List<Double> along = new ArrayList<>();
        sliceInto(rope.path, rope.arc, rope.tail, rope.head, pts, along);
        if (pts.size() < 2) {
            return;
        }
        boolean moving = !rope.complete;
        double[] radius = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            double fromHead = rope.head - along.get(i);
            double fromTail = along.get(i) - rope.tail;
            double r = thickness;
            if (moving) {
                // The flying head is a tight whipping tip; the tail end still streaming off the wand tapers too.
                r *= 0.45 + 0.55 * Mth.clamp(fromHead / 0.25, 0.0, 1.0);
                if (rope.tail > 0.0) {
                    r *= 0.45 + 0.55 * Mth.clamp(fromTail / 0.25, 0.0, 1.0);
                }
            }
            radius[i] = r;
        }
        tube(vc, m, level, pts, along, radius, camera, true, true);

        if (rope.ropeTime < IncarcerousRopeTimeline.FLIGHT + 2.0 && rope.ropeTime > 0.0) {
            List<Vec3> streak = new ArrayList<>();
            List<Double> ignored = new ArrayList<>();
            sliceInto(rope.path, rope.arc, Math.max(rope.tail, rope.head - 1.8), rope.head, streak, ignored);
            if (streak.size() >= 2) {
                double fade = 1.0 - Mth.clamp((rope.ropeTime - IncarcerousRopeTimeline.FLIGHT) / 2.0, 0.0, 1.0);
                for (int i = 0; i < streak.size(); i++) {
                    streak.set(i, streak.get(i).subtract(camera));
                }
                streaks.add(new Streak(streak, fade));
            }
        }
    }

    /** The loose end left after the wrap: a half-hitch knot, then a short tail hanging and swaying. */
    private static void drawFreeEnd(VertexConsumer vc, Matrix4f m, ClientLevel level, Rope rope, Vec3 feet,
                                    double thickness, double t, double strain, double sinceRelease, double ground,
                                    long seed, Vec3 camera) {
        int n = rope.path.size();
        Vec3 end = rope.path.get(n - 1);
        Vec3 tangent = end.subtract(rope.path.get(n - 2)).normalize();
        Vec3 outward = new Vec3(end.x - feet.x, 0.0, end.z - feet.z);
        outward = outward.lengthSqr() < 1.0E-6 ? tangent : outward.normalize();
        double grow = Mth.clamp((rope.ropeTime - IncarcerousRopeTimeline.FLIGHT - IncarcerousRopeTimeline.WRAP) / 4.0, 0.0, 1.0);

        // Knot: a small loop standing off the rope, in the plane of the tangent and the outward direction.
        List<Vec3> loop = new ArrayList<>();
        Vec3 centre = end.add(outward.scale(0.05 * grow));
        double loopR = 0.055 * grow;
        for (int i = 0; i <= 14; i++) {
            double a = Math.PI * 2.0 * i / 14.0;
            loop.add(fall(centre.add(tangent.scale(Math.cos(a) * loopR)).add(outward.scale(Math.sin(a) * loopR)),
                    ground, sinceRelease));
        }
        if (loopR > 0.01) {
            tubeConstant(vc, m, level, loop, thickness * 0.9, camera);
        }

        // Tail end: hangs from the knot, sways, and jumps when the target strains.
        List<Vec3> dangle = new ArrayList<>();
        double sway = Math.sin(t * 0.12 + (seed & 31)) * 0.05 + strain * 0.04 * Math.sin(t * 1.7);
        Vec3 swayDir = outward.cross(UP);
        for (int i = 0; i <= 8; i++) {
            double u = i / 8.0;
            Vec3 p = end.add(tangent.scale(0.05 * u)).add(outward.scale(0.04 * u))
                    .add(0.0, -0.34 * grow * Math.pow(u, 1.3), 0.0)
                    .add(swayDir.scale(sway * u * u));
            dangle.add(fall(p, ground, sinceRelease));
        }
        if (grow > 0.05) {
            tubeConstant(vc, m, level, dangle, thickness * 0.9, camera);
        }
    }

    /**
     * The leg rope runs on from its last coil down to a stake in the ground at the tether point, sagging when
     * the target stands close and pulling straight when it strains at the end of the leash.
     */
    private static void drawAnchor(VertexConsumer vc, Matrix4f m, ClientLevel level, IncarcerousRopesClientState.Bind bind,
                                   Rope shins, Vec3 feet, double thickness, double t, double sinceRelease, double ground,
                                   Vec3 camera) {
        if (bind.anchor == null) {
            return;
        }
        Vec3 from = shins.path.get(shins.path.size() - 1);
        if (bind.stake == null) {
            // Fixed once: a stake planted just outside the feet, on the side the leg rope ended.
            Vec3 out = new Vec3(from.x - feet.x, 0.0, from.z - feet.z);
            out = out.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : out.normalize();
            bind.stake = groundBelow(level, bind.anchor.add(out.scale(0.6)));
        }
        Vec3 stake = bind.stake;
        double grow = Mth.clamp((t - IncarcerousRopeTimeline.CINCHED) / IncarcerousRopeTimeline.ANCHOR_GROW, 0.0, 1.0);
        if (grow <= 0.0) {
            return;
        }
        double flat = Math.hypot(from.x - stake.x, from.z - stake.z);
        double sag = Math.max(0.0, 2.2 - flat) * 0.18;
        Vec3 mid = from.lerp(stake, 0.5).add(0.0, -sag, 0.0);
        List<Vec3> path = new ArrayList<>();
        for (int i = 0; i <= 16; i++) {
            double s = i / 16.0;
            Vec3 p = quadratic(from, mid, stake, s);
            path.add(fall(new Vec3(p.x, Math.max(stake.y + RADIUS * 0.8, p.y), p.z), ground, sinceRelease));
        }
        double[] arc = cumulative(path);
        List<Vec3> pts = new ArrayList<>();
        List<Double> along = new ArrayList<>();
        sliceInto(path, arc, 0.0, arc[arc.length - 1] * easeOut(grow), pts, along);
        if (pts.size() >= 2) {
            tube(vc, m, level, pts, along, filled(pts.size(), thickness), camera, false, true);
        }
        if (grow >= 1.0) {
            // The rope coiled flat round the stake where it bites into the ground.
            List<Vec3> ring = new ArrayList<>();
            for (int i = 0; i <= 16; i++) {
                double a = Math.PI * 2.0 * i / 16.0;
                ring.add(new Vec3(stake.x + Math.cos(a) * 0.1, stake.y + RADIUS * 0.8, stake.z + Math.sin(a) * 0.1));
            }
            tubeConstant(vc, m, level, ring, thickness, camera);
        }
    }

    /** A target bound mid-jump has its tether point in the air: stake the rope on the first solid top below it. */
    private static Vec3 groundBelow(ClientLevel level, Vec3 p) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z));
        for (int i = 0; i < 4; i++) {
            if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                return new Vec3(p.x, cursor.getY() + 1.0, p.z);
            }
            cursor.move(0, -1, 0);
        }
        return p;
    }

    /** Master: a rope from an extra target's chest to the main target's, slung between them. */
    private static void drawLink(VertexConsumer vc, Matrix4f m, ClientLevel level, LivingEntity from, LivingEntity to,
                                 float partial, double thickness, double t, double sinceRelease, Vec3 camera) {
        double grow = Mth.clamp((t - IncarcerousRopeTimeline.CINCHED) / IncarcerousRopeTimeline.ANCHOR_GROW, 0.0, 1.0);
        if (grow <= 0.0) {
            return;
        }
        Vec3 a = from.getPosition(partial).add(0.0, from.getBbHeight() * 0.62, 0.0);
        Vec3 b = to.getPosition(partial).add(0.0, to.getBbHeight() * 0.62, 0.0);
        Vec3 dir = new Vec3(b.x - a.x, 0.0, b.z - a.z);
        if (dir.lengthSqr() > 1.0E-6) {
            dir = dir.normalize();
            a = a.add(dir.scale(from.getBbWidth() * 0.5));
            b = b.subtract(dir.scale(to.getBbWidth() * 0.5));
        }
        Vec3 mid = a.lerp(b, 0.5).add(0.0, -0.3, 0.0);
        List<Vec3> path = new ArrayList<>();
        for (int i = 0; i <= 20; i++) {
            path.add(fall(quadratic(a, mid, b, i / 20.0), from.getY(), sinceRelease));
        }
        double[] arc = cumulative(path);
        List<Vec3> pts = new ArrayList<>();
        List<Double> along = new ArrayList<>();
        sliceInto(path, arc, 0.0, arc[arc.length - 1] * easeOut(grow), pts, along);
        if (pts.size() >= 2) {
            tube(vc, m, level, pts, along, filled(pts.size(), thickness * 0.85), camera, true, true);
        }
    }

    // ---- Geometry --------------------------------------------------------------------------------------------

    private static void tubeConstant(VertexConsumer vc, Matrix4f m, ClientLevel level, List<Vec3> pts, double radius, Vec3 camera) {
        double[] arc = cumulative(pts);
        List<Double> along = new ArrayList<>(arc.length);
        for (double d : arc) {
            along.add(d);
        }
        tube(vc, m, level, pts, along, filled(pts.size(), radius), camera, true, true);
    }

    /**
     * A textured tube along {@code pts} (world space), framed by parallel transport so the strands don't
     * twist or flip along the bends. {@code along} is each point's arc length, which the texture runs on.
     */
    private static void tube(VertexConsumer vc, Matrix4f m, ClientLevel level, List<Vec3> pts, List<Double> along,
                             double[] radius, Vec3 camera, boolean capStart, boolean capEnd) {
        int n = pts.size();
        Vec3[] tangents = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 d = pts.get(Math.min(n - 1, i + 1)).subtract(pts.get(Math.max(0, i - 1)));
            tangents[i] = d.lengthSqr() < 1.0E-12 ? (i > 0 ? tangents[i - 1] : UP) : d.normalize();
        }
        Vec3 normal = perpendicular(tangents[0]);
        float[][] ringPos = new float[n][(SIDES + 1) * 3];
        float[][] ringNrm = new float[n][(SIDES + 1) * 3];
        int[] light = new int[n];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            Vec3 tg = tangents[i];
            Vec3 projected = normal.subtract(tg.scale(normal.dot(tg)));
            normal = projected.lengthSqr() < 1.0E-8 ? perpendicular(tg) : projected.normalize();
            Vec3 binormal = tg.cross(normal);
            Vec3 p = pts.get(i);
            Vec3 rel = p.subtract(camera);
            light[i] = LevelRenderer.getLightColor(level, cursor.set(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z)));
            for (int j = 0; j <= SIDES; j++) {
                double a = Math.PI * 2.0 * j / SIDES;
                double cx = Math.cos(a);
                double sy = Math.sin(a);
                double nx = normal.x * cx + binormal.x * sy;
                double ny = normal.y * cx + binormal.y * sy;
                double nz = normal.z * cx + binormal.z * sy;
                ringPos[i][j * 3] = (float) (rel.x + nx * radius[i]);
                ringPos[i][j * 3 + 1] = (float) (rel.y + ny * radius[i]);
                ringPos[i][j * 3 + 2] = (float) (rel.z + nz * radius[i]);
                ringNrm[i][j * 3] = (float) nx;
                ringNrm[i][j * 3 + 1] = (float) ny;
                ringNrm[i][j * 3 + 2] = (float) nz;
            }
        }
        for (int i = 0; i + 1 < n; i++) {
            float v0 = (float) (along.get(i) * V_PER_BLOCK);
            float v1 = (float) (along.get(i + 1) * V_PER_BLOCK);
            for (int j = 0; j < SIDES; j++) {
                float u0 = j / (float) SIDES;
                float u1 = (j + 1) / (float) SIDES;
                vertex(vc, m, ringPos[i], ringNrm[i], j, u0, v0, light[i]);
                vertex(vc, m, ringPos[i + 1], ringNrm[i + 1], j, u0, v1, light[i + 1]);
                vertex(vc, m, ringPos[i + 1], ringNrm[i + 1], j + 1, u1, v1, light[i + 1]);
                vertex(vc, m, ringPos[i], ringNrm[i], j + 1, u1, v0, light[i]);
            }
        }
        if (capStart) {
            cap(vc, m, pts.get(0).subtract(camera), tangents[0].scale(-1.0), ringPos[0], light[0]);
        }
        if (capEnd) {
            cap(vc, m, pts.get(n - 1).subtract(camera), tangents[n - 1], ringPos[n - 1], light[n - 1]);
        }
    }

    /** A cut rope end: a fan of degenerate quads closing the ring, sampled from the groove so it reads as the dark core. */
    private static void cap(VertexConsumer vc, Matrix4f m, Vec3 centre, Vec3 facing, float[] ring, int light) {
        float cx = (float) centre.x;
        float cy = (float) centre.y;
        float cz = (float) centre.z;
        float nx = (float) facing.x;
        float ny = (float) facing.y;
        float nz = (float) facing.z;
        for (int j = 0; j < SIDES; j++) {
            capVertex(vc, m, cx, cy, cz, nx, ny, nz, light);
            capVertex(vc, m, cx, cy, cz, nx, ny, nz, light);
            capVertex(vc, m, ring[(j + 1) * 3], ring[(j + 1) * 3 + 1], ring[(j + 1) * 3 + 2], nx, ny, nz, light);
            capVertex(vc, m, ring[j * 3], ring[j * 3 + 1], ring[j * 3 + 2], nx, ny, nz, light);
        }
    }

    private static void capVertex(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                                  float nx, float ny, float nz, int light) {
        vc.addVertex(m, x, y, z).setColor(150, 130, 110, 255).setUv(0.0f, 0.0f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float[] pos, float[] nrm, int j, float u, float v, int light) {
        vc.addVertex(m, pos[j * 3], pos[j * 3 + 1], pos[j * 3 + 2]).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(nrm[j * 3], nrm[j * 3 + 1], nrm[j * 3 + 2]);
    }

    /**
     * The magic behind a flying rope: a soft additive ribbon over the last stretch behind the head, facing the
     * camera (the origin), brightest at the head.
     */
    private static void drawStreak(VertexConsumer vc, Matrix4f m, Streak streak) {
        List<Vec3> pts = streak.points();
        double fade = streak.fade();
        int n = pts.size();
        for (int i = 0; i + 1 < n; i++) {
            Vec3 a = pts.get(i);
            Vec3 b = pts.get(i + 1);
            Vec3 tg = b.subtract(a);
            Vec3 sideA = tg.cross(a);
            Vec3 sideB = tg.cross(b);
            if (sideA.lengthSqr() < 1.0E-10 || sideB.lengthSqr() < 1.0E-10) {
                continue;
            }
            double ua = i / (double) (n - 1);
            double ub = (i + 1) / (double) (n - 1);
            sideA = sideA.normalize().scale(0.05 + 0.12 * ua);
            sideB = sideB.normalize().scale(0.05 + 0.12 * ub);
            int alphaA = (int) (255 * 0.55 * ua * fade);
            int alphaB = (int) (255 * 0.55 * ub * fade);
            glowVertex(vc, m, a.add(sideA), alphaA);
            glowVertex(vc, m, b.add(sideB), alphaB);
            glowVertex(vc, m, b.subtract(sideB), alphaB);
            glowVertex(vc, m, a.subtract(sideA), alphaA);
        }
    }

    private static void glowVertex(VertexConsumer vc, Matrix4f m, Vec3 p, int alpha) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(255, 214, 140, alpha);
    }

    private static void sliceInto(List<Vec3> path, double[] arc, double from, double to, List<Vec3> pts, List<Double> along) {
        int n = path.size();
        from = Math.max(0.0, from);
        to = Math.min(arc[n - 1], to);
        if (to <= from) {
            return;
        }
        pts.add(pointAt(path, arc, from));
        along.add(from);
        for (int i = 0; i < n; i++) {
            if (arc[i] > from && arc[i] < to) {
                pts.add(path.get(i));
                along.add(arc[i]);
            }
        }
        pts.add(pointAt(path, arc, to));
        along.add(to);
    }

    private static Vec3 pointAt(List<Vec3> path, double[] arc, double d) {
        int n = path.size();
        for (int i = 1; i < n; i++) {
            if (arc[i] >= d) {
                double seg = arc[i] - arc[i - 1];
                double f = seg < 1.0E-9 ? 0.0 : (d - arc[i - 1]) / seg;
                return path.get(i - 1).lerp(path.get(i), f);
            }
        }
        return path.get(n - 1);
    }

    private static double[] cumulative(List<Vec3> pts) {
        double[] arc = new double[pts.size()];
        for (int i = 1; i < pts.size(); i++) {
            arc[i] = arc[i - 1] + pts.get(i).distanceTo(pts.get(i - 1));
        }
        return arc;
    }

    private static double length(List<Vec3> pts) {
        double[] arc = cumulative(pts);
        return arc[arc.length - 1];
    }

    private static double[] filled(int n, double value) {
        double[] out = new double[n];
        java.util.Arrays.fill(out, value);
        return out;
    }

    private static Vec3 perpendicular(Vec3 v) {
        Vec3 axis = Math.abs(v.y) < 0.9 ? UP : new Vec3(1.0, 0.0, 0.0);
        return v.cross(axis).normalize();
    }

    private static Vec3 cubic(Vec3 a, Vec3 b, Vec3 c, Vec3 d, double t) {
        double u = 1.0 - t;
        return a.scale(u * u * u).add(b.scale(3.0 * u * u * t)).add(c.scale(3.0 * u * t * t)).add(d.scale(t * t * t));
    }

    private static Vec3 quadratic(Vec3 a, Vec3 c, Vec3 b, double t) {
        double u = 1.0 - t;
        return a.scale(u * u).add(c.scale(2.0 * u * t)).add(b.scale(t * t));
    }

    private static double easeOut(double x) {
        return 1.0 - (1.0 - x) * (1.0 - x);
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }
}
