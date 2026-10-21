package com.hogwartsmagic.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A small wooden rowing boat used as a Pensieve memory actor (see {@code pensieve/scene/Rowboat}). It has no AI,
 * gravity, collision or save data. The memory places it each tick: the position already carries the heave, and
 * {@link #pitch} / {@link #roll} (with their previous-tick values for interpolation) carry the rocking the renderer
 * applies, since the vanilla rotation fields have no roll.
 */
public class MemoryRowboatEntity extends Entity {
    /** Bow-up pitch and left-up roll in degrees, this tick and the last (see Rowboat for the conventions). */
    public float pitch, roll, pitchO, rollO;

    public MemoryRowboatEntity(EntityType<? extends MemoryRowboatEntity> type, Level level) {
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

    /** The hull and the oars at any yaw, up to scale 4 (the oars reach about 3 blocks out from the middle). */
    @Override
    public AABB getBoundingBoxForCulling() {
        double x = getX(), y = getY(), z = getZ();
        return new AABB(x - 13, y - 3, z - 13, x + 13, y + 5, z + 13);
    }
}
