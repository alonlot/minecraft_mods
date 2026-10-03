package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.combat.CrowdControl;
import com.hogwartsmagic.spell.damage.DamageSupport;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Crucio as a channel. The beam's impact starts it: the victim is stunned for the rank's full length
 * (through CrowdControl, so diminishing returns and PvP apply; a boss is stunned for half the length
 * by the CrowdControl boss rule) and takes 1.5 every half second, while a player caster is rooted and must keep holding (the cast
 * packet heartbeat). It ends on release, a lapsed heartbeat, the rank cap, 24 blocks of distance, a death,
 * or an interrupt. Ending early lifts the stun it put on. At Master a surviving non-boss mob flees from the
 * caster for 10s. NPC casters run the full channel of their rank (rank 1; a boss rank 3) without holding. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CrucioChannel {
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.9f, 0.05f, 0.05f), 1.0f);

    private static final class Channel {
        final UUID casterId;
        final UUID targetId;
        final ResourceKey<Level> dimension;
        final long startedAt;
        final int maxTicks;
        final int rank;
        final boolean held;
        long lastSeen;
        long stunEndsAt;

        Channel(UUID casterId, UUID targetId, ResourceKey<Level> dimension, long startedAt, int maxTicks, int rank, boolean held) {
            this.casterId = casterId;
            this.targetId = targetId;
            this.dimension = dimension;
            this.startedAt = startedAt;
            this.maxTicks = maxTicks;
            this.rank = rank;
            this.held = held;
            this.lastSeen = startedAt;
        }
    }

    private record Flee(UUID from, long until) {
    }

    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    /** Fleeing mob UUID to who it runs from. Server only and in memory, so ticking mobs never touch persistent data. */
    private static final Map<UUID, Flee> FLEEING = new HashMap<>();

    private CrucioChannel() {
    }

    /** Imperio takes the mob over: it stops running from its last Crucio caster. */
    public static void stopFleeing(UUID mobId) {
        FLEEING.remove(mobId);
    }

    public static boolean isChanneling(Entity caster) {
        return CHANNELS.containsKey(caster.getUUID());
    }

    public static void onBeamHit(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        if (MindControl.pvpBlocked(target, caster)) {
            MindFx.tell(caster, "mind.pvp_off");
            return;
        }
        Entity owner = CombatEffects.responsible(caster);
        if (!(owner instanceof LivingEntity livingOwner) || owner == target || !livingOwner.isAlive()
                || MindControl.isControlled(livingOwner)) {
            return;
        }
        if (MindControl.isControlled(target)) {
            // The beam routes mastery XP through SpellBeamEntity, which skips it for this case too.
            MindFx.resisted(target, caster);
            return;
        }
        Channel previous = CHANNELS.get(owner.getUUID());
        if (previous != null) {
            end(level.getServer(), previous);
        }
        int rank = DisableSupport.casterRank(caster, SpellType.CRUCIO);
        boolean held = owner instanceof ServerPlayer;
        long now = level.getGameTime();
        int maxTicks = CrucioRules.channelTicks(rank);
        Channel channel = new Channel(owner.getUUID(), target.getUUID(), level.dimension(), now, maxTicks, rank, held);
        DisableSupport.interrupt(target);
        // CrowdControl owns diminishing returns, PvP and the boss rule (half duration).
        int stun = CrowdControl.apply(target, DisableSupport.holder(ExampleMod.STUNNED), maxTicks, caster);
        channel.stunEndsAt = now + stun;
        if (held) {
            livingOwner.addEffect(new MobEffectInstance(DisableSupport.holder(ExampleMod.ROOTED),
                    maxTicks + CrucioRules.HEARTBEAT_TIMEOUT, 0, false, false, false));
        }
        CHANNELS.put(owner.getUUID(), channel);
    }

    public static void heartbeat(ServerPlayer player) {
        Channel channel = CHANNELS.get(player.getUUID());
        if (channel != null) {
            channel.lastSeen = player.level().getGameTime();
        }
    }

    public static void release(ServerPlayer player) {
        Channel channel = CHANNELS.get(player.getUUID());
        if (channel != null && player.getServer() != null) {
            end(player.getServer(), channel);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CHANNELS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Channel channel : new ArrayList<>(CHANNELS.values())) {
            tickChannel(server, channel);
        }
    }

    private static void tickChannel(MinecraftServer server, Channel channel) {
        if (CHANNELS.get(channel.casterId) != channel) {
            return; // already ended or replaced this tick
        }
        ServerLevel level = server.getLevel(channel.dimension);
        LivingEntity caster = level == null ? null : living(level.getEntity(channel.casterId));
        LivingEntity target = level == null ? null : living(level.getEntity(channel.targetId));
        if (level == null || caster == null || target == null) {
            end(server, channel);
            return;
        }
        long now = level.getGameTime();
        long elapsed = now - channel.startedAt;
        boolean over = elapsed >= channel.maxTicks
                || MindControl.isControlled(caster) || MindControl.isControlled(target)
                || !caster.hasLineOfSight(target)
                || (channel.held && !caster.hasEffect(DisableSupport.holder(ExampleMod.ROOTED)))
                || caster.distanceToSqr(target) > CrucioRules.MAX_RANGE * CrucioRules.MAX_RANGE
                || (channel.held && (CrucioRules.heartbeatLapsed(now, channel.lastSeen) || !holdsCrucio(caster)));
        if (over) {
            end(server, channel);
            return;
        }
        if (CrucioRules.isDamageTick(elapsed)) {
            target.hurt(DamageSupport.magicFrom(target, caster), CrucioRules.DAMAGE);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + target.getBbHeight() * 0.6,
                    target.getZ(), 4, 0.25, 0.25, 0.25, 0.1);
        }
        if (elapsed % 4 == 0) {
            Vec3 from = caster.getEyePosition();
            Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
            for (int i = 1; i <= 6; i++) {
                Vec3 point = from.lerp(to, i / 7.0);
                level.sendParticles(RED, point.x, point.y, point.z, 1, 0.03, 0.03, 0.03, 0.0);
            }
        }
    }

    private static boolean holdsCrucio(LivingEntity caster) {
        ItemStack wand = caster.getMainHandItem();
        return MagicWandItem.isAnyWand(wand) && MagicWandItem.getSelectedSpell(wand) == SpellType.CRUCIO;
    }

    private static void end(MinecraftServer server, Channel channel) {
        if (!CHANNELS.remove(channel.casterId, channel)) {
            return;
        }
        ServerPlayer casterPlayer = channel.held ? server.getPlayerList().getPlayer(channel.casterId) : null;
        if (casterPlayer != null) {
            if (!PossessionManager.isPossessing(casterPlayer)) {
                casterPlayer.removeEffect(DisableSupport.holder(ExampleMod.ROOTED)); // also after a dimension change
            }
        }
        ServerLevel level = server.getLevel(channel.dimension);
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        LivingEntity caster = living(level.getEntity(channel.casterId));
        LivingEntity target = living(level.getEntity(channel.targetId));
        if (target == null) {
            target = living(server.getPlayerList().getPlayer(channel.targetId)); // a player who changed dimension
        }
        if (caster != null && channel.held && !PossessionManager.isPossessing(caster)) {
            caster.removeEffect(DisableSupport.holder(ExampleMod.ROOTED));
        }
        if (target == null) {
            return;
        }
        long stunLeft = channel.stunEndsAt - now;
        MobEffectInstance stun = target.getEffect(DisableSupport.holder(ExampleMod.STUNNED));
        if (stun != null && stunLeft > 0 && stun.getDuration() <= stunLeft + 1) {
            target.removeEffect(DisableSupport.holder(ExampleMod.STUNNED)); // ours, not a longer stun from someone else
        }
        if (caster != null && CrucioRules.fleesAfter(channel.rank) && target instanceof Mob mob && !CombatTags.isBoss(mob)
                && !MindControl.isControlled(mob)) {
            FLEEING.put(mob.getUUID(), new Flee(caster.getUUID(), now + CrucioRules.FLEE_TICKS));
            MindControl.setTargetBypassing(mob, null);
            MindControl.clearBrainTarget(mob, caster);
        }
    }

    @Nullable
    private static LivingEntity living(@Nullable Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /** Master: run from the caster, re-pathing every half second, until the time is up. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (FLEEING.isEmpty() || !(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()
                || mob.tickCount % 10 != 0 || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        Flee flee = FLEEING.get(mob.getUUID());
        if (flee == null) {
            return;
        }
        Entity from = level.getEntity(flee.from());
        if (from == null || !mob.isAlive() || level.getGameTime() >= flee.until()) {
            FLEEING.remove(mob.getUUID());
            return;
        }
        if (mob.getTarget() == from) {
            MindControl.setTargetBypassing(mob, null);
        }
        MindControl.clearBrainTarget(mob, from instanceof LivingEntity livingFrom ? livingFrom : null);
        Vec3 away = mob instanceof PathfinderMob pathfinder
                ? DefaultRandomPos.getPosAway(pathfinder, 16, 7, from.position())
                : null;
        if (away == null) {
            Vec3 flat = mob.position().subtract(from.position()).multiply(1.0, 0.0, 1.0);
            away = mob.position().add(flat.lengthSqr() < 1.0E-4 ? new Vec3(8.0, 0.0, 0.0) : flat.normalize().scale(12.0));
        }
        mob.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
    }

    /** A fleeing mob doesn't turn back on the caster. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (FLEEING.isEmpty() || event.getNewTarget() == null || MindControl.isBypassingTargetRules()
                || !(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) {
            return;
        }
        Flee flee = FLEEING.get(mob.getUUID());
        if (flee != null && event.getNewTarget().getUUID().equals(flee.from())
                && mob.level().getGameTime() < flee.until()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Channel channel : new ArrayList<>(CHANNELS.values())) {
            end(event.getServer(), channel);
        }
        CHANNELS.clear();
        FLEEING.clear();
    }

    /** A death on either side ends the channel (and lifts the stun it put on the survivor). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || CHANNELS.isEmpty() || event.getEntity().level().isClientSide()
                || event.getEntity().getServer() == null) {
            return;
        }
        endInvolving(event.getEntity().getServer(), event.getEntity().getUUID());
        FLEEING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null && !CHANNELS.isEmpty()) {
            endInvolving(player.getServer(), player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null && !CHANNELS.isEmpty()) {
            endInvolving(player.getServer(), player.getUUID());
        }
    }

    private static void endInvolving(MinecraftServer server, UUID id) {
        for (Channel channel : new ArrayList<>(CHANNELS.values())) {
            if (channel.casterId.equals(id) || channel.targetId.equals(id)) {
                end(server, channel);
            }
        }
    }
}
