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
    /** Full moons slept through in a row (see LycanthropyRules.SKIPPED_MOONS_BEFORE_FERAL). */
    private static final String SKIPPED_MOONS = "SkippedMoons";
    /** The day index the last slept-through full moon was skipped into (so its dawn does not count as lived). */
    private static final String SKIPPED_INTO_DAY = "SkippedIntoDay";
    /** This full moon the starved wolf drives the body. */
    private static final String FERAL = "Feral";

    /** What a client is told about a player (the packet's action byte). */
    public static final byte SHOW_HUMAN = 0;
    public static final byte SHOW_BEAST = 1;
    public static final byte MORPH_TO_BEAST = 2;
    public static final byte MORPH_TO_HUMAN = 3;
    public static final byte HOWL = 4;

    /** Server: ticks left of a player's transformation into the beast (for the slow and the climax howl). */
    private static final Map<UUID, Integer> MORPHING = new ConcurrentHashMap<>();

    /** Server: the feral beasts being driven, and what each is hunting. */
    private static final Map<UUID, FeralDrive> FERAL_DRIVES = new ConcurrentHashMap<>();

    private static final class FeralDrive {
        double lastY;
        boolean pushed;
        double pushX, pushY, pushZ;
        long pushAt;
        float wanderYaw;
        long turnAt;
        long preyPickedAt = Long.MIN_VALUE / 2;
        @javax.annotation.Nullable
        LivingEntity prey;
    }

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
        endFeral(player);
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
        FeralDrive drive = FERAL_DRIVES.get(player.getUUID());
        if (drive != null && !MORPHING.containsKey(player.getUUID()) && player.isAlive()) {
            driveFeral(player, drive);
        }
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
                turnBeast(player, curse);
            } else {
                turnHuman(player, curse);
            }
        }
        if (shouldBe) {
            tickBeast(player, curse.getBoolean(FERAL));
        }
        // The starved wolf's full moon: no bed holds you, even one you lay down in at dusk.
        if (night && natural && player.isSleeping()
                && LycanthropyRules.bedRefuses(curse.getInt(SKIPPED_MOONS), phase)) {
            player.stopSleepInBed(true, true);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.lycanthropy.no_sleep"), true);
        }

        boolean restless = able && LycanthropyRules.isRestless(true, awaiting, night, natural, phase);
        if (restless != curse.getBoolean(RESTLESS)) {
            curse.putBoolean(RESTLESS, restless);
            if (restless) {
                long day = level.getDayTime() / 24000L;
                if (!curse.contains(STIRRED_DAY) || curse.getLong(STIRRED_DAY) != day) {
                    curse.putLong(STIRRED_DAY, day);
                    String key = !LycanthropyRules.warnsOfFullMoon(phase) ? "restless"
                            : LycanthropyRules.feralDue(curse.getInt(SKIPPED_MOONS)) ? "eve_feral" : "eve";
                    player.displayClientMessage(Component.translatable("message.hogwartsmagic.lycanthropy." + key), false);
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

    private static void turnBeast(ServerPlayer player, CompoundTag curse) {
        boolean feral = LycanthropyRules.feralDue(curse.getInt(SKIPPED_MOONS));
        if (feral) {
            curse.putBoolean(FERAL, true);
            curse.putInt(SKIPPED_MOONS, 0); // the wolf has been fed
            write(player, curse);
        }
        MORPHING.put(player.getUUID(), LycanthropyRules.MORPH_TO_WOLF_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, LycanthropyRules.MORPH_TO_WOLF_TICKS, 3, false, false, false));
        sync(player, MORPH_TO_BEAST);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.WEREWOLF_HURT.get(),
                SoundSource.PLAYERS, 1.0F, 0.6F);
        player.displayClientMessage(Component.translatable(feral
                ? "message.hogwartsmagic.lycanthropy.feral" : "message.hogwartsmagic.lycanthropy.turn_full_moon"), feral ? false : true);
        com.hogwartsmagic.event.ModEvents.interruptActiveCasting(player);
        if (feral) {
            startFeral(player);
        }
    }

    private static void turnHuman(ServerPlayer player, CompoundTag curse) {
        boolean wasFeral = curse.getBoolean(FERAL);
        curse.remove(FERAL);
        Level level = player.level();
        long day = level.getDayTime() / 24000L;
        boolean skipped = curse.contains(SKIPPED_INTO_DAY) && curse.getLong(SKIPPED_INTO_DAY) == day;
        if (!wasFeral && player.isAlive() && level.isDay() && isNatural(level) && !skipped) {
            curse.putInt(SKIPPED_MOONS, 0); // a full moon lived through as the beast: the wolf is content
        }
        write(player, curse);
        MORPHING.remove(player.getUUID());
        endFeral(player);
        sync(player, MORPH_TO_HUMAN);
        clearBeastEffects(player);
        if (player.isAlive()) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, LycanthropyRules.DAWN_WEAKNESS_TICKS, 0));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, LycanthropyRules.DAWN_HUNGER_TICKS, 1));
            player.displayClientMessage(Component.translatable(wasFeral
                    ? "message.hogwartsmagic.lycanthropy.feral_dawn" : "message.hogwartsmagic.lycanthropy.dawn"), !wasFeral);
        }
    }

    // ---- sleeping through the full moon ---------------------------------------------------------------------------

    /** The starved wolf's full moon: the bed refuses a cursed player. */
    @SubscribeEvent
    public static void onSleepInBed(net.minecraftforge.event.entity.player.PlayerSleepInBedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isNatural(player.level())) {
            return;
        }
        CompoundTag curse = read(player);
        if (curse.getBoolean(CURSED)
                && LycanthropyRules.bedRefuses(curse.getInt(SKIPPED_MOONS), player.level().getMoonPhase())) {
            event.setResult(net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.lycanthropy.no_sleep"), true);
        }
    }

    /** The night is being slept away: every cursed sleeper skipping a full moon feeds the wolf's hunger. */
    @SubscribeEvent
    public static void onSleepFinished(net.minecraftforge.event.level.SleepFinishedTimeEvent event) {
        // Fired before the clock moves, so the moon phase is still the night being skipped.
        if (!(event.getLevel() instanceof ServerLevel level) || !isNatural(level)
                || level.getMoonPhase() != LycanthropyRules.FULL_MOON_PHASE) {
            return;
        }
        long intoDay = event.getNewTime() / 24000L;
        for (ServerPlayer player : level.players()) {
            if (!player.isSleeping()) {
                continue;
            }
            CompoundTag curse = read(player);
            if (!curse.getBoolean(CURSED) || curse.getBoolean(AWAITING_DUSK)) {
                continue;
            }
            int skipped = LycanthropyRules.afterSleptThroughFullMoon(curse.getInt(SKIPPED_MOONS));
            curse.putInt(SKIPPED_MOONS, skipped);
            curse.putLong(SKIPPED_INTO_DAY, intoDay);
            write(player, curse);
            player.displayClientMessage(Component.translatable(LycanthropyRules.feralDue(skipped)
                    ? "message.hogwartsmagic.lycanthropy.slept_moon_starved" : "message.hogwartsmagic.lycanthropy.slept_moon"), false);
        }
    }

    // ---- the feral beast -------------------------------------------------------------------------------------------

    private static void startFeral(ServerPlayer player) {
        FeralDrive drive = new FeralDrive();
        drive.lastY = player.getY();
        drive.wanderYaw = player.getYRot();
        FERAL_DRIVES.put(player.getUUID(), drive);
        ExampleMod.FERAL.getHolder().ifPresent(h -> player.addEffect(
                new MobEffectInstance(h, LycanthropyRules.BEAST_EFFECT_TICKS, 0, true, false, true)));
        com.hogwartsmagic.spell.mind.MindControl.closeOpenContainer(player);
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
    }

    private static void endFeral(ServerPlayer player) {
        FERAL_DRIVES.remove(player.getUUID());
        ExampleMod.FERAL.getHolder().ifPresent(player::removeEffect);
    }

    /** One tick of the starved wolf at the wheel: hunt the nearest living thing, or roam. */
    private static void driveFeral(ServerPlayer player, FeralDrive drive) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        LivingEntity prey = drive.prey;
        double range = LycanthropyRules.FERAL_HUNT_RANGE;
        if (prey == null || !prey.isAlive() || prey.level() != level || !isPrey(player, prey)
                || prey.distanceToSqr(player) > range * range * 2.25 || now - drive.preyPickedAt >= 20) {
            prey = findPrey(player);
            drive.prey = prey;
            drive.preyPickedAt = now;
        }
        double previousY = drive.lastY;
        drive.lastY = player.getY();
        double wx = 0.0;
        double wz = 0.0;
        float yaw;
        double dist = 0.0;
        if (prey != null) {
            double dx = prey.getX() - player.getX();
            double dz = prey.getZ() - player.getZ();
            dist = player.distanceTo(prey);
            yaw = (float) (net.minecraft.util.Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            double flat = Math.sqrt(dx * dx + dz * dz);
            if (dist > LycanthropyRules.FERAL_REACH - 0.4 && flat > 1.0E-3) {
                wx = dx / flat * LycanthropyRules.FERAL_CHASE_SPEED;
                wz = dz / flat * LycanthropyRules.FERAL_CHASE_SPEED;
            }
        } else {
            if (now >= drive.turnAt) {
                drive.wanderYaw = player.getRandom().nextFloat() * 360.0F;
                drive.turnAt = now + LycanthropyRules.FERAL_WANDER_TURN_TICKS;
            }
            yaw = drive.wanderYaw;
            double rad = Math.toRadians(yaw);
            wx = -Math.sin(rad) * LycanthropyRules.FERAL_WANDER_SPEED;
            wz = Math.cos(rad) * LycanthropyRules.FERAL_WANDER_SPEED;
        }
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        if ((wx != 0.0 || wz != 0.0) && com.hogwartsmagic.spell.mind.ImperioThralls.unsafeStep(player, wx, wz)) {
            wx = 0.0;
            wz = 0.0;
            drive.turnAt = now; // a wandering beast turns away from the drop or the lava
        }
        double vy = com.hogwartsmagic.spell.mind.ForcedPlayerMovement.verticalVelocity(player, previousY,
                player.horizontalCollision && (wx != 0.0 || wz != 0.0));
        if (com.hogwartsmagic.spell.mind.ImperioRules.needsPush(drive.pushed, drive.pushX, drive.pushY, drive.pushZ,
                wx, vy, wz, drive.pushAt, now)) {
            com.hogwartsmagic.spell.mind.ForcedPlayerMovement.push(player, wx, vy, wz);
            drive.pushed = true;
            drive.pushX = wx;
            drive.pushY = vy;
            drive.pushZ = wz;
            drive.pushAt = now;
        }
        if (prey != null && dist <= LycanthropyRules.FERAL_REACH
                && player.getAttackStrengthScale(0.5F) >= LycanthropyRules.FERAL_SWING_CHARGE) {
            com.hogwartsmagic.spell.mind.MindControl.forcedAttack(player, prey);
        }
    }

    @javax.annotation.Nullable
    private static LivingEntity findPrey(ServerPlayer player) {
        double range = LycanthropyRules.FERAL_HUNT_RANGE;
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity candidate : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range), e -> isPrey(player, e))) {
            double d = candidate.distanceToSqr(player);
            if (d < bestDist && d <= range * range && player.hasLineOfSight(candidate)) {
                best = candidate;
                bestDist = d;
            }
        }
        return best;
    }

    /** Anything alive it can hurt, except its own kind (werewolves and other beast players). */
    private static boolean isPrey(ServerPlayer beast, LivingEntity e) {
        if (e == beast || !e.isAlive() || e.isSpectator() || !e.attackable() || e.isInvulnerable()
                || e instanceof net.minecraft.world.entity.decoration.ArmorStand) {
            return false;
        }
        if (e instanceof com.hogwartsmagic.entity.LycanthropeVillagerEntity wolf && wolf.isWerewolfForm()) {
            return false;
        }
        if (e instanceof net.minecraft.world.entity.player.Player other) {
            if (other.isCreative() || (other instanceof ServerPlayer sp && isBeast(sp))) {
                return false;
            }
            return beast.server.isPvpAllowed() && beast.canHarmPlayer(other);
        }
        return true;
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
    private static void tickBeast(ServerPlayer player, boolean feral) {
        if (feral) {
            if (!FERAL_DRIVES.containsKey(player.getUUID())) {
                startFeral(player); // logged back in mid-night: the wolf still has them
            }
            ExampleMod.FERAL.getHolder().ifPresent(h -> topUp(player, h, 0, LycanthropyRules.BEAST_EFFECT_TICKS));
        }
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
        FERAL_DRIVES.remove(event.getEntity().getUUID());
    }
}
