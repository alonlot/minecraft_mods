package com.hogwartsmagic.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DementorEntity extends Monster {

    public DementorEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingMoveControl(this, 10, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new AvoidEntityGoal<>(this, PatronusEntity.class, 24.0F, 1.5D, 2.0D)); // Runs from Patronus IMPERATIVELY
        this.goalSelector.addGoal(2, new DementorSuckGoal(this)); // Custom soul-sucking attack
        this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, WizardEntity.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, StudentEntity.class, true));
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return com.hogwartsmagic.ModSounds.DEMENTOR_AMBIENT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return com.hogwartsmagic.ModSounds.DEMENTOR_HURT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return com.hogwartsmagic.ModSounds.DEMENTOR_DEATH.get();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 120.0D)
                .add(Attributes.FLYING_SPEED, 0.52D) // Still threatening, but slightly easier to shake off
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level) {
            @Override
            public boolean isStableDestination(net.minecraft.core.BlockPos pos) {
                return true; // We don't care about the destination blocks because we clip through them
            }
        };
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, net.minecraft.core.BlockPos pos) {
    }

    @Override
    public void tick() {
        super.tick();
        
        // Disable gravity entirely
        this.setNoGravity(true);

        // Ambient Dementor Aura of Dread and Cold Space
        if (!this.level().isClientSide && this.tickCount % 20 == 0) {
            // Apply blindness and slowness to all living things nearby
            List<LivingEntity> nearby = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(9.0D));
            for (LivingEntity e : nearby) {
                if (!(e instanceof DementorEntity) && !(e.getTags().contains("patronus"))) { // Exclude other Dementors and Patronuses
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false));
                }
            }
        }

        // Frost/Soul particles flowing off them
        if (this.level().isClientSide) {
            for (int i = 0; i < 2; i++) {
                this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        this.getRandomX(0.5D),
                        this.getRandomY(),
                        this.getRandomZ(0.5D),
                        0.0D, 0.05D, 0.0D);
            }
            this.level().addParticle(ParticleTypes.SNOWFLAKE,
                    this.getRandomX(1.5D),
                    this.getRandomY() + 0.5D,
                    this.getRandomZ(1.5D),
                    0.0D, -0.05D, 0.0D);
        }
    }

    class DementorSuckGoal extends Goal {
        private final DementorEntity dementor;
        private int suckTime = 0;

        public DementorSuckGoal(DementorEntity dementor) {
            this.dementor = dementor;
        }

        @Override
        public boolean canUse() {
            // Immediately stop trying to suck souls if a Patronus is nearby (Panic mode)
            if (!this.dementor.level().getEntitiesOfClass(PatronusEntity.class, this.dementor.getBoundingBox().inflate(24.0D)).isEmpty()) {
                return false;
            }
            return this.dementor.getTarget() != null && this.dementor.distanceToSqr(this.dementor.getTarget()) < 2304.0D; // 48 blocks
        }

        @Override
        public void start() {
            this.suckTime = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = this.dementor.getTarget();
            if (target != null) {
                this.dementor.getLookControl().setLookAt(target, 30.0F, 30.0F);
                
                double distSq = this.dementor.distanceToSqr(target);

                if (distSq < 4.0D) {
                    // Kiss of Death: Huge damage
                    if (this.suckTime % 10 == 0) {
                        target.hurt(this.dementor.damageSources().magic(), 3.0F);
                        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
                    }
                } else {
                    // Apply blindness as they get sucked in
                    if (distSq < 196.0D && this.suckTime % 20 == 0) {
                        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
                    }
                    
                    // Move towards the target while sucking them in
                    this.dementor.getNavigation().moveTo(target, 1.25D);
                    
                    // Pull the target towards the Dementor (Soul Sucking Wind)
                    Vec3 pull = new Vec3(this.dementor.getX() - target.getX(), 
                                         this.dementor.getY() + this.dementor.getEyeHeight() - target.getY(), 
                                         this.dementor.getZ() - target.getZ()).normalize().scale(0.03);
                    target.setDeltaMovement(target.getDeltaMovement().add(pull));
                    target.hurtMarked = true;
                }
                
                // Audio queue for sucking souls
                if (this.suckTime % 40 == 0 && !this.dementor.level().isClientSide) {
                    this.dementor.playSound(net.minecraft.sounds.SoundEvents.AMBIENT_CAVE.get(), 1.0F, 0.5F);
                }

                this.suckTime++;
            }
        }
    }
}
