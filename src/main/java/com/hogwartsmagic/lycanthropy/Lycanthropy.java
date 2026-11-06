package com.hogwartsmagic.lycanthropy;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The werewolf curse on players (server side; see {@link LycanthropyRules} for the rules). Kept in
 * PlayerPersisted/hogwartsmagic/Lycanthropy, which Forge copies on death, so dying never lifts it. Watching clients
 * draw a beast in the player's place from {@link SpellPackets.SyncWerewolfPlayerPacket}.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Lycanthropy {
    private static final String KEY = "Lycanthropy";
    private static final String CURSED = "Cursed";
    private static final String AWAITING_DUSK = "AwaitingDusk";
    private static final String BEAST = "Beast";
    private static final String RESTLESS = "Restless";
    /** The day index (dayTime / 24000) whose restless night was already announced. */
    private static final String STIRRED_DAY = "StirredDay";

    /** What a client is told about a player (the packet's action byte). */
    public static final byte SHOW_HUMAN = 0;
    public static final byte SHOW_BEAST = 1;
    public static final byte MORPH_TO_BEAST = 2;
    public static final byte MORPH_TO_HUMAN = 3;
    public static final byte HOWL = 4;

    /** Server: ticks left of a player's transformation into the beast (for the slow and the climax howl). */
    private static final Map<UUID, Integer> MORPHING = new ConcurrentHashMap<>();

    private Lycanthropy() {
    }

    // ---- state -------------------------------------------------------------------------------------------------

    private static CompoundTag read(ServerPlayer player) {
        return player.getPersistentData().getCompound("PlayerPersisted").getCompound(ExampleMod.MODID).getCompound(KEY);
    }

    private static void write(ServerPlayer player, CompoundTag curse) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound("PlayerPersisted");
        CompoundTag mod = persisted.getCompound(ExampleMod.MODID);
        mod.put(KEY, curse);
        persisted.put(ExampleMod.MODID, mod);
        root.put("PlayerPersisted", persisted);
    }

    public static boolean isCursed(ServerPlayer player) {
        return read(player).getBoolean(CURSED);
    }

    /** Server: the player is in beast form right now. */
    public static boolean isBeast(ServerPlayer player) {
        return read(player).getBoolean(BEAST);
    }

    private static boolean isNight(Level level) {
        return !level.isDay();
    }

    private static boolean isNatural(Level level) {
        return level.dimensionType().natural();
    }

    public static boolean isFullMoon(Level level) {
        return isNatural(level) && level.getMoonPhase() == 0;
    }

    // ---- infection and cure --------------------------------------------------------------------------------------

    /** A werewolf's bite landed on this player: maybe the curse takes hold. */
    public static void onBitten(ServerPlayer victim) {
        if (victim.isCreative() || victim.isSpectator() || isCursed(victim)) {
            return;
        }
        Level level = victim.level();
        if (!LycanthropyRules.biteInfects(victim.getRandom().nextDouble(), isFullMoon(level))) {
            return;
        }
        infect(victim);
    }

    /** Curses the player (the command and the bite). */
    public static void infect(ServerPlayer player) {
        CompoundTag curse = read(player);
        if (curse.getBoolean(CURSED)) {
            return; // already cursed: nothing to restart
        }
        curse.putBoolean(CURSED, true);
        curse.putBoolean(AWAITING_DUSK, LycanthropyRules.awaitsDuskAfterBite(isNight(player.level())));
        curse.putBoolean(BEAST, false);
        write(player, curse);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.lycanthropy.bitten"), false);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOLF_GROWL,
                SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    /**
     * Lifts the curse (the Wolfsbane Potion and the command). A beast changes back on the spot. False when there was
     * no curse to lift.
     */
    public static boolean cure(ServerPlayer player) {
        CompoundTag curse = read(player);
        if (!curse.getBoolean(CURSED)) {
            return false;
        }
        boolean wasBeast = curse.getBoolean(BEAST);
        write(player, new CompoundTag());
        MORPHING.remove(player.getUUID());
        if (wasBeast) {
            sync(player, MORPH_TO_HUMAN);
        }
        clearBeastEffects(player); // a beast's or a restless night's Night Vision
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY(0.6), player.getZ(), 18, 0.35, 0.5, 0.35, 0.03);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE,
                    SoundSource.PLAYERS, 0.7F, 1.2F);
        }
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.lycanthropy.cured"), false);
        return true;
    }

    /** The Wolfsbane Potion's drink. */
    public static void drinkWolfsbane(LivingEntity drinker) {
        if (drinker instanceof ServerPlayer player && cure(player)) {
            return;
        }
        drinker.addEffect(new MobEffectInstance(MobEffects.CONFUSION, LycanthropyRules.UNCURSED_DRINK_NAUSEA_TICKS, 0));
        if (drinker instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.lycanthropy.uncursed_drink"), true);
        }
    }

    // ---- the nightly change ----------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        tickMorph(player);
        if (player.tickCount % LycanthropyRules.CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        CompoundTag curse = read(player);
        if (!curse.getBoolean(CURSED)) {
            return;
        }
        Level level = player.level();
        boolean night = isNight(level);
        if (!night && curse.getBoolean(AWAITING_DUSK)) {
            curse.putBoolean(AWAITING_DUSK, false);
            write(player, curse);
        }
        boolean able = player.isAlive() && !player.isSpectator();
        boolean awaiting = curse.getBoolean(AWAITING_DUSK);
        boolean natural = isNatural(level);
        int phase = level.getMoonPhase();
        boolean beast = curse.getBoolean(BEAST);
        boolean shouldBe = able && LycanthropyRules.shouldBeBeast(true, awaiting, night, natural, phase);
        if (shouldBe != beast) {
            curse.putBoolean(BEAST, shouldBe);
            write(player, curse);
            if (shouldBe) {
                turnBeast(player);
            } else {
                turnHuman(player);
            }
        }
        if (shouldBe) {
            tickBeast(player);
        }

        boolean restless = able && LycanthropyRules.isRestless(true, awaiting, night, natural, phase);
        if (restless != curse.getBoolean(RESTLESS)) {
            curse.putBoolean(RESTLESS, restless);
            if (restless) {
                long day = level.getDayTime() / 24000L;
                if (!curse.contains(STIRRED_DAY) || curse.getLong(STIRRED_DAY) != day) {
                    curse.putLong(STIRRED_DAY, day);
                    player.displayClientMessage(Component.translatable(LycanthropyRules.warnsOfFullMoon(phase)
                            ? "message.hogwartsmagic.lycanthropy.eve" : "message.hogwartsmagic.lycanthropy.restless"), false);
                }
            } else {
                clearBeastEffects(player);
            }
            write(player, curse);
        }
        if (restless) {
            tickRestless(player);
        }
    }

    /** A night that is not the full moon: still yourself, wand and all, but the wolf is close under the skin. */
    private static void tickRestless(ServerPlayer player) {
        topUp(player, MobEffects.MOVEMENT_SPEED, 0, LycanthropyRules.BEAST_EFFECT_TICKS);
        topUp(player, MobEffects.HUNGER, 0, LycanthropyRules.BEAST_EFFECT_TICKS);
        topUpNightVision(player);
        if (player.getRandom().nextInt(LycanthropyRules.GROWL_ONE_IN_CHECKS) == 0) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOLF_GROWL,
                    SoundSource.PLAYERS, 0.6F, 0.55F + player.getRandom().nextFloat() * 0.1F);
        }
    }

    private static void topUpNightVision(ServerPlayer player) {
        MobEffectInstance sight = player.getEffect(MobEffects.NIGHT_VISION);
        if (sight == null || (!sight.isInfiniteDuration() && sight.getDuration() < 220)) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, LycanthropyRules.BEAST_NIGHT_VISION_TICKS, 0, true, false, true));
        }
    }

    private static void turnBeast(ServerPlayer player) {
        MORPHING.put(player.getUUID(), LycanthropyRules.MORPH_TO_WOLF_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, LycanthropyRules.MORPH_TO_WOLF_TICKS, 3, false, false, false));
        sync(player, MORPH_TO_BEAST);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.WEREWOLF_HURT.get(),
                SoundSource.PLAYERS, 1.0F, 0.6F);
        player.displayClientMessage(Component.translatable("message.hogwartsmagic.lycanthropy.turn_full_moon"), true);
        com.hogwartsmagic.event.ModEvents.interruptActiveCasting(player);
    }

    private static void turnHuman(ServerPlayer player) {
        MORPHING.remove(player.getUUID());
        sync(player, MORPH_TO_HUMAN);
        clearBeastEffects(player);
        if (player.isAlive()) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, LycanthropyRules.DAWN_WEAKNESS_TICKS, 0));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, LycanthropyRules.DAWN_HUNGER_TICKS, 1));
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.lycanthropy.dawn"), true);
        }
    }

    private static void tickMorph(ServerPlayer player) {
        Integer left = MORPHING.get(player.getUUID());
        if (left == null) {
            return;
        }
        int now = left - 1;
        if (now == LycanthropyRules.MORPH_HOWL_AT_TICKS_LEFT) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.WEREWOLF_HOWL.get(),
                    SoundSource.PLAYERS, 1.5F, 0.7F);
        }
        if (now <= 0) {
            MORPHING.remove(player.getUUID());
        } else {
            MORPHING.put(player.getUUID(), now);
        }
    }

    /** Every check while a beast: keep its strength up, and now and then a howl. */
    private static void tickBeast(ServerPlayer player) {
        int amp = LycanthropyRules.BEAST_AMPLIFIER;
        topUp(player, MobEffects.DAMAGE_BOOST, amp, LycanthropyRules.BEAST_EFFECT_TICKS);
        topUp(player, MobEffects.MOVEMENT_SPEED, amp, LycanthropyRules.BEAST_EFFECT_TICKS);
        topUp(player, MobEffects.JUMP, 0, LycanthropyRules.BEAST_EFFECT_TICKS);
        topUp(player, MobEffects.HUNGER, 0, LycanthropyRules.BEAST_EFFECT_TICKS);
        // Paws cannot hold a wand: no spells while the beast has you.
        ExampleMod.SILENCED.getHolder().ifPresent(h -> topUp(player, h, 0, LycanthropyRules.BEAST_EFFECT_TICKS));
        topUpNightVision(player);
        if (!MORPHING.containsKey(player.getUUID())
                && player.getRandom().nextInt(LycanthropyRules.HOWL_ONE_IN_CHECKS) == 0) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.WEREWOLF_HOWL.get(),
                    SoundSource.PLAYERS, 1.4F, 0.75F + player.getRandom().nextFloat() * 0.15F);
            sync(player, HOWL);
        }
    }

    private static void topUp(ServerPlayer player, Holder<MobEffect> effect, int amplifier, int ticks) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getAmplifier() > amplifier) {
            return; // a stronger one from elsewhere: leave it
        }
        if (current == null || current.getAmplifier() != amplifier
                || (!current.isInfiniteDuration() && current.getDuration() < LycanthropyRules.BEAST_EFFECT_REFRESH_BELOW)) {
            player.addEffect(new MobEffectInstance(effect, ticks, amplifier, true, false, true));
        }
    }

    /** Dawn or the cure: the beast's own Night Vision goes at once; the short effects run out within seconds. */
    private static void clearBeastEffects(ServerPlayer player) {
        MobEffectInstance sight = player.getEffect(MobEffects.NIGHT_VISION);
        if (sight != null && sight.isAmbient() && !sight.isInfiniteDuration()
                && sight.getDuration() <= LycanthropyRules.BEAST_NIGHT_VISION_TICKS) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    // ---- bites ---------------------------------------------------------------------------------------------------

    /**
     * A bite that drew blood: from a Lycanthrope Villager in its beast form, or a beast player's own melee hit on
     * another player. Either can pass the curse on.
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || event.getAmount() <= 0.0F) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.getEntity() == null || source.getDirectEntity() != source.getEntity()) {
            return; // only teeth and claws, never an arrow or a spell
        }
        if (source.getEntity() instanceof com.hogwartsmagic.entity.LycanthropeVillagerEntity wolf && wolf.isWerewolfForm()) {
            onBitten(victim);
        } else if (source.getEntity() instanceof ServerPlayer biter && biter != victim && isBeast(biter)) {
            onBitten(victim);
        }
    }

    // ---- syncing what watchers draw ------------------------------------------------------------------------------

    private static void sync(ServerPlayer player, byte action) {
        SpellPackets.CHANNEL.send(new SpellPackets.SyncWerewolfPlayerPacket(player.getUUID(), action),
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(player));
    }

    private static void syncCurrent(ServerPlayer player) {
        sync(player, isBeast(player) ? SHOW_BEAST : SHOW_HUMAN);
    }

    /** Whoever starts watching a player learns whether to draw them as the beast (human clears a stale beast). */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer watcher && event.getTarget() instanceof ServerPlayer target) {
            SpellPackets.CHANNEL.send(new SpellPackets.SyncWerewolfPlayerPacket(target.getUUID(),
                    isBeast(target) ? SHOW_BEAST : SHOW_HUMAN), PacketDistributor.PLAYER.with(watcher));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncCurrent(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncCurrent(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncCurrent(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MORPHING.remove(event.getEntity().getUUID());
    }
}
