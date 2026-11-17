package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The wand gesture each spell is cast with. A gesture is a few key poses of the wand arm over a short time;
 * the same curve drives the first-person arm (WandCastAnimation), the player model seen by everyone else
 * (third person, through the HumanoidModel mixin) and the wizard, Voldemort and student models. Every player's swing with a wand in the main hand starts
 * the gesture of the spell selected on that wand; every cast path swings the main hand, and the swing is
 * synced to other clients, so remote players animate too. Visual only.
 *
 * Pose numbers are {pitch, yaw, roll, reach}: pitch in degrees (negative raises the wand), yaw in degrees
 * (positive sweeps across the body towards the crosshair), roll in degrees (a twist of the wrist), reach in
 * blocks of the first-person view (negative thrusts forward).
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CastGestures {

    /**
     * One wand gesture: its name, length and the point at which the spell leaves the wand. The motion itself
     * is FirstPersonWandRig's keys; everyone (first person, third person, NPCs) plays those.
     */
    public static final class Gesture {
        public final String name;
        public final float ticks;
        /** Progress at which the spell visibly leaves the wand (the tip flare). */
        public final float release;
        /** Whether a cast may mirror the stroke across the body. A straight lift has nothing to mirror. */
        boolean mirrorable = true;

        Gesture(String name, float ticks, float release) {
            this.name = name;
            this.ticks = ticks;
            this.release = release;
        }
    }

    /** Wind up high and to the side, then a fast diagonal slash across to the crosshair. The default. */
    public static final Gesture FLICK = new Gesture("flick", 9, 0.36F);
    /** Draw back, then a straight jab at the target, held a moment: the duelling stunner. */
    public static final Gesture STAB = new Gesture("stab", 8, 0.34F);
    /** A big cut from high right to low left. */
    public static final Gesture SLASH = new Gesture("slash", 10, 0.42F);
    /** Two cuts in a Z: high right to low left, back across, and down again. */
    public static final Gesture DOUBLE_SLASH = new Gesture("double_slash", 13, 0.33F);
    /** The wand pulled in to the chest to gather force, then a heavy shove. */
    public static final Gesture PUSH = new Gesture("push", 10, 0.44F);
    /** The wand lifted high overhead and held up. */
    public static final Gesture RAISE = new Gesture("raise", 12, 0.36F);
    /** Swish and flick: a small loop, then a sharp flick upwards. */
    public static final Gesture SWISH_FLICK = new Gesture("swish_flick", 14, 0.58F);
    /** Two small circles, then a point: the mind charms and the binding. */
    public static final Gesture SPIRAL = new Gesture("spiral", 14, 0.64F);
    /** Reach out at the target, then yank back towards the chest: Accio. */
    public static final Gesture PULL = new Gesture("pull", 11, 0.24F);
    /** Lifted back over the shoulder, then lashed forward and down like a whip. */
    public static final Gesture WHIP = new Gesture("whip", 11, 0.40F);
    /** The killing stroke: the wand raised straight overhead, then driven down at the target and held. */
    public static final Gesture CURSE = new Gesture("curse", 11, 0.42F);
    /** Point at the lock and turn the wrist like a key. */
    public static final Gesture TWIST = new Gesture("twist", 10, 0.46F);

    static {
        // Mirroring suits strokes that cut across (flick, slash, whip, curse, swish); a lift, a jab, a shove, a
        // pull to the chest, a circle or a key turn made from the far side only looks awkward.
        for (Gesture g : new Gesture[]{RAISE, STAB, PUSH, PULL, SPIRAL, TWIST}) {
            g.mirrorable = false;
        }
    }

    private static final Map<SpellType, Gesture> BY_SPELL = new EnumMap<>(SpellType.class);

    static {
        put(STAB, SpellType.STUPEFY, SpellType.EXPELLIARMUS, SpellType.PETRIFICUS_TOTALUS, SpellType.LEVICORPUS,
                SpellType.REVELIO);
        put(SLASH, SpellType.DIFFINDO, SpellType.CONFRINGO, SpellType.INCENDIO);
        put(DOUBLE_SLASH, SpellType.SECTUMSEMPRA);
        put(PUSH, SpellType.BOMBARDA, SpellType.DEPULSO, SpellType.ARRESTO_MOMENTUM, SpellType.PROTEGO);
        put(RAISE, SpellType.LUMOS, SpellType.EXPECTO_PATRONUM, SpellType.PROTEGO_MAXIMA, SpellType.MORSMORDRE,
                SpellType.ASCENDIO, SpellType.HEAL, SpellType.CAVE_INIMICUM, SpellType.PIERTOTUM_LOCOMOTOR,
                SpellType.TENEBRIS_FLIGHT, SpellType.APPARITION);
        put(SWISH_FLICK, SpellType.WINGARDIUM_LEVIOSA, SpellType.ALATUS);
        put(SPIRAL, SpellType.CONFUNDO, SpellType.OBLIVIATE, SpellType.IMPERIO, SpellType.INCARCEROUS,
                SpellType.SERPENSORTIA, SpellType.REPARO);
        put(PULL, SpellType.ACCIO);
        put(WHIP, SpellType.GLACIUS, SpellType.AQUA_ERUCTO, SpellType.FIENDFYRE);
        put(CURSE, SpellType.AVADA_KEDAVRA, SpellType.CRUCIO, SpellType.DOLOHOV_CURSE);
        put(TWIST, SpellType.ALOHOMORA, SpellType.COLLOPORTUS);
    }

    /** Spells that keep going while the cast key is held; the wand stays up in the gesture's hold pose. */
    private static final Set<SpellType> CHANNELLED = EnumSet.of(
            SpellType.FIENDFYRE, SpellType.EXPECTO_PATRONUM, SpellType.WINGARDIUM_LEVIOSA, SpellType.CRUCIO,
            SpellType.IMPERIO, SpellType.PROTEGO_MAXIMA);

    private static void put(Gesture gesture, SpellType... spells) {
        for (SpellType spell : spells) {
            BY_SPELL.put(spell, gesture);
        }
    }

    public static Gesture forSpell(SpellType spell) {
        return spell == null ? FLICK : BY_SPELL.getOrDefault(spell, FLICK);
    }

    // ---------------------------------------------------------------- who is casting what

    /** A gesture being played by one player. {@code start} is that player's tickCount when it began. */
    /**
     * How one cast differs from the last, so repeated casts never look the same: mirrored casts swing from the
     * other side (the arm crosses the body first, then cuts back out), and every cast gets its own size and tilt.
     * shiftX/shiftY move the first-person strike on screen (FirstPersonWandRig).
     */
    public record Variant(boolean mirror, float scale, float pitchOffset, float rollOffset, float shiftX, float shiftY) {
        public static final Variant NONE = new Variant(false, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    /** The variant a player's next cast plays: mostly the other side from the last, never quite the same. */
    private static Variant nextVariant(LivingEntity player, Cast previous, Gesture gesture) {
        java.util.Random random = new java.util.Random(player.getId() * 341873128712L + player.tickCount * 132897987541L);
        boolean lastMirror = previous != null && previous.variant().mirror();
        boolean mirror = gesture.mirrorable && (random.nextFloat() < 0.75F ? !lastMirror : lastMirror);
        float scale = 0.8F + random.nextFloat() * 0.4F;
        float pitch = (random.nextFloat() - 0.5F) * 22.0F;
        float roll = (random.nextFloat() - 0.5F) * 30.0F;
        float shiftX = mirror ? -0.42F - random.nextFloat() * 0.18F : (random.nextFloat() - 0.3F) * 0.2F;
        float shiftY = (random.nextFloat() - 0.4F) * 0.22F;
        return new Variant(mirror, scale, pitch, roll, shiftX, shiftY);
    }

    public record Cast(int start, SpellType spell, Gesture gesture, Variant variant) {
        /** Progress 0..1, or -1 once it is over (or before it began). */
        public float progress(float ageInTicks) {
            float t = (ageInTicks - this.start) / this.gesture.ticks;
            return t >= 0.0F && t < 1.0F ? t : -1.0F;
        }
    }

    private static final Map<Integer, Cast> CASTS = new HashMap<>();
    private static final Map<Integer, int[]> SWING_STATE = new HashMap<>(); // {wasSwinging 0/1, lastSwingTime}
    /** Smoothed 0..1 weight of the local player's channel hold pose. */
    private static float holdWeight;
    private static float holdWeightO;

    /**
     * Whether this caster's model is posed by these gestures: wizards and students (WizardModel, StudentModel).
     * Players are posed by CastMotions in third person (and by FirstPersonWandRig in your own view); Voldemort has
     * his own rig.
     */
    public static boolean posesModel(LivingEntity caster) {
        return (caster instanceof com.hogwartsmagic.entity.WizardEntity
                && !(caster instanceof com.hogwartsmagic.entity.VoldemortEntity))
                || caster instanceof com.hogwartsmagic.entity.StudentEntity;
    }

    public static Cast castOf(LivingEntity entity) {
        return CASTS.get(entity.getId());
    }

    /** The local player's hold weight this frame, eased between ticks. */
    public static float localHoldWeight(float partialTick) {
        return Mth.lerp(partialTick, holdWeightO, holdWeight);
    }

    /** The hold pose of the local player's selected spell, as body angles (third person). */
    public static float[] localHoldPose() {
        Player player = Minecraft.getInstance().player;
        SpellType spell = player == null ? null : MagicWandItem.getSelectedSpell(player.getMainHandItem());
        return FirstPersonWandRig.bodyAngles(FirstPersonWandRig.motionOf(forSpell(spell)).holdKey());
    }

    /**
     * The body pose {pitch, yaw, roll, reach, weight} of a cast {@code since} ticks in, from the same keys as
     * the first-person arm, so everyone sees the swing the caster sees; null outside the gesture. The weight
     * blends the gesture in over the first tick and back out over the last third.
     */
    @javax.annotation.Nullable
    static float[] castAngles(Cast cast, float since) {
        FirstPersonWandRig.Motion motion = FirstPersonWandRig.motionOf(cast.gesture());
        if (since < 0.0F || since >= motion.length()) {
            return null;
        }
        FirstPersonWandRig.Key key = FirstPersonWandRig.pose(motion, cast.variant(), FirstPersonWandRig.baseRest(), since);
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
        // Players and wand-carrying NPCs (wizards, students, Voldemort) alike: a new wand swing is a cast.
        for (net.minecraft.world.entity.Entity e : minecraft.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity caster) || !MagicWandItem.isAnyWand(caster.getMainHandItem())) {
                continue;
            }
            int[] state = SWING_STATE.computeIfAbsent(caster.getId(), id -> new int[2]);
            boolean swinging = caster.swinging;
            if (swinging && (state[0] == 0 || caster.swingTime < state[1])) {
                Cast previous = CASTS.get(caster.getId());
                // An NPC's beam (seen this same tick) has already started its cast with the right spell.
                boolean justStarted = !(caster instanceof Player) && previous != null
                        && caster.tickCount - previous.start() <= 3;
                if (!justStarted) {
                    SpellType spell = MagicWandItem.getSelectedSpell(caster.getMainHandItem());
                    Gesture gesture = forSpell(spell);
                    CASTS.put(caster.getId(), new Cast(caster.tickCount, spell, gesture,
                            nextVariant(caster, previous, gesture)));
                }
            }
            state[0] = swinging ? 1 : 0;
            state[1] = caster.swingTime;
        }
        CASTS.entrySet().removeIf(e -> minecraft.level.getEntity(e.getKey()) == null);
        WandSwipeLayer.prune(id -> minecraft.level.getEntity(id) != null);
        SWING_STATE.keySet().removeIf(id -> minecraft.level.getEntity(id) == null);

        holdWeightO = holdWeight;
        Player local = minecraft.player;
        boolean holding = false;
        if (local != null && minecraft.options.keyUse.isDown() && MagicWandItem.isAnyWand(local.getMainHandItem())) {
            holding = CHANNELLED.contains(MagicWandItem.getSelectedSpell(local.getMainHandItem()));
        }
        holdWeight = Mth.clamp(holdWeight + (holding ? 0.25F : -0.2F), 0.0F, 1.0F);
    }

    /**
     * A beam from a non-player caster names the spell its wand has no selection for. A cast that started in the
     * last few ticks (its swing) takes the spell's gesture; with no such cast (Voldemort casts without a swing)
     * the beam starts one. Players are left to their swing and their wand's selected spell.
     */
    public static void onCasterBeam(LivingEntity caster, SpellType spell) {
        if (caster instanceof Player || spell == null || !MagicWandItem.isAnyWand(caster.getMainHandItem())) {
            return;
        }
        Cast previous = CASTS.get(caster.getId());
        if (previous != null && caster.tickCount - previous.start() <= 3) {
            CASTS.put(caster.getId(), new Cast(previous.start(), spell, forSpell(spell), previous.variant()));
        } else {
            Gesture gesture = forSpell(spell);
            CASTS.put(caster.getId(), new Cast(caster.tickCount, spell, gesture, nextVariant(caster, previous, gesture)));
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CASTS.clear();
        SWING_STATE.clear();
        holdWeight = holdWeightO = 0.0F;
    }

    // ---------------------------------------------------------------- the curve

    private static float smooth(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    // ---------------------------------------------------------------- third person

    /**
     * Called at the end of HumanoidModel.setupAnim for every humanoid model: poses the wand arm of a player
     * who is casting (or, for the local player, holding a channel). The arm is blended in from wherever vanilla
     * put it, pointed along the look, moved through the gesture, and blended back out; the body turns a little
     * with the stroke.
     */
    public static void applyThirdPerson(HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        if (ageInTicks == 0.0F) {
            return; // the first-person arm draw sets the model up with all zeros; WandCastAnimation poses that arm
        }
        if (!(entity instanceof Player player) || !MagicWandItem.isAnyWand(player.getMainHandItem())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && CastMotions.ownsRecentCast(player.getId(), minecraft.level.getGameTime())) {
            return; // the server-synced gesture (CastMotions, the same one Pensieve actors play) poses this cast
        }
        Cast cast = CASTS.get(player.getId());
        float[] g = cast == null ? null : castAngles(cast, ageInTicks - cast.start());
        float[] p;
        float weight;
        if (g != null) {
            p = g;
            weight = g[4];
        } else if (player == minecraft.player) {
            float partial = ageInTicks - player.tickCount;
            weight = localHoldWeight(Mth.clamp(partial, 0.0F, 1.0F));
            p = localHoldPose();
        } else {
            return;
        }
        if (weight <= 0.001F) {
            return;
        }
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        float side = right ? 1.0F : -1.0F;
        ModelPart arm = right ? model.rightArm : model.leftArm;
        float x = model.head.xRot - Mth.HALF_PI + p[0] * Mth.DEG_TO_RAD;
        float y = model.head.yRot - 0.1F * side + side * p[1] * 0.9F * Mth.DEG_TO_RAD;
        float z = side * p[2] * 0.5F * Mth.DEG_TO_RAD;
        arm.xRot = Mth.lerp(weight, arm.xRot, x);
        arm.yRot = Mth.lerp(weight, arm.yRot, y);
        arm.zRot = Mth.lerp(weight, arm.zRot, z);

        float twist = side * p[1] * 0.3F * Mth.DEG_TO_RAD * weight;
        if (Math.abs(twist) > 1.0E-4F) {
            model.body.yRot += twist;
            model.rightArm.z = Mth.sin(model.body.yRot) * 5.0F;
            model.rightArm.x = -Mth.cos(model.body.yRot) * 5.0F;
            model.leftArm.z = -Mth.sin(model.body.yRot) * 5.0F;
            model.leftArm.x = Mth.cos(model.body.yRot) * 5.0F;
            ModelPart other = right ? model.leftArm : model.rightArm;
            other.yRot += twist;
        }
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
