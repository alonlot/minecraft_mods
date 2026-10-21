package com.hogwartsmagic.entity;

import com.hogwartsmagic.pensieve.scene.LetterFlood;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A flat envelope used as a Pensieve memory actor and by the letter flood. It has no AI, gravity,
 * collision or save data; the memory paths it and the renderer flutters it while it moves.
 */
public class MemoryLetterEntity extends Entity {
    /**
     * Drives the flutter phase. Callers set it from a stable value (the actor id for a memory actor, the flood seed
     * for a flood letter); the default 0 is only for a letter nobody seeded. Never the entity id, which changes on
     * every replay.
     */
    public int flutterSeed;

    public MemoryLetterEntity(EntityType<? extends MemoryLetterEntity> type, Level level) {
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

    /** The tumbling quad reaches past the tiny hit box (corners up to the envelope's half-diagonal): inflate the cull box so it never pops out at the screen edge. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(LetterFlood.MARGIN);
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
