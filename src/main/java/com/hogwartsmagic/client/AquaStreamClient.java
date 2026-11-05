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
 * Aqua Eructo held, as the clients see it: a stream of water pouring from the caster's wand along their aim to
 * whatever it meets, every frame, drawn as water (SpellBeamFxRenderer's liquid drawing) with droplets peeling off
 * all along it and a churning spout where it lands: water bursting back, foam boiling, mist and spreading rings.
 * The caster's arm is held out, the roar of the jet follows them, and the spout throws blue light round it. The
 * stream is traced here from the caster's own aim, the same way the server traces it, so it follows smoothly.
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
        if (event.phase != TickEvent.Phase.END || minecraft.level == null || STREAMS.isEmpty()) {
            return;
        }
        double now = minecraft.level.getGameTime();
        STREAMS.values().removeIf(s -> now - s.lastPacket > LAPSE_TICKS || now < s.lastPacket);
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

    /** Where the stream meets something, traced as the server traces it; and whether it hit anything. */
    private static Vec3[] trace(Minecraft minecraft, LivingEntity caster, float partial) {
        Vec3 from = caster.getEyePosition(partial);
        Vec3 dir = caster.getViewVector(partial);
        Vec3 reach = from.add(dir.scale(AquaStream.RANGE));
        BlockHitResult block = minecraft.level.clip(new ClipContext(from, reach, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster));
        Vec3 to = block.getType() == HitResult.Type.MISS ? reach : block.getLocation();
        boolean hit = block.getType() != HitResult.Type.MISS;
        double best = from.distanceToSqr(to);
        for (Entity entity : minecraft.level.getEntities(caster, new AABB(from, to).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
            Optional<Vec3> clip = entity.getBoundingBox().inflate(0.3).clip(from, to);
            if (clip.isPresent() && from.distanceToSqr(clip.get()) < best) {
                best = from.distanceToSqr(clip.get());
                to = clip.get();
                hit = true;
            }
        }
        return new Vec3[]{to, hit ? dir : null};
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || STREAMS.isEmpty()) {
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
            Vec3 from = tip(caster, partial);
            Vec3[] traced = trace(minecraft, caster, partial);
            Vec3 to = traced[0];
            double age = now - stream.start;
            // The water grows out of the wand over its first few ticks, then pours its full length.
            float grow = (float) Math.min(1.0, age / 4.0);
            Vec3 end = from.lerp(to, Math.max(0.05f, grow));
            SpellBeamClientState.Beam beam = SpellBeamClientState.held(stream.seed, jet, from, end, stream.start, now);
            drawn.add(beam);
            SpellBeamFxRenderer.drawHeldLiquid(water, m, beam, now, camera);
            SpellBeamFxRenderer.drawLiquidSpray(water, m, from.subtract(camera), end.subtract(camera), age, stream.seed);
            if (traced[1] != null && grow >= 1.0f) {
                SpellBeamFxRenderer.drawLiquidSpout(water, m, to.subtract(camera), traced[1], age, stream.seed, 1.0f);
            }
        }
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
                sink.add(trace(minecraft, caster, partial)[0], 0.35f, 0.6f, 1.0f, 4.0f, 0.35f);
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
    }

    @SubscribeEvent
    public static void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            STREAMS.clear();
        }
    }
}
