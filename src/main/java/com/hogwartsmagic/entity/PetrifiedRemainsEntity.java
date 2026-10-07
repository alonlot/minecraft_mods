package com.hogwartsmagic.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;

public class PetrifiedRemainsEntity extends Entity {
    private static final EntityDataAccessor<String> DATA_MIMIC_TYPE =
            SynchedEntityData.defineId(PetrifiedRemainsEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_WIDTH =
            SynchedEntityData.defineId(PetrifiedRemainsEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT =
            SynchedEntityData.defineId(PetrifiedRemainsEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BODY_YAW =
            SynchedEntityData.defineId(PetrifiedRemainsEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEAD_YAW =
            SynchedEntityData.defineId(PetrifiedRemainsEntity.class, EntityDataSerializers.FLOAT);

    /**
     * The whole mob as it was (saveWithoutId minus the death fields), so a Mandrake Draught brings it back with its
     * name, owner, trades and inventory. Saved with the statue, never synced: the renderer needs only the type.
     * Empty for bosses and for statues from older worlds.
     */
    private CompoundTag storedMob = new CompoundTag();

    public PetrifiedRemainsEntity(EntityType<? extends PetrifiedRemainsEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static PetrifiedRemainsEntity fromLiving(Level level, LivingEntity victim) {
        PetrifiedRemainsEntity remains = ModEntities.PETRIFIED_REMAINS.get().create(level);
        if (remains == null) {
            return null;
        }
        remains.setMimicType(EntityType.getKey(victim.getType()).toString());
        remains.setStoredDimensions(victim.getBbWidth(), victim.getBbHeight());
        remains.moveTo(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), victim.getXRot());
        remains.setStoredBodyYaw(victim.yBodyRot);
        remains.setStoredHeadYaw(victim.getYHeadRot());
        if (!com.hogwartsmagic.spell.combat.CombatTags.isBoss(victim)) {
            remains.storedMob = com.hogwartsmagic.petrification.Petrification.captureMob(victim);
        }
        return remains;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_MIMIC_TYPE, "minecraft:pig");
        builder.define(DATA_WIDTH, 1.0F);
        builder.define(DATA_HEIGHT, 1.0F);
        builder.define(DATA_BODY_YAW, 0.0F);
        builder.define(DATA_HEAD_YAW, 0.0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.setMimicType(tag.getString("MimicType"));
        this.setStoredDimensions(tag.getFloat("StoredWidth"), tag.getFloat("StoredHeight"));
        if (tag.contains("BodyYaw")) {
            this.setStoredBodyYaw(tag.getFloat("BodyYaw"));
        }
        if (tag.contains("HeadYaw")) {
            this.setStoredHeadYaw(tag.getFloat("HeadYaw"));
        }
        this.storedMob = tag.getCompound("StoredMob").copy();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("MimicType", this.getMimicTypeId());
        tag.putFloat("StoredWidth", this.getStoredWidth());
        tag.putFloat("StoredHeight", this.getStoredHeight());
        tag.putFloat("BodyYaw", this.getStoredBodyYaw());
        tag.putFloat("HeadYaw", this.getStoredHeadYaw());
        if (!this.storedMob.isEmpty()) {
            tag.put("StoredMob", this.storedMob.copy());
        }
    }

    /** A copy of the stored mob, empty when there is none. */
    public CompoundTag getStoredMob() {
        return this.storedMob.copy();
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (!this.level().isClientSide) {
            this.kill();
        }
        return true;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(this.getStoredWidth(), this.getStoredHeight());
    }

    public String getMimicTypeId() {
        return this.entityData.get(DATA_MIMIC_TYPE);
    }

    public void setMimicType(String typeId) {
        this.entityData.set(DATA_MIMIC_TYPE, typeId);
    }

    public float getStoredWidth() {
        return this.entityData.get(DATA_WIDTH);
    }

    public float getStoredHeight() {
        return this.entityData.get(DATA_HEIGHT);
    }

    public void setStoredDimensions(float width, float height) {
        this.entityData.set(DATA_WIDTH, Math.max(0.2F, width));
        this.entityData.set(DATA_HEIGHT, Math.max(0.2F, height));
        this.refreshDimensions();
    }

    public float getStoredBodyYaw() {
        return this.entityData.get(DATA_BODY_YAW);
    }

    public void setStoredBodyYaw(float bodyYaw) {
        this.entityData.set(DATA_BODY_YAW, bodyYaw);
    }

    public float getStoredHeadYaw() {
        return this.entityData.get(DATA_HEAD_YAW);
    }

    public void setStoredHeadYaw(float headYaw) {
        this.entityData.set(DATA_HEAD_YAW, headYaw);
    }
}
