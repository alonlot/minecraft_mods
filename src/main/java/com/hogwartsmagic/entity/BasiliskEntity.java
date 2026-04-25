package com.hogwartsmagic.entity;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

public class BasiliskEntity extends Monster {
    private static final int PETRIFY_INTERVAL_TICKS = 2;
    private static final double PETRIFY_RANGE = 28.0D;
    private static final int PETRIFY_DURATION = 60;
    private static final double MELEE_RANGE_SQ = 4.5D * 4.5D;
    private static final double BASILISK_EYE_AIM_DOT = 0.94D;
    private static final double TARGET_EYE_AIM_DOT = 0.985D;
    private static final double BASILISK_EYE_AIM_RADIUS = 0.85D;
    private static final double TARGET_EYE_AIM_RADIUS = 0.33D;

    public BasiliskEntity(EntityType<? extends BasiliskEntity> type, Level level) {
        super(type, level);
        this.xpReward = 25;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 160.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 14.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    public static boolean canSpawn(EntityType<BasiliskEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, net.minecraft.util.RandomSource random) {
        return checkAnyLightMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.05D, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, WizardEntity.class, true));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, StudentEntity.class, true));
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide && this.tickCount % PETRIFY_INTERVAL_TICKS == 0) {
            this.tryPetrifyNearbyVictims();
        }

        if (this.level().isClientSide && this.tickCount % 2 == 0) {
            this.level().addParticle(
                    ParticleTypes.ENCHANTED_HIT,
                    this.getRandomX(0.9D),
                    this.getEyeY() - 0.2D,
                    this.getRandomZ(0.9D),
                    0.0D,
                    0.0D,
                    0.0D
            );
        }
    }

    private void tryPetrifyNearbyVictims() {
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(PETRIFY_RANGE), this::canPetrifyTarget)) {
            if (isMutuallyLooking(target)) {
                this.petrifyAndKill(target);
            }
        }
    }

    private boolean canPetrifyTarget(LivingEntity target) {
        if (target == this || !target.isAlive() || target instanceof BasiliskEntity) {
            return false;
        }
        if (target.hasEffect(ExampleMod.BASILISK_PETRIFICATION.getHolder().get())) {
            return false;
        }
        if (this.distanceToSqr(target) > PETRIFY_RANGE * PETRIFY_RANGE) {
            return false;
        }
        return this.hasLineOfSight(target) && target.hasLineOfSight(this);
    }

    private boolean isMutuallyLooking(LivingEntity target) {
        Vec3 basiliskEyes = this.getEyePosition();
        Vec3 targetEyes = target.getEyePosition();
        return isLookingAtEyes(this, targetEyes, BASILISK_EYE_AIM_RADIUS, BASILISK_EYE_AIM_DOT)
                && isLookingAtEyes(target, basiliskEyes, TARGET_EYE_AIM_RADIUS, TARGET_EYE_AIM_DOT);
    }

    private static boolean isLookingAtEyes(LivingEntity viewer, Vec3 eyeTarget, double maxOffset, double minDot) {
        Vec3 viewerEyes = viewer.getEyePosition();
        Vec3 toTarget = eyeTarget.subtract(viewerEyes);
        double distanceSquared = toTarget.lengthSqr();
        if (distanceSquared < 1.0E-6D) {
            return false;
        }

        Vec3 directionToTarget = toTarget.normalize();
        Vec3 viewVector = viewer.getViewVector(1.0F).normalize();
        double facing = viewVector.dot(directionToTarget);
        if (facing < minDot) {
            return false;
        }

        double projectedDistance = toTarget.dot(viewVector);
        if (projectedDistance <= 0.0D) {
            return false;
        }

        Vec3 closestPoint = viewerEyes.add(viewVector.scale(projectedDistance));
        return closestPoint.distanceToSqr(eyeTarget) <= maxOffset * maxOffset;
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            this.petrifyAndKill(living);
        }
        return hit;
    }

    private void petrifyAndKill(LivingEntity target) {
        target.addEffect(new MobEffectInstance(ExampleMod.BASILISK_PETRIFICATION.getHolder().get(), PETRIFY_DURATION, 0, false, true, true));
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 20, 6, false, false, false));
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 20, 2, false, false, false));

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ASH, target.getX(), target.getEyeY(), target.getZ(), 28, 0.35D, 0.7D, 0.35D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY(0.5D), target.getZ(), 18, 0.25D, 0.55D, 0.25D, 0.01D);
        }

        if (target.isAlive()) {
            target.hurt(this.damageSources().magic(), Float.MAX_VALUE);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        LivingEntity target = this.getTarget();
        if (target != null) {
            this.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (this.distanceToSqr(target) <= MELEE_RANGE_SQ) {
                this.setYRot(Mth.rotateIfNecessary(this.getYRot(), this.yHeadRot, 12.0F));
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SPIDER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource damageSource) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 1.3F;
    }
}
