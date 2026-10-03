package com.hogwartsmagic.spell.mind;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.SpellCosts;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.disable.DisableSupport;
import com.hogwartsmagic.spell.mastery.MasteryAwards;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Imperio ranks I-III: the thrall registry. A mob thrall gets three temporary goals (follow, assist,
 * brawl if it has no attack of its own) and only fights what its owner fights; a player thrall is walked
 * by the server each tick toward the owner or the owner's fight and swings with Player.attack. Everything
 * lives in memory on the server; a thrall that unloads, dies, logs out or changes dimension simply ends,
 * and EntityJoinLevelEvent strips anything it carried (the allied tag, the imperius marker) when it comes
 * back. Imperio's cooldown starts, and the cast earns its XP, when the owner's last thrall ends.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ImperioThralls {
    private static final String TAG_ADDED_ALLIED = ExampleMod.MODID + ":thrall_added_allied";
    private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.45f, 1.0f, 0.45f), 0.9f);

    static final class Thrall {
        final UUID victimId;
        final UUID ownerId;
        final String ownerName;
        final ResourceKey<Level> dimension;
        final boolean refreshable;
        long expiresAt;
        long strengthUntil;
        double lastY;
        boolean addedStrength;
        boolean addedAllied;
        /** Last velocity sent to a player thrall's client, and when (the throttle in ImperioRules.needsPush). */
        boolean pushed;
        double pushX;
        double pushY;
        double pushZ;
        long pushAt;
        final List<Goal> goals = new ArrayList<>();
        final List<Goal> targetGoals = new ArrayList<>();

        Thrall(UUID victimId, UUID ownerId, String ownerName, ResourceKey<Level> dimension, boolean refreshable, long expiresAt) {
            this.victimId = victimId;
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.dimension = dimension;
            this.refreshable = refreshable;
            this.expiresAt = expiresAt;
        }
    }

    /** "{@code other} was involved at game time {@code at}" for the assist logic. */
    private record Note(UUID other, long at) {
    }

    private static final Map<UUID, Thrall> THRALLS = new HashMap<>();
    /** attacker -> whom it last hit with a spell (only kept for owners). */
    private static final Map<UUID, Note> LAST_ATTACKED = new HashMap<>();
    /** victim -> who last hit it with a spell (only kept for owners). */
    private static final Map<UUID, Note> LAST_ATTACKER = new HashMap<>();
    /** "owner:victim" -> game time until which that owner can't enthrall that boss or player again. */
    private static final Map<String, Long> LOCKOUTS = new HashMap<>();

    private ImperioThralls() {
    }

    // ---- Queries -------------------------------------------------------------------------------

    public static boolean isThrall(Entity entity) {
        return THRALLS.containsKey(entity.getUUID());
    }

    /** The owner of {@code entity} if it is a thrall (in-memory map only), else null. */
    @Nullable
    public static UUID ownerIdOf(Entity entity) {
        Thrall thrall = THRALLS.get(entity.getUUID());
        return thrall == null ? null : thrall.ownerId;
    }

    public static int countFor(UUID ownerId) {
        int count = 0;
        for (Thrall thrall : THRALLS.values()) {
            if (thrall.ownerId.equals(ownerId)) {
                count++;
            }
        }
        return count;
    }

    /** The cast-path gate: ranks I-III can't cast (or pay) while every thrall slot is taken. */
    public static boolean mayCast(ServerPlayer caster) {
        int rank = SpellMasteryData.getRank(caster, SpellType.IMPERIO);
        if (ImperioRules.isPossessionRank(rank) || !ImperioRules.atCap(rank, countFor(caster.getUUID()))) {
            return true;
        }
        MindFx.tell(caster, "imperio.at_cap", ImperioRules.maxThralls(rank));
        return false;
    }

    @Nullable
    static LivingEntity ownerOf(Mob mob, UUID ownerId) {
        return mob.level() instanceof ServerLevel level ? resolveOwner(level, ownerId) : null;
    }

    @Nullable
    private static LivingEntity resolveOwner(ServerLevel level, UUID ownerId) {
        return level.getEntity(ownerId) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    @Nullable
    private static LivingEntity living(@Nullable Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    // ---- Enthralling ---------------------------------------------------------------------------

    public static boolean enthrall(LivingEntity owner, LivingEntity target, int rank) {
        if (!(target.level() instanceof ServerLevel level) || owner == target || !target.isAlive() || !owner.isAlive()) {
            return false;
        }
        boolean player = target instanceof Player;
        if (player) {
            Player victim = (Player) target;
            if (!Config.isImperioAffectsPlayers()) {
                MindFx.tell(owner, "imperio.players_off");
                return false;
            }
            if (victim.isCreative() || victim.isSpectator()) {
                return false;
            }
            if (!pvpAllowed(owner, victim)) {
                MindFx.tell(owner, "imperio.pvp_off");
                return false;
            }
        } else if (!(target instanceof Mob)) {
            return false;
        }
        boolean boss = CombatTags.isBoss(target);
        if (!ImperioRules.canEnthrall(rank, target.getMaxHealth(), boss, player)) {
            MindFx.tell(owner, "imperio.too_strong", target.getDisplayName());
            MindFx.burst(target, ParticleTypes.SMOKE);
            return false;
        }
        long now = level.getGameTime();
        int ticks = ImperioRules.thrallTicks(rank, player || boss);
        if (!ImperioRules.mayRefresh(player, boss)) {
            Long until = LOCKOUTS.get(lockKey(owner.getUUID(), target.getUUID()));
            if (until != null && ImperioRules.lockedOut(until, now)) {
                MindFx.tell(owner, "imperio.locked_out", target.getDisplayName());
                return false;
            }
        }
        Thrall existing = THRALLS.get(target.getUUID());
        if (existing != null) {
            if (!existing.ownerId.equals(owner.getUUID())) {
                MindFx.resisted(target, owner);
                return false;
            }
            if (!existing.refreshable) {
                return false; // a player or boss thrall can't be held on by re-casting
            }
            existing.expiresAt = now + ticks;
            existing.strengthUntil = now + ticks;
            addMarker(target, ticks);
            if (existing.addedStrength) {
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0));
            }
            return true;
        }
        if (ImperioRules.atCap(rank, countFor(owner.getUUID()))) {
            MindFx.tell(owner, "imperio.at_cap", ImperioRules.maxThralls(rank));
            return false;
        }
        String ownerName = owner.getName().getString();
        if (ownerName.length() > 64) {
            ownerName = ownerName.substring(0, 64);
        }
        Thrall thrall = new Thrall(target.getUUID(), owner.getUUID(), ownerName, level.dimension(), ImperioRules.mayRefresh(player, boss), now + ticks);
        thrall.lastY = target.getY();
        thrall.strengthUntil = now + ticks;
        addMarker(target, ticks);
        MindControl.closeOpenContainer(target); // a thrall player's open chest or GUI shuts
        if (target instanceof Mob mob) {
            setUpMob(mob, owner, thrall, rank, ticks);
        }
        CrucioChannel.stopFleeing(target.getUUID());
        if (target instanceof Mob confusedMob) {
            ConfundoSpell.clearConfusion(confusedMob);
        }
        THRALLS.put(target.getUUID(), thrall);
        broadcastLabel(target, thrall.ownerName);
        MindFx.burst(target, GREEN);
        MindFx.tell(owner, "imperio.thrall_gained", target.getDisplayName());
        return true;
    }

    /** A player owner may only turn or send a thrall at another player where the server allows PvP. */
    private static boolean pvpAllowed(LivingEntity owner, Player victim) {
        if (!(owner instanceof ServerPlayer ownerPlayer)) {
            return true;
        }
        MinecraftServer server = ownerPlayer.serverLevel().getServer();
        return server.isPvpAllowed() && ownerPlayer.canHarmPlayer(victim);
    }

    private static void addMarker(LivingEntity target, int ticks) {
        target.addEffect(new MobEffectInstance(DisableSupport.holder(ExampleMod.IMPERIUS), ticks + 5, 0, false, false, false));
    }

    private static void setUpMob(Mob mob, LivingEntity owner, Thrall thrall, int rank, int ticks) {
        Goal follow = new ThrallGoals.Follow(mob, thrall.ownerId);
        Goal assist = new ThrallGoals.Assist(mob, thrall.ownerId);
        mob.goalSelector.addGoal(2, follow);
        mob.targetSelector.addGoal(0, assist);
        thrall.goals.add(follow);
        thrall.targetGoals.add(assist);
        if (mob instanceof PathfinderMob pathfinder && mob.getAttribute(Attributes.ATTACK_DAMAGE) == null) {
            Goal brawl = new ThrallGoals.Brawl(pathfinder);
            mob.goalSelector.addGoal(2, brawl);
            thrall.goals.add(brawl);
        }
        if (owner instanceof Player && !mob.getTags().contains(CombatTags.ALLIED)) {
            mob.addTag(CombatTags.ALLIED);
            mob.getPersistentData().putBoolean(TAG_ADDED_ALLIED, true);
            thrall.addedAllied = true;
        }
        if (ImperioRules.grantsStrength(rank) && !mob.hasEffect(MobEffects.DAMAGE_BOOST)) {
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0));
            thrall.addedStrength = true;
        }
        MindControl.setTargetBypassing(mob, null);
        MindControl.clearBrainTarget(mob, owner);
        mob.setLastHurtByMob(null);
        if (mob instanceof NeutralMob neutral) {
            neutral.stopBeingAngry();
        }
        mob.getNavigation().stop();
    }

    // ---- The owner's fight -------------------------------------------------------------------

    /** Record a spell hit (from MindHooks); melee and arrows already land in vanilla's last-hurt fields. */
    public static void noteAttack(@Nullable Entity caster, LivingEntity target) {
        Entity attacker = CombatEffects.responsible(caster);
        if (attacker == null || attacker == target || target.level().isClientSide()) {
            return;
        }
        long now = target.level().getGameTime();
        if (countFor(attacker.getUUID()) > 0) {
            LAST_ATTACKED.put(attacker.getUUID(), new Note(target.getUUID(), now));
        }
        if (countFor(target.getUUID()) > 0) {
            LAST_ATTACKER.put(target.getUUID(), new Note(attacker.getUUID(), now));
        }
    }

    /** Whoever hurt the owner in the last 5s, else whatever the owner hurt (a mob owner: its target). */
    @Nullable
    static LivingEntity pickTarget(LivingEntity owner, LivingEntity self) {
        long now = self.level().getGameTime();
        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker != null && ImperioRules.fresh(owner.tickCount, owner.getLastHurtByMobTimestamp())
                && isFairTarget(owner, self, attacker)) {
            return attacker;
        }
        LivingEntity spellAttacker = noted(LAST_ATTACKER.get(owner.getUUID()), self, now);
        if (isFairTarget(owner, self, spellAttacker)) {
            return spellAttacker;
        }
        LivingEntity victim = owner instanceof Mob mobOwner
                ? mobOwner.getTarget()
                : (ImperioRules.fresh(owner.tickCount, owner.getLastHurtMobTimestamp()) ? owner.getLastHurtMob() : null);
        if (isFairTarget(owner, self, victim)) {
            return victim;
        }
        LivingEntity spellVictim = noted(LAST_ATTACKED.get(owner.getUUID()), self, now);
        return isFairTarget(owner, self, spellVictim) ? spellVictim : null;
    }

    @Nullable
    private static LivingEntity noted(@Nullable Note note, LivingEntity self, long now) {
        if (note == null || !ImperioRules.fresh(now, note.at()) || !(self.level() instanceof ServerLevel level)) {
            return null;
        }
        return living(level.getEntity(note.other()));
    }

    /** Never the owner, itself, the owner's team or other thralls and allies, creative players, or anything far off. */
    static boolean isFairTarget(LivingEntity owner, LivingEntity self, @Nullable LivingEntity candidate) {
        if (candidate == null || !candidate.isAlive() || candidate == owner || candidate == self
                || candidate.level() != self.level()) {
            return false;
        }
        if (candidate instanceof Player player && (player.isCreative() || player.isSpectator() || !pvpAllowed(owner, player))) {
            return false;
        }
        if (candidate.isAlliedTo(owner) || (owner instanceof Player && candidate.getTags().contains(CombatTags.ALLIED))) {
            return false;
        }
        Thrall other = THRALLS.get(candidate.getUUID());
        if (other != null && other.ownerId.equals(owner.getUUID())) {
            return false;
        }
        double max = ImperioRules.MAX_TARGET_DISTANCE;
        return candidate.distanceToSqr(self) <= max * max;
    }

    // ---- Ticking -------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (!THRALLS.isEmpty()) {
            for (Thrall thrall : new ArrayList<>(THRALLS.values())) {
                tickThrall(server, thrall);
            }
        }
        if (server.getTickCount() % 100 == 0) {
            pruneNotes(server.overworld().getGameTime());
        }
    }

    /** Stunned (Stupefy, Petrificus, Glacius, Crucio) or hung/chained: a thrall must not walk or teleport out of it. */
    static boolean pinned(LivingEntity e) {
        return com.hogwartsmagic.spell.combat.CombatEffects.isStunned(e)
                || com.hogwartsmagic.event.ModEvents.isHeldInPlace(e);
    }

    private static void tickThrall(MinecraftServer server, Thrall thrall) {
        ServerLevel level = server.getLevel(thrall.dimension);
        LivingEntity victim = level == null ? null : living(level.getEntity(thrall.victimId));
        if (level == null || victim == null) {
            THRALLS.remove(thrall.victimId);
            if (level != null) {
                dropLabel(level.getEntity(thrall.victimId));
            }
            afterRelease(server, thrall);
            return;
        }
        LivingEntity owner = resolveOwner(level, thrall.ownerId);
        long now = level.getGameTime();
        if (owner == null || now >= thrall.expiresAt) {
            end(server, thrall, victim);
            return;
        }
        if (victim instanceof Mob mob) {
            tickMob(mob, owner, now);
        }
        if (victim instanceof ServerPlayer player) {
            if (pinned(player)) {
                thrall.lastY = player.getY();
                thrall.pushed = false;
            } else {
                drivePlayer(thrall, player, owner);
            }
        }
        if (now % 10 == 0) {
            level.sendParticles(GREEN, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                    3, victim.getBbWidth() * 0.4, victim.getBbHeight() * 0.3, victim.getBbWidth() * 0.4, 0.0);
        }
    }

    /**
     * Keeps a mob thrall on its owner's side. Goal-driven mobs get their target from ThrallGoals.Assist and
     * the change-target filter; a brain-driven mob (Piglin, Hoglin, Breeze, Axolotl, Warden...) reads
     * ATTACK_TARGET from its brain, which no goal and no event touches, so it is checked and set here, and it
     * follows the owner by navigation (the Follow goal does not steer a brain mob).
     */
    private static void tickMob(Mob mob, LivingEntity owner, long now) {
        LivingEntity target = mob.getTarget();
        if (target != null && !isFairTarget(owner, mob, target)) {
            MindControl.setTargetBypassing(mob, null);
        }
        if (!hasBrainTarget(mob)) {
            return;
        }
        if ((now & 1L) == 0L) {
            LivingEntity pick = pickTarget(owner, mob);
            LivingEntity current = brainTarget(mob);
            if (current != null && current != pick
                    && !(isFairTarget(owner, mob, current) && current == mob.getLastHurtByMob())) {
                MindControl.clearBrainTarget(mob, current);
                MindControl.setTargetBypassing(mob, null);
                current = null;
            }
            if (pick != null && pick != current) {
                MindControl.setTargetBypassing(mob, pick);
                MindControl.setBrainTarget(mob, pick);
            }
        }
        if (now % 10 == 0 && mob.getTarget() == null && brainTarget(mob) == null && !pinned(mob)) {
            double start = ImperioRules.FOLLOW_START;
            double dist = mob.distanceToSqr(owner);
            if (dist > ImperioRules.TELEPORT_DISTANCE * ImperioRules.TELEPORT_DISTANCE) {
                teleportNear(mob, owner);
            } else if (dist > start * start) {
                mob.getNavigation().moveTo(owner, 1.2);
            }
        }
    }

    private static boolean hasBrainTarget(Mob mob) {
        return mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED);
    }

    @Nullable
    private static LivingEntity brainTarget(Mob mob) {
        Brain<?> brain = mob.getBrain();
        return brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)
                ? brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) : null;
    }

    /** Walk the player toward the owner's fight (or the owner), and swing when in reach and recharged. */
    private static void drivePlayer(Thrall thrall, ServerPlayer victim, LivingEntity owner) {
        LivingEntity fight = pickTarget(owner, victim);
        LivingEntity goal = fight != null ? fight : owner;
        double previousY = thrall.lastY;
        thrall.lastY = victim.getY();
        double dist = victim.distanceTo(goal);
        if (fight == null && dist > ImperioRules.TELEPORT_DISTANCE) {
            if (teleportNear(victim, owner)) {
                thrall.lastY = victim.getY();
                thrall.pushed = false;
            }
            return;
        }
        Vec3 flat = new Vec3(goal.getX() - victim.getX(), 0.0, goal.getZ() - victim.getZ());
        if (flat.lengthSqr() > 1.0E-4) {
            float yaw = (float) (Mth.atan2(flat.z, flat.x) * (180.0 / Math.PI)) - 90.0F;
            victim.setYRot(yaw);
            victim.setYHeadRot(yaw);
        }
        double stopAt = fight != null ? ImperioRules.PLAYER_REACH - 0.4 : ImperioRules.FOLLOW_STOP;
        double vy = ForcedPlayerMovement.verticalVelocity(victim, previousY, victim.horizontalCollision);
        double wx = 0.0;
        double wz = 0.0;
        if (dist > stopAt && flat.lengthSqr() > 1.0E-4) {
            double speed = fight != null ? ImperioRules.PLAYER_CHASE_SPEED : ImperioRules.PLAYER_WALK_SPEED;
            Vec3 velocity = flat.normalize().scale(speed);
            wx = velocity.x;
            wz = velocity.z;
        }
        if ((wx != 0.0 || wz != 0.0) && unsafeStep(victim, wx, wz)) {
            wx = 0.0;
            wz = 0.0;
        }
        long now = victim.level().getGameTime();
        if (ImperioRules.needsPush(thrall.pushed, thrall.pushX, thrall.pushY, thrall.pushZ, wx, vy, wz, thrall.pushAt, now)) {
            ForcedPlayerMovement.push(victim, wx, vy, wz);
            thrall.pushed = true;
            thrall.pushX = wx;
            thrall.pushY = vy;
            thrall.pushZ = wz;
            thrall.pushAt = now;
        }
        if (fight != null && dist <= ImperioRules.PLAYER_REACH && victim.getAttackStrengthScale(0.5F) >= 0.9F) {
            MindControl.forcedAttack(victim, fight);
        }
    }

    /** Like a wolf: a random standable spot within 3 blocks of the owner; false if none was free. */
    public static boolean teleportNear(LivingEntity mover, LivingEntity owner) {
        if (mover.level() != owner.level()) {
            return false;
        }
        RandomSource random = mover.getRandom();
        for (int attempt = 0; attempt < 10; attempt++) {
            double x = Mth.floor(owner.getX()) + random.nextInt(7) - 3 + 0.5;
            double z = Mth.floor(owner.getZ()) + random.nextInt(7) - 3 + 0.5;
            double y = Mth.floor(owner.getY());
            if (Math.abs(x - owner.getX()) < 1.5 && Math.abs(z - owner.getZ()) < 1.5) {
                continue;
            }
            BlockPos below = BlockPos.containing(x, y - 1, z);
            if (!mover.level().isLoaded(below)) {
                continue;
            }
            if (!mover.level().getBlockState(below).isFaceSturdy(mover.level(), below, Direction.UP)) {
                continue;
            }
            BlockPos feet = BlockPos.containing(x, y, z);
            if (hazard(mover.level(), below) || hazard(mover.level(), feet) || hazard(mover.level(), feet.above())) {
                continue;
            }
            if (mover instanceof Mob walker && WalkNodeEvaluator.getPathTypeStatic(walker, feet) != PathType.WALKABLE) {
                continue;
            }
            AABB box = mover.getBoundingBox().move(x - mover.getX(), y - mover.getY(), z - mover.getZ());
            if (!mover.level().noCollision(mover, box)) {
                continue;
            }
            if (mover instanceof ServerPlayer player) {
                player.teleportTo(x, y, z);
            } else {
                mover.teleportTo(x, y, z);
            }
            if (mover instanceof Mob mob) {
                mob.getNavigation().stop();
            }
            return true;
        }
        return false;
    }

    private static void pruneNotes(long now) {
        long horizon = ImperioRules.ASSIST_MEMORY_TICKS * 2L;
        LAST_ATTACKED.values().removeIf(note -> now - note.at() > horizon);
        LAST_ATTACKER.values().removeIf(note -> now - note.at() > horizon);
    }

    // ---- Release -------------------------------------------------------------------------------

    private static void end(MinecraftServer server, Thrall thrall, LivingEntity victim) {
        THRALLS.remove(thrall.victimId);
        release(victim, thrall);
        afterRelease(server, thrall);
    }

    /** Undo everything on a victim that is still in the world. */
    private static void release(LivingEntity victim, Thrall thrall) {
        if (victim instanceof Mob mob) {
            for (Goal goal : thrall.goals) {
                mob.goalSelector.removeGoal(goal);
            }
            for (Goal goal : thrall.targetGoals) {
                mob.targetSelector.removeGoal(goal);
            }
            if (thrall.addedAllied) {
                mob.removeTag(CombatTags.ALLIED);
                mob.getPersistentData().remove(TAG_ADDED_ALLIED);
            }
            if (thrall.addedStrength) {
                MobEffectInstance strength = mob.getEffect(MobEffects.DAMAGE_BOOST);
                long left = Math.max(0L, thrall.strengthUntil - mob.level().getGameTime());
                if (strength != null && strength.getAmplifier() == 0 && strength.getDuration() <= left + 2L) {
                    mob.removeEffect(MobEffects.DAMAGE_BOOST);
                }
            }
            if (mob instanceof NeutralMob neutral) {
                neutral.stopBeingAngry();
            }
            MindControl.setTargetBypassing(mob, null);
            MindControl.clearBrainTarget(mob, null);
            mob.setLastHurtByMob(null);
            mob.getNavigation().stop();
        }
        Holder<MobEffect> marker = DisableSupport.holder(ExampleMod.IMPERIUS);
        if (victim.hasEffect(marker)) {
            victim.removeEffect(marker);
        }
        dropLabel(victim);
    }

    /** The "Thrall of <owner>" nameplate for everyone watching the victim (and the victim, if a player). */
    private static void broadcastLabel(Entity victim, String ownerName) {
        SpellPackets.CHANNEL.send(new SpellPackets.ThrallLabelPacket(victim.getId(), ownerName),
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(victim));
    }

    /** Every path that drops a thrall clears its nameplate through here (the empty name). */
    private static void dropLabel(@Nullable Entity victim) {
        if (victim != null && !(victim instanceof ServerPlayer player && player.connection == null)) {
            broadcastLabel(victim, "");
        }
    }

    /** Someone walks into view of a thrall after it was enthralled. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        Thrall thrall = THRALLS.get(event.getTarget().getUUID());
        if (thrall != null && event.getEntity() instanceof ServerPlayer watcher) {
            SpellPackets.CHANNEL.send(new SpellPackets.ThrallLabelPacket(event.getTarget().getId(), thrall.ownerName),
                    PacketDistributor.PLAYER.with(watcher));
        }
    }

    /** The owner's last thrall is gone: Imperio's cooldown starts now. */
    private static void afterRelease(MinecraftServer server, Thrall thrall) {
        ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(thrall.ownerId);
        if (!thrall.refreshable) {
            recordLockout(server, thrall, ownerPlayer);
        }
        if (ownerPlayer != null && countFor(thrall.ownerId) == 0) {
            startImperioCooldown(ownerPlayer);
        }
    }

    private static String lockKey(UUID ownerId, UUID victimId) {
        return ownerId + ":" + victimId;
    }

    /** A boss or player thrall ended: its owner can't take that same victim again for one Imperio cooldown. */
    private static void recordLockout(MinecraftServer server, Thrall thrall, @Nullable ServerPlayer ownerPlayer) {
        long now = server.overworld().getGameTime();
        LOCKOUTS.values().removeIf(until -> !ImperioRules.lockedOut(until, now));
        double multiplier = ownerPlayer == null ? 1.0 : SpellCosts.masteryMultiplier(ownerPlayer, SpellType.IMPERIO);
        long ticks = ImperioRules.lockoutTicks(SpellType.IMPERIO.getCooldownTicks(), multiplier);
        LOCKOUTS.put(lockKey(thrall.ownerId, thrall.victimId), now + ticks);
    }

    /**
     * Imperio's cooldown on every wand the player carries (so swapping wands can't dodge it), and the cast's
     * Imperio XP once. Called when the last thrall ends and (Phase 6b) when a possession ends.
     */
    public static void startImperioCooldown(ServerPlayer owner) {
        if (owner.isCreative()) {
            return;
        }
        long now = owner.level().getGameTime();
        ItemStack main = owner.getMainHandItem();
        boolean awarded = false;
        if (MagicWandItem.isAnyWand(main)) {
            SpellCosts.startCooldown(owner, main, SpellType.IMPERIO, now);
            awarded = true;
        }
        Inventory inventory = owner.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != main && MagicWandItem.isAnyWand(stack)) {
                SpellCosts.startCooldown(owner, stack, SpellType.IMPERIO, now, SpellType.IMPERIO.getCooldownTicks(), false);
            }
        }
        if (!awarded) {
            MasteryAwards.onCastCompleted(owner, SpellType.IMPERIO);
        }
    }

    // ---- Events --------------------------------------------------------------------------------

    /** A thrall picks no fights of its own; it may only take its owner's fight or hit back at its attacker. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewTarget();
        LivingEntity self = event.getEntity();
        if (event.isCanceled() || newTarget == null || MindControl.isBypassingTargetRules() || !(self.level() instanceof ServerLevel level)) {
            return;
        }
        Thrall thrall = THRALLS.get(self.getUUID());
        if (thrall == null) {
            return;
        }
        LivingEntity owner = resolveOwner(level, thrall.ownerId);
        if (owner == null || !isFairTarget(owner, self, newTarget) || newTarget != self.getLastHurtByMob()) {
            event.setCanceled(true);
        }
    }

    /**
     * The victim is leaving (unload, death, logout, portal). Don't touch its goals here (this can fire from
     * inside its own AI tick); EntityJoinLevelEvent strips the marker and the tag if it ever comes back.
     */
    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Thrall thrall = THRALLS.remove(event.getEntity().getUUID());
        if (thrall != null) {
            dropLabel(event.getEntity());
            afterRelease(level.getServer(), thrall);
        }
    }

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity living)
                || THRALLS.containsKey(living.getUUID())) {
            return;
        }
        if (living.getTags().contains(CombatTags.ALLIED) && living.getPersistentData().getBoolean(TAG_ADDED_ALLIED)) {
            living.removeTag(CombatTags.ALLIED);
            living.getPersistentData().remove(TAG_ADDED_ALLIED);
        }
        Holder<MobEffect> imperius = ExampleMod.IMPERIUS.getHolder().orElse(null);
        if (imperius != null && living.hasEffect(imperius)) {
            living.removeEffect(imperius);
        }
    }

    /** A thrall's blows (melee, arrows, blasts, sweeps) never land on its owner or the owner's other thralls. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onThrallAttack(LivingAttackEvent event) {
        if (THRALLS.isEmpty() || event.isCanceled() || event.getEntity().level().isClientSide()) {
            return;
        }
        Entity source = event.getSource().getEntity() != null ? event.getSource().getEntity() : event.getSource().getDirectEntity();
        Entity attacker = CombatEffects.responsible(source);
        Thrall thrall = attacker == null ? null : THRALLS.get(attacker.getUUID());
        if (thrall == null) {
            return;
        }
        LivingEntity victim = event.getEntity();
        Thrall other = THRALLS.get(victim.getUUID());
        if (victim.getUUID().equals(thrall.ownerId) || (other != null && other.ownerId.equals(thrall.ownerId))) {
            event.setCanceled(true);
        }
    }

    private static boolean hazard(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getFluidState().isEmpty() || state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW);
    }

    /** Would the next step in this direction land in lava, fire or a damaging block, or off a drop over 3 blocks? */
    private static boolean unsafeStep(LivingEntity mover, double dx, double dz) {
        Level level = mover.level();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) {
            return false;
        }
        BlockPos feet = BlockPos.containing(mover.getX() + dx / len * 1.2, mover.getY() + 0.1, mover.getZ() + dz / len * 1.2);
        if (!level.isLoaded(feet)) {
            return true;
        }
        if (hazard(level, feet) || hazard(level, feet.above())) {
            return true;
        }
        for (int depth = 1; depth <= 4; depth++) {
            BlockPos floor = feet.below(depth);
            BlockState state = level.getBlockState(floor);
            if (!state.getCollisionShape(level, floor).isEmpty() || !state.getFluidState().isEmpty()) {
                return hazard(level, floor);
            }
        }
        return true;
    }

    /** The marker was stripped early (Totem of Undying, /effect clear, milk): the thrall ends on the next tick. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.isCanceled() || event.getEffect() != ExampleMod.IMPERIUS.get()
                || event.getEntity().level().isClientSide()) {
            return;
        }
        // Never change effects here: removeAllEffects fires this inside its own effect iterator. The tick ends it.
        Thrall thrall = THRALLS.get(event.getEntity().getUUID());
        if (thrall != null) {
            thrall.expiresAt = 0L;
        }
    }

    /** Runs before the worlds save, so no thrall is written to disk still carrying the allied tag. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        Iterator<Thrall> iterator = new ArrayList<>(THRALLS.values()).iterator();
        while (iterator.hasNext()) {
            Thrall thrall = iterator.next();
            ServerLevel level = event.getServer().getLevel(thrall.dimension);
            LivingEntity victim = level == null ? null : living(level.getEntity(thrall.victimId));
            THRALLS.remove(thrall.victimId);
            if (victim != null) {
                release(victim, thrall);
            }
        }
        THRALLS.clear();
        LAST_ATTACKED.clear();
        LAST_ATTACKER.clear();
        LOCKOUTS.clear();
    }
}
