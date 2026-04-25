package com.hogwartsmagic.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

public class MinatorEntity extends Monster implements RangedAttackMob {

    /** Only use hoof melee when this close; beyond this the bow goal runs. */
    private static final double STOMP_MELEE_RANGE_SQ = 2.35 * 2.35;
    private static final double STOMP_MELEE_CONTINUE_SQ = 2.85 * 2.85;

    public MinatorEntity(EntityType<? extends MinatorEntity> type, Level level) {
        super(type, level);
        this.setLeftHanded(false);
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!this.level().isClientSide && this.getMainHandItem().isEmpty()) {
            this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            this.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.15F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 36.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D);
    }

    public static boolean canSpawn(EntityType<MinatorEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, net.minecraft.util.RandomSource random) {
        return checkAnyLightMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            this.playSound(SoundEvents.HORSE_ANGRY, 0.9F, 0.75F + this.random.nextFloat() * 0.25F);
        }
        return hit;
    }

    /**
     * Skeleton-style: bow works without an arrow item stack (otherwise {@link ProjectileUtil#getMobArrow}
     * often gets {@code null}).
     */
    @Override
    public ItemStack getProjectile(ItemStack weapon) {
        if (weapon.is(Items.BOW)) {
            return new ItemStack(Items.ARROW);
        }
        return super.getProjectile(weapon);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MinatorStompMeleeGoal(this, 1.3D, true));
        this.goalSelector.addGoal(2, new RangedBowAttackGoal(this, 1.0D, 20, 24.0F));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        this.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.15F);
        return data;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        ItemStack bow = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, Items.BOW));
        ItemStack projectile = this.getProjectile(bow);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, bow, velocity, projectile);
        if (arrow == null) {
            return;
        }
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horiz * 0.2D, dz, 1.6F, 14 - this.level().getDifficulty().getId() * 4);
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        this.level().addFreshEntity(arrow);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return SoundEvents.PILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PILLAGER_DEATH;
    }

    private static final class MinatorStompMeleeGoal extends MeleeAttackGoal {
        private final MinatorEntity minator;

        MinatorStompMeleeGoal(MinatorEntity mob, double speed, boolean longMemory) {
            super(mob, speed, longMemory);
            this.minator = mob;
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.minator.getTarget();
            if (target == null || this.minator.distanceToSqr(target) > STOMP_MELEE_RANGE_SQ) {
                return false;
            }
            return super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = this.minator.getTarget();
            if (target == null || this.minator.distanceToSqr(target) > STOMP_MELEE_CONTINUE_SQ) {
                return false;
            }
            return super.canContinueToUse();
        }
    }
}
