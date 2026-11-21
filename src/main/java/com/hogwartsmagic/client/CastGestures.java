package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * The wand gestures of the wand-carrying NPCs (wizards, students, Voldemort): each spell's own move from
 * FirstPersonWandRig, read as a body pose for their models. A new swing of an NPC's wand is a cast, and a beam
 * leaving its wand names the spell (Voldemort casts without a swing). Visual only.
 *
 * <p>Players are not posed here: your own first-person arm is FirstPersonWand's (FirstPersonCast picks the move),
 * and every player seen from outside plays the same moves through CastMotions. The one player pose left here is
 * your own channel hold seen in third person. The Killing Curse is AvadaChargePose's everywhere.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CastGestures {

    /** A gesture being played by one NPC. {@code start} is that NPC's tickCount when it began. */
    public record Cast(int start, SpellType spell, FirstPersonCast.Gesture gesture, FirstPersonCast.Variant variant) {
        FirstPersonWandRig.Motion motion() {
            return FirstPersonWandRig.motionOf(this.spell, this.gesture);
        }

        /** Progress 0..1, or -1 once it is over (or before it began). */
        public float progress(float ageInTicks) {
            float t = (ageInTicks - this.start) / motion().length();
            return t >= 0.0F && t < 1.0F ? t : -1.0F;
        }
    }

    private static final Map<Integer, Cast> CASTS = new HashMap<>();
    private static final Map<Integer, int[]> SWING_STATE = new HashMap<>(); // {wasSwinging 0/1, lastSwingTime}

    private static Cast newCast(LivingEntity caster, SpellType spell) {
        FirstPersonCast.Gesture gesture = FirstPersonCast.forSpell(spell);
        // The server's cast packet came first: play its variant, which the bolt's bend is taken from too.
        CastMotions.Cast motion = CastMotions.get(caster.getId(), AvadaChargeClientState.now(1.0F));
        if (motion != null && motion.spell == spell) {
            return new Cast(caster.tickCount, spell, gesture, motion.variant);
        }
        java.util.Random random = new java.util.Random(caster.getId() * 341873128712L + caster.tickCount * 132897987541L);
        return new Cast(caster.tickCount, spell, gesture, FirstPersonCast.randomVariant(random, gesture, spell));
    }

    /**
     * Whether this caster's model has its wand tip tracked as drawn (WandSwipeLayer): players, wizards, students
     * and Voldemort. Beams, the wand trail and the bolt's bend start from that tip while it is known.
     */
    public static boolean posesModel(LivingEntity caster) {
        return caster instanceof Player || caster instanceof com.hogwartsmagic.entity.WizardEntity
                || caster instanceof com.hogwartsmagic.entity.StudentEntity;
    }

    /** Whether an NPC {@code caster} is in the middle of one of these gestures (its swing and the settle after). */
    public static boolean ownsArm(LivingEntity caster) {
        Cast cast = CASTS.get(caster.getId());
        if (cast == null) {
            return false;
        }
        int since = caster.tickCount - cast.start();
        return since >= 0 && since <= cast.motion().length() + 2;
    }

    /**
     * Whether {@code caster}'s arm is mid-cast as drawn from outside: an NPC's gesture here, or a player's move
     * from CastMotions. WandSwipeLayer keeps the drawn wand tip of these.
     */
    public static boolean castingSeen(LivingEntity caster) {
        if (caster instanceof Player) {
            return CastMotions.get(caster.getId(), AvadaChargeClientState.now(1.0F)) != null;
        }
        return ownsArm(caster);
    }

    public static Cast castOf(LivingEntity entity) {
        return CASTS.get(entity.getId());
    }

    /**
     * The body pose {pitch, yaw, roll, reach, weight} of a cast {@code since} ticks in, from the same move the
     * first-person arm plays; null outside it. The weight blends the gesture in over the first tick and back out
     * over the last third.
     */
    @javax.annotation.Nullable
    static float[] castAngles(Cast cast, float since) {
        FirstPersonWandRig.Motion motion = cast.motion();
        if (since < 0.0F || since >= motion.length()) {
            return null;
        }
        FirstPersonWandRig.Key rest = FirstPersonWandRig.baseRest();
        FirstPersonWandRig.Key key = FirstPersonWandRig.pose(motion, cast.variant(), rest, since, rest);
        float[] p = FirstPersonWandRig.bodyAngles(key);
        float weight = smooth(since / 1.2F) * smooth((motion.length() - since) / (motion.length() * 0.35F));
        return new float[]{p[0], p[1], p[2], p[3], weight};
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            CASTS.clear();
            SWING_STATE.clear();
            return;
        }
        // Wand-carrying NPCs (wizards, students, Voldemort): a new wand swing is a cast.
        for (Entity e : minecraft.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity caster) || caster instanceof Player
                    || !MagicWandItem.isAnyWand(caster.getMainHandItem())) {
                continue;
            }
            int[] state = SWING_STATE.computeIfAbsent(caster.getId(), id -> new int[2]);
            boolean swinging = caster.swinging;
            if (swinging && (state[0] == 0 || caster.swingTime < state[1])) {
                Cast previous = CASTS.get(caster.getId());
                // A beam seen this same tick has already started the cast with the right spell.
                boolean justStarted = previous != null && caster.tickCount - previous.start() <= 3;
                SpellType selected = MagicWandItem.getSelectedSpell(caster.getMainHandItem());
                if (!justStarted && selected != SpellType.AVADA_KEDAVRA) { // its windup is AvadaChargePose's
                    CASTS.put(caster.getId(), newCast(caster, selected));
                }
            }
            state[0] = swinging ? 1 : 0;
            state[1] = caster.swingTime;
        }
        CASTS.entrySet().removeIf(e -> minecraft.level.getEntity(e.getKey()) == null);
        WandSwipeLayer.prune(id -> minecraft.level.getEntity(id) != null);
        SWING_STATE.keySet().removeIf(id -> minecraft.level.getEntity(id) == null);
    }

    /** A beam appearing near its caster is a fresh cast: an NPC's beam names its gesture. */
    @SubscribeEvent
    public static void onBeamJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() || !(event.getEntity() instanceof SpellBeamEntity beam)) {
            return;
        }
        if (beam.getOwner() instanceof LivingEntity caster && beam.distanceToSqr(caster.getEyePosition()) <= 9.0) {
            onCasterBeam(caster, beam.getSpellType()); // further off it is coming into tracking range, not a cast
        }
    }

    /**
     * A beam from a non-player caster names the spell its wand has no selection for. A cast that started in the
     * last few ticks (its swing) takes the spell's gesture; with no such cast (Voldemort casts without a swing)
     * the beam starts one.
     */
    public static void onCasterBeam(LivingEntity caster, SpellType spell) {
        if (caster instanceof Player || spell == null || spell == SpellType.AVADA_KEDAVRA
                || !MagicWandItem.isAnyWand(caster.getMainHandItem())) {
            return; // the Killing Curse: AvadaChargePose's windup and strike, not a gesture
        }
        Cast previous = CASTS.get(caster.getId());
        if (previous != null && caster.tickCount - previous.start() <= 3) {
            CASTS.put(caster.getId(), new Cast(previous.start(), spell, FirstPersonCast.forSpell(spell),
                    newCast(caster, spell).variant()));
        } else {
            CASTS.put(caster.getId(), newCast(caster, spell));
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CASTS.clear();
        SWING_STATE.clear();
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    // ---------------------------------------------------------------- third person

    /**
     * Called at the end of HumanoidModel.setupAnim for every humanoid model: while you hold a channelled spell
     * (Fiendfyre, the Patronus...) and are seen in third person, your wand arm stays up in its hold pose. A cast
     * itself is CastMotions' (ActorPoseMixin), which this leaves alone.
     */
    public static void applyThirdPerson(HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ageInTicks == 0.0F || entity != minecraft.player || !MagicWandItem.isAnyWand(entity.getMainHandItem())
                || CastMotions.get(entity.getId(), AvadaChargeClientState.now(1.0F)) != null) {
            return; // ageInTicks 0: the first-person arm draw, which FirstPersonWand poses
        }
        float weight = FirstPersonCast.holdWeight(Mth.clamp(ageInTicks - entity.tickCount, 0.0F, 1.0F));
        if (weight <= 0.001F) {
            return;
        }
        SpellType held = FirstPersonCast.heldSpell();
        float[] p = FirstPersonWandRig.bodyAngles(
                FirstPersonWandRig.motionOf(held, FirstPersonCast.forSpell(held)).holdKey());
        boolean right = entity.getMainArm() == HumanoidArm.RIGHT;
        float side = right ? 1.0F : -1.0F;
        ModelPart arm = right ? model.rightArm : model.leftArm;
        arm.xRot = Mth.lerp(weight, arm.xRot, model.head.xRot - Mth.HALF_PI + p[0] * Mth.DEG_TO_RAD);
        arm.yRot = Mth.lerp(weight, arm.yRot, model.head.yRot - 0.1F * side + side * p[1] * 0.9F * Mth.DEG_TO_RAD);
        arm.zRot = Mth.lerp(weight, arm.zRot, side * p[2] * 0.5F * Mth.DEG_TO_RAD);
        arm.z += p[3] * 6.0F * weight; // reach: forward is -Z in model space
    }

    /** The gesture pose {pitch, yaw, roll, reach} of an NPC caster and its blend weight, or null when idle. */
    @javax.annotation.Nullable
    private static float[] npcPose(LivingEntity entity, float ageInTicks) {
        if (!MagicWandItem.isAnyWand(entity.getMainHandItem())) {
            return null;
        }
        Cast cast = CASTS.get(entity.getId());
        float[] g = cast == null ? null : castAngles(cast, ageInTicks - cast.start());
        return g == null || g[4] <= 0.001F ? null : g;
    }

    /**
     * The gesture on an NPC model with a free wand arm (WizardModel: wizards and Voldemort). Called at the end
     * of the model's setupAnim, so it blends over the model's own wind-up and recoil. Rotations only: these
     * models do not reset part positions each frame, so moving them would drift.
     */
    public static void applyToArm(LivingEntity entity, float ageInTicks, ModelPart rightArm, ModelPart leftArm,
                                  ModelPart body, float headX, float headY) {
        float[] g = npcPose(entity, ageInTicks);
        if (g == null) {
            return;
        }
        float weight = g[4];
        boolean right = entity.getMainArm() == HumanoidArm.RIGHT;
        float side = right ? 1.0F : -1.0F;
        ModelPart arm = right ? rightArm : leftArm;
        arm.xRot = Mth.lerp(weight, arm.xRot, headX - Mth.HALF_PI + g[0] * Mth.DEG_TO_RAD);
        arm.yRot = Mth.lerp(weight, arm.yRot, headY - 0.1F * side + side * g[1] * 0.9F * Mth.DEG_TO_RAD);
        arm.zRot = Mth.lerp(weight, arm.zRot, side * g[2] * 0.5F * Mth.DEG_TO_RAD);
        body.yRot += side * g[1] * 0.2F * Mth.DEG_TO_RAD * weight;
    }

    /**
     * The gesture on a model that holds its wand with both arms as one group (StudentModel, villager style):
     * the whole group is lifted to point, swung across and twisted.
     */
    public static void applyToArmGroup(LivingEntity entity, float ageInTicks, ModelPart arms, ModelPart body) {
        float[] g = npcPose(entity, ageInTicks);
        if (g == null) {
            return;
        }
        float weight = g[4];
        arms.xRot = Mth.lerp(weight, arms.xRot, -1.65F + g[0] * Mth.DEG_TO_RAD);
        arms.yRot = Mth.lerp(weight, arms.yRot, g[1] * 0.6F * Mth.DEG_TO_RAD);
        arms.zRot = Mth.lerp(weight, arms.zRot, g[2] * 0.4F * Mth.DEG_TO_RAD);
        body.yRot += g[1] * 0.2F * Mth.DEG_TO_RAD * weight;
    }

    private CastGestures() {
    }
}
