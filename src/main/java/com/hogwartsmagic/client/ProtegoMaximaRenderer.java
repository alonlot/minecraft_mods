package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.support.ProtegoShield;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Protego Maxima, drawn as its own spell rather than a big Protego bubble.
 *
 * <p>A channelling caster holds the wand up to the sky. A white pillar of light, two strands winding round it and
 * pulses running up it, climbs from the wand tip to where it meets the shell, and blooms into a star there. From that
 * point the shell unfurls downwards over the caster like a cloak thrown over them, its hex lattice lit by a bright seam
 * at the falling edge, until it meets the ground and lights a turning rune circle with a ring of light rolling out.
 * While held, light keeps pouring from the bloom down over the shell; a spell striking it flashes white at the point
 * and sends a ring out over the surface; the weaker the bubble (the server reports its health with each hit), the
 * dimmer it burns, and near breaking it gutters. Released, the shell draws back up into the bloom; broken, it bursts
 * into lattice shards thrown outwards.
 *
 * <p>Domes held by NPCs (layer shields of 3 and more) have no pillar: their light pours from the top. Your own dome in
 * first person is dimmer and the pillar starts beyond the wand, so it never fills the screen.
 *
 * <p>Drawn at {@code AFTER_TRANSLUCENT_BLOCKS} from an identity matrix, camera-relative in double precision, depth
 * tested but not written, so the ground hides the lower half of the sphere.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class ProtegoMaximaRenderer {

    /** The barrier the server deflects at (ModEvents' PROTEGO_MAXIMA_BARRIER_RADIUS). */
    public static final float RADIUS = 4.5f;

    private static final RenderType HEX = ModRenderTypes.ceilingGlow(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/shield_hex.png"));
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;

    private static final int LAT = 28;
    private static final int LON = 56;
    /** Hex tiles round the equator and from pole to pole: roughly a block each. */
    private static final float TILES_ROUND = 24f;
    private static final float TILES_UP = 12f;
    private static final int SHARD_LAT = 12;
    private static final int SHARD_LON = 24;
    private static final int SPARKS = 22;

    /** Formation: the pillar climbs, then the shell unfurls from the bloom down to the ground. */
    private static final float BEAM_TICKS = 3f;
    private static final float UNFURL_START = 2f;
    private static final float UNFURL_TICKS = 14f;
    private static final float RING_IGNITE = 12f;
    /** The unfurling edge's softness, in radians from the bloom. */
    private static final float EDGE = 0.22f;
    private static final float FULL = Mth.PI + 0.35f;

    private static final float DROP_TICKS = 11f;
    private static final float SHATTER_TICKS = 16f;
    private static final float FLARE_TICKS = 8f;
    private static final float RIPPLE_TICKS = 24f;
    /** How far a hit's ring runs over the shell, in blocks of arc. */
    private static final float RIPPLE_REACH = 10f;

    /** The server's regeneration (ModEvents): 20 ticks after a hit, 0.7 of 100 a tick. */
    private static final int REGEN_DELAY = 20;
    private static final float REGEN_PER_TICK = 0.007f;

    private static final float[] CORE = {0.36f, 0.64f, 1.00f};
    private static final float[] RIM = {0.84f, 0.95f, 1.00f};
    private static final float[] HEX_BLUE = {0.40f, 0.70f, 1.00f};

    private record Impact(Vec3 at, long tick) {
    }

    private record Health(float value, long tick) {
    }

    /** Hits and health as the server reported them, by holder. Client thread only. */
    private static final Map<Integer, List<Impact>> IMPACTS = new HashMap<>();
    private static final Map<Integer, Health> HEALTH = new HashMap<>();

    private ProtegoMaximaRenderer() {
    }

    /** The packet handler: something struck {@code entityId}'s dome at {@code at}, leaving it at {@code health} (0..1). */
    public static void acceptImpact(int entityId, Vec3 at, float health, long now) {
        List<Impact> list = IMPACTS.computeIfAbsent(entityId, id -> new ArrayList<>());
        list.add(new Impact(at, now));
        if (list.size() > 8) {
            list.remove(0);
        }
        HEALTH.put(entityId, new Health(Mth.clamp(health, 0.0f, 1.0f), now));
    }

    private static float health(int entityId, float now) {
        Health health = HEALTH.get(entityId);
        if (health == null) {
            return 1.0f;
        }
        float regen = Math.max(0.0f, now - health.tick() - REGEN_DELAY) * REGEN_PER_TICK;
        return Math.min(1.0f, health.value() + regen);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        IMPACTS.clear();
        HEALTH.clear();
    }

    // ---- the caster's pose -------------------------------------------------------------------------------------

    /** A channelling caster holds the wand straight up along their gaze, the arm trembling with the strain. */
    public static void applyPose(EntityModel<?> model, Entity entity, float partial) {
        if (!(model instanceof HumanoidModel<?> humanoid) || !(entity instanceof Player player)) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || ProtegoShield.views().isEmpty()) {
            return;
        }
        long tick = level.getGameTime();
        ProtegoShield.View view = ProtegoShield.view(entity.getId(), tick);
        if (view == null || !view.dome() || !view.up(tick) || player.getViewXRot(partial) > -50.0f) {
            return;
        }
        float t = tick + partial;
        float tremble = 0.02f * Mth.sin(t * 3.7f) + 0.012f * Mth.sin(t * 9.1f);
        humanoid.rightArm.xRot = humanoid.head.xRot - Mth.HALF_PI + tremble;
        humanoid.rightArm.yRot = humanoid.head.yRot - 0.06f;
        humanoid.rightArm.zRot = 0.0f;
        humanoid.leftArm.xRot = Mth.lerp(0.6f, humanoid.leftArm.xRot, -0.25f);
        humanoid.leftArm.zRot = Mth.lerp(0.6f, humanoid.leftArm.zRot, 0.45f);
        if (model instanceof PlayerModel<?> playerModel) {
            playerModel.rightSleeve.copyFrom(humanoid.rightArm);
            playerModel.leftSleeve.copyFrom(humanoid.leftArm);
        }
    }

    // ---- per-frame state ---------------------------------------------------------------------------------------

    private static final int UP = 0;
    private static final int DROP = 1;
    private static final int SHATTER = 2;

    /** Everything one dome needs for a frame. Positions ending in {@code Rel} are camera-relative. */
    private static final class Dome {
        int entityId;
        int phase;
        /** Ticks into the phase's own animation: formation age while up, else 0..1 of the drop or burst. */
        float formAge;
        float t;
        float age;
        Vec3 centerWorld;
        Vec3 centerRel;
        Vec3 pole = new Vec3(0.0, 1.0, 0.0);
        /** How far from the bloom (radians) the shell reaches, and how bright its edge burns. */
        float front;
        float seamGain;
        float strength;
        float flare;
        boolean ownFirstPerson;
        @Nullable Vec3 tipRel;
        @Nullable Vec3 endRel;
        Vec3 beamDir;
        float beamLength;
        float beamAlpha;
        double groundY = Double.NaN;
        final List<float[]> ripples = new ArrayList<>();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Map<Integer, ProtegoShield.View> views = ProtegoShield.views();
        if (views.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        // The real partial tick: the event's own is the frame length (Forge 51).
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        long tick = level.getGameTime();
        float now = tick + partial;
        Vec3 camera = event.getCamera().getPosition();

        List<Dome> domes = new ArrayList<>();
        for (Integer id : views.keySet()) {
            ProtegoShield.View view = ProtegoShield.view(id, tick);
            if (view == null || !view.dome()) {
                continue;
            }
            Entity entity = level.getEntity(id);
            if (!(entity instanceof LivingEntity living)
                    || (minecraft.player != null && living.isInvisibleTo(minecraft.player))) {
                continue;
            }
            Dome dome = prepare(level, living, view, tick, now, partial, camera);
            if (dome != null) {
                domes.add(dome);
            }
        }
        IMPACTS.entrySet().removeIf(e -> {
            e.getValue().removeIf(i -> now - i.tick() > RIPPLE_TICKS + 2);
            return e.getValue().isEmpty();
        });
        if (domes.isEmpty()) {
            return;
        }

        Matrix4f matrix = new Matrix4f();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        VertexConsumer hex = buffer.getBuffer(HEX);
        for (Dome dome : domes) {
            if (dome.phase != SHATTER) {
                drawLattice(hex, matrix, dome);
            }
        }
        buffer.endBatch(HEX);

        VertexConsumer light = buffer.getBuffer(ModRenderTypes.PROTEGO_LIGHT);
        for (Dome dome : domes) {
            if (dome.phase == SHATTER) {
                drawShatter(light, matrix, dome);
            } else {
                drawGlow(light, matrix, dome);
                drawBeam(light, matrix, dome);
            }
            drawGroundRing(light, matrix, dome);
        }
        buffer.endBatch(ModRenderTypes.PROTEGO_LIGHT);
    }

    @Nullable
    private static Dome prepare(ClientLevel level, LivingEntity living, ProtegoShield.View view, long tick,
                                float now, float partial, Vec3 camera) {
        Dome d = new Dome();
        d.entityId = living.getId();
        d.age = now - view.raisedAt();
        float sinceEvent = now - view.eventAt();
        byte event = view.event();
        if (view.up(tick)) {
            d.phase = UP;
            // Someone already shielded when they came into view (STATE first, raisedAt backdated by GROW_TICKS)
            // is shown fully formed rather than unfurling.
            boolean seenFormed = event == ProtegoShield.STATE
                    && view.eventAt() - view.raisedAt() == ProtegoShield.GROW_TICKS;
            d.formAge = seenFormed ? 1000.0f : d.age;
        } else if (event == ProtegoShield.SHATTERED) {
            d.phase = SHATTER;
            d.t = sinceEvent / SHATTER_TICKS;
            if (d.t >= 1.0f) {
                return null;
            }
        } else {
            d.phase = DROP;
            d.t = sinceEvent / DROP_TICKS;
            if (d.t >= 1.0f) {
                return null;
            }
        }

        float height = living.getBbHeight();
        d.centerWorld = new Vec3(
                Mth.lerp(partial, living.xo, living.getX()),
                Mth.lerp(partial, living.yo, living.getY()) + height * 0.55,
                Mth.lerp(partial, living.zo, living.getZ()));
        d.centerRel = d.centerWorld.subtract(camera);
        d.ownFirstPerson = AvadaChargePose.isOwnFirstPerson(living);

        float health = health(d.entityId, now);
        d.strength = 0.5f + 0.5f * health;
        if (health < 0.35f) {
            float weak = 1.0f - health / 0.35f;
            float gutter = 0.5f + 0.5f * Mth.sin(now * 2.9f) * Mth.sin(now * 1.7f + 0.6f);
            d.strength *= 1.0f - 0.5f * weak * gutter;
        }
        if (d.ownFirstPerson) {
            d.strength *= 0.7f;
        }

        if (d.phase == UP) {
            float unfurl = Mth.clamp((d.formAge - UNFURL_START) / UNFURL_TICKS, 0.0f, 1.0f);
            d.front = FULL * (1.0f - (1.0f - unfurl) * (1.0f - unfurl) * (1.0f - unfurl)) - (unfurl <= 0.0f ? EDGE : 0.0f);
            d.seamGain = unfurl >= 1.0f ? 0.0f : 1.0f;
            if ((event == ProtegoShield.HIT || event == ProtegoShield.PARRIED) && sinceEvent < FLARE_TICKS) {
                float k = 1.0f - sinceEvent / FLARE_TICKS;
                d.flare = k * k;
            }
        } else if (d.phase == DROP) {
            // Drawn back up into the bloom, the edge burning as it goes.
            d.front = FULL * (1.0f - d.t * d.t * d.t) - EDGE * d.t;
            d.seamGain = 1.0f - 0.3f * d.t;
            d.strength *= 1.0f - 0.25f * d.t;
        }

        // The pillar: from the wand tip along the caster's gaze to where it leaves the sphere.
        if (living instanceof Player && d.phase != SHATTER && living.getViewXRot(partial) <= -50.0f) {
            Vec3 tip = wandTip(living, partial, d.ownFirstPerson);
            Vec3 dir = living.getViewVector(partial).normalize();
            Vec3 oc = tip.subtract(d.centerWorld);
            double b = oc.dot(dir);
            double c = oc.lengthSqr() - RADIUS * RADIUS;
            double disc = b * b - c;
            if (disc > 0.0) {
                double exit = -b + Math.sqrt(disc);
                if (exit > 0.05) {
                    Vec3 end = tip.add(dir.scale(exit));
                    d.pole = end.subtract(d.centerWorld).normalize();
                    d.tipRel = tip.subtract(camera);
                    d.endRel = end.subtract(camera);
                    d.beamDir = dir;
                    d.beamLength = (float) exit;
                    d.beamAlpha = d.phase == UP ? smoothstep(0.0f, BEAM_TICKS, d.formAge) : Math.max(0.0f, 1.0f - d.t * 1.8f);
                }
            }
        }

        BlockHitResult ground = level.clip(new ClipContext(d.centerWorld, d.centerWorld.subtract(0.0, RADIUS, 0.0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, living));
        if (ground.getType() == HitResult.Type.BLOCK) {
            d.groundY = ground.getLocation().y;
        }

        List<Impact> impacts = IMPACTS.get(d.entityId);
        if (impacts != null) {
            for (Impact impact : impacts) {
                float age = now - impact.tick();
                Vec3 dir = impact.at().subtract(d.centerWorld);
                if (age >= 0.0f && age < RIPPLE_TICKS && dir.lengthSqr() > 1.0E-6) {
                    dir = dir.normalize();
                    d.ripples.add(new float[]{(float) dir.x, (float) dir.y, (float) dir.z, age});
                }
            }
        }
        return d;
    }

    /** Where the pillar leaves the wand: as AquaStreamClient's tip, the arm raised along the gaze. */
    private static Vec3 wandTip(LivingEntity caster, float partial, boolean firstPerson) {
        Vec3 look = caster.getViewVector(partial);
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        if (firstPerson) {
            Vec3 up = right.cross(look).normalize();
            return caster.getEyePosition(partial).add(look.scale(0.62)).add(right.scale(0.3)).add(up.scale(-0.2));
        }
        float bodyYaw = Mth.rotLerp(partial, caster.yBodyRotO, caster.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 bodyRight = new Vec3(-Mth.cos(bodyYaw), 0.0, -Mth.sin(bodyYaw));
        double shoulder = (caster.isCrouching() ? 18.8 : 22.0) / 16.0 * 0.9375;
        return caster.getPosition(partial).add(0.0, shoulder, 0.0).add(bodyRight.scale(5.0 / 16.0 * 0.9375))
                .add(look.scale(1.12));
    }

    // ---- the shell ---------------------------------------------------------------------------------------------

    /** The per-vertex light terms shared by the lattice and the glow. */
    private static final class Surface {
        float rim;
        float cover;
        float seam;
        float pours;
        float ripple;
        float foot;
        float shimmer;
    }

    private static void surface(Dome d, float nx, float ny, float nz, double relX, double relY, double relZ,
                                double worldY, float theta, float phi, Surface s) {
        double length = Math.sqrt(relX * relX + relY * relY + relZ * relZ);
        float facing = length < 1.0E-4 ? 1.0f : (float) Math.abs((relX * nx + relY * ny + relZ * nz) / length);
        float g = 1.0f - facing;
        s.rim = g * g * g;

        float ang = (float) Math.acos(Mth.clamp(nx * d.pole.x + ny * d.pole.y + nz * d.pole.z, -1.0, 1.0));
        s.cover = smoothstep(0.0f, EDGE, d.front - ang);
        s.seam = d.seamGain <= 0.0f ? 0.0f
                : d.seamGain * gauss((ang - (d.front - EDGE * 0.55f)) / 0.12f);

        // Light pouring from the bloom down over the shell, two waves a second and a half apart.
        float pours = 0.0f;
        for (int k = 0; k < 2; k++) {
            float ph = frac(d.age * 0.022f + k * 0.5f);
            float band = ph * (Mth.PI + 0.4f) - 0.2f;
            pours += 0.32f * gauss((ang - band) / 0.16f) * (1.0f - 0.7f * ph);
        }
        s.pours = d.tipRel != null ? pours : pours * 0.5f;
        // The bloom's own glow on the shell around it.
        s.pours += (d.tipRel != null ? 0.55f : 0.25f) * gauss(ang / 0.22f);

        float ripple = 0.0f;
        for (float[] r : d.ripples) {
            float arc = (float) Math.acos(Mth.clamp(nx * r[0] + ny * r[1] + nz * r[2], -1.0f, 1.0f)) * RADIUS;
            float age = r[3];
            float f = age / RIPPLE_TICKS;
            float reach = RIPPLE_REACH * (1.0f - (1.0f - f) * (1.0f - f));
            float width = 0.7f + 1.5f * f;
            ripple += gauss((arc - reach) / width) * (1.0f - f) * 1.1f;
            if (age < 6.0f) {
                ripple += (float) Math.exp(-arc * arc / 1.2f) * (1.0f - age / 6.0f) * 2.0f;
            }
        }
        s.ripple = ripple;

        s.foot = 0.0f;
        if (!Double.isNaN(d.groundY)) {
            double dy = worldY - d.groundY;
            if (dy > -0.2) {
                s.foot = gauss((float) (dy / 0.45));
            }
        }
        s.shimmer = 1.0f + 0.12f * Mth.sin(phi * 7.0f + d.age * 0.05f) * Mth.sin(theta * 9.0f - d.age * 0.035f);
    }

    /** The hex lattice over the shell, additive and textured. */
    private static void drawLattice(VertexConsumer consumer, Matrix4f matrix, Dome d) {
        int stride = LON + 1;
        float[] xs = new float[(LAT + 1) * stride];
        float[] ys = new float[xs.length];
        float[] zs = new float[xs.length];
        float[][] colours = new float[xs.length][];
        Surface s = new Surface();
        boolean any = false;
        for (int i = 0; i <= LAT; i++) {
            float theta = Mth.PI * i / LAT;
            float sinT = Mth.sin(theta);
            float cosT = Mth.cos(theta);
            // The mesh pinches at its poles: let the lattice go there (the glow carries the light).
            float poleFade = smoothstep(0.0f, 0.3f, sinT);
            for (int j = 0; j <= LON; j++) {
                float phi = Mth.TWO_PI * j / LON;
                float nx = sinT * Mth.cos(phi);
                float nz = sinT * Mth.sin(phi);
                double x = d.centerRel.x + nx * RADIUS;
                double y = d.centerRel.y + cosT * RADIUS;
                double z = d.centerRel.z + nz * RADIUS;
                int index = i * stride + j;
                xs[index] = (float) x;
                ys[index] = (float) y;
                zs[index] = (float) z;
                surface(d, nx, cosT, nz, x, y, z, d.centerWorld.y + cosT * RADIUS, theta, phi, s);
                if (s.cover <= 0.001f) {
                    continue;
                }
                float intensity = (0.10f + 0.60f * s.rim + 1.3f * s.pours + 1.5f * s.ripple + 1.6f * s.seam + 0.6f * s.foot)
                        * s.shimmer * s.cover * d.strength * poleFade * (1.0f + d.flare);
                float white = Mth.clamp(s.ripple + s.seam + d.flare * 0.5f, 0.0f, 1.0f);
                colours[index] = new float[]{
                        Math.min(1.0f, Mth.lerp(white, HEX_BLUE[0], 1.0f) * intensity),
                        Math.min(1.0f, Mth.lerp(white, HEX_BLUE[1], 1.0f) * intensity),
                        Math.min(1.0f, Mth.lerp(white, HEX_BLUE[2], 1.0f) * intensity)};
                any = true;
            }
        }
        if (!any) {
            return;
        }
        for (int i = 0; i < LAT; i++) {
            for (int j = 0; j < LON; j++) {
                int a = i * stride + j;
                int b = (i + 1) * stride + j;
                if (colours[a] == null && colours[a + 1] == null && colours[b] == null && colours[b + 1] == null) {
                    continue;
                }
                float u0 = TILES_ROUND * j / LON;
                float u1 = TILES_ROUND * (j + 1) / LON;
                float v0 = TILES_UP * i / LAT;
                float v1 = TILES_UP * (i + 1) / LAT;
                hexVertex(consumer, matrix, xs[a], ys[a], zs[a], colours[a], u0, v0);
                hexVertex(consumer, matrix, xs[a + 1], ys[a + 1], zs[a + 1], colours[a + 1], u1, v0);
                hexVertex(consumer, matrix, xs[b + 1], ys[b + 1], zs[b + 1], colours[b + 1], u1, v1);
                hexVertex(consumer, matrix, xs[b], ys[b], zs[b], colours[b], u0, v1);
            }
        }
    }

    private static void hexVertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                                  @Nullable float[] colour, float u, float v) {
        float r = 0.0f;
        float g = 0.0f;
        float b = 0.0f;
        if (colour != null) {
            r = colour[0];
            g = colour[1];
            b = colour[2];
        }
        consumer.addVertex(matrix, x, y, z).setColor(r, g, b, 1.0f).setUv(u, v).setOverlay(OVERLAY).setLight(LIGHT)
                .setNormal(0.0f, 1.0f, 0.0f);
    }

    /** The shell's body of light under the lattice: fresnel rim, pouring light, ripples, the seam and the foot. */
    private static void drawGlow(VertexConsumer consumer, Matrix4f matrix, Dome d) {
        int stride = LON + 1;
        float[] xs = new float[(LAT + 1) * stride];
        float[] ys = new float[xs.length];
        float[] zs = new float[xs.length];
        float[] alphas = new float[xs.length];
        float[] mixes = new float[xs.length];
        Surface s = new Surface();
        for (int i = 0; i <= LAT; i++) {
            float theta = Mth.PI * i / LAT;
            float sinT = Mth.sin(theta);
            float cosT = Mth.cos(theta);
            for (int j = 0; j <= LON; j++) {
                float phi = Mth.TWO_PI * j / LON;
                float nx = sinT * Mth.cos(phi);
                float nz = sinT * Mth.sin(phi);
                double x = d.centerRel.x + nx * RADIUS;
                double y = d.centerRel.y + cosT * RADIUS;
                double z = d.centerRel.z + nz * RADIUS;
                int index = i * stride + j;
                xs[index] = (float) x;
                ys[index] = (float) y;
                zs[index] = (float) z;
                surface(d, nx, cosT, nz, x, y, z, d.centerWorld.y + cosT * RADIUS, theta, phi, s);
                alphas[index] = Mth.clamp((0.035f + 0.85f * s.rim + 0.6f * s.pours + 0.9f * s.ripple + 1.1f * s.seam
                        + 0.45f * s.foot) * s.shimmer * s.cover * d.strength * (1.0f + d.flare * 1.4f), 0.0f, 1.0f);
                mixes[index] = Mth.clamp(s.rim * 0.8f + s.ripple + s.seam + d.flare * 0.5f, 0.0f, 1.0f);
            }
        }
        for (int i = 0; i < LAT; i++) {
            for (int j = 0; j < LON; j++) {
                int a = i * stride + j;
                int b = (i + 1) * stride + j;
                if (alphas[a] <= 0.002f && alphas[a + 1] <= 0.002f && alphas[b] <= 0.002f && alphas[b + 1] <= 0.002f) {
                    continue;
                }
                glowVertex(consumer, matrix, xs, ys, zs, alphas, mixes, a);
                glowVertex(consumer, matrix, xs, ys, zs, alphas, mixes, a + 1);
                glowVertex(consumer, matrix, xs, ys, zs, alphas, mixes, b + 1);
                glowVertex(consumer, matrix, xs, ys, zs, alphas, mixes, b);
            }
        }
    }

    private static void glowVertex(VertexConsumer consumer, Matrix4f matrix, float[] xs, float[] ys, float[] zs,
                                   float[] alphas, float[] mixes, int index) {
        float mix = mixes[index];
        consumer.addVertex(matrix, xs[index], ys[index], zs[index])
                .setColor(Mth.lerp(mix, CORE[0], RIM[0]), Mth.lerp(mix, CORE[1], RIM[1]), Mth.lerp(mix, CORE[2], RIM[2]),
                        alphas[index]);
    }

    // ---- the pillar and the bloom ------------------------------------------------------------------------------

    private static void drawBeam(VertexConsumer consumer, Matrix4f matrix, Dome d) {
        if (d.tipRel == null || d.endRel == null || d.beamAlpha <= 0.004f) {
            return;
        }
        float length = d.beamLength * (d.phase == UP ? d.beamAlpha : 1.0f);
        if (length < 0.05f) {
            return;
        }
        float alpha = d.beamAlpha * (0.88f + 0.12f * Mth.sin(d.age * 1.9f)) * Math.max(0.35f, d.strength);
        if (d.ownFirstPerson) {
            alpha *= 0.55f;
        }
        // In your own view the pillar fades in well beyond the wand, so it never lies across the screen.
        float fadeIn = d.ownFirstPerson ? 1.8f : 0.25f;
        Vec3 dir = d.beamDir;
        Vec3 u = dir.cross(new Vec3(0.0, 1.0, 0.0));
        if (u.lengthSqr() < 1.0E-4) {
            u = dir.cross(new Vec3(1.0, 0.0, 0.0));
        }
        u = u.normalize();
        Vec3 v = dir.cross(u).normalize();

        int segments = Math.max(8, Mth.ceil(length * 4.0f));
        Vec3[] points = new Vec3[segments + 1];
        float[] glowWidths = new float[segments + 1];
        float[] glowAlphas = new float[segments + 1];
        float[] coreWidths = new float[segments + 1];
        float[] coreAlphas = new float[segments + 1];
        Vec3[][] strands = new Vec3[2][segments + 1];
        float[] strandWidths = new float[segments + 1];
        float[] strandAlphas = new float[segments + 1];
        for (int i = 0; i <= segments; i++) {
            float dist = length * i / segments;
            Vec3 p = d.tipRel.add(dir.scale(dist));
            points[i] = p;
            float a = alpha * smoothstep(0.0f, fadeIn, dist);
            float pulse = (float) Math.pow(Math.max(0.0f, Mth.sin(dist * 1.4f - d.age * 0.9f)), 8.0);
            glowWidths[i] = 0.20f * (1.0f + 0.5f * pulse);
            glowAlphas[i] = Mth.clamp(0.30f * a * (1.0f + 1.5f * pulse), 0.0f, 1.0f);
            coreWidths[i] = 0.05f * (1.0f + 0.6f * pulse);
            coreAlphas[i] = Mth.clamp(0.90f * a, 0.0f, 1.0f);
            strandWidths[i] = 0.028f;
            strandAlphas[i] = Mth.clamp(0.55f * a * (0.6f + 0.4f * pulse), 0.0f, 1.0f);
            for (int k = 0; k < 2; k++) {
                float turn = dist * 3.2f - d.age * 0.35f + k * Mth.PI;
                float helix = 0.11f * (1.0f - 0.4f * smoothstep(length - 0.8f, length, dist));
                strands[k][i] = p.add(u.scale(Mth.cos(turn) * helix)).add(v.scale(Mth.sin(turn) * helix));
            }
        }
        ribbon(consumer, matrix, points, glowWidths, glowAlphas, 0.2f);
        ribbon(consumer, matrix, points, coreWidths, coreAlphas, 1.0f);
        ribbon(consumer, matrix, strands[0], strandWidths, strandAlphas, 0.6f);
        ribbon(consumer, matrix, strands[1], strandWidths, strandAlphas, 0.6f);

        // A spark on the wand tip.
        float tipAlpha = alpha * (d.ownFirstPerson ? 0.35f : 0.9f);
        disk(consumer, matrix, d.tipRel, 0.16f, tipAlpha, 0.8f);
        star(consumer, matrix, d.tipRel, 0.32f, tipAlpha, 4, d.age * 0.05f);

        // The bloom where the pillar meets the shell, once the pillar has reached it.
        if (d.phase == UP && d.formAge < BEAM_TICKS) {
            return;
        }
        float bloom = d.beamAlpha * (0.85f + 0.15f * Mth.sin(d.age * 0.6f)) * Math.max(0.4f, d.strength);
        disk(consumer, matrix, d.endRel, 0.95f, 0.55f * bloom, 0.5f);
        disk(consumer, matrix, d.endRel, 0.35f, 0.9f * bloom, 1.0f);
        star(consumer, matrix, d.endRel, 1.5f, 0.8f * bloom, 6, d.age * 0.02f);
        star(consumer, matrix, d.endRel, 0.9f, 0.6f * bloom, 6, -d.age * 0.035f + 0.26f);
        float sinceHit = d.formAge - BEAM_TICKS;
        if (d.phase == UP && sinceHit < 9.0f) {
            float k = 1.0f - sinceHit / 9.0f;
            disk(consumer, matrix, d.endRel, 1.2f + 1.6f * (1.0f - k), 0.9f * k * k, 0.9f);
        }
    }

    // ---- the rune circle on the ground -------------------------------------------------------------------------

    private static void drawGroundRing(VertexConsumer consumer, Matrix4f matrix, Dome d) {
        if (Double.isNaN(d.groundY)) {
            return;
        }
        double dy = d.centerWorld.y - d.groundY;
        if (dy >= RADIUS - 0.3) {
            return;
        }
        float ringRadius = (float) Math.sqrt(RADIUS * RADIUS - dy * dy);
        float on;
        if (d.phase == UP) {
            on = smoothstep(RING_IGNITE, RING_IGNITE + 4.0f, d.formAge);
        } else if (d.phase == DROP) {
            on = 1.0f - smoothstep(0.0f, 0.5f, d.t);
        } else {
            on = Math.max(0.0f, 1.0f - d.t * 3.0f);
        }
        final float lit = on * Math.max(0.4f, d.strength);
        Vec3 c = new Vec3(d.centerRel.x, d.groundY + 0.03 - (d.centerWorld.y - d.centerRel.y), d.centerRel.z);
        if (lit > 0.004f) {
            float age = d.age;
            flatRing(consumer, matrix, c, ringRadius - 0.06f, 0.06f, s -> 0.75f * lit, 0.9f);
            flatRing(consumer, matrix, c, ringRadius - 0.80f, 0.035f, s -> 0.50f * lit, 0.7f);
            flatRing(consumer, matrix, c, ringRadius - 0.43f, 0.20f, s -> {
                float glyph = (float) Math.pow(Math.max(0.0f, Mth.sin(s * 18.0f + age * 0.04f)), 4.0);
                float swell = 0.55f + 0.45f * Mth.sin(s * 5.0f - age * 0.03f);
                return (0.06f + 0.75f * glyph * swell) * lit;
            }, 0.5f);
            flatRing(consumer, matrix, c, ringRadius - 1.15f, 0.12f, s -> {
                float glyph = (float) Math.pow(Math.max(0.0f, Mth.sin(s * 9.0f - age * 0.06f)), 6.0);
                return 0.45f * glyph * lit;
            }, 0.4f);
        }
        // The moment the shell touches down: a ring of light rolls out over the ground.
        if (d.phase == UP) {
            float f = (d.formAge - RING_IGNITE) / 10.0f;
            if (f >= 0.0f && f < 1.0f) {
                float k = (1.0f - f) * (1.0f - f);
                flatRing(consumer, matrix, c, ringRadius + 2.5f * (1.0f - (1.0f - f) * (1.0f - f)),
                        0.25f + 0.35f * f, s -> 0.85f * k, 0.8f);
            }
        }
    }

    // ---- breaking ----------------------------------------------------------------------------------------------

    private static void drawShatter(VertexConsumer consumer, Matrix4f matrix, Dome d) {
        float t = d.t;
        float since = t * SHATTER_TICKS;
        // The flash of the break: the whole shell lit at once, thrown a little outwards.
        if (t < 0.35f) {
            float k = 1.0f - t / 0.35f;
            float radius = RADIUS * (1.0f + 0.12f * (1.0f - k));
            flashShell(consumer, matrix, d, radius, 0.9f * k * k);
        }
        float fade = (float) Math.pow(1.0f - t, 1.5);
        for (int i = 0; i < SHARD_LAT; i++) {
            float t0 = Mth.PI * i / SHARD_LAT;
            float t1 = Mth.PI * (i + 1) / SHARD_LAT;
            for (int j = 0; j < SHARD_LON; j++) {
                float p0 = Mth.TWO_PI * j / SHARD_LON;
                float p1 = Mth.TWO_PI * (j + 1) / SHARD_LON;
                Vec3 a = onSphere(t0, p0);
                Vec3 b = onSphere(t0, p1);
                Vec3 cc = onSphere(t1, p1);
                Vec3 e = onSphere(t1, p0);
                Vec3 normal = a.add(b).add(cc).add(e).normalize();
                Vec3 centroid = normal.scale(RADIUS);
                if (!Double.isNaN(d.groundY) && d.centerWorld.y + centroid.y < d.groundY - 0.2) {
                    continue;
                }
                int seed = i * 131 + j * 7919 + d.entityId * 31;
                float r1 = hash(seed);
                float r2 = hash(seed + 17);
                float r3 = hash(seed + 41);
                float speed = 0.16f + 0.22f * r1;
                Vec3 offset = normal.scale(speed * since * (1.0f - 0.35f * t))
                        .add(0.0, -0.012 * since * since, 0.0);
                float shrink = 0.86f * (1.0f - 0.6f * t);
                Vec3 base = d.centerRel.add(centroid).add(offset);
                Vec3 qa = base.add(a.scale(RADIUS).subtract(centroid).scale(shrink));
                Vec3 qb = base.add(b.scale(RADIUS).subtract(centroid).scale(shrink));
                Vec3 qc = base.add(cc.scale(RADIUS).subtract(centroid).scale(shrink));
                Vec3 qd = base.add(e.scale(RADIUS).subtract(centroid).scale(shrink));
                double length = base.length();
                float facing = length < 1.0E-4 ? 1.0f : (float) Math.abs(base.dot(normal) / length);
                float rim = (1.0f - facing) * (1.0f - facing);
                float alpha = Mth.clamp(fade * (0.30f + 0.5f * r2) * (0.55f + rim), 0.0f, 1.0f);
                float white = 0.3f + 0.6f * r3;
                // Each shard brighter at its middle, its edges cut sharp.
                quad(consumer, matrix, qa, alpha * 0.6f, qb, alpha * 0.6f, qc, alpha, qd, alpha, white);
            }
        }
        // Sparks flung clear of the break.
        for (int k = 0; k < SPARKS; k++) {
            float theta = (float) Math.acos(1.0 - 2.0 * hash(k * 53 + d.entityId));
            float phi = Mth.TWO_PI * hash(k * 97 + 7 + d.entityId);
            Vec3 dir = new Vec3(Mth.sin(theta) * Mth.cos(phi), Mth.cos(theta), Mth.sin(theta) * Mth.sin(phi));
            float reach = RADIUS + (3.0f + 2.0f * hash(k * 13 + 3)) * (1.0f - (1.0f - t) * (1.0f - t));
            Vec3 pos = d.centerRel.add(dir.scale(reach)).add(0.0, -0.01 * since * since, 0.0);
            star(consumer, matrix, pos, 0.28f * (1.0f - 0.5f * t), (1.0f - t), 4, k);
        }
    }

    private static void flashShell(VertexConsumer consumer, Matrix4f matrix, Dome d, float radius, float strength) {
        int lat = 18;
        int lon = 36;
        for (int i = 0; i < lat; i++) {
            float t0 = Mth.PI * i / lat;
            float t1 = Mth.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                float p0 = Mth.TWO_PI * j / lon;
                float p1 = Mth.TWO_PI * (j + 1) / lon;
                Vec3 a = onSphere(t0, p0);
                Vec3 b = onSphere(t0, p1);
                Vec3 c = onSphere(t1, p1);
                Vec3 e = onSphere(t1, p0);
                quad(consumer, matrix,
                        d.centerRel.add(a.scale(radius)), flashAlpha(d, a, radius, strength),
                        d.centerRel.add(b.scale(radius)), flashAlpha(d, b, radius, strength),
                        d.centerRel.add(c.scale(radius)), flashAlpha(d, c, radius, strength),
                        d.centerRel.add(e.scale(radius)), flashAlpha(d, e, radius, strength), 0.85f);
            }
        }
    }

    private static float flashAlpha(Dome d, Vec3 normal, float radius, float strength) {
        Vec3 p = d.centerRel.add(normal.scale(radius));
        double length = p.length();
        float facing = length < 1.0E-4 ? 1.0f : (float) Math.abs(p.dot(normal) / length);
        float g = 1.0f - facing;
        return Mth.clamp((0.25f + 0.8f * g * g * g) * strength, 0.0f, 1.0f);
    }

    private static Vec3 onSphere(float theta, float phi) {
        return new Vec3(Mth.sin(theta) * Mth.cos(phi), Mth.cos(theta), Mth.sin(theta) * Mth.sin(phi));
    }

    // ---- primitives --------------------------------------------------------------------------------------------

    private interface SegmentAlpha {
        float at(float angle);
    }

    /** A flat band of light on the ground round {@code c}, soft at both edges, its brightness varying round it. */
    private static void flatRing(VertexConsumer consumer, Matrix4f matrix, Vec3 c, float radius, float halfWidth,
                                 SegmentAlpha alpha, float white) {
        if (radius <= halfWidth) {
            return;
        }
        int segments = 96;
        for (int k = 0; k < segments; k++) {
            float s0 = Mth.TWO_PI * k / segments;
            float s1 = Mth.TWO_PI * (k + 1) / segments;
            float a0 = Mth.clamp(alpha.at(s0), 0.0f, 1.0f);
            float a1 = Mth.clamp(alpha.at(s1), 0.0f, 1.0f);
            if (a0 <= 0.003f && a1 <= 0.003f) {
                continue;
            }
            Vec3 d0 = new Vec3(Mth.cos(s0), 0.0, Mth.sin(s0));
            Vec3 d1 = new Vec3(Mth.cos(s1), 0.0, Mth.sin(s1));
            Vec3 m0 = c.add(d0.scale(radius));
            Vec3 m1 = c.add(d1.scale(radius));
            quad(consumer, matrix, c.add(d0.scale(radius - halfWidth)), 0.0f, c.add(d1.scale(radius - halfWidth)), 0.0f,
                    m1, a1, m0, a0, white);
            quad(consumer, matrix, m0, a0, m1, a1, c.add(d1.scale(radius + halfWidth)), 0.0f,
                    c.add(d0.scale(radius + halfWidth)), 0.0f, white);
        }
    }

    /** A camera-facing strip through {@code points}, bright along its middle and clear at its edges. */
    private static void ribbon(VertexConsumer consumer, Matrix4f matrix, Vec3[] points, float[] widths, float[] alphas,
                               float white) {
        int n = points.length;
        Vec3[] sides = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 tangent = points[Math.min(n - 1, i + 1)].subtract(points[Math.max(0, i - 1)]);
            Vec3 side = tangent.cross(points[i]);
            if (side.lengthSqr() < 1.0E-8) {
                side = tangent.cross(new Vec3(0.0, 1.0, 0.0));
                if (side.lengthSqr() < 1.0E-8) {
                    side = new Vec3(1.0, 0.0, 0.0);
                }
            }
            sides[i] = side.normalize();
        }
        for (int i = 0; i < n - 1; i++) {
            if (alphas[i] <= 0.003f && alphas[i + 1] <= 0.003f) {
                continue;
            }
            Vec3 p0 = points[i];
            Vec3 p1 = points[i + 1];
            Vec3 s0 = sides[i].scale(widths[i]);
            Vec3 s1 = sides[i + 1].scale(widths[i + 1]);
            quad(consumer, matrix, p0, alphas[i], p1, alphas[i + 1], p1.subtract(s1), 0.0f, p0.subtract(s0), 0.0f, white);
            quad(consumer, matrix, p0, alphas[i], p0.add(s0), 0.0f, p1.add(s1), 0.0f, p1, alphas[i + 1], white);
        }
    }

    /** A soft camera-facing disk of light: bright at its centre, clear at its edge. */
    private static void disk(VertexConsumer consumer, Matrix4f matrix, Vec3 pos, float radius, float alpha, float white) {
        Vec3[] basis = billboard(pos);
        if (basis == null || alpha <= 0.004f) {
            return;
        }
        int segments = 16;
        for (int k = 0; k < segments; k++) {
            float s0 = Mth.TWO_PI * k / segments;
            float s1 = Mth.TWO_PI * (k + 1) / segments;
            Vec3 e0 = pos.add(basis[0].scale(Mth.cos(s0) * radius)).add(basis[1].scale(Mth.sin(s0) * radius));
            Vec3 e1 = pos.add(basis[0].scale(Mth.cos(s1) * radius)).add(basis[1].scale(Mth.sin(s1) * radius));
            quad(consumer, matrix, pos, Math.min(1.0f, alpha), pos, Math.min(1.0f, alpha), e1, 0.0f, e0, 0.0f, white);
        }
    }

    /** A camera-facing star of {@code rays} thin rays, turned by {@code spin}. */
    private static void star(VertexConsumer consumer, Matrix4f matrix, Vec3 pos, float size, float alpha, int rays,
                             float spin) {
        Vec3[] basis = billboard(pos);
        if (basis == null || alpha <= 0.004f) {
            return;
        }
        alpha = Math.min(1.0f, alpha);
        for (int r = 0; r < rays; r++) {
            float angle = spin + Mth.TWO_PI * r / rays;
            Vec3 ray = basis[0].scale(Mth.cos(angle)).add(basis[1].scale(Mth.sin(angle)));
            Vec3 side = basis[0].scale(-Mth.sin(angle)).add(basis[1].scale(Mth.cos(angle)));
            float length = size * (r % 2 == 0 ? 1.0f : 0.6f);
            quad(consumer, matrix, pos, alpha, pos.add(side.scale(size * 0.07f)), 0.0f, pos.add(ray.scale(length)), 0.0f,
                    pos.add(side.scale(-size * 0.07f)), 0.0f, 1.0f);
        }
    }

    /** Two unit axes across the view at {@code pos} (the camera is the origin), or null right at the eye. */
    @Nullable
    private static Vec3[] billboard(Vec3 pos) {
        if (pos.lengthSqr() < 1.0E-6) {
            return null;
        }
        Vec3 normal = pos.scale(-1.0).normalize();
        Vec3 right = normal.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() < 1.0E-4) {
            right = normal.cross(new Vec3(1.0, 0.0, 0.0));
        }
        right = right.normalize();
        Vec3 up = right.cross(normal).normalize();
        return new Vec3[]{right, up};
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             Vec3 p0, float a0, Vec3 p1, float a1, Vec3 p2, float a2, Vec3 p3, float a3, float white) {
        vertex(consumer, matrix, p0, a0, white);
        vertex(consumer, matrix, p1, a1, white);
        vertex(consumer, matrix, p2, a2, white);
        vertex(consumer, matrix, p3, a3, white);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 pos, float alpha, float white) {
        consumer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(Mth.lerp(white, CORE[0], 1.0f), Mth.lerp(white, CORE[1], 1.0f), Mth.lerp(white, CORE[2], 1.0f),
                        Mth.clamp(alpha, 0.0f, 1.0f));
    }

    private static float gauss(float x) {
        return (float) Math.exp(-x * x);
    }

    private static float frac(float x) {
        return x - Mth.floor(x);
    }

    private static float hash(int n) {
        int h = n * 0x27d4eb2d;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
