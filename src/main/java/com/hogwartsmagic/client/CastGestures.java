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
 * the same curve drives the first-person arm (WandCastAnimation) and the player model seen by everyone else
 * (third person, through the HumanoidModel mixin). Every player's swing with a wand in the main hand starts
 * the gesture of the spell selected on that wand; every cast path swings the main hand, and the swing is
 * synced to other clients, so remote players animate too. Visual only.
 *
 * Pose numbers are {pitch, yaw, roll, reach}: pitch in degrees (negative raises the wand), yaw in degrees
 * (positive sweeps across the body towards the crosshair), roll in degrees (a twist of the wrist), reach in
 * blocks of the first-person view (negative thrusts forward).
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CastGestures {

    /** One wand gesture. Keys are {t, pitch, yaw, roll, reach}; rest (all zero) is implied at t = 0 and t = 1. */
    public static final class Gesture {
        public final String name;
        public final float ticks;
        /** Progress at which the spell visibly leaves the wand (the tip flare). */
        public final float release;
        final float[][] keys;
        /** Pose while a channelled spell is held after the gesture. */
        final float[] hold;

        Gesture(String name, float ticks, float release, float[] hold, float[]... keys) {
            this.name = name;
            this.ticks = ticks;
            this.release = release;
            this.hold = hold;
            this.keys = keys;
        }
    }

    private static final float[] POINT = {-6.0F, 14.0F, 0.0F, -0.28F};
    private static final float[] RAISED = {-48.0F, 8.0F, 0.0F, -0.12F};

    /** Wind up high and to the side, then a fast diagonal slash across to the crosshair. The default. */
    public static final Gesture FLICK = new Gesture("flick", 9, 0.36F, POINT,
            new float[]{0.2F, -34, -22, -14, 0.10F},
            new float[]{0.4F, -8, 34, 14, -0.36F});
    /** Draw back, then a straight jab at the target, held a moment: the duelling stunner. */
    public static final Gesture STAB = new Gesture("stab", 8, 0.34F, POINT,
            new float[]{0.22F, -14, -6, 0, 0.20F},
            new float[]{0.38F, 2, 14, 0, -0.55F},
            new float[]{0.58F, 2, 12, 0, -0.48F});
    /** A big cut from high right to low left. */
    public static final Gesture SLASH = new Gesture("slash", 10, 0.42F, POINT,
            new float[]{0.24F, -52, -30, -26, 0.06F},
            new float[]{0.46F, 30, 44, 30, -0.26F});
    /** Two cuts in a Z: high right to low left, back across, and down again. */
    public static final Gesture DOUBLE_SLASH = new Gesture("double_slash", 15, 0.33F, POINT,
            new float[]{0.16F, -50, -30, -25, 0.05F},
            new float[]{0.32F, 28, 40, 25, -0.20F},
            new float[]{0.50F, -32, 40, -12, -0.10F},
            new float[]{0.68F, 32, -8, 12, -0.26F});
    /** The wand pulled in to the chest to gather force, then a heavy shove. */
    public static final Gesture PUSH = new Gesture("push", 12, 0.44F, POINT,
            new float[]{0.28F, -12, 8, 0, 0.32F},
            new float[]{0.44F, 0, 18, 0, -0.62F},
            new float[]{0.62F, 0, 16, 0, -0.55F});
    /** The wand lifted high overhead and held up. */
    public static final Gesture RAISE = new Gesture("raise", 14, 0.36F, RAISED,
            new float[]{0.34F, -70, 10, 0, -0.10F},
            new float[]{0.66F, -66, 10, 0, -0.10F});
    /** Swish and flick: a small loop, then a sharp flick upwards. */
    public static final Gesture SWISH_FLICK = new Gesture("swish_flick", 14, 0.58F, new float[]{-30, 10, 0, -0.2F},
            new float[]{0.12F, -10, -16, -6, 0.0F},
            new float[]{0.24F, 10, 0, 0, -0.05F},
            new float[]{0.36F, -6, 16, 6, -0.05F},
            new float[]{0.48F, 12, 6, 0, -0.10F},
            new float[]{0.58F, -46, 10, 0, -0.14F});
    /** Two small circles, then a point: the mind charms and the binding. */
    public static final Gesture SPIRAL = new Gesture("spiral", 14, 0.64F, POINT,
            new float[]{0.08F, -14, 0, 0, 0.0F},
            new float[]{0.18F, 0, 16, 0, -0.04F},
            new float[]{0.28F, 14, 0, 0, -0.08F},
            new float[]{0.38F, 0, -10, 0, -0.04F},
            new float[]{0.48F, -14, 4, 0, -0.08F},
            new float[]{0.56F, 4, 16, 0, -0.10F},
            new float[]{0.64F, -2, 18, 0, -0.40F});
    /** Reach out at the target, then yank back towards the chest: Accio. */
    public static final Gesture PULL = new Gesture("pull", 11, 0.24F, POINT,
            new float[]{0.24F, 0, 16, 0, -0.48F},
            new float[]{0.52F, -24, 4, -8, 0.28F});
    /** Lifted back over the shoulder, then lashed forward and down like a whip. */
    public static final Gesture WHIP = new Gesture("whip", 11, 0.40F, POINT,
            new float[]{0.24F, -58, -16, -12, 0.16F},
            new float[]{0.40F, 24, 26, 10, -0.36F},
            new float[]{0.52F, 8, 20, 4, -0.26F});
    /** The killing stroke: the wand raised straight overhead, then driven down at the target and held. */
    public static final Gesture CURSE = new Gesture("curse", 13, 0.42F, POINT,
            new float[]{0.30F, -78, -10, -20, 0.20F},
            new float[]{0.42F, 18, 24, 10, -0.46F},
            new float[]{0.62F, 16, 22, 10, -0.42F});
    /** Point at the lock and turn the wrist like a key. */
    public static final Gesture TWIST = new Gesture("twist", 12, 0.46F, POINT,
            new float[]{0.24F, -2, 18, 0, -0.32F},
            new float[]{0.46F, -2, 18, 64, -0.32F},
            new float[]{0.62F, -2, 18, -16, -0.32F});

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
     */
    public record Variant(boolean mirror, float scale, float pitchOffset, float rollOffset, float shiftX, float shiftY) {
        public static final Variant NONE = new Variant(false, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F);

        /**
         * Where the whole arm moves on screen during the stroke (first person, in view units; x towards the
         * main-hand side). A mirrored stroke is made across the body, so the arm crosses over to the far side.
         */
        public float[] screenShift(float t) {
            if (t < 0.0F || t >= 1.0F) {
                return new float[2];
            }
            float env = smooth(t / 0.18F) * smooth((1.0F - t) / 0.45F);
            return new float[]{this.shiftX * env, this.shiftY * env};
        }

        /** Yaw a mirrored stroke swings around: the far side of the body is MIRROR_CENTRE degrees across. */
        private static final float MIRROR_CENTRE = 30.0F;

        /** The variant applied to one key pose {pitch, yaw, roll, reach}. */
        float[] apply(float[] key) {
            float yaw = this.mirror ? MIRROR_CENTRE - key[1] : key[1];
            float roll = this.mirror ? -key[2] : key[2];
            return new float[]{key[0] * this.scale + this.pitchOffset, yaw * this.scale,
                    roll * this.scale + this.rollOffset, key[3] * this.scale};
        }
    }

    /** The variant a player's next cast plays: mostly the other side from the last, never quite the same. */
    private static Variant nextVariant(Player player, Cast previous) {
        java.util.Random random = new java.util.Random(player.getId() * 341873128712L + player.tickCount * 132897987541L);
        boolean lastMirror = previous != null && previous.variant().mirror();
        boolean mirror = random.nextFloat() < 0.75F ? !lastMirror : lastMirror;
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

    public static Cast castOf(LivingEntity entity) {
        return CASTS.get(entity.getId());
    }

    /** The local player's hold weight this frame, eased between ticks. */
    public static float localHoldWeight(float partialTick) {
        return Mth.lerp(partialTick, holdWeightO, holdWeight);
    }

    /** The hold pose of the local player's selected spell. */
    public static float[] localHoldPose() {
        Player player = Minecraft.getInstance().player;
        SpellType spell = player == null ? null : MagicWandItem.getSelectedSpell(player.getMainHandItem());
        return forSpell(spell).hold;
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
        for (Player player : minecraft.level.players()) {
            int[] state = SWING_STATE.computeIfAbsent(player.getId(), id -> new int[2]);
            boolean swinging = player.swinging;
            if (swinging && (state[0] == 0 || player.swingTime < state[1])
                    && MagicWandItem.isAnyWand(player.getMainHandItem())) {
                SpellType spell = MagicWandItem.getSelectedSpell(player.getMainHandItem());
                CASTS.put(player.getId(), new Cast(player.tickCount, spell, forSpell(spell),
                        nextVariant(player, CASTS.get(player.getId()))));
            }
            state[0] = swinging ? 1 : 0;
            state[1] = player.swingTime;
        }
        CASTS.entrySet().removeIf(e -> minecraft.level.getEntity(e.getKey()) == null);
        SWING_STATE.keySet().removeIf(id -> minecraft.level.getEntity(id) == null);

        holdWeightO = holdWeight;
        Player local = minecraft.player;
        boolean holding = false;
        if (local != null && minecraft.options.keyUse.isDown() && MagicWandItem.isAnyWand(local.getMainHandItem())) {
            holding = CHANNELLED.contains(MagicWandItem.getSelectedSpell(local.getMainHandItem()));
        }
        holdWeight = Mth.clamp(holdWeight + (holding ? 0.25F : -0.2F), 0.0F, 1.0F);
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

    /** The pose {pitch, yaw, roll, reach} of a gesture at progress t (rest outside 0..1). */
    /** The pose of a cast at progress t, with its variant. */
    public static float[] pose(Cast cast, float t) {
        return pose(cast.gesture(), t, cast.variant());
    }

    public static float[] pose(Gesture gesture, float t) {
        return pose(gesture, t, Variant.NONE);
    }

    public static float[] pose(Gesture gesture, float t, Variant variant) {
        float[] out = new float[4];
        if (t < 0.0F || t >= 1.0F) {
            return out;
        }
        float prevT = 0.0F;
        float[] prev = out.clone();
        for (int i = 0; i <= gesture.keys.length; i++) {
            float keyT = i < gesture.keys.length ? gesture.keys[i][0] : 1.0F;
            float[] key = new float[4];
            if (i < gesture.keys.length) {
                System.arraycopy(gesture.keys[i], 1, key, 0, 4);
                key = variant.apply(key);
            }
            if (t <= keyT) {
                float k = smooth((t - prevT) / Math.max(1.0E-4F, keyT - prevT));
                for (int j = 0; j < 4; j++) {
                    out[j] = Mth.lerp(k, prev[j], key[j]);
                }
                return out;
            }
            prevT = keyT;
            prev = key;
        }
        return out;
    }

    /** Mixes two poses: 0 gives a, 1 gives b. */
    public static float[] mix(float[] a, float[] b, float k) {
        float[] out = new float[4];
        for (int i = 0; i < 4; i++) {
            out[i] = Mth.lerp(k, a[i], b[i]);
        }
        return out;
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
        Cast cast = CASTS.get(player.getId());
        float t = cast == null ? -1.0F : cast.progress(ageInTicks);
        float[] p;
        float weight;
        if (t >= 0.0F) {
            p = pose(cast, t);
            weight = smooth(t / 0.12F) * smooth((1.0F - t) / 0.3F);
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

    private CastGestures() {
    }
}
