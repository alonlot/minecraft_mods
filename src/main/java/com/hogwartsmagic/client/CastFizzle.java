package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.MagicParticleOptions;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.client.fx.FxClient;
import com.hogwartsmagic.fx.FxKinds;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.CastFizzles;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A cast that would not come. Two failures, two moves:
 * <ul>
 * <li><b>Cooldown, the sputter</b>: the wand jabs for the crosshair, chokes on it (the tip coughs a few grey sparks
 * and a wisp of smoke, its light stutters), and the wrist gives it an impatient tap, as you would a pen that will
 * not write.</li>
 * <li><b>No mana, the droop</b>: the wand comes up to cast and strains there, trembling, a faint light trying at the
 * tip; it gutters out, and the arm sags, the tip hanging low and dripping the last of your mana, before it comes
 * back up. The mana bar flashes.</li>
 * </ul>
 * Your own is played the moment you press (the client knows your cooldowns and mana) and the server is told so
 * the players round you see it (spell.CastFizzles); theirs arrive as {@link FxKinds#CAST_FIZZLE}. Your first-person
 * arm is posed here through FirstPersonWand, everyone's third-person arm through CastMotions. Visual only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CastFizzle {

    /** How long each move lasts, ticks. */
    static final float SPUTTER_LENGTH = 14.0F;
    static final float DROOP_LENGTH = 24.0F;

    /**
     * Your first-person moves, as offsets from the arm's pose: {tick, hand dx, dy, dz, wand pitch (radians, up),
     * wand yaw (radians, right), shake}. Eased between with smoothstep; the shake is a fast tremble on top.
     */
    private static final float[][] SPUTTER = {
            {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
            {1.6F, -0.07F, 0.035F, -0.09F, 0.12F, -0.04F, 0.0F},   // the jab for the crosshair
            {2.4F, -0.05F, 0.02F, -0.06F, -0.06F, -0.02F, 1.0F},   // it chokes: stopped dead, shuddering
            {4.5F, -0.04F, 0.015F, -0.045F, 0.04F, 0.0F, 0.4F},
            {5.6F, -0.04F, 0.05F, -0.04F, 0.24F, 0.0F, 0.0F},      // the wrist comes up...
            {6.5F, -0.045F, -0.025F, -0.05F, -0.34F, 0.0F, 0.3F},  // ...and taps it down, impatient
            {8.0F, -0.03F, 0.0F, -0.03F, 0.03F, 0.0F, 0.0F},
            {SPUTTER_LENGTH, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
    };
    private static final float[][] DROOP = {
            {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
            {3.0F, -0.05F, 0.08F, -0.07F, 0.28F, -0.03F, 0.2F},    // up to cast
            {6.0F, -0.05F, 0.075F, -0.07F, 0.25F, -0.03F, 0.9F},   // straining, trembling: nothing comes
            {8.0F, -0.04F, 0.05F, -0.05F, 0.12F, 0.0F, 0.6F},      // it falters
            {13.0F, 0.03F, -0.13F, 0.02F, -0.55F, 0.10F, 0.15F},   // and sags, the tip hanging
            {18.5F, 0.035F, -0.15F, 0.03F, -0.62F, 0.12F, 0.1F},
            {DROOP_LENGTH, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
    };

    /** One caster's fizzle. {@code start} is the level's game time it began; {@code fired} the beats already played. */
    private static final class Fizzle {
        final int kind;
        final SpellType spell;
        final double start;
        int fired;

        Fizzle(int kind, SpellType spell, double start) {
            this.kind = kind;
            this.spell = spell;
            this.start = start;
        }

        float length() {
            return kind == CastFizzles.DRAINED ? DROOP_LENGTH : SPUTTER_LENGTH;
        }
    }

    private static final Map<Integer, Fizzle> ACTIVE = new HashMap<>();
    /** The use key has been let go since your last fizzle: a held key fizzles once, not every repeat. */
    private static boolean useReleased = true;
    /** Game time the mana bar last flashed (a drained fizzle of yours). */
    private static double manaFlashAt = -1000.0;
    /** Game time the server last told you a cast of yours went off (CastMotions). */
    private static double lastServerCast = -1000.0;
    /**
     * A refusal this soon after a cast that went off is a held key's repeat or a duplicate packet of that cast, not a
     * press of its own: it must not cut the real gesture short.
     */
    private static final double ECHO_TICKS = 15.0;

    private CastFizzle() {
    }

    /** Hooked up from ClientModEvents.onClientSetup (nested mod-bus setup listeners never fire). */
    public static void registerFx() {
        FxClient.register(FxKinds.CAST_FIZZLE, e -> {
            int kind = (int) e.param(0, 0.0F);
            int ordinal = (int) e.param(1, -1.0F);
            SpellType[] all = SpellType.values();
            SpellType spell = ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && e.entityId() == minecraft.player.getId()) {
                // The server refused a cast your client let through: the gesture you started becomes the fizzle.
                if (minecraft.level != null && minecraft.level.getGameTime() - lastServerCast < ECHO_TICKS) {
                    return; // the repeat of a cast that went off
                }
                Fizzle own = ACTIVE.get(e.entityId());
                if (own == null || own.kind != kind || age(own, 0.0F) > own.length() - 2.0F) {
                    start(minecraft.player, spell, kind);
                }
                return;
            }
            if (e.entity() instanceof LivingEntity caster && minecraft.level != null) {
                Fizzle old = ACTIVE.get(caster.getId());
                if (old != null && old.kind == kind && age(old, 0.0F) < old.length() - 4.0F) {
                    return; // still playing: a held key's repeats do not restart it
                }
                start(caster, spell, kind);
            }
        });
    }

    // ---------------------------------------------------------------- your own press

    /**
     * Whether this use-key press is a fresh one (the key was up since the last), and marks it taken. Every press of
     * the use key on a wand takes it, cast or not, so the key's repeats while held (every 4 ticks) never fizzle: a
     * held click after a cast that went off would otherwise sputter on that cast's own new cooldown.
     */
    public static boolean takeFreshUse() {
        boolean fresh = useReleased;
        useReleased = false;
        return fresh;
    }

    /** The server says a cast of yours went off (its gesture packet). */
    static void noteServerCast() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            lastServerCast = minecraft.level.getGameTime();
        }
    }

    /** A real cast of yours began: any fizzle still playing on your arm gives way to it. */
    static void clearOwn() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            ACTIVE.remove(player.getId());
        }
    }

    /**
     * Your wand's {@code spell} cannot be cast for {@code kind} (CastFizzles.COOLDOWN or DRAINED): play the fizzle
     * and tell the server, so the players round you see it.
     */
    public static void predict(Player player, SpellType spell, int kind) {
        if (player != Minecraft.getInstance().player) {
            return;
        }
        Fizzle old = ACTIVE.get(player.getId());
        if (old != null && old.kind == kind && age(old, 0.0F) < 5.0F) {
            return; // a key mashed: one fizzle at a time
        }
        start(player, spell, kind);
        SpellPackets.CHANNEL.send(new SpellPackets.CastFizzleNoticePacket((byte) kind), PacketDistributor.SERVER.noArg());
    }

    private static void start(LivingEntity caster, SpellType spell, int kind) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        double now = minecraft.level.getGameTime();
        ACTIVE.put(caster.getId(), new Fizzle(kind, spell, now));
        if (caster instanceof Player) {
            CastMotions.putFizzle(caster.getId(), spell, kind == CastFizzles.DRAINED, now);
        }
        if (caster == minecraft.player) {
            FirstPersonCast.cancel(); // a swing the server turned down: its strike never lands
            if (kind == CastFizzles.DRAINED) {
                manaFlashAt = now;
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.keyUse.isDown()) {
            useReleased = true;
        }
        ClientLevel level = minecraft.level;
        if (level == null || ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Integer, Fizzle>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Fizzle> entry = it.next();
            Fizzle fizzle = entry.getValue();
            float age = age(fizzle, 0.0F);
            Entity entity = level.getEntity(entry.getKey());
            if (age > fizzle.length() || age < -1.0F || !(entity instanceof LivingEntity caster) || caster.isRemoved()) {
                it.remove();
                continue;
            }
            beats(level, caster, fizzle, age);
        }
    }

    /** Ticks into {@code fizzle}, at {@code partialTick} into this one. */
    private static float age(Fizzle fizzle, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 1000.0F : (float) (minecraft.level.getGameTime() + partialTick - fizzle.start);
    }

    // ---------------------------------------------------------------- sound and sparks

    /** Plays each beat of the move once its tick has come: the whoosh, the cough, the tap, the guttering, the drips. */
    private static void beats(ClientLevel level, LivingEntity caster, Fizzle f, float age) {
        boolean hidden = Minecraft.getInstance().player != null && caster.isInvisibleTo(Minecraft.getInstance().player);
        RandomSource random = level.random;
        if (f.kind == CastFizzles.COOLDOWN) {
            if (beat(f, 0, age, 0.0F)) {
                sound(caster, ModSounds.CAST_SWISH_LIGHT.get(), 0.28F, 1.1F, hidden);
            }
            if (beat(f, 1, age, 1.8F)) {
                sound(caster, ModSounds.SPELL_FIZZLE.get(), 0.32F, 1.55F + random.nextFloat() * 0.1F, hidden);
                if (!hidden) {
                    cough(level, caster, f.spell, 5, 0.075F, 2);
                }
            }
            if (beat(f, 2, age, 3.3F) && !hidden) {
                cough(level, caster, f.spell, 2, 0.05F, 0);
            }
            if (beat(f, 3, age, 6.5F)) {
                sound(caster, SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.22F, 1.9F, hidden);
                if (!hidden) {
                    cough(level, caster, f.spell, 1, 0.04F, 1);
                }
            }
            return;
        }
        if (beat(f, 0, age, 0.0F)) {
            sound(caster, ModSounds.CAST_SWISH_RISE.get(), 0.22F, 0.8F, hidden);
        }
        if (hidden) {
            if (beat(f, 1, age, 7.5F)) {
                sound(caster, ModSounds.OLLIVANDERS_FAIL_FIZZLE.get(), 0.36F, 0.78F, true);
            }
            return;
        }
        int mana = manaColor();
        // Straining: a faint light tries at the tip, a mote at a time.
        for (int i = 0; i < 5; i++) {
            if (beat(f, 8 + i, age, 3.0F + i) && random.nextFloat() < 0.7F) {
                Vec3 tip = tip(caster);
                level.addParticle(MagicParticleOptions.mote(mana, 0.28F), tip.x, tip.y, tip.z,
                        (random.nextFloat() - 0.5F) * 0.01F, 0.004F, (random.nextFloat() - 0.5F) * 0.01F);
            }
        }
        if (beat(f, 1, age, 7.5F)) {
            // It gutters out.
            sound(caster, ModSounds.OLLIVANDERS_FAIL_FIZZLE.get(), 0.36F, 0.78F, false);
            Vec3 tip = tip(caster);
            for (int i = 0; i < 2; i++) {
                level.addParticle(MagicParticleOptions.smoke(0.42F, 0.48F, 0.6F, 0.22F), tip.x, tip.y, tip.z,
                        (random.nextFloat() - 0.5F) * 0.006F, 0.008F, (random.nextFloat() - 0.5F) * 0.006F);
            }
        }
        // The last of the mana drips off the hanging tip.
        float[] drips = {10.0F, 12.5F, 15.0F, 17.0F};
        for (int i = 0; i < drips.length; i++) {
            if (beat(f, 2 + i, age, drips[i])) {
                Vec3 tip = tip(caster);
                level.addParticle(MagicParticleOptions.spark(mana, 0.26F), tip.x, tip.y - 0.03, tip.z,
                        0.0, -0.01, 0.0);
            }
        }
    }

    /** Whether beat {@code bit} is due at {@code age} and not yet played; marks it played. */
    private static boolean beat(Fizzle f, int bit, float age, float at) {
        int mask = 1 << bit;
        if (age < at || (f.fired & mask) != 0) {
            return false;
        }
        f.fired |= mask;
        return true;
    }

    /** The choke: a few dull sparks spat from the tip, and {@code smoke} wisps of grey. */
    private static void cough(ClientLevel level, LivingEntity caster, SpellType spell, int sparks, float speed,
                              int smoke) {
        RandomSource random = level.random;
        Vec3 tip = tip(caster);
        Vec3 look = caster.getViewVector(1.0F);
        float[] c = FirstPersonWand.lightColor(spell);
        // The spell's light gone dull: most of the way to ash grey.
        float r = Mth.lerp(0.6F, c[0], 0.62F);
        float g = Mth.lerp(0.6F, c[1], 0.6F);
        float b = Mth.lerp(0.6F, c[2], 0.58F);
        for (int i = 0; i < sparks; i++) {
            Vec3 v = new Vec3(random.nextGaussian(), random.nextGaussian() + 0.6, random.nextGaussian()).normalize()
                    .scale(speed * (0.5F + random.nextFloat())).add(look.scale(0.03));
            level.addParticle(MagicParticleOptions.spark(r, g, b, 0.32F), tip.x, tip.y, tip.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < smoke; i++) {
            level.addParticle(MagicParticleOptions.smoke(0.5F, 0.5F, 0.52F, 0.2F), tip.x, tip.y, tip.z,
                    look.x * 0.01 + (random.nextFloat() - 0.5F) * 0.006, 0.01, look.z * 0.01);
        }
    }

    /** The mana bar's colour, for the drained light (a Corrupted caster's mana is dark too). */
    private static int manaColor() {
        return com.hogwartsmagic.spell.dark.DarkRules.manaBarColor(DarkMagicClientState.tier()) & 0xFFFFFF;
    }

    /** Where {@code caster}'s wand tip is now: as your first-person arm draws it, or at the end of the posed arm. */
    private static Vec3 tip(LivingEntity caster) {
        Minecraft minecraft = Minecraft.getInstance();
        if (caster == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
            Camera camera = minecraft.gameRenderer.getMainCamera();
            Vec3 drawn = FirstPersonWand.localTipInWorld(camera);
            if (drawn != null) {
                return drawn;
            }
        }
        Vec3 drawn = WandSwipeLayer.recentTip(caster);
        if (drawn != null) {
            return drawn;
        }
        CastMotions.Cast cast = CastMotions.get(caster.getId(), AvadaChargeClientState.now(1.0F));
        if (cast != null) {
            return CastMotions.wandTip(caster, cast, 1.0F, false);
        }
        return caster.getEyePosition().add(caster.getViewVector(1.0F).scale(0.8)).add(0.0, -0.3, 0.0);
    }

    private static void sound(LivingEntity caster, SoundEvent sound, float volume, float pitch, boolean hidden) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || com.hogwartsmagic.client.pensieve.PensieveClientState.active()) {
            return;
        }
        // A cloaked caster is heard, faintly, but not seen.
        minecraft.level.playLocalSound(caster.getX(), caster.getEyeY() - 0.3, caster.getZ(), sound,
                SoundSource.PLAYERS, hidden ? volume * 0.5F : volume, pitch, false);
    }

    // ---------------------------------------------------------------- your first-person arm

    /** Your own fizzle playing now, or null. */
    private static Fizzle own() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        Fizzle f = ACTIVE.get(player.getId());
        return f != null && age(f, 0.0F) <= f.length() ? f : null;
    }

    /** Whether your arm is fizzling (FirstPersonCast keeps the channel hold pose down meanwhile). */
    static boolean ownActive() {
        return own() != null;
    }

    /** Your arm's {@code pose} with the fizzle laid on (changed in place); untouched when none is playing. */
    static void applyFirstPerson(FirstPersonWandRig.Key pose, float partialTick) {
        Fizzle f = own();
        if (f == null) {
            return;
        }
        float t = age(f, partialTick);
        if (t < 0.0F || t > f.length()) {
            return;
        }
        float[] o = sample(f.kind == CastFizzles.DRAINED ? DROOP : SPUTTER, t);
        float shake = o[6];
        float n1 = 0.5F * (Mth.sin(t * 9.3F) + Mth.sin(t * 15.7F + 1.1F));
        float n2 = 0.5F * (Mth.sin(t * 11.1F + 2.3F) + Mth.sin(t * 17.3F));
        pose.hand().add(o[1] + n1 * 0.005F * shake, o[2] + n2 * 0.005F * shake, o[3]);
        Vector3f wand = pose.wand().normalize();
        Vector3f right = new Vector3f(wand).cross(0.0F, 1.0F, 0.0F);
        if (right.lengthSquared() > 1.0E-6F) {
            right.normalize();
            wand.rotateAxis(o[4] + n2 * 0.04F * shake, right.x, right.y, right.z);
        }
        wand.rotateAxis(-(o[5] + n1 * 0.04F * shake), 0.0F, 1.0F, 0.0F).normalize();
    }

    /**
     * How bright your wand tip may glow through the fizzle, 1 when none is playing: the sputter's light stutters as
     * it chokes, the drained light flickers weakly and goes out until the arm comes back up.
     */
    static float tipGlow(float partialTick) {
        Fizzle f = own();
        if (f == null) {
            return 1.0F;
        }
        float t = age(f, partialTick);
        float flicker = (Mth.sin(t * 13.7F) + Mth.sin(t * 23.1F + 0.7F)) > 0.4F ? 1.0F : 0.15F;
        if (f.kind == CastFizzles.COOLDOWN) {
            if (t < 1.6F) {
                return 1.0F;
            }
            if (t < 4.5F) {
                return 0.25F * flicker;
            }
            return Mth.lerp(AvadaChargePose.smoothstep(4.5F, SPUTTER_LENGTH, t), 0.25F, 1.0F);
        }
        if (t < 3.0F) {
            return 0.6F;
        }
        if (t < 7.5F) {
            return 0.45F * flicker * (1.0F - (t - 3.0F) / 6.0F);
        }
        if (t < 18.5F) {
            return 0.0F;
        }
        return AvadaChargePose.smoothstep(18.5F, DROOP_LENGTH, t);
    }

    /** How strongly the mana bar flashes red now, 0..1. */
    public static float manaFlash(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return 0.0F;
        }
        float t = (float) (minecraft.level.getGameTime() + partialTick - manaFlashAt);
        if (t < 0.0F || t > 20.0F) {
            return 0.0F;
        }
        // Two pulses, the second weaker.
        float pulse = Mth.abs(Mth.sin(t * Mth.PI / 8.0F));
        return pulse * (1.0F - t / 20.0F);
    }

    /** The keyed offsets {@code t} ticks in. */
    private static float[] sample(float[][] keys, float t) {
        float[] out = new float[7];
        for (int i = 0; i < keys.length - 1; i++) {
            float[] a = keys[i];
            float[] b = keys[i + 1];
            if (t <= b[0]) {
                float k = AvadaChargePose.smoothstep(a[0], b[0], t);
                for (int j = 1; j < 7; j++) {
                    out[j] = Mth.lerp(k, a[j], b[j]);
                }
                return out;
            }
        }
        return out;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
        useReleased = true;
        manaFlashAt = -1000.0;
        lastServerCast = -1000.0;
    }
}
