package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Which gesture your own first-person arm plays, and when (FirstPersonWandRig draws it). A cast starts on your
 * own swing, the moment you press the key, so the strike can land as the spell leaves the wand; a cast the
 * server reports with no swing of yours (CastMotions' packet) starts then. Everyone else's arm is CastMotions'.
 * Every cast differs a little from the last ({@link Variant}). Visual only.
 *
 * <p>Maps and sets, not switches: switch-on-enum has failed to load in this mod's render code before.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class FirstPersonCast {

    /** One wand gesture: its name, its length in ticks and the fraction of it at which the spell is let go. */
    public static final class Gesture {
        public final String name;
        public final float ticks;
        public final float release;
        /** Whether a cast may mirror the stroke across the body. A straight lift has nothing to mirror. */
        final boolean mirrorable;

        Gesture(String name, float ticks, float release, boolean mirrorable) {
            this.name = name;
            this.ticks = ticks;
            this.release = release;
            this.mirrorable = mirrorable;
        }
    }

    public static final Gesture FLICK = new Gesture("flick", 9, 0.36F, true);
    public static final Gesture STAB = new Gesture("stab", 8, 0.34F, false);
    public static final Gesture SLASH = new Gesture("slash", 10, 0.42F, true);
    public static final Gesture DOUBLE_SLASH = new Gesture("double_slash", 13, 0.33F, true);
    public static final Gesture PUSH = new Gesture("push", 10, 0.44F, false);
    public static final Gesture RAISE = new Gesture("raise", 12, 0.36F, false);
    public static final Gesture SWISH_FLICK = new Gesture("swish_flick", 14, 0.58F, true);
    public static final Gesture SPIRAL = new Gesture("spiral", 14, 0.64F, false);
    public static final Gesture PULL = new Gesture("pull", 11, 0.24F, false);
    public static final Gesture WHIP = new Gesture("whip", 11, 0.40F, true);
    public static final Gesture CURSE = new Gesture("curse", 11, 0.42F, true);
    public static final Gesture TWIST = new Gesture("twist", 10, 0.46F, false);

    private static final Map<SpellType, Gesture> BY_SPELL = new EnumMap<>(SpellType.class);

    private static void put(Gesture gesture, SpellType... spells) {
        for (SpellType spell : spells) {
            BY_SPELL.put(spell, gesture);
        }
    }

    static {
        put(STAB, SpellType.STUPEFY, SpellType.EXPELLIARMUS, SpellType.PETRIFICUS_TOTALUS, SpellType.LEVICORPUS,
                SpellType.REVELIO);
        put(SLASH, SpellType.DIFFINDO, SpellType.CONFRINGO, SpellType.INCENDIO);
        put(DOUBLE_SLASH, SpellType.SECTUMSEMPRA);
        put(PUSH, SpellType.BOMBARDA, SpellType.DEPULSO, SpellType.ARRESTO_MOMENTUM, SpellType.PROTEGO);
        put(RAISE, SpellType.LUMOS, SpellType.EXPECTO_PATRONUM, SpellType.PROTEGO_MAXIMA, SpellType.MORSMORDRE,
                SpellType.ASCENDIO, SpellType.HEAL, SpellType.CAVE_INIMICUM, SpellType.PIERTOTUM_LOCOMOTOR,
                SpellType.TENEBRIS_FLIGHT, SpellType.APPARITION);
        put(SWISH_FLICK, SpellType.WINGARDIUM_LEVIOSA, SpellType.ALATUS, SpellType.RIDDIKULUS);
        put(SPIRAL, SpellType.CONFUNDO, SpellType.OBLIVIATE, SpellType.IMPERIO, SpellType.INCARCEROUS,
                SpellType.SERPENSORTIA, SpellType.REPARO);
        put(PULL, SpellType.ACCIO);
        put(WHIP, SpellType.GLACIUS, SpellType.AQUA_ERUCTO, SpellType.FIENDFYRE);
        put(CURSE, SpellType.AVADA_KEDAVRA, SpellType.CRUCIO, SpellType.DOLOHOV_CURSE);
        put(TWIST, SpellType.ALOHOMORA, SpellType.COLLOPORTUS);
    }

    public static Gesture forSpell(SpellType spell) {
        return spell == null ? FLICK : BY_SPELL.getOrDefault(spell, FLICK);
    }

    /** Spells that keep going while the cast key is held: the wand stays up in the gesture's hold pose. */
    private static final Set<SpellType> CHANNELLED = EnumSet.of(SpellType.FIENDFYRE, SpellType.EXPECTO_PATRONUM,
            SpellType.WINGARDIUM_LEVIOSA, SpellType.CRUCIO, SpellType.IMPERIO, SpellType.PROTEGO_MAXIMA);

    /**
     * How one cast differs from the last, so repeated casts never look the same: a mirrored cast winds up on the
     * far side and cuts back across, and each gets its own size, tilt (the stroke turned round the strike point,
     * radians) and place on screen.
     */
    public record Variant(boolean mirror, float scale, float pitchOffset, float shiftX, float shiftY, float tilt,
                          float place) {
        public static final Variant NONE = new Variant(false, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    /** A gesture being played by your own arm. {@code start} is your tick count when it began. */
    public record Cast(int start, SpellType spell, Gesture gesture, Variant variant) {
    }

    private static Cast current;
    private static boolean wasSwinging;
    private static int lastSwingTime;
    private static float holdWeight;
    private static float holdWeightO;

    private FirstPersonCast() {
    }

    /** Your current (or last) cast, or null. */
    static Cast current() {
        return current;
    }

    /** How far the channel hold pose is blended in this frame, 0..1. */
    static float holdWeight(float partialTick) {
        return Mth.lerp(partialTick, holdWeightO, holdWeight);
    }

    /** The spell whose hold pose the arm keeps while a channel is held. */
    static SpellType heldSpell() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? null : MagicWandItem.getSelectedSpell(player.getMainHandItem());
    }

    private static void start(LocalPlayer player, SpellType spell) {
        Gesture gesture = forSpell(spell);
        current = new Cast(player.tickCount, spell, gesture, nextVariant(player, current, gesture, spell));
        CastSounds.swing(player, spell);
    }

    /** The server says you cast {@code spell}: start its gesture unless your own swing already did. */
    static void onServerCast(SpellType spell) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (current != null && player.tickCount - current.start() <= 4 + CastDelay.ticks(current.spell())) {
            if (current.spell() != spell) { // the swing guessed the wand's spell; the server knows better
                current = new Cast(current.start(), spell, forSpell(spell), current.variant());
            }
            return;
        }
        start(player, spell);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            current = null;
            return;
        }
        boolean wand = MagicWandItem.isAnyWand(player.getMainHandItem());
        boolean swinging = player.swinging;
        // The server echoes your swing back a tick later, which restarts it: that is the same cast, not a new one.
        boolean echo = current != null && player.tickCount - current.start() <= 4 + CastDelay.ticks(current.spell());
        if (wand && swinging && !echo && (!wasSwinging || player.swingTime < lastSwingTime)) {
            start(player, MagicWandItem.getSelectedSpell(player.getMainHandItem()));
        }
        wasSwinging = swinging;
        lastSwingTime = player.swingTime;

        holdWeightO = holdWeight;
        boolean holding = wand && minecraft.options.keyUse.isDown()
                && CHANNELLED.contains(MagicWandItem.getSelectedSpell(player.getMainHandItem()));
        holdWeight = Mth.clamp(holdWeight + (holding ? 0.25F : -0.2F), 0.0F, 1.0F);
    }

    /** Mostly the other side from the last cast, never quite the same size, tilt or place. */
    private static Variant nextVariant(LocalPlayer player, Cast previous, Gesture gesture, SpellType spell) {
        java.util.Random random = new java.util.Random(player.getId() * 341873128712L + player.tickCount * 132897987541L);
        boolean lastMirror = previous != null && previous.variant().mirror();
        boolean mirror = gesture == TWIST ? spell == SpellType.COLLOPORTUS // locking turns the key the other way
                : gesture.mirrorable && (random.nextFloat() < 0.75F ? !lastMirror : lastMirror);
        // Hand-keyed, not generated: each value is one of two authored versions (A or B), never a random spread.
        float scale = random.nextBoolean() ? 0.9F : 1.1F;
        float pitch = random.nextBoolean() ? -11.0F : 11.0F;
        float shiftX = 0.0F;
        float shiftY = random.nextBoolean() ? -0.08F : 0.08F;
        // The stroke turned round the strike point: up to about 45 degrees either way for the cuts, less for a
        // jab, a lift or a circle, so no two casts come from the same direction.
        // Which way the whole gesture is turned on the screen (and, by its sign, which way a lift, a circle or a
        // spiral goes): the same spread for every gesture, so no two casts come in from quite the same direction.
        float tilt = random.nextBoolean() ? -0.5F : 0.5F;
        // Where a sweep ends (FirstPersonWandRig.sweepEnd: the sign the side, the size how low). Mostly across
        // to the other side from where the arm is (rest is on the right); now and then a cut down or up on the
        // side it is on, as in the reference (left, lower left, right...).
        boolean back = previous == null || player.tickCount - previous.start() > FirstPersonWandRig.SWEEP_LENGTH;
        float lastPlace = back ? 0.6F : previous.variant().place();
        float lastSide = lastPlace < 0.0F ? -1.0F : 1.0F;
        // From rest (on the right) most casts cut across to the left, but not all: some cut up or down on the
        // right, so casts with a pause between them do not all go the same way.
        boolean across = random.nextFloat() < (back ? 0.6F : 0.75F);
        float size;
        // Two authored endings: high (0.55) or low (0.95) on that side.
        if (across || back) {
            size = random.nextBoolean() ? 0.55F : 0.95F;
        } else {
            size = Math.abs(lastPlace) > 0.75F ? 0.55F : 0.95F;
        }
        float place = (across ? -lastSide : lastSide) * size;
        return new Variant(mirror, scale, pitch, shiftX, shiftY, tilt, place);
    }

    /** A random variant for someone else's cast (third person), as {@link #nextVariant} gives your own. */
    static Variant randomVariant(java.util.Random random, Gesture gesture, SpellType spell) {
        boolean mirror = gesture == TWIST ? spell == SpellType.COLLOPORTUS : gesture.mirrorable && random.nextBoolean();
        float scale = random.nextBoolean() ? 0.9F : 1.1F;
        float pitch = random.nextBoolean() ? -11.0F : 11.0F;
        float shiftY = random.nextBoolean() ? -0.08F : 0.08F;
        float tilt = random.nextBoolean() ? -0.5F : 0.5F;
        float place = (random.nextBoolean() ? 1.0F : -1.0F) * (random.nextBoolean() ? 0.55F : 0.95F);
        return new Variant(mirror, scale, pitch, 0.0F, shiftY, tilt, place);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        current = null;
        holdWeight = holdWeightO = 0.0F;
    }
}
