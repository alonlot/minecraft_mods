package com.hogwartsmagic.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.PartEntity;

import javax.annotation.Nullable;

/**
 * One hittable section of a basilisk.
 *
 * <p>A single axis-aligned box cannot follow fifteen blocks of curving serpent,
 * so the body is covered by a row of these instead — the same trick the Ender
 * Dragon uses. The basilisk moves them onto its spine every tick and forwards
 * anything that hits one back to itself, scaled by where on the body it landed.
 *
 * <p>Parts are hit volumes only: they do not collide, so nothing gets shoved
 * around by a passing tail, but the tail can be struck.
 */
public class BasiliskPart extends PartEntity<BasiliskEntity> {

    private final EntityDimensions size;
    /** Damage taken here is multiplied by this before reaching the basilisk. */
    private final float damageScale;
    /** Hits here count toward putting the eyes out. */
    private final boolean head;

    public BasiliskPart(BasiliskEntity parent, float width, float height, float damageScale, boolean head) {
        super(parent);
        this.size = EntityDimensions.scalable(width, height);
        this.damageScale = damageScale;
        this.head = head;
        this.refreshDimensions();
    }

    public boolean isHead() {
        return this.head;
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
        return true;
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        return this.getParent().getPickResult();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !this.isInvulnerableTo(source)
                && this.getParent().hurtPart(this, source, amount * this.damageScale);
    }

    /** So the basilisk cannot damage its own tail, and so targeting sees one animal. */
    @Override
    public boolean is(Entity entity) {
        return this == entity || this.getParent() == entity;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.size;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
