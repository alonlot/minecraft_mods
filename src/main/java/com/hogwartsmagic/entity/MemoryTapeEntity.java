package com.hogwartsmagic.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A cloth measuring tape used as a Pensieve memory actor ({@code hogwartsmagic:memory_tape}). The actor is the tape's
 * free brass end; the renderer trails the rest of the tape behind it along the actor's own path (see TapeTrail). It
 * has no AI, gravity, collision or save data.
 */
public class MemoryTapeEntity extends Entity {
    public MemoryTapeEntity(EntityType<? extends MemoryTapeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** The tape trails up to its length (1.5 blocks, 6 at scale 4) behind its end in any direction. */
    @Override
    public AABB getBoundingBoxForCulling() {
        double x = getX(), y = getY(), z = getZ();
        return new AABB(x - 6.5, y - 6.5, z - 6.5, x + 6.5, y + 6.5, z + 6.5);
    }
}
