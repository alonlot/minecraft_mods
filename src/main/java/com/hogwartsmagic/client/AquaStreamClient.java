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
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Aqua Eructo held, as the clients see it: a stream of water pouring from the caster's wand along their aim and
 * falling under its own weight to whatever it meets, every frame, drawn as water (SpellBeamFxRenderer's liquid drawing) with droplets peeling off
 * all along it and a churning spout where it lands: water bursting back, foam boiling, mist and spreading rings.
 * Where it lands on the ground it leaves a spreading wet patch that dries away after. The caster's arm is held out,
 * the roar of the jet follows them, and the spout throws blue light round it. The stream is traced here from the
 * caster's own aim with the server's own trace, so the water you see is the water that pushes.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class AquaStreamClient {
    private static final double LAPSE_TICKS = 15.0;

    private static final class Stream {
        final int casterId;
        final double start;
        final int seed;
        double lastPacket;
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
        if (packet.state() == AquaStream.END) {
            STREAMS.remove(packet.casterId());
            return;
        }
        Stream stream = STREAMS.get(packet.casterId());
        if (stream == null) {
            stream = new Stream(packet.casterId(), now);
            STREAMS.put(packet.casterId(), stream);
            stream.sound = new LoopSound(stream);
            minecraft.getSoundManager().play(stream.sound);
        }
        stream.lastPacket = now;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.level == null || STREAMS.isEmpty() && PUDDLES.isEmpty()) {
            return;
        }
        double now = minecraft.level.getGameTime();
        STREAMS.values().removeIf(s -> now - s.lastPacket > LAPSE_TICKS || now < s.lastPacket);
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

    /** The stream's falling path from the wand, traced exactly as the server traces it (AquaStream.trace). */
    private static AquaStream.Pour trace(Minecraft minecraft, LivingEntity caster, float partial) {
        return AquaStream.trace(minecraft.level, caster, tip(caster, partial), caster.getViewVector(partial));
    }

    /** The first {@code fraction} of a path by length: the water growing out of the wand as it starts. */
    private static java.util.List<Vec3> grown(java.util.List<Vec3> points, float fraction) {
        if (fraction >= 1.0f || points.size() < 2) {
            return points;
        }
        double total = 0.0;
        for (int i = 1; i < points.size(); i++) {
            total += points.get(i).distanceTo(points.get(i - 1));
        }
        double want = Math.max(0.05, total * fraction);
        java.util.List<Vec3> out = new java.util.ArrayList<>();
        out.add(points.get(0));
        double run = 0.0;
        for (int i = 1; i < points.size(); i++) {
            double seg = points.get(i).distanceTo(points.get(i - 1));
            if (run + seg >= want) {
                out.add(points.get(i - 1).lerp(points.get(i), (want - run) / Math.max(1.0E-9, seg)));
                return out;
            }
            run += seg;
            out.add(points.get(i));
        }
        return out;
    }

    /** A wet patch where the stream lands on the ground: it spreads while fed and dries away after. */
    private static final class Puddle {
        final Vec3 centre;
        final double born;
        double lastFed;
        int fed;

        Puddle(Vec3 centre, double now) {
            this.centre = centre;
            this.born = now;
            this.lastFed = now;
        }

        float radius() {
            return Math.min(2.6f, 0.45f + 0.06f * fed);
        }
    }

    private static final java.util.List<Puddle> PUDDLES = new java.util.ArrayList<>();
    private static final double PUDDLE_DRY_TICKS = 100.0;
    private static final int MAX_PUDDLES = 10;
    private static long lastPuddleTick = Long.MIN_VALUE;

    /** Feed the puddle under a landing on the top of a block, or start one. Once a tick per stream. */
    private static void feedPuddle(Vec3 at, double now) {
        for (Puddle puddle : PUDDLES) {
            if (Math.abs(puddle.centre.y - at.y) < 0.3 && puddle.centre.distanceToSqr(at) < puddle.radius() * puddle.radius()) {
                puddle.lastFed = now;
                puddle.fed++;
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

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || STREAMS.isEmpty() && PUDDLES.isEmpty()) {
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
        java.util.List<SpellBeamClientState.Beam> drawn = new java.util.ArrayList<>();
        for (Stream stream : STREAMS.values()) {
            if (!(minecraft.level.getEntity(stream.casterId) instanceof LivingEntity caster) || !caster.isAlive()
                    || minecraft.player != null && caster.isInvisibleTo(minecraft.player)) {
                continue;
            }
            AquaStream.Pour pour = trace(minecraft, caster, partial);
            double age = now - stream.start;
            // The water grows out of the wand along its arc over its first few ticks, then pours its full length.
            float grow = (float) Math.min(1.0, age / 4.0);
            java.util.List<Vec3> path = grown(pour.points(), grow);
            SpellBeamClientState.Beam beam = SpellBeamClientState.heldPath(stream.seed, jet, path, stream.start, now);
            drawn.add(beam);
            SpellBeamFxRenderer.drawHeldLiquid(water, m, beam, now, camera);
            java.util.List<Vec3> relative = new java.util.ArrayList<>();
            for (Vec3 point : path) {
                relative.add(point.subtract(camera));
            }
            SpellBeamFxRenderer.drawLiquidSpray(water, m, relative, age, stream.seed);
            if (pour.hitSomething() && grow >= 1.0f) {
                java.util.List<Vec3> pts = pour.points();
                Vec3 dir = pts.get(pts.size() - 1).subtract(pts.get(Math.max(0, pts.size() - 2)));
                dir = dir.lengthSqr() < 1.0E-8 ? caster.getViewVector(partial) : dir.normalize();
                SpellBeamFxRenderer.drawLiquidSpout(water, m, pour.end().subtract(camera), dir, age, stream.seed, 1.0f);
                long tick = minecraft.level.getGameTime();
                if (pour.face() == net.minecraft.core.Direction.UP && tick != lastPuddleTick) {
                    feedPuddle(pour.end(), tick);
                }
            }
        }
        if (minecraft.level.getGameTime() != lastPuddleTick) {
            lastPuddleTick = minecraft.level.getGameTime();
        }
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

    /** A blue gleam where the water lands (SpellLight). */
    static void lights(float partial, double now, SpellLight.Sink sink) {
        Minecraft minecraft = Minecraft.getInstance();
        if (STREAMS.isEmpty() || minecraft.level == null) {
            return;
        }
        for (Stream stream : STREAMS.values()) {
            if (minecraft.level.getEntity(stream.casterId) instanceof LivingEntity caster) {
                sink.add(trace(minecraft, caster, partial).end(), 0.35f, 0.6f, 1.0f, 4.0f, 0.35f);
            }
        }
    }

    /** The caster holds the wand out into the stream, braced against it. */
    public static void applyPose(EntityModel<?> model, Entity entity, float partial) {
        if (STREAMS.isEmpty() || !(model instanceof HumanoidModel<?> humanoid) || !(entity instanceof Player)
                || !STREAMS.containsKey(entity.getId())) {
            return;
        }
        float t = (float) ((AvadaChargeClientState.now(partial) - STREAMS.get(entity.getId()).start) % 20000.0);
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
            if (minecraft.level == null || STREAMS.get(stream.casterId) != stream
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
    }

    @SubscribeEvent
    public static void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            STREAMS.clear();
            PUDDLES.clear();
        }
    }
}
