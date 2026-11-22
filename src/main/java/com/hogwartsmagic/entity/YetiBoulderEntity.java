package com.hogwartsmagic.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A boulder of packed snow and ice, thrown by a Yeti in a high arc. It bursts on whatever it hits:
 * 9 damage, Slowness II for three seconds and a shove to anything living it strikes.
 */
public class YetiBoulderEntity extends ThrowableProjectile {
    public static final float DAMAGE = 9.0F;

    public YetiBoulderEntity(EntityType<? extends YetiBoulderEntity> type, Level level) {
        super(type, level);
    }

    public YetiBoulderEntity(Level level, LivingEntity owner) {
        super(ModEntities.YETI_BOULDER.get(), owner, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04D;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide && this.tickCount % 2 == 0) {
            this.level().addParticle(ParticleTypes.SNOWFLAKE, getX(), getY() + 0.3D, getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity e = result.getEntity();
        Entity owner = getOwner();
        if (e == owner) {
            return;
        }
        e.hurt(damageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null), DAMAGE);
        if (e instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            Vec3 push = getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).normalize().scale(0.6D);
            living.push(push.x, 0.25D, push.z);
            living.hurtMarked = true;
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof YetiEntity);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState()),
                    getX(), getY(), getZ(), 40, 0.4D, 0.4D, 0.4D, 0.2D);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                    getX(), getY(), getZ(), 16, 0.3D, 0.3D, 0.3D, 0.2D);
            level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY(), getZ(), 20, 0.5D, 0.5D, 0.5D, 0.08D);
            level.playSound(null, blockPosition(), SoundEvents.SNOW_BREAK, SoundSource.HOSTILE, 1.6F, 0.6F);
            level.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.8F, 0.7F);
            this.discard();
        }
    }
}
