package com.hogwartsmagic.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * One hinged door leaf used as a Pensieve memory actor. It has no AI, gravity, collision or save data; the
 * memory sets its yaw and the renderer swings the leaf about its hinge edge (see DoorHinge).
 */
public class MemoryDoorEntity extends Entity {
    public MemoryDoorEntity(EntityType<? extends MemoryDoorEntity> type, Level level) {
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

    /**
     * The leaf swings about the hinge and can be scaled up to 4, so it reaches 4 blocks sideways and 12 up
     * from the entity position, far outside the 1 x 3 hit box that vanilla culling would use.
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        double x = getX(), y = getY(), z = getZ();
        return new AABB(x - 4.5, y - 0.5, z - 4.5, x + 4.5, y + 12.5, z + 4.5);
    }
}
