package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.damage.DamageSupport;
import com.hogwartsmagic.spell.disable.DisableSupport;
import com.hogwartsmagic.spell.mastery.SpellMasteryData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Depulso, the banishing charm. Its knockback slams targets into walls: a banished target that meets
 * a solid block within 10 ticks takes slam damage once. Expert: enemies within 2 blocks of the impact
 * are banished too (also on a block hit), and arrows and fireballs near the beam's path fly back,
 * owned by the caster. Master: a target banished into another mob hurts both. Only a player caster gets
 * any of this; an NPC caster keeps the original plain push. A Protego-reflected beam acts as Novice and
 * earns nothing. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DepulsoSpell {
    public static final String DEFLECTED_TAG = ExampleMod.MODID + ":depulso_deflected";

    private record Slam(ResourceKey<Level> dimension, @Nullable UUID casterId, double dirX, double dirZ, int rank,
                        long start, long until) {
    }

    private static final Map<UUID, Slam> SLAMS = new HashMap<>();

    private DepulsoSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        apply(target, caster, true);
    }

    /** @param masteryEligible false for a Protego-reflected beam: it behaves as Novice (rank 1) */
    public static void apply(LivingEntity target, @Nullable Entity caster, boolean masteryEligible) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(caster instanceof ServerPlayer player)) {
            // NPC casters (and no caster): the original unconditional push, no slam, no area.
            Vec3 away = target.position().vectorTo(
                    caster != null ? caster.position() : target.position().add(0, 0, -5))
                    .normalize().scale(-1.5);
            target.push(away.x, 0.5, away.z);
            return;
        }
        int rank = masteryEligible ? SpellMasteryData.getRank(player, SpellType.DEPULSO) : 1;
        Vec3 from = player.position();
        banish(level, target, from, rank, player);
        if (DepulsoRules.pushesArea(rank)) {
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
                    target.getBoundingBox().inflate(DepulsoRules.AREA_RADIUS),
                    e -> DisableSupport.isEnemyOf(player, e, target))) {
                banish(level, other, from, rank, player);
            }
        }
    }

    /**
     * Expert and up: a Depulso on a wall or floor still banishes the enemies around it.
     *
     * @param reflected a Protego-reflected beam: behaves as Novice, so nothing happens
     */
    public static boolean onBlockHit(ServerLevel level, Vec3 impact, @Nullable Entity caster, boolean reflected) {
        if (reflected || !(caster instanceof ServerPlayer player)) {
            return false;
        }
        int rank = SpellMasteryData.getRank(player, SpellType.DEPULSO);
        if (!DepulsoRules.pushesArea(rank)) {
            return false;
        }
        Vec3 from = player.position();
        List<LivingEntity> banished = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(impact, impact).inflate(DepulsoRules.AREA_RADIUS),
                e -> DamageSupport.isSplashTarget(player, e, null));
        for (LivingEntity target : banished) {
            banish(level, target, from, rank, player);
        }
        return !banished.isEmpty();
    }

    /**
     * Called every server tick of a Depulso beam. Expert and up: incoming arrows and fireballs turn around,
     * now owned by the caster. Spell beams are never touched (Protego owns beam reflection), nor are
     * tridents, ender pearls, hooks or thrown items. Queries only a small box.
     */
    public static int deflectProjectiles(SpellBeamEntity beam) {
        if (beam.isReflected() || !(beam.level() instanceof ServerLevel level)
                || !(beam.getOwner() instanceof ServerPlayer owner)) {
            return 0;
        }
        if (!DepulsoRules.deflectsProjectiles(SpellMasteryData.getRank(owner, SpellType.DEPULSO))) {
            return 0;
        }
        int deflected = 0;
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class,
                beam.getBoundingBox().inflate(DepulsoRules.DEFLECT_RADIUS),
                p -> p != beam && p.isAlive() && isCombatProjectile(p) && p.getOwner() != owner
                        && !(p.getOwner() != null && p.getOwner().isAlliedTo(owner))
                        && !p.getTags().contains(DEFLECTED_TAG) && p.getDeltaMovement().lengthSqr() > 0.01)) {
            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1.0));
            // setOwner flips a disallowed pickup to allowed for a player owner: keep it, no free items.
            AbstractArrow.Pickup pickup = projectile instanceof AbstractArrow arrow ? arrow.pickup : null;
            projectile.setOwner(owner);
            if (pickup != null) {
                ((AbstractArrow) projectile).pickup = pickup;
            }
            projectile.addTag(DEFLECTED_TAG);
            projectile.hasImpulse = true;
            projectile.hurtMarked = true;
            level.sendParticles(ParticleTypes.CLOUD, projectile.getX(), projectile.getY(), projectile.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
            deflected++;
        }
        if (deflected > 0) {
            level.playSound(null, beam.getX(), beam.getY(), beam.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.6f);
        }
        return deflected;
    }

    private static boolean isCombatProjectile(Projectile p) {
        return (p instanceof AbstractArrow && !(p instanceof ThrownTrident)) || p instanceof AbstractHurtingProjectile;
    }

    private static void banish(ServerLevel level, LivingEntity target, Vec3 from, int rank, ServerPlayer owner) {
        Vec3 away = new Vec3(target.getX() - from.x, 0.0, target.getZ() - from.z);
        away = away.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
        double knockback = DepulsoRules.knockback(rank);
        // One impulse: movement is client-authoritative for a player, so never re-applied per tick.
        target.push(away.x * knockback, DepulsoRules.VERTICAL_LIFT, away.z * knockback);
        target.hurtMarked = true;
        long now = level.getGameTime();
        SLAMS.put(target.getUUID(), new Slam(level.dimension(), owner.getUUID(),
                away.x, away.z, rank, now, now + DepulsoRules.SLAM_WINDOW_TICKS));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || SLAMS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        List<UUID> spent = new java.util.ArrayList<>(); // removed after the loop: no CME
        Iterator<Map.Entry<UUID, Slam>> it = SLAMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Slam> entry = it.next();
            Slam slam = entry.getValue();
            if (spent.contains(entry.getKey())) {
                continue; // already struck this tick as the other half of a pair
            }
            if (slam.dimension() != level.dimension()) {
                continue;
            }
            if (!(level.getEntity(entry.getKey()) instanceof LivingEntity target) || !target.isAlive() || now > slam.until()) {
                it.remove();
                continue;
            }
            if (now <= slam.start()) {
                continue; // the tick of the banishing itself: collision flags are stale
            }
            Entity caster = slam.casterId() == null ? null : level.getEntity(slam.casterId());
            if (caster == null || !caster.isAlive()) {
                it.remove(); // caster gone: no slam rather than one with a weakened ally check
                continue;
            }
            float damage = DamageSupport.scaled(caster, DepulsoRules.slamDamage(slam.rank()));
            // A group banished together overlaps at first; contact with another mob only counts once apart.
            if (DepulsoRules.slamsIntoMobs(slam.rank()) && now > slam.start() + 2) {
                List<LivingEntity> struck = level.getEntitiesOfClass(LivingEntity.class,
                        target.getBoundingBox().inflate(DepulsoRules.MOB_CONTACT_INFLATE),
                        e -> e != target && e != caster && DisableSupport.isEnemyOf(caster, e, target));
                if (!struck.isEmpty()) {
                    LivingEntity other = struck.get(0);
                    other.hurt(DamageSupport.magicFrom(other, caster), damage);
                    slamFeedback(level, target);
                    target.hurt(DamageSupport.magicFrom(target, caster), damage);
                    spent.add(other.getUUID());
                    it.remove();
                    continue;
                }
            }
            if (target.horizontalCollision || blockedAhead(level, target, slam)) {
                target.hurt(DamageSupport.magicFrom(target, caster), damage);
                slamFeedback(level, target);
                it.remove();
            }
        }
        spent.forEach(SLAMS::remove);
    }

    private static boolean blockedAhead(ServerLevel level, LivingEntity target, Slam slam) {
        double reach = target.getBbWidth() * 0.5 + DepulsoRules.WALL_PROBE;
        BlockPos pos = BlockPos.containing(target.getX() + slam.dirX() * reach,
                target.getY() + target.getBbHeight() * 0.5,
                target.getZ() + slam.dirZ() * reach);
        return level.isLoaded(pos) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static void slamFeedback(ServerLevel level, LivingEntity target) {
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                12, 0.3, 0.3, 0.3, 0.1);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND,
                SoundSource.NEUTRAL, 0.35f, 1.4f);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SLAMS.clear();
    }
}
