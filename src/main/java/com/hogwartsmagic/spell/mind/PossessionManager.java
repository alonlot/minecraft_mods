package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.event.ModEvents;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.disable.DisableSupport;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Imperio Master: possession sessions. The server owns them: it starts them, takes input only from each
 * session's own caster (rate-limited and sanitised), drives the victim every tick, keeps both clients told
 * what they are (PossessionStatePacket, re-sent every second as a keepalive), and ends the session for
 * every reason the spec lists. A mob victim is driven here by velocity; a player victim's own client
 * carries out the caster's input (PuppetInputPacket), so player movement stays client-authoritative.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PossessionManager {
    /** Why a possession ended. Each carries the caster-facing line. */
    public enum EndReason {
        RELEASED("released"),
        TIMEOUT("timeout"),
        BROKE_FREE("broke_free"),
        BODY_HIT("body_hit"),
        DIED("died"),
        LOGGED_OUT("logged_out"),
        DIMENSION("dimension"),
        TOO_FAR("too_far"),
        INTERRUPTED("interrupted"),
        NO_MANA("no_mana"),
        INVALID("invalid");

        private final String key;

        EndReason(String key) {
            this.key = key;
        }

        public String langKey() {
            return "gui." + ExampleMod.MODID + ".possession.end." + this.key;
        }
    }

    private static final Map<UUID, PossessionSession> BY_CASTER = new HashMap<>();
    private static final Map<UUID, PossessionSession> BY_VICTIM = new HashMap<>();
    private static Holder<MobEffect> possessedHolder;
    /** Game time until which a victim who broke free shrugs off Imperio. */
    static final String TAG_IMMUNE_UNTIL = ExampleMod.MODID + ":imperio_immune_until";
    private static Holder<MobEffect> possessingHolder;
    /** True while end() itself strips the markers, so the removal guard lets those removals through. */
    private static boolean ending;

    private PossessionManager() {
    }

    // ---- Queries -------------------------------------------------------------------------------

    public static boolean isPossessing(LivingEntity entity) {
        return BY_CASTER.containsKey(entity.getUUID());
    }

    public static boolean isPuppet(LivingEntity entity) {
        return BY_VICTIM.containsKey(entity.getUUID());
    }

    /** Hot path (LivingEntityStunMixin runs it for every living entity every tick): a possessed non-player. */
    public static boolean isPossessedMob(LivingEntity entity) {
        if (entity instanceof Player) {
            return false;
        }
        Holder<MobEffect> holder = possessedHolder;
        if (holder == null) {
            holder = possessedHolder = ExampleMod.POSSESSED.getHolder().orElse(null);
            if (holder == null) {
                return false;
            }
        }
        return entity.hasEffect(holder);
    }

    /** Hot path (LivingEntityStunMixin, both sides): the frozen body of a possessing caster. Effect marker only, no maps. */
    public static boolean isPossessingBody(LivingEntity entity) {
        Holder<MobEffect> holder = possessingHolder;
        if (holder == null) {
            holder = possessingHolder = ExampleMod.POSSESSING.getHolder().orElse(null);
            if (holder == null) {
                return false;
            }
        }
        return entity.hasEffect(holder);
    }

    /** Broke free less than 3s ago: Imperio (thrall or possession) slides off. */
    public static boolean isImmune(LivingEntity entity) {
        return entity.getPersistentData().getLong(TAG_IMMUNE_UNTIL) > entity.level().getGameTime();
    }

    private static boolean holdsImperio(ServerPlayer caster) {
        return MagicWandItem.isAnyWand(caster.getMainHandItem())
                && MagicWandItem.getSelectedSpell(caster.getMainHandItem()) == SpellType.IMPERIO;
    }

    @Nullable
    private static LivingEntity living(@Nullable Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    // ---- Start ---------------------------------------------------------------------------------

    public static boolean start(ServerPlayer caster, LivingEntity victim, boolean debug) {
        if (!(caster.level() instanceof ServerLevel level) || victim.level() != level || victim == caster || !victim.isAlive()) {
            return false;
        }
        if (MindControl.isControlled(caster)) {
            return false;
        }
        if (isPossessing(caster) || isPuppet(caster) || isPossessing(victim) || isPuppet(victim) || ImperioThralls.isThrall(victim)) {
            MindFx.tell(caster, "possession.busy", victim.getDisplayName());
            return false;
        }
        if (isImmune(victim)) {
            MindFx.resisted(victim, caster);
            return false;
        }
        boolean player = victim instanceof ServerPlayer;
        if (player) {
            ServerPlayer target = (ServerPlayer) victim;
            if (!Config.isImperioAffectsPlayers()) {
                MindFx.tell(caster, "imperio.players_off");
                return false;
            }
            if (!caster.server.isPvpAllowed() || !caster.canHarmPlayer(target)) {
                MindFx.tell(caster, "possession.pvp_off");
                return false;
            }
            if (target.isCreative() || target.isSpectator()) {
                return false;
            }
        } else if (!(victim instanceof Mob)) {
            return false;
        }
        boolean boss = CombatTags.isBoss(victim);
        long now = level.getGameTime();
        PossessionSession session = new PossessionSession(caster.getUUID(), victim.getUUID(), level.dimension(), now,
                PossessionRules.maxTicks(boss), boss, player, debug);
        session.yaw = victim.getYRot();
        session.pitch = victim.getXRot();
        session.lastVictimY = victim.getY();
        if (victim instanceof ServerPlayer target) {
            session.forcedSlot = target.getInventory().selected;
        }
        CrucioChannel.release(caster); // a Crucio channel must not end later this tick and strip the markers
        DisableSupport.interrupt(caster); // the caster's own channels and windups stop
        BY_CASTER.put(session.casterId, session);
        BY_VICTIM.put(session.victimId, session);
        int markerTicks = session.maxTicks + PossessionRules.MARKER_GRACE_TICKS;
        addMarkers(caster, victim, markerTicks);
        DisableSupport.interrupt(victim);
        if (victim instanceof Mob mob) {
            MindControl.setTargetBypassing(mob, null);
            MindControl.clearBrainTarget(mob, caster);
            mob.getNavigation().stop();
        }
        sendState(session, caster, victim, now);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SpellType.IMPERIO.getSoundEvent(),
                SoundSource.PLAYERS, 1.0F, 0.8F);
        MindFx.burst(victim, ParticleTypes.HAPPY_VILLAGER);
        return true;
    }

    private static void addMarkers(ServerPlayer caster, LivingEntity victim, int ticks) {
        caster.addEffect(new MobEffectInstance(DisableSupport.holder(ExampleMod.ROOTED), ticks, 0, false, false, false));
        caster.addEffect(new MobEffectInstance(DisableSupport.holder(ExampleMod.POSSESSING), ticks, 0, false, false, false));
        victim.addEffect(new MobEffectInstance(DisableSupport.holder(ExampleMod.POSSESSED), ticks, 0, false, false, false));
    }

    /**
     * Milk, /effect clear and totems can't strip a live session's markers. This only cancels: it never adds or
     * removes an effect (removeAllEffects fires it inside its own iterator); tickSession re-adds anything missing.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEffectRemove(MobEffectEvent.Remove event) {
        LivingEntity entity = event.getEntity();
        if (ending || entity.level().isClientSide() || BY_CASTER.isEmpty()) {
            return;
        }
        MobEffect effect = event.getEffect();
        if (effect == ExampleMod.POSSESSED.get() ? BY_VICTIM.containsKey(entity.getUUID())
                : (effect == ExampleMod.POSSESSING.get() || effect == ExampleMod.ROOTED.get())
                        && BY_CASTER.containsKey(entity.getUUID())) {
            event.setCanceled(true);
        }
    }

    /** The Master hold reached its threshold on a target the beam hit: check the start mana, then possess. */
    public static void startFromHold(ServerPlayer caster, ItemStack wand, LivingEntity target) {
        if (!caster.isCreative()) {
            CompoundTag modData = PlayerMagicData.getModData(caster);
            float mana = modData.contains("Mana") ? modData.getFloat("Mana") : PlayerMagicData.getMaxMana(caster);
            if (mana < ModEvents.getImperioStartCost(caster, wand)) {
                caster.displayClientMessage(Component.literal("Not enough mana!").withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        if (PossessionRules.tooFar(caster.distanceToSqr(target))) {
            MindFx.tell(caster, "possession.target_far");
            return;
        }
        if (start(caster, target, false)) {
            ModEvents.revealInvisibilityCloak(caster);
        }
    }

    // ---- Input ---------------------------------------------------------------------------------

    /** Every ImperioControlPacket from a possessing caster lands here; only their own session is ever touched. */
    public static boolean handleCasterPacket(ServerPlayer sender, SpellPackets.ImperioControlPacket packet) {
        PossessionSession session = BY_CASTER.get(sender.getUUID());
        if (session == null) {
            return false;
        }
        if (packet.stopChannel()) {
            end(session, EndReason.RELEASED);
        } else if (!packet.startChannel()) {
            acceptInput(session, packet, sender.level().getGameTime());
        }
        return true;
    }

    private static void acceptInput(PossessionSession session, SpellPackets.ImperioControlPacket packet, long now) {
        if (!session.inputRate.tryAcquire(now)) {
            return; // over the rate: dropped (a flood can't hold the session open faster than allowed)
        }
        session.lastSeen = now;
        session.forward = PossessionRules.axis(packet.forward());
        session.strafe = PossessionRules.axis(packet.strafe());
        session.yaw = PossessionRules.yaw(packet.yaw());
        session.pitch = PossessionRules.pitch(packet.pitch());
        session.jump = packet.jump();
        session.attack = packet.attack();
    }

    /**
     * A possessed player's report of SPACE presses. Only the session's own victim counts, at most one report
     * per 8 ticks, at most 12 presses per half-second window, and nothing before the 8-second mark.
     */
    public static void acceptResist(ServerPlayer sender, int presses) {
        PossessionSession session = BY_VICTIM.get(sender.getUUID());
        if (session == null || !session.victimIsPlayer) {
            return;
        }
        long now = sender.level().getGameTime();
        if (!PossessionRules.reportAllowed(now, session.lastResistReportAt)) {
            return;
        }
        session.lastResistReportAt = now;
        if (!PossessionRules.resistOpen(now - session.startedAt)) {
            return;
        }
        session.resistPresses = PossessionRules.clampPresses(session.resistPresses + PossessionRules.clampPresses(presses));
    }

    /** Closes each half-second window: 6+ presses in it is a 20% roll to break free. Returns true if it ended. */
    private static boolean tickResist(PossessionSession session, ServerPlayer caster, LivingEntity victim, long now, long elapsed) {
        int window = PossessionRules.resistWindow(elapsed);
        if (window < 0) {
            return false;
        }
        if (!session.resistAnnounced) {
            session.resistAnnounced = true;
            sendState(session, caster, victim, now); // the victim's HUD switches to "Mash SPACE"
        }
        if (window == session.resistWindow) {
            return false;
        }
        int presses = session.resistPresses;
        boolean evaluate = session.resistWindow >= 0;
        session.resistWindow = window;
        session.resistPresses = 0;
        if (evaluate && PossessionRules.breaksFree(presses, victim.getRandom().nextDouble())) {
            end(session, EndReason.BROKE_FREE);
            return true;
        }
        return false;
    }

    // ---- End -----------------------------------------------------------------------------------

    public static void endByCaster(ServerPlayer caster, EndReason reason) {
        PossessionSession session = BY_CASTER.get(caster.getUUID());
        if (session != null) {
            end(session, reason);
        }
    }

    static void end(PossessionSession session, EndReason reason) {
        end(session, reason, false);
    }

    /** {@code casterLeaving}: the caster is the one logging out, so there is no body left to make dizzy. */
    private static void end(PossessionSession session, EndReason reason, boolean casterLeaving) {
        if (BY_CASTER.get(session.casterId) != session) {
            return; // already ended
        }
        BY_CASTER.remove(session.casterId);
        BY_VICTIM.remove(session.victimId);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ServerPlayer caster = server.getPlayerList().getPlayer(session.casterId);
        ServerLevel level = server.getLevel(session.dimension);
        Entity rawVictim = level == null ? null : level.getEntity(session.victimId);
        if (rawVictim == null) {
            rawVictim = server.getPlayerList().getPlayer(session.victimId);
        }
        if (rawVictim == null) {
            for (ServerLevel other : server.getAllLevels()) { // a mob carried through a portal; end() is rare
                rawVictim = other.getEntity(session.victimId);
                if (rawVictim != null) {
                    break;
                }
            }
        }
        ending = true;
        try {
            if (caster != null) {
                caster.removeEffect(DisableSupport.holder(ExampleMod.ROOTED));
                caster.removeEffect(DisableSupport.holder(ExampleMod.POSSESSING));
            }
            if (rawVictim instanceof LivingEntity victim) {
                victim.removeEffect(DisableSupport.holder(ExampleMod.POSSESSED));
                if (reason == EndReason.BROKE_FREE) {
                    victim.getPersistentData().putLong(TAG_IMMUNE_UNTIL, victim.level().getGameTime() + PossessionRules.IMMUNITY_TICKS);
                }
            }
        } finally {
            ending = false;
        }
        if (caster != null) {
            sendNone(caster);
            caster.displayClientMessage(Component.translatable(reason.langKey()).withStyle(ChatFormatting.GREEN), true);
            if (!session.debug) {
                ImperioThralls.startImperioCooldown(caster); // the cooldown, and the Imperio XP, start when it ends
            }
            if (caster.isAlive() && !casterLeaving && reason != EndReason.INVALID) {
                caster.addEffect(new MobEffectInstance(MobEffects.CONFUSION, PossessionRules.NAUSEA_TICKS, 0));
            }
        }
        if (rawVictim instanceof LivingEntity victim) {
            if (victim instanceof ServerPlayer victimPlayer) {
                sendNone(victimPlayer);
            }
            if (victim instanceof Mob mob) {
                MindControl.setTargetBypassing(mob, null);
                MindControl.clearBrainTarget(mob, null);
                mob.getNavigation().stop();
            }
        }
    }

    // ---- Logout, login, load ---------------------------------------------------------------------

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (BY_CASTER.isEmpty() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PossessionSession asCaster = BY_CASTER.get(player.getUUID());
        if (asCaster != null) {
            end(asCaster, EndReason.LOGGED_OUT, true);
        }
        PossessionSession asVictim = BY_VICTIM.get(player.getUUID());
        if (asVictim != null) {
            end(asVictim, EndReason.LOGGED_OUT);
        }
    }

    /** A crash can leave the markers saved on the player: with no session, sweep them. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || isPossessing(player) || isPuppet(player)) {
            return;
        }
        player.removeEffect(DisableSupport.holder(ExampleMod.POSSESSING));
        player.removeEffect(DisableSupport.holder(ExampleMod.POSSESSED));
        if (!CrucioChannel.isChanneling(player)) {
            player.removeEffect(DisableSupport.holder(ExampleMod.ROOTED));
        }
    }

    /** A mob that reloads with the possessed marker but no session (its chunk unloaded mid-possession) loses it. */
    @SubscribeEvent
    public static void onMobJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || !isPossessedMob(mob)
                || BY_VICTIM.containsKey(mob.getUUID())) {
            return;
        }
        mob.removeEffect(DisableSupport.holder(ExampleMod.POSSESSED));
    }

    // ---- Tick ----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BY_CASTER.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (PossessionSession session : new ArrayList<>(BY_CASTER.values())) {
            tickSession(server, session);
        }
    }

    private static void tickSession(MinecraftServer server, PossessionSession session) {
        ServerLevel level = server.getLevel(session.dimension);
        ServerPlayer caster = server.getPlayerList().getPlayer(session.casterId);
        LivingEntity victim = level == null ? null : living(level.getEntity(session.victimId));
        if (level == null || caster == null || !caster.isAlive() || victim == null) {
            end(session, EndReason.INVALID); // end() also strips the victim's marker if it is still reachable
            return;
        }
        long now = level.getGameTime();
        long elapsed = now - session.startedAt;
        if (PossessionRules.expired(elapsed, session.maxTicks)) {
            end(session, EndReason.TIMEOUT);
            return;
        }
        if (!session.debug && PossessionRules.heartbeatLapsed(now, session.lastSeen)) {
            end(session, EndReason.RELEASED);
            return;
        }
        if (caster.level() != level) {
            end(session, EndReason.DIMENSION);
            return;
        }
        if (PossessionRules.tooFar(victim.distanceToSqr(caster))) {
            end(session, EndReason.TOO_FAR);
            return;
        }
        if (!session.debug && !holdsImperio(caster)) {
            end(session, EndReason.RELEASED); // switched wand or spell: same as letting go
            return;
        }
        int markerTicks = session.maxTicks + PossessionRules.MARKER_GRACE_TICKS - (int) elapsed;
        if (!isPossessingBody(caster) || !victim.hasEffect(DisableSupport.holder(ExampleMod.POSSESSED))) {
            addMarkers(caster, victim, Math.max(markerTicks, 2)); // stripped by something that bypassed the guard
        }
        if (session.victimIsPlayer && tickResist(session, caster, victim, now, elapsed)) {
            return;
        }
        if (!drainUpkeep(caster, session, now)) {
            end(session, EndReason.NO_MANA);
            return;
        }
        drive(session, victim);
        if (now - session.lastStateAt >= PossessionRules.STATE_KEEPALIVE_TICKS) {
            sendState(session, caster, victim, now);
        }
    }

    private static void drive(PossessionSession session, LivingEntity victim) {
        victim.setYRot(session.yaw);
        victim.setYHeadRot(session.yaw);
        victim.setXRot(session.pitch);
        if (victim instanceof ServerPlayer victimPlayer) {
            // Relay the caster's input to the victim's own client, only when it changed (the client holds the last one).
            if (!session.sentAny || session.sentForward != session.forward || session.sentStrafe != session.strafe
                    || session.sentJump != session.jump || session.sentYaw != session.yaw || session.sentPitch != session.pitch) {
                session.sentAny = true;
                session.sentForward = session.forward;
                session.sentStrafe = session.strafe;
                session.sentJump = session.jump;
                session.sentYaw = session.yaw;
                session.sentPitch = session.pitch;
                SpellPackets.CHANNEL.send(new SpellPackets.PuppetInputPacket(session.forward, session.strafe, session.jump,
                        session.yaw, session.pitch), PacketDistributor.PLAYER.with(victimPlayer));
            }
        } else if (victim instanceof Mob mob) {
            PossessionDriver.driveMob(mob, session);
        }
    }

    /** The old channel's upkeep every half second; false when the caster can't pay. */
    private static boolean drainUpkeep(ServerPlayer caster, PossessionSession session, long now) {
        if (session.debug || caster.isCreative() || now - session.lastDrainAt < PossessionRules.UPKEEP_INTERVAL) {
            return true;
        }
        session.lastDrainAt = now;
        float cost = ModEvents.getImperioUpkeepCost(caster, caster.getMainHandItem());
        CompoundTag modData = PlayerMagicData.getModData(caster);
        float maxMana = PlayerMagicData.getMaxMana(caster);
        float mana = modData.contains("Mana") ? modData.getFloat("Mana") : maxMana;
        if (mana < cost) {
            return false;
        }
        float next = mana - cost;
        modData.putFloat("Mana", next);
        PlayerMagicData.saveModData(caster, modData);
        if ((int) mana != (int) next) {
            SpellPackets.CHANNEL.send(new SpellPackets.SyncManaPacket(next, maxMana), PacketDistributor.PLAYER.with(caster));
        }
        return true;
    }

    // ---- Packets out ---------------------------------------------------------------------------

    private static void sendState(PossessionSession session, ServerPlayer caster, LivingEntity victim, long now) {
        session.lastStateAt = now;
        long elapsed = now - session.startedAt;
        int left = (int) Math.max(0L, session.maxTicks - elapsed);
        boolean resistOpen = session.victimIsPlayer && PossessionRules.resistOpen(elapsed);
        SpellPackets.CHANNEL.send(new SpellPackets.PossessionStatePacket(PossessionRules.ROLE_CASTER, victim.getId(), left,
                resistOpen, !session.debug), PacketDistributor.PLAYER.with(caster));
        if (victim instanceof ServerPlayer victimPlayer) {
            SpellPackets.CHANNEL.send(new SpellPackets.PossessionStatePacket(PossessionRules.ROLE_VICTIM, caster.getId(), left,
                    resistOpen, false), PacketDistributor.PLAYER.with(victimPlayer));
            // Each keepalive also restates the current input, so a victim client that lost it catches up.
            SpellPackets.CHANNEL.send(new SpellPackets.PuppetInputPacket(session.forward, session.strafe, session.jump,
                    session.yaw, session.pitch), PacketDistributor.PLAYER.with(victimPlayer));
        }
    }

    private static void sendNone(ServerPlayer player) {
        SpellPackets.CHANNEL.send(new SpellPackets.PossessionStatePacket(PossessionRules.ROLE_NONE, -1, 0, false, false),
                PacketDistributor.PLAYER.with(player));
    }

    // ---- Event-driven ends ---------------------------------------------------------------------

    @Nullable
    private static PossessionSession sessionOf(UUID id) {
        PossessionSession session = BY_CASTER.get(id);
        return session != null ? session : BY_VICTIM.get(id);
    }

    /** A real blow to the frozen body (4+ after armour) throws the caster out: everyone nearby's counterplay. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide()) {
            return;
        }
        PossessionSession session = BY_CASTER.get(event.getEntity().getUUID());
        if (session != null && PossessionRules.ejects(event.getAmount())) {
            end(session, EndReason.BODY_HIT);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide()) {
            return;
        }
        PossessionSession session = sessionOf(event.getEntity().getUUID());
        if (session != null) {
            end(session, EndReason.DIED);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide()) {
            return;
        }
        PossessionSession session = sessionOf(event.getEntity().getUUID());
        if (session != null) {
            end(session, EndReason.DIMENSION);
        }
    }

    /** Before the worlds save, so nobody is written to disk rooted or possessed. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (PossessionSession session : new ArrayList<>(BY_CASTER.values())) {
            end(session, EndReason.INVALID);
        }
    }
}
