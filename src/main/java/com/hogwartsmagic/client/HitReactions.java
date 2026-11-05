package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellBeamFx;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hogwartsmagic.client.SpellFxGeometry.*;

/**
 * How a body takes a spell. Stupefy throws it into a backwards tumble with a half spin, and it picks itself up;
 * Expelliarmus flings the wand arm up as the wand flies out of the hand trailing red light; a petrified body goes
 * rigid, arms snapped to its sides; the Killing Curse washes the body in green-white light that drains to a
 * sickly pallor while a pale light rises from where it fell; anything that hurts makes it flinch away from the
 * beam. Whole-body moves and the tint are laid on the render (RenderLivingEvent), limbs after setupAnim
 * (ActorPoseMixin), the rising light in the world.
 *
 * <p>A map, not a switch: switch-on-enum has failed to load in this mod's render code before.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class HitReactions {
    private static final int NONE = 0;
    private static final int TUMBLE = 1;
    private static final int FLINCH = 2;
    private static final int DISARM = 3;
    private static final int DRAINED = 4;

    private static final float TUMBLE_TICKS = 22.0f;
    private static final float FLINCH_TICKS = 8.0f;
    private static final float DISARM_TICKS = 12.0f;
    private static final float DRAINED_TICKS = 40.0f;
    private static final float FLIGHT_TICKS = 30.0f;

    private static final Map<SpellType, Integer> KINDS = new EnumMap<>(SpellType.class);

    static {
        KINDS.put(SpellType.STUPEFY, TUMBLE);
        KINDS.put(SpellType.EXPELLIARMUS, DISARM);
        KINDS.put(SpellType.AVADA_KEDAVRA, DRAINED);
        for (SpellType spell : new SpellType[]{SpellType.CONFRINGO, SpellType.BOMBARDA, SpellType.DIFFINDO,
                SpellType.SECTUMSEMPRA, SpellType.INCENDIO, SpellType.FIENDFYRE, SpellType.CRUCIO,
                SpellType.DOLOHOV_CURSE, SpellType.GLACIUS, SpellType.DEPULSO}) {
            KINDS.put(spell, FLINCH);
        }
    }

    private static final class Reaction {
        final int kind;
        final Vec3 dir;
        final double start;
        /** Where a drained body fell, for the light that rises from it. */
        Vec3 fell;

        Reaction(int kind, Vec3 dir, double start) {
            this.kind = kind;
            this.dir = dir;
            this.start = start;
        }

        float age(double now) {
            return (float) (now - start);
        }

        float length() {
            if (kind == TUMBLE) {
                return TUMBLE_TICKS;
            }
            if (kind == DISARM) {
                return DISARM_TICKS;
            }
            return kind == DRAINED ? DRAINED_TICKS : FLINCH_TICKS;
        }
    }

    /** A disarmed wand in flight, followed by a trail. */
    static final class Flight {
        final SpellType spell;
        final double start;

        Flight(SpellType spell, double start) {
            this.spell = spell;
            this.start = start;
        }
    }

    private static final Map<Integer, Reaction> REACTIONS = new HashMap<>();
    private static final Map<Integer, Flight> FLIGHTS = new HashMap<>();
    /** Entities this frame's Pre pushed a pose for, so Post pops exactly those. */
    private static final Set<Integer> PUSHED = new HashSet<>();
    private static final Set<Integer> TINTED = new HashSet<>();

    /** Your own view being struck: when, from where, how hard. */
    private static double kickStart = -1.0;
    private static Vec3 kickDir = Vec3.ZERO;
    private static float kickStrength;
    private static final float KICK_TICKS = 8.0f;

    private HitReactions() {
    }

    /** Struck yourself: the view snaps back and rolls away from the side the spell came from, and settles. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOW)
    public static void onCameraAngles(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (kickStart < 0.0 || minecraft.level == null || minecraft.player == null) {
            return;
        }
        float t = (float) (minecraft.level.getGameTime() + event.getPartialTick() - kickStart);
        if (t < 0.0f || t > KICK_TICKS) {
            kickStart = -1.0;
            return;
        }
        float decay = 1.0f - t / KICK_TICKS;
        decay *= decay;
        Vec3 look = minecraft.player.getViewVector(1.0f);
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        float side = right.lengthSqr() < 1.0E-4 ? 0.0f : (float) right.normalize().dot(kickDir);
        float snap = AvadaChargePose.smoothstep(0.0f, 1.0f, t);
        event.setPitch(event.getPitch() - 5.0f * kickStrength * decay * snap);
        event.setRoll(event.getRoll() + 6.0f * kickStrength * side * decay * snap);
    }

    public static void accept(SpellPackets.HitReactionPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        SpellType spell = SpellType.fromId(packet.spellId());
        if (minecraft.level == null || spell == null) {
            return;
        }
        double now = minecraft.level.getGameTime();
        if (packet.kind() == SpellBeamFx.REACT_HIT && minecraft.player != null
                && packet.entityId() == minecraft.player.getId()) {
            kickStart = now;
            kickDir = new Vec3(packet.dx(), packet.dy(), packet.dz());
            kickStrength = spell == SpellType.AVADA_KEDAVRA || KINDS.getOrDefault(spell, NONE) == TUMBLE ? 1.0f : 0.55f;
        }
        if (packet.kind() == SpellBeamFx.REACT_FLIGHT) {
            FLIGHTS.put(packet.entityId(), new Flight(spell, now));
            return;
        }
        int kind = KINDS.getOrDefault(spell, NONE);
        if (kind == NONE) {
            return;
        }
        Vec3 dir = new Vec3(packet.dx(), 0.0, packet.dz());
        dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
        Reaction reaction = new Reaction(kind, dir, now);
        Entity entity = minecraft.level.getEntity(packet.entityId());
        if (entity != null) {
            reaction.fell = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
        }
        REACTIONS.put(packet.entityId(), reaction);
    }

    /** Drop reactions and flights for bodies that are never drawn again (walked off, logged out). */
    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || minecraft.level == null) {
            return;
        }
        double now = minecraft.level.getGameTime();
        REACTIONS.values().removeIf(reaction -> reaction.age(now) > reaction.length() || reaction.age(now) < 0.0f);
        FLIGHTS.values().removeIf(flight -> now - flight.start > FLIGHT_TICKS || now < flight.start);
    }

    private static Reaction active(int entityId, double now) {
        Reaction reaction = REACTIONS.get(entityId);
        if (reaction != null && reaction.age(now) > reaction.length()) {
            REACTIONS.remove(entityId);
            return null;
        }
        return reaction;
    }

    static boolean hasFlights() {
        return !FLIGHTS.isEmpty();
    }

    /** Wands in flight, for the trail renderer: {entity id, flight}. */
    static Iterable<Map.Entry<Integer, Flight>> flights(double now) {
        FLIGHTS.values().removeIf(flight -> now - flight.start > FLIGHT_TICKS);
        return FLIGHTS.entrySet();
    }

    // ---- the whole body ----------------------------------------------------------------------------------------

    /** Last, so a handler that cancels the render never leaves our pushed pose on the stack. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (REACTIONS.isEmpty()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        double now = AvadaChargeClientState.now(event.getPartialTick());
        Reaction reaction = active(entity.getId(), now);
        if (reaction == null) {
            return;
        }
        float t = reaction.age(now);
        if (reaction.kind == DRAINED) {
            // A green-white flash over the body, draining to a sickly green pallor.
            float flash = 1.0f - AvadaChargePose.smoothstep(0.0f, 6.0f, t);
            // The colour applies when a batch is drawn, not when it is written: flush what came before, and (in
            // Post) this body's own geometry before the colour is put back.
            if (event.getMultiBufferSource() instanceof MultiBufferSource.BufferSource pending) {
                pending.endBatch();
            }
            RenderSystem.setShaderColor(0.62f + 0.38f * flash, 0.86f + 0.14f * flash, 0.66f + 0.34f * flash, 1.0f);
            TINTED.add(entity.getId());
            return;
        }
        // A dragon or a troll doesn't tumble onto its back: only human-sized humanoids, and never a boss.
        if (!(event.getRenderer().getModel() instanceof net.minecraft.client.model.HumanoidModel<?>)
                || entity.getBbHeight() > 2.5f || com.hogwartsmagic.spell.combat.CombatTags.isBoss(entity)) {
            return;
        }
        float tilt;
        float spin = 0.0f;
        if (reaction.kind == TUMBLE) {
            // Thrown back and round, held down a moment, then up again.
            float down = AvadaChargePose.smoothstep(0.0f, 5.0f, t);
            float up = AvadaChargePose.smoothstep(14.0f, TUMBLE_TICKS, t);
            tilt = 1.25f * down * (1.0f - up);
            spin = (float) Math.PI * AvadaChargePose.smoothstep(0.0f, 7.0f, t) * (1.0f - up);
        } else {
            // A jolt away from the beam, recovering at once.
            float jolt = AvadaChargePose.smoothstep(0.0f, 1.5f, t) * (1.0f - AvadaChargePose.smoothstep(1.5f,
                    reaction.length(), t));
            tilt = (reaction.kind == DISARM ? 0.3f : 0.25f) * jolt;
        }
        if (tilt <= 0.001f && spin <= 0.001f) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        PUSHED.add(entity.getId());
        double half = entity.getBbHeight() * 0.5;
        Vec3 axis = new Vec3(reaction.dir.z, 0.0, -reaction.dir.x);
        // Pivot at mid-height; a body thrown flat rests on its back, half a body depth above the ground.
        if (reaction.kind == TUMBLE) {
            pose.translate(0.0, (entity.getBbWidth() * 0.5 - half) * Math.min(1.0f, tilt / 1.25f), 0.0);
        }
        pose.translate(0.0, half, 0.0);
        pose.mulPose(new Quaternionf().rotateAxis(tilt, (float) axis.x, 0.0f, (float) axis.z));
        pose.mulPose(new Quaternionf().rotateY(spin));
        pose.translate(0.0, -half, 0.0);
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        int id = event.getEntity().getId();
        if (TINTED.remove(id)) {
            if (event.getMultiBufferSource() instanceof MultiBufferSource.BufferSource pending) {
                pending.endBatch();
            }
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
        if (PUSHED.remove(id)) {
            event.getPoseStack().popPose();
        }
    }

    // ---- the limbs ---------------------------------------------------------------------------------------------

    /** Right after setupAnim (ActorPoseMixin): a disarmed arm flung up, a petrified body rigid. */
    public static void applyPose(EntityModel<?> model, Entity entity, float partial) {
        if (!(model instanceof HumanoidModel<?> humanoid) || !(entity instanceof LivingEntity living)) {
            return;
        }
        boolean changed = false;
        if (living.hasEffect(ExampleMod.PETRIFIED.getHolder().get())) {
            // Rigid: arms snapped to the sides, legs locked together.
            humanoid.rightArm.xRot = 0.0f;
            humanoid.rightArm.yRot = 0.0f;
            humanoid.rightArm.zRot = 0.04f;
            humanoid.leftArm.xRot = 0.0f;
            humanoid.leftArm.yRot = 0.0f;
            humanoid.leftArm.zRot = -0.04f;
            humanoid.rightLeg.xRot = 0.0f;
            humanoid.rightLeg.yRot = 0.0f;
            humanoid.rightLeg.zRot = 0.0f;
            humanoid.leftLeg.xRot = 0.0f;
            humanoid.leftLeg.yRot = 0.0f;
            humanoid.leftLeg.zRot = 0.0f;
            humanoid.head.xRot = 0.0f;
            humanoid.head.yRot = 0.0f;
            humanoid.hat.copyFrom(humanoid.head);
            changed = true;
        }
        if (!REACTIONS.isEmpty()) {
            double now = AvadaChargeClientState.now(partial);
            Reaction reaction = active(entity.getId(), now);
            if (reaction != null && reaction.kind == DISARM) {
                float t = reaction.age(now);
                float fling = AvadaChargePose.smoothstep(0.0f, 1.5f, t)
                        * (1.0f - AvadaChargePose.smoothstep(4.0f, DISARM_TICKS, t));
                humanoid.rightArm.xRot = Mth.lerp(fling, humanoid.rightArm.xRot, -2.7f);
                humanoid.rightArm.zRot = Mth.lerp(fling, humanoid.rightArm.zRot, 0.35f);
                changed = true;
            }
        }
        if (changed && model instanceof PlayerModel<?> player) {
            player.rightSleeve.copyFrom(humanoid.rightArm);
            player.leftSleeve.copyFrom(humanoid.leftArm);
            player.rightPants.copyFrom(humanoid.rightLeg);
            player.leftPants.copyFrom(humanoid.leftLeg);
        }
    }

    // ---- the light leaving a body ------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || REACTIONS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float partial = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        double now = AvadaChargeClientState.now(partial);
        Vec3 camera = event.getCamera().getPosition();
        List<Reaction> drained = new ArrayList<>();
        for (Reaction reaction : REACTIONS.values()) {
            if (reaction.kind == DRAINED && reaction.fell != null && reaction.age(now) <= DRAINED_TICKS) {
                drained.add(reaction);
            }
        }
        if (drained.isEmpty()) {
            return;
        }
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer out = buffer.getBuffer(ModRenderTypes.AVADA_LIGHT);
        for (Reaction reaction : drained) {
            float t = reaction.age(now);
            float f = t / DRAINED_TICKS;
            Vec3 base = reaction.fell.subtract(camera);
            // A pale column rising out of the body and thinning away.
            float rise = AvadaChargePose.smoothstep(0.0f, 0.7f, f);
            float alpha = 0.5f * AvadaChargePose.smoothstep(0.0f, 0.1f, f) * (1.0f - f);
            Vec3[] column = new Vec3[6];
            float[] width = new float[6];
            float[] a = new float[6];
            for (int i = 0; i < 6; i++) {
                float u = i / 5.0f;
                column[i] = base.add(0.0, 2.4 * rise * u, 0.0);
                width[i] = 0.35f * (1.0f - 0.7f * u);
                a[i] = alpha * (1.0f - u);
            }
            ribbon(out, m, column, width, a, 1.0f, 0.7f, GREEN_R, GREEN_G, GREEN_B);
            ribbon(out, m, column, width, a, 0.25f, 1.0f, HOT_R, HOT_G, HOT_B);
            billboard(out, m, base, 0.9f, 0.35f * (1.0f - f), GREEN_R, GREEN_G, GREEN_B, false);
            // Motes drifting up out of it.
            for (int k = 0; k < 8; k++) {
                float life = t - k * 3.0f;
                if (life < 0.0f || life > 20.0f) {
                    continue;
                }
                Vec3 mote = base.add(randomDirection(reaction.hashCode() + k * 31).scale(0.35))
                        .add(0.0, 0.08 * life, 0.0);
                billboard(out, m, mote, 0.06f, 0.9f * (1.0f - life / 20.0f), HOT_R, HOT_G, HOT_B, true, 2, 8);
            }
        }
        buffer.endBatch(ModRenderTypes.AVADA_LIGHT);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        REACTIONS.clear();
        FLIGHTS.clear();
        PUSHED.clear();
        TINTED.clear();
    }
}
