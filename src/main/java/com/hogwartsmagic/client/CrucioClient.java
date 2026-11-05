package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.mind.CrucioChannel;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
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

import static com.hogwartsmagic.client.SpellFxGeometry.*;

/**
 * The Cruciatus Curse held: a writhing blood-red beam from the caster's wand into the victim, in Crucio's own
 * style, for as long as it lasts. Each surge of pain (a damage tick) is a hot bead racing down the beam that bursts
 * on the body; the victim writhes, arms flailing and head thrown back; the caster's arm is held out on them; the
 * victim is lit crimson. Drawn from CrucioChannelPackets; a channel this client stops hearing of lapses.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CrucioClient {
    private static final double LAPSE_TICKS = 20.0;
    private static final float PULSE_TRAVEL = 4.0f;
    private static final float PULSE_BURST = 8.0f;

    private static final class Channel {
        final int casterId;
        final int targetId;
        final double start;
        final int seed;
        double lastPacket;
        final List<Double> pulses = new ArrayList<>();

        Channel(int casterId, int targetId, double start) {
            this.casterId = casterId;
            this.targetId = targetId;
            this.start = start;
            this.lastPacket = start;
            this.seed = casterId * 0x1B873593 + targetId;
        }
    }

    private static final Map<Integer, Channel> CHANNELS = new HashMap<>();

    private CrucioClient() {
    }

    public static void accept(SpellPackets.CrucioChannelPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        double now = minecraft.level.getGameTime();
        Channel channel = CHANNELS.get(packet.casterId());
        if (packet.state() == CrucioChannel.END) {
            CHANNELS.remove(packet.casterId());
            return;
        }
        if (channel == null || channel.targetId != packet.targetId()) {
            channel = new Channel(packet.casterId(), packet.targetId(), now);
            CHANNELS.put(packet.casterId(), channel);
        }
        channel.lastPacket = now;
        if (packet.state() == CrucioChannel.PULSE
                && (channel.pulses.isEmpty() || channel.pulses.get(channel.pulses.size() - 1) != now)) {
            channel.pulses.add(now); // the same surge can arrive once per side of the channel
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || minecraft.level == null || CHANNELS.isEmpty()) {
            return;
        }
        double now = minecraft.level.getGameTime();
        CHANNELS.values().removeIf(c -> now - c.lastPacket > LAPSE_TICKS || now < c.lastPacket);
        for (Channel c : CHANNELS.values()) {
            c.pulses.removeIf(p -> now - p > PULSE_TRAVEL + PULSE_BURST);
        }
    }

    /** Where the caster's wand tip is: held straight out along their gaze. */
    private static Vec3 tip(LivingEntity caster, float partial) {
        Vec3 look = caster.getViewVector(partial);
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 eye = caster.getEyePosition(partial);
        if (AvadaChargePose.isOwnFirstPerson(caster)) {
            Vec3 up = right.cross(look).normalize();
            return eye.add(look.scale(0.62)).add(right.scale(0.3)).add(up.scale(-0.2));
        }
        return eye.add(look.scale(0.85)).add(right.scale(0.28)).add(0.0, -0.3, 0.0);
    }

    private static Vec3 body(LivingEntity target, float partial) {
        return target.getPosition(partial).add(0.0, target.getBbHeight() * 0.6, 0.0);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || CHANNELS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        BeamStyle style = BeamStyles.of(SpellType.CRUCIO);
        if (style == null) {
            return;
        }
        BeamStyle jet = style.asJet();
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        double now = minecraft.level.getGameTime() + partial;
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        List<SpellBeamClientState.Beam> beams = new ArrayList<>();
        List<Channel> drawn = new ArrayList<>();
        List<Vec3[]> ends = new ArrayList<>();
        for (Channel c : CHANNELS.values()) {
            Entity caster = minecraft.level.getEntity(c.casterId);
            Entity target = minecraft.level.getEntity(c.targetId);
            if (!(caster instanceof LivingEntity lc) || !(target instanceof LivingEntity lt)
                    || minecraft.player != null && lc.isInvisibleTo(minecraft.player)) {
                continue;
            }
            Vec3 from = tip(lc, partial);
            Vec3 to = body(lt, partial);
            beams.add(SpellBeamClientState.held(c.seed, jet, from, to, c.start, now));
            drawn.add(c);
            ends.add(new Vec3[]{from, to});
        }
        if (beams.isEmpty()) {
            return;
        }
        VertexConsumer dark = buffer.getBuffer(ModRenderTypes.AVADA_SHADOW);
        for (SpellBeamClientState.Beam beam : beams) {
            SpellBeamFxRenderer.drawHeldDark(dark, m, beam, now, camera);
        }
        buffer.endBatch(ModRenderTypes.AVADA_SHADOW);

        VertexConsumer light = buffer.getBuffer(ModRenderTypes.AVADA_LIGHT);
        for (int i = 0; i < beams.size(); i++) {
            SpellBeamFxRenderer.drawHeldLight(light, m, beams.get(i), now, camera);
            Channel c = drawn.get(i);
            Vec3 from = ends.get(i)[0].subtract(camera);
            Vec3 to = ends.get(i)[1].subtract(camera);
            // The victim burns red where the curse holds them.
            float throb = 0.75f + 0.25f * Mth.sin((float) (now - c.start) * 1.9f);
            billboard(light, m, to, 0.9f * throb, 0.45f, style.glowR, style.glowG, style.glowB, false);
            for (double pulse : c.pulses) {
                float t = (float) (now - pulse);
                if (t < 0.0f) {
                    continue;
                }
                if (t < PULSE_TRAVEL) {
                    // A surge of pain racing down the beam.
                    Vec3 bead = from.lerp(to, AvadaChargePose.smoothstep(0.0f, PULSE_TRAVEL, t));
                    billboard(light, m, bead, 0.45f, 0.6f, style.glowR, style.glowG, style.glowB, false, 3, 12);
                    billboard(light, m, bead, 0.16f, 1.0f, style.coreR, style.coreG, style.coreB, true, 3, 12);
                } else {
                    // ...bursting on the body.
                    float f = (t - PULSE_TRAVEL) / PULSE_BURST;
                    billboard(light, m, to, 0.4f + 1.4f * f, (1.0f - f) * (1.0f - f), style.coreR, style.coreG,
                            style.coreB, true);
                    Vec3[] axes = frame(to.subtract(from).normalize());
                    ring(light, m, to, axes[0], axes[1], 0.3f + 1.2f * f, 0.08f, 0.7f * (1.0f - f),
                            style.coreR, style.coreG, style.coreB, 28);
                }
            }
        }
        buffer.endBatch(ModRenderTypes.AVADA_LIGHT);
    }

    /** The victim lit crimson while it lasts (SpellLight). */
    static void lights(float partial, double now, SpellLight.Sink sink) {
        Minecraft minecraft = Minecraft.getInstance();
        if (CHANNELS.isEmpty() || minecraft.level == null) {
            return;
        }
        for (Channel c : CHANNELS.values()) {
            if (minecraft.level.getEntity(c.targetId) instanceof LivingEntity target) {
                sink.add(body(target, partial), 1.0f, 0.12f, 0.1f, 4.5f, 0.6f);
            }
        }
    }

    /** The victim writhes, arms flailing and head thrown back; the caster holds the wand out on them. */
    public static void applyPose(EntityModel<?> model, Entity entity, float partial) {
        if (CHANNELS.isEmpty() || !(model instanceof HumanoidModel<?> humanoid)) {
            return;
        }
        int id = entity.getId();
        double now = AvadaChargeClientState.now(partial);
        for (Channel c : CHANNELS.values()) {
            float t = (float) (now - c.start);
            if (c.targetId == id) {
                float pain = 1.0f;
                for (double pulse : c.pulses) {
                    float since = (float) (now - pulse);
                    if (since >= 0.0f && since < 6.0f) {
                        pain = Math.max(pain, 1.0f + 1.2f * (1.0f - since / 6.0f));
                    }
                }
                float w = Mth.sin(t * 1.7f);
                float w2 = Mth.sin(t * 2.6f + 1.0f);
                humanoid.rightArm.xRot = -0.4f + 0.5f * w * pain;
                humanoid.rightArm.zRot = 0.6f + 0.35f * w2 * pain;
                humanoid.leftArm.xRot = -0.4f - 0.5f * w2 * pain;
                humanoid.leftArm.zRot = -0.6f - 0.35f * w * pain;
                humanoid.head.xRot = -0.45f - 0.15f * pain + 0.08f * w2;
                humanoid.head.yRot += 0.25f * Mth.sin(t * 1.1f) * pain;
                humanoid.hat.copyFrom(humanoid.head);
                humanoid.rightLeg.xRot += 0.15f * w;
                humanoid.leftLeg.xRot -= 0.15f * w;
                copySleeves(model, humanoid);
                return;
            }
            if (c.casterId == id && entity instanceof Player) {
                float shake = 0.03f * Mth.sin(t * 3.3f);
                humanoid.rightArm.xRot = humanoid.head.xRot - Mth.HALF_PI + shake;
                humanoid.rightArm.yRot = humanoid.head.yRot - 0.06f;
                humanoid.rightArm.zRot = 0.0f;
                copySleeves(model, humanoid);
                return;
            }
        }
    }

    private static void copySleeves(EntityModel<?> model, HumanoidModel<?> humanoid) {
        if (model instanceof PlayerModel<?> player) {
            player.rightSleeve.copyFrom(humanoid.rightArm);
            player.leftSleeve.copyFrom(humanoid.leftArm);
            player.rightPants.copyFrom(humanoid.rightLeg);
            player.leftPants.copyFrom(humanoid.leftLeg);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CHANNELS.clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            CHANNELS.clear();
        }
    }
}
