package com.hogwartsmagic.spell.motion;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.BasiliskVenomEntity;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Arresto Momentum from Expert (Spell Mastery Phase 7b): arrows, tridents, fireballs and Basilisk venom near
 * where the charm lands stop in midair for FREEZE_TICKS, then drop. It never stops a spell: no SpellBeamEntity
 * of any kind (Avada or otherwise) is frozen (user ruling 2026-10-03). Records live per dimension and hold only
 * UUIDs and positions. A frozen projectile is snapped back to its spot at the end of each level tick (its position
 * doesn't change, so nothing is re-sent); it gets one motion packet when it freezes and one when it drops. Each
 * carries FROZEN_TAG while frozen, so if it is saved or unloaded mid-freeze it drops
 * when it loads again. It never sends anything back. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArrestoFreeze {
    /** Entity tag (saved with the entity) on every frozen projectile. */
    private static final String FROZEN_TAG = "hogwartsmagic:arresto_frozen";
    /** Second entity tag: the projectile already had NoGravity before it was frozen. */
    private static final String FROZEN_NG_TAG = "hogwartsmagic:arresto_frozen_ng";
    private static final Map<ResourceKey<Level>, Map<UUID, Frozen>> FROZEN = new HashMap<>();

    private record Frozen(Vec3 at, long until, boolean hadNoGravity) {
    }

    private ArrestoFreeze() {
    }

    public static int freezeAround(ServerLevel level, Vec3 center, @Nullable Entity caster) {
        int rank = DisableSupport.casterRank(caster, SpellType.ARRESTO_MOMENTUM);
        if (!ArrestoRules.freezes(rank)) {
            return 0;
        }
        double radius = ArrestoRules.freezeRadius(rank);
        Map<UUID, Frozen> frozen = FROZEN.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        long now = level.getGameTime();
        long until = now + ArrestoRules.FREEZE_TICKS;
        int count = 0;
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, new AABB(center, center).inflate(radius),
                candidate -> isFreezable(candidate) && candidate.position().distanceToSqr(center) <= radius * radius)) {
            if (frozen.size() >= ArrestoRules.MAX_FROZEN_PER_LEVEL) {
                break;
            }
            if (frozen.containsKey(projectile.getUUID())) {
                continue;
            }
            boolean hadNoGravity = projectile.isNoGravity();
            frozen.put(projectile.getUUID(), new Frozen(projectile.position(), until, hadNoGravity));
            projectile.addTag(FROZEN_TAG); // fireballs too: a saved one must not hang motionless forever
            if (hadNoGravity) {
                projectile.addTag(FROZEN_NG_TAG); // so a restore puts NoGravity back exactly as it was
            }
            projectile.setNoGravity(true);
            projectile.setDeltaMovement(Vec3.ZERO);
            projectile.hasImpulse = true; // one motion packet: clients stop predicting its flight
            // One teleport packet per freeze so clients see it where the server holds it (never per tick).
            level.getChunkSource().broadcast(projectile, new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(projectile));
            level.sendParticles(ParticleTypes.GLOW, projectile.getX(), projectile.getY(), projectile.getZ(),
                    6, 0.15D, 0.15D, 0.15D, 0.01D);
            count++;
        }
        if (count > 0) {
            level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.6f);
        }
        return count;
    }

    /** In flight and one of the physical kinds the charm stops. Never a spell beam of any kind. */
    private static boolean isFreezable(Projectile projectile) {
        if (projectile instanceof SpellBeamEntity) {
            return false; // user ruling: Arresto never stops spells, Avada or otherwise
        }
        // Real movement last tick: an arrow or trident stuck in a block keeps a residual deltaMovement in 1.21,
        // but its position no longer changes.
        if (!projectile.isAlive() || projectile.getDeltaMovement().lengthSqr() < 1.0E-4D
                || projectile.position().distanceToSqr(projectile.xo, projectile.yo, projectile.zo) < 1.0E-4D) {
            return false;
        }
        if (projectile instanceof AbstractArrow arrow && arrow.isNoPhysics()) {
            return false; // a loyal trident flying home
        }
        return projectile instanceof AbstractArrow
                || projectile instanceof AbstractHurtingProjectile
                || projectile instanceof BasiliskVenomEntity;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        Map<UUID, Frozen> frozen = FROZEN.get(level.dimension());
        if (frozen == null) {
            return;
        }
        if (frozen.isEmpty()) {
            FROZEN.remove(level.dimension());
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Frozen>> it = frozen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Frozen> entry = it.next();
            Frozen record = entry.getValue();
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null || entity.isRemoved()) {
                it.remove(); // unloaded or gone; FROZEN_TAG drops it if it ever loads again
                continue;
            }
            if (ArrestoRules.released(record.until(), now)) {
                release(entity, record);
                it.remove();
                continue;
            }
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setPos(record.at().x, record.at().y, record.at().z);
            if (now % 4L == 0L) {
                level.sendParticles(ParticleTypes.GLOW, record.at().x, record.at().y, record.at().z, 1, 0.1D, 0.1D, 0.1D, 0.0D);
            }
        }
    }

    /** Drops it: gravity back to what it had; fireballs (no gravity of their own) are sent down. */
    private static void release(Entity entity, Frozen record) {
        entity.setNoGravity(record.hadNoGravity());
        entity.removeTag(FROZEN_TAG);
        entity.removeTag(FROZEN_NG_TAG);
        if (entity instanceof AbstractHurtingProjectile) {
            entity.setDeltaMovement(0.0D, -ArrestoRules.DROP_SPEED, 0.0D);
        } else {
            entity.setDeltaMovement(Vec3.ZERO);
        }
        entity.hasImpulse = true;
    }

    /** A projectile saved or unloaded mid-freeze drops when it loads again (Projectiles only; a tag, not persistent data). */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile projectile)
                || !projectile.getTags().contains(FROZEN_TAG)) {
            return;
        }
        Map<UUID, Frozen> frozen = FROZEN.get(event.getLevel().dimension());
        if (frozen != null && frozen.containsKey(projectile.getUUID())) {
            return; // still held this session
        }
        projectile.setNoGravity(projectile.getTags().contains(FROZEN_NG_TAG)); // exactly what it was before
        projectile.removeTag(FROZEN_TAG);
        projectile.removeTag(FROZEN_NG_TAG);
        if (projectile instanceof AbstractHurtingProjectile) {
            projectile.setDeltaMovement(0.0D, -ArrestoRules.DROP_SPEED, 0.0D); // no gravity of their own: sent down
        } else {
            projectile.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** A held projectile can't hit a creature (a push can't turn it into a weapon); it runs before Tenebris's listener. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void onProjectileImpact(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        if (FROZEN.isEmpty() || event.getProjectile().level().isClientSide()) {
            return;
        }
        Map<UUID, Frozen> frozen = FROZEN.get(event.getProjectile().level().dimension());
        if (frozen != null && frozen.containsKey(event.getProjectile().getUUID())
                && event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult) {
            event.setImpactResult(net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Entities are still loaded here: drop everything first so nothing is saved hanging in the air.
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Map<UUID, Frozen> frozen = FROZEN.get(level.dimension());
            if (frozen == null) {
                continue;
            }
            for (Map.Entry<UUID, Frozen> entry : frozen.entrySet()) {
                Entity entity = level.getEntity(entry.getKey());
                if (entity != null && !entity.isRemoved()) {
                    release(entity, entry.getValue());
                }
            }
        }
        FROZEN.clear();
    }
}
