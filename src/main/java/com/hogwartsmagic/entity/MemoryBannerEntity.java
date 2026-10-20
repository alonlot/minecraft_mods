package com.hogwartsmagic.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A hanging cloth banner used as a Pensieve memory actor (one entity type per design, see ModEntities). It has no
 * AI, gravity, collision or save data; its position is the middle of the hanging rod and the cloth hangs below it,
 * swaying (see BannerCloth).
 */
public class MemoryBannerEntity extends Entity {
    public MemoryBannerEntity(EntityType<? extends MemoryBannerEntity> type, Level level) {
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
     * Covers the cloth at any yaw up to scale 2: it hangs 16 blocks below the rod and reaches 2 blocks either side.
     * Pensieve draws actors through EntityRenderDispatcher.render, which does not frustum-cull, so this only
     * matters to a path that does.
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        double x = getX(), y = getY(), z = getZ();
        return new AABB(x - 2.5, y - 16.5, z - 2.5, x + 2.5, y + 0.5, z + 2.5);
    }
}
