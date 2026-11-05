package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.event.MandrakePullEvents;
import com.hogwartsmagic.network.SpellPackets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Pulling a mandrake, as the body does it. The puller turns square to the plant, bends double at the hips and
 * takes it in both hands, feet braced one before the other; then heaves, again and again, each heave a hard jerk
 * back and a slow give as the root fights, leaning further back and shaking harder as it starts to come. When it
 * comes, the last heave throws them back with the root hoisted overhead (it flies up out of the soil, server
 * side); when it slips, the grip tears loose and they reel back, arms flailing, before finding their feet. Let go
 * early and they straighten up.
 *
 * <p>The lean is laid on the render (RenderLivingEvent, pivoting at the hips, legs counter-turned so the feet stay
 * put), the limbs right after setupAnim (ActorPoseMixin), the arms aimed at the plant from the shoulders' real
 * position. In first person both arms reach into view and tug, and the view jerks with each heave.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class MandrakePullClient {
    private static final byte HOLDING = -1;
    private static final float PULL_TICKS = MandrakePullEvents.PULL_TICKS;
    /** Bending down and taking hold. */
    private static final float REACH_TICKS = 7.0f;
    /** One heave: a hard jerk back, then the root's slow drag forward again. */
    private static final float HEAVE_TICKS = 10.0f;
    /** A pull whose end never arrives (walked out of tracking range) lets go this long after it should be over. */
    private static final float LAPSE_TICKS = 15.0f;
    private static final float PULLED_TICKS = 18.0f;
    private static final float SLIPPED_TICKS = 22.0f;
    private static final float STOPPED_TICKS = 6.0f;

    /** Bent double over the plant, radians forward of upright. */
    private static final float STOOP = 0.95f;
    /** Player models are drawn at 15/16 scale: hips and shoulders above the feet, in blocks. */
    private static final double HIP_HEIGHT = 0.9375 * (1.501 - 12.0 / 16.0);
    private static final double HIP_TO_SHOULDER = 0.9375 * 10.0 / 16.0;
    /** Where on the plant the hands close: the base of the leaves. */
    private static final double GRIP_HEIGHT = 0.30;

    private static final class Pull {
        final BlockPos pos;
        final double start;
        final float phase;
        byte end = HOLDING;
        double endTime;

        Pull(int entityId, BlockPos pos, double start) {
            this.pos = pos;
            this.start = start;
            this.phase = ((entityId * 0x9E3779B1L + pos.asLong()) & 0xFFFF) / 65536.0f * Mth.TWO_PI;
        }

        float length() {
            if (end == SpellPackets.MandrakePullPacket.PULLED) {
                return PULLED_TICKS;
            }
            return end == SpellPackets.MandrakePullPacket.SLIPPED ? SLIPPED_TICKS : STOPPED_TICKS;
        }
    }

    /** One moment of the pull, everything in radians unless said otherwise. */
    private static final class Frame {
        /** How much of the pose is showing. */
        float weight = 1.0f;
        /** Forward of upright, pivoting at the hips; negative leans back. */
        float lean;
        /** 1: both arms aimed at the plant; 0: at {@link #armUp}. */
        float aim = 1.0f;
        /** Arms' angle from straight down toward forward, when not aimed at the plant. */
        float armUp;
        /** Each arm swung either way of {@link #armUp}, opposite to the other (flailing). */
        float armSplit;
        /** Arms flung out sideways. */
        float armOut;
        /** Hands drawn in together on the plant (or the root). */
        float grip;
        /** One foot forward, the other back. */
        float stride;
        /** Head tipped up from where it is looking. */
        float headUp;
        float tremble;
        /** 0 to 1 through each heave's jerk, for the first-person arms and the view. */
        float tug;
        /** 0 to 1 through the whole heaving. */
        float progress;
        /** First person: arms reaching into view. */
        float reach;
        /** First person: arms thrown up (the root hoisted, or the grip torn loose). */
        float lift;
        /** The view's jerk when it comes out or slips. */
        float kick;
    }

    private static final Map<Integer, Pull> PULLS = new HashMap<>();
    /** Entities this frame's Pre pushed a pose for, so Post pops exactly those. */
    private static final Set<Integer> PUSHED = new HashSet<>();

    private MandrakePullClient() {
    }

    public static void accept(SpellPackets.MandrakePullPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        double now = minecraft.level.getGameTime();
        if (packet.state() == SpellPackets.MandrakePullPacket.START) {
            PULLS.put(packet.playerId(), new Pull(packet.playerId(), packet.pos().immutable(), now));
            return;
        }
        Pull pull = PULLS.get(packet.playerId());
        if (pull != null && pull.end == HOLDING) {
            pull.end = packet.state();
            pull.endTime = now;
        }
    }

    // ---- the timeline ------------------------------------------------------------------------------------------

    private static Frame frame(Pull pull, double now) {
        if (pull.end == HOLDING) {
            float t = (float) (now - pull.start);
            if (t > PULL_TICKS + LAPSE_TICKS) {
                pull.end = SpellPackets.MandrakePullPacket.STOPPED;
                pull.endTime = now;
            } else {
                return heave(Math.max(0.0f, t), pull.phase);
            }
        }
        float s = (float) (now - pull.endTime);
        if (s > pull.length()) {
            return null;
        }
        Frame from = heave(Math.max(0.0f, (float) (pull.endTime - pull.start)), pull.phase);
        if (pull.end == SpellPackets.MandrakePullPacket.PULLED) {
            return pulled(from, s);
        }
        if (pull.end == SpellPackets.MandrakePullPacket.SLIPPED) {
            return slipped(from, s);
        }
        from.weight = 1.0f - smoothstep(0.0f, STOPPED_TICKS, s);
        from.reach *= from.weight;
        return from;
    }

    /** Taking hold, then heaving: each heave a jerk back and a slow give, leaning further back as it comes. */
    private static Frame heave(float t, float phase) {
        Frame f = new Frame();
        float reach = smoothstep(0.0f, REACH_TICKS, t);
        float h = Math.max(0.0f, t - REACH_TICKS);
        float p = Mth.clamp(h / (PULL_TICKS - REACH_TICKS), 0.0f, 1.0f);
        float c = (h % HEAVE_TICKS) / HEAVE_TICKS;
        float tug = c < 0.3f ? ease(c / 0.3f) : 1.0f - ease((c - 0.3f) / 0.7f);
        tug *= smoothstep(0.0f, 3.0f, h);
        float amp = 0.10f + 0.14f * p;
        f.lean = reach * (STOOP - 0.5f * p - amp * tug);
        f.grip = 0.32f * reach;
        f.stride = 0.32f * reach;
        f.headUp = (0.10f + 0.18f * p) * tug;
        f.tremble = (0.015f + 0.05f * p) * reach * (Mth.sin(t * 2.9f + phase) + 0.5f * Mth.sin(t * 6.1f + phase * 2.0f));
        f.tug = tug;
        f.progress = p;
        f.reach = reach;
        return f;
    }

    /** It comes: thrown back by the last heave, the root hoisted overhead, then straightening up. */
    private static Frame pulled(Frame from, float s) {
        Frame f = new Frame();
        float yank = smoothstep(0.0f, 3.0f, s);
        float out = smoothstep(9.0f, PULLED_TICKS, s);
        f.weight = 1.0f - out;
        f.lean = Mth.lerp(yank, from.lean, -0.42f);
        f.aim = 1.0f - yank;
        f.armUp = 2.75f;
        f.grip = from.grip;
        f.stride = Mth.lerp(yank, from.stride, 0.45f);
        f.headUp = 0.35f * yank;
        f.reach = from.reach * (1.0f - out);
        f.lift = yank;
        f.kick = yank * (1.0f - smoothstep(3.0f, 14.0f, s));
        return f;
    }

    /** It slips: the grip tears loose and they reel back, arms flailing, until they find their feet. */
    private static Frame slipped(Frame from, float s) {
        Frame f = new Frame();
        float yank = smoothstep(0.0f, 2.5f, s);
        float flail = yank * (1.0f - smoothstep(8.0f, 14.0f, s));
        float out = smoothstep(11.0f, SLIPPED_TICKS, s);
        f.weight = 1.0f - out;
        f.lean = Mth.lerp(yank, from.lean, -0.62f) + 0.08f * flail * Mth.sin(s * 0.9f);
        f.aim = 1.0f - yank;
        f.armUp = 2.3f + 0.35f * flail * Mth.sin(s * 1.1f);
        f.armSplit = 0.6f * flail * Mth.sin(s * 1.3f);
        f.armOut = 0.55f * yank;
        f.grip = from.grip * (1.0f - yank);
        f.stride = Mth.lerp(yank, from.stride, 0.55f);
        f.headUp = 0.3f * yank;
        f.reach = from.reach * (1.0f - out);
        f.lift = yank;
        f.kick = -yank * (1.0f - smoothstep(3.0f, 16.0f, s));
        return f;
    }

    private static Frame frameFor(Entity entity, double now) {
        if (PULLS.isEmpty() || !(entity instanceof Player)) {
            return null;
        }
        Pull pull = PULLS.get(entity.getId());
        return pull == null ? null : frame(pull, now);
    }

    // ---- the whole body ----------------------------------------------------------------------------------------

    /** Degrees the body turns to face the plant square, already weighted. */
    private static float turn(LivingEntity entity, Pull pull, float weight, float partial) {
        double dx = pull.pos.getX() + 0.5 - Mth.lerp(partial, entity.xo, entity.getX());
        double dz = pull.pos.getZ() + 0.5 - Mth.lerp(partial, entity.zo, entity.getZ());
        if (dx * dx + dz * dz < 0.01) {
            return 0.0f;
        }
        float yawToPlant = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        float bodyYaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
        return Mth.wrapDegrees(yawToPlant - bodyYaw) * weight;
    }

    /** Last, so a handler that cancels the render never leaves our pushed pose on the stack. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (PULLS.isEmpty() || !(event.getRenderer().getModel() instanceof HumanoidModel<?>)) {
            return;
        }
        double now = AvadaChargeClientState.now(event.getPartialTick());
        Frame f = frameFor(entity, now);
        if (f == null || f.weight <= 0.001f) {
            return;
        }
        Pull pull = PULLS.get(entity.getId());
        float partial = event.getPartialTick();
        float turn = turn(entity, pull, f.weight, partial);
        float lean = (f.lean + f.tremble * 0.3f) * f.weight;
        float yaw = (Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot) + turn) * Mth.DEG_TO_RAD;
        float rightX = -Mth.cos(yaw);
        float rightZ = -Mth.sin(yaw);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        PUSHED.add(entity.getId());
        // Bent at the hips: a positive turn about the body's right leans it back, so forward is negative.
        pose.translate(0.0, HIP_HEIGHT, 0.0);
        pose.mulPose(new Quaternionf().rotateAxis(-lean, rightX, 0.0f, rightZ));
        pose.translate(0.0, -HIP_HEIGHT, 0.0);
        // Square to the plant (setupRotations turns by 180 - body yaw, so this adds to the body yaw).
        pose.mulPose(Axis.YP.rotationDegrees(-turn));
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (PUSHED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }

    // ---- the limbs ---------------------------------------------------------------------------------------------

    /** Right after setupAnim (ActorPoseMixin), only on parts setupAnim sets outright every frame. */
    public static void applyPose(EntityModel<?> model, Entity entity, float partial) {
        if (PULLS.isEmpty() || !(model instanceof HumanoidModel<?> humanoid) || !(entity instanceof LivingEntity living)) {
            return;
        }
        double now = AvadaChargeClientState.now(partial);
        Frame f = frameFor(entity, now);
        if (f == null || f.weight <= 0.001f) {
            return;
        }
        Pull pull = PULLS.get(entity.getId());
        float w = f.weight;
        float lean = (f.lean + f.tremble * 0.3f) * w;
        float turn = turn(living, pull, w, partial);

        // The head keeps looking where it looked (at the plant) however the body bends, tipping up as it strains.
        humanoid.head.xRot = humanoid.head.xRot - lean - f.headUp * w;
        humanoid.head.yRot = humanoid.head.yRot - turn * Mth.DEG_TO_RAD;

        // Both arms from the shoulders to the plant, or up and out once it has come (or slipped).
        float aimed = aimAt(living, pull, partial, lean);
        float shake = f.tremble;
        for (int i = 0; i < 2; i++) {
            boolean right = i == 0;
            float side = right ? 1.0f : -1.0f;
            float up = Mth.lerp(f.aim, f.armUp + side * f.armSplit, aimed);
            net.minecraft.client.model.geom.ModelPart arm = right ? humanoid.rightArm : humanoid.leftArm;
            arm.xRot = Mth.lerp(w, arm.xRot, -(up + lean) + shake * side);
            // Drawn in together: the right arm turns toward +x, the left toward -x.
            arm.yRot = Mth.lerp(w, arm.yRot, -side * f.grip);
            arm.zRot = Mth.lerp(w, arm.zRot, side * (f.armOut + 0.04f) + shake * 0.5f);
        }

        // Braced: left foot forward, right foot back, a little apart; counter-turned so they stay on the ground.
        humanoid.leftLeg.xRot = Mth.lerp(w, humanoid.leftLeg.xRot, -f.stride - lean);
        humanoid.rightLeg.xRot = Mth.lerp(w, humanoid.rightLeg.xRot, f.stride * 0.8f - lean);
        humanoid.leftLeg.yRot = Mth.lerp(w, humanoid.leftLeg.yRot, -0.12f);
        humanoid.rightLeg.yRot = Mth.lerp(w, humanoid.rightLeg.yRot, 0.12f);
        humanoid.leftLeg.zRot = Mth.lerp(w, humanoid.leftLeg.zRot, -0.07f);
        humanoid.rightLeg.zRot = Mth.lerp(w, humanoid.rightLeg.zRot, 0.07f);

        humanoid.hat.copyFrom(humanoid.head);
        if (model instanceof PlayerModel<?> player) {
            player.rightSleeve.copyFrom(humanoid.rightArm);
            player.leftSleeve.copyFrom(humanoid.leftArm);
            player.rightPants.copyFrom(humanoid.rightLeg);
            player.leftPants.copyFrom(humanoid.leftLeg);
        }
    }

    /** The arms' angle from straight down toward forward that points the shoulders at the grip on the plant. */
    private static float aimAt(LivingEntity entity, Pull pull, float partial, float lean) {
        double x = Mth.lerp(partial, entity.xo, entity.getX());
        double y = Mth.lerp(partial, entity.yo, entity.getY());
        double z = Mth.lerp(partial, entity.zo, entity.getZ());
        double tx = pull.pos.getX() + 0.5 - x;
        double tz = pull.pos.getZ() + 0.5 - z;
        double ahead = Math.sqrt(tx * tx + tz * tz) - HIP_TO_SHOULDER * Mth.sin(lean);
        double below = y + HIP_HEIGHT + HIP_TO_SHOULDER * Mth.cos(lean) - (pull.pos.getY() + GRIP_HEIGHT);
        return (float) Mth.atan2(ahead, below);
    }

    // ---- first person ------------------------------------------------------------------------------------------

    /**
     * Both bare arms reaching into view and tugging, drawn in place of the hands. LOW, so a handler that already
     * cancelled the hand (cloak, possession) is never delivered to here.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (PULLS.isEmpty() || player == null || PossessionClient.isCaster()) {
            return;
        }
        Frame f = frameFor(player, AvadaChargeClientState.now(event.getPartialTick()));
        if (f == null || f.weight <= 0.001f) {
            return;
        }
        event.setCanceled(true);
        boolean main = event.getHand() == InteractionHand.MAIN_HAND;
        HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
        float t = player.tickCount + event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        firstPersonPose(pose, arm, f, main, t);
        renderBareArm(minecraft, pose, event.getMultiBufferSource(), event.getPackedLight(), player, arm);
        pose.popPose();
    }

    /**
     * Our move, laid over the vanilla resting arm: drawn in toward the middle and down onto the plant, jerked back
     * toward you with each heave, thrown up at the end. At weight 0 the main arm is exactly the vanilla bare hand,
     * so handing back to vanilla shows no jump; the other arm drops out of sight.
     */
    private static void firstPersonPose(PoseStack pose, HumanoidArm arm, Frame f, boolean main, float t) {
        float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        float w = f.weight;
        float r = f.reach;
        float tug = f.tug * (0.6f + 0.6f * f.progress);
        float shake = f.tremble * 0.6f;
        float dx = side * (-0.30f * r + 0.22f * f.lift * f.armOut / 0.55f);
        float dy = -0.10f * r - 0.05f * tug + 0.30f * f.lift + shake * 0.5f;
        float dz = -0.10f * r + 0.16f * tug + 0.06f * f.lift;
        float pitch = (-28.0f * r + 18.0f * tug + 55.0f * f.lift) * w;
        float roll = side * (12.0f * r - 20.0f * f.lift * f.armOut / 0.55f) * w + shake * 20.0f;
        pose.translate(dx * w, dy * w, dz * w);
        if (!main) {
            // The other hand has no vanilla pose to hand back to: it comes up from below and sinks out of sight.
            float shown = Math.min(1.0f, r + f.lift) * w;
            pose.translate(0.0f, -0.8f * (1.0f - shown), 0.0f);
        }
        // Turned about the shoulder, just below the bottom corner of the view.
        pose.translate(side * 0.64f, -0.6f, -0.72f);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        pose.translate(-side * 0.64f, 0.6f, 0.72f);
    }

    /** Vanilla's bare first-person arm (ItemInHandRenderer#renderPlayerArm with no swing and fully equipped). */
    private static void renderBareArm(Minecraft minecraft, PoseStack pose, MultiBufferSource buffer, int light,
                                      AbstractClientPlayer player, HumanoidArm arm) {
        boolean right = arm != HumanoidArm.LEFT;
        float f = right ? 1.0f : -1.0f;
        pose.translate(f * 0.64000005f, -0.6f, -0.71999997f);
        pose.mulPose(Axis.YP.rotationDegrees(f * 45.0f));
        pose.translate(f * -1.0f, 3.6f, 3.5f);
        pose.mulPose(Axis.ZP.rotationDegrees(f * 120.0f));
        pose.mulPose(Axis.XP.rotationDegrees(200.0f));
        pose.mulPose(Axis.YP.rotationDegrees(f * -135.0f));
        pose.translate(f * 5.6f, 0.0f, 0.0f);
        EntityRenderer<? super AbstractClientPlayer> renderer = minecraft.getEntityRenderDispatcher().getRenderer(player);
        if (renderer instanceof PlayerRenderer playerRenderer) {
            if (right) {
                playerRenderer.renderRightHand(pose, buffer, light, player);
            } else {
                playerRenderer.renderLeftHand(pose, buffer, light, player);
            }
        }
    }

    /** Your own view: a jerk up with each heave and a shake that grows, a big kick when it comes or slips. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (PULLS.isEmpty() || minecraft.player == null || event.getCamera().getEntity() != minecraft.player) {
            return;
        }
        float partial = (float) event.getPartialTick();
        Frame f = frameFor(minecraft.player, AvadaChargeClientState.now(partial));
        if (f == null || f.weight <= 0.001f) {
            return;
        }
        float strain = (1.5f + 2.5f * f.progress) * f.tug * f.reach;
        float kick = Math.abs(f.kick);
        float pitch = -strain - 9.0f * kick + f.tremble * 12.0f;
        float roll = f.tremble * 18.0f;
        if (f.kick < 0.0f) {
            // Slipped: reeling, the view tips away and rolls.
            pitch -= 5.0f * kick;
            roll += 7.0f * kick * Mth.sin((minecraft.player.tickCount + partial) * 0.7f);
        }
        event.setPitch(event.getPitch() + pitch * f.weight);
        event.setRoll(event.getRoll() + roll * f.weight);
    }

    // ---- housekeeping ------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || PULLS.isEmpty() || minecraft.level == null) {
            return;
        }
        double now = minecraft.level.getGameTime();
        PULLS.entrySet().removeIf(entry -> minecraft.level.getEntity(entry.getKey()) == null
                || frame(entry.getValue(), now) == null);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PULLS.clear();
        PUSHED.clear();
    }

    /** Entity ids mean nothing in the next level (a dimension change, a respawn). */
    @SubscribeEvent
    public static void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            PULLS.clear();
        }
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        return ease((x - edge0) / (edge1 - edge0));
    }
}
