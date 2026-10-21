package com.hogwartsmagic.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A broom used as a Pensieve memory actor ({@code memory_broom}, the school broom, and {@code memory_broom_racing},
 * the Nimbus; see {@code pensieve/scene/BroomFlight}). It has no AI, gravity, collision or save data. The memory places
 * it each tick: the position already carries the hover bob, and {@link #pitch} / {@link #roll} (with their
 * previous-tick values for interpolation) carry what the renderer applies, since the vanilla rotation fields have no
 * roll. Its origin is the seat point on the handle.
 */
public class MemoryBroomEntity extends Entity {
    /** Nose-down pitch and right-side-down roll in degrees, this tick and the last (see BroomFlight). */
    public float pitch, roll, pitchO, rollO;

    public MemoryBroomEntity(EntityType<? extends MemoryBroomEntity> type, Level level) {
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

    /** The whole broom at any yaw, pitch and roll, up to scale 4 (it is under 2 blocks from the seat each way). */
    @Override
    public AABB getBoundingBoxForCulling() {
        double x = getX(), y = getY(), z = getZ();
        return new AABB(x - 8, y - 8, z - 8, x + 8, y + 8, z + 8);
    }
}
