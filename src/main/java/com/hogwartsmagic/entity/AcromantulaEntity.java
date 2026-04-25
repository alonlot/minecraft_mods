package com.hogwartsmagic.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

public class AcromantulaEntity extends Monster {

    private static final EntityDataAccessor<Byte> DATA_FLAGS_ID = SynchedEntityData.defineId(AcromantulaEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(AcromantulaEntity.class, EntityDataSerializers.FLOAT);

    public AcromantulaEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }
    
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FLAGS_ID, (byte)0);
        builder.define(DATA_SCALE, 1.0F); // Default multiplier
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(4, new SpiderAttackGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public static boolean canSpawn(EntityType<AcromantulaEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, net.minecraft.util.RandomSource random) {
        return checkAnyLightMonsterSpawnRules(type, level, spawnType, pos, random);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.setClimbing(this.horizontalCollision);
            
            // Generate cobwebs around itself frequently (30% chance every second)
            if (this.tickCount % 20 == 0 && this.random.nextFloat() < 0.3F) {
                net.minecraft.core.BlockPos pos = this.blockPosition();
                // Randomly place it either at feet, or 1 block around
                pos = pos.offset(this.random.nextInt(3) - 1, this.random.nextInt(2), this.random.nextInt(3) - 1);
                
                if (this.level().isEmptyBlock(pos)) {
                    this.level().setBlock(pos, net.minecraft.world.level.block.Blocks.COBWEB.defaultBlockState(), 3);
                }
            }
        }
    }

    @Override
    public boolean onClimbable() {
        return this.isClimbing();
    }
    
    @Override
    public void makeStuckInBlock(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.phys.Vec3 motionMultiplier) {
        if (!state.is(net.minecraft.world.level.block.Blocks.COBWEB)) {
            super.makeStuckInBlock(state, motionMultiplier);
        }
    }

    public boolean isClimbing() {
        return (this.entityData.get(DATA_FLAGS_ID) & 1) != 0;
    }

    public void setClimbing(boolean climbing) {
        byte b0 = this.entityData.get(DATA_FLAGS_ID);
        if (climbing) {
            b0 = (byte)(b0 | 1);
        } else {
            b0 = (byte)(b0 & -2);
        }
        this.entityData.set(DATA_FLAGS_ID, b0);
    }

    public float getSpiderScale() {
        return this.entityData.get(DATA_SCALE);
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty, net.minecraft.world.entity.MobSpawnType reason, @javax.annotation.Nullable net.minecraft.world.entity.SpawnGroupData spawnData) {
        net.minecraft.world.entity.SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        // Randomize scale from tiny (0.2F) to large (1.2F max, reduced per user request)
        float[] scales = {0.2F, 0.4F, 0.6F, 0.8F, 1.0F, 1.2F};
        float scale = scales[this.random.nextInt(scales.length)];
        this.entityData.set(DATA_SCALE, scale);
        
        // Scale attributes relative to size
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D * scale);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10.0D * scale);
        this.setHealth(this.getMaxHealth());
        
        return data;
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("SpiderScale", this.getSpiderScale());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SpiderScale")) {
            this.entityData.set(DATA_SCALE, tag.getFloat("SpiderScale"));
        }
    }

    static class SpiderAttackGoal extends MeleeAttackGoal {
        public SpiderAttackGoal(AcromantulaEntity spider) {
            super(spider, 1.0D, true);
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !this.mob.isVehicle();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse();
        }

        // Attempting to override getAttackReachSqr or similar is fraught with 1.21 mappings changes.
        // We'll let the standard MeleeAttackGoal handle reach for now, or just increase its base scale.
    }
}
