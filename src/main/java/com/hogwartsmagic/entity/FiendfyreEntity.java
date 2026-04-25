package com.hogwartsmagic.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Entity that manages the spreading of Fiendfyre.
 * It starts small and spreads to nearby blocks (even non-flammable ones) for 15 seconds.
 */
public class FiendfyreEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_LIFE_TICKS = SynchedEntityData.defineId(FiendfyreEntity.class, EntityDataSerializers.INT);
    private static final int MAX_LIFE = 300; // 15 seconds (20 ticks per second)

    public FiendfyreEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LIFE_TICKS, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setLifeTicks(tag.getInt("LifeTicks"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("LifeTicks", getLifeTicks());
    }

    public int getLifeTicks() {
        return this.entityData.get(DATA_LIFE_TICKS);
    }

    public void setLifeTicks(int ticks) {
        this.entityData.set(DATA_LIFE_TICKS, ticks);
    }

    @Override
    public void tick() {
        super.tick();
        BlockPos center = this.blockPosition();
        
        int ticks = getLifeTicks();
        if (ticks >= MAX_LIFE) {
            if (!this.level().isClientSide) {
                this.discard();
            }
            return;
        }
        setLifeTicks(ticks + 1);

        if (!this.level().isClientSide) {
            // Spreading logic every 10 ticks
            if (ticks % 10 == 0) {
                spreadFire();
            }
            
            // Visuals on server (particles)
            if (this.level() instanceof ServerLevel serverLevel && ticks % 2 == 0) {
                serverLevel.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 0.5, this.getZ(), 5, 0.5, 0.5, 0.5, 0.05);
                serverLevel.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY() + 0.5, this.getZ(), 1, 0.3, 0.3, 0.3, 0.02);
            }
        }
    }

    private void spreadFire() {
        // Randomly pick blocks around the center to ignite
        BlockPos center = this.blockPosition();
        int radius = 1 + (getLifeTicks() / 40); // Grows from 1 to 8 radius over 15s (300 ticks)
        
        for (int i = 0; i < 5; i++) {
            int rx = this.random.nextInt(radius * 2 + 1) - radius;
            int ry = this.random.nextInt(3) - 1;
            int rz = this.random.nextInt(radius * 2 + 1) - radius;
            
            BlockPos target = center.offset(rx, ry, rz);
            if (this.level().isEmptyBlock(target)) {
                this.level().setBlockAndUpdate(target, Blocks.FIRE.defaultBlockState());
            }
        }
    }
}
