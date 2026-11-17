package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Every spell's gesture. A cast is instant (the beam is already on its way when anyone hears of it), so each
 * gesture is built around the release: a flick snaps through the line of aim, a slash cuts across it, a whip
 * comes down from overhead, a charm swishes round and flicks, Accio points and draws back, Depulso shoves, the
 * light spells hold the wand high. Third person poses the arm (relative to where the caster looks, the way the
 * vanilla bow pose is); first person turns the held wand the same way, scaled down so it stays on screen.
 *
 * <p>Keyframes are (tick, raise above the aim in radians, turn to the right in radians, push forward in model
 * pixels), eased between with smoothstep. A map, not a switch: switch-on-enum has failed to load in this mod's
 * render code before.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CastMotions {
    /** Player models are drawn at 15/16 scale. */
    private static final double PLAYER_SCALE = 0.9375;
    /** Shoulder to wand tip along the arm, blocks. */
    private static final double ARM_REACH = 1.12;
    private static final float FIRST_PERSON_SCALE = 0.55f;
    /** How long before its snap a gesture is shown from: the last sliver of the wind-up. */
    private static final float RELEASE_LEAD = 0.6f;

    /** A gesture: keyframes and how long the whole thing lasts before it lets go. */
    static final class Motion {
        final float[][] keys;
        final float length;
        /** The tick of the release: the snap, the cut, the strike that sends the spell. */
        final float snap;

        Motion(float length, float snap, float[]... keys) {
            this.keys = keys;
            this.length = length;
            this.snap = snap;
        }

        /** {raise, turn, push} at {@code t} ticks in. */
        float[] at(float t) {
            if (t <= keys[0][0]) {
                return new float[]{keys[0][1], keys[0][2], keys[0][3]};
            }
            for (int i = 0; i < keys.length - 1; i++) {
                float[] a = keys[i];
                float[] b = keys[i + 1];
                if (t <= b[0]) {
                    float f = AvadaChargePose.smoothstep(a[0], b[0], t);
                    return new float[]{Mth.lerp(f, a[1], b[1]), Mth.lerp(f, a[2], b[2]), Mth.lerp(f, a[3], b[3])};
                }
            }
            float[] last = keys[keys.length - 1];
            return new float[]{last[1], last[2], last[3]};
        }
    }

    private static final Motion FLICK = new Motion(14.0f, 1.5f,
            new float[]{0.0f, 0.35f, 0.05f, 0.0f}, new float[]{1.5f, -0.18f, 0.0f, 1.0f},
            new float[]{4.0f, -0.05f, 0.0f, 0.5f}, new float[]{10.0f, 0.0f, 0.0f, 0.0f});
    private static final Motion SLASH = new Motion(16.0f, 2.5f,
            new float[]{0.0f, 0.75f, -0.7f, 0.0f}, new float[]{2.5f, -0.45f, 0.55f, 1.5f},
            new float[]{6.0f, -0.35f, 0.45f, 1.0f}, new float[]{12.0f, 0.0f, 0.0f, 0.0f});
    private static final Motion THRUST = new Motion(14.0f, 1.5f,
            new float[]{0.0f, -0.2f, 0.0f, -2.0f}, new float[]{1.5f, 0.04f, 0.0f, 3.0f},
            new float[]{4.0f, -0.02f, 0.0f, 2.5f}, new float[]{11.0f, 0.0f, 0.0f, 0.0f});
    private static final Motion WHIP = new Motion(16.0f, 2.0f,
            new float[]{0.0f, 1.3f, 0.2f, 0.0f}, new float[]{2.0f, -0.55f, -0.1f, 1.5f},
            new float[]{4.5f, -0.25f, 0.0f, 0.5f}, new float[]{12.0f, 0.0f, 0.0f, 0.0f});
    private static final Motion RAISE = new Motion(20.0f, 2.5f,
            new float[]{0.0f, 0.4f, 0.0f, 0.0f}, new float[]{2.5f, 1.2f, 0.0f, 1.0f},
            new float[]{10.0f, 1.1f, 0.0f, 1.0f}, new float[]{16.0f, 0.25f, 0.0f, 0.0f});
    private static final Motion SWISH = new Motion(13.0f, 4.5f,
            new float[]{0.0f, 0.2f, -0.35f, 0.0f}, new float[]{1.5f, 0.55f, 0.0f, 0.5f},
            new float[]{3.0f, 0.25f, 0.35f, 0.5f}, new float[]{4.5f, -0.15f, -0.05f, 1.5f},
            new float[]{9.0f, 0.0f, 0.0f, 0.0f});
    private static final Motion PULL = new Motion(17.0f, 2.0f,
            new float[]{0.0f, 0.0f, 0.0f, 1.5f}, new float[]{2.0f, -0.05f, 0.0f, 2.0f},
            new float[]{6.0f, 0.5f, -0.25f, -2.5f}, new float[]{10.0f, 0.35f, -0.15f, -1.5f},
            new float[]{15.0f, 0.0f, 0.0f, 0.0f});
    private static final Motion TWIRL = new Motion(13.0f, 6.0f,
            new float[]{0.0f, 0.3f, -0.3f, 0.0f}, new float[]{1.5f, 0.6f, 0.1f, 0.5f},
            new float[]{3.0f, 0.2f, 0.4f, 0.5f}, new float[]{4.5f, 0.5f, -0.1f, 0.5f},
            new float[]{6.0f, 0.1f, -0.3f, 1.0f}, new float[]{10.0f, 0.0f, 0.0f, 0.0f});
    private static final Motion SHOVE = new Motion(14.0f, 2.0f,
            new float[]{0.0f, 0.1f, 0.0f, -1.5f}, new float[]{2.0f, 0.02f, 0.0f, 3.5f},
            new float[]{6.0f, 0.0f, 0.0f, 3.0f}, new float[]{12.0f, 0.0f, 0.0f, 0.0f});

    private static final Map<SpellType, Motion> MOTIONS = new EnumMap<>(SpellType.class);

    static {
        for (SpellType spell : new SpellType[]{SpellType.STUPEFY, SpellType.EXPELLIARMUS, SpellType.PETRIFICUS_TOTALUS,
                SpellType.GLACIUS, SpellType.CRUCIO, SpellType.IMPERIO, SpellType.REVELIO, SpellType.CAVE_INIMICUM}) {
            MOTIONS.put(spell, FLICK);
        }
        for (SpellType spell : new SpellType[]{SpellType.SECTUMSEMPRA, SpellType.DIFFINDO}) {
            MOTIONS.put(spell, SLASH);
        }
        for (SpellType spell : new SpellType[]{SpellType.BOMBARDA, SpellType.CONFRINGO, SpellType.INCENDIO,
                SpellType.FIENDFYRE}) {
            MOTIONS.put(spell, THRUST);
        }
        for (SpellType spell : new SpellType[]{SpellType.INCARCEROUS, SpellType.DOLOHOV_CURSE,
                SpellType.SERPENSORTIA, SpellType.LEVICORPUS, SpellType.TENEBRIS_FLIGHT}) {
            MOTIONS.put(spell, WHIP);
        }
        for (SpellType spell : new SpellType[]{SpellType.EXPECTO_PATRONUM, SpellType.LUMOS, SpellType.MORSMORDRE,
                SpellType.ASCENDIO, SpellType.HEAL, SpellType.PROTEGO, SpellType.PROTEGO_MAXIMA,
                SpellType.APPARITION, SpellType.PIERTOTUM_LOCOMOTOR}) {
            MOTIONS.put(spell, RAISE);
        }
        for (SpellType spell : new SpellType[]{SpellType.WINGARDIUM_LEVIOSA, SpellType.ALOHOMORA, SpellType.REPARO,
                SpellType.OBLIVIATE, SpellType.COLLOPORTUS, SpellType.ALATUS}) {
            MOTIONS.put(spell, SWISH);
        }
        MOTIONS.put(SpellType.ACCIO, PULL);
        MOTIONS.put(SpellType.CONFUNDO, TWIRL);
        MOTIONS.put(SpellType.RIDDIKULUS, TWIRL);
        MOTIONS.put(SpellType.DEPULSO, SHOVE);
        MOTIONS.put(SpellType.ARRESTO_MOMENTUM, SHOVE);
        MOTIONS.put(SpellType.AQUA_ERUCTO, SHOVE);
    }

    /** A gesture being played by a caster. */
    public static final class Cast {
        final Motion motion;
        final SpellType spell;
        final double start;

        Cast(Motion motion, SpellType spell, double start) {
            this.motion = motion;
            this.spell = spell;
            this.start = start;
        }

        float age(double now) {
            return (float) (now - start);
        }

        boolean over(double now) {
            return age(now) > motion.length;
        }

        /** {raise, turn, push, weight}: eases in over a tick, out over the last four. */
        float[] pose(double now) {
            float t = age(now);
            float[] p = motion.at(t);
            float weight = AvadaChargePose.smoothstep(0.0f, 1.0f, t + 0.5f)
                    * (1.0f - AvadaChargePose.smoothstep(motion.length - 4.0f, motion.length, t));
            return new float[]{p[0], p[1], p[2], weight};
        }

        public SpellType spell() {
            return spell;
        }
    }

    private static final Map<Integer, Cast> CASTS = new HashMap<>();
    /** When each caster's latest gesture started, kept a little past its end (see {@link #ownsRecentCast}). */
    private static final Map<Integer, Double> LAST_START = new HashMap<>();
    /** How long after a gesture starts this class still owns the caster's arm. */
    private static final double OWNERSHIP_TICKS = 30.0;

    private CastMotions() {
    }

    public static void accept(SpellPackets.CastMotionPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        SpellType spell = SpellType.fromId(packet.spellId());
        if (minecraft.level == null || spell == null) {
            return;
        }
        Motion motion = MOTIONS.getOrDefault(spell, FLICK);
        // The spell is already on its way: start the gesture just short of its release, so the snap lands with
        // the beam leaving the wand instead of a beat after it.
        CASTS.put(packet.casterId(), new Cast(motion, spell, minecraft.level.getGameTime() - (motion.snap - RELEASE_LEAD)));
        LAST_START.put(packet.casterId(), (double) minecraft.level.getGameTime());
    }

    /** Drop gestures that are over for casters never drawn again (walked off, logged out). */
    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || minecraft.level == null || CASTS.isEmpty()) {
            return;
        }
        double now = minecraft.level.getGameTime();
        CASTS.values().removeIf(cast -> cast.over(now) || cast.age(now) < 0.0f);
        LAST_START.values().removeIf(start -> now - start > OWNERSHIP_TICKS || now < start);
    }

    /**
     * Whether a gesture from the server started for {@code entityId} lately. A player's third-person arm then
     * belongs to this class, and the swing-driven CastGestures pose stays off it for the whole cast, so the two
     * never fight over one arm or hand over mid-swing.
     */
    public static boolean ownsRecentCast(int entityId, double now) {
        Double start = LAST_START.get(entityId);
        return start != null && now - start <= OWNERSHIP_TICKS && now >= start;
    }

    /** The gesture {@code entityId} is playing now, or null. */
    public static Cast get(int entityId, double now) {
        Cast cast = CASTS.get(entityId);
        if (cast != null && cast.over(now)) {
            CASTS.remove(entityId);
            return null;
        }
        return cast;
    }

    public static boolean isEmpty() {
        return CASTS.isEmpty();
    }

    public static Iterable<Map.Entry<Integer, Cast>> all() {
        return CASTS.entrySet();
    }

    /** Third person: pose the wand arm. Right after the renderer's setupAnim (ActorPoseMixin). */
    public static void apply(EntityModel<?> model, Entity entity, float partial) {
        if (CASTS.isEmpty() || !(model instanceof HumanoidModel<?> humanoid) || !(entity instanceof Player)) {
            return;
        }
        Cast cast = get(entity.getId(), AvadaChargeClientState.now(partial));
        if (cast == null) {
            return;
        }
        float[] p = cast.pose(AvadaChargeClientState.now(partial));
        float weight = p[3];
        if (weight <= 0.0f) {
            return;
        }
        // Pointing along the gaze is the vanilla bow pose; the gesture is laid on top of it.
        float armX = humanoid.head.xRot - Mth.HALF_PI - p[0];
        float armY = humanoid.head.yRot - 0.06f + p[1];
        humanoid.rightArm.xRot = Mth.lerp(weight, humanoid.rightArm.xRot, armX);
        humanoid.rightArm.yRot = Mth.lerp(weight, humanoid.rightArm.yRot, armY);
        humanoid.rightArm.zRot = Mth.lerp(weight, humanoid.rightArm.zRot, 0.0f);
        humanoid.rightArm.z = humanoid.rightArm.z - p[2] * weight;
        // The off hand comes out a little for balance.
        float brace = weight * 0.6f;
        humanoid.leftArm.xRot = Mth.lerp(brace, humanoid.leftArm.xRot, 0.25f);
        humanoid.leftArm.zRot = Mth.lerp(brace, humanoid.leftArm.zRot, 0.22f);
        if (model instanceof PlayerModel<?> player) {
            player.rightSleeve.copyFrom(humanoid.rightArm);
            player.leftSleeve.copyFrom(humanoid.leftArm);
        }
    }

    /**
     * Where the wand tip of a caster playing a gesture is, in world space: at the end of the posed arm, or for
     * your own first-person view where the held wand shows on screen.
     */
    public static Vec3 wandTip(LivingEntity caster, Cast cast, float partial, boolean firstPerson) {
        double now = AvadaChargeClientState.now(partial);
        float[] p = cast.pose(now);
        double x = Mth.lerp(partial, caster.xo, caster.getX());
        double y = Mth.lerp(partial, caster.yo, caster.getY());
        double z = Mth.lerp(partial, caster.zo, caster.getZ());
        if (!(caster instanceof Player)) {
            // An NPC poses its own arm (its model aims the wand at its target); the gesture turns that aim.
            float pitch = caster.getViewXRot(partial) - p[0] * p[3] * Mth.RAD_TO_DEG * 0.6f;
            float yaw = Mth.rotLerp(partial, caster.yHeadRotO, caster.yHeadRot) + p[1] * p[3] * Mth.RAD_TO_DEG * 0.6f;
            Vec3 look = caster.getViewVector(partial);
            Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
            right = right.lengthSqr() < 1.0E-4 ? Vec3.ZERO : right.normalize();
            Vec3 shoulder = new Vec3(x, y + caster.getEyeHeight() - 0.35, z).add(right.scale(0.32));
            return shoulder.add(Vec3.directionFromRotation(pitch, yaw).scale(0.95));
        }
        if (firstPerson) {
            // Your own view: the wand the first-person arm (FirstPersonWandRig) is drawing right now.
            Vec3 drawnTip = WandCastAnimation.localTipInWorld(Minecraft.getInstance().gameRenderer.getMainCamera());
            if (drawnTip != null) {
                return drawnTip;
            }
            Vec3 look = caster.getViewVector(partial);
            Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
            right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
            Vec3 up = right.cross(look).normalize();
            float s = FIRST_PERSON_SCALE * p[3];
            return new Vec3(x, y + caster.getEyeHeight(), z).add(look.scale(0.62)).add(right.scale(0.30 + 0.25 * p[1] * s))
                    .add(up.scale(-0.20 + 0.3 * p[0] * s));
        }
        float bodyYaw = Mth.rotLerp(partial, caster.yBodyRotO, caster.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 bodyRight = new Vec3(-Mth.cos(bodyYaw), 0.0, -Mth.sin(bodyYaw));
        Vec3 bodyForward = new Vec3(-Mth.sin(bodyYaw), 0.0, Mth.cos(bodyYaw));
        double shoulderHeight = (caster.isCrouching() ? 18.8 : 22.0) / 16.0 * PLAYER_SCALE;
        Vec3 shoulder = new Vec3(x, y + shoulderHeight, z).add(bodyRight.scale(5.0 / 16.0 * PLAYER_SCALE))
                .add(bodyForward.scale(p[2] * p[3] / 16.0 * PLAYER_SCALE));
        float pitch = caster.getViewXRot(partial) - p[0] * p[3] * Mth.RAD_TO_DEG;
        float yaw = Mth.rotLerp(partial, caster.yHeadRotO, caster.yHeadRot) + p[1] * p[3] * Mth.RAD_TO_DEG;
        return shoulder.add(Vec3.directionFromRotation(pitch, yaw).scale(ARM_REACH));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CASTS.clear();
    }
}
