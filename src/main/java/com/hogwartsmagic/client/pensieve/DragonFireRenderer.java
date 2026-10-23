package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.entity.MemoryHorntailEntity;
import com.hogwartsmagic.pensieve.scene.DragonFire;
import com.hogwartsmagic.pensieve.scene.DragonFlight;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.ScorchDecal;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The memory Horntail's fire: every {@code breathe} of every {@code memory_horntail} actor, drawn from the pure numbers
 * of {@link DragonFire} so it is the same for every viewer and replay. One jet is a stream of burning gas leaving her
 * mouth: each puff keeps the mouth and aim it was born with (a sweeping head bends the stream), so the jet is drawn as
 * <ul>
 *   <li><b>the core:</b> camera-facing flame ribbons threaded through the puffs' centres, additive, a white-gold core
 *       inside orange inside a dark-red edge, the streaming flame texture fixed to the gas so it flows outward;</li>
 *   <li><b>the billows:</b> a textured, spinning flame sprite per puff, widening and cooling as it flies; where the ray
 *       meets a block the puffs splash and spread along the surface as licking tongues;</li>
 *   <li><b>smoke:</b> black smoke rolling up off the front and the splash, lit orange while it is near the fire;</li>
 *   <li><b>the heat shimmer:</b> faint, wavering translucent ribbons over and around the jet (no post shader), and over
 *       each glowing scorch;</li>
 *   <li><b>light:</b> a bloom at the mouth, a haze along the jet and a glow where it splashes (full-bright; it lights
 *       nothing);</li>
 *   <li><b>scorch:</b> soot decals on the hit block faces out to {@link DragonFire#scorchRadius}, glowing embers for
 *       {@link DragonFire#EMBER_TICKS} after the last hit, staying until the scene ends; a late joiner sees them;</li>
 *   <li>a sprinkle of vanilla flame, lava and smoke particles, at most {@link DragonFire#MAX_PARTICLES} alive a jet.</li>
 * </ul>
 * <b>Where the jet leaves from:</b> her drawn mouth, stored on the puppet by its renderer this very frame
 * ({@link MemoryHorntailEntity#snoutIn}, stamped with {@link #frame()}); a snout from an older frame (she was not drawn)
 * is never used: the mouth is then estimated from her pose and head look ({@link DragonFlight#mouthEstimate}). A
 * puff born between two frames takes the mouth between them ({@link DragonFire#birth}). The aim is the {@code target}
 * (a point, or an actor's chest), else her head's direction; each puff's ray is cut at the first block (a client
 * raycast against the set), and where it splashes, one more ray along its splash direction holds the splash short of
 * any block beside or behind the face ({@link DragonFire#splashReach}).
 *
 * <p><b>The scorch's cost</b> ({@link ScorchDecal}): each spot's cells are baked once for its radius and replayed;
 * spots out of view or past {@link ScorchDecal#CULL_DISTANCE} are skipped, and a pass draws at most
 * {@link ScorchDecal#MAX_FRAME_CELLS} cells, nearest first. A spot's faces are listed at most
 * {@link ScorchDecal#MAX_BUILDS_PER_FRAME} spots a frame and rechecked against the set a few spots a tick, so soot on a
 * block a memory event removes goes with it.
 *
 * <p>Drawn at {@code AFTER_TRANSLUCENT_BLOCKS}, after the actors (so the mouth is this frame's), from an identity
 * matrix (stages after the camera push start from identity). Smoke farther than the jet is drawn before the flame and
 * smoke nearer the camera after it. In a {@code dev_} scene a {@code Pensieve fire:} line logs the CPU cost every 5 s
 * (the scorch's share apart), the frame rate, an estimate of the sprites' screen overdraw and whether Fabulous
 * graphics is on.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class DragonFireRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/" + name + ".png");
    }

    private static final ResourceLocation SPRITES = tex("dragon_fire_sprites");
    private static final RenderType SPRITE_GLOW = ModRenderTypes.ceilingGlow(SPRITES);
    private static final RenderType SPRITE_SMOKE = ModRenderTypes.ceilingLayer(SPRITES);
    private static final RenderType BODY = ModRenderTypes.ceilingGlow(tex("dragon_fire_body"));
    private static final RenderType SHIMMER = ModRenderTypes.ceilingLayer(tex("dragon_fire_shimmer"));
    private static final RenderType SCORCH = ModRenderTypes.ceilingLayer(tex("dragon_scorch"));
    private static final RenderType EMBERS = ModRenderTypes.ceilingGlow(tex("dragon_embers"));
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;

    /** A smoke sprite's half-size is held to this share of its distance to the camera (overdraw near the lens). */
    private static final double SMOKE_NEAR_CAP = 0.6;
    /** Spots whose faces are rechecked against the set each client tick. */
    private static final int FACE_CHECKS_PER_TICK = 2;

    /** The render frame: the Horntail renderer stamps her snout with it, this class moves it on after drawing. */
    private static long frame;

    private static final Map<String, Jet> JETS = new LinkedHashMap<>();
    private static SceneDef forScene;
    private static BlockPos forOrigin;
    private static float lastNow;

    // The dev timer.
    private static long timedNanos, worstNanos, decalNanos, worstDecalNanos, lastLog;
    private static int timedFrames, staleFrames, lastPuffs, lastSmoke, lastDecals, lastEmbers, droppedSpots, faceDrops;
    private static double worstAim, overdraw, sumOverdraw, worstOverdraw;
    /** This frame's projection scale (1 / tan of the half fov, across and up), for the overdraw estimate. */
    private static double projX = 1, projY = 1;
    private static Vec3 viewDir = new Vec3(0, 0, 1);
    private static int faceCheckCursor;
    /** The camera-facing core sprites for ribbon points seen end on, this frame: {x, y, z, half, r, g, b, cell}. */
    private static final List<double[]> HEAD_ON = new ArrayList<>();

    private DragonFireRenderer() {
    }

    /** This render frame's number (see {@link MemoryHorntailEntity#snoutIn}). */
    public static long frame() {
        return frame;
    }

    /** One breathe: its puffs as they were born, and the places it scorched. */
    private static final class Jet {
        final ActorRuntime actor;
        final SceneDef.ActionCue cue;
        final float t0;
        final float duration;
        final long seed;
        final Rec[] recs;
        int made;
        final List<DragonFire.Spot> spots = new ArrayList<>();
        final Map<DragonFire.Spot, SpotDraw> draws = new java.util.IdentityHashMap<>();
        final DragonFire.Budget budget = new DragonFire.Budget(DragonFire.MAX_PARTICLES);
        double[] lastO, lastD;
        float lastT = Float.NaN;

        Jet(ActorRuntime actor, SceneDef.ActionCue cue) {
            this.actor = actor;
            this.cue = cue;
            this.t0 = cue.t();
            this.duration = Float.isNaN(cue.duration()) ? DragonFire.DEFAULT_DURATION : cue.duration();
            float[] root = actor.def.path().isEmpty() ? new float[]{0, 0, 0} : actor.def.path().sample(t0);
            this.seed = DragonFire.seed(actor.def.id(), t0, root);
            this.recs = new Rec[DragonFire.puffs(duration)];
        }

        double scale() {
            return actor.def.scale();
        }

        boolean emitting(float now) {
            return now >= t0 && now <= t0 + duration;
        }

        boolean burning(float now) {
            return now >= t0 && now <= t0 + duration + DragonFire.MAX_REACH / DragonFire.SPEED + DragonFire.SPLASH_TICKS;
        }
    }

    /**
     * One puff, as born: mouth, aim, where its ray meets a block (distance, normal), how far its splash may spread
     * before a block, and its look.
     */
    private record Rec(double[] o, double[] d, double hit, double[] n, double splashReach, DragonFire.Look look,
                       double tb, double birthAge, boolean emitted, boolean smokes) {
    }

    /** A scorch spot's faces (null until listed) and its baked soot and embers (null until baked, or stale). */
    private static final class SpotDraw {
        List<ScorchDecal.Face> faces;
        ScorchDecal.Baked soot, embers;
    }

    // ---- Keeping up with the scene --------------------------------------------------------------------------------

    private static void clear() {
        JETS.clear();
        forScene = null;
        forOrigin = null;
    }

    /** The jets of the scene now: new ones as their breathe begins; all forgotten when the memory or scene changes. */
    private static void sync(float now) {
        SceneDef scene = PensieveClientState.scene();
        BlockPos origin = PensieveClientState.origin();
        if (scene != forScene || origin == null || !origin.equals(forOrigin) || now < lastNow - 10) {
            JETS.clear();
            forScene = scene;
            forOrigin = origin;
        }
        lastNow = now;
        for (ActorRuntime a : MemoryActors.horntails()) {
            List<SceneDef.ActionCue> acts = a.def.actions();
            for (int i = 0; i < acts.size(); i++) {
                SceneDef.ActionCue c = acts.get(i);
                if (!DragonFire.ACTION.equals(c.action()) || c.t() > now) continue;
                String key = a.def.id() + "#" + i;
                Jet j = JETS.get(key);
                if (j == null || j.actor != a) JETS.put(key, new Jet(a, c));
            }
        }
    }

    /** Births every puff whose moment has come, with this frame's mouth or the pose's estimate (see the class comment). */
    private static void update(Jet j, ClientLevel level, float now, long thisFrame, Vec3 origin) {
        MemoryHorntailEntity dragon = j.actor.entity instanceof MemoryHorntailEntity m ? m : null;
        double[] sn = dragon == null ? null : dragon.snoutIn(thisFrame);
        double[] curO = null, curD = null;
        if (sn != null) {
            curO = new double[]{sn[0], sn[1], sn[2]};
            curD = new double[]{sn[3], sn[4], sn[5]};
        } else if (j.emitting(now)) {
            staleFrames++;
        }
        double[] org = {origin.x, origin.y, origin.z};
        double[] to = target(j, org);
        while (j.made < j.recs.length) {
            int k = j.made;
            double tb = j.t0 + DragonFire.birth(k);
            if (tb > now) break;
            double lastT = j.lastO == null ? Double.NaN : j.lastT;
            DragonFire.Birth b = DragonFire.birth(tb, now, j.lastO, j.lastD, lastT, curO, curD,
                    () -> mouthFromPose(j.actor.def, tb, org), to);
            double[] o = b.o(), d = b.d();
            worstAim = Math.max(worstAim, Math.toDegrees(Math.acos(Mth.clamp(dot(d, b.head()), -1, 1))));
            double[] n = new double[3];
            double hit = raycast(level, o, d, n, DragonFire.MAX_REACH);
            DragonFire.Look look = DragonFire.look(j.seed, k);
            double age = DragonFire.birth(k);
            boolean emitted = DragonFire.emitted(look, age, j.duration);
            double reach = Double.POSITIVE_INFINITY;
            if (emitted && hit < DragonFire.MAX_REACH) {
                // One more ray along its splash: it never spreads through the rock beside or behind the face.
                double[] e = DragonFire.splashDir(d, n, look);
                double lift = DragonFire.SPLASH_RAY_LIFT;
                double[] from = {o[0] + d[0] * hit + n[0] * lift, o[1] + d[1] * hit + n[1] * lift,
                        o[2] + d[2] * hit + n[2] * lift};
                reach = DragonFire.splashReach(raycast(level, from, e, new double[3],
                        DragonFire.SPLASH_SPREAD * j.scale()));
            }
            j.recs[k] = new Rec(o, d, hit, n, reach, look, tb, age, emitted,
                    emitted && DragonFire.smokes(look, age, j.duration));
            if (emitted && hit < DragonFire.MAX_REACH) {
                DragonFire.addHit(j.spots, o[0] + d[0] * hit, o[1] + d[1] * hit, o[2] + d[2] * hit, n,
                        tb + hit / DragonFire.SPEED, ScorchDecal.MAX_SPOTS);
            }
            j.made++;
        }
        if (curO != null) {
            j.lastO = curO;
            j.lastD = curD;
            j.lastT = now;
        }
    }

    /** Her mouth from her pose and head look alone (world, and the head's direction): DragonFlight.mouthEstimate. */
    private static double[] mouthFromPose(SceneDef.ActorDef def, double t, double[] origin) {
        double[] m = DragonFlight.mouthEstimate(def, t);
        return new double[]{m[0] + origin[0], m[1] + origin[1], m[2] + origin[2], m[3], m[4], m[5]};
    }

    /** The breathe's target in the world (a point, or an actor's chest), or null for none: then she aims along her head. */
    private static double[] target(Jet j, double[] origin) {
        SceneDef.Target t = j.cue.target();
        if (t != null && t.actor() != null) {
            ActorRuntime other = MemoryActors.actor(t.actor());
            if (other == null) return null;
            Entity e = other.entity;
            return new double[]{e.getX(), e.getY() + e.getBbHeight() * other.def.scale() * 0.65, e.getZ()};
        }
        if (t != null && t.point() != null && t.point().length >= 3) return DragonFire.pointTarget(t.point(), origin);
        return null;
    }

    /** The distance to the first block along the ray (the face normal into {@code n}), or +infinity within the reach. */
    private static double raycast(ClientLevel level, double[] o, double[] d, double[] n, double reach) {
        Vec3 from = new Vec3(o[0], o[1], o[2]);
        Vec3 to = from.add(d[0] * reach, d[1] * reach, d[2] * reach);
        BlockHitResult r = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        if (r.getType() != HitResult.Type.BLOCK) return Double.POSITIVE_INFINITY;
        Direction f = r.getDirection();
        n[0] = f.getStepX();
        n[1] = f.getStepY();
        n[2] = f.getStepZ();
        return r.getLocation().distanceTo(from);
    }

    // ---- Drawing --------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        long thisFrame = frame++;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.level instanceof ClientLevel level) || !PensieveClientState.inMemory()) {
            if (!JETS.isEmpty() || forScene != null) clear();
            return;
        }
        float now = PensieveClientState.sceneTime(event.getPartialTick());
        sync(now);
        if (JETS.isEmpty()) return;
        long start = System.nanoTime();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vec3 origin = MemoryEvents.sceneOrigin();
        for (Jet j : JETS.values()) update(j, level, now, thisFrame, origin);
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector(), look = camera.getLookVector();
        double[] R = {-left.x(), -left.y(), -left.z()}, U = {up.x(), up.y(), up.z()};
        viewDir = new Vec3(look.x(), look.y(), look.z());
        Matrix4f proj = event.getProjectionMatrix();
        projX = Math.abs(proj.m00());
        projY = Math.abs(proj.m11());
        overdraw = 0;
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        int puffs = 0, smoke = 0;

        // 1. The soot on the scorched faces, then its embers (depth-tested, so the set hides what is behind it).
        long decalStart = System.nanoTime();
        scorchPasses(buffer, m, level, now, cam, event.getFrustum());
        long decalTime = System.nanoTime() - decalStart;
        // 2. Smoke behind the fire: the puffs no nearer the camera than the jet's nearest point.
        double near = nearestFire(now, cam);
        VertexConsumer vc = buffer.getBuffer(SPRITE_SMOKE);
        for (Jet j : JETS.values()) smoke += smoke(vc, m, j, now, cam, R, U, near, false);
        buffer.endBatch(SPRITE_SMOKE);
        // 3. The heat shimmer.
        vc = buffer.getBuffer(SHIMMER);
        for (Jet j : JETS.values()) shimmer(vc, m, j, now, cam);
        buffer.endBatch(SHIMMER);
        // 4. The flame: its body (straight alpha), then the light over it: ribbons, billows, glows.
        vc = buffer.getBuffer(SPRITE_SMOKE);
        for (Jet j : JETS.values()) billows(vc, m, j, now, cam, R, U, true);
        buffer.endBatch(SPRITE_SMOKE);
        HEAD_ON.clear();
        vc = buffer.getBuffer(BODY);
        for (Jet j : JETS.values()) core(vc, m, j, now, cam);
        buffer.endBatch(BODY);
        vc = buffer.getBuffer(SPRITE_GLOW);
        for (Jet j : JETS.values()) puffs += billows(vc, m, j, now, cam, R, U, false);
        // Where a ribbon is seen end on (looking down the jet) it thins to nothing: a camera-facing core instead.
        for (double[] h : HEAD_ON) {
            sprite(vc, m, h[0], h[1], h[2], h[3], now * 0.07, (int) h[7] & 3, 0, cam, R, U,
                    new float[]{(float) h[4], (float) h[5], (float) h[6]}, 1f);
        }
        buffer.endBatch(SPRITE_GLOW);
        // 5. Smoke in front of the fire, over it.
        vc = buffer.getBuffer(SPRITE_SMOKE);
        for (Jet j : JETS.values()) smoke += smoke(vc, m, j, now, cam, R, U, near, true);
        buffer.endBatch(SPRITE_SMOKE);
        lastPuffs = puffs;
        lastSmoke = smoke;
        debugTimer(System.nanoTime() - start, decalTime, mc);
    }

    /** How near the camera the fire comes this frame: its jets' centre lines and the splashes landing now. */
    private static double nearestFire(float now, Vec3 cam) {
        double near = Double.POSITIVE_INFINITY;
        for (Jet j : JETS.values()) {
            if (!j.burning(now)) continue;
            for (double[] p : axis(j, now)) near = Math.min(near, dist(p, cam));
            for (DragonFire.Spot s : j.spots) {
                if (now - s.lastHit(now) <= DragonFire.SPLASH_TICKS) near = Math.min(near, dist(new double[]{s.x, s.y, s.z}, cam));
            }
        }
        return near;
    }

    private static double dist(double[] p, Vec3 cam) {
        double dx = p[0] - cam.x, dy = p[1] - cam.y, dz = p[2] - cam.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Adds a quad of {@code area} square blocks centred at p to this frame's overdraw estimate: the share of the
     * screen it covers (at most 1), from the projection's scale. In front of the camera only.
     */
    private static void countOverdraw(double x, double y, double z, double area, Vec3 cam) {
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
        double depth = dx * viewDir.x + dy * viewDir.y + dz * viewDir.z;
        double d2 = dx * dx + dy * dy + dz * dz;
        if (depth <= 0.05 && d2 > area) return;
        // NDC spans 2 x 2: a quad of side a at distance d covers (a * px / d) x (a * py / d) of it.
        overdraw += Math.min(1, area * projX * projY / (4 * Math.max(0.05, d2)));
    }

    /** The live flying puffs' centre line, newest (at the mouth) first: {x, y, z, s, birthAge} per point. */
    private static List<double[]> axis(Jet j, float now) {
        List<double[]> pts = new ArrayList<>();
        if (j.emitting(now) && j.lastO != null && now - j.lastT < 1) {
            pts.add(new double[]{j.lastO[0], j.lastO[1], j.lastO[2], 0, now - j.t0});
        }
        for (int k = j.made - 1; k >= 0; k -= 2) {
            Rec r = j.recs[k];
            double a = now - r.tb();
            double s = DragonFire.SPEED * a;
            if (s > Math.min(r.hit(), DragonFire.MAX_REACH)) break;
            double rise = 0.0035 * a * a * j.scale();
            pts.add(new double[]{r.o()[0] + r.d()[0] * s, r.o()[1] + r.d()[1] * s + rise, r.o()[2] + r.d()[2] * s, s,
                    r.birthAge()});
        }
        return pts;
    }

    /** The flame ribbons: three nested layers through the jet's centre line, the texture streaming with the gas. */
    private static void core(VertexConsumer vc, Matrix4f m, Jet j, float now, Vec3 cam) {
        List<double[]> pts = axis(j, now);
        if (pts.size() < 2) return;
        double sc = j.scale();
        // {width share of the puff radius, brightness, heat lift, texture scale, texture drift}
        double[][] layers = {{1.15, 0.15, 0.0, 0.22, 0.0}, {0.62, 0.21, 0.18, 0.31, 0.07}, {0.26, 0.28, 0.45, 0.45, 0.13}};
        for (double[] layer : layers) {
            for (int i = 0; i + 1 < pts.size(); i++) {
                double[] a = pts.get(i), b = pts.get(i + 1);
                ribbon(vc, m, cam, a, b, layer, j, now, sc, pts.size(), i);
            }
        }
    }

    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 cam, double[] a, double[] b, double[] layer, Jet j,
                               float now, double sc, int n, int i) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double wa = DragonFire.radius(a[3]) * sc * layer[0], wb = DragonFire.radius(b[3]) * sc * layer[0];
        // The jet's tail fades over its last points, and each point carries its puff's strength.
        double fa = DragonFire.strength(a[4], j.duration) * tailFade(i, n), fb = DragonFire.strength(b[4], j.duration) * tailFade(i + 1, n);
        float[] ca = heatColour(Math.min(1, DragonFire.heat(a[3]) + layer[2])), cb = heatColour(Math.min(1, DragonFire.heat(b[3]) + layer[2]));
        // Seen end on, the ribbon fades out (its width across the view goes to nothing) and a core sprite fades in.
        double end = endOn(a, dx, dy, dz, cam);
        double keep = smooth((end - 0.15) / 0.15);
        if (keep < 1 && layer[1] * fa * (1 - keep) > 0.004) {
            double k = layer[1] * fa * (1 - keep) * 0.8;
            HEAD_ON.add(new double[]{a[0], a[1], a[2], wa * 1.1, ca[0] * k, ca[1] * k, ca[2] * k, i});
        }
        double[] sa = side(a, dx, dy, dz, cam), sb = side(b, dx, dy, dz, cam);
        if (sa == null || sb == null || keep <= 0) return;
        countOverdraw((a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2,
                (wa + wb) * Math.sqrt(dx * dx + dy * dy + dz * dz), cam);
        // Additive (ONE, ONE): the light added is the colour itself, so the strength scales the colour.
        float ka = (float) (layer[1] * fa * keep), kb = (float) (layer[1] * fb * keep);
        ca = new float[]{ca[0] * ka, ca[1] * ka, ca[2] * ka};
        cb = new float[]{cb[0] * kb, cb[1] * kb, cb[2] * kb};
        float va = (float) (-a[4] * DragonFire.SPEED * layer[3] + now * layer[4]), vb = (float) (-b[4] * DragonFire.SPEED * layer[3] + now * layer[4]);
        // The flame texture tiles along v: its own coordinates are fixed to the gas, so it flows outward.
        va += (float) (a[3] * layer[3]);
        vb += (float) (b[3] * layer[3]);
        vertex(vc, m, a[0] - sa[0] * wa - cam.x, a[1] - sa[1] * wa - cam.y, a[2] - sa[2] * wa - cam.z, ca, ka, 0f, va);
        vertex(vc, m, a[0] + sa[0] * wa - cam.x, a[1] + sa[1] * wa - cam.y, a[2] + sa[2] * wa - cam.z, ca, ka, 1f, va);
        vertex(vc, m, b[0] + sb[0] * wb - cam.x, b[1] + sb[1] * wb - cam.y, b[2] + sb[2] * wb - cam.z, cb, kb, 1f, vb);
        vertex(vc, m, b[0] - sb[0] * wb - cam.x, b[1] - sb[1] * wb - cam.y, b[2] - sb[2] * wb - cam.z, cb, kb, 0f, vb);
    }

    private static double tailFade(int i, int n) {
        int fromEnd = n - 1 - i;
        return fromEnd >= 4 ? 1 : fromEnd / 4.0;
    }

    /** The unit vector across a ribbon segment, facing the camera; null when the camera looks straight along it. */
    private static double[] side(double[] p, double dx, double dy, double dz, Vec3 cam) {
        double vx = p[0] - cam.x, vy = p[1] - cam.y, vz = p[2] - cam.z;
        double cx = dy * vz - dz * vy, cy = dz * vx - dx * vz, cz = dx * vy - dy * vx;
        double l = Math.sqrt(cx * cx + cy * cy + cz * cz);
        return l < 1e-6 ? null : new double[]{cx / l, cy / l, cz / l};
    }

    /** How square-on a segment is to the view at p: |seg x view| / (|seg| |view|), 0 end on, 1 side on. */
    private static double endOn(double[] p, double dx, double dy, double dz, Vec3 cam) {
        double vx = p[0] - cam.x, vy = p[1] - cam.y, vz = p[2] - cam.z;
        double cx = dy * vz - dz * vy, cy = dz * vx - dx * vz, cz = dx * vy - dy * vx;
        double ls = Math.sqrt(dx * dx + dy * dy + dz * dz), lv = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (ls < 1e-9 || lv < 1e-9) return 1;
        return Math.sqrt(cx * cx + cy * cy + cz * cz) / (ls * lv);
    }

    /**
     * The puffs. {@code body} true: their straight-alpha body, a deep flame colour that holds on a bright sky (additive
     * light alone washes out to white in daylight). False: the light over it, billowing sprites (licking tongues where
     * they splash), the white-hot throat, the haze along the jet, the mouth's bloom and the splash glow.
     */
    private static int billows(VertexConsumer vc, Matrix4f m, Jet j, float now, Vec3 cam, double[] R, double[] U,
                               boolean body) {
        double sc = j.scale();
        int drawn = 0;
        if (!j.burning(now)) return 0;
        for (int k = 0; k < j.made; k++) {
            Rec r = j.recs[k];
            if (!r.emitted()) continue;
            double a = now - r.tb();
            DragonFire.Flame f = DragonFire.flame(r.o(), r.d(), r.hit(), r.n(), r.look(), a, sc, r.splashReach());
            if (f == null) continue;
            double str = DragonFire.strength(r.birthAge(), j.duration);
            double rot = r.look().spin() + r.look().spinRate() * a;
            boolean splash = f.stage() == DragonFire.Flame.SPLASHING;
            double size = f.radius() * (splash ? 1.4 : 2.0) * (0.55 + 0.45 * str);
            double sway = Math.sin(now * 0.6 + r.look().wobble()) * 0.25;
            if (body) {
                float[] c = heatColour(f.heat() - (splash ? 0 : 0.12));
                c = new float[]{c[0] * 0.95f, c[1] * 0.95f, c[2] * 0.95f};
                float k1 = (float) (0.7 * f.alpha() * str * (splash ? 0.6 : 1));
                if (splash) {
                    sprite(vc, m, f.x(), f.y() + size * 0.35, f.z(), size * 1.1, sway, 2 + (k & 1), 2, cam, R, U, c, k1, false);
                } else {
                    sprite(vc, m, f.x(), f.y(), f.z(), size, rot, r.look().sprite(), 0, cam, R, U, c, k1, false);
                }
                drawn++;
                continue;
            }
            float k1 = (float) (0.05 * f.alpha() * str);
            float[] c = heatColour(f.heat());
            if (splash) {
                // Licking tongues on the surface, upright, swaying.
                sprite(vc, m, f.x(), f.y() + size * 0.35, f.z(), size * 1.1, sway, 2 + (k & 1), 2, cam, R, U, c, k1 * 1.2f);
            } else {
                sprite(vc, m, f.x(), f.y(), f.z(), size, rot, r.look().sprite(), 0, cam, R, U, c, k1);
            }
            // The white-hot throat near the mouth.
            double s = DragonFire.SPEED * a;
            if (f.stage() == DragonFire.Flame.FLYING && s < 3.5) {
                float[] w = heatColour(1);
                sprite(vc, m, f.x(), f.y(), f.z(), size * 0.5, -rot, 0, 2, cam, R, U, w, (float) (0.10 * str * (1 - s / 3.5)));
            }
            // A soft haze of light along the jet.
            if ((k & 3) == 0 && f.stage() != DragonFire.Flame.FADING) {
                sprite(vc, m, f.x(), f.y(), f.z(), size * 2.1, 0, 0, 2, cam, R, U, c, (float) (0.03 * f.alpha() * str));
            }
        }
        if (body) return drawn;
        // The mouth's bloom while she breathes.
        if (j.emitting(now) && j.lastO != null) {
            double str = DragonFire.strength(now - j.t0, j.duration);
            double flick = 0.85 + 0.15 * Math.sin(now * 2.3 + (j.seed & 15));
            sprite(vc, m, j.lastO[0], j.lastO[1], j.lastO[2], 1.3 * sc * str, 0, 0, 2, cam, R, U,
                    new float[]{1f, 0.7f, 0.35f}, (float) (0.45 * str * flick));
        }
        // The splash's glow where the jet is landing now.
        for (DragonFire.Spot s : j.spots) {
            double last = s.lastHit(now);
            if (now - last > 3) continue;
            double k2 = 1 - (now - last) / 3;
            sprite(vc, m, s.x + s.nx * 0.4, s.y + s.ny * 0.4, s.z + s.nz * 0.4, 2.6 * sc, now * 0.05, 0, 2, cam, R, U,
                    new float[]{1f, 0.55f, 0.2f}, (float) (0.15 * k2));
        }
        return drawn;
    }

    /**
     * Smoke puffs: lit orange near the fire, rolling up black. {@code front} false draws the puffs at least
     * {@code near} from the camera (behind the fire), true the nearer ones (in front of it, drawn after the flame).
     */
    private static int smoke(VertexConsumer vc, Matrix4f m, Jet j, float now, Vec3 cam, double[] R, double[] U,
                             double near, boolean front) {
        double sc = j.scale();
        int drawn = 0;
        if (now > j.t0 + DragonFire.lifetime(j.duration)) return 0;
        for (int k = 0; k < j.made; k++) {
            Rec r = j.recs[k];
            if (!r.smokes()) continue;
            DragonFire.Smoke s = DragonFire.smoke(r.o(), r.d(), r.hit(), r.n(), r.look(), now - r.tb(), sc, r.splashReach());
            if (s == null) continue;
            double d = dist(new double[]{s.x(), s.y(), s.z()}, cam);
            if ((d < near) != front) continue;
            float soot = (float) s.soot();
            float[] c = {Mth.lerp(soot, 0.62f, 0.07f), Mth.lerp(soot, 0.32f, 0.065f), Mth.lerp(soot, 0.13f, 0.06f)};
            double rot = r.look().spin() * 0.5 + (now - r.tb()) * 0.02 * (r.look().spinRate() > 0 ? 1 : -1);
            // Held to a share of its distance: a puff rolling past the lens does not fill the screen.
            double half = Math.min(s.radius() * 1.7, SMOKE_NEAR_CAP * d);
            sprite(vc, m, s.x(), s.y(), s.z(), half, rot, r.look().sprite(), 1, cam, R, U, c, (float) (0.62 * s.alpha()));
            drawn++;
        }
        return drawn;
    }

    /** The heat haze: wavering ribbons over the jet and round it, and columns over glowing scorch. */
    private static void shimmer(VertexConsumer vc, Matrix4f m, Jet j, float now, Vec3 cam) {
        double sc = j.scale();
        List<double[]> pts = axis(j, now);
        for (int layer = 0; layer < 2; layer++) {
            for (int i = 0; i + 1 < pts.size(); i++) {
                double[] a = pts.get(i), b = pts.get(i + 1);
                double la = layer == 0 ? DragonFire.radius(a[3]) * sc * 0.95 + 0.35 : 0;
                double lb = layer == 0 ? DragonFire.radius(b[3]) * sc * 0.95 + 0.35 : 0;
                double[] pa = {a[0], a[1] + la, a[2]}, pb = {b[0], b[1] + lb, b[2]};
                double dx = pb[0] - pa[0], dy = pb[1] - pa[1], dz = pb[2] - pa[2];
                double[] sa = side(pa, dx, dy, dz, cam), sb = side(pb, dx, dy, dz, cam);
                if (sa == null || sb == null) continue;
                double share = layer == 0 ? 0.8 : 1.9;
                double wa = (DragonFire.radius(a[3]) * share + 0.3) * sc, wb = (DragonFire.radius(b[3]) * share + 0.3) * sc;
                float ka = (float) ((layer == 0 ? 0.16 : 0.09) * DragonFire.strength(a[4], j.duration) * tailFade(i, pts.size()));
                float kb = (float) ((layer == 0 ? 0.16 : 0.09) * DragonFire.strength(b[4], j.duration) * tailFade(i + 1, pts.size()));
                float va = (float) (a[3] * 0.18 - now * 0.045 + layer * 0.37), vb = (float) (b[3] * 0.18 - now * 0.045 + layer * 0.37);
                float[] w = {1f, 1f, 1f};
                vertex(vc, m, pa[0] - sa[0] * wa - cam.x, pa[1] - sa[1] * wa - cam.y, pa[2] - sa[2] * wa - cam.z, w, ka, 0f, va);
                vertex(vc, m, pa[0] + sa[0] * wa - cam.x, pa[1] + sa[1] * wa - cam.y, pa[2] + sa[2] * wa - cam.z, w, ka, 1f, va);
                vertex(vc, m, pb[0] + sb[0] * wb - cam.x, pb[1] + sb[1] * wb - cam.y, pb[2] + sb[2] * wb - cam.z, w, kb, 1f, vb);
                vertex(vc, m, pb[0] - sb[0] * wb - cam.x, pb[1] - sb[1] * wb - cam.y, pb[2] - sb[2] * wb - cam.z, w, kb, 0f, vb);
            }
        }
        // Over each glowing scorch, heat rising: a column turned to the camera about the vertical.
        for (DragonFire.Spot s : j.spots) {
            double glow = DragonFire.emberGlow(now, s.lastHit(now));
            double rad = s.radius(now);
            if (glow <= 0.01 || rad < 0.2 || s.lastHit(now) == Double.NEGATIVE_INFINITY) continue;
            double vx = s.x - cam.x, vz = s.z - cam.z;
            double l = Math.sqrt(vx * vx + vz * vz);
            if (l < 1e-3) continue;
            double rx = -vz / l * rad, rz = vx / l * rad;
            double y0 = s.y, y1 = s.y + 2.6 * sc;
            float k = (float) (0.11 * glow);
            float v0 = (float) (-now * 0.06), v1 = v0 + 1.2f;
            float[] w = {1f, 1f, 1f};
            vertex(vc, m, s.x - rx - cam.x, y0 - cam.y, s.z - rz - cam.z, w, k, 0f, v0);
            vertex(vc, m, s.x + rx - cam.x, y0 - cam.y, s.z + rz - cam.z, w, k, 1f, v0);
            vertex(vc, m, s.x + rx - cam.x, y1 - cam.y, s.z + rz - cam.z, w, 0f, 1f, v1);
            vertex(vc, m, s.x - rx - cam.x, y1 - cam.y, s.z - rz - cam.z, w, 0f, 0f, v1);
        }
    }

    // ---- The scorch ------------------------------------------------------------------------------------------------

    /** One spot as this frame sees it. */
    private record SpotView(Jet jet, DragonFire.Spot spot, SpotDraw draw, double radius, double distance,
                            boolean visible) {
    }

    /**
     * The soot on every scorched face, then the glowing embers: each spot baked for its radius (rebaked only when the
     * radius changes or its faces do) and replayed, culled by the frustum and {@link ScorchDecal#CULL_DISTANCE}, at
     * most {@link ScorchDecal#MAX_FRAME_CELLS} cells a pass, nearest first.
     */
    private static void scorchPasses(MultiBufferSource.BufferSource buffer, Matrix4f m, ClientLevel level, float now,
                                     Vec3 cam, Frustum frustum) {
        List<SpotView> views = new ArrayList<>();
        int builds = 0;
        for (Jet j : JETS.values()) {
            for (DragonFire.Spot s : j.spots) {
                double rad = s.radius(now);
                if (rad < 0.05) continue;
                double d = dist(new double[]{s.x, s.y, s.z}, cam);
                if (d > ScorchDecal.CULL_DISTANCE + rad) continue;
                SpotDraw sd = j.draws.computeIfAbsent(s, x -> new SpotDraw());
                if (sd.faces == null) {
                    // A late joiner's first frame would list every spot's faces at once: a few a frame instead.
                    if (builds >= ScorchDecal.MAX_BUILDS_PER_FRAME) continue;
                    sd.faces = facesNear(level, s);
                    builds++;
                }
                double e = rad + 0.5;
                boolean vis = frustum == null || frustum.isVisible(new AABB(s.x - e, s.y - e, s.z - e, s.x + e, s.y + e, s.z + e));
                views.add(new SpotView(j, s, sd, rad, d, vis));
            }
        }
        int n = views.size();
        double[] dist = new double[n], rad = new double[n];
        boolean[] vis = new boolean[n];
        int[] cells = new int[n];
        for (int i = 0; i < n; i++) {
            SpotView v = views.get(i);
            dist[i] = v.distance();
            rad[i] = v.radius();
            vis[i] = v.visible();
            if (v.visible() && (v.draw().soot == null || v.draw().soot.reach != v.radius())) {
                v.draw().soot = ScorchDecal.bake(v.draw().faces, v.spot().x, v.spot().y, v.spot().z, v.radius());
            }
            cells[i] = v.visible() ? v.draw().soot.cells : 0;
        }
        int[] soot = ScorchDecal.plan(dist, rad, vis, cells, ScorchDecal.MAX_FRAME_CELLS);
        VertexConsumer vc = buffer.getBuffer(SCORCH);
        int quads = 0;
        for (int i : soot) quads += replay(vc, m, views.get(i), views.get(i).draw().soot, now, cam, false, 1);
        buffer.endBatch(SCORCH);
        // The embers, while they glow: baked over 0.8 of the radius, each face flickering on its own.
        for (int i = 0; i < n; i++) {
            SpotView v = views.get(i);
            double glow = DragonFire.emberGlow(now, v.spot().lastHit(now));
            cells[i] = 0;
            if (!v.visible() || glow <= 0.01) continue;
            double reach = v.radius() * 0.8;
            if (v.draw().embers == null || v.draw().embers.reach != reach) {
                v.draw().embers = ScorchDecal.bake(v.draw().faces, v.spot().x, v.spot().y, v.spot().z, reach);
            }
            cells[i] = v.draw().embers.cells;
        }
        int[] embers = ScorchDecal.plan(dist, rad, vis, cells, ScorchDecal.MAX_FRAME_CELLS);
        vc = buffer.getBuffer(EMBERS);
        int emberQuads = 0;
        for (int i : embers) {
            SpotView v = views.get(i);
            double glow = DragonFire.emberGlow(now, v.spot().lastHit(now));
            emberQuads += replay(vc, m, v, v.draw().embers, now, cam, true, (float) glow);
        }
        buffer.endBatch(EMBERS);
        int shown = 0;
        for (boolean b : vis) if (b) shown++;
        droppedSpots = Math.max(droppedSpots, shown - soot.length);
        lastDecals = quads;
        lastEmbers = emberQuads;
    }

    /** Replays a baked decal with the camera offset and this distance's lift; the embers scaled by glow and flicker. */
    private static int replay(VertexConsumer vc, Matrix4f m, SpotView v, ScorchDecal.Baked b, float now, Vec3 cam,
                              boolean embers, float glow) {
        List<ScorchDecal.Face> faces = v.draw().faces;
        double lift = ScorchDecal.lift(v.distance());
        double ox = v.spot().x - cam.x, oy = v.spot().y - cam.y, oz = v.spot().z - cam.z;
        float[] flick = null;
        if (embers) {
            flick = new float[faces.size()];
            for (int f = 0; f < flick.length; f++) {
                int h = faces.get(f).hash();
                flick[f] = (float) (glow * (0.7 + 0.3 * Math.sin(now * 0.37 + h * 0.71) * Math.sin(now * 0.23 + h)));
            }
        }
        float[] col = new float[3];
        for (int c = 0; c < b.cells; c++) {
            ScorchDecal.Face f = faces.get(b.face[c]);
            double lx = f.nx() * lift + ox, ly = f.ny() * lift + oy, lz = f.nz() * lift + oz;
            for (int i = 0; i < 4; i++) {
                float fall = b.fall[c * 4 + i];
                float k;
                if (embers) {
                    // Additive: the colour is the light, falling off twice as fast as the soot.
                    float a = fall * flick[b.face[c]] * fall;
                    col[0] = a;
                    col[1] = 0.62f * a;
                    col[2] = 0.32f * a;
                    k = 1f;
                } else {
                    col[0] = col[1] = col[2] = 1f;
                    k = fall * 0.93f;
                }
                vertex(vc, m, b.xyz[c * 12 + i * 3] + lx, b.xyz[c * 12 + i * 3 + 1] + ly, b.xyz[c * 12 + i * 3 + 2] + lz,
                        col, k, b.uv[c * 8 + i * 2], b.uv[c * 8 + i * 2 + 1]);
            }
        }
        return b.cells;
    }

    /**
     * The open faces of the blocks round a spot (on the shape's own box), facing out of the stone toward air, within
     * the widest scorch of its centre, nearest first, at most {@link ScorchDecal#MAX_FACES}.
     */
    private static List<ScorchDecal.Face> facesNear(ClientLevel level, DragonFire.Spot s) {
        List<ScorchDecal.Face> out = new ArrayList<>();
        List<Double> dist = new ArrayList<>();
        double reach = DragonFire.SCORCH_MAX + 0.8;
        int r = (int) Math.ceil(reach) + 1;
        BlockPos c = BlockPos.containing(s.x, s.y, s.z);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState st = level.getBlockState(p);
                    if (st.isAir()) continue;
                    VoxelShape shape = st.getCollisionShape(level, p);
                    if (shape.isEmpty()) continue;
                    AABB bb = shape.bounds();
                    for (Direction d : Direction.values()) {
                        // A face toward the hit side (or across it), not the far side of the stone.
                        double nd = d.getStepX() * s.nx + d.getStepY() * s.ny + d.getStepZ() * s.nz;
                        if (nd < -0.2) continue;
                        BlockPos nb = p.relative(d);
                        BlockState ns = level.getBlockState(nb);
                        if (!ns.isAir() && ns.isSolidRender(level, nb) && touches(bb, d)) continue;
                        ScorchDecal.Face f = face(p, bb, d);
                        double[] mid = f.at(0.5, 0.5);
                        double dd = s.distance(mid[0], mid[1], mid[2]);
                        if (dd > reach) continue;
                        out.add(f);
                        dist.add(dd);
                    }
                }
            }
        }
        Integer[] idx = new Integer[out.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, Comparator.comparingDouble(dist::get));
        List<ScorchDecal.Face> sorted = new ArrayList<>();
        for (int i = 0; i < Math.min(ScorchDecal.MAX_FACES, idx.length); i++) sorted.add(out.get(idx[i]));
        return sorted;
    }

    /**
     * Drops the faces of a few spots a tick whose block has gone (air, or no shape left: a memory event smashed it),
     * so their soot does not hang in the air; the spot is rebaked on its next frame.
     */
    private static void recheckFaces(ClientLevel level) {
        List<SpotDraw> all = new ArrayList<>();
        for (Jet j : JETS.values()) {
            for (SpotDraw sd : j.draws.values()) if (sd.faces != null) all.add(sd);
        }
        if (all.isEmpty()) return;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int k = 0; k < Math.min(FACE_CHECKS_PER_TICK, all.size()); k++) {
            SpotDraw sd = all.get(Math.floorMod(faceCheckCursor++, all.size()));
            List<ScorchDecal.Face> kept = new ArrayList<>(sd.faces.size());
            for (ScorchDecal.Face f : sd.faces) {
                p.set(BlockPos.getX(f.block()), BlockPos.getY(f.block()), BlockPos.getZ(f.block()));
                BlockState st = level.getBlockState(p);
                if (!st.isAir() && !st.getCollisionShape(level, p).isEmpty()) kept.add(f);
            }
            if (kept.size() != sd.faces.size()) {
                faceDrops += sd.faces.size() - kept.size();
                sd.faces = kept;
                sd.soot = null;
                sd.embers = null;
            }
        }
    }

    /** Whether the shape's face on side d lies on the block's own boundary (so a solid neighbour hides it). */
    private static boolean touches(AABB bb, Direction d) {
        // An if-chain, not a switch: no enum switch in client code.
        if (d == Direction.UP) return bb.maxY >= 1 - 1e-4;
        if (d == Direction.DOWN) return bb.minY <= 1e-4;
        if (d == Direction.EAST) return bb.maxX >= 1 - 1e-4;
        if (d == Direction.WEST) return bb.minX <= 1e-4;
        if (d == Direction.SOUTH) return bb.maxZ >= 1 - 1e-4;
        return bb.minZ <= 1e-4;
    }

    /** The face of box {@code bb} (block-local) of the block at p on side d: four corners in order round it. */
    private static ScorchDecal.Face face(BlockPos p, AABB bb, Direction d) {
        double x0 = p.getX() + bb.minX, x1 = p.getX() + bb.maxX, y0 = p.getY() + bb.minY, y1 = p.getY() + bb.maxY;
        double z0 = p.getZ() + bb.minZ, z1 = p.getZ() + bb.maxZ;
        double[][] c;
        if (d == Direction.UP) c = new double[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
        else if (d == Direction.DOWN) c = new double[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
        else if (d == Direction.EAST) c = new double[][]{{x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}};
        else if (d == Direction.WEST) c = new double[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
        else if (d == Direction.SOUTH) c = new double[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
        else c = new double[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}};
        int hash = (p.getX() * 73856093) ^ (p.getY() * 19349663) ^ (p.getZ() * 83492791) ^ d.ordinal();
        return new ScorchDecal.Face(c[0], c[1], c[2], c[3], d.getStepX(), d.getStepY(), d.getStepZ(), hash & 0xFFFF,
                p.asLong());
    }

    // ---- The particle sprinkle ---------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JETS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.level instanceof ClientLevel level) || mc.isPaused() || !PensieveClientState.inMemory()) return;
        float now = PensieveClientState.sceneTime(0);
        long tick = (long) Math.floor(now);
        recheckFaces(level);
        for (Jet j : JETS.values()) {
            try {
                particles(level, j, now, tick);
            } catch (RuntimeException e) {
                LOGGER.warn("Pensieve fire: particles failed: {}", e.toString());
            }
        }
    }

    private static void particles(ClientLevel level, Jet j, float now, long tick) {
        var rnd = level.random;
        double sc = j.scale();
        List<DragonFire.Flame> flying = new ArrayList<>(), splashing = new ArrayList<>();
        if (j.burning(now)) {
            for (int k = 0; k < j.made; k++) {
                Rec r = j.recs[k];
                if (!r.emitted()) continue;
                DragonFire.Flame f = DragonFire.flame(r.o(), r.d(), r.hit(), r.n(), r.look(), now - r.tb(), sc, r.splashReach());
                if (f == null) continue;
                (f.stage() == DragonFire.Flame.SPLASHING ? splashing : flying).add(f);
            }
        }
        if (!flying.isEmpty()) {
            int n = j.budget.allow(tick, 7, 44);
            for (int i = 0; i < n; i++) {
                DragonFire.Flame f = flying.get(rnd.nextInt(flying.size()));
                Rec r = j.recs[Math.max(0, j.made - 1)];
                double v = DragonFire.SPEED * (0.35 + 0.3 * rnd.nextDouble());
                spawn(level, ParticleTypes.FLAME, f, f.radius() * 0.6, r.d()[0] * v, r.d()[1] * v, r.d()[2] * v, rnd);
            }
        }
        if (!splashing.isEmpty()) {
            int n = j.budget.allow(tick, 2, 80);
            for (int i = 0; i < n; i++) {
                DragonFire.Flame f = splashing.get(rnd.nextInt(splashing.size()));
                spawn(level, ParticleTypes.LAVA, f, f.radius() * 0.5, 0, 0, 0, rnd);
            }
            int s = j.budget.allow(tick, 2, 44);
            for (int i = 0; i < s; i++) {
                DragonFire.Flame f = splashing.get(rnd.nextInt(splashing.size()));
                spawn(level, ParticleTypes.FLAME, f, f.radius(), (rnd.nextDouble() - 0.5) * 0.12, 0.05 + rnd.nextDouble() * 0.08,
                        (rnd.nextDouble() - 0.5) * 0.12, rnd);
            }
        }
        // Embers popping off the hot scorch, and a thread of smoke.
        for (DragonFire.Spot s : j.spots) {
            double glow = DragonFire.emberGlow(now, s.lastHit(now));
            double rad = s.radius(now);
            if (glow <= 0.05 || rad < 0.3 || rnd.nextDouble() > 0.5 * glow) continue;
            if (j.budget.allow(tick, 1, 44) == 0) break;
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * rad * 0.7;
            double[] p1 = new double[3], p2 = new double[3];
            DragonFire.basis(new double[]{s.nx, s.ny, s.nz}, p1, p2);
            double x = s.x + (p1[0] * Math.cos(a) + p2[0] * Math.sin(a)) * d + s.nx * 0.1;
            double y = s.y + (p1[1] * Math.cos(a) + p2[1] * Math.sin(a)) * d + s.ny * 0.1;
            double z = s.z + (p1[2] * Math.cos(a) + p2[2] * Math.sin(a)) * d + s.nz * 0.1;
            level.addParticle(rnd.nextInt(3) == 0 ? ParticleTypes.SMOKE : ParticleTypes.SMALL_FLAME, x, y, z, 0, 0.03, 0);
        }
    }

    private static void spawn(ClientLevel level, SimpleParticleType type, DragonFire.Flame f, double spread, double vx,
                              double vy, double vz, net.minecraft.util.RandomSource rnd) {
        level.addParticle(type, f.x() + (rnd.nextDouble() - 0.5) * spread, f.y() + (rnd.nextDouble() - 0.5) * spread,
                f.z() + (rnd.nextDouble() - 0.5) * spread, vx, vy, vz);
    }

    // ---- Vertex helpers -------------------------------------------------------------------------------------------

    /**
     * A camera-facing sprite from {@code dragon_fire_sprites.png}: 128-px cell {@code cell} of row {@code row}, turned
     * by {@code rot}. Additive types take the colour premultiplied by {@code k}; translucent ones take {@code k} as alpha
     * (by default row 1, the smoke, is translucent and the rest additive).
     */
    private static void sprite(VertexConsumer vc, Matrix4f m, double x, double y, double z, double half, double rot,
                               int cell, int row, Vec3 cam, double[] R, double[] U, float[] c, float k) {
        sprite(vc, m, x, y, z, half, rot, cell, row, cam, R, U, c, k, row != 1);
    }

    private static void sprite(VertexConsumer vc, Matrix4f m, double x, double y, double z, double half, double rot,
                               int cell, int row, Vec3 cam, double[] R, double[] U, float[] c, float k, boolean additive) {
        if (k <= 0.002f || half <= 0) return;
        countOverdraw(x, y, z, 4 * half * half, cam);
        double cs = Math.cos(rot) * half, sn = Math.sin(rot) * half;
        double ax = R[0] * cs + U[0] * sn, ay = R[1] * cs + U[1] * sn, az = R[2] * cs + U[2] * sn;
        double bx = -R[0] * sn + U[0] * cs, by = -R[1] * sn + U[1] * cs, bz = -R[2] * sn + U[2] * cs;
        float u0 = cell * 0.25f, u1 = u0 + 0.25f, v0 = row * 0.25f, v1 = v0 + 0.25f;
        double px = x - cam.x, py = y - cam.y, pz = z - cam.z;
        float[] col = additive ? new float[]{c[0] * k, c[1] * k, c[2] * k} : c;
        float a = additive ? 1f : k;
        vertex(vc, m, px - ax - bx, py - ay - by, pz - az - bz, col, a, u0, v1);
        vertex(vc, m, px + ax - bx, py + ay - by, pz + az - bz, col, a, u1, v1);
        vertex(vc, m, px + ax + bx, py + ay + by, pz + az + bz, col, a, u1, v0);
        vertex(vc, m, px - ax + bx, py - ay + by, pz - az + bz, col, a, u0, v0);
    }

    /**
     * One vertex. For the additive types the colour is the light added, so {@code k} scales it (the shader multiplies
     * texture by colour); for the translucent types {@code k} is the alpha.
     */
    private static void vertex(VertexConsumer vc, Matrix4f m, double x, double y, double z, float[] c, float k, float u,
                               float v) {
        vc.addVertex(m, (float) x, (float) y, (float) z).setColor(clamp(c[0]), clamp(c[1]), clamp(c[2]), clamp(k))
                .setUv(u, v).setOverlay(OVERLAY).setLight(LIGHT).setNormal(0f, 1f, 0f);
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    /** The colour of burning gas at a heat 0..1: deep red, orange, gold, white-gold. */
    private static float[] heatColour(double heat) {
        double[][] ramp = {{0.0, 0.16, 0.03, 0.01}, {0.2, 0.45, 0.08, 0.02}, {0.4, 0.86, 0.22, 0.05},
                {0.6, 1.0, 0.45, 0.10}, {0.8, 1.0, 0.72, 0.28}, {1.0, 1.0, 0.94, 0.72}};
        double h = Math.max(0, Math.min(1, heat));
        for (int i = 1; i < ramp.length; i++) {
            if (h <= ramp[i][0]) {
                double f = (h - ramp[i - 1][0]) / (ramp[i][0] - ramp[i - 1][0]);
                return new float[]{(float) (ramp[i - 1][1] + (ramp[i][1] - ramp[i - 1][1]) * f),
                        (float) (ramp[i - 1][2] + (ramp[i][2] - ramp[i - 1][2]) * f),
                        (float) (ramp[i - 1][3] + (ramp[i][3] - ramp[i - 1][3]) * f)};
            }
        }
        return new float[]{1f, 0.94f, 0.72f};
    }

    private static double smooth(double x) {
        double c = Math.max(0, Math.min(1, x));
        return c * c * (3 - 2 * c);
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** The dev scenes' frame-time line: CPU time per frame spent on the fire, every 5 s. */
    private static void debugTimer(long nanos, long decal, Minecraft mc) {
        SceneDef scene = PensieveClientState.scene();
        if (scene == null || !scene.id().startsWith("dev_")) return;
        timedNanos += nanos;
        worstNanos = Math.max(worstNanos, nanos);
        decalNanos += decal;
        worstDecalNanos = Math.max(worstDecalNanos, decal);
        sumOverdraw += overdraw;
        worstOverdraw = Math.max(worstOverdraw, overdraw);
        timedFrames++;
        long wall = System.currentTimeMillis();
        if (lastLog == 0) lastLog = wall;
        if (wall - lastLog < 5000) return;
        int burning = 0, spots = 0, alive = 0;
        float now = PensieveClientState.sceneTime(0);
        for (Jet j : JETS.values()) {
            if (j.burning(now)) burning++;
            spots += j.spots.size();
            alive += j.budget.alive((long) Math.floor(now));
        }
        int fps = mc.getFps();
        LOGGER.info("Pensieve fire: t {} s, {} jets ({} burning), {} flame puffs, {} smoke puffs, {} soot and {} ember "
                        + "cells on {} spots ({} in view left out by the cell cap at most, {} faces dropped as their "
                        + "blocks went), {} particles alive, {} frames on a stale snout, aim off her head by {} degrees "
                        + "at most, CPU {} ms per frame on average, {} ms at worst (the scorch {} ms on average, {} at "
                        + "worst), over {} frames; {} fps ({} ms a frame), sprite overdraw {} screens on average, {} "
                        + "at worst; Fabulous graphics {}",
                String.format(Locale.ROOT, "%.1f", now / 20f), JETS.size(), burning, lastPuffs, lastSmoke, lastDecals,
                lastEmbers, spots, droppedSpots, faceDrops, alive, staleFrames,
                String.format(Locale.ROOT, "%.1f", worstAim),
                String.format(Locale.ROOT, "%.3f", timedNanos / 1e6 / timedFrames),
                String.format(Locale.ROOT, "%.3f", worstNanos / 1e6),
                String.format(Locale.ROOT, "%.3f", decalNanos / 1e6 / timedFrames),
                String.format(Locale.ROOT, "%.3f", worstDecalNanos / 1e6), timedFrames, fps,
                String.format(Locale.ROOT, "%.1f", fps > 0 ? 1000.0 / fps : 0),
                String.format(Locale.ROOT, "%.2f", sumOverdraw / timedFrames),
                String.format(Locale.ROOT, "%.2f", worstOverdraw), Minecraft.useShaderTransparency() ? "on" : "off");
        timedNanos = worstNanos = decalNanos = worstDecalNanos = 0;
        sumOverdraw = worstOverdraw = 0;
        timedFrames = 0;
        staleFrames = 0;
        droppedSpots = 0;
        worstAim = 0;
        lastLog = wall;
    }
}
