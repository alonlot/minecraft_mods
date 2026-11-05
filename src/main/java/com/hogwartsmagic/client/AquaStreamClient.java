package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.AquaStream;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aqua Eructo held, as the clients see it. The water is real water: every tick the caster's wand throws out a
 * fresh {@link AquaStream.Drop} along their aim, run here exactly as the server runs it, and once out of the wand
 * it flies on its own: straight while it has pressure, then slowing and falling under its own weight. So a swung
 * wand bends the stream behind it like a hose, and when the caster lets go or turns away, the water already in the
 * air still flies on and lands where it was going. The drops are joined into smooth runs of water (drawn by
 * SpellBeamFxRenderer's liquid drawing: a glassy rope out of the wand, breaking into chunks and foam as it goes),
 * with droplets peeling off along them. Wherever water lands it splashes: droplets bursting back, foam boiling,
 * mist and spreading rings; on the ground it leaves a wet patch that spreads while fed and dries away after. The
 * caster's arm is held out, the roar of the jet follows them, and where it lands throws blue light.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class AquaStreamClient {
    private static final double LAPSE_TICKS = 15.0;
    /** Neighbouring drops further apart than this (a fast swing) are separate runs of water. */
    private static final double JOIN = 7.0;

    private static final class Stream {
        final int casterId;
        final int seed;
        double start;
        double lastPacket;
        /** Let go: no more water comes out, but what is in the air still lands. */
        boolean ended;
        long nextDrop;
        final List<AquaStream.Drop> drops = new ArrayList<>();
        LoopSound sound;

        Stream(int casterId, double start) {
            this.casterId = casterId;
            this.start = start;
            this.lastPacket = start;
            this.seed = casterId * 0x27D4EB2F;
        }
    }

    private static final Map<Integer, Stream> STREAMS = new HashMap<>();

    private AquaStreamClient() {
    }

    public static void accept(SpellPackets.AquaStreamPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        double now = minecraft.level.getGameTime();
        Stream stream = STREAMS.get(packet.casterId());
        if (packet.state() == AquaStream.END) {
            if (stream != null) {
                stream.ended = true;
            }
            return;
        }
        if (stream == null) {
            stream = new Stream(packet.casterId(), now);
            STREAMS.put(packet.casterId(), stream);
        } else if (stream.ended) {
            // Cast again while the last stream's water is still landing.
            stream.ended = false;
            stream.start = now;
            stream.nextDrop += 1000;
        } else {
            stream.lastPacket = now;
            return;
        }
        stream.lastPacket = now;
        stream.sound = new LoopSound(stream);
        minecraft.getSoundManager().play(stream.sound);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.level == null || minecraft.isPaused()
                || STREAMS.isEmpty() && PUDDLES.isEmpty() && SPLASHES.isEmpty()) {
            return;
        }
        long tick = minecraft.level.getGameTime();
        double now = tick;
        java.util.Iterator<Stream> it = STREAMS.values().iterator();
        while (it.hasNext()) {
            Stream stream = it.next();
            if (!stream.ended && (now - stream.lastPacket > LAPSE_TICKS || now < stream.lastPacket)) {
                stream.ended = true;
            }
            if (!(minecraft.level.getEntity(stream.casterId) instanceof LivingEntity caster)) {
                it.remove(); // out of sight: its water goes with it
                continue;
            }
            if (!stream.ended && caster.isAlive()) {
                stream.drops.add(new AquaStream.Drop(stream.nextDrop++, tip(caster, 1.0f), caster.getViewVector(1.0f)));
            }
            for (AquaStream.Drop drop : stream.drops) {
                AquaStream.Drop.Hit hit = drop.step(minecraft.level, caster);
                if (hit != null) {
                    splash(hit, tick, stream.seed);
                }
            }
            stream.drops.removeIf(d -> d.gone);
            if (stream.ended && stream.drops.isEmpty()) {
                it.remove();
            }
        }
        SPLASHES.removeIf(s -> now - s.lastHit > 8.0 || now < s.lastHit);
        PUDDLES.removeIf(p -> now - p.lastFed > PUDDLE_DRY_TICKS + 12.0 || now < p.lastFed);
    }

    /** The wand tip the water pours from: held straight out along the caster's aim. */
    private static Vec3 tip(LivingEntity caster, float partial) {
        Vec3 look = caster.getViewVector(partial);
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 eye = caster.getEyePosition(partial);
        if (AvadaChargePose.isOwnFirstPerson(caster)) {
            Vec3 up = right.cross(look).normalize();
            return eye.add(look.scale(0.62)).add(right.scale(0.3)).add(up.scale(-0.2));
        }
        if (caster instanceof Player) {
            float bodyYaw = Mth.rotLerp(partial, caster.yBodyRotO, caster.yBodyRot) * Mth.DEG_TO_RAD;
            Vec3 bodyRight = new Vec3(-Mth.cos(bodyYaw), 0.0, -Mth.sin(bodyYaw));
            double shoulder = (caster.isCrouching() ? 18.8 : 22.0) / 16.0 * 0.9375;
            return caster.getPosition(partial).add(0.0, shoulder, 0.0).add(bodyRight.scale(5.0 / 16.0 * 0.9375))
                    .add(look.scale(1.12));
        }
        return eye.add(look.scale(0.85)).add(right.scale(0.28)).add(0.0, -0.3, 0.0);
    }

    // ---- splashes: wherever water lands ----

    /** Water landing on one spot: kept going while drops keep landing there, so a held stream boils at its foot. */
    private static final class Splash {
        Vec3 at;
        Vec3 dir;
        final double born;
        double lastHit;
        final int seed;

        Splash(Vec3 at, Vec3 dir, double now, int seed) {
            this.at = at;
            this.dir = dir;
            this.born = now;
            this.lastHit = now;
            this.seed = seed;
        }
    }

    private static final List<Splash> SPLASHES = new ArrayList<>();
    private static final int MAX_SPLASHES = 24;

    private static void splash(AquaStream.Drop.Hit hit, long tick, int seed) {
        Splash near = null;
        for (Splash s : SPLASHES) {
            if (tick - s.lastHit <= 3.0 && s.at.distanceToSqr(hit.at()) < 2.25) {
                near = s;
                break;
            }
        }
        if (near != null) {
            near.at = near.at.lerp(hit.at(), 0.5);
            near.dir = near.dir.lerp(hit.dir(), 0.5).normalize();
            near.lastHit = tick;
        } else {
            if (SPLASHES.size() >= MAX_SPLASHES) {
                SPLASHES.remove(0);
            }
            SPLASHES.add(new Splash(hit.at(), hit.dir(), tick, seed + (int) (tick * 131)));
        }
        if (hit.face() == Direction.UP) {
            feedPuddle(hit.at(), tick);
        }
    }

    private static void drawSplashes(VertexConsumer out, Matrix4f m, double now, Vec3 camera) {
        for (Splash s : SPLASHES) {
            float fade = 1.0f - SpellFxGeometry.smoothstep(1.5f, 7.0f, (float) (now - s.lastHit));
            if (fade <= 0.02f) {
                continue;
            }
            SpellBeamFxRenderer.drawLiquidSpout(out, m, s.at.subtract(camera), s.dir, now - s.born, s.seed,
                    0.45f + 0.55f * fade);
        }
    }

    // ---- puddles ----

    /** A wet patch where the water lands on the ground: it spreads while fed and dries away after. */
    private static final class Puddle {
        final Vec3 centre;
        double lastFed;
        int fed;

        Puddle(Vec3 centre, double now) {
            this.centre = centre;
            this.lastFed = now;
        }

        float radius() {
            return Math.min(2.6f, 0.45f + 0.06f * fed);
        }
    }

    private static final List<Puddle> PUDDLES = new ArrayList<>();
    private static final double PUDDLE_DRY_TICKS = 100.0;
    private static final int MAX_PUDDLES = 10;

    private static void feedPuddle(Vec3 at, double now) {
        for (Puddle puddle : PUDDLES) {
            if (Math.abs(puddle.centre.y - at.y) < 0.3 && puddle.centre.distanceToSqr(at) < puddle.radius() * puddle.radius()) {
                if (puddle.lastFed != now) {
                    puddle.fed++;
                }
                puddle.lastFed = now;
                return;
            }
        }
        if (PUDDLES.size() >= MAX_PUDDLES) {
            PUDDLES.remove(0);
        }
        PUDDLES.add(new Puddle(new Vec3(at.x, at.y + 0.015, at.z), now));
    }

    /** The wet patches: darkened ground with a blue sheen, a glossy edge and rings running out while fed. */
    private static void drawPuddles(VertexConsumer out, Matrix4f m, double now, Vec3 camera) {
        Vec3 right = new Vec3(1.0, 0.0, 0.0);
        Vec3 up = new Vec3(0.0, 0.0, 1.0);
        for (Puddle puddle : PUDDLES) {
            float dry = (float) Math.max(0.0, now - puddle.lastFed - 10.0);
            float fade = 1.0f - SpellFxGeometry.smoothstep(0.0f, (float) PUDDLE_DRY_TICKS, dry);
            if (fade <= 0.01f) {
                continue;
            }
            Vec3 c = puddle.centre.subtract(camera);
            float r = puddle.radius() * (0.85f + 0.15f * fade);
            SpellFxGeometry.disc(out, m, c, right, up, r, 0.5f * fade, 0.04f, 0.09f, 0.16f, false, 4, 20);
            SpellFxGeometry.disc(out, m, c.add(0.0, 0.003, 0.0), right, up, r * 0.75f, 0.28f * fade, 0.25f, 0.5f, 0.78f,
                    false, 4, 20);
            SpellFxGeometry.ring(out, m, c.add(0.0, 0.005, 0.0), right, up, r * 0.9f, 0.08f, 0.22f * fade,
                    0.92f, 0.96f, 1.0f, 28);
            if (now - puddle.lastFed < 4.0) {
                for (int k = 0; k < 3; k++) {
                    float rt = (float) (((now + k * 3.0) % 9.0) / 9.0);
                    SpellFxGeometry.ring(out, m, c.add(0.0, 0.006, 0.0), right, up, r * (0.2f + 0.75f * rt), 0.04f,
                            0.5f * (1.0f - rt), 0.92f, 0.96f, 1.0f, 28);
                }
            }
        }
    }

    // ---- the water in the air ----

    /** A run of water: points from the wand end outward, and how far its first point has flown from the wand. */
    private record Run(List<Vec3> points, double offset) {
    }

    /**
     * The drops joined into runs, newest (at the wand) to oldest (furthest out). A run breaks where a drop has
     * already landed, where neighbouring drops were thrown far apart by a fast swing, or where it lands.
     */
    private static List<Run> runs(Stream stream, LivingEntity caster, float partial) {
        List<Run> runs = new ArrayList<>();
        List<Vec3> current = null;
        double offset = 0.0;
        long lastId = Long.MIN_VALUE;
        Vec3 lastPoint = null;
        Vec3 lastVel = Vec3.ZERO;
        for (int i = stream.drops.size() - 1; i >= 0; i--) {
            AquaStream.Drop drop = stream.drops.get(i);
            // Landed water is drawn right where it landed, so the stream always reaches what it is pouring on.
            Vec3 p = drop.landed ? drop.pos : drop.prev.lerp(drop.pos, partial);
            boolean joins = current != null && drop.id == lastId - 1 && p.distanceTo(lastPoint) < JOIN;
            if (!joins) {
                if (current != null) {
                    runs.add(close(current, offset, lastVel));
                }
                current = new ArrayList<>();
                boolean fromWand = !stream.ended && drop.id == stream.nextDrop - 1;
                if (fromWand) {
                    current.add(tip(caster, partial));
                    offset = 0.0;
                } else {
                    offset = drop.travelled;
                }
            }
            current.add(p);
            lastId = drop.id;
            lastPoint = p;
            lastVel = drop.vel;
            if (drop.landed) {
                runs.add(close(current, offset, lastVel));
                current = null;
            }
        }
        if (current != null) {
            runs.add(close(current, offset, lastVel));
        }
        return runs;
    }

    /** Smooths a run through its drops (Catmull-Rom); a lone drop becomes a short streak along its flight. */
    private static Run close(List<Vec3> points, double offset, Vec3 vel) {
        if (points.size() == 1) {
            Vec3 p = points.get(0);
            List<Vec3> streak = new ArrayList<>();
            Vec3 back = vel.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 1.0, 0.0) : vel.normalize();
            streak.add(p.subtract(back.scale(1.5)));
            streak.add(p);
            return new Run(streak, offset);
        }
        List<Vec3> out = new ArrayList<>();
        int n = points.size();
        final int sub = 3;
        for (int i = 0; i < n - 1; i++) {
            Vec3 p0 = points.get(Math.max(0, i - 1));
            Vec3 p1 = points.get(i);
            Vec3 p2 = points.get(i + 1);
            Vec3 p3 = points.get(Math.min(n - 1, i + 2));
            for (int s = 0; s < sub; s++) {
                double t = s / (double) sub;
                out.add(catmullRom(p0, p1, p2, p3, t));
            }
        }
        out.add(points.get(n - 1));
        return new Run(out, offset);
    }

    private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return p1.scale(2.0)
                .add(p2.subtract(p0).scale(t))
                .add(p0.scale(2.0).subtract(p1.scale(5.0)).add(p2.scale(4.0)).subtract(p3).scale(t2))
                .add(p1.scale(3.0).subtract(p0).subtract(p2.scale(3.0)).add(p3).scale(t3))
                .scale(0.5);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || STREAMS.isEmpty() && PUDDLES.isEmpty() && SPLASHES.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        BeamStyle style = BeamStyles.of(SpellType.AQUA_ERUCTO);
        if (minecraft.level == null || style == null) {
            return;
        }
        BeamStyle jet = style.asJet();
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        double now = minecraft.level.getGameTime() + partial;
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer water = buffer.getBuffer(ModRenderTypes.AVADA_SHADOW);
        List<SpellBeamClientState.Beam> drawn = new ArrayList<>();
        for (Stream stream : STREAMS.values()) {
            if (!(minecraft.level.getEntity(stream.casterId) instanceof LivingEntity caster)
                    || minecraft.player != null && caster.isInvisibleTo(minecraft.player)) {
                continue;
            }
            double age = now - stream.start;
            for (Run run : runs(stream, caster, partial)) {
                if (run.points().size() < 2) {
                    continue;
                }
                SpellBeamClientState.Beam beam = SpellBeamClientState.heldPath(stream.seed, jet, run.points(),
                        stream.start, now, run.offset());
                drawn.add(beam);
                SpellBeamFxRenderer.drawHeldLiquid(water, m, beam, now, camera);
                List<Vec3> relative = new ArrayList<>(run.points().size());
                for (Vec3 point : run.points()) {
                    relative.add(point.subtract(camera));
                }
                SpellBeamFxRenderer.drawLiquidSpray(water, m, relative, age, stream.seed);
            }
        }
        drawSplashes(water, m, now, camera);
        drawPuddles(water, m, now, camera);
        buffer.endBatch(ModRenderTypes.AVADA_SHADOW);
        if (drawn.isEmpty()) {
            return;
        }
        VertexConsumer highlight = buffer.getBuffer(ModRenderTypes.AVADA_LIGHT);
        for (SpellBeamClientState.Beam beam : drawn) {
            SpellBeamFxRenderer.drawHeldLiquidHighlight(highlight, m, beam, now, camera);
        }
        buffer.endBatch(ModRenderTypes.AVADA_LIGHT);
    }

    /** A blue gleam where the water is landing (SpellLight). */
    static void lights(float partial, double now, SpellLight.Sink sink) {
        if (SPLASHES.isEmpty()) {
            return;
        }
        int lit = 0;
        for (int i = SPLASHES.size() - 1; i >= 0 && lit < 3; i--) {
            Splash s = SPLASHES.get(i);
            if (now - s.lastHit < 3.0) {
                sink.add(s.at, 0.35f, 0.6f, 1.0f, 4.0f, 0.35f);
                lit++;
            }
        }
    }

    /** The caster holds the wand out into the stream, braced against it. */
    public static void applyPose(EntityModel<?> model, Entity entity, float partial) {
        if (STREAMS.isEmpty() || !(model instanceof HumanoidModel<?> humanoid) || !(entity instanceof Player)) {
            return;
        }
        Stream stream = STREAMS.get(entity.getId());
        if (stream == null || stream.ended) {
            return;
        }
        float t = (float) ((AvadaChargeClientState.now(partial) - stream.start) % 20000.0);
        float shudder = 0.025f * Mth.sin(t * 4.1f);
        humanoid.rightArm.xRot = humanoid.head.xRot - Mth.HALF_PI + shudder;
        humanoid.rightArm.yRot = humanoid.head.yRot - 0.06f;
        humanoid.rightArm.zRot = 0.0f;
        humanoid.leftArm.xRot = Mth.lerp(0.6f, humanoid.leftArm.xRot, 0.3f);
        humanoid.leftArm.zRot = Mth.lerp(0.6f, humanoid.leftArm.zRot, 0.25f);
        if (model instanceof PlayerModel<?> player) {
            player.rightSleeve.copyFrom(humanoid.rightArm);
            player.leftSleeve.copyFrom(humanoid.leftArm);
        }
    }

    /** The roar of the jet, at the caster, for as long as it pours. */
    private static final class LoopSound extends AbstractTickableSoundInstance {
        private final Stream stream;

        LoopSound(Stream stream) {
            super(ModSounds.AQUA_STREAM.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.stream = stream;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.9f;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || STREAMS.get(stream.casterId) != stream || stream.ended || stream.sound != this
                    || !(minecraft.level.getEntity(stream.casterId) instanceof LivingEntity caster)) {
                stop();
                return;
            }
            this.x = caster.getX();
            this.y = caster.getEyeY();
            this.z = caster.getZ();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        STREAMS.clear();
        PUDDLES.clear();
        SPLASHES.clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            STREAMS.clear();
            PUDDLES.clear();
            SPLASHES.clear();
        }
    }
}
