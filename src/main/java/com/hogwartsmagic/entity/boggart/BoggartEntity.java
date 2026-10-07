package com.hogwartsmagic.entity.boggart;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * SKELETON: agent A replaces the body. The public API below is fixed (see docs/superpowers/plans/2026-10-08-boggart.md).
 */
public class BoggartEntity extends Monster implements RiddikulusTarget {
    private static final EntityDataAccessor<String> DATA_FORM =
            SynchedEntityData.defineId(BoggartEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_RIDDIKULUS_HITS =
            SynchedEntityData.defineId(BoggartEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LAUGH_TICKS =
            SynchedEntityData.defineId(BoggartEntity.class, EntityDataSerializers.INT);

    public BoggartEntity(EntityType<? extends BoggartEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FORM, "");
        builder.define(DATA_RIDDIKULUS_HITS, 0);
        builder.define(DATA_LAUGH_TICKS, 0);
    }

    public String getFormId() {
        return this.entityData.get(DATA_FORM);
    }

    public boolean isHidden() {
        return getFormId().isEmpty();
    }

    public int getRiddikulusHits() {
        return this.entityData.get(DATA_RIDDIKULUS_HITS);
    }

    public int getLaughTicks() {
        return this.entityData.get(DATA_LAUGH_TICKS);
    }

    public boolean isLaughing() {
        return getLaughTicks() > 0;
    }

    @Override
    public void onRiddikulus(int rank, @Nullable Entity caster) {
    }
}
