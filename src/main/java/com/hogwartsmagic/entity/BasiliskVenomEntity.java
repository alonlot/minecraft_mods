package com.hogwartsmagic.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.entity.PartEntity;

import java.util.List;
import java.util.Optional;

/**
 * A glob of basilisk venom, spat at range.
 *
 * <p>Hitting a victim poisons and withers them outright; hitting anything at all
 * leaves an acid pool behind, so a miss still denies ground. The pool is an
 * {@link AreaEffectCloud} because that is exactly the job it does in vanilla.
 */
public class BasiliskVenomEntity extends ThrowableItemProjectile {

    private static final float IMPACT_DAMAGE = 6.0F;
    private static final int POOL_DURATION_TICKS = 140;
    private static final float POOL_RADIUS = 2.6F;
    private static final int VENOM_COLOUR = 0x4C8B2B;

    public BasiliskVenomEntity(EntityType<? extends BasiliskVenomEntity> type, Level level) {
        super(type, level);
    }

    public BasiliskVenomEntity(Level level, LivingEntity shooter) {
        super(ModEntities.BASILISK_VENOM.get(), shooter, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SLIME_BALL;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.06D;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.level().addParticle(ParticleTypes.ITEM_SLIME,
                    this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) {
            return; // damage and status effects are server-side; the client only renders the glob
        }
        Entity struck = result.getEntity();
        // Another basilisk is met through one of its body parts, never in
        // person, and a part is not a BasiliskEntity — so both the "venom does
        // not pass between basilisks" rule and the poison have to ask the hit
        // box who owns it. The glob itself still lands on the box, which scales
        // the damage on the way through.
        Entity victim = struck instanceof PartEntity<?> part ? part.getParent() : struck;
        if (victim instanceof BasiliskEntity) {
            return;
        }
        struck.hurt(this.damageSources().indirectMagic(this, this.getOwner()), IMPACT_DAMAGE);
        if (victim instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 140, 1));
            living.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level().isClientSide) {
            return;
        }
        spawnAcidPool();
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ITEM_SLIME,
                    this.getX(), this.getY(), this.getZ(), 24, 0.4D, 0.2D, 0.4D, 0.08D);
        }
        this.discard();
    }

    private void spawnAcidPool() {
        AreaEffectCloud pool = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
        Entity owner = this.getOwner();
        if (owner instanceof LivingEntity living) {
            pool.setOwner(living);
        }
        pool.setRadius(POOL_RADIUS);
        pool.setDuration(POOL_DURATION_TICKS);
        pool.setRadiusOnUse(-0.06F);
        pool.setWaitTime(6);
        pool.setRadiusPerTick(-pool.getRadius() / POOL_DURATION_TICKS);
        pool.setParticle(ParticleTypes.ITEM_SLIME);
        // 1.21 moved the cloud's effects onto a PotionContents component; the
        // custom colour is what tints the cloud's own particles.
        pool.setPotionContents(new PotionContents(
                Optional.empty(),
                Optional.of(VENOM_COLOUR),
                List.of(new MobEffectInstance(MobEffects.POISON, 100, 1))));
        this.level().addFreshEntity(pool);
    }
}
