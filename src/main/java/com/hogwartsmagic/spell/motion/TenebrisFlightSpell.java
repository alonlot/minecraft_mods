package com.hogwartsmagic.spell.motion;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.StudentEntity;
import com.hogwartsmagic.spell.combat.CombatTags;
import com.hogwartsmagic.spell.mind.MindControl;
import com.hogwartsmagic.spell.mind.PossessionManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tenebris Flight by rank (Spell Mastery Phase 7b). From Expert, projectiles and spell beams that would
 * hit a flyer pass through it 30% (Master 50%) of the time; the roll is made once per projectile and
 * flyer and stored on the projectile, so a beam that keeps overlapping the flyer gets the same answer
 * every tick. At Master, a flying player who rams an enemy deals 5 damage and Blindness, once per second
 * per target. Server only.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TenebrisFlightSpell {
    /** Flyer persistent data: target UUID → game time of the last ram. Cleared when the flight ends. */
    public static final String TAG_RAMS = "hogwartsmagic:tenebris_rams";
    /** Projectile persistent data: flyer UUID → whether it passes through that flyer. */
    private static final String TAG_ROLLS = "hogwartsmagic:tenebris_rolls";

    private TenebrisFlightSpell() {
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getImpactResult() != ProjectileImpactEvent.ImpactResult.DEFAULT) {
            return; // another handler already decided this impact
        }
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        Entity struck = hit.getEntity();
        if (struck instanceof PartEntity<?> part) {
            struck = part.getParent();
        }
        if (!(struck instanceof LivingEntity flyer) || flyer.level().isClientSide()) {
            return;
        }
        // Effect first: only a flyer's hits ever touch the projectile's persistent data.
        MobEffectInstance flight = flyer.getEffect(ExampleMod.TENEBRIS_FLIGHT_EFFECT.getHolder().get());
        if (flight == null) {
            return;
        }
        int rank = flight.getAmplifier() + 1;
        if (TenebrisRules.passChance(rank) <= 0.0D) {
            return;
        }
        Projectile projectile = event.getProjectile();
        CompoundTag rolls = projectile.getPersistentData().getCompound(TAG_ROLLS);
        String key = flyer.getStringUUID();
        boolean passes;
        if (rolls.contains(key)) {
            passes = rolls.getBoolean(key);
        } else {
            passes = TenebrisRules.passes(rank, flyer.getRandom().nextDouble());
            rolls.putBoolean(key, passes);
            projectile.getPersistentData().put(TAG_ROLLS, rolls);
            if (passes && flyer.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, projectile.getX(), projectile.getY(), projectile.getZ(),
                        8, 0.2D, 0.2D, 0.2D, 0.01D);
            }
        }
        if (passes) {
            event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        }
    }

    /** Master: called every tick a player flies in smoke form (ModEvents.handleTenebrisFlight). */
    public static void tickRam(ServerPlayer player, int rank) {
        if (!TenebrisRules.rams(rank) || PossessionManager.isForcedCaster(player) || MindControl.isControlled(player)) {
            return; // a possessed body never rams on the puppeteer's behalf
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        CompoundTag rams = player.getPersistentData().getCompound(TAG_RAMS);
        boolean changed = false;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(TenebrisRules.RAM_REACH), candidate -> isRamTarget(player, candidate))) {
            String key = victim.getStringUUID();
            if (!TenebrisRules.ramReady(rams.contains(key), rams.getLong(key), now)) {
                continue;
            }
            if (victim.hurt(player.damageSources().playerAttack(player), TenebrisRules.RAM_DAMAGE)) {
                victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, TenebrisRules.RAM_BLIND_TICKS, 0), player);
                Vec3 motion = player.getDeltaMovement();
                if (motion.horizontalDistanceSqr() > 1.0E-4D) {
                    victim.knockback(0.6D, -motion.x, -motion.z);
                }
                level.sendParticles(ParticleTypes.SQUID_INK, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(),
                        16, 0.3D, 0.4D, 0.3D, 0.05D);
            }
            rams.putLong(key, now);
            changed = true;
        }
        if (changed) {
            for (String stale : new java.util.ArrayList<>(rams.getAllKeys())) {
                if (now - rams.getLong(stale) >= TenebrisRules.RAM_COOLDOWN_TICKS) {
                    rams.remove(stale);
                }
            }
            player.getPersistentData().put(TAG_RAMS, rams);
        }
    }

    /** Hostile mobs (not Students), mobs hunting the flyer, and players the flyer may harm; never allies. */
    private static boolean isRamTarget(ServerPlayer player, LivingEntity candidate) {
        if (candidate == player || !candidate.isAlive() || candidate.isSpectator() || candidate instanceof ArmorStand) {
            return false;
        }
        if (candidate.getTags().contains(CombatTags.ALLIED) || candidate.isAlliedTo(player)) {
            return false;
        }
        if (candidate instanceof OwnableEntity ownable && player.getUUID().equals(ownable.getOwnerUUID())) {
            return false;
        }
        if (candidate instanceof Player other) {
            return !MindControl.puppeteerSpares(player.serverLevel(), player.getUUID(), other);
        }
        if (candidate instanceof Mob mob && mob.getTarget() == player) {
            return true;
        }
        return candidate instanceof Enemy && !(candidate instanceof StudentEntity);
    }
}
