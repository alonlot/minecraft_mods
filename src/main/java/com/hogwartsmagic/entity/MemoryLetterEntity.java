package com.hogwartsmagic.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * A flat envelope used as a Pensieve memory actor and by the letter flood. It has no AI, gravity,
 * collision or save data; the memory paths it and the renderer flutters it while it moves.
 */
public class MemoryLetterEntity extends Entity {
    /** Drives the flutter phase. Defaults to the entity id; the letter flood sets it. */
    public int flutterSeed;

    public MemoryLetterEntity(EntityType<? extends MemoryLetterEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.flutterSeed = getId();
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
}
